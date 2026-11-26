plugins {
    id("openhealth.android.app")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.openhealth"

    // The store flavours remain (Play and the Play-Services-free F-Droid build), but the
    // barcode decoder is ZXing in both: ML Kit's transitive Google Data Transport is a
    // telemetry path that the no-telemetry invariant forbids (ADR 0011, B6).
    flavorDimensions += "store"
    productFlavors {
        create("play") { dimension = "store" }
        create("fdroid") { dimension = "store" }
    }

    // Release signing reads the owner's keystore from the environment, never from the
    // repository (B10). `.env.local` is gitignored and holds the four values; a machine
    // without them still builds an unsigned release for inspection, and the release
    // pipeline fails loudly rather than silently shipping a debug key.
    val releaseKeystore = providers.environmentVariable("OH_KEYSTORE").orNull
    val releaseStorePassword = providers.environmentVariable("OH_KEYSTORE_PASSWORD").orNull
    val releaseKeyAlias = providers.environmentVariable("OH_KEY_ALIAS").orNull
    val releaseKeyPassword = providers.environmentVariable("OH_KEY_PASSWORD").orNull
    if (releaseKeystore != null &&
        releaseStorePassword != null &&
        releaseKeyAlias != null &&
        releaseKeyPassword != null
    ) {
        signingConfigs {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    // R8 and resource shrinking are on for release (Tech-Stack.md §11, §12). The
    // benchmark variant inherits them because it must measure the shape that ships.
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.findByName("release")
        }
        // Macrobenchmark (B9) installs this variant beside the benchmark test APK. It is a
        // release-shaped build signed with the debug key so `connectedBenchmarkAndroidTest`
        // can launch it without the owner's release keystore.
        create("benchmark") {
            initWith(getByName("release"))
            matchingFallbacks += listOf("release")
            signingConfig = signingConfigs.getByName("debug")
            isDebuggable = false
            // The profile generator reads the running app's class names; R8 renames them
            // into a mapping that only this build shares, so the benchmark variant stays
            // unmangled. It still shrinks and runs the release dependency graph.
            isMinifyEnabled = false
            isShrinkResources = false
            // A separate package and data directory: a benchmark run must never merge
            // its synthetic corpus into the record on the owner's phone (ADR 0014).
            applicationIdSuffix = ".bench"
        }
    }

    // The FIT golden fixtures are the same bytes the :integrations tests parse, so a
    // device test cannot drift from the JVM one. They are read-only test assets, never
    // shipped in an APK: only the androidTest source set sees them.
    sourceSets.getByName("androidTest").assets.srcDir(
        rootProject.file("integrations/src/test/resources"),
    )
}

dependencies {
    implementation(project(":core"))
    implementation(project(":data"))
    implementation(project(":sync"))
    implementation(project(":integrations"))
    implementation(project(":ui"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.koin.core)
    implementation(libs.koin.android)

    // CameraX and ZXing are Apache-2.0 and shared by both store flavours. ZXing keeps the
    // decoder offline, free and telemetry-free; ML Kit was removed in B6 for the latter.
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.zxing.core)

    // MapLibre Native (BSD-2-Clause) draws the activity route over a PMTiles archive.
    // No Google Maps and no telemetry: the tiles are a local file the person downloaded.
    implementation(libs.maplibre.android)

    // Health Connect: the platform bus (Tech-Stack.md §8). The client library has no
    // Play Services dependency; the Health Connect app itself is a system module on
    // Android 14+ and a Play app on 13 and lower.
    implementation(libs.androidx.health.connect)

    // Encrypted storage behind the :core seam (Tech-Stack.md §5). SQLCipher is the
    // community edition; its attribution requirement is recorded in NOTICE.md.
    implementation(libs.sqldelight.android.driver)
    implementation(libs.sqlcipher.android)

    debugImplementation(libs.compose.ui.tooling)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.kotlin.test.junit)
    debugImplementation(libs.compose.ui.test.manifest)
}
