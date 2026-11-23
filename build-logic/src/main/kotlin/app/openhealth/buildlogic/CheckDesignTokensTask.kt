package app.openhealth.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Guards DESIGN.md §0.2 and §12.6: the token set is declared once, in `ui/theme`. A production
 * screen may not write a `Color(0x…)` literal or a raw `.dp` literal; it reads `OhTheme.colors`,
 * `OhTheme.space`, `OhTheme.radius` or `OhTheme.geometry`. The B1 exit requires this to fail if one
 * appears.
 *
 * Test sources are excluded: a screenshot test legitimately compares colours and sizes its
 * preview. Comments are stripped before matching, string literals are respected, so a comment may
 * still quote a hex and a `//` inside a string is not a comment.
 */
abstract class CheckDesignTokensTask : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    @TaskAction
    fun check() {
        val colorLiteral = Regex("""Color\(\s*(?:[A-Za-z_][A-Za-z0-9_]*\s*=\s*)?(?:0x[0-9a-fA-F]+|-?\d)""")
        val dpLiteral = Regex("""\b\d+(?:\.\d+)?f?\.dp\b""")
        val offenders = mutableListOf<String>()
        var scanned = 0

        sources.files.sortedBy { it.invariantSeparatorsPath }.forEach { file ->
            if (!file.isFile || file.extension != "kt") return@forEach
            scanned++
            stripComments(file.readText()).lineSequence().forEachIndexed { index, line ->
                if (colorLiteral.containsMatchIn(line)) {
                    offenders += "${file.invariantSeparatorsPath}:${index + 1}: Color( literal: ${line.trim()}"
                }
                if (dpLiteral.containsMatchIn(line)) {
                    offenders += "${file.invariantSeparatorsPath}:${index + 1}: raw .dp literal: ${line.trim()}"
                }
            }
        }

        if (offenders.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine(
                        "checkDesignTokens failed: a colour or dp value outside ui/theme " +
                            "(DESIGN.md §0.2, §12.6). Use OhTheme tokens or add the value to " +
                            "DESIGN.md Appendix A first. Offending lines:",
                    )
                    offenders.forEach { appendLine("  $it") }
                },
            )
        }
        logger.lifecycle("checkDesignTokens: scanned $scanned screen file(s), no colour or dp literals.")
    }

    /** Removes line and block comments, keeps strings and line breaks (so line numbers stay true). */
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
                STRING, CHAR -> {
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
