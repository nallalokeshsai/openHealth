plugins {
    id("openhealth.kotlin.library")
}

// Importers and exporters land in B8/B11; their Android implementations arrive with them.
// The API half stays free of android.* and androidx.* (Tech-Stack.md §3).

dependencies {
    api(project(":core"))
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testRuntimeOnly(libs.junit.vintage.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}
