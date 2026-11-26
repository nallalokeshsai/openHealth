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

// TECH-STACK.md §13, §16. Deferred modules (app-wear, server, tools) are added by their phase.
include(":core")
include(":data")
include(":sync")
include(":integrations")
include(":ui")
include(":app-android")
include(":tools:benchmark")
include(":tools:fixtures")
include(":tools:exerciseindex")
include(":tools:foodindex")
include(":server")
