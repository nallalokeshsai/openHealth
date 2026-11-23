plugins {
    id("openhealth.android.library")
}

android {
    namespace = "app.openhealth.ui"
}

dependencies {
    // The design system (OhTheme and the 19 components) arrives in B1.
    api(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
}
