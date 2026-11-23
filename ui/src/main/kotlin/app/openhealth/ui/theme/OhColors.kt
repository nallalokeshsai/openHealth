@file:Suppress("MagicNumber", "LongParameterList")

package app.openhealth.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * The closed colour set of DESIGN.md §2 and Appendix A. Every value here is a token from that
 * document; no screen may declare a colour of its own (`checkDesignTokens` enforces it).
 *
 * The dark theme is canonical (DESIGN.md §12.1). The light theme is a verified sibling, not an
 * inversion (§12.2). Alpha tokens (`label-2`, `label-3`, `label-4`, `label-dim`, `sep`, `sep-op`,
 * `raised-line`) are stored with their alpha and are composited over the surface they sit on;
 * `OhColors.contrast` in the test source set does that compositing for the §12.5 audit.
 */
@Immutable
data class OhColors(
    // Surface ramp (§2.2)
    val bg: Color,
    val bgEl: Color,
    val surface: Color,
    val surface2: Color,
    val surface3: Color,
    val surfaceRaised: Color,
    val raisedLine: Color,
    // Text ramp (§2.3)
    val label: Color,
    val label2: Color,
    val label3: Color,
    val label4: Color,
    val labelDim: Color,
    // Separators (§2.4); the 0.5 dp width is OhSpace.hairline
    val sep: Color,
    val sepOp: Color,
    // Component materials stated in §5.10–5.11 but not part of the palette ramp.
    val segmentThumb: Color,
    val switchKnob: Color,
    val switchKnobRing: Color,
    // Accent (§2.6)
    val acc: Color,
    val acc2: Color,
    val onAcc: Color,
    val accInk: Color,
    val accSoft: Color,
    val accLine: Color,
    // Data hues (§2.5). The recovery steps reuse green/orange/red, named explicitly because they
    // are data, not status (§9.1).
    val fat: Color,
    val recReady: Color,
    val recEasing: Color,
    val recActive: Color,
    val blue: Color,
    val green: Color,
    val red: Color,
    val orange: Color,
    val yellow: Color,
    val teal: Color,
    val indigo: Color,
    val pink: Color,
    val purple: Color,
    val mint: Color,
    val brown: Color,
    val grey: Color,
    // Status inks (§2.7): the text-safe shade of each fill.
    val greenInk: Color,
    val orangeInk: Color,
    val goldInk: Color,
    val redInk: Color,
    val blueInk: Color,
    val tealInk: Color,
    val streakInk: Color,
    val winInk: Color,
    val frost: Color,
    // Which half of the pair this is. Components that must differ (shadows, hairlines on raised
    // surfaces) branch on this instead of reading the platform configuration.
    val isDark: Boolean
)

/**
 * The nine accent presets of DESIGN.md §2.6. A preset is a Look setting, one per account; it never
 * changes meaning.
 *
 * `acc-2` is the pressed graphic shade. DESIGN.md specifies it for the brand preset only; the
 * owner's decision of 2026-11-23 (recorded in DESIGN.md Appendix A) is that the other eight reuse
 * their own fill rather than invent a hex. No B1 component darkens an accent fill behind text:
 * buttons press by scale and opacity (§5.7).
 */
enum class OhAccentPreset(
    val key: String,
    private val darkFill: Color,
    private val lightFill: Color,
    private val lightInk: Color,
    private val onAccent: Color,
    private val darkPressed: Color? = null,
    private val lightPressed: Color? = null
) {
    Brand(
        key = "brand",
        darkFill = Color(0xFFA3E635),
        lightFill = Color(0xFF84CC16),
        lightInk = Color(0xFF4A7A0E),
        onAccent = Color(0xFF0C0E12),
        darkPressed = Color(0xFF84CC16),
        lightPressed = Color(0xFF65A30D)
    ),
    Lime(
        key = "lime",
        darkFill = Color(0xFF30D158),
        lightFill = Color(0xFF34C759),
        lightInk = Color(0xFF1A7F37),
        onAccent = Color(0xFF000000)
    ),
    Sky(
        key = "sky",
        darkFill = Color(0xFF0A84FF),
        lightFill = Color(0xFF007AFF),
        lightInk = Color(0xFF0062CC),
        onAccent = Color(0xFF000000)
    ),
    Orange(
        key = "orange",
        darkFill = Color(0xFFFF9F0A),
        lightFill = Color(0xFFFF9500),
        lightInk = Color(0xFFA85200),
        onAccent = Color(0xFF000000)
    ),
    Violet(
        key = "violet",
        darkFill = Color(0xFFBF5AF2),
        lightFill = Color(0xFFAF52DE),
        lightInk = Color(0xFF8944AB),
        onAccent = Color(0xFF000000)
    ),
    Pink(
        key = "pink",
        darkFill = Color(0xFFFF375F),
        lightFill = Color(0xFFFF2D55),
        lightInk = Color(0xFFD70036),
        onAccent = Color(0xFF000000)
    ),
    Red(
        key = "red",
        darkFill = Color(0xFFFF453A),
        lightFill = Color(0xFFFF3B30),
        lightInk = Color(0xFFD70015),
        onAccent = Color(0xFF000000)
    ),
    Teal(
        key = "teal",
        darkFill = Color(0xFF40C8E0),
        lightFill = Color(0xFF30B0C7),
        lightInk = Color(0xFF0071A4),
        onAccent = Color(0xFF000000)
    ),
    Gold(
        key = "gold",
        darkFill = Color(0xFFFFD60A),
        lightFill = Color(0xFFFFCC00),
        lightInk = Color(0xFF8F5E00),
        onAccent = Color(0xFF000000)
    )
    ;

    /** The accent fill; `acc-ink` on dark equals this (§2.6, A.1). */
    fun fill(dark: Boolean): Color = if (dark) darkFill else lightFill

    /** The pressed graphic shade; the fill for presets DESIGN.md does not give a shade. */
    fun pressed(dark: Boolean): Color = if (dark) darkPressed ?: darkFill else lightPressed ?: lightFill

    /** The text/icon shade of the accent on a page or card background (§2.6). */
    fun ink(dark: Boolean): Color = if (dark) darkFill else lightInk

    /** Dark ink on every preset: white fails 4.5:1 on all nine fills (§2.6). */
    fun onAccent(): Color = onAccent

    companion object {
        fun fromKey(key: String): OhAccentPreset = entries.firstOrNull { it.key == key } ?: Brand
    }
}

/** The dark ramp, Appendix A.1. Authored first and canonical (§12.1). */
@Suppress("LongParameterList")
internal fun darkColors(preset: OhAccentPreset, highContrast: Boolean): OhColors {
    val label2Alpha = if (highContrast) 0xC7 else 0x99
    val labelDimAlpha = if (highContrast) 0xB8 else 0x8C
    val sepAlpha = if (highContrast) 0xD9 else 0x99
    val sepOpAlpha = if (highContrast) 0x8C else 0x57
    return OhColors(
        bg = Color(0xFF000000),
        bgEl = Color(0xFF0E0E10),
        surface = Color(0xFF1C1C1E),
        surface2 = Color(0xFF2C2C2E),
        surface3 = Color(0xFF3A3A3C),
        surfaceRaised = Color(0xFF2C2C2E),
        raisedLine = Color(0x17FFFFFF),
        label = Color(0xFFFFFFFF),
        label2 = Color(0xEBEBF5).withAlpha(label2Alpha),
        label3 = Color(0xEBEBF5).withAlpha(0x52),
        label4 = Color(0xEBEBF5).withAlpha(0x2E),
        labelDim = Color(0xEBEBF5).withAlpha(labelDimAlpha),
        sep = Color(0x545458).withAlpha(sepAlpha),
        sepOp = Color(0x545458).withAlpha(sepOpAlpha),
        segmentThumb = Color(0xFF636366),
        switchKnob = Color(0xFFFFFFFF),
        switchKnobRing = Color(0x000000).withAlpha(0x47),
        acc = preset.fill(dark = true),
        acc2 = preset.pressed(dark = true),
        onAcc = preset.onAccent(),
        accInk = preset.ink(dark = true),
        accSoft = preset.fill(dark = true).copy(alpha = 0.16f),
        accLine = preset.fill(dark = true).copy(alpha = 0.38f),
        fat = Color(0xFFEBD27A),
        recReady = Color(0xFF30D158),
        recEasing = Color(0xFFFF9F0A),
        recActive = Color(0xFFFF453A),
        blue = Color(0xFF0A84FF),
        green = Color(0xFF30D158),
        red = Color(0xFFFF453A),
        orange = Color(0xFFFF9F0A),
        yellow = Color(0xFFFFD60A),
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
        isDark = true
    )
}

/** The light ramp, Appendix A.2. Derived and contrast-verified (§12.2). */
@Suppress("LongParameterList")
internal fun lightColors(preset: OhAccentPreset, highContrast: Boolean): OhColors {
    val label2Alpha = if (highContrast) 0xD9 else 0xBF
    val label3Alpha = if (highContrast) 0x4D else 0x4D
    val label4Alpha = 0x29
    val sepAlpha = if (highContrast) 0x80 else 0x4A
    val sepOpAlpha = if (highContrast) 0x59 else 0x33
    val label2 = Color(0x3C3C43).withAlpha(label2Alpha)
    return OhColors(
        bg = Color(0xFFF2F2F7),
        bgEl = Color(0xFFF7F7FA),
        surface = Color(0xFFFFFFFF),
        surface2 = Color(0xFFECECEF),
        surface3 = Color(0xFFE3E3E8),
        surfaceRaised = Color(0xFFFFFFFF),
        raisedLine = Color(0x3C3C43).withAlpha(0x1A),
        label = Color(0xFF000000),
        label2 = label2,
        label3 = Color(0x3C3C43).withAlpha(label3Alpha),
        label4 = Color(0x3C3C43).withAlpha(label4Alpha),
        // A.2: light label-dim is label-2.
        labelDim = label2,
        sep = Color(0x3C3C43).withAlpha(sepAlpha),
        sepOp = Color(0x3C3C43).withAlpha(sepOpAlpha),
        segmentThumb = Color(0xFFFFFFFF),
        switchKnob = Color(0xFFFFFFFF),
        switchKnobRing = Color(0x000000).withAlpha(0x47),
        acc = preset.fill(dark = false),
        acc2 = preset.pressed(dark = false),
        onAcc = preset.onAccent(),
        accInk = preset.ink(dark = false),
        accSoft = preset.fill(dark = false).copy(alpha = 0.16f),
        accLine = preset.fill(dark = false).copy(alpha = 0.38f),
        fat = Color(0xFF9A6F00),
        recReady = Color(0xFF34C759),
        recEasing = Color(0xFFFF9500),
        recActive = Color(0xFFFF3B30),
        blue = Color(0xFF007AFF),
        green = Color(0xFF34C759),
        red = Color(0xFFFF3B30),
        orange = Color(0xFFFF9500),
        yellow = Color(0xFFFFCC00),
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
        isDark = false
    )
}

/** Replaces the alpha channel of an `0xFFRRGGBB` literal without writing a second hex token. */
private fun Color.withAlpha(alpha: Int): Color = copy(alpha = (alpha and 0xFF) / 255f)
