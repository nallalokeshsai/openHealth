package app.openhealth.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The nine spacing values of DESIGN.md §4.1 and Appendix A.4, plus the three sizes §4.1 explicitly
 * keeps out of the spacing scale: the hairline width, the touch target and the content column.
 *
 * Anything larger than 32 dp is a layout decision, not a spacing token, so it is not here. A
 * 44 dp target is a size, not spacing.
 */
@Immutable
data class OhSpace(
    val space0: Dp = 2.dp,
    val space1: Dp = 4.dp,
    val space2: Dp = 6.dp,
    val space3: Dp = 8.dp,
    val space4: Dp = 10.dp,
    val space5: Dp = 12.dp,
    val space6: Dp = 16.dp,
    val space7: Dp = 22.dp,
    val space8: Dp = 32.dp,
    /** §2.4: 0.5 dp, drawn as a sub-pixel line, never a 1 dp divider. */
    val hairline: Dp = 0.5.dp,
    /** §4.5: the minimum touch target. Invisible slop is drawn outside the painted box. */
    val touchTarget: Dp = 44.dp,
    /** §4.3: the content column is 560 dp maximum, centred. */
    val contentMaxWidth: Dp = 560.dp
)
