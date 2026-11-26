package app.openhealth.android

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.openhealth.android.wear.WatchApply
import app.openhealth.android.wear.WatchKind
import app.openhealth.core.observation.ObservationType
import app.openhealth.core.preferences.HealthModule
import app.openhealth.core.watch.WatchCommand
import app.openhealth.core.watch.WatchCommandType
import app.openhealth.data.db.Profile
import app.openhealth.data.db.Session_exercise
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * B14's round trip on a real encrypted store: a watch start, a queued set applied late,
 * an idempotent replay, a heart-rate sample and a finish (`BUILD-ROADMAP.md` §B14;
 * `docs/adr/0019`). It runs on the phone emulator because it uses the phone's database;
 * the Wear transport itself is exercised by the composable and store tests.
 */
@RunWith(AndroidJUnit4::class)
class WearExitTest {
    private lateinit var context: Context
    private lateinit var container: AppContainer

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(TEST_DATABASE)
        context.getSharedPreferences(TEST_VAULT, Context.MODE_PRIVATE).edit().clear().commit()
        container = AppContainer(
            context,
            databaseName = TEST_DATABASE,
            vaultPreferences = TEST_VAULT,
        )
        container.watchApplied.clear()
        seedProfile()
    }

    @After
    fun tearDown() {
        container.restTimer.skip()
        container.close()
        context.deleteDatabase(TEST_DATABASE)
        context.getSharedPreferences(TEST_VAULT, Context.MODE_PRIVATE).edit().clear().commit()
        container.watchApplied.clear()
    }

    @Test
    fun aQueuedWatchSetIsFiledOnceWithWristProvenanceAndTheDayRebuilds() {
        val now = container.clock.now().toEpochMilliseconds()
        val exercise = container.store.training.exercises()
            .first { it.primary_muscles.contains("quadriceps") }

        val start = WatchCommand(
            id = "watch-start-1",
            type = WatchCommandType.START_SESSION,
            happenedAtMillis = now - 10_000,
        )
        assertEquals(WatchApply.APPLIED, container.watchCommands.apply(start, WatchKind.WEAR))
        val session = assertNotNull(container.store.training.openSession())
        container.store.training.upsertSessionExercise(
            Session_exercise(
                id = "watch-entry",
                session_id = session.id,
                exercise_id = exercise.id,
                position = 0,
                notes = null,
                group_index = null,
                target_sets = 3,
                target_reps = 8,
                target_reps_high = 12,
                target_weight_kg = 80.0,
                progression = "linear",
                rest_seconds = 120,
            ),
        )

        val snapshot = container.watchSnapshots.build()
        assertEquals(session.id, snapshot.session?.id)
        assertEquals(1, snapshot.session?.exercises?.size)
        assertEquals("wear", snapshot.session?.sourceId)

        val setAt = now - 60_000
        val log = WatchCommand(
            id = "watch-set-1",
            type = WatchCommandType.LOG_SET,
            happenedAtMillis = setAt,
            sessionId = session.id,
            exerciseId = exercise.id,
            weightKg = 82.5,
            reps = 6,
        )
        assertEquals(WatchApply.APPLIED, container.watchCommands.apply(log, WatchKind.WEAR))
        assertEquals(
            WatchApply.DUPLICATE,
            container.watchCommands.apply(log, WatchKind.WEAR),
            "a replayed Data Layer message must write one set",
        )
        val set = container.store.training.setsForSession(session.id).single()
        assertEquals(82.5, set.weight_kg)
        assertEquals(6, set.reps)
        assertEquals(setAt, set.happened_at)
        assertEquals("wear", set.device)
        assertEquals(AppContainer.WATCH_SOURCE.value, set.source_id)
        assertTrue(set.recorded_at >= set.happened_at)

        val after = container.watchSnapshots.build()
        assertTrue("watch-set-1" in after.appliedCommandIds)
        assertEquals(1, after.session?.completedSetCount)
        val day = kotlinx.datetime.LocalDate.parse(session.local_date)

        val heart = WatchCommand(
            id = "watch-hr-1",
            type = WatchCommandType.HEART_RATE,
            happenedAtMillis = now - 5_000,
            sessionId = session.id,
            heartRateBpm = 72,
        )
        assertEquals(WatchApply.APPLIED, container.watchCommands.apply(heart, WatchKind.CONNECT_IQ))
        val observation = assertNotNull(
            container.store.observations.latest(ObservationType.HEART_RATE),
        )
        assertEquals(72.0, observation.value.value)
        assertEquals(AppContainer.CONNECT_IQ_SOURCE.value, observation.provenance.sourceId.value)
        assertEquals("connect-iq", observation.provenance.device)
        assertEquals(
            app.openhealth.core.observation.MeasurementMethod.WATCH_OPTICAL,
            observation.provenance.method,
        )

        val finish = WatchCommand(
            id = "watch-finish-1",
            type = WatchCommandType.FINISH_SESSION,
            happenedAtMillis = now,
            sessionId = session.id,
        )
        assertEquals(WatchApply.APPLIED, container.watchCommands.apply(finish, WatchKind.WEAR))
        assertNull(container.store.training.openSession())
        val rollup = assertNotNull(
            container.store.rollups.forDayAndType(day, ObservationType.TRAINING_SETS),
            "finishing a watch session rebuilds the day's training rollup",
        )
        assertTrue(rollup.value.value > 0.0)
    }

    @Test
    fun aMalformedOrFutureCommandNeverWritesAValue() {
        val now = container.clock.now().toEpochMilliseconds()
        val bad = WatchCommand(
            id = "bad",
            type = WatchCommandType.LOG_SET,
            happenedAtMillis = now,
            sessionId = "missing",
            exerciseId = "missing",
            weightKg = -1.0,
            reps = 5,
        )
        assertEquals(WatchApply.REJECTED, container.watchCommands.apply(bad, WatchKind.WEAR))
        assertNull(container.store.training.openSession())
        assertEquals(0, container.store.observations.count())
    }

    private fun seedProfile() {
        val now = container.clock.now().toEpochMilliseconds()
        runBlocking {
            container.store.profile.upsert(
                Profile(
                    id = "profile-wear",
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
                    intake_tier = 1,
                    created_at = now,
                    updated_at = now,
                    source_id = AppContainer.MANUAL_SOURCE.value,
                    revision = 1,
                ),
            )
            container.preferences.setModules(HealthModule.entries.toSet())
        }
    }

    companion object {
        private const val TEST_DATABASE = "wear-exit-test.db"
        private const val TEST_VAULT = "wear-exit-vault"
    }
}
