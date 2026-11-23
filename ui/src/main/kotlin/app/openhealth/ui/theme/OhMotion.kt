@file:Suppress("MagicNumber")

package app.openhealth.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.runtime.Immutable

/**
 * The motion tokens of `DESIGN.md` §6.9 and the press tiers of §6.1.
 *
 * Only transform and opacity animate. With reduced motion every [duration] becomes
 * zero: the change still happens, it just does not perform.
 */
@Immutable
data class OhMotion(
    /** 140 ms: press, toggle, chip. */
    val fast: Int = 140,
    /** 220 ms: toast, card, fade. */
    val med: Int = 220,
    /** 380 ms: drill in. */
    val pagePush: Int = 380,
    /** 340 ms: back. */
    val pagePop: Int = 340,
    /** 340 ms: sheet rise. */
    val sheet: Int = 340,
    /** 280 ms: dialog pop. */
    val dialog: Int = 280,
    /** 700 ms: a bar's fill. */
    val progress: Int = 700,
    /** The one curve: decelerating, no overshoot. */
    val ease: Easing = CubicBezierEasing(0.32f, 0.72f, 0f, 1f),
    /** Controls (button, chip, segment, stepper, tick). */
    val pressScale: Float = 0.97f,
    /** Small round controls. */
    val pressScaleSmall: Float = 0.92f,
    /** Cards. */
    val pressScaleCard: Float = 0.985f,
    /** Tab and Train disc. */
    val pressScaleTab: Float = 0.93f,
    /** The system's "Remove animations" setting. */
    val reduced: Boolean = false,
) {
    /** Zero under reduced motion, the token otherwise. */
    fun duration(ms: Int): Int = if (reduced) 0 else ms

    /** [fast], reduced-motion aware. */
    fun fastDuration(): Int = duration(fast)
}
