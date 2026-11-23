package openhealth.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * The design-system guard of `DESIGN.md` §12.6 and `BUILD-ROADMAP.md` §B1.
 *
 * Outside the theme package, a screen may not write a colour literal, a raw `.dp`
 * or `.sp`, or read `MaterialTheme`: every value comes from `OhTheme`, so the closed
 * token set cannot drift. A line inside a comment is ignored.
 */
abstract class CheckDesignTokensTask : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    /** Relative directories whose files are the token declarations and may hold literals. */
    @get:Input
    abstract val allowedPrefixes: ListProperty<String>

    private data class Rule(val pattern: Regex, val description: String)

    @TaskAction
    fun check() {
        val rules = listOf(
            Rule(Regex("""\bColor\(\s*(-?0x|[-0-9])"""), "a Color(...) literal"),
            Rule(Regex("""\b\d+(\.\d+)?\.dp\b"""), "a raw .dp literal"),
            Rule(Regex("""\b\d+(\.\d+)?\.sp\b"""), "a raw .sp literal"),
            Rule(Regex("""\bMaterialTheme\."""), "a MaterialTheme reference"),
        )
        val allowed = allowedPrefixes.get()
        val violations = mutableListOf<String>()
        sources.files
            .filter { it.isFile && it.name.endsWith(".kt") }
            .sortedBy { it.path }
            .forEach { file ->
                val path = file.invariantSeparatorsPath
                if (allowed.any { path.contains(it) }) return@forEach
                file.readLines().forEachIndexed { index, line ->
                    val trimmed = line.trimStart()
                    if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) {
                        return@forEachIndexed
                    }
                    rules.forEach { rule ->
                        if (rule.pattern.containsMatchIn(line)) {
                            violations += "${file.path}:${index + 1}: ${rule.description}: $trimmed"
                        }
                    }
                }
            }
        if (violations.isNotEmpty()) {
            throw GradleException(
                "checkDesignTokens failed: values must come from OhTheme (DESIGN.md §12.6, Appendix B).\n" +
                    violations.joinToString("\n"),
            )
        }
    }
}
