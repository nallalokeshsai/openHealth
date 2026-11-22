// Convention plugin for the phone app (:app-android). Kotlin comes from AGP's built-in Kotlin
// support (AGP 9+); Compose is on, and the design system arrives from :ui in B1.
import app.openhealth.buildlogic.CheckLicencesTask
import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.artifacts.component.ModuleComponentIdentifier

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

apply(plugin = "openhealth.quality")

extensions.configure<ApplicationExtension> {
    compileSdk = 37
    compileSdkMinor = 2

    defaultConfig {
        minSdk = 28
        targetSdk = 37
        versionCode = 1
        versionName = "0.0.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("failed", "skipped")
    }
}

// checkLicences: the shipped classpath must be reviewed and GPL-free (Tech-Stack.md §11, §16).
// It runs here because only this project may resolve its own configurations at execution time.
val shippedCoordinates = provider {
    listOf("debugRuntimeClasspath", "releaseRuntimeClasspath")
        .flatMap { name -> configurations.getByName(name).incoming.resolutionResult.allComponents }
        .mapNotNull { it.id as? ModuleComponentIdentifier }
        .map { "${it.group}:${it.module}" }
        .toSortedSet()
}

tasks.register<CheckLicencesTask>("checkLicences") {
    group = "verification"
    description = "Fails on a GPL-family or unreviewed dependency on the shipped client classpath"
    registry.set(rootProject.layout.projectDirectory.file("gradle/client-licences.txt"))
    coordinates.set(shippedCoordinates)
}

tasks.named("check") {
    dependsOn("checkLicences")
}
