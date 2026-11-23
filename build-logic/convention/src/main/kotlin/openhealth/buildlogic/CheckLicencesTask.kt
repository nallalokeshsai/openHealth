package openhealth.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

/**
 * Fails when a resolved dependency is not on the licence allowlist in
 * gradle/licences/allowed.tsv. The list records a licence per coordinate, so a GPL,
 * LGPL or AGPL dependency cannot enter a client module without failing the build
 * (Tech-Stack.md §11, §16; pr.md §12.4).
 */
abstract class CheckLicencesTask : DefaultTask() {

    /** `group:module` of every resolved runtime dependency. */
    @get:Input
    abstract val modules: ListProperty<String>

    /** Allowlist patterns, `group:module` with `*` as a wildcard. */
    @get:Input
    abstract val allowed: ListProperty<String>

    @TaskAction
    fun check() {
        val patterns = allowed.get().map { it to globToRegex(it) }
        val violations = modules.get().filter { coordinate ->
            patterns.none { (_, regex) -> regex.matches(coordinate) }
        }
        if (violations.isNotEmpty()) {
            throw GradleException(
                "checkLicences failed: add each coordinate to gradle/licences/allowed.tsv " +
                    "with its licence, and refuse anything in the GPL family (pr.md §12.4).\n" +
                    violations.joinToString("\n") { "  $it" },
            )
        }
    }

    private fun globToRegex(pattern: String): Regex {
        val escaped = pattern.split("*").joinToString(".*") { Regex.escape(it) }
        return Regex("^$escaped$")
    }
}
