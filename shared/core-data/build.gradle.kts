/*
 * :shared:core-data — Kotlin Multiplatform module (KMP migration, Phase 2).
 *
 * Purpose: host the platform-agnostic DATA layer shared by Android + Web. This includes
 * rate-limit queue infrastructure (RateLimitedQueue), and later Ktor networking, caching
 * strategies, and repository implementations whose signatures reference only :shared:core-model
 * and :shared:core-domain types.
 *
 * Room DAOs/entities/migrations stay in :app (androidMain) — Room has no wasmJs target.
 * Web data sources (Supabase-remote-first + IndexedDB/localStorage cache) will live in wasmJsMain.
 *
 * Depends on:
 *   - :shared:core-model  (domain model types referenced by repo impls)
 *   - :shared:core-domain (repository interfaces that impls will fulfill)
 *   - :shared:core-common (DispatcherProvider, KeyValueStore, CrashReporter)
 *
 * Targets/source-set/plugin setup mirror :shared:core-domain (the AGP-9 KMP-library path).
 *
 * HARD RULE: commonMain must contain ZERO Android / AndroidX / browser / Room imports — pure
 * Kotlin + kotlinx-coroutines + shared module dependencies only.
 */
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
}

kotlin {
    // ── Android target ────────────────────────────────────────────────────────────────────────
    androidLibrary {
        namespace = "com.mmg.manahub.core.data"
        compileSdk = 36
        minSdk = 29

        // Enable a JVM host unit-test component so commonTest runs as an Android host test
        // (task: :shared:core-data:testAndroidHostTest).
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
                // Repository impls reference core-model types.
                api(project(":shared:core-model"))
                // Repository interfaces live in core-domain; impls fulfill them.
                api(project(":shared:core-domain"))
                // Cross-cutting contracts (DispatcherProvider, KeyValueStore, CrashReporter).
                implementation(project(":shared:core-common"))
                implementation(libs.coroutines.core)
            }
        }
        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }
        // androidMain / wasmJsMain intentionally have minimal code yet (placeholders for future actuals).
        androidMain {
            dependencies {}
        }
        wasmJsMain {
            dependencies {}
        }
    }
}
