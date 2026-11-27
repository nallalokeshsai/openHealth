package app.openhealth.wear

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The Wear APK opens on the workout screen without a phone, a crash or a permission
 * dialog (`docs/adr/0019`). The screen itself is covered by the JVM composable test; this
 * is the real Wear process and the real Data Layer/Health Services wiring.
 */
@RunWith(AndroidJUnit4::class)
class WearSmokeTest {
    @get:Rule
    val compose = createAndroidComposeRule<WearMainActivity>()

    @Test
    fun theWatchOpensTheWorkoutOrSessionScreen() {
        val workout = compose.onAllNodesWithText("Workout").fetchSemanticsNodes()
        val logged = compose.onAllNodesWithText("Log set").fetchSemanticsNodes()
        assertTrue(workout.isNotEmpty() || logged.isNotEmpty())
    }
}
