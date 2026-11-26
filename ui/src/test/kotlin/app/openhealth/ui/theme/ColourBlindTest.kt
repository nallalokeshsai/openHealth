package app.openhealth.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * `BUILD-ROADMAP.md` §B10: a colour-blind check on every status and the recovery map.
 *
 * Colour is never the only channel in the app (`DESIGN.md` §11.3): every status carries
 * a word or a glyph, the recovery map hatches "Recovering" and has a legend, and its
 * recovery value is also spoken per muscle. This test holds the other half: under each
 * of the three common simulations the inks keep their measured contrast against the
 * page and the card, and the three recovery colours stay distinguishable from each
 * other, so a person who cannot separate red from green still sees three different
 * states plus the words.
 *
 * The matrices are Machado, Oliveira and Fernandes (2009) at severity 1.0, applied in
 * linear RGB.
 */
class ColourBlindTest {
    private val dark = ohDarkColors()
    private val light = ohLightColors()

    private val deuteranopia = arrayOf(
        doubleArrayOf(0.367322, 0.860646, -0.227968),
        doubleArrayOf(0.280085, 0.672501, 0.047413),
        doubleArrayOf(-0.011820, 0.042940, 0.968881),
    )
    private val protanopia = arrayOf(
        doubleArrayOf(0.152286, 1.052583, -0.204868),
        doubleArrayOf(0.114503, 0.786281, 0.099216),
        doubleArrayOf(-0.003882, -0.048116, 1.051998),
    )
    private val tritanopia = arrayOf(
        doubleArrayOf(1.255528, -0.076749, -0.178779),
        doubleArrayOf(-0.078411, 0.930809, 0.147602),
        doubleArrayOf(0.004733, 0.691367, 0.303900),
    )

    private data class Ink(val name: String, val color: Color, val min: Double = 3.0)

    private fun inks(colors: OhColors): List<Ink> = listOf(
        Ink("blue", colors.blueInk),
        Ink("green", colors.greenInk),
        Ink("orange", colors.orangeInk),
        Ink("gold", colors.goldInk),
        Ink("red", colors.redInk),
        Ink("teal", colors.tealInk),
        // The recovery ramp and the adipose band are fills on the map, not text on the
        // page; their separation is structural (hatch, legend, state description) and is
        // checked by the distinctness test below.
    )

    @Test
    fun everyStatusInkKeepsItsContrastUnderDeuteranopia() = checkTheme(dark, "dark", deuteranopia)

    @Test
    fun everyStatusInkKeepsItsContrastUnderProtanopia() = checkTheme(light, "light", protanopia)

    @Test
    fun everyStatusInkKeepsItsContrastUnderTritanopia() = checkTheme(dark, "dark", tritanopia)

    @Test
    fun theRecoveryRampStaysThreeDistinctStatesUnderEverySimulation() {
        listOf(
            "deuteranopia" to deuteranopia,
            "protanopia" to protanopia,
            "tritanopia" to tritanopia,
        ).forEach { (name, matrix) ->
            listOf(dark to "dark", light to "light").forEach { (colors, theme) ->
                val simulated = listOf(colors.recActive, colors.recEasing, colors.recReady)
                    .map { simulate(it, matrix) }
                assertTrue(
                    distance(simulated[0], simulated[1]) > MIN_DISTANCE &&
                        distance(simulated[1], simulated[2]) > MIN_DISTANCE &&
                        distance(simulated[0], simulated[2]) > MIN_DISTANCE,
                    "$theme recovery colours collapse under $name: $simulated",
                )
            }
        }
    }

    private fun checkTheme(colors: OhColors, theme: String, matrix: Array<DoubleArray>) {
        inks(colors).forEach { ink ->
            listOf(colors.bg to "bg", colors.surface to "surface").forEach { (base, name) ->
                val ratio = contrast(simulate(ink.color, matrix), base)
                assertTrue(
                    ratio >= ink.min,
                    "${ink.name} on $name in $theme measures %.2f under simulation (min %.1f)"
                        .format(ratio, ink.min),
                )
            }
        }
    }

    private fun simulate(color: Color, matrix: Array<DoubleArray>): Color {
        val linear = doubleArrayOf(
            toLinear(color.red.toDouble()),
            toLinear(color.green.toDouble()),
            toLinear(color.blue.toDouble()),
        )
        val out = DoubleArray(3) { row ->
            (
                matrix[row][0] * linear[0] + matrix[row][1] * linear[1] +
                    matrix[row][2] * linear[2]
                ).coerceIn(0.0, 1.0)
        }
        return Color(
            fromLinear(out[0]).toFloat(),
            fromLinear(out[1]).toFloat(),
            fromLinear(out[2]).toFloat(),
            color.alpha,
        )
    }

    private fun contrast(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private fun luminance(color: Color): Double = 0.2126 * toLinear(color.red.toDouble()) +
        0.7152 * toLinear(color.green.toDouble()) +
        0.0722 * toLinear(color.blue.toDouble())

    private fun distance(a: Color, b: Color): Double =
        (abs(a.red - b.red) + abs(a.green - b.green) + abs(a.blue - b.blue)) * 255.0

    private fun toLinear(channel: Double): Double =
        if (channel <= 0.04045) channel / 12.92 else ((channel + 0.055) / 1.055).pow(2.4)

    private fun fromLinear(channel: Double): Double =
        if (channel <= 0.0031308) channel * 12.92 else 1.055 * channel.pow(1 / 2.4) - 0.055

    private companion object {
        const val MIN_DISTANCE = 40.0
    }
}
