/*
 * :shared:core-domain — third Kotlin Multiplatform module (KMP migration, Phase 2 / Slice 1).
 *
 * Purpose: host the platform-agnostic DOMAIN contracts (repository interfaces, and later the pure
 * use cases) shared by Android + Web. Slice 1 moves a first batch of repository INTERFACES whose
 * signatures reference only :shared:core-model types, kotlinx (Flow, etc.) and primitives. The
 * concrete implementations stay in :app (Android side) and keep implementing these interfaces.
 *
 * Depends on :shared:core-model (the moved interfaces reference its model types). :shared:core-common
 * is intentionally NOT a dependency yet — the current batch needs none of its contracts; it is wired
 * in later when use cases that touch DispatcherProvider/KeyValueStore/CrashReporter move here.
 *
 * Targets/source-set/plugin setup mirror :shared:core-model and :shared:core-common (the AGP-9
 * KMP-library path).
 *
 * HARD RULE: commonMain must contain ZERO Android / AndroidX / browser / Room imports — pure Kotlin +
 * kotlinx-coroutines + :shared:core-model only.
 */
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
}

kotlin {
    // ── Android target ────────────────────────────────────────────────────────────────────────
    androidLibrary {
        namespace = "com.mmg.manahub.core.domain"
        compileSdk = 36
        minSdk = 29

        // Enable a JVM host unit-test component so commonTest runs as an Android host test
        // (task: :shared:core-domain:testAndroidHostTest).
        withHostTestBuilder {}
    }

    // ── Web target (Compose Multiplatform / wasmJs) ───────────────────────────────────────────
    @Suppress("OPT_IN_USAGE")
    wasmJs {
        browser()
    }

    // JVM toolchain — match :app (JVM 17).
    jvmToolchain(17)

    sourceSets {
        commonMain {
            dependencies {
                // Moved repository interfaces reference core-model types (UserPreferences, CollectionStats…).
                api(project(":shared:core-model"))
                implementation(libs.coroutines.core)
                // Use cases use Clock.System.now() for event timestamps.
                implementation(libs.kotlinx.datetime)
            }
        }
        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }
        // androidMain / wasmJsMain intentionally have no code yet (placeholders for future actuals).
        androidMain {
            dependencies {}
        }
        wasmJsMain {
            dependencies {}
        }
    }
}
