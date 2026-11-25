plugins {
    id("openhealth.kotlin.library")
    // The device registry and the sync book are small JSON documents in the settings
    // layer; the compiler plugin generates their serializers at build time.
    alias(libs.plugins.kotlin.serialization)
}

// Domain types, UCUM units, formulas and validators. No android.* and no androidx.*
// (Tech-Stack.md §3). kotlinx-datetime and kotlinx-serialization only.
dependencies {
    api(libs.kotlinx.datetime)
    api(libs.kotlinx.serialization.json)
    api(libs.kotlinx.coroutines.core)

    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.junit)
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotest.property)
    testRuntimeOnly(libs.junit.vintage.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}
