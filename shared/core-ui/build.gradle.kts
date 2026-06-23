/*
 * :shared:core-ui — Kotlin Multiplatform module (KMP migration, Phase 3 / Slice 1).
 *
 * Purpose: host the platform-agnostic DESIGN SYSTEM shared by Android + Web. This includes
 * the MagicTheme token system (MagicColors, Spacing, Shapes), CompositionLocals, and the
 * Material 3 bridge. Typography and font loading stay in :app (androidMain) until the CMP
 * resource system replaces R.font.* references.
 *
 * Depends on:
 *   - :shared:core-model  (PreferredCurrency, AppTheme used by the theme composable)
 *
 * Targets/source-set/plugin setup mirror the other shared modules but ADD the Compose
 * Multiplatform plugin for compose.runtime / compose.material3 / compose.foundation artifacts.
 *
 * HARD RULE: commonMain must contain ZERO Android / AndroidX / browser / Room imports — only
 * the compose-multiplatform accessors (compose.runtime, compose.material3, etc.) which resolve
 * to JetBrains CMP artifacts on all targets.
 */
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose.multiplatform)  // JetBrains Compose Multiplatform
    alias(libs.plugins.kotlin.compose)          // Kotlin compose compiler
}

kotlin {
    // ── Android target ────────────────────────────────────────────────────────────────────────
    androidLibrary {
        namespace = "com.mmg.manahub.core.ui"
        compileSdk = 36
        minSdk = 29

        // Enable a JVM host unit-test component so commonTest runs as an Android host test
        // (task: :shared:core-ui:testAndroidHostTest).
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
                // Theme references core-model types (PreferredCurrency).
                implementation(project(":shared:core-model"))
                // CMP compose artifacts — resolve to JetBrains multiplatform compose on all targets,
                // binary-compatible with AndroidX Compose on Android.
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
                implementation(libs.coil.compose)
            }
        }
        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }
        // androidMain / wasmJsMain intentionally empty — all theme code is pure-Compose.
        androidMain { dependencies {} }
        wasmJsMain { dependencies {} }
    }
}
