plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    // AGP 9 compiles Kotlin itself (built-in Kotlin) and ships KGP 2.2.10.
    // Declaring the pinned KGP here raises it for every module; keep in step with
    // gradle/libs.versions.toml and docs/adr/0003-pinned-versions.md.
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.sqldelight) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

// The convention plugins in build-logic/ add ktlint, Detekt, checkPortability and
// checkLicences to each module's `check` task (Tech-Stack.md §16).
