plugins {
    id("openhealth.android.app")
    alias(libs.plugins.kotlin.serialization)
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
    implementation(libs.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.koin.core)
    implementation(libs.koin.android)

    // Encrypted storage behind the :core seam (Tech-Stack.md §5). SQLCipher is the
    // community edition; its attribution requirement is recorded in NOTICE.md.
    implementation(libs.sqldelight.android.driver)
    implementation(libs.sqlcipher.android)

    debugImplementation(libs.compose.ui.tooling)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.kotlin.test.junit)
    debugImplementation(libs.compose.ui.test.manifest)
}
