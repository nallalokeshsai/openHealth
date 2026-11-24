package app.openhealth.android

import android.Manifest
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.openhealth.android.ui.AppViewModel
import app.openhealth.android.ui.OpenHealthRoot
import app.openhealth.core.preferences.HealthModule
import app.openhealth.core.preferences.UnitSystem
import app.openhealth.data.db.Profile
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * B5's exit scenarios on the phone (`BUILD-ROADMAP.md` §B5). Maestro is not installed on
 * the build machine, so these Compose instrumented tests drive the real screens, the real
 * state holder and the encrypted store; the build log records the substitution.
 */
@RunWith(AndroidJUnit4::class)
class TrainingExitTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var context: Context
    private lateinit var container: AppContainer
    private lateinit var viewModel: AppViewModel

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            runCatching {
                InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
                    context.packageName,
                    Manifest.permission.POST_NOTIFICATIONS,
                )
            }
        }
        context.deleteDatabase(TEST_DATABASE)
        context.getSharedPreferences(TEST_VAULT, Context.MODE_PRIVATE).edit().clear().commit()
        container = AppContainer(
            context,
            databaseName = TEST_DATABASE,
            vaultPreferences = TEST_VAULT,
        )
        viewModel = AppViewModel(container, UnitSystem.METRIC)
    }

    @After
    fun tearDown() {
        viewModel.restSkip()
        viewModel.stop()
        Thread.sleep(300)
        container.close()
        context.deleteDatabase(TEST_DATABASE)
        context.getSharedPreferences(TEST_VAULT, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun aSetIsLoggedInUnderTwoSecondsWithTheFieldsPrefilled() {
        seedProfile()
        compose.setContent { OpenHealthRoot(container, viewModel) }
        waitFor("loaded") { viewModel.state.value.loaded }
        waitFor("profile") { viewModel.state.value.onboardingComplete }

        val exerciseId = container.store.training.exercises()
            .first { it.primary_muscles.contains("quadriceps") }.id

        // A previous session supplies the prefill. It is written through the repository,
        // so the timed action below is the one being measured.
        seedPreviousSession(exerciseId)

        compose.onNodeWithContentDescription("Train").performClick()
        compose.onNodeWithText("Start").performClick()
        waitFor("second live") { viewModel.state.value.live != null }
        val second = viewModel.state.value.live!!.sessionId
        viewModel.addLiveExercise(second, exerciseId)
        waitFor("second exercise") {
            viewModel.state.value.live?.exercises?.isNotEmpty() == true
        }
        val active = viewModel.state.value.live!!.exercises.first()
            .sets.first { !it.done }
        assertEquals("80", active.weight, "the weight is prefilled from the last session")
        assertEquals("8", active.reps, "the reps are prefilled from the plan")

        val started = System.nanoTime()
        compose.onNodeWithContentDescription("Complete set").performClick()
        waitFor("set stored") { container.store.training.countSetsForSession(second) == 1L }
        val millis = (System.nanoTime() - started) / 1_000_000.0
        println("one-tap set: %.0f ms".format(millis))
        assertTrue(millis < 2_000.0, "one-tap set took %.0f ms".format(millis))
        viewModel.restSkip()
    }

    @Test
    fun aPastWorkoutIsFiledOnItsDayAndKeepsAShortDuration() {
        seedProfile()
        compose.setContent { OpenHealthRoot(container, viewModel) }
        waitFor("loaded") { viewModel.state.value.loaded }
        waitFor("profile") { viewModel.state.value.onboardingComplete }
        val exerciseId = container.store.training.exercises()
            .first { it.primary_muscles.contains("quadriceps") }.id
        val date = container.today.plus(kotlinx.datetime.DatePeriod(days = -1))

        viewModel.startPastSession(date)
        waitFor("past live") { viewModel.state.value.live?.backdated == true }
        val id = viewModel.state.value.live!!.sessionId
        assertEquals(date, viewModel.state.value.live!!.localDate)
        viewModel.addLiveExercise(id, exerciseId)
        waitFor("past exercise") {
            viewModel.state.value.live?.exercises?.isNotEmpty() == true
        }
        viewModel.logSet(
            id,
            app.openhealth.ui.screens.train.LoggedSet(
                exerciseId,
                "60",
                "5",
                null,
                app.openhealth.ui.screens.train.LiveSetKind.WORK,
                null,
            ),
        )
        waitFor("past set") { container.store.training.countSetsForSession(id) == 1L }
        viewModel.finishLive(id)
        waitFor("past summary") { viewModel.state.value.summary != null }
        val summary = viewModel.state.value.summary!!
        assertEquals(date, summary.date)
        assertTrue(summary.duration != "0:00", "the backfilled session has a duration")
        assertTrue(
            container.store.rollups.forDayAndType(
                date,
                app.openhealth.core.observation.ObservationType.TRAINING_VOLUME,
            ) != null,
            "the past day carries the training volume",
        )
    }

    private fun seedPreviousSession(exerciseId: String) {
        val now = container.clock.now()
        val happened = now - 2.hours
        val localDay = happened.toLocalDateTime(container.zone).date
        val sessionId = "previous-session"
        val entryId = "previous-entry"
        runBlocking {
            container.store.training.upsertSession(
                app.openhealth.data.db.Session(
                    id = sessionId,
                    started_at = (now - 2.hours).toEpochMilliseconds(),
                    ended_at = (now - 1.hours).toEpochMilliseconds(),
                    local_date = localDay.toString(),
                    zone_id = container.zone.id,
                    happened_at = (now - 2.hours).toEpochMilliseconds(),
                    recorded_at = (now - 1.hours).toEpochMilliseconds(),
                    routine_id = null,
                    programme_id = null,
                    notes = null,
                    source_id = AppContainer.MANUAL_SOURCE.value,
                    idempotency_key = "previous-session",
                    observation_id = null,
                    raw_record_id = null,
                    device = "manual",
                    app_version = "test",
                    revision = 1L,
                    updated_at = (now - 1.hours).toEpochMilliseconds(),
                    title = "Previous",
                    feeling = null,
                    perceived_effort = null,
                ),
            )
            container.store.training.upsertSessionExercise(
                app.openhealth.data.db.Session_exercise(
                    id = entryId,
                    session_id = sessionId,
                    exercise_id = exerciseId,
                    position = 0L,
                    notes = null,
                    group_index = null,
                    target_sets = 3L,
                    target_reps = 8L,
                    target_reps_high = 12L,
                    target_weight_kg = 80.0,
                    progression = "linear",
                    rest_seconds = 120L,
                ),
            )
            container.store.training.upsertSet(
                app.openhealth.data.db.Work_set(
                    id = "previous-set",
                    session_id = sessionId,
                    session_exercise_id = entryId,
                    exercise_id = exerciseId,
                    position = 0L,
                    weight_kg = 80.0,
                    reps = 5L,
                    rir = 2.0,
                    rpe = null,
                    is_warmup = false,
                    is_backoff = false,
                    is_failure = false,
                    is_bodyweight = false,
                    tempo = null,
                    rest_seconds = 120L,
                    notes = null,
                    completed_at = (now - 1.hours).toEpochMilliseconds(),
                    happened_at = (now - 1.hours).toEpochMilliseconds(),
                    recorded_at = (now - 1.hours).toEpochMilliseconds(),
                    source_id = AppContainer.MANUAL_SOURCE.value,
                    idempotency_key = "previous-set",
                    observation_id = null,
                    raw_record_id = null,
                    device = "manual",
                    app_version = "test",
                    revision = 1L,
                    is_drop = false,
                    is_assisted = false,
                    assistance_kg = null,
                    band_kg = null,
                ),
            )
        }
        viewModel.refresh()
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
            container.preferences.setModules(HealthModule.entries.toSet())
            container.preferences.markOnboardingComplete(1)
        }
        viewModel.refresh()
    }

    private fun waitFor(what: String, timeoutMillis: Long = 30_000, condition: () -> Boolean) {
        val reached = runCatching {
            compose.waitUntil(timeoutMillis = timeoutMillis, condition = condition)
        }.isSuccess
        assertTrue(reached, "timed out waiting for $what")
    }

    private companion object {
        const val TEST_DATABASE = "training-exit.db"
        const val TEST_VAULT = "training-exit.vault"
    }
}
