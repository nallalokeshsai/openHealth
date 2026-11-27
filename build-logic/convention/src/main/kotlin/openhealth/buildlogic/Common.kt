package openhealth.buildlogic

import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.jlleitschuh.gradle.ktlint.KtlintExtension
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
    extensions.configure<KtlintExtension> {
        // Generated sources (SQLDelight) are not hand-written and are excluded.
        filter {
            exclude { element -> element.file.path.contains("/build/generated/") }
        }
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
 * Adds `checkNoTelemetrySdks` to `check`: no analytics, crash-reporting or attribution
 * SDK on any runtime classpath (`pr.md` §6.23, §6.31; `BUILD-ROADMAP.md` §B10). It is a
 * deny list, separate from the licence allowlist, so a permissively licensed telemetry
 * SDK cannot enter by adding an allowlist row.
 */
internal fun Project.registerTelemetryCheck() {
    val modulePath = path
    val telemetryConfigurations = configurations.matching {
        it.name.endsWith("RuntimeClasspath")
    }
    val coordinates: Provider<List<String>> = provider {
        val matched = telemetryConfigurations.toList()
        check(matched.isNotEmpty()) {
            "checkNoTelemetrySdks found no runtime classpath in ${path}: the gate would " +
                "pass without inspecting anything."
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
    val checkNoTelemetrySdks = tasks.register<CheckTelemetryTask>("checkNoTelemetrySdks") {
        group = "verification"
        description = "Fails when an analytics, crash-reporting or attribution SDK resolves."
        modules.set(coordinates)
        allowedModules.set(
            // The Wear OS Data Layer is the one platform feature that needs Play Services
            // (Tech-Stack.md §4; ADR 0019). Only these four exact coordinates, and only on
            // the phone and Wear modules, may pass; every other Google artifact still
            // fails, and the F-Droid build is held to zero by its own check.
            if (modulePath == ":app-android" || modulePath == ":app-wear") WEARABLE_STACK else emptyList(),
        )
        deniedGroups.set(
            listOf(
                "com.google.firebase",
                "com.google.android.gms",
                "com.google.android.datatransport",
                "com.google.analytics",
                "com.crashlytics",
                "io.sentry",
                "com.datadog",
                "com.amplitude",
                "com.mixpanel",
                "com.bugsnag",
                "com.newrelic",
                "com.instabug",
                "com.microsoft.appcenter",
                "com.flurry",
                "com.adjust.sdk",
                "com.appsflyer",
                "com.facebook.appevents",
            ),
        )
    }
    tasks.matching { it.name == "check" }.configureEach { dependsOn(checkNoTelemetrySdks) }
}

/**
 * The exact Google coordinates the Wear Data Layer needs, and nothing else. It is a set
 * of whole coordinates so a group prefix can never be widened into analytics (`ADR 0019`).
 */
private val WEARABLE_STACK: List<String> = listOf(
    "com.google.android.gms:play-services-wearable",
    "com.google.android.gms:play-services-base",
    "com.google.android.gms:play-services-basement",
    "com.google.android.gms:play-services-tasks",
)

/**
 * Adds `checkDesignTokens` to `check`: no colour literal, raw `.dp`/`.sp` or
 * `MaterialTheme` reference outside the theme package (`DESIGN.md` §12.6, Appendix B;
 * `BUILD-ROADMAP.md` §B1 exit).
 */
internal fun Project.registerDesignTokenCheck() {
    val checkDesignTokens = tasks.register<CheckDesignTokensTask>("checkDesignTokens") {
        group = "verification"
        description = "Fails on a colour, .dp or .sp literal outside the theme package."
        sources.from(
            fileTree("src") {
                include("**/*.kt")
                // Tests compose components and, where a test is about colour itself,
                // need a literal to reason with. They are never shipped, so the rule
                // applies to production source only.
                // The tree root is `src`, so the patterns are relative to it.
                exclude("test/**", "androidTest/**")
            },
        )
        // The allowlist is module-relative, so a checkout path can never widen it.
        modulePath.set(projectDir.invariantSeparatorsPath)
        allowedPrefixes.set(
            if (project.path == ":ui") {
                listOf("src/main/kotlin/app/openhealth/ui/theme")
            } else {
                emptyList()
            },
        )
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
