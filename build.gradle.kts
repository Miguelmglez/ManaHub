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
