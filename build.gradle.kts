plugins {
    id("openhealth.guards")
    alias(libs.plugins.roborazzi) apply false
}

allprojects {
    group = "app.openhealth"
    version = "0.0.0"
}
