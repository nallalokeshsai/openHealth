package app.openhealth.core.activity

import app.openhealth.core.ids.SeededIdGenerator
import app.openhealth.core.ids.SourceId
import app.openhealth.core.observation.Observation
import app.openhealth.core.observation.ObservationType
import app.openhealth.core.observation.SourceKind
import app.openhealth.core.seam.HealthReading
import app.openhealth.core.source.Source
import app.openhealth.core.source.SourceRegistry
import app.openhealth.core.units.UcumUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/**
 * The health-bus seam's domain side (`Tech-Stack.md` §8): a reading becomes a layer-2
 * observation with its own interval, unit, source and both timestamps, and nothing here
 * decides a winner — that stays the rollup's job (`pr.md` §7.6).
 */
class HealthBusImportTest {
    private val zone = TimeZone.of("Europe/London")
    private val recordedAt = Instant.parse("2026-11-24T09:00:00Z")
    private val start = Instant.parse("2026-11-23T23:30:00Z")

    private fun context() = HealthImportContext(
        sourceId = SourceId("health-connect"),
        zone = zone,
        recordedAt = recordedAt,
        device = "Pixel Watch",
        ids = SeededIdGenerator(3),
    )

    @Test
    fun aStepsReadingBecomesOneIntervalObservation() {
        val result = HealthBusImporter.toObservations(
            listOf(
                HealthReading(
                    type = HealthBusTypes.STEPS,
                    value = 640.0,
                    unit = "{steps}",
                    startEpochMillis = start.toEpochMilliseconds(),
                    endEpochMillis = start.plus(
                        kotlin.time.Duration.parse("30m"),
                    ).toEpochMilliseconds(),
                    metadata = mapOf("device" to "Pixel Watch"),
                ),
            ),
            context(),
        )

        val observation = result.observations.single()
        assertEquals(ObservationType.STEPS, observation.type)
        assertEquals(640.0, observation.value.convertTo(UcumUnit.STEP).value, 1e-9)
        assertEquals(start, observation.start)
        assertEquals(start + kotlin.time.Duration.parse("30m"), observation.end)
        // 23:30 UTC is 23:30 in London in November, so the local day matches the instant.
        assertEquals(LocalDate(2026, 11, 23), observation.localDate)
        assertEquals(recordedAt, observation.provenance.recordedAt)
        assertEquals("Pixel Watch", observation.provenance.device)
    }

    @Test
    fun aLateImportKeepsTheDaysItBelongsTo() {
        val late = listOf(
            reading(HealthBusTypes.STEPS, 100.0, "{steps}", "2026-11-20T08:00:00Z"),
            reading(HealthBusTypes.STEPS, 200.0, "{steps}", "2026-11-21T08:00:00Z"),
        )
        val observations = HealthBusImporter.toObservations(late, context()).observations
        assertEquals(
            listOf(LocalDate(2026, 11, 20), LocalDate(2026, 11, 21)),
            observations.map { it.localDate },
        )
        assertTrue(observations.all { it.provenance.isBackfilled })
    }

    @Test
    fun anUnknownTypeIsSkippedRatherThanGuessed() {
        val result = HealthBusImporter.toObservations(
            listOf(reading("vo2_max", 48.0, "1", "2026-11-23T08:00:00Z")),
            context(),
        )
        assertTrue(result.observations.isEmpty())
        assertEquals(listOf("vo2_max"), result.skipped)
    }

    @Test
    fun aNegativeReadingIsNotStored() {
        val result = HealthBusImporter.toObservations(
            listOf(reading(HealthBusTypes.HEART_RATE, -5.0, "/min", "2026-11-23T08:00:00Z")),
            context(),
        )
        assertTrue(result.observations.isEmpty())
        assertEquals(listOf(HealthBusTypes.HEART_RATE), result.skipped)
    }

    @Test
    fun aSleepStageKeepsItsOwnTypeRatherThanTheNightsTotal() {
        val observation = HealthBusImporter.toObservations(
            listOf(
                HealthReading(
                    type = HealthBusTypes.SLEEP_REM,
                    value = 5_400.0,
                    unit = "s",
                    startEpochMillis = start.toEpochMilliseconds(),
                    endEpochMillis = start.plus(
                        kotlin.time.Duration.parse("90m"),
                    ).toEpochMilliseconds(),
                    metadata = mapOf("stage" to "rem"),
                ),
            ),
            context(),
        ).observations.single()
        assertEquals(ObservationType.SLEEP_REM, observation.type)
        assertEquals(5_400.0, observation.value.convertTo(UcumUnit.SECOND).value, 1e-9)
    }

    /**
     * A night's stages, its asleep total and its time in bed all describe the same
     * minutes. The sleep rollup sums what it is given, so each has to land under its
     * own observation type or a staged night is counted about twice (`pr.md` §7.6).
     */
    @Test
    fun aStagedNightDoesNotSumItsTotalWithItsParts() {
        val night = listOf(
            sleepReading(HealthBusTypes.SLEEP, 25_200.0, 0, 480),
            sleepReading(HealthBusTypes.SLEEP_DEEP, 5_400.0, 0, 90),
            sleepReading(HealthBusTypes.SLEEP_LIGHT, 12_600.0, 90, 300),
            sleepReading(HealthBusTypes.SLEEP_REM, 7_200.0, 300, 420),
            sleepReading(HealthBusTypes.SLEEP_AWAKE, 3_600.0, 420, 480),
            sleepReading(HealthBusTypes.SLEEP_IN_BED, 28_800.0, 0, 480),
        )
        val observations = HealthBusImporter.toObservations(night, context()).observations

        assertEquals(night.size, observations.size)
        assertEquals(
            night.size,
            observations.map { it.type }.distinct().size,
            "every sleep key keeps a type of its own",
        )
        val durations = observations.filter { it.type == ObservationType.SLEEP_DURATION }
        assertEquals(1, durations.size)
        assertEquals(25_200.0, durations.single().value.convertTo(UcumUnit.SECOND).value, 1e-9)
        assertEquals(
            ObservationType.SLEEP_IN_BED,
            observations.single { it.value.convertTo(UcumUnit.SECOND).value == 28_800.0 }.type,
        )
    }

    private fun sleepReading(
        type: String,
        seconds: Double,
        fromMinute: Long,
        toMinute: Long,
    ): HealthReading = HealthReading(
        type = type,
        value = seconds,
        unit = "s",
        startEpochMillis = start.plus(
            kotlin.time.Duration.parse("${fromMinute}m"),
        ).toEpochMilliseconds(),
        endEpochMillis = start.plus(
            kotlin.time.Duration.parse("${toMinute}m"),
        ).toEpochMilliseconds(),
    )

    @Test
    fun anExerciseSessionBecomesActiveMinutes() {
        val observation = HealthBusImporter.toObservations(
            listOf(
                HealthReading(
                    type = HealthBusTypes.EXERCISE,
                    value = 45.0,
                    unit = "min",
                    startEpochMillis = start.toEpochMilliseconds(),
                    endEpochMillis = start.plus(
                        kotlin.time.Duration.parse("45m"),
                    ).toEpochMilliseconds(),
                    metadata = mapOf("group" to "hc-session-1"),
                ),
            ),
            context(),
        ).observations.single()
        assertEquals(ObservationType.ACTIVE_MINUTES, observation.type)
        assertEquals(45.0, observation.value.convertTo(UcumUnit.MINUTE).value, 1e-9)
        assertEquals("hc-session-1", observation.groupKey)
    }

    @Test
    fun theSameRecordTwiceKeepsTheSameIdentity() {
        val reading = reading(HealthBusTypes.STEPS, 500.0, "{steps}", "2026-11-23T08:00:00Z")
        val first = HealthBusImporter.toObservations(
            listOf(reading),
            context(),
        ).observations.single()
        val second = HealthBusImporter.toObservations(
            listOf(reading),
            context(),
        ).observations.single()
        assertEquals(first.provenance.idempotencyKey, second.provenance.idempotencyKey)
    }

    @Test
    fun aDeclaredUnitIsConvertedToTheCanonicalOne() {
        val observation = HealthBusImporter.toObservations(
            listOf(
                HealthReading(
                    type = HealthBusTypes.WEIGHT,
                    value = 154.32,
                    unit = "[lb_av]",
                    startEpochMillis = start.toEpochMilliseconds(),
                    endEpochMillis = null,
                ),
            ),
            context(),
        ).observations.single()
        assertEquals(ObservationType.WEIGHT, observation.type)
        assertEquals(70.0, observation.value.value, 0.01)
    }

    @Test
    fun everyBusTypeMapsToAClosedObservationType() {
        HealthBusTypes.all.forEach { key ->
            assertTrue(HealthBusTypes.observationType(key) != null, "no type for $key")
            assertTrue(HealthBusTypes.unit(key) != null, "no unit for $key")
        }
    }

    @Test
    fun everyReadCapabilityNamesATypeTheDomainKnows() {
        assertEquals(10, HealthBusCapabilities.all.size)
        assertEquals(10, HealthBusCapabilities.readKeys.distinct().size)
        assertTrue(
            HealthBusCapabilities.readKeys.all { HealthBusTypes.observationType(it) != null },
        )
    }

    private fun reading(type: String, value: Double, unit: String, at: String): HealthReading =
        HealthReading(
            type = type,
            value = value,
            unit = unit,
            startEpochMillis = Instant.parse(at).toEpochMilliseconds(),
            endEpochMillis = null,
        )

    @Test
    fun aGarminDeviceNameMarksTheReadingAsTheGarminFamily() {
        assertTrue(GarminDetector.isGarmin("vívoactive 5"))
        assertTrue(GarminDetector.isGarmin("Garmin Forerunner 265"))
        assertTrue(GarminDetector.isGarmin("FENIX 7X"))
        assertFalse(GarminDetector.isGarmin("Pixel Watch 3"))
        assertFalse(GarminDetector.isGarmin("Withings Body+"))
        assertFalse(GarminDetector.isGarmin(null))
    }

    @Test
    fun aGarminReadingKeepsItsOwnFamilySoNoDayIsCountedTwice() {
        val sources = SourceRegistry(
            listOf(
                Source(SourceId("phone-steps"), SourceKind.DEVICE, "Phone steps", family = "phone"),
                Source(SourceId("garmin-fit"), SourceKind.GARMIN, "Garmin FIT"),
                Source(
                    SourceId("garmin-hc"),
                    SourceKind.HEALTH_CONNECT,
                    "Garmin via Health Connect",
                    family = "garmin",
                ),
            ),
        )
        assertEquals("garmin", sources.familyOf(SourceId("garmin-fit")))
        assertEquals("garmin", sources.familyOf(SourceId("garmin-hc")))
        assertEquals("phone", sources.familyOf(SourceId("phone-steps")))
    }
}

/**
 * Which source reported what, for the provenance line under a steps number. The stored
 * rollup stays the number of record; this only explains it (`pr.md` §7.6).
 */
class SourceDayTest {
    private val day = LocalDate(2026, 11, 23)
    private val zone = TimeZone.UTC
    private val registry = SourceRegistry(
        listOf(
            Source(SourceId("phone-steps"), SourceKind.DEVICE, "Phone steps", family = "phone"),
            Source(SourceId("garmin-fit"), SourceKind.GARMIN, "Garmin FIT"),
        ),
    )

    @Test
    fun theLoserKeepsItsFigureAndIsNotAddedToTheWinner() {
        val observations = interval("phone-steps", 8_412.0, 8) +
            interval("garmin-fit", 9_110.0, 8)
        val breakdown = SourceDay.breakdown(ObservationType.STEPS, day, observations, registry)

        assertEquals(SourceId("garmin-fit"), breakdown.winningSource)
        assertEquals("garmin", breakdown.winningFamily)
        assertEquals(2, breakdown.contributions.size)
        assertEquals(1, breakdown.losers.size)
        assertEquals(8_412.0, breakdown.losers.single().total, 1e-9)
        assertEquals(9_110.0, breakdown.contributions.first().total, 1e-9)
    }

    @Test
    fun aDailyTotalReplacesThatSourcesIntervals() {
        val observations = interval("phone-steps", 2_000.0, 4) +
            dailyTotal("phone-steps", 8_000.0) +
            interval("garmin-fit", 8_500.0, 8)
        val breakdown = SourceDay.breakdown(ObservationType.STEPS, day, observations, registry)
        val phone = breakdown.contributions.single { it.sourceId == SourceId("phone-steps") }
        assertEquals(8_000.0, phone.total, 1e-9)
        assertTrue(phone.isDailyTotal)
    }

    @Test
    fun noObservationsMeansNoWinner() {
        val breakdown = SourceDay.breakdown(ObservationType.STEPS, day, emptyList(), registry)
        assertEquals(null, breakdown.winningSource)
        assertTrue(breakdown.contributions.isEmpty())
    }

    private fun interval(source: String, total: Double, segments: Int): List<Observation> =
        (0 until segments).map { index ->
            observation(
                source = source,
                value = total / segments,
                start = Instant.parse("2026-11-23T0${index % 10}:00:00Z"),
                end = Instant.parse("2026-11-23T0${index % 10}:30:00Z"),
            )
        }

    private fun dailyTotal(source: String, total: Double): Observation = observation(
        source = source,
        value = total,
        start = Instant.parse("2026-11-23T23:00:00Z"),
        end = null,
    )

    private fun observation(
        source: String,
        value: Double,
        start: Instant,
        end: Instant?,
    ): Observation {
        val provenance = app.openhealth.core.observation.Provenance(
            sourceId = SourceId(source),
            time = app.openhealth.core.time.TimeProvenance.of(start, start, zone),
            idempotencyKey = "k:$source:$value:${start.epochSeconds}",
        )
        return Observation(
            id = app.openhealth.core.ids.ObservationId("o:$source:$value:${start.epochSeconds}"),
            type = ObservationType.STEPS,
            value = app.openhealth.core.units.Measure(value, UcumUnit.STEP),
            start = start,
            end = end,
            provenance = provenance,
        )
    }
}

/** The editable priority list of `pr.md` §7.6. */
class SourcePriorityPlanTest {
    private val steps = ObservationType.STEPS
    private val sources = listOf(
        Source(SourceId("garmin-fit"), SourceKind.GARMIN, "Garmin file", family = "garmin"),
        Source(SourceId("phone-steps"), SourceKind.DEVICE, "Phone steps", family = "phone"),
        Source(SourceId("health-connect"), SourceKind.HEALTH_CONNECT, "Health Connect"),
    )

    @Test
    fun theOrderStartsFromTheDefaultPriorities() {
        assertEquals(
            listOf("garmin-fit", "phone-steps", "health-connect"),
            SourcePriorityPlan.order(sources, steps).map { it.value },
        )
    }

    @Test
    fun movingARowWritesExplicitPrioritiesForTheWholeList() {
        val order = SourcePriorityPlan.order(sources, steps)
        val moved = SourcePriorityPlan.move(order, SourceId("phone-steps"), up = true)
        assertEquals(
            listOf("phone-steps", "garmin-fit", "health-connect"),
            moved.map { it.value },
        )
        val updated = SourcePriorityPlan.priorities(moved, steps, sources.associateBy(Source::id))
        assertEquals(10, updated.getValue(SourceId("phone-steps")).priorityFor(steps))
        assertEquals(20, updated.getValue(SourceId("garmin-fit")).priorityFor(steps))
        assertEquals(30, updated.getValue(SourceId("health-connect")).priorityFor(steps))
    }

    @Test
    fun anotherTypesPriorityIsUntouched() {
        val order = SourcePriorityPlan.order(sources, steps)
        val moved = SourcePriorityPlan.move(order, SourceId("health-connect"), up = true)
        val updated = SourcePriorityPlan.priorities(moved, steps, sources.associateBy(Source::id))
        updated.values.forEach { source ->
            assertEquals(
                source.priorityFor(ObservationType.HEART_RATE),
                sources.single { it.id == source.id }.priorityFor(ObservationType.HEART_RATE),
            )
        }
    }

    @Test
    fun movingPastTheEdgeIsANoOp() {
        val order = SourcePriorityPlan.order(sources, steps)
        assertEquals(order, SourcePriorityPlan.move(order, order.first(), up = true))
        assertEquals(order, SourcePriorityPlan.move(order, order.last(), up = false))
        assertEquals(order, SourcePriorityPlan.move(order, SourceId("unknown"), up = true))
    }
}
