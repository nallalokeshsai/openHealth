package app.openhealth.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The six radii of DESIGN.md §4.2 and Appendix A.4, plus `full`. No seventh radius.
 *
 * `base` is the table's `r` (12 dp) and `card` the table's `r-card` (14 dp); Kotlin has no bare
 * `r` that reads well next to `sm`, so the neutral step is called `base`.
 */
@Immutable
data class OhRadius(
    val sm: Dp = 8.dp,
    val md: Dp = 10.dp,
    val base: Dp = 12.dp,
    val card: Dp = 14.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 22.dp,
    /** Chips, avatars, switches, the raised Train disc, pills. */
    val full: Dp = 999.dp
)
