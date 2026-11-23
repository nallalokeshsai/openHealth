package openhealth.buildlogic

import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.Project
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

/** Shared wiring for every openHealth module (Tech-Stack.md §16). */
internal fun Project.applyCodeQuality() {
    pluginManager.apply("io.gitlab.arturbosch.detekt")
    pluginManager.apply("org.jlleitschuh.gradle.ktlint")
    extensions.configure<DetektExtension> {
        // The defaults stay on; gradle/detekt.yml scopes four rules for @Composable.
        buildUponDefaultConfig = true
        config.setFrom(rootProject.files("gradle/detekt.yml"))
        parallel = true
    }
}

/** Adds `checkPortability` to `check`: no `android.*` or `androidx.*` in a pure module. */
internal fun Project.registerPortabilityCheck() {
    val checkPortability = tasks.register<CheckPortabilityTask>("checkPortability") {
        group = "verification"
        description = "Fails on android.* or androidx.* imports in a pure Kotlin module."
        sources.from(fileTree("src") { include("**/*.kt", "**/*.kts") })
        forbiddenPrefixes.set(listOf("android.", "androidx."))
    }
    tasks.matching { it.name == "check" }.configureEach { dependsOn(checkPortability) }
}

/**
 * Adds `checkLicences` to `check`: every resolved runtime dependency of a client module
 * must appear in gradle/licences/allowed.tsv with its licence recorded. A GPL, LGPL or
 * AGPL dependency therefore fails the build because it cannot be on the list
 * (Tech-Stack.md §11, §16; pr.md §12.4).
 */
internal fun Project.registerLicenceCheck() {
    val allowlist = rootProject.file("gradle/licences/allowed.tsv").readLines()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .map { it.substringBefore('\t').substringBefore(' ') }

    // `matching` is a live view: Android modules create their runtime and compile
    // configurations after this convention plugin is applied, so an eager filter would
    // see none. Compile classpaths are included so a compileOnly GPL-family artifact
    // cannot hide from the gate (Tech-Stack.md §11, §16).
    val licenceConfigurations = configurations.matching {
        it.name.endsWith("RuntimeClasspath") || it.name.endsWith("CompileClasspath")
    }

    val coordinates: Provider<List<String>> = provider {
        val matched = licenceConfigurations.toList()
        check(matched.isNotEmpty()) {
            "checkLicences found no runtime classpath in ${path}: the licence gate would " +
                "pass without inspecting anything. Add the module's runtime configuration."
        }
        matched
            .flatMap { configuration ->
                configuration.incoming.resolutionResult.allComponents.mapNotNull { component ->
                    (component.id as? ModuleComponentIdentifier)?.let { "${it.group}:${it.module}" }
                }
            }
            .distinct()
            .sorted()
    }

    val checkLicences = tasks.register<CheckLicencesTask>("checkLicences") {
        group = "verification"
        description = "Fails when a resolved dependency is missing from the licence allowlist."
        modules.set(coordinates)
        allowed.set(allowlist)
    }
    tasks.matching { it.name == "check" }.configureEach { dependsOn(checkLicences) }
}

/**
 * Adds `checkDesignTokens` to `check`: no colour literal, raw `.dp`/`.sp` or
 * `MaterialTheme` reference outside the theme package (`DESIGN.md` §12.6, Appendix B;
 * `BUILD-ROADMAP.md` §B1 exit).
 */
internal fun Project.registerDesignTokenCheck() {
    val checkDesignTokens = tasks.register<CheckDesignTokensTask>("checkDesignTokens") {
        group = "verification"
        description = "Fails on a colour, .dp or .sp literal outside the theme package."
        sources.from(fileTree("src") { include("**/*.kt") })
        allowedPrefixes.set(listOf("/ui/theme/"))
    }
    tasks.matching { it.name == "check" }.configureEach { dependsOn(checkDesignTokens) }
}

/** JVM toolchain and Kotlin target for the pure Kotlin modules. */
internal fun Project.configureJvmToolchain() {
    extensions.configure<KotlinJvmProjectExtension> {
        jvmToolchain(21)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }
}

internal fun Project.configureTestTask() {
    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
}
