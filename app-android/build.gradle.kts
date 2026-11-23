plugins {
    id("openhealth.android.app")
}

android {
    namespace = "app.openhealth"
}

dependencies {
    implementation(project(":core"))
    implementation(project(":data"))
    implementation(project(":sync"))
    implementation(project(":integrations"))
    implementation(project(":ui"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.androidx.activity.compose)
}
