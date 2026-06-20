package com.mmg.manahub.core.common

/**
 * Platform-neutral crash/log reporting contract.
 *
 * Lets shared code record non-fatal exceptions, breadcrumb logs, and custom keys without depending
 * on Firebase directly. The Android `actual` ([provideCrashReporter]) wraps
 * `FirebaseCrashlytics.getInstance()`; the web `actual` is a no-op (no Crashlytics on wasmJs).
 *
 * NOTE: callers must never pass PII (emails, real names, tokens, raw free-text queries) — log
 * lengths / enum ids only, per the project telemetry rules.
 */
interface CrashReporter {

    /** Records a non-fatal exception. */
    fun recordException(throwable: Throwable)

    /** Records a breadcrumb log message (snake_case `action_context_result` style). */
    fun log(message: String)

    /** Sets a custom key surfaced alongside the next crash/non-fatal report. */
    fun setCustomKey(key: String, value: String)
}

/**
 * Returns the platform [CrashReporter] — Firebase Crashlytics on Android, a no-op on web.
 */
expect fun provideCrashReporter(): CrashReporter
