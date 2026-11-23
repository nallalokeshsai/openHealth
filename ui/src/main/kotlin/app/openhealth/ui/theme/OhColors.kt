@file:Suppress("MagicNumber")

package app.openhealth.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * The closed colour set of `DESIGN.md` §2 and Appendix A.
 *
 * This file is the **only** place a colour literal may appear in the UI module
 * (`DESIGN.md` §12.6, Appendix B); `checkDesignTokens` fails the build on a
 * `Color(` literal anywhere else. Alpha-carrying tokens keep their alpha here, so
 * contrast can be measured by compositing them over the surface they sit on
 * (`DESIGN.md` §12.5, `OhContrastTest`).
 */
@Immutable
data class OhColors(
    val dark: Boolean,
    // §2.2 surface ramp
    val bg: Color,
    val bgEl: Color,
    val surface: Color,
    val surface2: Color,
    val surface3: Color,
    val surfaceRaised: Color,
    val raisedLine: Color,
    // §2.3 text
    val label: Color,
    val label2: Color,
    val label3: Color,
    val label4: Color,
    val labelDim: Color,
    // §2.4 separators
    val sep: Color,
    val sepOp: Color,
    // §2.6 the accent (per preset)
    val acc: Color,
    val acc2: Color,
    val onAcc: Color,
    val accInk: Color,
    val accSoft: Color,
    val accLine: Color,
    // §2.5 data hues
    val blue: Color,
    val green: Color,
    val red: Color,
    val orange: Color,
    val yellow: Color,
    val fat: Color,
    val teal: Color,
    val indigo: Color,
    val pink: Color,
    val purple: Color,
    val mint: Color,
    val brown: Color,
    val grey: Color,
    // §2.7 status inks
    val greenInk: Color,
    val orangeInk: Color,
    val goldInk: Color,
    val redInk: Color,
    val blueInk: Color,
    val tealInk: Color,
    val streakInk: Color,
    val winInk: Color,
    val frost: Color,
    // §9.1 recovery map
    val recReady: Color,
    val recEasing: Color,
    val recActive: Color,
    // Derived tokens the component specs state in words.
    /** `red` at 15 %: the Danger button fill (`DESIGN.md` §5.7). */
    val dangerFill: Color,
    /** `#636366` in dark, `surface` in light: the segmented control's thumb (§5.10). */
    val segmentThumb: Color,
    /** White in both themes: the switch knob and slider knob (§5.11, §5.13). */
    val knob: Color,
    /** `rgba(0,0,0,.28)`: the knob's hairline ring on a very light accent (§2.6). */
    val knobRing: Color,
    /** 40 % black: the sheet's scrim (§5.16). */
    val scrim: Color,
    /** `bg-el` at 94 %: the tab bar and the top bar (§4.3, §5.15). */
    val barFill: Color,
    /** `surface-2` at 88 %: the toast (§5.18). */
    val toastFill: Color,
    /** `surface-raised` at 97 %: the timer bar (§5.19). */
    val timerBarFill: Color,
    /** `label` at 11 % (dark) / 14 % (light): the body map's untrained base (§9.1). */
    val bodyBase: Color,
)

/** The nine accents of `DESIGN.md` §2.6. No more (`§0.2`). */
enum class OhAccent(
    val key: String,
    private val darkFill: Long,
    private val lightFill: Long,
    private val lightInk: Long,
    private val onAccent: Long,
) {
    Brand("brand", 0xFFA3E635, 0xFF84CC16, 0xFF4A7A0E, 0xFF0C0E12),
    Lime("lime", 0xFF30D158, 0xFF34C759, 0xFF1A7F37, 0xFF000000),
    Sky("sky", 0xFF0A84FF, 0xFF007AFF, 0xFF0062CC, 0xFF000000),
    Orange("orange", 0xFFFF9F0A, 0xFFFF9500, 0xFFA85200, 0xFF000000),
    Violet("violet", 0xFFBF5AF2, 0xFFAF52DE, 0xFF8944AB, 0xFF000000),
    Pink("pink", 0xFFFF375F, 0xFFFF2D55, 0xFFD70036, 0xFF000000),
    Red("red", 0xFFFF453A, 0xFFFF3B30, 0xFFD70015, 0xFF000000),
    Teal("teal", 0xFF40C8E0, 0xFF30B0C7, 0xFF0071A4, 0xFF000000),
    Gold("gold", 0xFFFFD60A, 0xFFFFCC00, 0xFF8F5E00, 0xFF000000),
    ;

    companion object {
        fun fromKey(key: String?): OhAccent? = entries.firstOrNull { it.key == key }
    }

    internal val darkAcc: Color get() = Color(darkFill)
    internal val lightAcc: Color get() = Color(lightFill)
    internal val lightAccInk: Color get() = Color(lightInk)
    internal val onAccentColor: Color get() = Color(onAccent)

    /**
     * `acc-2`, the pressed graphic shade of `DESIGN.md` §2.6. Appendix A gives it for
     * the default `brand` accent only, so for the other eight presets it resolves to the
     * same preset's documented one-step shade and needs no invented hex (§0.2): the
     * "Light fill" value in dark (the same hue, one step off the resting accent) and the
     * "Light ink" value in light (the documented darker shade of the hue). Both clear
     * 3:1 on `surface`. It is never text, and no component presses with it: buttons press
     * by scale and opacity.
     */
    internal val darkAcc2: Color get() = if (this == Brand) Color(0xFF84CC16) else Color(lightFill)
    internal val lightAcc2: Color get() = if (this == Brand) Color(0xFF65A30D) else Color(lightInk)
}

/** Dark theme: canonical (`DESIGN.md` §12.1, Appendix A.1). */
fun ohDarkColors(accent: OhAccent = OhAccent.Brand): OhColors = OhColors(
    dark = true,
    bg = Color(0xFF000000),
    bgEl = Color(0xFF0E0E10),
    surface = Color(0xFF1C1C1E),
    surface2 = Color(0xFF2C2C2E),
    surface3 = Color(0xFF3A3A3C),
    surfaceRaised = Color(0xFF2C2C2E),
    raisedLine = Color(0x17FFFFFF),
    label = Color(0xFFFFFFFF),
    label2 = Color(0x99EBEBF5),
    label3 = Color(0x52EBEBF5),
    label4 = Color(0x2EEBEBF5),
    labelDim = Color(0x8CEBEBF5),
    sep = Color(0x99545458),
    sepOp = Color(0x57545458),
    acc = accent.darkAcc,
    acc2 = accent.darkAcc2,
    onAcc = accent.onAccentColor,
    accInk = accent.darkAcc,
    accSoft = accent.darkAcc.copy(alpha = OhAlpha.soft),
    accLine = accent.darkAcc.copy(alpha = OhAlpha.line),
    blue = Color(0xFF0A84FF),
    green = Color(0xFF30D158),
    red = Color(0xFFFF453A),
    orange = Color(0xFFFF9F0A),
    yellow = Color(0xFFFFD60A),
    fat = Color(0xFFF2C230),
    teal = Color(0xFF40C8E0),
    indigo = Color(0xFF5E5CE6),
    pink = Color(0xFFFF375F),
    purple = Color(0xFFBF5AF2),
    mint = Color(0xFF63E6E2),
    brown = Color(0xFFAC8E68),
    grey = Color(0xFF8E8E93),
    greenInk = Color(0xFF30D158),
    orangeInk = Color(0xFFFF9F0A),
    goldInk = Color(0xFFFFD60A),
    redInk = Color(0xFFFF453A),
    blueInk = Color(0xFF0A84FF),
    tealInk = Color(0xFF40C8E0),
    streakInk = Color(0xFFFF9F0A),
    winInk = Color(0xFF30D158),
    frost = Color(0xFF64D2FF),
    recReady = Color(0xFF30D158),
    recEasing = Color(0xFFFF9F0A),
    recActive = Color(0xFFFF453A),
    dangerFill = Color(0xFFFF453A).copy(alpha = OhAlpha.danger),
    segmentThumb = Color(0xFF636366),
    knob = Color(0xFFFFFFFF),
    knobRing = Color(0x47000000),
    scrim = Color(0xFF000000).copy(alpha = OhAlpha.scrim),
    barFill = Color(0xFF0E0E10).copy(alpha = OhAlpha.bar),
    toastFill = Color(0xFF2C2C2E).copy(alpha = OhAlpha.toast),
    timerBarFill = Color(0xFF2C2C2E).copy(alpha = OhAlpha.timerBar),
    bodyBase = Color(0xFFFFFFFF).copy(alpha = 0.11f),
)

/** Light theme: derived and contrast-verified (`DESIGN.md` §12.2, Appendix A.2). */
fun ohLightColors(accent: OhAccent = OhAccent.Brand): OhColors = OhColors(
    dark = false,
    bg = Color(0xFFF2F2F7),
    bgEl = Color(0xFFF7F7FA),
    surface = Color(0xFFFFFFFF),
    surface2 = Color(0xFFECECEF),
    surface3 = Color(0xFFE3E3E8),
    surfaceRaised = Color(0xFFFFFFFF),
    raisedLine = Color(0x1A3C3C43),
    label = Color(0xFF000000),
    label2 = Color(0xBF3C3C43),
    label3 = Color(0x4D3C3C43),
    label4 = Color(0x293C3C43),
    labelDim = Color(0xBF3C3C43),
    sep = Color(0x4A3C3C43),
    sepOp = Color(0x333C3C43),
    acc = accent.lightAcc,
    acc2 = accent.lightAcc2,
    onAcc = accent.onAccentColor,
    accInk = accent.lightAccInk,
    accSoft = accent.lightAcc.copy(alpha = OhAlpha.soft),
    accLine = accent.lightAcc.copy(alpha = OhAlpha.line),
    blue = Color(0xFF007AFF),
    green = Color(0xFF34C759),
    red = Color(0xFFFF3B30),
    orange = Color(0xFFFF9500),
    yellow = Color(0xFFFFCC00),
    fat = Color(0xFFA87C00),
    teal = Color(0xFF30B0C7),
    indigo = Color(0xFF5856D6),
    pink = Color(0xFFFF2D55),
    purple = Color(0xFFAF52DE),
    mint = Color(0xFF00C7BE),
    brown = Color(0xFFA2845E),
    grey = Color(0xFF8E8E93),
    greenInk = Color(0xFF1A7F37),
    orangeInk = Color(0xFFA85200),
    goldInk = Color(0xFF8F5E00),
    redInk = Color(0xFFD70015),
    blueInk = Color(0xFF0058C4),
    tealInk = Color(0xFF0E7490),
    streakInk = Color(0xFFE06A00),
    winInk = Color(0xFF1A7F37),
    frost = Color(0xFF0A7FC4),
    recReady = Color(0xFF34C759),
    recEasing = Color(0xFFFF9500),
    recActive = Color(0xFFFF3B30),
    dangerFill = Color(0xFFFF3B30).copy(alpha = OhAlpha.danger),
    segmentThumb = Color(0xFFFFFFFF),
    knob = Color(0xFFFFFFFF),
    knobRing = Color(0x00000000),
    scrim = Color(0xFF000000).copy(alpha = OhAlpha.scrim),
    barFill = Color(0xFFF7F7FA).copy(alpha = OhAlpha.bar),
    toastFill = Color(0xFFECECEF).copy(alpha = OhAlpha.toast),
    timerBarFill = Color(0xFFFFFFFF).copy(alpha = OhAlpha.timerBar),
    bodyBase = Color(0xFF000000).copy(alpha = 0.14f),
)
