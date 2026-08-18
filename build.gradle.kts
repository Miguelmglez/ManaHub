// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.test) apply false
    // kotlin.android no longer needed as a separate plugin — AGP 9+ has built-in Kotlin support.
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
    // KMP migration — Phase 0, Spike A. Declared here (apply false) so the plugin version is pinned
    // on the root classpath; AGP 9's built-in Kotlin support otherwise leaves it "unknown version".
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.android.kmp.library) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    // Deck Engine Unification plan, RUN 5 / D5: :tools:tag-pipeline is a plain Kotlin/JVM module
    // (not KMP). Declared here (apply false) for the same reason as kotlin-multiplatform above —
    // AGP 9's built-in Kotlin support already puts org.jetbrains.kotlin.jvm on the root classpath
    // with an unknown version, so the submodule's own `alias(libs.plugins.kotlin.jvm)` fails
    // version-compatibility checking unless the version is pinned here first.
    alias(libs.plugins.kotlin.jvm) apply false
}

// Web roadmap W0 (:webApp, wasmJs target): the Kotlin/Wasm Gradle plugin registers its OWN
// Node.js-distribution repository at project-configuration time (to download the Node.js binary
// used by the wasmJs toolchain), which conflicts with settings.gradle.kts's
// `RepositoriesMode.FAIL_ON_PROJECT_REPOS` ("repository ... was added by unknown code" build
// failure). Rather than loosen that project-wide repository hygiene rule, point the wasm Node.js
// setup at the already-installed system Node.js (verified present: `node --version` on this
// machine) so the plugin never needs to add a download repository in the first place.
plugins.withType<org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsPlugin> {
    the<org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsEnvSpec>().download = false
}
// Same fix for Yarn (the wasm toolchain's default package manager — kept as Yarn rather than
// switched to plain npm: a real npm/cli bug, https://github.com/npm/cli/issues/9133, makes npm's
// own `--prefer-online`/`--prefer-offline` flag combo fail specifically when resolving a git-URL
// dependency, which Kotlin's aggregated wasm tooling package.json has (`karma: github:Kotlin/karma`,
// used by wasmJsBrowserTest across every wasmJs module in this project) — Yarn resolves git
// dependencies through its own logic and isn't affected. Installed locally via `npm install -g yarn`.
plugins.withType<org.jetbrains.kotlin.gradle.targets.wasm.yarn.WasmYarnPlugin> {
    the<org.jetbrains.kotlin.gradle.targets.wasm.yarn.WasmYarnRootEnvSpec>().download = false
}
// The wasmJs webpack bundling task (`wasmJsBrowserProductionWebpack`/`...Distribution`) reuses the
// CLASSIC (non-wasm) Kotlin/JS Node.js infra internally, separate from the wasm-specific
// WasmNodeJsPlugin above — needs the identical download=false + system-Node.js fix or it tries to
// exec a never-downloaded `node.exe` under `.gradle/nodejs/`.
plugins.withType<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsPlugin> {
    the<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsEnvSpec>().download = false
}

// Auth SDK bump (supabase-bom 3.1.4 -> 3.5.0, 2026-08-13, UserUpdateBuilder.currentPassword):
// supabase-kt >= 3.2.1 itself requires kotlinx-datetime 0.7.1, which Gradle's default
// highest-version-wins conflict resolution silently applies PROJECT-WIDE, overriding this
// project's own pinned `kotlinx-datetime = "0.6.2"` (gradle/libs.versions.toml) even for modules
// that never touch Supabase. kotlinx-datetime 0.7.x turns its own `Clock`/`Instant` into
// @Deprecated typealiases for `kotlin.time.Clock`/`kotlin.time.Instant` (stdlib types gated
// `@SinceKotlin("2.3")`) — under this project's default Kotlin compiler apiVersion resolution,
// every existing `Clock.System.now()`/`Clock.System.todayIn(...)` call site across the WHOLE app
// (Decks, Gamification, Puzzle, Tournament, Trades, Friends, Home, ManaHubApp — none of them
// Supabase-related) broke with "Unresolved reference 'System'", confirmed via a full
// `:app:assembleDebug` after the bump. Force the datetime resolution back to the project's own
// already-tested 0.6.2 so the auth SDK bump doesn't drag an unrelated, unvetted kotlinx-datetime
// migration along with it — supabase-kt's own Instant/Clock usage stays internal to its compiled
// jar and is not part of the public API surface this project's `AuthRepositoryImpl`/
// `WebAuthRepository` consume (both parse timestamps from plain `String` fields, never receive a
// supabase-kt-typed `Instant`/`Clock` value directly), so pinning back is safe.
subprojects {
    configurations.all {
        resolutionStrategy {
            force("org.jetbrains.kotlinx:kotlinx-datetime:0.6.2")
        }
    }
}
