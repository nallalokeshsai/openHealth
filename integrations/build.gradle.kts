plugins {
    id("openhealth.kotlin.library")
}

dependencies {
    testImplementation(libs.kotlin.test.junit5)
    testRuntimeOnly(libs.junit.jupiter.engine)
}
