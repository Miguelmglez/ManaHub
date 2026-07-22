package com.mmg.manahub.core.common

/**
 * Plain-JVM [CrashReporter] actual (Deck Engine Unification plan, RUN 5 / D5 — added so
 * :tools:tag-pipeline can depend on this module's commonMain). There is no Crashlytics on a JVM
 * CLI; unlike the wasmJs no-op, this prints to stderr so pipeline runs — which are unattended,
 * long-running, and have no crash-reporting backend of their own — don't silently lose diagnostic
 * signal. Never receives PII (same contract as the Android/wasmJs actuals).
 */
actual fun provideCrashReporter(): CrashReporter = ConsoleCrashReporter

private object ConsoleCrashReporter : CrashReporter {
    override fun recordException(throwable: Throwable) {
        System.err.println("[tag-pipeline] non-fatal: ${throwable.message}")
    }

    override fun log(message: String) {
        System.err.println("[tag-pipeline] log: $message")
    }

    override fun setCustomKey(key: String, value: String) {
        System.err.println("[tag-pipeline] key: $key=$value")
    }
}
