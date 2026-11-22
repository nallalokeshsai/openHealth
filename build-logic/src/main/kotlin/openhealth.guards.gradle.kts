// The two architecture guards of Tech-Stack.md §16. checkPortability scans the pure modules;
// checkLicences lives in :app-android (see openhealth.android.app) because the guard has to
// resolve the classpath the phone actually ships, and Gradle forbids resolving another
// project's configurations during task execution.
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

tasks.named("check") {
    dependsOn("checkPortability")
}
