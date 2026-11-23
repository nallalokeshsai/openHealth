package app.openhealth.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * `DESIGN.md` §12.5, computed from the token values themselves in both themes.
 *
 * Every pair the section states is measured here; a token that drops below its ratio
 * fails `./gradlew check` with the number that proves it. Translucent tokens are
 * composited over the surface they are read on before measuring, because that is what
 * the eye sees.
 */
class OhContrastTest {

    private val accents = OhAccent.entries

    private fun themes(): List<Pair<String, OhColors>> = accents.flatMap { accent ->
        listOf(
            "dark/${accent.key}" to ohDarkColors(accent),
            "light/${accent.key}" to ohLightColors(accent),
        )
    }

    private fun baseThemes(): List<Pair<String, OhColors>> = listOf(
        "dark" to ohDarkColors(),
        "light" to ohLightColors(),
    )

    /** The three sRGB channels of a colour, alpha dropped. */
    private fun channels(c: Color): FloatArray = floatArrayOf(c.red, c.green, c.blue)

    /** Alpha-composites [fg] over an opaque background, in sRGB, as a screen does. */
    private fun overChannels(fg: Color, bg: FloatArray): FloatArray {
        val a = fg.alpha
        val f = channels(fg)
        return FloatArray(3) { i -> f[i] * a + bg[i] * (1f - a) }
    }

    private fun channel(c: Float): Double {
        val v = c.toDouble()
        return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }

    private fun luminance(c: FloatArray): Double =
        0.2126 * channel(c[0]) + 0.7152 * channel(c[1]) + 0.0722 * channel(c[2])

    private fun ratio(fg: Color, bg: Color): Double {
        val background = channels(bg)
        return ratioChannels(overChannels(fg, background), background)
    }

    private fun ratioChannels(fg: FloatArray, bg: FloatArray): Double {
        val a = luminance(fg)
        val b = luminance(bg)
        val light = maxOf(a, b)
        val dark = minOf(a, b)
        return (light + 0.05) / (dark + 0.05)
    }

    /** A translucent fill composited over an opaque base, ready for an ink to sit on. */
    private fun tinted(tint: Color, base: Color): FloatArray = overChannels(tint, channels(base))

    private fun assertAtLeast(label: String, fg: Color, bg: Color, minimum: Double) {
        val measured = ratio(fg, bg)
        assertRatios(label, measured, minimum)
    }

    /** Measures an ink over a translucent fill that was composited over an opaque base. */
    private fun assertAtLeastOn(label: String, fg: Color, bg: FloatArray, minimum: Double) {
        val measured = ratioChannels(overChannels(fg, bg), bg)
        assertRatios(label, measured, minimum)
    }

    private fun assertRatios(label: String, measured: Double, minimum: Double) {
        assertTrue(
            measured >= minimum,
            "$label measured ${"%.2f".format(measured)}:1, below the $minimum:1 of DESIGN.md §12.5",
        )
    }

    @Test
    fun bodyTextIsReadableOnBothSurfaces() {
        baseThemes().forEach { (theme, colors) ->
            assertAtLeast("$theme label on bg", colors.label, colors.bg, 4.5)
            assertAtLeast("$theme label on surface", colors.label, colors.surface, 4.5)
        }
    }

    @Test
    fun secondaryAndQuietTextAreReadable() {
        baseThemes().forEach { (theme, colors) ->
            listOf(
                "bg" to colors.bg,
                "surface" to colors.surface,
            ).forEach { (surfaceName, surface) ->
                assertAtLeast("$theme label-2 on $surfaceName", colors.label2, surface, 4.5)
                assertAtLeast("$theme label-dim on $surfaceName", colors.labelDim, surface, 4.5)
            }
        }
    }

    @Test
    fun everyAccentInkIsReadableInBothThemes() {
        themes().forEach { (theme, colors) ->
            assertAtLeast("$theme acc-ink on bg", colors.accInk, colors.bg, 4.5)
            assertAtLeast("$theme acc-ink on surface", colors.accInk, colors.surface, 4.5)
        }
    }

    @Test
    fun everyAccentCarriesDarkInk() {
        themes().forEach { (theme, colors) ->
            assertAtLeast("$theme on-acc on acc", colors.onAcc, colors.acc, 4.5)
        }
    }

    @Test
    fun accTwoIsAGraphicShade() {
        themes().forEach { (theme, colors) ->
            assertAtLeast("$theme acc-2 on surface", colors.acc2, colors.surface, 3.0)
        }
    }

    @Test
    fun statusInksAreReadable() {
        baseThemes().forEach { (theme, colors) ->
            listOf(
                "green-ink" to colors.greenInk,
                "orange-ink" to colors.orangeInk,
                "gold-ink" to colors.goldInk,
                "red-ink" to colors.redInk,
                "teal-ink" to colors.tealInk,
                "blue-ink" to colors.blueInk,
                "win-ink" to colors.winInk,
            ).forEach { (name, ink) ->
                assertAtLeast("$theme $name on bg", ink, colors.bg, 4.5)
                assertAtLeast("$theme $name on surface", ink, colors.surface, 4.5)
            }
        }
    }

    @Test
    fun graphicOnlyInksReachThreeToOne() {
        baseThemes().forEach { (theme, colors) ->
            listOf(
                "streak-ink" to colors.streakInk,
                "frost" to colors.frost,
            ).forEach { (name, ink) ->
                assertAtLeast("$theme $name on bg", ink, colors.bg, 3.0)
                assertAtLeast("$theme $name on surface", ink, colors.surface, 3.0)
            }
        }
    }

    @Test
    fun theFocusRingIsVisible() {
        baseThemes().forEach { (theme, colors) ->
            assertAtLeast("$theme focus ring on bg", colors.accInk, colors.bg, 3.0)
            assertAtLeast("$theme focus ring on surface", colors.accInk, colors.surface, 3.0)
        }
    }

    @Test
    fun theFatLayerIsAVisibleGraphic() {
        baseThemes().forEach { (theme, colors) ->
            assertAtLeast("$theme fat on bg", colors.fat, colors.bg, 3.0)
            assertAtLeast("$theme fat on surface", colors.fat, colors.surface, 3.0)
            assertAtLeast("$theme fat on surface-2", colors.fat, colors.surface2, 3.0)
        }
    }

    /**
     * The recovery map's three steps are the palette's green, orange and red
     * (`DESIGN.md` Appendix A). They are fills with a legend, a selected-muscle line
     * and a hatch, so the section does not claim 4.5:1 for them; what must hold is that
     * each theme uses its own documented values and that the three steps stay apart.
     */
    @Test
    fun textOnTintedFillsIsReadable() {
        baseThemes().forEach { (theme, colors) ->
            // §12.5 guarantees a text ink on `bg` and `surface`; a tinted fill is
            // measured on those two, the surfaces a control is placed on.
            listOf("bg" to colors.bg, "surface" to colors.surface).forEach { (surfaceName, base) ->
                assertAtLeastOn(
                    "$theme danger label on danger fill over $surfaceName",
                    colors.label,
                    tinted(colors.dangerFill, base),
                    4.5,
                )
                assertAtLeastOn(
                    "$theme tinted button label on acc-soft over $surfaceName",
                    colors.label,
                    tinted(colors.accSoft, base),
                    4.5,
                )
            }
            listOf("bg" to colors.bg, "surface" to colors.surface).forEach { (surfaceName, base) ->
                assertAtLeastOn(
                    "$theme toast message on toast fill over $surfaceName",
                    colors.label,
                    tinted(colors.toastFill, base),
                    4.5,
                )
                assertAtLeastOn(
                    "$theme timer clock on timer bar over $surfaceName",
                    colors.label,
                    tinted(colors.timerBarFill, base),
                    4.5,
                )
                assertAtLeastOn(
                    "$theme work clock on timer bar over $surfaceName",
                    colors.accInk,
                    tinted(colors.timerBarFill, base),
                    4.5,
                )
                assertAtLeastOn(
                    "$theme inactive tab label on the bar over $surfaceName",
                    colors.label2,
                    tinted(colors.barFill, base),
                    4.5,
                )
            }
        }
    }

    @Test
    fun theRecoveryStepsAreTheDocumentedTokens() {
        baseThemes().forEach { (theme, colors) ->
            assertTrue(
                colors.recReady == colors.green,
                "$theme rec-ready must be green (DESIGN.md Appendix A)",
            )
            assertTrue(
                colors.recEasing == colors.orange,
                "$theme rec-easing must be orange (DESIGN.md Appendix A)",
            )
            assertTrue(
                colors.recActive == colors.red,
                "$theme rec-active must be red (DESIGN.md Appendix A)",
            )
            val steps = listOf(colors.recReady, colors.recEasing, colors.recActive)
            steps.forEachIndexed { i, first ->
                steps.drop(i + 1).forEach { second ->
                    assertTrue(
                        first != second,
                        "$theme recovery steps $i and ${steps.indexOf(second)} are the same colour",
                    )
                }
            }
        }
    }

    @Test
    fun lightIsADistinctVerifiedSibling() {
        val dark = ohDarkColors()
        val light = ohLightColors()
        assertTrue(dark.dark, "The dark theme is canonical (DESIGN.md §1.10)")
        assertTrue(!light.dark, "The light theme is derived, not an inversion (DESIGN.md §12.2)")
        assertTrue(dark.bg != light.bg, "Dark and light pages differ")
        assertTrue(dark.label != light.label, "Dark and light text differ")
    }
}
