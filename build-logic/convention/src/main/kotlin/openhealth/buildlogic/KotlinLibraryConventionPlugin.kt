package openhealth.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply

/** Pure Kotlin module: no android.*, no androidx.* (Tech-Stack.md §3, §16). */
class KotlinLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.jvm")
        configureJvmToolchain()
        applyCodeQuality()
        registerPortabilityCheck()
        registerLicenceCheck()
        registerTelemetryCheck()
        configureTestTask()
    }
}
