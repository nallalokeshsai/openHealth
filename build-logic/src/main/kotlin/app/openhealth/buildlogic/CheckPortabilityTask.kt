package app.openhealth.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Guards `Tech-Stack.md` §3: the pure modules may not import `android.*` or `androidx.*`.
 *
 * The task is fed file trees, not directories, so the callers can exclude the Android
 * implementations under `integrations/` and any build output before the scan runs.
 */
abstract class CheckPortabilityTask : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    @TaskAction
    fun check() {
        val forbidden = Regex("^\\s*import\\s+(android|androidx)\\.")
        val offenders = mutableListOf<String>()
        var scanned = 0

        sources.files.sortedBy { it.invariantSeparatorsPath }.forEach { file ->
            if (!file.isFile) return@forEach
            if (file.extension !in SCANNED_EXTENSIONS) return@forEach
            scanned++
            file.readLines().forEachIndexed { index, line ->
                if (forbidden.containsMatchIn(line)) {
                    offenders += "${file.invariantSeparatorsPath}:${index + 1}: ${line.trim()}"
                }
            }
        }

        if (offenders.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("checkPortability failed: pure modules must not import android.* or androidx.*")
                    appendLine("(Tech-Stack.md §3). Offending lines:")
                    offenders.forEach { appendLine("  $it") }
                },
            )
        }
        logger.lifecycle("checkPortability: scanned $scanned source file(s), no Android imports.")
    }

    private companion object {
        val SCANNED_EXTENSIONS = setOf("kt", "kts", "java")
    }
}
