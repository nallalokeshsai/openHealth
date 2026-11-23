@file:Suppress("MagicNumber")

package app.openhealth.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * The 11 type roles of DESIGN.md §3.2 and Appendix A.3: nine text roles plus `display` and
 * `timer`, which are fixed-box display roles rather than prose sizes.
 *
 * The family is the platform UI font (§3.1): Roboto on Android through `FontFamily.Default`. No
 * typeface ships, so there is nothing to license or bundle; Inter is the only approved alternative
 * and only after a dated decision in DESIGN.md.
 *
 * Numerals are tabular wherever a figure can change or align (§3.1), so `tnum` is on every role:
 * proportional figures are allowed only inside prose, and using one feature everywhere is the
 * cheaper rule to keep true.
 */
@Immutable
data class OhType(
    val caption2: TextStyle = role(11.sp, 1.33f, 0f, FontWeight.Normal),
    val caption: TextStyle = role(12.sp, 1.33f, 0f, FontWeight.Normal),
    val footnote: TextStyle = role(13.sp, 1.38f, -0.004f, FontWeight.Normal),
    val subhead: TextStyle = role(15.sp, 1.33f, -0.010f, FontWeight.Normal),
    val callout: TextStyle = role(16.sp, 1.29f, -0.010f, FontWeight.Normal),
    val body: TextStyle = role(17.sp, 1.29f, -0.010f, FontWeight.Normal),
    val title3: TextStyle = role(20.sp, 1.20f, -0.021f, FontWeight.Normal),
    val title2: TextStyle = role(22.sp, 1.20f, -0.021f, FontWeight.Normal),
    val large: TextStyle = role(34.sp, 1.06f, -0.028f, FontWeight.Bold),
    val display: TextStyle = role(28.sp, 1.10f, -0.026f, FontWeight.SemiBold),
    val timer: TextStyle = role(28.sp, 1.10f, -0.026f, FontWeight.SemiBold),
    /** The `large` role drops to 30 sp below 420 dp (§3.2): same role, smaller end of its range. */
    val largeCompact: TextStyle = role(30.sp, 1.06f, -0.028f, FontWeight.Bold),
    /** The `timer` role's 64 sp end for the full-screen session timer (§5.19), not a 12th role. */
    val timerFull: TextStyle = role(64.sp, 1.10f, -0.026f, FontWeight.Bold),
    /**
     * Component styles DESIGN.md states outside the scale: chip text is 14/400, 500 when selected
     * (§5.9). They live in the theme so no screen writes an sp value of its own.
     */
    val chip: TextStyle = role(14.sp, 1.29f, -0.010f, FontWeight.Normal)
)

/** §3.3 allows 400, 500, 600 and 700 only. A role's size, leading and tracking always move together. */
fun TextStyle.mediumWeight(): TextStyle = copy(fontWeight = FontWeight.Medium)

fun TextStyle.semiboldWeight(): TextStyle = copy(fontWeight = FontWeight.SemiBold)

fun TextStyle.boldWeight(): TextStyle = copy(fontWeight = FontWeight.Bold)

private fun role(size: TextUnit, leading: Float, trackingEm: Float, weight: FontWeight): TextStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = size,
    lineHeight = size * leading,
    letterSpacing = size * trackingEm,
    fontWeight = weight,
    fontFeatureSettings = "tnum"
)
