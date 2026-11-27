plugins {
    id("openhealth.android.app")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.openhealth.wear"

    defaultConfig {
        // Wear OS 3 (API 30) is the floor for Compose for Wear and Health Services.
        // The application id matches the phone so the Data Layer namespaces agree
        // (docs/adr/0019); the APKs install on different devices.
        applicationId = "app.openhealth"
        minSdk = 30
    }

    // R8 and resource shrinking for release: the watch app pulls in `:ui` for the theme
    // and R8 removes the phone screens the four watch screens never reference.
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(project(":core"))
    // The one theme object (DESIGN.md Appendix B; Tech-Stack.md §2). The watch draws with
    // the same tokens as the phone rather than inventing a watch palette.
    implementation(project(":ui"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.androidx.wear.compose.material3)
    implementation(libs.androidx.wear.compose.foundation)
    implementation(libs.androidx.activity.compose)
    // ActivityResultContracts lives on ComponentActivity, but the lint contract checks the
    // Fragment version even when no Fragment is used; this keeps that check honest.
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    // The paired link and the optical heart-rate stream. Both are Play-only platform
    // services on the watch; the phone's F-Droid build never resolves them.
    implementation(libs.play.services.wearable)
    implementation(libs.androidx.health.services.client)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.compose.ui.test.manifest)
    testRuntimeOnly(libs.junit.vintage.engine)
    testRuntimeOnly(libs.junit.platform.launcher)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.kotlin.test.junit)
    debugImplementation(libs.compose.ui.test.manifest)
}
