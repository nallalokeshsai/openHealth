package openhealth.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File

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

    /** The directory whose files are the token declarations and may hold literals. */
    @get:Input
    abstract val allowedPrefixes: ListProperty<String>

    /** The module directory the allowlist is relative to. */
    @get:Input
    abstract val modulePath: Property<String>

    private data class Rule(val pattern: Regex, val description: String)

    @TaskAction
    fun check() {
        val rules = listOf(
            Rule(Regex("""\bColor\s*\("""), "a Color(...) literal"),
            Rule(Regex("""\bColor\.(?!Transparent\b)[A-Za-z]\w*"""), "a Color constant or factory"),
            Rule(Regex("""\b\d+(\.\d+)?f?\.dp\b"""), "a raw .dp literal"),
            Rule(Regex("""\b\d+(\.\d+)?f?\.sp\b"""), "a raw .sp literal"),
            Rule(Regex("""\bDp\s*\("""), "a raw Dp(...) value"),
            Rule(Regex("""\bTextUnit\s*\("""), "a raw TextUnit(...) value"),
            Rule(Regex("""\bMaterialTheme\b"""), "a MaterialTheme reference"),
        )
        val module = File(modulePath.get())
        val allowed = allowedPrefixes.get()
        val violations = mutableListOf<String>()
        var scanned = 0
        sources.files
            .filter { it.isFile && it.name.endsWith(".kt") }
            .sortedBy { it.path }
            .forEach { file ->
                val relative = file.relativeToOrNull(module)?.invariantSeparatorsPath
                if (relative != null && allowed.any { relative.startsWith(it) }) return@forEach
                scanned++
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
        check(scanned > 0) {
            "checkDesignTokens scanned no Kotlin files in ${modulePath.get()}: the guard would " +
                "pass by looking at nothing."
        }
        if (violations.isNotEmpty()) {
            throw GradleException(
                "checkDesignTokens failed: values must come from OhTheme (DESIGN.md §12.6, Appendix B).\n" +
                    violations.joinToString("\n"),
            )
        }
    }
}
