plugins {
    id("openhealth.kotlin.library")
    application
}

application {
    mainClass.set("app.openhealth.tools.fixtures.FixtureCli")
}

// The five-year seeded corpus and the query benchmark (BUILD-ROADMAP.md §B2,
// Tech-Stack.md §10, §12). A developer tool, never shipped in the APK.
dependencies {
    implementation(project(":core"))
    implementation(project(":data"))
    implementation(libs.sqldelight.runtime)
    implementation(libs.sqldelight.sqlite.driver)

    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.junit)
    testImplementation(libs.sqldelight.sqlite.driver)
    testRuntimeOnly(libs.junit.vintage.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// The corpus benchmark prints every query's best time. Showing that output in CI is what
// makes a budget failure diagnosable (which query, how far over) instead of a bare line.
tasks.withType<Test>().configureEach {
    testLogging {
        showStandardStreams = true
    }
}
