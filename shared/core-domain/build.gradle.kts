/*
 * :shared:core-domain — third Kotlin Multiplatform module (KMP migration, Phase 2 / Slice 1).
 *
 * Purpose: host the platform-agnostic DOMAIN contracts (repository interfaces, and later the pure
 * use cases) shared by Android + Web. Slice 1 moves a first batch of repository INTERFACES whose
 * signatures reference only :shared:core-model types, kotlinx (Flow, etc.) and primitives. The
 * concrete implementations stay in :app (Android side) and keep implementing these interfaces.
 *
 * Depends on :shared:core-model (the moved interfaces reference its model types) and
 * :shared:core-common (cross-cutting contracts — CrashReporter, DispatcherProvider, KeyValueStore —
 * consumed by use cases moved here, e.g. ImportCommunityDeckUseCase).
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
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    // ── Android target ────────────────────────────────────────────────────────────────────────
    androidLibrary {
        namespace = "com.mmg.manahub.core.domain"
        compileSdk = 37
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

    // ── Plain JVM target (Deck Engine Unification plan, RUN 5 / D5) ──────────────────────────
    // See :shared:core-model's build.gradle.kts for the full rationale. This module hosts
    // TribeDeriver/ArchetypeId/ThemeId — :tools:tag-pipeline needs them directly.
    jvm()

    // JVM toolchain — match :app (JVM 17).
    jvmToolchain(17)

    sourceSets {
        commonMain {
            dependencies {
                // Moved repository interfaces reference core-model types (UserPreferences, CollectionStats…).
                api(project(":shared:core-model"))
                // Cross-cutting contracts (CrashReporter, DispatcherProvider, KeyValueStore) consumed
                // by use cases moved here (e.g. ImportCommunityDeckUseCase → CrashReporter).
                implementation(project(":shared:core-common"))
                implementation(libs.coroutines.core)
                // Use cases use Clock.System.now() for event timestamps.
                implementation(libs.kotlinx.datetime)
                // Daily Puzzle feature (Batch B1): SubmitPuzzleGuessUseCase parses a Puzzle's raw
                // payloadJson into a typed GuessCardPayload.
                implementation(libs.kotlinx.serialization.json)
            }
        }
        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
                // Deck Doctor Community/Archetype plan, Phase 2: SuggestAddsFromCollectionUseCase's
                // `invoke` is `suspend` (withContext(ioDispatcher)) — runTest/virtual-time control is
                // needed to call it deterministically from commonTest.
                implementation(libs.coroutines.test)
            }
        }
        // androidMain / wasmJsMain intentionally have no code yet (placeholders for future actuals).
        androidMain {
            dependencies {}
        }
        wasmJsMain {
            dependencies {}
        }
        // jvmMain intentionally has no code — no jvm-specific actual is needed (see core-common's
        // note; core-domain has zero expect/actual declarations of its own).
        jvmMain {
            dependencies {}
        }
    }
}
