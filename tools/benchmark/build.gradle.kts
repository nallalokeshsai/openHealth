plugins {
    id("com.android.test")
}

/**
 * B9's macrobenchmark (BUILD-ROADMAP.md §B9, Tech-Stack.md §12). It targets the
 * `benchmark` variant of `:app-android`; the module is not part of the app and adds
 * nothing to a release APK.
 */
android {
    namespace = "app.openhealth.benchmark"
    compileSdk = 37
    compileSdkMinor = 2

    defaultConfig {
        minSdk = 28
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":app-android"
    experimentalProperties["android.experimental.self-instrumenting"] = true

    // The app ships two store flavours; the benchmark targets the Play one, whose
    // dependencies are the same as F-Droid's apart from the absent store service.
    flavorDimensions += "store"
    productFlavors {
        create("play") { dimension = "store" }
        create("fdroid") { dimension = "store" }
    }

    buildTypes {
        create("benchmark") {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.kotlin.test.junit)
}
