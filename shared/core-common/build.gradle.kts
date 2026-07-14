/*
 * :shared:core-common — second Kotlin Multiplatform module (KMP migration, Phase 1).
 *
 * Purpose: host the cross-cutting platform contracts the rest of the shared code will depend on,
 * each defined in commonMain and backed by an Android `actual` and a wasmJs `actual`:
 *   - DispatcherProvider  → CoroutineDispatcher abstraction (no Dispatchers.IO on wasm).
 *   - KeyValueStore       → suspend key/value persistence (Android DataStore / web localStorage).
 *   - CrashReporter       → crash/log reporting (Firebase Crashlytics on Android, no-op on web).
 *   - Page / PaginatedResult → a platform-neutral pagination model (future PagingData replacement).
 *
 * Targets/source-set/plugin setup mirror :shared:core-model (the AGP-9 KMP-library path).
 *
 * HARD RULE: commonMain must contain ZERO Android / AndroidX / browser imports — pure Kotlin +
 * kotlinx-coroutines only. Android-only APIs live in androidMain; browser APIs in wasmJsMain.
 */
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
}

kotlin {
    // ── Android target ────────────────────────────────────────────────────────────────────────
    androidLibrary {
        namespace = "com.mmg.manahub.core.common"
        compileSdk = 36
        minSdk = 29

        // Enable a JVM host unit-test component so commonTest runs as an Android host test
        // (task: :shared:core-common:testAndroidHostTest).
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
                implementation(libs.coroutines.core)
            }
        }
        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }
        androidMain {
            dependencies {
                // KeyValueStore Android actual — DataStore Preferences.
                implementation(libs.datastore.preferences)
                // CrashReporter Android actual — Firebase Crashlytics.
                implementation(libs.firebase.crashlytics)
            }
        }
        wasmJsMain {
            dependencies {}
        }
    }
}
