@file:Suppress("MagicNumber")

package app.openhealth.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * The one ramp of `DESIGN.md` §8.3: five steps from `l0` (nothing) to `l4` (the accent),
 * used by heatmaps, body maps, macro bars and progress bars so "more" always looks the
 * same. A step is a `surface-2` track plus the accent at the stated mix; `l4` is the
 * accent itself. The renderer draws the track and then the overlay, so the result is the
 * documented colour composited on a card.
 */
@Immutable
data class RampStep(val track: Color, val overlay: Color?)

object OhRamp {
    private const val L1_MIX = 0.30f
    private const val L2_MIX = 0.55f
    private const val L3_MIX = 0.78f

    fun l0(colors: OhColors): RampStep = RampStep(colors.surface2, null)

    fun l1(colors: OhColors): RampStep = RampStep(colors.surface2, colors.acc.copy(alpha = L1_MIX))

    fun l2(colors: OhColors): RampStep = RampStep(colors.surface2, colors.acc.copy(alpha = L2_MIX))

    fun l3(colors: OhColors): RampStep = RampStep(colors.surface2, colors.acc.copy(alpha = L3_MIX))

    fun l4(colors: OhColors): RampStep = RampStep(colors.acc, null)

    /**
     * The step for a 0..1 share: any load earns `l1`, then the quartiles climb. A zero
     * share is `l0`, so an untrained muscle keeps the body base (`DESIGN.md` §9.1).
     */
    fun of(colors: OhColors, share: Float): RampStep = when {
        share <= 0f -> l0(colors)
        share <= 0.25f -> l1(colors)
        share <= 0.50f -> l2(colors)
        share <= 0.75f -> l3(colors)
        else -> l4(colors)
    }
}
