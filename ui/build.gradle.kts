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
    api(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)

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
    debugImplementation(libs.compose.ui.test.manifest)
}

// Roborazzi without its Gradle plugin: `check` verifies the committed goldens and
// `-Proborazzi.record` rewrites them. Screenshot tests never record by accident.
val recordScreenshots = providers.gradleProperty("roborazzi.record").isPresent
tasks.withType<Test>().configureEach {
    systemProperty("roborazzi.test.record", recordScreenshots.toString())
    systemProperty("roborazzi.test.verify", (!recordScreenshots).toString())
    systemProperty("robolectric.graphicsMode", "NATIVE")
    maxHeapSize = "2g"
}
