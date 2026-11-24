package app.openhealth.ui.screens.roots

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.openhealth.ui.theme.OhTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A module that is switched off hides its surfaces (`pr.md` §2.4, §6.21). The root is the
 * one place it could leak back in, so it shows a calm explanation and a way to turn the
 * module on, and no logging action at all (`DESIGN.md` §7.6).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ModuleOffRootTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun vitalsOffOffersSettingsAndNoWeightLog() {
        var opened = 0
        compose.setContent {
            OhTheme(dark = true) {
                VitalsRoot(
                    onLogWeight = { opened++ },
                    moduleOff = true,
                    onOpenSettings = { opened++ },
                )
            }
        }
        compose.onNodeWithText("Vitals is off").assertExists()
        compose.onNodeWithText("Log weight").assertDoesNotExist()
        compose.onNodeWithText("Open settings").performClick()
        assertEquals(1, opened)
    }

    @Test
    fun aModuleThatIsOnKeepsItsOwnRoot() {
        compose.setContent {
            OhTheme(dark = true) {
                VitalsRoot(onLogWeight = {}, moduleOff = false)
            }
        }
        compose.onNodeWithText("No measurements yet").assertExists()
        compose.onNodeWithText("Vitals is off").assertDoesNotExist()
    }

    @Test
    fun nutritionOffDropsTheWaterAction() {
        compose.setContent {
            OhTheme(dark = true) {
                NutritionRoot(onLogWater = {}, moduleOff = true)
            }
        }
        compose.onNodeWithText("Nutrition is off").assertExists()
        compose.onNodeWithText("Log water").assertDoesNotExist()
    }

    @Test
    fun trainingOffDropsTheNoteAction() {
        compose.setContent {
            OhTheme(dark = true) {
                TrainRoot(onAddNote = {}, moduleOff = true)
            }
        }
        compose.onNodeWithText("Train is off").assertExists()
        compose.onNodeWithText("Add note").assertDoesNotExist()
    }
}
