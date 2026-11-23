package openhealth.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** Android application module (Tech-Stack.md §4, §16). */
class AndroidAppConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
        extensions.configure<ApplicationExtension> {
            compileSdk = 37
            compileSdkMinor = 2
            defaultConfig {
                applicationId = "app.openhealth"
                minSdk = 28
                targetSdk = 37
                versionCode = 1
                versionName = "0.1.0"
            }
            buildFeatures {
                compose = true
            }
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_21
                targetCompatibility = JavaVersion.VERSION_21
            }
        }
        applyCodeQuality()
        registerLicenceCheck()
        registerDesignTokenCheck()
        configureTestTask()
    }
}
