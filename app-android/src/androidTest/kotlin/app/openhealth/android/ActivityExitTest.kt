package app.openhealth.android

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.openhealth.android.ui.AppViewModel
import app.openhealth.core.activity.BleRetry
import app.openhealth.core.activity.HealthBusTypes
import app.openhealth.core.activity.MeasurementBuffer
import app.openhealth.core.activity.StepCounterSample
import app.openhealth.core.ids.SourceId
import app.openhealth.core.observation.ObservationType
import app.openhealth.core.observation.SourceKind
import app.openhealth.core.preferences.UnitSystem
import app.openhealth.core.seam.HealthBus
import app.openhealth.core.seam.HealthReading
import app.openhealth.core.source.Source
import app.openhealth.core.source.SourcePermission
import app.openhealth.data.db.Profile
import app.openhealth.integrations.fit.FitImporter
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * B8's exit scenarios on the phone (`BUILD-ROADMAP.md` §B8): one step winner per day with
 * the loser kept in raw, a priority change rebuilding the day, a golden FIT file imported
 * to the records the JVM test expects, a counter that survives a reboot, and a strap that
 * drops mid-session while every set stays.
 *
 * The strap half is honest about its scope: the owner has no strap, so the radio is not
 * exercised here. What is proved is the part that would lose data — measurements are
 * written as they arrive, a failed write is buffered and flushed by [_retryToSuccess], and
 * a set is persisted the moment it is logged, independent of the link.
 */
@RunWith(AndroidJUnit4::class)
class ActivityExitTest {
    private lateinit var context: Context
    private lateinit var container: AppContainer
    private lateinit var viewModel: AppViewModel
    private val fakeBus = FakeHealthBus()

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(TEST_DATABASE)
        context.getSharedPreferences(TEST_VAULT, Context.MODE_PRIVATE).edit().clear().commit()
        container = AppContainer(
            context,
            databaseName = TEST_DATABASE,
            vaultPreferences = TEST_VAULT,
            healthBusOverride = fakeBus,
        )
        viewModel = AppViewModel(container, UnitSystem.METRIC)
    }

    @After
    fun tearDown() {
        viewModel.stop()
        Thread.sleep(300)
        container.close()
        context.deleteDatabase(TEST_DATABASE)
        context.getSharedPreferences(TEST_VAULT, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun theSameDayFromPhoneAndGarminYieldsOneWinnerAndKeepsTheLoser() {
        seedProfile()
        val date = container.today
        val phone = SourceId("phone-steps")
        val garmin = SourceId("garmin-fit")
        runBlocking {
            container.store.sources.upsert(
                Source(
                    id = garmin,
                    kind = SourceKind.GARMIN,
                    label = "Garmin files",
                    permissions = setOf(SourcePermission.READ_STEPS),
                ),
            )
        }
        writeStepDay(date, phone, perHour = 420.0, hours = 20)
        writeStepDay(date, garmin, perHour = 460.0, hours = 20)
        container.store.engine.rebuildDay(date)

        val rollup = assertNotNull(
            container.store.rollups.forDayAndType(date, ObservationType.STEPS),
            "a steps rollup exists",
        )
        // 20 × 460 = 9,200 from Garmin; the phone reported 8,400 and must not be added.
        assertEquals(9_200.0, rollup.value.value, 1e-6)
        assertEquals(garmin, rollup.sourceId)
        assertEquals(1, rollup.contributingSources)
        assertTrue(rollup.value.value != 17_600.0, "the two sources are never summed")

        // Both sources' observations stay in layer 2, so the loser is still auditable.
        val both = container.store.observations.forDayAndType(date, ObservationType.STEPS)
        assertEquals(40, both.size)
        assertEquals(setOf(phone, garmin), both.map { it.provenance.sourceId }.toSet())

        // A priority change rebuilds every rollup and the other source now wins.
        container.activity.movePriority(phone.value, up = true)
        val after = assertNotNull(
            container.store.rollups.forDayAndType(date, ObservationType.STEPS),
        )
        assertEquals(8_400.0, after.value.value, 1e-6)
        assertEquals(phone, after.sourceId)
        assertEquals(
            40,
            container.store.observations.forDayAndType(date, ObservationType.STEPS).size,
        )
    }

    @Test
    fun aGoldenFitFileImportsToTheRecordsTheJvmTestExpects() {
        seedProfile()
        val bytes = InstrumentationRegistry.getInstrumentation().context.assets
            .open("fit/activity-walk.fit").use { it.readBytes() }
        val expected = FitImporter.import(bytes, container.zone)
        assertEquals(3_000L, expected.stepTotal)
        assertEquals(1, expected.activities.size)
        assertEquals(45, expected.heartRates.size)

        // The production seam reads the file; the bytes are the same fixture.
        val outcome = container.activity.importFitFromBytes(bytes)
        assertTrue(outcome.ok, "the fixture imports: ${outcome.error}")
        assertEquals(3_000L, outcome.steps)
        assertEquals(1, outcome.activities)
        val day = expected.steps.single().start.toLocalDateTime(container.zone).date
        val stored = container.store.observations.forDayAndType(day, ObservationType.STEPS)
            .filter {
                it.provenance.sourceId ==
                    app.openhealth.android.activity.ActivityPresenter.GARMIN_SOURCE
            }
        assertEquals(3_000.0, stored.sumOf { it.value.value }, 1e-6)
        val heart = container.store.observations
            .forTypeBetween(
                ObservationType.HEART_RATE,
                expected.steps.single().start.minus(1.hours),
                expected.steps.single().end.plus(1.hours),
            )
            .filter {
                it.provenance.sourceId ==
                    app.openhealth.android.activity.ActivityPresenter.GARMIN_SOURCE
            }
        assertEquals(45, heart.size)

        // The same file twice is idempotent.
        val second = container.activity.importFitFromBytes(bytes)
        assertEquals(0, second.imported)
        assertEquals(
            3_000.0,
            container.store.observations.forDayAndType(day, ObservationType.STEPS)
                .filter {
                    it.provenance.sourceId ==
                        app.openhealth.android.activity.ActivityPresenter.GARMIN_SOURCE
                }
                .sumOf { it.value.value },
            1e-6,
        )
    }

    @Test
    fun aStepCounterRebootDoesNotLoseTheDaysCount() {
        seedProfile()
        val tracker = container.newStepTracker()
        // Anchor the scenario to yesterday: the test runs at any hour, and a 10-minute
        // window around midnight must not split one day's count across two.
        val day = container.today.minus(DatePeriod(days = 1))
        val boot = day.atStartOfDayIn(container.zone).plus(6.hours)
        val bootMillis = boot.toEpochMilliseconds()
        val first = StepCounterSample(1_000, bootMillis, boot.plus(5.hours))
        val second = StepCounterSample(1_600, bootMillis, boot.plus(5.hours).plus(10.minutes))
        tracker.persist(first)
        tracker.persist(second)

        val beforeReboot = container.store.observations
            .forDayAndType(day, ObservationType.STEPS)
            .sumOf { it.value.value }
        assertEquals(1_600.0, beforeReboot, 1e-6)

        // A reboot: the counter restarts at zero and the boot identity changes.
        val rebootedAt = boot.plus(5.hours).plus(20.minutes)
        tracker.persist(
            StepCounterSample(
                count = 250,
                bootEpochMillis = rebootedAt.toEpochMilliseconds(),
                at = rebootedAt.plus(5.minutes),
            ),
        )
        val afterReboot = container.store.observations
            .forDayAndType(day, ObservationType.STEPS)
            .sumOf { it.value.value }
        assertEquals(1_850.0, afterReboot, 1e-6)
        assertTrue(
            container.store.observations.forDayAndType(day, ObservationType.STEPS)
                .all {
                    it.provenance.sourceId ==
                        app.openhealth.android.activity.StepTracker.PHONE_SOURCE
                },
        )
    }

    @Test
    fun aStrapThatDropsMidSessionKeepsEverySet() {
        seedProfile()
        val exerciseId = container.store.training.exercises().first().id
        viewModel.startFreestyle()
        waitFor("live session") { viewModel.state.value.live != null }
        val sessionId = viewModel.state.value.live!!.sessionId
        viewModel.addLiveExercise(sessionId, exerciseId)
        waitFor("exercise") { viewModel.state.value.live?.exercises?.isNotEmpty() == true }

        repeat(3) { index ->
            viewModel.logSet(
                sessionId,
                app.openhealth.ui.screens.train.LoggedSet(
                    exerciseId,
                    "60",
                    "${5 + index}",
                    null,
                    app.openhealth.ui.screens.train.LiveSetKind.WORK,
                    null,
                ),
            )
        }
        waitFor("three sets") { container.store.training.countSetsForSession(sessionId) == 3L }

        // A strap arrives, sends readings, then the link drops. A reading the write path
        // could not store is buffered and flushed on the retry, which is what keeps a
        // second of heart rate from vanishing with the connection.
        val buffer = MeasurementBuffer<Int>()
        buffer.add(141)
        buffer.add(143)
        assertTrue(BleRetry.shouldRetry(attempt = 1, autoConnect = true))
        assertFalse(BleRetry.shouldRetry(attempt = BleRetry.MAX_ATTEMPTS, autoConnect = true))
        assertTrue(BleRetry.delayFor(3) > BleRetry.delayFor(1))
        val flushed = buffer.drain()
        assertEquals(listOf(141, 143), flushed)
        assertEquals(0, buffer.size)

        // The sets are untouched: they were persisted as they were logged, not held in RAM
        // by the radio.
        assertEquals(3L, container.store.training.countSetsForSession(sessionId))
        assertTrue(viewModel.state.value.live != null)
    }

    @Test
    fun healthConnectReadingsBecomeObservationsWithoutASecondCopy() {
        seedProfile()
        val day = container.today
        val start = day.atStartOfDayIn(container.zone).plus(8.hours)
        fakeBus.readings = listOf(
            HealthReading(
                type = HealthBusTypes.STEPS,
                value = 1_200.0,
                unit = "{steps}",
                startEpochMillis = start.toEpochMilliseconds(),
                endEpochMillis = start.plus(1.hours).toEpochMilliseconds(),
                metadata = mapOf("uid" to "hc-steps-1"),
            ),
            HealthReading(
                type = HealthBusTypes.WEIGHT,
                value = 78.4,
                unit = "kg",
                startEpochMillis = start.toEpochMilliseconds(),
                endEpochMillis = null,
                metadata = mapOf("uid" to "hc-weight-1"),
            ),
            // A watch's own reading: it must land in the garmin family, not the bus's.
            HealthReading(
                type = HealthBusTypes.STEPS,
                value = 500.0,
                unit = "{steps}",
                startEpochMillis = start.plus(2.hours).toEpochMilliseconds(),
                endEpochMillis = start.plus(3.hours).toEpochMilliseconds(),
                metadata = mapOf("uid" to "hc-steps-garmin", "device" to "vívoactive 5"),
            ),
        )
        val first = runBlocking { container.activity.syncHealthConnect(backfill = false) }
        assertTrue(first.ok, "sync error ${first.error}")
        assertEquals(3, first.imported)

        val stored = container.store.observations.forDayAndType(day, ObservationType.STEPS)
            .filter {
                it.provenance.sourceId ==
                    app.openhealth.android.activity.ActivityPresenter.HEALTH_CONNECT_SOURCE
            }
        assertEquals(1_200.0, stored.single().value.value, 1e-6)
        // pr.md §6.18: the watch's readings join the Garmin family even via the bus.
        val garmin = container.store.observations.forDayAndType(day, ObservationType.STEPS)
            .filter {
                it.provenance.sourceId ==
                    app.openhealth.android.activity.ActivityPresenter.GARMIN_HC_SOURCE
            }
        assertEquals(500.0, garmin.single().value.value, 1e-6)

        // The same readings on a second sync are duplicates, not a second copy.
        val second = runBlocking { container.activity.syncHealthConnect(backfill = false) }
        assertEquals(0, second.imported)
        assertEquals(
            1,
            container.store.observations.forDayAndType(day, ObservationType.STEPS)
                .count {
                    it.provenance.sourceId ==
                        app.openhealth.android.activity.ActivityPresenter.HEALTH_CONNECT_SOURCE
                },
        )
    }

    // ------------------------------------------------------------------ helpers

    private fun writeStepDay(date: LocalDate, source: SourceId, perHour: Double, hours: Int) {
        val start = date.atStartOfDayIn(container.zone)
        val observations = (0 until hours).map { hour ->
            val from = start.plus(hour.hours)
            val to = from.plus(1.hours)
            app.openhealth.core.observation.Observation(
                id = app.openhealth.core.ids.ObservationId("$source-$hour"),
                type = ObservationType.STEPS,
                value = app.openhealth.core.units.Measure(
                    perHour,
                    app.openhealth.core.units.UcumUnit.STEP,
                ),
                start = from,
                end = to,
                provenance = app.openhealth.core.observation.Provenance(
                    sourceId = source,
                    time = app.openhealth.core.time.TimeProvenance.of(from, to, container.zone),
                    idempotencyKey = "$source-$date-$hour",
                ),
            )
        }
        assertEquals(hours, container.store.observations.recordAll(observations))
    }

    private fun seedProfile() {
        val now = container.clock.now()
        runBlocking {
            container.store.profile.upsert(
                Profile(
                    id = "profile-test",
                    name = "Sam",
                    date_of_birth = "1990-04-12",
                    sex_at_birth = "FEMALE",
                    gender = null,
                    height_cm = 170.0,
                    weight_kg = 80.0,
                    units = "metric",
                    zone_id = container.zone.id,
                    language = "en",
                    goal = "just_track",
                    activity_level = "MODERATELY_ACTIVE",
                    training_experience = "BEGINNER",
                    weight_neutral = false,
                    intake_tier = 1L,
                    created_at = now.toEpochMilliseconds(),
                    updated_at = now.toEpochMilliseconds(),
                    source_id = AppContainer.MANUAL_SOURCE.value,
                    revision = 1L,
                ),
            )
        }
        container.preferences.setModules(
            app.openhealth.core.preferences.HealthModule.entries.toSet(),
        )
        container.preferences.markOnboardingComplete(1)
        viewModel.refresh()
        waitFor("profile") { viewModel.state.value.vitals != null }
    }

    private fun waitFor(what: String, timeoutMillis: Long = 30_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(25)
        }
        assertTrue(condition(), "timed out waiting for $what")
    }

    private class FakeHealthBus : HealthBus {
        var readings: List<HealthReading> = emptyList()

        override fun isAvailable(): Boolean = true

        override suspend fun grantedPermissions(): Set<String> = setOf("health.read")

        override suspend fun read(
            type: String,
            fromEpochMillis: Long,
            toEpochMillis: Long,
        ): List<HealthReading> = readings.filter { it.type == type }

        override suspend fun write(readings: List<HealthReading>) = Unit
    }

    private companion object {
        const val TEST_DATABASE = "activity-exit.db"
        const val TEST_VAULT = "openhealth.vault.b8-test"
    }
}
