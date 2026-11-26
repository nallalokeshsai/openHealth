@file:Suppress("MagicNumber")

package app.openhealth.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * The 11 type roles of `DESIGN.md` §3.2.
 *
 * Nine text roles (`caption2` … `large`) plus two display roles inside fixed boxes
 * (`display`, `timer`). A role's size, leading and tracking move as a set; weights
 * are applied by the component (`§3.3`) and are never a twelfth role.
 */
@Immutable
class OhType internal constructor(
    /** 34 sp, or 30 sp below a 420 dp window (`DESIGN.md` §3.2). */
    largeSizeSp: Float,
) {
    /** Tab labels, heatmap months, badge counts. 11 / 1.33 / 0. */
    val caption2: TextStyle = role(11f, 1.33f, 0f)

    /** Meta, axis labels, provenance chips, units. 12 / 1.33 / 0. */
    val caption: TextStyle = role(12f, 1.33f, 0f)

    /** Subtitles, row secondary lines, section headers. 13 / 1.38 / -0.004 em. */
    val footnote: TextStyle = role(13f, 1.38f, -0.004f)

    /** Values, secondary lines, field labels. 15 / 1.33 / -0.010 em. */
    val subhead: TextStyle = role(15f, 1.33f, -0.010f)

    /** Dense list titles, inputs, body in sheets. 16 / 1.29 / -0.010 em. */
    val callout: TextStyle = role(16f, 1.29f, -0.010f)

    /** Default body and row titles. 17 / 1.29 / -0.010 em. */
    val body: TextStyle = role(17f, 1.29f, -0.010f)

    /** Card titles, exercise name, sheet title. 20 / 1.20 / -0.021 em. */
    val title3: TextStyle = role(20f, 1.20f, -0.021f)

    /** A lead line, a dialog figure. 22 / 1.20 / -0.021 em. */
    val title2: TextStyle = role(22f, 1.20f, -0.021f)

    /** Page title. 34 sp, 30 below 420 dp. 1.06 / -0.028 em. */
    val large: TextStyle = role(largeSizeSp, 1.06f, -0.028f)

    /** The one big number in a card (weight, kcal left). 28 / 1.10 / -0.026 em. */
    val display: TextStyle = role(28f, 1.10f, -0.026f)

    /** Rest/work clock. 28 sp at the bottom of the role's 28–64 range. */
    val timer: TextStyle = role(28f, 1.10f, -0.026f)

    /**
     * The full-screen session clock: 64 sp at the top of the `timer` role's range.
     * The same role, not a twelfth one (`DESIGN.md` §3.2, §5.19).
     */
    val timerFullScreen: TextStyle = role(64f, 1.10f, -0.026f)
}

private fun role(sizeSp: Float, lineHeightMultiplier: Float, trackingEm: Float): TextStyle =
    TextStyle(
        fontSize = sizeSp.sp,
        lineHeight = (sizeSp * lineHeightMultiplier).sp,
        letterSpacing = trackingEm.em,
        // Sentence case everywhere, never an uppercase transform (§3.3, §11.4).
        textAlign = TextAlign.Unspecified,
        // Bidi resolves from the content, not the layout: English prose on an RTL phone
        // keeps its full stop on the right (§11.5).
        textDirection = TextDirection.Content,
    )

/**
 * The paragraph direction for a block whose lines must align to the layout's start.
 *
 * The roles themselves resolve bidi from the content (English prose on an RTL phone
 * keeps its own direction), but a block that fills a column has to lay its lines out
 * from the same edge the column starts at, or a right-to-left screen left-aligns its
 * titles while its rows start on the right. Only those blocks ask for this.
 */
@Composable
fun ohBlockDirection(): TextDirection = if (LocalLayoutDirection.current == LayoutDirection.Rtl) {
    TextDirection.Rtl
} else {
    TextDirection.Content
}

/** Builds the scale for a window width: 34 sp titles, 30 sp below 420 dp (§3.2). */
fun ohType(windowWidthDp: Int): OhType = OhType(largeSizeSp = if (windowWidthDp < 420) 30f else 34f)

/** Weights of `DESIGN.md` §3.3. No 800/900, no italics outside an explanation line. */
object OhWeight {
    val regular = FontWeight.W400
    val medium = FontWeight.W500
    val semibold = FontWeight.W600
    val bold = FontWeight.W700
}

/**
 * Tabular figures (`DESIGN.md` §3.1): timers, weights, macros, charts, tables.
 * Proportional figures are allowed only inside prose, which never uses this.
 */
fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = "tnum")

/**
 * Figures read left-to-right in every script, including RTL: time is not mirrored
 * and a unit never swaps sides of its value (`DESIGN.md` §11.5, §3.4).
 */
fun TextStyle.ltrFigures(): TextStyle = copy(textDirection = TextDirection.Ltr)
