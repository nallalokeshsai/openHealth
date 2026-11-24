plugins {
    id("openhealth.kotlin.library")
    application
}

application {
    mainClass.set("app.openhealth.tools.exerciseindex.ExerciseIndexCli")
}

// Paths in the CLI are repository-relative; run it from the root, not the module folder.
tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
}

// Converts the public-domain Free Exercise DB into the plain-text library `:core` ships
// (BUILD-ROADMAP.md §B4, pr.md §6.5, §12.2). A developer tool, never in the APK.
dependencies {
    implementation(project(":core"))
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.junit)
    testRuntimeOnly(libs.junit.vintage.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}
