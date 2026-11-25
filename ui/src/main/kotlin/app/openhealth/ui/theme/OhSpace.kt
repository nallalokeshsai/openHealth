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

    // §14.2 Live session set table
    /** The set column: 34 dp, warm-ups as W1… and work sets numbered. */
    val setColumn: Dp = 34.dp,
    /** The RIR column: 56 dp, a sheet opener and never a free-text keyboard. */
    val setRir: Dp = 56.dp,
    /** The Done circle: 38 dp inside a 44 dp target. */
    val setDone: Dp = 38.dp,
    /** The active row's leading edge: 1.5 dp of accent. */
    val setActiveEdge: Dp = 1.5.dp,

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

    // §14.2 Nutrition and §8.2 charts
    /** §14.2: the energy card's calorie ring is 120 dp. */
    val nutritionRing: Dp = 120.dp,
    /** §8.2: a ring is an 8 dp stroke with a round cap on a `surface-3` track. */
    val nutritionRingStroke: Dp = 8.dp,
    /** §8.2: a bar is 8 dp tall with an 8 dp radius cap and a 2 dp gap. */
    val macroBarHeight: Dp = 8.dp,
    val macroBarGap: Dp = 2.dp,
    /** §14.2: the date strip cell is a 44 dp target. */
    val nutritionDayCell: Dp = 44.dp,
    /** §14.2 nutrition nutrient fields: wide enough for four digits and a separator. */
    val nutritionField: Dp = 128.dp,
    /** §14.2: the barcode viewfinder fills the width and most of the page's height. */
    val scanPreview: Dp = 320.dp,

    // §9.1 the body map
    /** §9.1: `maxHeight = min(46 % of the viewport, 340 dp)`. */
    val bodyMapMaxFraction: Float = 0.46f,
    val bodyMapMax: Dp = 340.dp,
    /** §9.1: the front and back pair sits 6 dp apart and always at equal height. */
    val bodyMapPairGap: Dp = 6.dp,
    /** §9.1: every path is stroked 2.5 dp in the card's surface colour. */
    val bodyMapStroke: Dp = 2.5.dp,
    /** §9.1: the selected muscle is a 7 dp `label` stroke over a 2 dp surface glow. */
    val bodyMapSelectedStroke: Dp = 7.dp,
    val bodyMapSelectedGlow: Dp = 2.dp,
    /** §9.1: the mini map is 46 dp tall, 3 dp strokes, a 2 dp pair gap. */
    val bodyMapMiniHeight: Dp = 46.dp,
    val bodyMapMiniStroke: Dp = 3.dp,
    val bodyMapMiniGap: Dp = 2.dp,
    /** §9.1: the session header's mini map is 30 dp tall. */
    val bodyMapEventMini: Dp = 30.dp,
    /** §9.1: the Recovering hatch is a 1.5 dp line every 7 dp, at 35 %. */
    val bodyMapHatchStroke: Dp = 1.5.dp,
    val bodyMapHatchSpacing: Dp = 7.dp,

    // §9.3, B7: the measurement silhouette's anchor is a 22 dp glow with an 8 dp dot.
    val measureAnchor: Dp = 22.dp,

    // §8.1-8.2.1, B7: line-chart chrome and the clinical range strip.
    val chartHeight: Dp = 160.dp,
    val chartAxisWidth: Dp = 46.dp,
    val chartScaleLabel: Dp = 64.dp,
    val chartLine: Dp = 2.5.dp,
    val chartSecondLine: Dp = 2.dp,
    val chartGrid: Dp = 1.dp,
    val chartGoal: Dp = 1.5.dp,
    val chartCrosshair: Dp = 1.dp,
    val chartPoint: Dp = 2.5.dp,
    val chartLastPoint: Dp = 4.dp,
    val chartScrubRing: Dp = 7.dp,
    val chartScrubDot: Dp = 5.dp,
    val chartLegendDot: Dp = 8.dp,
    val chartDashOn: Dp = 7.dp,
    val chartDashOff: Dp = 4.dp,
    /** §8.2.1: the strip is 10 dp tall inside a 16 dp tick box, with a 3 dp tick. */
    val rangeStripTrack: Dp = 10.dp,
    val rangeStripBox: Dp = 16.dp,
    val rangeStripTickWidth: Dp = 3.dp,

    // §9.4, B7: the before/after photo slider.
    val photoCompareMax: Dp = 420.dp,
    val photoHandle: Dp = 2.dp,
    val photoGrip: Dp = 12.dp,
    val photoGripDot: Dp = 3.dp,
    val photoGripDotOffset: Dp = 4.dp,
    val photoGripStroke: Dp = 1.5.dp,
    val photoHiddenBlur: Dp = 18.dp,
    val photoDatePillPadV: Dp = 2.dp,
    val photoGridGap: Dp = 10.dp,
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
