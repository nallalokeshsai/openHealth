// Convention plugin for Android library modules (:ui today; feature modules later).
// Kotlin comes from AGP's built-in Kotlin support (AGP 9+), so there is no kotlin-android plugin.
import com.android.build.api.dsl.LibraryExtension

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
}

apply(plugin = "openhealth.quality")

extensions.configure<LibraryExtension> {
    compileSdk = 37
    compileSdkMinor = 2

    defaultConfig {
        minSdk = 28
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("failed", "skipped")
    }
}
