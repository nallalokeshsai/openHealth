pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "openhealth"

// The module layout is fixed by Tech-Stack.md §13. Add a module only when the
// roadmap phase that owns it starts (B13 for :server, B14 for :app-wear).
include(":core")
include(":data")
include(":sync")
include(":integrations")
include(":ui")
include(":app-android")
