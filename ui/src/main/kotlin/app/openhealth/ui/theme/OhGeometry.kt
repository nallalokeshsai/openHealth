@file:Suppress("MagicNumber")

package app.openhealth.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Component geometry from DESIGN.md §4.3–§4.5 and §5. These are not new design tokens: every
 * value is the size the component section states. They live here because the design contract
 * declares sizes once (Appendix A.4) and because the token guard forbids a raw `.dp` literal
 * anywhere outside this package.
 *
 * Sizes expressed as a product of tokens (for example a top bar built from a 36 dp icon button
 * plus 8 dp padding) are written that way in the component, not frozen here.
 */
@Immutable
data class OhGeometry(
    // §4.3 screen skeleton
    val screenPadding: Dp = 16.dp,
    /** Space reserved under scrolling content for the 56 dp tab bar plus breathing room. */
    val screenTabReserve: Dp = 88.dp,
    /** Extra gap below the status-bar inset. */
    val screenTopGap: Dp = 8.dp,
    /** The connection banner (§6.5). */
    val bannerHeight: Dp = 34.dp,
    /** §6.7 empty state glyph. */
    val emptyGlyph: Dp = 29.dp,

    // §4.4 top bar
    val topBarIconButton: Dp = 36.dp,
    val topBarGlyph: Dp = 18.dp,
    val topBarGap: Dp = 8.dp,

    // §5.1–5.2 card and card header
    val cardPadding: Dp = 16.dp,
    val cardGap: Dp = 12.dp,
    val cardHeaderGlyph: Dp = 29.dp,
    val cardHeaderTrailing: Dp = 44.dp,

    // §10 icons
    val iconBox: Dp = 24.dp,
    val iconStroke: Dp = 1.7.dp,
    val iconStrokeTab: Dp = 1.65.dp,
    val iconStrokeTabActive: Dp = 2.0.dp,
    val iconStrokeChevron: Dp = 2.4.dp,
    val iconInText: Dp = 14.dp,
    val iconPadding: Dp = 1.5.dp,

    // §5.3 inset group list and row
    val rowMinHeight: Dp = 46.dp,
    val rowVerticalPadding: Dp = 12.dp,
    val rowHorizontalPadding: Dp = 16.dp,
    val rowGap: Dp = 12.dp,
    val rowIconTile: Dp = 29.dp,
    val rowIconRadius: Dp = 8.dp,
    val rowChevron: Dp = 15.dp,
    val destructiveGroupGap: Dp = 22.dp,
    /** �6.3: a disabled row is 45 % opaque, a disabled control 32 % (buttonDisabledAlpha). */
    val rowDisabledAlpha: Float = 0.45f,

    // §5.4 media row
    val mediaMinHeight: Dp = 60.dp,
    val mediaPadding: Dp = 12.dp,
    val mediaThumbnail: Dp = 50.dp,
    val mediaThumbnailRadius: Dp = 8.dp,
    val mediaAction: Dp = 32.dp,
    val mediaGridMinWidth: Dp = 150.dp,

    // §5.5 stat tile
    val statTilePadding: Dp = 12.dp,
    val statTileGap: Dp = 10.dp,
    val statTileGlyph: Dp = 14.dp,
    val statDeltaGap: Dp = 2.dp,

    // §5.6 KPI band
    val kpiHorizontalPadding: Dp = 16.dp,
    val kpiVerticalPadding: Dp = 12.dp,

    // §5.7 button
    val buttonHeight: Dp = 50.dp,
    val buttonHeightSmall: Dp = 35.dp,
    val buttonHeightExtraSmall: Dp = 28.dp,
    val buttonPaddingVertical: Dp = 12.dp,
    val buttonPaddingHorizontal: Dp = 16.dp,
    val buttonSmallPaddingVertical: Dp = 8.dp,
    val buttonSmallPaddingHorizontal: Dp = 12.dp,
    val buttonDisabledAlpha: Float = 0.32f,
    val buttonPressedAlpha: Float = 0.90f,

    // §5.8 icon button
    val iconButton: Dp = 36.dp,
    val iconButtonGlyph: Dp = 18.dp,

    // §5.9 chip
    val chipHeight: Dp = 32.dp,
    val chipPaddingVertical: Dp = 6.dp,
    val chipPaddingHorizontal: Dp = 12.dp,

    // §5.10 segmented control
    val segmentHeight: Dp = 32.dp,
    val segmentPadding: Dp = 2.dp,
    val segmentMaxCount: Int = 5,

    // §5.11 switch
    val switchWidth: Dp = 51.dp,
    val switchHeight: Dp = 31.dp,
    val switchKnob: Dp = 27.dp,

    // §5.12 stepper
    val stepperHeight: Dp = 44.dp,
    val stepperCellWidth: Dp = 44.dp,

    // §5.13 slider
    val sliderTrack: Dp = 4.dp,
    val sliderKnob: Dp = 24.dp,
    val sliderRing: Dp = 2.dp,

    // §5.14 text and search field
    val fieldHeight: Dp = 46.dp,
    val fieldPaddingHorizontal: Dp = 16.dp,
    val fieldFocusRing: Dp = 2.5.dp,
    val fieldFocusOffset: Dp = 2.dp,
    val fieldFocusRadius: Dp = 8.dp,
    val searchGlyph: Dp = 18.dp,
    val searchClear: Dp = 22.dp,

    // §5.15 tab bar
    val tabBarHeight: Dp = 56.dp,
    val tabBarPaddingVertical: Dp = 6.dp,
    val tabBarAlpha: Float = 0.94f,
    val tabIcon: Dp = 25.dp,
    val tabLabelGap: Dp = 2.dp,
    val trainDisc: Dp = 56.dp,
    val trainDiscLift: Dp = 12.dp,
    val tabDot: Dp = 8.dp,
    val tabDotRing: Dp = 2.dp,

    // §5.16 sheet
    val sheetPaddingVertical: Dp = 8.dp,
    val sheetPaddingHorizontal: Dp = 16.dp,
    val sheetBottomPadding: Dp = 22.dp,
    val grabberWidth: Dp = 36.dp,
    val grabberHeight: Dp = 5.dp,
    val grabberGap: Dp = 6.dp,
    val sheetMaxHeightFraction: Float = 0.90f,
    val sheetScrimAlpha: Float = 0.40f,
    val sheetScrimBlur: Dp = 2.dp,

    // §5.17 dialog
    val dialogWidth: Dp = 300.dp,
    val dialogWideWidth: Dp = 440.dp,
    val dialogPadding: Dp = 22.dp,

    // §5.18 toast and undo
    val toastMaxWidthFraction: Float = 0.88f,
    val toastLift: Dp = 96.dp,
    val toastPaddingVertical: Dp = 12.dp,
    val toastPaddingHorizontal: Dp = 22.dp,
    val toastActionTarget: Dp = 36.dp,
    val toastPlainMillis: Int = 4_000,
    val toastUndoMillis: Int = 6_000,

    // §5.19 timer bar
    val timerBarLift: Dp = 22.dp,
    val timerBarPadding: Dp = 12.dp,
    val timerBarAlpha: Float = 0.97f,
    val timerPausedAlpha: Float = 0.50f,
    val timerProgress: Dp = 3.dp,
    val timerControl: Dp = 44.dp,
    val timerSkipGap: Dp = 6.dp
) {
    /** A 36 dp icon button plus 8 dp of vertical air, the pinned top bar's height (§4.4). */
    val topBarHeight: Dp get() = topBarIconButton + topBarGap * 2
}
