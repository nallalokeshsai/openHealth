// The architecture and appearance guards: checkPortability (Tech-Stack.md §16), checkLicences
// (in :app-android, where the shipped classpath can be resolved) and checkDesignTokens
// (DESIGN.md §0.2, the closed token set).
import app.openhealth.buildlogic.CheckDesignTokensTask
import app.openhealth.buildlogic.CheckPortabilityTask

apply(plugin = "base")

tasks.register<CheckPortabilityTask>("checkPortability") {
    group = "verification"
    description = "Fails if core, data, sync or the pure half of integrations imports android.*/androidx.*"
    sources.from(
        fileTree("core") { exclude("**/build/**") },
        fileTree("data") { exclude("**/build/**") },
        fileTree("sync") { exclude("**/build/**") },
        fileTree("integrations") {
            exclude("**/build/**")
            exclude("**/android/**", "**/androidMain/**", "**/androidTest/**")
        },
    )
}

tasks.register<CheckDesignTokensTask>("checkDesignTokens") {
    group = "verification"
    description = "Fails if a Color( literal or a raw .dp literal appears outside ui/theme"
    sources.from(
        fileTree("ui/src") {
            include("**/*.kt")
            exclude("**/build/**")
            exclude("**/theme/**")
            exclude("**/test/**", "**/androidTest/**")
        },
        fileTree("app-android/src") {
            include("**/main/**/*.kt")
            exclude("**/build/**")
        },
    )
}

tasks.named("check") {
    dependsOn("checkPortability", "checkDesignTokens")
}
