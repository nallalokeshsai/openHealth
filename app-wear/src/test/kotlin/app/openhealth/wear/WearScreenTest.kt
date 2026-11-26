package app.openhealth.wear

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.openhealth.core.watch.WatchExercise
import app.openhealth.core.watch.WatchLiveController
import app.openhealth.core.watch.WatchLiveState
import app.openhealth.core.watch.WatchSessionSummary
import app.openhealth.core.watch.WatchSet
import app.openhealth.core.watch.WatchSnapshot
import app.openhealth.ui.theme.OhTheme
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The watch screens render from a snapshot and ask the holder for one action at a time
 * (`DESIGN.md` §14.3). The test drives the stateless composable, so it never needs a
 * paired phone or the Wear Data Layer.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WearScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val now = 1_700_000_000_000L

    private fun state(withSession: Boolean = true): WatchLiveState {
        if (!withSession) {
            val empty = WatchSnapshot(
                generatedAtMillis = now,
                routines = listOf(app.openhealth.core.watch.WatchRoutine("r1", "Push day", 5)),
            )
            return WatchLiveController.onSnapshot(WatchLiveState(), empty, now, connected = true)
        }
        val snapshot = WatchSnapshot(
            generatedAtMillis = now,
            session = WatchSessionSummary(
                id = "s1",
                title = "Push",
                startedAtMillis = now - 600_000,
                exercises = listOf(
                    WatchExercise(
                        id = "e1",
                        name = "Bench press",
                        targetSets = 3,
                        targetReps = 8,
                        targetWeightKg = 80.0,
                        restSeconds = 120,
                        sets = listOf(WatchSet(80.0, 8, now - 60_000)),
                    ),
                ),
                currentExerciseIndex = 0,
                completedSetCount = 1,
            ),
            routines = listOf(app.openhealth.core.watch.WatchRoutine("r1", "Push day", 5)),
            weightUnit = "kg",
            weightIncrementKg = 2.5,
        )
        return WatchLiveController.onSnapshot(WatchLiveState(), snapshot, now, connected = true)
    }

    private fun content(
        state: WatchLiveState,
        onLog: () -> Unit = {},
        onStart: (String?) -> Unit = {},
    ) {
        compose.setContent {
            OhTheme(dark = true) {
                WearScreen(
                    state = state,
                    connected = true,
                    heartRateAllowed = false,
                    onStart = onStart,
                    onLogSet = onLog,
                    onFinish = {},
                    onWeight = {},
                    onReps = {},
                    onExtendRest = {},
                    onSkipRest = {},
                    onAllowHeartRate = {},
                )
            }
        }
    }

    @Test
    fun theSessionShowsTheExerciseTheSetAndTheWeight() {
        content(state())
        compose.onNodeWithText("Bench press").assertIsDisplayed()
        compose.onNodeWithText("Set 2 of 3", substring = true).assertIsDisplayed()
        compose.onNodeWithText("80.0 kg").assertIsDisplayed()
        compose.onNodeWithText("Log set").assertIsDisplayed()
    }

    @Test
    fun loggingASetCallsTheHolderOnce() {
        var logged = 0
        content(state(), onLog = { logged += 1 })
        compose.onNodeWithText("Log set").performClick()
        assertEquals(1, logged)
    }

    @Test
    fun aRoutineCanBeStartedFromTheWatchWhenNoSessionIsOpen() {
        var routine: String? = "not-called"
        content(state(withSession = false), onStart = { routine = it })
        compose.onNodeWithText("Push day").assertIsDisplayed()
        compose.onNodeWithText("Push day").performClick()
        assertEquals("r1", routine)
    }

    @Test
    fun anEmptyWatchOffersThePhoneInsteadOfAnEmptyShell() {
        content(WatchLiveState(nowMillis = now, connected = true))
        compose.onNodeWithText("Start a workout on your phone").assertIsDisplayed()
    }

    @Test
    fun aRunningRestShowsItsClockAndTheTwoActions() {
        val resting = WatchLiveController.logSet(state(), "c1", now).state
        content(resting)
        compose.onNodeWithText("Rest").assertIsDisplayed()
        compose.onNodeWithText("2:00").assertIsDisplayed()
        compose.onNodeWithText("+15 s").assertIsDisplayed()
        compose.onNodeWithText("Skip").assertIsDisplayed()
    }
}
