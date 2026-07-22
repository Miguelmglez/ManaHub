/*
 * :tools:tag-pipeline — Deck Engine Unification plan, RUN 5 (plan §5 Phase 5a / decision D5).
 *
 * A plain Kotlin/JVM CLI (NOT a KMP module) that bulk-computes strategy tags/tribes/theme+archetype
 * affinities for every Magic card, by running Scryfall's `oracle-cards` bulk dump through the EXACT
 * SAME production rule engine the in-app tagging pipeline uses (`TagDictionary`/`StrategyAnalyzer`
 * from `:shared:core-data`, `TribeDeriver` from `:shared:core-domain`) — zero drift between this
 * offline pipeline and what a user sees when a card is auto-tagged live in the app. This is why D5
 * rejected a Python port: two independent rule engines would inevitably diverge.
 *
 * Depends on the shared modules' `jvm()` targets (added alongside this module — see
 * :shared:core-model/core-common/core-domain/core-data's build.gradle.kts for the "why jvm()"
 * rationale) rather than :app directly: :app is a `com.android.application` module and Gradle does
 * not allow any module to depend on an Android *application* project (only library/AAR-producing
 * modules can be consumed as dependencies).
 *
 * Run via `./gradlew :tools:tag-pipeline:run --args="..."` — see README.md for the full command
 * reference (smoke test + full production run).
 */
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

application {
    mainClass.set("com.mmg.manahub.tools.tagpipeline.MainKt")
}

// Run the CLI with the REPO ROOT as its working directory, not this subproject's directory (Gradle's
// default for `application`'s `run` task). Two reasons this matters, both found running the real
// production-readiness smoke tests: (1) `--out build/pipeline-out/...`-style relative paths in the
// README/docs must land where a human invoking `./gradlew :tools:tag-pipeline:run` from the repo
// root would expect (repo-root `build/`, not `tools/tag-pipeline/build/`); (2) the `upload`
// subcommand's `local.properties` fallback (`upload/SupabaseCredentials.kt`) reads the repo-root
// file — this project's ONE established secrets file — via a plain relative path, which only
// resolves correctly when the process's working directory IS the repo root.
tasks.named<JavaExec>("run") {
    workingDir = rootDir
}

dependencies {
    // The production rule engine + models this pipeline reuses (zero drift, D5's whole point).
    implementation(project(":shared:core-model"))
    implementation(project(":shared:core-domain"))
    implementation(project(":shared:core-data"))

    implementation(libs.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    // core-model depends on this as `implementation` (not `api`), so it isn't exposed
    // transitively — declared directly here for Clock.System.now()/Instant ISO-8601 strings.
    implementation(libs.kotlinx.datetime)

    // NOTE — deliberately NOT depending on Ktor for the Scryfall/EDHREC bulk-file fetches (see
    // `ScryfallBulkClient`/`EdhrecThemeClient`): those stream large files straight to disk via
    // `java.net.http.HttpClient` and have no cross-platform constraint to satisfy.
    //
    // Ktor IS added below, scoped to ONE consumer: the Archidekt category-enrichment sampler
    // (RUN "pipeline production-readiness", plan §5 Phase 5a addendum). The task explicitly calls
    // for reusing this app's EXISTING `ArchidektClient`/`ArchidektRequestQueue`/DTOs
    // (`:shared:core-data`, already jvm()-targeted since RUN 5) rather than hand-rolling a second
    // Archidekt HTTP client — literal class reuse gives the same zero-drift guarantee D5 established
    // for the tagging rule engine (same DTOs, same 429/503 retry/back-off/jitter policy, no risk of a
    // second implementation silently diverging from the one the live app ships). CIO is the plain
    // pure-Kotlin JVM engine (no OkHttp/Android dependency needed for a CLI).
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    // kotlin("test") on a plain kotlin.jvm module resolves to kotlin-test-junit (JUnit4) —
    // matching the JUnit4 convention already used everywhere else in this repo (:app, :shared:*'s
    // commonTest via kotlin-test); no reason to introduce a second test framework for one module.
    testImplementation(kotlin("test"))
    testImplementation(libs.coroutines.test)
    // Lets ArchidektCategorySamplerTest exercise ArchidektClient/ArchidektRequestQueue against a
    // scripted MockEngine instead of real network — offline-by-default test convention (see
    // README "Testing").
    testImplementation(libs.ktor.client.mock)
}

kotlin {
    jvmToolchain(17)
}
