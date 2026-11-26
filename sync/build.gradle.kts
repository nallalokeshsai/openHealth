plugins {
    id("openhealth.kotlin.library")
    alias(libs.plugins.kotlin.serialization)
}

// The change log, hybrid logical clock, merge engine and Ktor client (B13). Pure Kotlin:
// the same code is compiled into the Android app and the Ktor server (Tech-Stack.md §6).
dependencies {
    api(project(":core"))
    api(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.client.encoding)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)

    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.junit)
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotest.property)
    testImplementation(libs.kotlinx.coroutines.test)
    testRuntimeOnly(libs.junit.vintage.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}
