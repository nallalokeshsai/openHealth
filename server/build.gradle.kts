plugins {
    id("openhealth.kotlin.library")
    alias(libs.plugins.kotlin.serialization)
    application
}

// The optional, self-hostable sync server (Tech-Stack.md §7). AGPL-3.0: see
// server/LICENSE. It compiles :core, :sync and :data for the JVM and never requires the
// app; `docker compose up` in this directory starts it with PostgreSQL, MinIO and Caddy.
application {
    mainClass.set("app.openhealth.server.MainKt")
}

dependencies {
    implementation(project(":core"))
    implementation(project(":sync"))

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.coroutines.core)

    implementation(libs.postgresql)
    implementation(libs.hikari)
    implementation(libs.flyway.core)
    implementation(libs.flyway.database.postgresql)
    implementation(libs.webauthn4j.core)
    implementation(libs.bcrypt)
    implementation(libs.slf4j.simple)

    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.junit)
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotest.property)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.client.content.negotiation)
    testImplementation(libs.ktor.serialization.kotlinx.json)
    testImplementation(project(":data"))
    testImplementation(libs.sqldelight.sqlite.driver)
    testRuntimeOnly(libs.junit.vintage.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}
