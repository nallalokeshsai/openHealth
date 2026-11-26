package openhealth.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

/**
 * Fails when a resolved runtime dependency belongs to an analytics, crash-reporting or
 * attribution SDK family (`pr.md` §6.23, §6.31; `BUILD-ROADMAP.md` §B10).
 *
 * The licence allowlist cannot carry this rule on its own: it is an allowlist of
 * coordinates, and a telemetry SDK with a permissive licence would be allowed in if
 * somebody added its row. This task is the deny list, and it is deliberately separate so
 * a future phase cannot pass it by editing `gradle/licences/allowed.tsv`.
 */
abstract class CheckTelemetryTask : DefaultTask() {

    /** `group:module` of every resolved runtime dependency. */
    @get:Input
    abstract val modules: ListProperty<String>

    /** Forbidden group prefixes; a coordinate matches when its group starts with one. */
    @get:Input
    abstract val deniedGroups: ListProperty<String>

    /**
     * Exact coordinates that are not telemetry and are required by a platform feature.
     * Empty almost everywhere; only the Wear Data Layer stack names itself here
     * (`docs/adr/0019`). A group prefix can never be allowed, only a whole coordinate.
     */
    @get:Input
    abstract val allowedModules: ListProperty<String>

    @TaskAction
    fun check() {
        val denied = deniedGroups.get()
        val allowed = allowedModules.get().toSet()
        val violations = modules.get().filter { coordinate ->
            val group = coordinate.substringBefore(':')
            coordinate !in allowed &&
                denied.any { prefix -> group == prefix || group.startsWith("$prefix.") }
        }
        if (violations.isNotEmpty()) {
            throw GradleException(
                "checkNoTelemetrySdks failed: the client ships no analytics, crash-reporting " +
                    "or attribution SDK (pr.md §6.23, §6.31; AGENTS.md invariants). Remove it " +
                    "or replace it with a local implementation.\n" +
                    violations.joinToString("\n") { "  $it" },
            )
        }
    }
}
