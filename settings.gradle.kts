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
// KMP migration — Phase 2 / Slice 1: shared domain contracts (repository interfaces; later pure
// use cases). commonMain only — pure Kotlin + core-model.
include(":shared:core-domain")
// KMP migration — Phase 2: shared data layer (repo impls in commonMain, Room stays androidMain,
// rate-limit queues, later Ktor networking).
include(":shared:core-data")
// KMP migration — Phase 3 / Slice 1: shared design system (MagicTheme tokens, MagicColors,
// Spacing, Shapes, Material 3 bridge). Uses CMP compose plugin. Typography stays in :app
// until font loading is abstracted via the CMP resource system.
include(":shared:core-ui")
