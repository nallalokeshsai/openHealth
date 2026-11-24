plugins {
    id("openhealth.kotlin.library")
    application
}

application {
    mainClass.set("app.openhealth.tools.foodindex.FoodIndexCli")
}

// Paths in the CLI are repository-relative; run it from the root, not the module folder.
tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
}

// Converts USDA FoodData Central (CC0) into the compact read-only index the app ships
// (BUILD-ROADMAP.md §B6, pr.md §6.3, §12.2). A developer tool, never in the APK.
dependencies {
    implementation(project(":core"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.sqldelight.sqlite.driver)

    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.junit)
    testRuntimeOnly(libs.junit.vintage.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}
