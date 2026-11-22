plugins {
    `kotlin-dsl`
}

group = "app.openhealth.buildlogic"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
    implementation(libs.findLibrary("android-gradle-plugin").get())
    implementation(libs.findLibrary("kotlin-gradle-plugin").get())
    implementation(libs.findLibrary("compose-compiler-gradle-plugin").get())
    implementation(libs.findLibrary("detekt-gradle-plugin").get())
    implementation(libs.findLibrary("ktlint-gradle-plugin").get())
}
