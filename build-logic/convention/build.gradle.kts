import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-dsl`
}

group = "app.openhealth.buildlogic"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    implementation(libs.detekt.gradlePlugin)
    implementation(libs.ktlint.gradlePlugin)
    implementation(libs.kotlin.compose.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("kotlinLibrary") {
            id = "openhealth.kotlin.library"
            implementationClass = "openhealth.buildlogic.KotlinLibraryConventionPlugin"
        }
        register("androidLibrary") {
            id = "openhealth.android.library"
            implementationClass = "openhealth.buildlogic.AndroidLibraryConventionPlugin"
        }
        register("androidApp") {
            id = "openhealth.android.app"
            implementationClass = "openhealth.buildlogic.AndroidAppConventionPlugin"
        }
    }
}
