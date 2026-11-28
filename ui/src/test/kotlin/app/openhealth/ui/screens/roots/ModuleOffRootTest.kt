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
    fun anOffModuleOffersSettingsAndNoAction() {
        var opened = 0
        compose.setContent {
            OhTheme(dark = true) {
                ModuleOffRoot(name = "Vitals", onOpenSettings = { opened++ })
            }
        }
        compose.onNodeWithText("Vitals is off").assertExists()
        compose.onNodeWithText("Open settings").performClick()
        assertEquals(1, opened)
    }

    @Test
    fun theExplanationSaysTheDataIsKept() {
        compose.setContent {
            OhTheme(dark = true) {
                ModuleOffRoot(name = "Nutrition", onOpenSettings = {})
            }
        }
        compose.onNodeWithText(
            "Its cards are hidden and your data is kept. Turn it on in Settings, under Modules.",
        ).assertExists()
    }
}
