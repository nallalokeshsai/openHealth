plugins {
    id("openhealth.android.app")
}

android {
    namespace = "app.openhealth.android"

    defaultConfig {
        applicationId = "app.openhealth"
    }
}

dependencies {
    implementation(project(":ui"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    testImplementation(libs.kotlin.test.junit5)
    testRuntimeOnly(libs.junit.jupiter.engine)
}
