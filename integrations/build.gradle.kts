plugins {
    id("openhealth.kotlin.library")
    alias(libs.plugins.kotlin.serialization)
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

// The Connect IQ table lives with the Monkey C app, so the test reads the real file and
// the manifest it must match (`docs/adr/0019`). Declared as inputs so a table edit always
// re-runs the check.
val connectIqDir = rootProject.layout.projectDirectory.dir("watch/connectiq")
tasks.withType<Test>().configureEach {
    systemProperty(
        "openhealth.connectiq.devices",
        connectIqDir.file("devices.json").asFile.absolutePath,
    )
    systemProperty(
        "openhealth.connectiq.manifest",
        connectIqDir.file("manifest.xml").asFile.absolutePath,
    )
    // The Monkey C sources, the jungle, the resources and build.sh are read by
    // `ConnectIqSourceConsistencyTest`, which is the only gate the Garmin half has while
    // the SDK is not installed (`docs/adr/0019`).
    systemProperty("openhealth.connectiq.dir", connectIqDir.asFile.absolutePath)
    inputs.file(connectIqDir.file("devices.json")).withPropertyName("connectIqDevices")
    inputs.file(connectIqDir.file("manifest.xml")).withPropertyName("connectIqManifest")
    inputs.file(connectIqDir.file("build.sh")).withPropertyName("connectIqBuildScript")
    inputs.dir(connectIqDir.dir("source")).withPropertyName("connectIqSource")
    inputs.dir(connectIqDir.dir("resources")).withPropertyName("connectIqResources")
}
