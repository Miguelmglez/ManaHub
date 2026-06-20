pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://alphacephei.com/maven/") }
    }
}

rootProject.name = "ManaHub"
include(":app")
include(":baseline-profile")
// KMP migration — Phase 0, Spike A: first shared Kotlin Multiplatform module (android + wasmJs).
include(":shared:core-model")
// KMP migration — Phase 1: shared cross-cutting contracts (DispatcherProvider, KeyValueStore,
// CrashReporter, pagination model) with android + wasmJs actuals.
include(":shared:core-common")
