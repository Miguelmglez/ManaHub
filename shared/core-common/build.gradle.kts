/*
 * :shared:core-common — second Kotlin Multiplatform module (KMP migration, Phase 1).
 *
 * Purpose: host the cross-cutting platform contracts the rest of the shared code will depend on,
 * each defined in commonMain and backed by an Android `actual` and a wasmJs `actual`:
 *   - DispatcherProvider  → CoroutineDispatcher abstraction (no Dispatchers.IO on wasm).
 *   - KeyValueStore       → suspend key/value persistence (Android DataStore / web localStorage).
 *   - CrashReporter       → crash/log reporting (Firebase Crashlytics on Android, no-op on web).
 *   - Page / PaginatedResult → a platform-neutral pagination model (future PagingData replacement).
 *   - SupabaseJwt         → pure-Kotlin JWT-claim decoding (e.g. GoTrue's top-level `is_anonymous`
 *                           claim), shared by the Android AuthRepositoryImpl and the web AuthViewModel.
 *
 * Targets/source-set/plugin setup mirror :shared:core-model (the AGP-9 KMP-library path).
 *
 * HARD RULE: commonMain must contain ZERO Android / AndroidX / browser imports — pure Kotlin +
 * kotlinx-coroutines only. Android-only APIs live in androidMain; browser APIs in wasmJsMain.
 */
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    // SupabaseJwt.kt parses the decoded JWT payload as JSON (kotlinx.serialization.json).
    alias(libs.plugins.kotlin.serialization)
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

    // ── Plain JVM target (Deck Engine Unification plan, RUN 5 / D5) ──────────────────────────
    // See :shared:core-model's build.gradle.kts for the full rationale. core-domain/core-data
    // (which :tools:tag-pipeline actually needs) depend on this module via `implementation`, so
    // the jvm() target must exist here too for Gradle's KMP dependency resolution to find a
    // matching target across the whole project-dependency chain.
    jvm()

    // JVM toolchain — match :app (JVM 17).
    jvmToolchain(17)

    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.coroutines.core)
                // SupabaseJwt.kt: parses the base64url-decoded JWT payload segment as JSON.
                implementation(libs.kotlinx.serialization.json)
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
            dependencies {
                // KeyValueStore wasmJs actual — real window.localStorage (web roadmap W1).
                implementation(libs.kotlinx.browser)
            }
        }
        // jvmMain intentionally has no code — no jvm-specific actual is needed by the pipeline
        // (it only consumes pure commonMain types: Card, CardTag, TagDictionaryEntry, etc.).
        // If a jvm actual for DispatcherProvider/KeyValueStore/CrashReporter is ever needed, add
        // it here (java.util.concurrent-backed dispatcher, file-backed KeyValueStore, no-op
        // CrashReporter) — the CLI does not need any of these today.
        jvmMain {
            dependencies {}
        }
    }
}
