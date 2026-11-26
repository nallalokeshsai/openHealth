package app.openhealth.android

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.openhealth.android.activity.PreferencesGpsStore
import app.openhealth.android.stats.StatsPresenter
import app.openhealth.android.today.TodayPresenter
import app.openhealth.android.training.TrainingPresenter
import app.openhealth.core.activity.ActivitySource
import app.openhealth.core.activity.ActivityType
import app.openhealth.core.activity.GpsFix
import app.openhealth.core.activity.GpsRecorder
import app.openhealth.core.activity.GpsSnapshotCodec
import app.openhealth.core.activity.YogaLibrary
import app.openhealth.core.activity.YogaLoad
import app.openhealth.core.body.Muscle
import app.openhealth.core.observation.ObservationType
import app.openhealth.data.db.Profile
import app.openhealth.integrations.activity.GpxReader
import app.openhealth.integrations.activity.GpxWriter
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toLocalDateTime
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * B11's exit scenarios on the device (`BUILD-ROADMAP.md` §B11): a 30-minute GPS recording
 * that survives a process death and exports valid GPX, a Strava bulk export with correct
 * totals and one winner per day, and a yoga session that reaches the recovery map and the
 * energy card. The recordings are built through the same pure recorder the service uses,
 * so the test does not wait thirty minutes or depend on the sky.
 */
@RunWith(AndroidJUnit4::class)
class MovementExitTest {
    private lateinit var context: Context
    private lateinit var container: AppContainer

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(TEST_DATABASE)
        context.getSharedPreferences(TEST_VAULT, Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("gps-recording", Context.MODE_PRIVATE).edit().clear().commit()
        container =
            AppContainer(context, databaseName = TEST_DATABASE, vaultPreferences = TEST_VAULT)
        seedProfile()
    }

    @After
    fun tearDown() {
        Thread.sleep(300)
        container.close()
        context.deleteDatabase(TEST_DATABASE)
        context.getSharedPreferences(TEST_VAULT, Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("gps-recording", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun aThirtyMinuteRecordingSurvivesProcessDeathAndExportsValidGpx() {
        val zone = container.zone
        val start = container.clock.now().minus(kotlin.time.Duration.parse("31m"))
        val date = start.toLocalDateTime(zone).date
        var snapshot = GpsRecorder.start(
            id = container.ids.newId(),
            type = ActivityType.RUN,
            startedAt = start,
            zoneId = zone.id,
            localDate = date,
        )
        val startMillis = start.toEpochMilliseconds()
        // 181 fixes, every ten seconds, about 30 minutes of a steady northward run.
        repeat(181) { index ->
            snapshot = GpsRecorder.addFix(
                snapshot,
                GpsFix(
                    latitude = 52.5200 + index * 0.0003,
                    longitude = 13.4050,
                    timeEpochMillis = startMillis + index * 10_000L,
                    accuracyM = 5.0,
                    elevationM = 34.0 + index * 0.1,
                    heartRate = 145.0 + (index % 10),
                ),
            )
        }
        assertEquals(181, snapshot.points.size)

        // The service's persisted snapshot, then a second store instance after the
        // process death: this is exactly what START_STICKY reads back.
        PreferencesGpsStore(context).save(GpsSnapshotCodec.encode(snapshot))
        val recovered = PreferencesGpsStore(context).load()?.let {
            GpsSnapshotCodec.decode(it)
        }
        assertNotNull(recovered)
        assertEquals(1800, recovered.points.last().elapsedSeconds)
        assertTrue(recovered.distanceM > 4_000.0, "distance ${recovered.distanceM}")

        val record = GpsRecorder.finish(recovered, startMillis + 1_800_000L)
        val id = container.activityLog.finishRecording(record)
        assertNotNull(id)
        val stored = container.store.activities.byIdWithPoints(id)
        assertNotNull(stored)
        assertEquals(ActivitySource.RECORDED, stored.source)
        assertEquals(181, stored.points.size)
        assertEquals(1_800, stored.durationSeconds)

        val bytes = container.activityLog.exportGpx(id)
        assertNotNull(bytes)
        val xml = bytes.toString(Charsets.UTF_8)
        assertTrue(xml.contains("<gpx version=\"1.1\""), "a GPX 1.1 document")
        assertTrue(xml.contains("<trkpt"), "track points")
        val parsed = GpxReader.read(bytes)
        assertEquals(181, parsed.points.size)
        assertEquals(1_800, parsed.points.last().elapsedSeconds)
        assertEquals(ActivityType.RUN, parsed.type)
        assertEquals(record.distanceM, parsed.distanceM, 5.0)
        assertEquals(145.0, parsed.points.first().heartRate)
    }

    @Test
    fun aStravaArchiveImportsCorrectTotalsAndOneSourceWinsTheDay() {
        val zip = ByteArrayOutputStream()
        ZipOutputStream(zip).use { output ->
            output.putNextEntry(ZipEntry("activities.csv"))
            output.write(
                """
                Activity ID,Activity Date,Activity Name,Activity Type,Distance,Calories
                1001,"Nov 24, 2026, 8:00:00 AM","Morning run",Run,5000,320
                1002,"Nov 24, 2026, 6:00:00 PM","Evening run",Run,3000,190
                """.trimIndent().toByteArray(),
            )
            output.closeEntry()
            output.putNextEntry(ZipEntry("activities/1001.gpx"))
            output.write(gpx("Morning run", 5_000.0).toByteArray())
            output.closeEntry()
            output.putNextEntry(ZipEntry("activities/1002.gpx"))
            output.write(gpx("Evening run", 3_000.0).toByteArray())
            output.closeEntry()
        }
        val outcome = container.activityLog.import("export.zip", zip.toByteArray())
        assertEquals(2, outcome.imported)
        val imported = container.store.activities.all()
        assertEquals(2, imported.size)
        assertEquals(8_000.0, imported.sumOf { it.distanceM }, 200.0)
        assertEquals(ActivitySource.IMPORTED, imported.first().source)

        // A different source's 2 km on the same day must not be added to the total.
        val other = container.activityLog.import(
            "walk.gpx",
            gpx("Walk", 2_000.0).toByteArray(),
        )
        assertEquals(1, other.imported)
        val date = imported.first().localDate
        container.store.engine.rebuildDay(date)
        val rollup = assertNotNull(
            container.store.rollups.forDayAndType(date, ObservationType.DISTANCE),
        )
        assertEquals(8_000.0, rollup.value.value, 200.0)
        assertEquals("strava-archive", rollup.sourceId?.value)
    }

    @Test
    fun aYogaSessionFeedsRecoveryAndTheEnergyCard() {
        val preferences = container.preferences.snapshot()
        val profile = assertNotNull(container.store.profile.active())
        val today = container.today
        val todayPresenter = TodayPresenter(
            context = context,
            store = container.store,
            clock = container.clock,
            zone = container.zone,
        )
        val before = todayPresenter.model(preferences, profile).energy

        val flow = YogaLibrary.flow("beginner-full-body")!!
        val start = container.clock.now().minus(kotlin.time.Duration.parse("30m"))
        val id = container.activityLog.saveYoga(
            flowId = flow.id,
            startedAt = start,
            endedAt = container.clock.now().minus(kotlin.time.Duration.parse("5m")),
            muscles = YogaLoad.of(flow),
        )
        assertNotNull(id)
        assertEquals(1, container.store.activities.forDay(today).size)
        val active = assertNotNull(
            container.store.rollups.forDayAndType(today, ObservationType.ACTIVE_MINUTES),
        )
        assertTrue(active.value.value >= 25.0)

        val training = TrainingPresenter(
            store = container.store,
            clock = container.clock,
            zone = container.zone,
            restTimer = container.restTimer,
            ids = container.ids,
            manualSource = AppContainer.MANUAL_SOURCE,
            appVersion = container.versionName,
            freestyleTitle = "Freestyle",
            pastTitle = "Past workout",
        )
        val train = training.train(preferences, profile)
        val quad = train.recovery.shade(Muscle.QUADS)
        assertTrue(
            quad is app.openhealth.ui.body.MuscleShade.Recovery &&
                quad.step != app.openhealth.core.formula.RecoveryStep.UNTRAINED,
            "the yoga pose's muscles are shaded, was $quad",
        )

        val after = assertNotNull(todayPresenter.model(preferences, profile).energy)
        val beforeOut = before?.outHighKcal ?: 0.0
        assertTrue(
            after.outHighKcal > beforeOut,
            "the energy card includes the yoga session: $beforeOut -> ${after.outHighKcal}",
        )

        val stats = StatsPresenter(
            context = context,
            store = container.store,
            clock = container.clock,
            zone = container.zone,
        ).model(app.openhealth.ui.screens.stats.StatsSegment.TRAINING, preferences)
        assertTrue(
            stats.cards.any { it.chart == app.openhealth.ui.screens.stats.ChartId.TRAINING_LOAD },
            "the training-load card exists",
        )
    }

    private fun gpx(name: String, metres: Double): String {
        val start = Instant.parse("2026-11-24T08:00:00Z")
        val record = app.openhealth.core.activity.ActivityRecord(
            id = name,
            type = ActivityType.RUN,
            source = ActivitySource.IMPORTED,
            startedAt = start,
            endedAt = start.plus(kotlin.time.Duration.parse("20m")),
            localDate = LocalDate(2026, 11, 24),
            zoneId = container.zone.id,
            distanceM = metres,
            name = name,
            points = (0..10).map { index ->
                app.openhealth.core.activity.ActivityPoint(
                    latitude = 52.5200 + index * 0.0004,
                    longitude = 13.4050,
                    elapsedSeconds = index * 120L,
                )
            },
        )
        return GpxWriter.write(record)
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
    }

    private companion object {
        const val TEST_DATABASE = "movement-exit.db"
        const val TEST_VAULT = "openhealth.vault.b11-test"
    }
}
