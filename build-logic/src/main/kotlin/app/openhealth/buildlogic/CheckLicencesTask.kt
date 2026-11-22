package app.openhealth.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction

/**
 * Guards `Tech-Stack.md` §11: a GPL/AGPL/LGPL-family dependency may not reach a client module.
 *
 * The registry at `gradle/client-licences.txt` is the reviewed list: every dependency that ships
 * must be named there with the licence we rely on. An unknown dependency fails the check as
 * unreviewed, so a new dependency cannot appear on the shipped classpath without a deliberate
 * line in that file.
 */
abstract class CheckLicencesTask : DefaultTask() {

    /** `group:module` of every module on the resolved client classpath. */
    @get:Input
    abstract val coordinates: SetProperty<String>

    @get:InputFile
    abstract val registry: RegularFileProperty

    @TaskAction
    fun check() {
        val reviewed = mutableMapOf<String, String>()
        // Strip a UTF-8 BOM: Windows editors add one and it must not break the first line.
        val lines = registry.get().asFile.readText().removePrefix("\uFEFF").lines()
        lines.forEachIndexed { index, raw ->
            val line = raw.substringBefore('#').trim()
            if (line.isEmpty()) return@forEachIndexed
            val separator = line.indexOf('=')
            if (separator <= 0 || separator == line.lastIndex) {
                throw GradleException(
                    "client-licences.txt line ${index + 1} is not 'group:module = SPDX': $raw",
                )
            }
            val coordinate = line.substring(0, separator).trim()
            val licence = line.substring(separator + 1).trim()
            reviewed[coordinate] = licence
        }

        val resolved = coordinates.get()
        val unreviewed = resolved.filterNot { reviewed.containsKey(it) }.sorted()
        val nonFree = resolved
            .mapNotNull { coordinate -> reviewed[coordinate]?.let { coordinate to it } }
            .filter { (_, licence) -> GPL_FAMILY.containsMatchIn(licence) }
            .sortedBy { it.first }

        val problems = buildList {
            if (nonFree.isNotEmpty()) {
                add("GPL-family licence in a client dependency:")
                nonFree.forEach { (coordinate, licence) -> add("  $coordinate -> $licence") }
            }
            if (unreviewed.isNotEmpty()) {
                add("unreviewed client dependency (add it to gradle/client-licences.txt):")
                unreviewed.forEach { add("  $it") }
            }
        }

        if (problems.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("checkLicences failed (Tech-Stack.md §11, pr.md §12.4):")
                    problems.forEach { appendLine(it) }
                },
            )
        }
        logger.lifecycle("checkLicences: ${resolved.size} client dependency module(s), all reviewed and non-GPL.")
    }

    private companion object {
        // Matches GPL, GPLv2, GPL-2.0-only, AGPLv3, LGPL-2.1 and the spelled-out GNU names. The
        // trailing boundary is deliberately loose: a POM that writes "GPLv2" instead of an SPDX id
        // must still fail, and the registry's SPDX convention is a review rule, not the guard.
        val GPL_FAMILY = Regex(
            "(?i)(\\b(?:A|L)?GPL)|(GNU (?:Affero |Lesser |Library )?General Public License)",
        )
    }
}
