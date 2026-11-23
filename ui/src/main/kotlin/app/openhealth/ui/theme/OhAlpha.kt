@file:Suppress("MagicNumber", "ktlint:standard:property-naming")

package app.openhealth.ui.theme

/**
 * Opacity values `DESIGN.md` states in words, as numbers (`§6.3`, `§5.7`, `§2.6`,
 * `§5.16`, `§4.3`, `§5.19`). Components never write an alpha literal.
 */
object OhAlpha {
    /** Fully opaque. */
    const val full: Float = 1f

    /** §6.3: a disabled control. */
    const val disabled: Float = 0.32f

    /** §6.3: a disabled row. */
    const val disabledRow: Float = 0.45f

    /** §5.7: the Danger button's fill is `red` at 15 %. The label is `label` ink. */
    const val danger: Float = 0.15f

    /** §2.6: `acc-soft` is the accent at 16 %. */
    const val soft: Float = 0.16f

    /** §2.6: `acc-line` is the accent at 38 %. */
    const val line: Float = 0.38f

    /** §4.3: the tab bar and top bar sit at 94 %. */
    const val bar: Float = 0.94f

    /** §5.18: the toast sits at 88 %. */
    const val toast: Float = 0.88f

    /** §5.19: the timer bar sits at 97 %. */
    const val timerBar: Float = 0.97f

    /** §5.16: the sheet scrim is 40 % black. */
    const val scrim: Float = 0.40f

    /** §5.7: a pressed button is 90 % opacity on the same fill. */
    const val pressed: Float = 0.90f

    /** §5.19: a paused timer is at 50 %. */
    const val paused: Float = 0.50f
}
