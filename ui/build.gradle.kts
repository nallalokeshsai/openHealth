plugins {
    id("openhealth.android.library")
    // Screenshot testing: Roborazzi creates record/verify/compare tasks for this module's
    // unit tests (Tech-Stack.md §10). Test-only; nothing from it ships.
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "app.openhealth.ui"

    testOptions {
        unitTests {
            // Robolectric needs the merged resources and manifest of the debug variant.
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.foundation)
    // Material 3 is used for behaviour only (sheet, text style default), never for its palette or
    // shapes (DESIGN.md Appendix B).
    implementation(libs.androidx.compose.material3)

    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(composeBom)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.kotlin.test.junit5)
    testRuntimeOnly(libs.junit.jupiter.engine)
    // Robolectric and Compose UI rules are JUnit 4; the vintage engine lets JUnit Platform run
    // them next to the JUnit 5 unit tests.
    testRuntimeOnly(libs.junit.vintage.engine)
}
