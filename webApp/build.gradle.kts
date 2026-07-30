import java.util.Properties

/*
 * :webApp — Compose Multiplatform / wasmJs web application (KMP web roadmap, see
 * docs/plans/kmp-migration-plan.md §5 and docs/plans/kmp-migration-progress.md).
 *
 * Web-only KMP module: `wasmJs` is the ONLY target (no `androidLibrary`/`jvm`, unlike the
 * `:shared:core-*` modules which are Android+Web+JVM).
 *
 * Status: W0 (runtime smoke spike — supabase-kt/Ktor/Coil3/localStorage proven live on wasmJs) and
 * W1 (real Koin startup + responsive shell + `ThemeShowcaseScreen`, replacing W0's throwaway
 * `main()`) are both done. Auth (W2) is next.
 *
 * HARD RULE (same as :shared:core-*): commonMain/wasmJsMain must never import the Android
 * `SupabaseModule`/`SecureSessionManager` (Hilt+Android-only) — this module builds its own
 * from-scratch Supabase client + Koin graph.
 */
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// ── W0 spike: build-time config injection ───────────────────────────────────────────────────────
// wasmJs-only KMP modules have no Android `BuildConfig` mechanism, so SUPABASE_URL/SUPABASE_ANON_KEY
// (gitignored in local.properties, same as :app's `requiredProperty` pattern) are generated into a
// throwaway Kotlin source file at build time instead of being hardcoded in committed sources. This
// generation mechanism is expected to be revisited/promoted in W1+ once the real app needs more than
// two constants.
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use { localProperties.load(it) }
}

fun requiredProperty(name: String): String =
    localProperties.getProperty(name)
        ?: System.getenv(name)
        ?: error("Missing required build property: $name. Add it to local.properties or set as environment variable in CI.")

val generatedWebAppConfigDir = layout.buildDirectory.dir("generated/webAppConfig/kotlin")

val generateWebAppConfig by tasks.registering {
    val outputDirProvider = generatedWebAppConfigDir
    outputs.dir(outputDirProvider)
    doLast {
        val supabaseUrl = requiredProperty("SUPABASE_URL")
        val supabaseAnonKey = requiredProperty("SUPABASE_ANON_KEY")
        val packageDir = outputDirProvider.get().asFile.resolve("com/mmg/manahub/web/config")
        packageDir.mkdirs()
        packageDir.resolve("WebAppConfig.kt").writeText(
            """
            |package com.mmg.manahub.web.config
            |
            |// GENERATED at build time from local.properties by :webApp:generateWebAppConfig.
            |// DO NOT EDIT. DO NOT COMMIT (build/ is gitignored).
            |internal object WebAppConfig {
            |    const val SUPABASE_URL: String = "$supabaseUrl"
            |    const val SUPABASE_ANON_KEY: String = "$supabaseAnonKey"
            |}
            |
            """.trimMargin()
        )
    }
}

kotlin {
    // ── Web target (Compose Multiplatform / wasmJs) — the ONLY target for this module ──────────
    @Suppress("OPT_IN_USAGE")
    wasmJs {
        outputModuleName.set("webApp")
        browser {
            commonWebpackConfig {
                outputFileName = "webApp.js"
            }
        }
        binaries.executable()
    }

    // JVM toolchain — match :app / :shared:* (JVM 17).
    jvmToolchain(17)

    sourceSets {
        wasmJsMain {
            kotlin.srcDir(generatedWebAppConfigDir)
            dependencies {
                implementation(project(":shared:core-ui"))
                implementation(project(":shared:core-common"))
                // core-ui/core-common depend on core-model via `implementation`, so it is NOT
                // exposed transitively — webApp's own code (ThemeShowcaseScreen's placeholder
                // Card instances) needs it declared directly.
                implementation(project(":shared:core-model"))
                // W2a: WebAppKoinModule calls createManaHubSupabaseClient(...) from core-data's
                // commonMain — same transitive-exposure reasoning as core-model above.
                implementation(project(":shared:core-data"))

                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
                // Same transitive-exposure reasoning as core-model above: core-ui declares this as
                // `implementation`, so App.kt's own `Icons.Default.*` usage needs it directly too.
                implementation(compose.materialIconsExtended)

                // `platform()` inside a KMP sourceSet's `KotlinDependencyHandler` is deprecated
                // (KT-58759) in favor of calling the project's own Gradle DependencyHandler directly.
                implementation(project.dependencies.platform(libs.supabase.bom))
                implementation(libs.supabase.auth)
                implementation(libs.supabase.postgrest)

                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.js)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.serialization.kotlinx.json)

                implementation(libs.coil.compose)
                implementation(libs.coil.network.ktor3)

                implementation(libs.kotlinx.browser)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.coroutines.core)

                // Web roadmap W1: Koin DI + the pure-CMP koinViewModel() (koin-androidx-compose is
                // Android-only). Supabase/Ktor above are kept even though W1's own screen doesn't use
                // them -- W2 (auth on web, the very next slice) needs them back immediately.
                implementation(libs.koin.core)
                implementation(libs.koin.compose.viewmodel)
            }
        }
    }
}

tasks.matching { it.name == "compileKotlinWasmJs" }.configureEach {
    dependsOn(generateWebAppConfig)
}

// W0 spike finding: Gradle's default "highest version wins" resolution bumps kotlinx-datetime from
// this project's pinned 0.6.2 up to 0.7.1 (something else in the wasmJs graph — CMP/Coil3 — requests
// 0.7.1 directly). supabase-kt 3.1.4's precompiled wasmJs klib was built against kotlinx-datetime
// 0.6.x and references `InstantIso8601Serializer`, a symbol kotlinx-datetime 0.7.x removed/relocated
// (the 0.6→0.7 release dropped the old Instant type in favor of stdlib's kotlin.time.Instant) — this
// manifests as an IrLinkageError ONLY when the code path is actually exercised at wasmJs runtime
// (auth session-expiry parsing during signInAnonymously), not at compile time. Force the resolved
// version back down to what supabase-kt actually targets.
configurations.all {
    resolutionStrategy {
        force("org.jetbrains.kotlinx:kotlinx-datetime:0.6.2")
    }
}

// See root build.gradle.kts for the Node.js/Yarn equivalents of this fix (all three trip the same
// FAIL_ON_PROJECT_REPOS conflict). BinaryenPlugin/BinaryenEnvSpec appear to be applied to the
// subproject declaring the wasmJs target (unlike the root-singleton Node/Yarn plugins), hence this
// block lives here rather than in the root script.
@OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
plugins.withType<org.jetbrains.kotlin.gradle.targets.wasm.binaryen.BinaryenPlugin> {
    the<org.jetbrains.kotlin.gradle.targets.wasm.binaryen.BinaryenEnvSpec>().download = false
}
