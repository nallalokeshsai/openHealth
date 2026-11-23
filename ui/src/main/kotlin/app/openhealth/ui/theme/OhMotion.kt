@file:Suppress("MagicNumber")

package app.openhealth.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.runtime.Immutable

/**
 * The motion tokens of DESIGN.md §6.9 and Appendix A.4, plus the press scales of §6.1.
 *
 * Only transform and opacity animate. Durations collapse to zero under reduced motion; the sheet
 * still appears, the page still changes, progress still moves (§6.9). Colour changes are instant
 * and therefore never carry a duration.
 */
@Immutable
data class OhMotion(
    /** Android "Remove animations" is on, or the caller asked for reduced motion. */
    val reduced: Boolean = false,
    /** Everything: decelerating, no overshoot. */
    val ease: Easing = EASE,
    /** A bar's fill to a new value (§6.9). */
    val progressEase: Easing = PROGRESS_EASE
) {
    /** Press, toggle, chip. */
    val fast: Int get() = if (reduced) 0 else 140

    /** Toast, card, fade. */
    val med: Int get() = if (reduced) 0 else 220

    /** Drill in: enters from the inline end. */
    val pagePush: Int get() = if (reduced) 0 else 380

    /** Back: previous page enters from −28 % at 70 % opacity. */
    val pagePop: Int get() = if (reduced) 0 else 340

    /** Bottom sheet rise. */
    val sheet: Int get() = if (reduced) 0 else 340

    /** 94 % scale plus fade. */
    val dialog: Int get() = if (reduced) 0 else 280

    /** A progress fill's move to a new value. */
    val progress: Int get() = if (reduced) 0 else 700

    /** §6.1 press tiers: controls `.97`, small round `.92`, cards `.985`, tab/disc `.93`. */
    val pressControl: Float = 0.97f
    val pressSmall: Float = 0.92f
    val pressCard: Float = 0.985f
    val pressTab: Float = 0.93f

    private companion object {
        val EASE = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)
        val PROGRESS_EASE = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
    }
}
