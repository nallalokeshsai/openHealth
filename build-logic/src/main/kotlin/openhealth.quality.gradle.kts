// Shared quality gates: ktlint and Detekt, both wired into `check`.
// Values live in .editorconfig and config/detekt/detekt.yml so the modules stay identical.
apply(plugin = "org.jlleitschuh.gradle.ktlint")
apply(plugin = "io.gitlab.arturbosch.detekt")

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    buildUponDefaultConfig = true
    parallel = true
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
}

tasks.named("check") {
    dependsOn(tasks.withType<io.gitlab.arturbosch.detekt.Detekt>())
}
