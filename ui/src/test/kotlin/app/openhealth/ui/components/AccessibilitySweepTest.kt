package app.openhealth.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onParent
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.openhealth.ui.icons.OhIcons
import app.openhealth.ui.screens.common.OhPageHeader
import app.openhealth.ui.theme.OhTheme
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The B10 accessibility sweep's automatable half (`DESIGN.md` §11, §4.3):
 *
 * - the tab bar keeps all five names at 200 % text even when it drops the labels;
 * - the bar keeps a 44 dp target at both scales;
 * - a page title is a heading, so TalkBack can jump by heading;
 * - a toast is a polite live region, so its message is announced without stealing focus;
 * - a segmented control grows to the larger line at 200 % rather than clipping it.
 *
 * The parts that need a person (TalkBack order, colour-blind reading of a chart, reduced
 * motion feel) are on the B10 QA sweep instead; these are the ones a machine can hold.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-mdpi")
class AccessibilitySweepTest {
    @get:Rule
    val compose = createComposeRule()

    private val tabs = listOf(
        OhTabItem("Today", OhIcons.today),
        OhTabItem("Vitals", OhIcons.vitals),
        OhTabItem("Train", OhIcons.train, raised = true),
        OhTabItem("Nutrition", OhIcons.nutrition),
        OhTabItem("Stats", OhIcons.stats),
    )

    private fun tabBar(scale: Float) {
        compose.setContent {
            val density = LocalDensity.current
            OhTheme(dark = true) {
                CompositionLocalProvider(
                    LocalDensity provides Density(density.density, fontScale = scale),
                ) {
                    OhTabBar(
                        tabs = tabs,
                        selectedIndex = 0,
                        onSelect = {},
                        modifier = Modifier.testTag(TAB),
                    )
                }
            }
        }
    }

    @Test
    fun everyTabKeepsItsNameAtNormalScale() {
        tabBar(1f)
        listOf("Today", "Vitals", "Train", "Nutrition", "Stats").forEach { name ->
            compose.onNodeWithContentDescription(name).assertIsDisplayed()
        }
        compose.onNodeWithText("Nutrition").assertIsDisplayed()
    }

    @Test
    fun everyTabKeepsItsNameAtTwoHundredPercentEvenWhenTheLabelDrops() {
        tabBar(2f)
        // The label is no longer drawn, but the accessible name is still there.
        listOf("Today", "Vitals", "Train", "Nutrition", "Stats").forEach { name ->
            compose.onNodeWithContentDescription(name).assertIsDisplayed()
        }
        compose.onAllNodesWithContentDescription("Nutrition").assertCountEquals(1)
    }

    @Test
    fun theTabTargetIsStillFortyFourAtTwoHundredPercent() {
        tabBar(2f)
        compose.onNodeWithContentDescription("Vitals")
            .assertHeightIsAtLeast(44.dp)
        val bounds = compose.onNodeWithContentDescription("Vitals").getUnclippedBoundsInRoot()
        assertTrue(
            (bounds.right - bounds.left) >= 44.dp,
            "the tab cell narrowed below a touch target: $bounds",
        )
    }

    @Test
    fun thePageTitleIsAHeading() {
        compose.setContent {
            OhTheme(dark = true) {
                Box { OhPageHeader(title = "Vitals", onBack = {}) }
            }
        }
        compose.onNodeWithText("Vitals").assertIsDisplayed()
        val node = compose.onNodeWithText("Vitals").fetchSemanticsNode()
        assertTrue(
            node.config.contains(SemanticsProperties.Heading),
            "a page title must be a heading (DESIGN.md §11.2)",
        )
    }

    @Test
    fun aToastIsAPoliteLiveRegion() {
        compose.setContent {
            OhTheme(dark = true) {
                OhToast(message = "Saved", onDismiss = {})
            }
        }
        // The row that carries the live region is the text's parent; the announcement
        // belongs to the whole toast, not to one word of it.
        val node = compose.onNodeWithText("Saved").onParent()
            .fetchSemanticsNode()
        assertTrue(
            node.config.contains(SemanticsProperties.LiveRegion),
            "the toast must declare a live region",
        )
        assertEquals(
            androidx.compose.ui.semantics.LiveRegionMode.Polite,
            node.config[SemanticsProperties.LiveRegion],
        )
    }

    @Test
    fun theSegmentedControlGrowsWithTheTextInsteadOfClippingIt() {
        compose.setContent {
            val density = LocalDensity.current
            OhTheme(dark = true) {
                CompositionLocalProvider(
                    LocalDensity provides Density(density.density, fontScale = 2f),
                ) {
                    OhSegmentedControl(
                        options = listOf("System", "Dark", "Light"),
                        selectedIndex = 1,
                        onSelect = {},
                        modifier = Modifier.testTag(SEGMENTED),
                    )
                }
            }
        }
        compose.onNodeWithTag(SEGMENTED).assertHeightIsAtLeast(44.dp)
        // Every segment is still reachable: the control scrolls, it does not clip.
        compose.onNodeWithText("Light").assertExists()
        compose.onNodeWithText("Dark").performSemanticsAction(SemanticsActions.OnClick)
    }

    private companion object {
        const val TAB = "tab-bar"
        const val SEGMENTED = "segmented"
    }
}
