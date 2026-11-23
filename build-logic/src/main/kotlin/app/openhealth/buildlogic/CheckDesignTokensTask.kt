package app.openhealth.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Guards DESIGN.md §0.2 and §12.6: the token set is declared once, in `ui/theme`. A screen may not
 * write a `Color(0x…)` literal or a raw `.dp` literal; it reads `OhTheme.colors`, `OhTheme.space`,
 * `OhTheme.radius` or `OhTheme.geometry`. The B1 exit requires this to fail if one appears.
 *
 * Comments are stripped before matching, so a comment may still quote a hex or a size. Test sources
 * are not scanned: a screenshot test legitimately sizes its preview and compares colours.
 */
abstract class CheckDesignTokensTask : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    @TaskAction
    fun check() {
        val colorLiteral = Regex("""Color\(\s*(0x[0-9a-fA-F]+|-?\d)""")
        val dpLiteral = Regex("""\b\d+(?:\.\d+)?\.dp\b""")
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

    /** Removes line and block comments but keeps line breaks, so reported line numbers stay true. */
    private fun stripComments(source: String): String {
        val withoutBlocks = Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL)
            .replace(source) { match -> "\n".repeat(match.value.count { it == '\n' }) }
        return withoutBlocks.lineSequence()
            .joinToString("\n") { line -> line.substringBefore("//") }
    }
}
