package app.openhealth.ui.screenshots

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.openhealth.ui.components.OhChip
import app.openhealth.ui.components.OhIconButton
import app.openhealth.ui.components.OhInsetGroup
import app.openhealth.ui.components.OhRow
import app.openhealth.ui.components.OhSwitch
import app.openhealth.ui.components.OhTabBar
import app.openhealth.ui.components.OhTabItem
import app.openhealth.ui.icons.OhIcons
import app.openhealth.ui.theme.OhTheme
import app.openhealth.ui.theme.ohDims
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The accessibility half of the `DESIGN.md` §14.5 gate, asserted rather than eyeballed:
 * one node per row, 44 dp targets, names on icon-only controls and real selected/checked
 * states. Runs with Robolectric, so it is part of `check`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-mdpi")
class ComponentSemanticsTest {

    @get:Rule
    val compose = createComposeRule()

    private fun setContent(content: @Composable () -> Unit) {
        compose.setContent { OhTheme(dark = true) { content() } }
    }

    @Test
    fun aRowIsOneAccessibilityNode() {
        setContent {
            OhInsetGroup {
                OhRow(
                    title = "Resting heart rate",
                    subtitle = "Watch · 07:42",
                    value = "54 bpm",
                    onClick = {},
                )
            }
        }
        // Title, subtitle and value are read as one item; the chevron is not a second node.
        compose.onAllNodes(hasClickAction()).assertCountEquals(1)
        compose.onNodeWithText("Resting heart rate").assertHasClickAction()
    }

    @Test
    fun aRowWithoutAnActionIsNotAButton() {
        setContent {
            OhInsetGroup {
                OhRow(title = "Weight", value = "82.4 kg")
            }
        }
        compose.onNodeWithText("Weight").assertHasNoClickAction()
    }

    @Test
    fun anIconButtonHasANameAndAFortyFourTarget() {
        setContent {
            OhIconButton(icon = OhIcons.settings, contentDescription = "Settings", onClick = {})
        }
        compose.onNodeWithContentDescription("Settings")
            .assertWidthIsAtLeast(ohDims.touchTarget)
            .assertHeightIsAtLeast(ohDims.touchTarget)
            .assertHasClickAction()
    }

    @Test
    fun anIconButtonFiresOnce() {
        var clicks = 0
        setContent {
            OhIconButton(icon = OhIcons.add, contentDescription = "Add", onClick = { clicks++ })
        }
        compose.onNodeWithContentDescription("Add").performClick()
        compose.onNodeWithContentDescription("Add").performClick()
        assert(clicks == 2) { "Two taps must be two clicks, not four (no double-fired gesture)." }
    }

    @Test
    fun aChipHasAFortyFourTargetAndAChosenState() {
        setContent {
            Column {
                OhChip(text = "All", selected = true, onClick = {})
                OhChip(text = "Manual", selected = false, onClick = {})
            }
        }
        compose.onNodeWithText("All")
            .assertHeightIsAtLeast(ohDims.touchTarget)
            .assertHasClickAction()
            .assertIsSelected()
        compose.onNodeWithText("Manual").assertIsNotSelected()
    }

    @Test
    fun theTabBarExposesFiveDestinationsAndTheActiveOne() {
        setContent {
            OhTabBar(
                tabs = listOf(
                    OhTabItem(label = "Today", icon = OhIcons.today),
                    OhTabItem(label = "Vitals", icon = OhIcons.vitals),
                    OhTabItem(label = "Train", icon = OhIcons.train, raised = true),
                    OhTabItem(label = "Nutrition", icon = OhIcons.nutrition),
                    OhTabItem(label = "Stats", icon = OhIcons.stats),
                ),
                selectedIndex = 0,
                onSelect = {},
            )
        }
        compose.onNodeWithText("Today").assertIsSelected()
        compose.onNodeWithContentDescription("Train").assertHasClickAction().assertIsNotSelected()
        compose.onAllNodes(hasClickAction()).assertCountEquals(5)
    }

    @Test
    fun theTrainDiscAnnouncesItsSelection() {
        setContent {
            OhTabBar(
                tabs = listOf(
                    OhTabItem(label = "Today", icon = OhIcons.today),
                    OhTabItem(label = "Vitals", icon = OhIcons.vitals),
                    OhTabItem(label = "Train", icon = OhIcons.train, raised = true),
                    OhTabItem(label = "Nutrition", icon = OhIcons.nutrition),
                    OhTabItem(label = "Stats", icon = OhIcons.stats),
                ),
                selectedIndex = 2,
                onSelect = {},
            )
        }
        compose.onNodeWithContentDescription("Train").assertIsSelected()
    }

    @Test
    fun aSwitchAnnouncesItsState() {
        setContent {
            OhSwitch(checked = true, onCheckedChange = {}, contentDescription = "Module on")
        }
        compose.onNodeWithContentDescription("Module on").assertIsOn()
        compose.onNodeWithContentDescription(
            "Module on",
        ).assertContentDescriptionEquals("Module on")
    }

    @Test
    fun anOffSwitchAnnouncesOff() {
        setContent {
            OhSwitch(checked = false, onCheckedChange = {}, contentDescription = "Module off")
        }
        compose.onNodeWithContentDescription("Module off").assertIsOff()
    }
}
