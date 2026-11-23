@file:Suppress("MagicNumber")

package app.openhealth.ui.theme

import androidx.compose.ui.graphics.Color
import java.util.Locale
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The DESIGN.md §12.5 contrast audit, computed from the token values rather than trusted from a
 * table. It runs for both themes and every accent preset, so a changed hex fails `check` before it
 * reaches a screen.
 *
 * Alpha tokens are composited over the surface they sit on: `label-2` and `label-dim` are rgba
 * values, and their measured ratio depends on what is behind them.
 */
class ContrastTest {

    private val bodyMinimum = 4.5
    private val graphicMinimum = 3.0

    @Test
    fun label2AndLabelDimClearBodyTextInBothThemes() {
        forEachThemeAndPreset { theme, colors ->
            for (background in listOf("bg" to colors.bg, "surface" to colors.surface)) {
                assertRatio(
                    colors.label2,
                    background.second,
                    bodyMinimum,
                    "$theme ${background.first}: label-2"
                )
                assertRatio(
                    colors.labelDim,
                    background.second,
                    bodyMinimum,
                    "$theme ${background.first}: label-dim"
                )
            }
            assertRatio(colors.label, colors.bg, bodyMinimum, "$theme bg: label")
            assertRatio(colors.label, colors.surface, bodyMinimum, "$theme surface: label")
        }
    }

    @Test
    fun everyPresetAccentInkClearsBodyText() {
        forEachThemeAndPreset { theme, colors ->
            assertRatio(colors.accInk, colors.bg, bodyMinimum, "$theme bg: acc-ink")
            assertRatio(colors.accInk, colors.surface, bodyMinimum, "$theme surface: acc-ink")
            // The focus ring is acc-ink at 2.5 dp (§11.1); it is a control, so 3:1 is the floor,
            // but it is measured against the same surfaces.
            assertRatio(colors.accInk, colors.surface, graphicMinimum, "$theme focus ring")
        }
    }

    @Test
    fun onAccentClearsBodyTextOnEveryPresetFill() {
        forEachTheme { theme, colors ->
            assertRatio(colors.onAcc, colors.acc, bodyMinimum, "$theme on-acc on acc")
        }
    }

    @Test
    fun statusInksClearBodyTextAndGraphicInksClearControls() {
        forEachTheme { theme, colors ->
            for (background in listOf("bg" to colors.bg, "surface" to colors.surface)) {
                for ((name, ink) in listOf(
                    "green-ink" to colors.greenInk,
                    "orange-ink" to colors.orangeInk,
                    "gold-ink" to colors.goldInk,
                    "red-ink" to colors.redInk,
                    "teal-ink" to colors.tealInk,
                    "blue-ink" to colors.blueInk
                )) {
                    assertRatio(ink, background.second, bodyMinimum, "$theme ${background.first}: $name")
                }
                assertRatio(
                    colors.streakInk,
                    background.second,
                    graphicMinimum,
                    "$theme ${background.first}: streak-ink"
                )
                assertRatio(
                    colors.frost,
                    background.second,
                    graphicMinimum,
                    "$theme ${background.first}: frost"
                )
            }
            // acc-2 is the pressed graphic shade and is only ever painted on a card or control
            // track, never on the page behind one (a progress fill sits in a card).
            assertRatio(colors.acc2, colors.surface, graphicMinimum, "$theme surface: acc-2")
        }
    }

    @Test
    fun fatLayerClearsTheGraphicFloorOnEverySurface() {
        forEachTheme { theme, colors ->
            for (background in listOf(
                "bg" to colors.bg,
                "surface" to colors.surface,
                "surface-2" to colors.surface2
            )) {
                assertRatio(colors.fat, background.second, graphicMinimum, "$theme ${background.first}: fat")
            }
        }
        // DESIGN.md §12.5 records why the light fat value was darkened from #C99A1E: it measured
        // 2.6:1 on white and was rejected.
        assertTrue(
            contrastRatio(Color(0xFFC99A1E), Color(0xFFFFFFFF)) < graphicMinimum,
            "the rejected light fat value #C99A1E no longer fails the graphic floor"
        )
    }

    private fun forEachTheme(block: (String, OhColors) -> Unit) {
        block("dark", darkColors(OhAccentPreset.Brand, highContrast = false))
        block("light", lightColors(OhAccentPreset.Brand, highContrast = false))
    }

    private fun forEachThemeAndPreset(block: (String, OhColors) -> Unit) {
        for (preset in OhAccentPreset.entries) {
            block("dark/${preset.key}", darkColors(preset, highContrast = false))
            block("light/${preset.key}", lightColors(preset, highContrast = false))
        }
    }

    private fun assertRatio(foreground: Color, background: Color, minimum: Double, label: String) {
        val ratio = contrastRatio(foreground, background)
        val measured = String.format(Locale.ROOT, "%.2f", ratio)
        val required = String.format(Locale.ROOT, "%.1f", minimum)
        assertTrue(ratio >= minimum, "$label is $measured:1, below the required $required:1")
    }

    companion object {
        /** WCAG 2.2 relative luminance and contrast ratio, after compositing a translucent colour. */
        fun contrastRatio(foreground: Color, background: Color): Double {
            val backgroundLuminance = luminance(background)
            val composited = foreground.compositeOver(background)
            val lighter = maxOf(luminance(composited), backgroundLuminance)
            val darker = minOf(luminance(composited), backgroundLuminance)
            return (lighter + 0.05) / (darker + 0.05)
        }

        fun Color.compositeOver(background: Color): Color {
            val a = alpha
            return Color(
                red = red * a + background.red * (1f - a),
                green = green * a + background.green * (1f - a),
                blue = blue * a + background.blue * (1f - a),
                alpha = 1f
            )
        }

        fun luminance(color: Color): Double {
            fun channel(value: Float): Double =
                if (value <= 0.03928f) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
            return 0.2126 * channel(color.red) +
                0.7152 * channel(color.green) +
                0.0722 * channel(color.blue)
        }
    }
}
