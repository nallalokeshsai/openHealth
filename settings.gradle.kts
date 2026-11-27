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

// TECH-STACK.md §13, §16. Modules are added by their phase; app-wear landed in B14.
include(":core")
include(":data")
include(":sync")
include(":integrations")
include(":ui")
include(":app-android")
include(":app-wear")
include(":tools:benchmark")
include(":tools:fixtures")
include(":tools:exerciseindex")
include(":tools:foodindex")
include(":server")
