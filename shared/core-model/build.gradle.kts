/*
 * :shared:core-model — first Kotlin Multiplatform module (KMP migration, Phase 0 / Spike A).
 *
 * Purpose: prove the KMP toolchain (Kotlin 2.3.20 + AGP 9.1) can build ONE shared module for BOTH
 * the Android target and the Web (wasmJs) target, sitting alongside the existing Android-only :app.
 *
 * Targets:
 *   - androidLibrary  → the Android target, configured via the AGP KMP library plugin
 *                       (com.android.kotlin.multiplatform.library). This is the AGP-9 supported path;
 *                       the legacy `com.android.library` + `kotlin { androidTarget() }` combo is not
 *                       used because AGP 9 dropped the standalone Kotlin-Android plugin.
 *   - wasmJs          → the Web target (Compose Multiplatform for Web runs on this).
 *
 * Source sets: commonMain (the only one with code today), empty androidMain / wasmJsMain placeholders
 * for future platform actuals, and commonTest using kotlin-test.
 *
 * HARD RULE: commonMain must contain ZERO Android / AndroidX / browser imports — pure Kotlin only.
 */
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
}

kotlin {
    // ── Android target ────────────────────────────────────────────────────────────────────────
    androidLibrary {
        namespace = "com.mmg.manahub.core.model"
        compileSdk = 36
        minSdk = 29

        // Enable a JVM host unit-test component so commonTest runs as an Android host test
        // (task: :shared:core-model:testDebugUnitTest).
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
            // Pure Kotlin only — no dependencies needed for the moved domain enums.
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
