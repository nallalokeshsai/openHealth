import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.testing.Test

plugins {
    id("openhealth.android.library")
}

android {
    namespace = "app.openhealth.ui"

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    // Screens ask the pure policies in :core (modules, weight-neutral, age). Storage and
    // Android stay out of its API surface.
    api(project(":core"))
    api(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.animation)
    implementation(libs.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.datetime)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.compose.ui.test.manifest)
    testRuntimeOnly(libs.junit.vintage.engine)
    testRuntimeOnly(libs.junit.platform.launcher)

    debugImplementation(libs.compose.ui.tooling)
}

// Roborazzi without its Gradle plugin: `check` verifies the committed goldens and
// `-Proborazzi.record` rewrites them. Screenshot tests never record by accident.
val recordScreenshots = providers.gradleProperty("roborazzi.record").isPresent
tasks.withType<Test>().configureEach {
    systemProperty("roborazzi.test.record", recordScreenshots.toString())
    systemProperty("roborazzi.test.verify", (!recordScreenshots).toString())
    systemProperty("robolectric.graphicsMode", "NATIVE")
    // A golden that changes must re-run the verification, not leave the task up to date.
    inputs.dir(layout.projectDirectory.dir("src/test/screenshots"))
        .withPropertyName("screenshotGoldens")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    maxHeapSize = "2g"
}
