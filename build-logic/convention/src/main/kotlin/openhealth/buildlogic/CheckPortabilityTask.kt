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
 * Fails when a pure Kotlin module imports `android.*` or `androidx.*`
 * (Tech-Stack.md §3, §16). The module boundary is the portability guarantee.
 */
abstract class CheckPortabilityTask : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    @get:Input
    abstract val forbiddenPrefixes: ListProperty<String>

    @TaskAction
    fun check() {
        val violations = mutableListOf<String>()
        sources.files
            .filter { it.isFile }
            .sortedBy { it.path }
            .forEach { file ->
                file.readLines().forEachIndexed { index, line ->
                    val trimmed = line.trim()
                    val isComment = trimmed.startsWith("//") ||
                        trimmed.startsWith("*") ||
                        trimmed.startsWith("/*")
                    if (trimmed.isEmpty() || isComment) return@forEachIndexed
                    forbiddenPrefixes.get().forEach { prefix ->
                        val imported = trimmed.startsWith("import $prefix")
                        // A fully-qualified use is as much an Android dependency as an
                        // import: `android.util.Log` in a body must fail too.
                        val qualified = Regex("\\b${Regex.escape(prefix)}[A-Za-z]")
                            .containsMatchIn(trimmed)
                        if (imported || qualified) {
                            violations += "${file.path}:${index + 1}: $trimmed"
                        }
                    }
                }
            }
        if (violations.isNotEmpty()) {
            throw GradleException(
                "checkPortability failed: android.* and androidx.* are not allowed in a pure Kotlin module.\n" +
                    violations.joinToString("\n"),
            )
        }
    }
}
