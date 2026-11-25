package app.openhealth.core.source

import app.openhealth.core.ids.ObservationId
import app.openhealth.core.ids.RawRecordId
import app.openhealth.core.ids.SourceId
import app.openhealth.core.merge.AppendOnlyPolicy
import app.openhealth.core.merge.LastWriterWinsPolicy
import app.openhealth.core.merge.MergeAction
import app.openhealth.core.merge.MergePolicyRegistry
import app.openhealth.core.observation.Observation
import app.openhealth.core.observation.ObservationType
import app.openhealth.core.observation.Provenance
import app.openhealth.core.observation.SourceKind
import app.openhealth.core.time.TimeProvenance
import app.openhealth.core.units.Measure
import app.openhealth.core.units.UcumUnit
import io.kotest.property.Arb
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

private val DAY = LocalDate(2026, 3, 1)
private val ZONE = TimeZone.UTC
private val DAY_START = Instant.parse("2026-03-01T00:00:00Z")

private fun source(id: String, kind: SourceKind, family: String = kind.defaultFamily) =
    Source(SourceId(id), kind, id, family = family)

private fun registry() = SourceRegistry(
    listOf(
        source("manual", SourceKind.MANUAL),
        source("garmin", SourceKind.GARMIN),
        source("fitbit", SourceKind.FITBIT),
        source("garmin-hc", SourceKind.HEALTH_CONNECT, family = "garmin"),
    ),
)

private fun hourlySteps(
    sourceId: String,
    perHour: List<Double>,
    startHour: Int = 0,
    day: LocalDate = DAY,
): List<Observation> = perHour.mapIndexed { index, value ->
    val start = Instant.parse("${day}T00:00:00Z").plus((startHour + index).toDouble().hours)
    Observation(
        id = ObservationId("$sourceId-$startHour-$index"),
        type = ObservationType.STEPS,
        value = Measure(value, UcumUnit.STEP),
        start = start,
        end = start.plus(1.hours),
        provenance = Provenance(
            sourceId = SourceId(sourceId),
            time = TimeProvenance.of(start.plus(30.0.minutes), start.plus(1.hours), ZONE),
            idempotencyKey = "$sourceId-$startHour-$index",
            rawRecordId = RawRecordId("raw-$sourceId-$startHour-$index"),
        ),
    )
}

private fun weightObservation(
    sourceId: String,
    valueKg: Double,
    at: Instant = DAY_START.plus(8.hours),
): Observation = Observation(
    id = ObservationId("w-$sourceId-$valueKg-${at.epochSeconds}"),
    type = ObservationType.WEIGHT,
    value = Measure(valueKg, UcumUnit.KILOGRAM),
    start = at,
    end = null,
    provenance = Provenance(
        sourceId = SourceId(sourceId),
        time = TimeProvenance.of(at, at, ZONE),
        idempotencyKey = "w-$sourceId-$valueKg-${at.epochSeconds}",
    ),
)

class StepsDedupPropertyTest {
    @Test
    fun aDayNobodyCoversIsMarkedIncompleteRatherThanAFallback() {
        val oneHour = hourlySteps("garmin", List(1) { 500.0 }, startHour = 9)
        val resolution = OneSourcePerDayPolicy().resolve(
            oneHour,
            registry(),
            app.openhealth.core.time.LocalDay(DAY, ZONE),
        ) as DayResolution.Selected
        assertEquals(SourceId("garmin"), resolution.sourceId)
        assertEquals(DedupReason.INCOMPLETE_DAY, resolution.reason)
    }

    @Test
    fun dedupNeverSumsTwoSources() {
        runBlocking {
            checkAll(Arb.long(0L..5_000L), Arb.long(0L..5_000L)) { a, b ->
                val observations = hourlySteps("garmin", List(24) { a.toDouble() / 24 }) +
                    hourlySteps("fitbit", List(24) { b.toDouble() / 24 })
                val resolution = OneSourcePerDayPolicy().resolve(
                    observations,
                    registry(),
                    app.openhealth.core.time.LocalDay(DAY, ZONE),
                )
                val selected = resolution as DayResolution.Selected
                assertEquals(
                    1,
                    selected.observations.map {
                        it.provenance.sourceId
                    }.distinct().size,
                )
                val total = selected.observations.sumOf { it.value.value }
                val expected = if (selected.sourceId == SourceId("garmin")) {
                    a.toDouble()
                } else {
                    b.toDouble()
                }
                assertEquals(expected, total, 1e-6)
                assertTrue(total != (a + b).toDouble() || a == 0L || b == 0L)
            }
        }
    }

    @Test
    fun theHigherPrioritySourceWinsWhenBothCoverTheDay() {
        val observations = hourlySteps("fitbit", List(24) { 100.0 }) +
            hourlySteps("garmin", List(24) { 200.0 })
        val resolution = OneSourcePerDayPolicy().resolve(
            observations,
            registry(),
            app.openhealth.core.time.LocalDay(DAY, ZONE),
        ) as DayResolution.Selected
        assertEquals(SourceId("garmin"), resolution.sourceId)
        assertEquals(DedupReason.ONE_SOURCE_WINS, resolution.reason)
        assertEquals(4_800.0, resolution.observations.sumOf { it.value.value }, 1e-9)
    }

    @Test
    fun aGapFallsBackToTheWholeNextSourceInsteadOfStitching() {
        val gappy =
            hourlySteps("garmin", List(16) { 100.0 }) +
                hourlySteps("garmin", List(6) { 100.0 }, startHour = 18)
        val complete = hourlySteps("fitbit", List(24) { 120.0 })
        val resolution = OneSourcePerDayPolicy().resolve(
            gappy + complete,
            registry(),
            app.openhealth.core.time.LocalDay(DAY, ZONE),
        ) as DayResolution.Selected
        assertEquals(SourceId("fitbit"), resolution.sourceId)
        assertEquals(DedupReason.GAP_FALLBACK, resolution.reason)
        assertEquals(2_880.0, resolution.observations.sumOf { it.value.value }, 1e-9)
    }

    @Test
    fun aFamilyMemberWithTheBetterPriorityStillWinsOneCopy() {
        val sources = SourceRegistry(
            listOf(
                source("garmin", SourceKind.GARMIN),
                Source(
                    SourceId("garmin-hc"),
                    SourceKind.HEALTH_CONNECT,
                    "Garmin via Health Connect",
                    family = "garmin",
                    priorities = mapOf(ObservationType.STEPS to 1),
                ),
            ),
        )
        val observations = hourlySteps("garmin", List(24) { 100.0 }) +
            hourlySteps("garmin-hc", List(24) { 100.0 })
        val resolution = OneSourcePerDayPolicy().resolve(
            observations,
            sources,
            app.openhealth.core.time.LocalDay(DAY, ZONE),
        ) as DayResolution.Selected
        assertEquals(SourceId("garmin-hc"), resolution.sourceId)
        assertEquals(2_400.0, resolution.observations.sumOf { it.value.value }, 1e-9)
    }

    @Test
    fun aFamilyTieIsBrokenBySourceIdNotInputOrder() {
        val sources = SourceRegistry(
            listOf(
                source("garmin-b", SourceKind.GARMIN),
                source("garmin-a", SourceKind.GARMIN),
            ),
        )
        val a = hourlySteps("garmin-a", List(24) { 100.0 })
        val b = hourlySteps("garmin-b", List(24) { 100.0 })
        val first = OneSourcePerDayPolicy().resolve(
            a + b,
            sources,
            app.openhealth.core.time.LocalDay(DAY, ZONE),
        ) as DayResolution.Selected
        val second = OneSourcePerDayPolicy().resolve(
            b + a,
            sources,
            app.openhealth.core.time.LocalDay(DAY, ZONE),
        ) as DayResolution.Selected
        assertEquals(SourceId("garmin-a"), first.sourceId)
        assertEquals(first.sourceId, second.sourceId)
    }

    @Test
    fun theSameVendorThroughTwoPipesIsNotCountedTwice() {
        val viaFit = hourlySteps("garmin", List(24) { 150.0 })
        val viaHealthConnect = hourlySteps("garmin-hc", List(24) { 150.0 })
        val resolution = OneSourcePerDayPolicy().resolve(
            viaFit + viaHealthConnect,
            registry(),
            app.openhealth.core.time.LocalDay(DAY, ZONE),
        ) as DayResolution.Selected
        assertEquals("garmin", resolution.family)
        assertEquals(3_600.0, resolution.observations.sumOf { it.value.value }, 1e-9)
    }

    @Test
    fun aWakingWindowOfSixteenHoursCoversTheDay() {
        val waking = hourlySteps("garmin", List(16) { 100.0 }, startHour = 7)
        val fitbit = hourlySteps("fitbit", List(24) { 300.0 })
        val resolution = OneSourcePerDayPolicy().resolve(
            waking + fitbit,
            registry(),
            app.openhealth.core.time.LocalDay(DAY, ZONE),
        ) as DayResolution.Selected
        assertEquals(SourceId("garmin"), resolution.sourceId)
        assertEquals(DedupReason.ONE_SOURCE_WINS, resolution.reason)
        assertEquals(1_600.0, resolution.observations.sumOf { it.value.value }, 1e-9)
    }

    @Test
    fun oneSyncedHourIsNotACoveredDay() {
        val oneHour = hourlySteps("garmin", List(1) { 500.0 }, startHour = 9)
        val fitbit = hourlySteps("fitbit", List(24) { 120.0 })
        val resolution = OneSourcePerDayPolicy().resolve(
            oneHour + fitbit,
            registry(),
            app.openhealth.core.time.LocalDay(DAY, ZONE),
        ) as DayResolution.Selected
        assertEquals(SourceId("fitbit"), resolution.sourceId)
        assertEquals(DedupReason.GAP_FALLBACK, resolution.reason)
    }

    @Test
    fun anEmptyDayResolvesToNothing() {
        val resolution = OneSourcePerDayPolicy()
            .resolve(emptyList(), registry(), app.openhealth.core.time.LocalDay(DAY, ZONE))
        assertEquals(DayResolution.Empty, resolution)
    }
}

class HeartRateDedupTest {
    private val day = app.openhealth.core.time.LocalDay(DAY, ZONE)

    @Test
    fun aDayTakesTheHigherPrioritySourcesSeriesAndTheOtherReadingsRemain() {
        val garmin = (0 until 3).map { hour ->
            Observation(
                id = ObservationId("hr-g-$hour"),
                type = ObservationType.HEART_RATE,
                value = Measure(60.0, UcumUnit.PER_MINUTE),
                start = DAY_START.plus(hour.hours),
                end = null,
                provenance = Provenance(
                    sourceId = SourceId("garmin"),
                    time = TimeProvenance.of(
                        DAY_START.plus(hour.hours),
                        DAY_START.plus(hour.hours),
                        ZONE,
                    ),
                    idempotencyKey = "hr-g-$hour",
                ),
            )
        }
        val fitbit = (0 until 3).map { hour ->
            Observation(
                id = ObservationId("hr-f-$hour"),
                type = ObservationType.HEART_RATE,
                value = Measure(90.0, UcumUnit.PER_MINUTE),
                start = DAY_START.plus(hour.hours),
                end = null,
                provenance = Provenance(
                    sourceId = SourceId("fitbit"),
                    time = TimeProvenance.of(
                        DAY_START.plus(hour.hours),
                        DAY_START.plus(hour.hours),
                        ZONE,
                    ),
                    idempotencyKey = "hr-f-$hour",
                ),
            )
        }
        val resolution = PrimarySourcePolicy().resolve(
            garmin + fitbit,
            registry(),
            day,
        ) as DayResolution.Selected
        assertEquals(SourceId("garmin"), resolution.sourceId)
        assertEquals(3, resolution.observations.size)
        assertEquals(60.0, resolution.observations.map { it.value.value }.average(), 1e-9)
    }
}

class WeightDedupTest {
    private val day = app.openhealth.core.time.LocalDay(DAY, ZONE)

    @Test
    fun manualBeatsDeviceAndLaterBeatsEarlier() {
        val observations = listOf(
            weightObservation("garmin", 81.0),
            weightObservation("manual", 80.5),
        )
        val resolution = ManualFirstPerDayPolicy()
            .resolve(observations, registry(), day) as DayResolution.Selected
        assertEquals(SourceId("manual"), resolution.sourceId)
        assertEquals(80.5, resolution.observations.single().value.value)
        assertEquals(DedupReason.MANUAL_OVERRIDE, resolution.reason)
    }

    @Test
    fun twoDeviceReadingsKeepTheLaterOne() {
        val earlier = weightObservation("garmin", 81.0, DAY_START.plus(7.hours))
        val later = weightObservation("garmin", 80.8, DAY_START.plus(9.hours))
        val resolution = ManualFirstPerDayPolicy()
            .resolve(listOf(earlier, later), registry(), day) as DayResolution.Selected
        assertEquals(80.8, resolution.observations.single().value.value)
        assertEquals(DedupReason.LATER_WINS, resolution.reason)
    }

    @Test
    fun duplicatesWithinAMinuteMerge() {
        val first = weightObservation("garmin", 81.0, DAY_START.plus(7.hours))
        val sameMinute = weightObservation("garmin", 81.1, DAY_START.plus(7.hours).plus(30.seconds))
        val resolution = ManualFirstPerDayPolicy()
            .resolve(listOf(first, sameMinute), registry(), day) as DayResolution.Selected
        assertEquals(1, resolution.observations.size)
        assertEquals(DedupReason.DUPLICATE_MERGED, resolution.reason)
    }
}

class MergePolicyTest {
    @Test
    fun appendOnlyKeepsTheLocalRecord() {
        val outcome = AppendOnlyPolicy<String>("observation").merge("a", "b", 1, 2)
        assertEquals(MergeAction.KEEP_LOCAL, outcome.action)
        assertEquals("a", outcome.value)
    }

    @Test
    fun notesTakeTheLaterWriter() {
        val policy = LastWriterWinsPolicy<String>("note")
        assertEquals("remote", policy.merge("local", "remote", 1, 2).value)
        assertEquals("local", policy.merge("local", "remote", 2, 1).value)
    }

    @Test
    fun settingsMergeKeyByKeyAndKeepTheLocalValueOnConflict() {
        val policy = app.openhealth.core.merge.SettingsFieldMergePolicy()
        val merged = policy.merge(
            mapOf("units" to "metric", "theme" to "dark"),
            mapOf("units" to "imperial", "accent" to "blue"),
            1,
            2,
        ).value
        assertEquals("metric", merged?.get("units"))
        assertEquals("dark", merged?.get("theme"))
        assertEquals("blue", merged?.get("accent"))
    }

    @Test
    fun rollupsAreDeclaredNeverSynced() {
        val registry = MergePolicyRegistry.default()
        assertEquals(listOf("daily_rollup"), registry.neverSynced())
        assertTrue(registry.get("observation") is AppendOnlyPolicy<*>)
        listOf("measurement", "vital", "entry", "session", "profile", "source", "food").forEach {
            assertTrue(registry.get(it) != null, "no merge policy for $it")
        }
    }
}

class ValidatorTest {
    @Test
    fun aValueOutsideThePlausibleRangeIsFlaggedNotEdited() {
        val observation = weightObservation("manual", 800.0)
        val result = app.openhealth.core.validate.ObservationValidator.validate(
            observation,
            DAY_START.plus(12.hours),
        )
        assertTrue(
            app.openhealth.core.validate.ValidationIssue.OUTSIDE_PLAUSIBLE_RANGE in result.issues,
        )
        assertEquals(800.0, observation.value.value)
    }

    @Test
    fun everyObservationTypeHasARange() {
        ObservationType.entries.forEach { type ->
            val range = app.openhealth.core.validate.PlausibilityRanges.of(type)
            assertEquals(type, range.type)
            assertTrue(range.min < range.max)
        }
    }
}
