package app.openhealth.ui.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import app.openhealth.core.review.ReviewKind
import app.openhealth.ui.screens.review.ReviewAction
import app.openhealth.ui.screens.review.ReviewActionModel
import app.openhealth.ui.screens.review.ReviewModel
import app.openhealth.ui.screens.review.ReviewReadingModel
import app.openhealth.ui.screens.review.ReviewRowModel
import app.openhealth.ui.screens.review.ReviewScreen
import app.openhealth.ui.theme.OhTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The B10 review inbox in dark, light, 200 % text and RTL (`pr.md` §6.28,
 * `DESIGN.md` §14.5). The populated and empty states are both goldens because the empty
 * state is a sentence the person reads, not a blank page.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-mdpi")
class ReviewScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private enum class Variant(
        val dir: String,
        val dark: Boolean,
        val scale: Float,
        val rtl: Boolean,
    ) {
        Dark("dark", true, 1f, false),
        Light("light", false, 1f, false),
        Text200("text200", true, 2f, false),
        Rtl("rtl", true, 1f, true),
    }

    private fun shot(name: String, model: ReviewModel) {
        val variant = mutableStateOf(Variant.Dark)
        compose.setContent {
            val current = variant.value
            val density = LocalDensity.current
            OhTheme(dark = current.dark) {
                CompositionLocalProvider(
                    LocalDensity provides Density(density.density, fontScale = current.scale),
                    LocalLayoutDirection provides if (current.rtl) {
                        LayoutDirection.Rtl
                    } else {
                        LayoutDirection.Ltr
                    },
                ) {
                    Box(
                        modifier = Modifier
                            .testTag(SHOT)
                            .fillMaxSize()
                            .background(OhTheme.colors.bg),
                    ) {
                        ReviewScreen(model = model, onBack = {}, onAction = { _, _ -> })
                    }
                }
            }
        }
        Variant.entries.forEach { entry ->
            compose.runOnIdle { variant.value = entry }
            compose.waitForIdle()
            compose.onNodeWithTag(SHOT)
                .captureRoboImage("src/test/screenshots/${entry.dir}/$name.png")
        }
    }

    private val populated = ReviewModel(
        rows = listOf(
            ReviewRowModel(
                key = "disagree:STEPS:2026-11-25",
                kind = ReviewKind.DISAGREEMENT,
                title = "Two sources disagree on steps",
                subtitle = "Garmin says 8 412 and Fitbit says 6 108. Today shows Garmin.",
                dateLabel = "2026-11-25",
                readings = listOf(
                    ReviewReadingModel(
                        "Garmin",
                        "8 412 steps",
                        "08:12",
                        detail = null,
                        winner = true,
                    ),
                    ReviewReadingModel(
                        "Fitbit",
                        "6 108 steps",
                        "08:20",
                        detail = null,
                        winner = false,
                    ),
                ),
                explanation = null,
                actions = listOf(
                    ReviewActionModel(ReviewAction.KEEP, "Keep Garmin"),
                    ReviewActionModel(ReviewAction.EXCLUDE, "Leave Fitbit out of trends"),
                    ReviewActionModel(ReviewAction.EDIT, "Open the timeline"),
                    ReviewActionModel(ReviewAction.IGNORE, "Ignore this"),
                ),
            ),
            ReviewRowModel(
                key = "range:WEIGHT:2026-11-24",
                kind = ReviewKind.OUTSIDE_RANGE,
                title = "Weight is outside the plausible range",
                subtitle = "Scale recorded 512 kg in weight. The plausible range is 1 to 500 kg.",
                dateLabel = "2026-11-24",
                readings = listOf(
                    ReviewReadingModel("Scale", "512 kg", "07:02", detail = null, winner = false),
                ),
                explanation = "The range is a screen for entry mistakes, not a clinical " +
                    "judgement. Nothing is changed unless you choose to.",
                actions = listOf(
                    ReviewActionModel(
                        ReviewAction.EXCLUDE,
                        "Leave this reading out of trends",
                    ),
                    ReviewActionModel(ReviewAction.EDIT, "Open the timeline"),
                    ReviewActionModel(ReviewAction.IGNORE, "Ignore this"),
                ),
            ),
            ReviewRowModel(
                key = "duplicate:WEIGHT:2026-11-23",
                kind = ReviewKind.DUPLICATE,
                title = "Scale recorded weight twice",
                subtitle = "Readings under a minute apart. Merging keeps the first one.",
                dateLabel = "2026-11-23",
                readings = listOf(
                    ReviewReadingModel("Scale", "80.1 kg", "07:02", detail = null, winner = false),
                    ReviewReadingModel("Scale", "80.1 kg", "07:02", detail = null, winner = false),
                ),
                explanation = null,
                actions = listOf(
                    ReviewActionModel(ReviewAction.MERGE, "Move the duplicate to Trash"),
                    ReviewActionModel(ReviewAction.EDIT, "Open the timeline"),
                    ReviewActionModel(ReviewAction.IGNORE, "Ignore this"),
                ),
            ),
        ),
        emptyTitle = "Nothing to review",
        emptyBody = "Duplicate readings, values outside the plausible range and days where " +
            "two sources disagree appear here. There is nothing waiting now.",
        countLabel = "3 to review",
    )

    private val empty = populated.copy(
        rows = emptyList(),
        emptyTitle = "Nothing to review",
        emptyBody = "Duplicate readings, values outside the plausible range and days where " +
            "two sources disagree appear here. There is nothing waiting now.",
        countLabel = "0 to review",
    )

    @Test
    fun thePopulatedInbox() = shot("review-inbox", populated)

    @Test
    fun theEmptyInbox() = shot("review-empty", empty)

    private companion object {
        const val SHOT = "review"
    }
}
