@file:Suppress("MagicNumber")

package app.openhealth.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The nine spacing values of `DESIGN.md` §4.1. Nothing in between exists. */
object OhSpace {
    val none: Dp = 0.dp

    /** 2 dp: icon-to-text optical nudge, chart tick gap. */
    val s0: Dp = 2.dp

    /** 4 dp: inside a chip, badge padding, tight stacks. */
    val s1: Dp = 4.dp

    /** 6 dp: chip gaps, hairline insets, row internal gaps. */
    val s2: Dp = 6.dp

    /** 8 dp: grid gaps, icon-to-label, small stacks. */
    val s3: Dp = 8.dp

    /** 10 dp: tile grid gap, segmented inner gap. */
    val s4: Dp = 10.dp

    /** 12 dp: card inner stack, row gap, control padding. */
    val s5: Dp = 12.dp

    /** 16 dp: screen padding, card padding, sheet side padding. */
    val s6: Dp = 16.dp

    /** 22 dp: between sections, sheet title to content. */
    val s7: Dp = 22.dp

    /** 32 dp: section break on a sparse screen, empty-state breathing. */
    val s8: Dp = 32.dp
}

/** The six radii plus full of `DESIGN.md` §4.2. */
object OhRadius {
    /** 8 dp: tags, small icon tiles, small buttons, checkbox. */
    val sm: Dp = 8.dp

    /** 10 dp: inputs, segments, keyboard-adjacent controls. */
    val md: Dp = 10.dp

    /** 12 dp: buttons, steppers, small cards. */
    val base: Dp = 12.dp

    /** 14 dp: cards, list rows, grouped-list container. */
    val card: Dp = 14.dp

    /** 16 dp: media tiles, dialogs, photo cards. */
    val lg: Dp = 16.dp

    /** 22 dp: sheet top corners, toast. */
    val xl: Dp = 22.dp

    /** Full: chips, avatars, switches, the raised Train disc, pills. */
    val full: Dp = 999.dp
}

/**
 * Component geometry stated by `DESIGN.md` §4.3, §4.5 and §5, in one place.
 *
 * These are sizes, not new tokens: every value is quoted from the section named on
 * the field, and no component may write a raw `.dp` (`checkDesignTokens`). Spacing
 * comes from [OhSpace] and corners from [OhRadius]; this object holds only what the
 * component anatomies fix.
 */
@Immutable
data class OhDims(
    /** `DESIGN.md` §0.3, §4.3: every separator is a sub-pixel hairline. */
    val hairline: Dp = 0.5.dp,
    /** §4.5, §11.1: the minimum touch target. */
    val touchTarget: Dp = 44.dp,
    /** §4.3: the centred content column. */
    val contentMax: Dp = 560.dp,
    /** §4.3: the bottom reserve above the tab bar. */
    val screenBottomReserve: Dp = 88.dp,
    /** §4.3: at or above this window width a title is 34 sp, below it 30 sp. */
    val wideWindow: Int = 420,
    /** §5.3: at or below 430 dp a value in words moves under its title. */
    val narrowValueRow: Int = 430,
    /** §4.3, §11.1: at this font scale labels and action pairs adapt rather than clip. */
    val largeFontScale: Float = 1.5f,

    // §5.3 inset group list and row
    val rowMin: Dp = 46.dp,
    val rowPadV: Dp = 12.dp,
    val rowPadH: Dp = 16.dp,
    val rowGap: Dp = 12.dp,
    /** §5.3: the hairline starts 16 dp in, or 57 dp after an icon rail. */
    val rowHairInset: Dp = 16.dp,
    val rowIconInset: Dp = 57.dp,

    // §5.1 card, §5.2 card header
    val cardPad: Dp = 16.dp,
    val cardGap: Dp = 12.dp,
    /** §5.2: the card header's glyph sits in a 29 dp optical box. */
    val glyph: Dp = 29.dp,
    /** §10.1: the optical size of a glyph inside 13 sp text. */
    val glyphInline: Dp = 14.dp,

    // §5.2, §5.8, §4.4
    val iconButton: Dp = 36.dp,
    val iconButtonGlyph: Dp = 18.dp,
    /** §5.3: the row chevron. */
    val chevron: Dp = 15.dp,
    /** §5.3, §5.4: the leading icon tile is 29 × 29 dp with radius 8. */
    val iconTile: Dp = 29.dp,

    // §5.4 media row
    val mediaRowMin: Dp = 60.dp,
    val mediaThumb: Dp = 50.dp,
    val mediaPad: Dp = 12.dp,
    /** §5.4: the grid variant's minimum tile. */
    val gridMin: Dp = 150.dp,

    // §5.7 button
    val buttonHeight: Dp = 50.dp,
    val buttonPadH: Dp = 16.dp,
    val buttonPadV: Dp = 12.dp,
    val buttonSmallHeight: Dp = 35.dp,
    val buttonSmallPadH: Dp = 12.dp,
    val buttonSmallPadV: Dp = 8.dp,
    val buttonXsHeight: Dp = 28.dp,

    // §5.9 chip
    val chipHeight: Dp = 32.dp,
    val chipPadH: Dp = 12.dp,
    val chipPadV: Dp = 6.dp,

    // §5.10 segmented control
    val segmentHeight: Dp = 32.dp,
    val segmentPad: Dp = 2.dp,

    // §5.11 switch
    val switchWidth: Dp = 51.dp,
    val switchHeight: Dp = 31.dp,
    val switchKnob: Dp = 27.dp,

    // §5.12 stepper
    val stepperHeight: Dp = 44.dp,

    // §5.13 slider
    val sliderTrack: Dp = 4.dp,
    val sliderKnob: Dp = 24.dp,

    // §5.14 text and search field
    val fieldHeight: Dp = 46.dp,
    val fieldPadH: Dp = 16.dp,
    val focusRing: Dp = 2.5.dp,
    val focusOffset: Dp = 2.dp,
    /** §5.14: the search field's magnifier and its clear button. */
    val searchGlyph: Dp = 18.dp,
    val clearGlyph: Dp = 22.dp,

    // §14.2 Today and global quick add: an 84 dp tile with a 21 dp accent glyph.
    val quickTile: Dp = 84.dp,
    val quickTileGlyph: Dp = 21.dp,

    // §5.15 tab bar
    val tabBarHeight: Dp = 56.dp,
    val tabIcon: Dp = 25.dp,
    val trainDisc: Dp = 56.dp,
    val trainDiscLift: Dp = 12.dp,
    /** §5.15: the 8 dp status dot with its 2 dp ring. */
    val dot: Dp = 8.dp,
    val dotRing: Dp = 2.dp,

    // §5.16 sheet
    val grabberWidth: Dp = 36.dp,
    val grabberHeight: Dp = 5.dp,
    val grabberTop: Dp = 6.dp,

    // §5.17 dialog
    val dialogWidth: Dp = 300.dp,
    val dialogWidthWide: Dp = 440.dp,
    val dialogPad: Dp = 22.dp,

    // §5.18 toast
    val toastPadH: Dp = 22.dp,
    val toastPadV: Dp = 12.dp,
    val toastLift: Dp = 96.dp,
    /** §5.18: max 88 % width. */
    val toastMaxWidthFraction: Float = 0.88f,

    // §5.16 sheet
    /** §5.16: a sheet is at most 90 % of the viewport minus the status inset. */
    val sheetMaxHeightFraction: Float = 0.90f,

    // §5.19 timer bar
    val timerProgress: Dp = 3.dp,

    // §12.3 soft shadows, mapped to Compose elevation (dark is stronger than light)
    val dialogElevation: Dp = 12.dp,
    val raisedElevation: Dp = 6.dp,

    // §10.1 the icon set: a 24 dp box, stroke 1.7, round caps and joins
    val iconBox: Dp = 24.dp,
    val iconStroke: Float = 1.7f,
    /** §5.15: tab icons rest at 1.65 and go to 2.0 when active. */
    val tabStrokeRest: Float = 1.65f,
    val tabStrokeActive: Float = 2.0f,
    /** §10.1: chevrons may go to 2.0–2.4 for weight. */
    val chevronStroke: Float = 2.4f,
)

/** Spacing and radius are single instances; geometry is one immutable record. */
val ohDims: OhDims = OhDims()

/**
 * §5.16: a sheet is at most 90 % of the viewport minus the status inset. The arithmetic
 * lives here so no component writes a raw `Dp(...)`.
 */
fun sheetMaxHeight(screenHeightDp: Int, statusInsetDp: Float, fraction: Float): Dp =
    ((screenHeightDp - statusInsetDp) * fraction).dp

/** §5.18: a toast is at most 88 % of the window width. */
fun toastMaxWidth(screenWidthDp: Int, fraction: Float): Dp = (screenWidthDp * fraction).dp
