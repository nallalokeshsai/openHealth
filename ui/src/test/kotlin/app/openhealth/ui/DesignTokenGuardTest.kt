@file:Suppress("LongMethod", "CyclomaticComplexMethod", "NestedBlockDepth", "ComplexCondition")

package app.openhealth.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The unit-test half of the B1 token guard (BUILD-ROADMAP.md B1 exit): no `Color(` literal and no
 * raw `.dp` literal outside `ui/theme`. The Gradle task `checkDesignTokens` is the primary gate;
 * this test proves the same rule from inside the test suite, and it fails if the matcher stops
 * matching. Test sources are deliberately not scanned; a screenshot test compares colours.
 */
class DesignTokenGuardTest {

    private val colorLiteral = Regex(
        """Color\(\s*(?:[A-Za-z_][A-Za-z0-9_]*\s*=\s*)?(?:0x[0-9a-fA-F]+|-?\d)"""
    )
    private val dpLiteral = Regex("""\b\d+(?:\.\d+)?f?\.dp\b""")

    @Test
    fun theMatcherStillCatchesBothLiteralKinds() {
        assertTrue(colorLiteral.containsMatchIn("val c = Color(0xFF123456)"), "Color( literal not matched")
        assertTrue(colorLiteral.containsMatchIn("val c = Color(0x80FFFFFF)"), "alpha Color( literal not matched")
        assertTrue(colorLiteral.containsMatchIn("val c = Color(color = 0xFF123456)"), "named argument not matched")
        assertTrue(dpLiteral.containsMatchIn("Modifier.height(24.dp)"), "raw .dp not matched")
        assertTrue(dpLiteral.containsMatchIn("val h = 0.5.dp"), "fractional .dp not matched")
        assertTrue(dpLiteral.containsMatchIn("val h = 16f.dp"), "float literal .dp not matched")
        assertTrue(!colorLiteral.containsMatchIn("val c = OhTheme.colors.bg"), "token read was flagged")
        assertTrue(!dpLiteral.containsMatchIn("OhTheme.space.space6"), "token read was flagged")
    }

    @Test
    fun noColourOrDpLiteralOutsideTheTheme() {
        val roots = listOf(
            File("src/main/kotlin").canonicalFile,
            File("src/debug/kotlin").canonicalFile,
            File("../app-android/src/main/kotlin").canonicalFile
        )
        val offenders = mutableListOf<String>()
        var scanned = 0
        for (root in roots) {
            if (!root.isDirectory) continue
            root.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .filterNot { it.invariantSeparatorsPath.contains("/ui/theme/") }
                .forEach { file ->
                    scanned++
                    stripComments(file.readText()).lineSequence().forEachIndexed { index, line ->
                        if (colorLiteral.containsMatchIn(line) || dpLiteral.containsMatchIn(line)) {
                            offenders += "${file.path}:${index + 1}: ${line.trim()}"
                        }
                    }
                }
        }
        assertTrue(scanned > 0, "the guard scanned no files; check the working directory")
        assertTrue(
            offenders.isEmpty(),
            "Colour or dp literals outside ui/theme (DESIGN.md §0.2):\n${offenders.joinToString("\n")}"
        )
    }

    /** Same scanner as the Gradle task: comments out, strings and line numbers preserved. */
    private fun stripComments(source: String): String {
        val out = StringBuilder(source.length)
        var i = 0
        var state = CODE
        while (i < source.length) {
            val c = source[i]
            val next = if (i + 1 < source.length) source[i + 1] else '\u0000'
            when (state) {
                CODE -> when {
                    c == '/' && next == '/' -> {
                        state = LINE_COMMENT
                        i += 2
                    }
                    c == '/' && next == '*' -> {
                        state = BLOCK_COMMENT
                        i += 2
                    }
                    c == '"' -> {
                        state = STRING
                        out.append(c)
                        i++
                    }
                    c == '\'' -> {
                        state = CHAR
                        out.append(c)
                        i++
                    }
                    else -> {
                        out.append(c)
                        i++
                    }
                }
                LINE_COMMENT -> {
                    if (c == '\n') {
                        state = CODE
                        out.append('\n')
                    }
                    i++
                }
                BLOCK_COMMENT -> {
                    if (c == '*' && next == '/') {
                        state = CODE
                        i += 2
                    } else {
                        if (c == '\n') out.append('\n')
                        i++
                    }
                }
                else -> {
                    out.append(c)
                    if (c == '\\' && i + 1 < source.length) {
                        out.append(source[i + 1])
                        i += 2
                    } else {
                        if ((state == STRING && c == '"') || (state == CHAR && c == '\'')) {
                            state = CODE
                        }
                        i++
                    }
                }
            }
        }
        return out.toString()
    }

    private companion object {
        const val CODE = 0
        const val LINE_COMMENT = 1
        const val BLOCK_COMMENT = 2
        const val STRING = 3
        const val CHAR = 4
    }
}
