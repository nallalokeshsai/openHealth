plugins {
    id("openhealth.android.library")
}

android {
    namespace = "app.openhealth.ui"
}

dependencies {
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.foundation)

    testImplementation(libs.kotlin.test.junit5)
    testRuntimeOnly(libs.junit.jupiter.engine)
}
