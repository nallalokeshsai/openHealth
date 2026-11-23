import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("openhealth.kotlin.library")
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.kotlin.serialization)
}

// SQLDelight 2.x schema and migrations (Tech-Stack.md §5). The database is created by
// this module from a SqlDriver handed in by the platform; no android.* reaches it.
sqldelight {
    databases {
        create("OpenHealthDb") {
            packageName.set("app.openhealth.data.db")
            schemaOutputDirectory.set(file("src/main/sqldelight/databases"))
            deriveSchemaFromMigrations.set(false)
            verifyMigrations.set(false)
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

dependencies {
    api(project(":core"))

    implementation(libs.sqldelight.runtime)
    implementation(libs.sqldelight.coroutines)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.serialization.json)
    // Argon2id for the export passphrase (ADR 0005): pure Java, Bouncy Castle licence.
    implementation(libs.bouncycastle.bcprov)

    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.junit)
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotest.property)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.sqldelight.sqlite.driver)
    testRuntimeOnly(libs.junit.vintage.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}
