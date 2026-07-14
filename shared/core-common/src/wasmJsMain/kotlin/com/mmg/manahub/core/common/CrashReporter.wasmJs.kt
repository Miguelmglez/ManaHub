package com.mmg.manahub.core.common

/**
 * wasmJs [CrashReporter] actual — a no-op. There is no Crashlytics on the web target.
 *
 * TODO: optionally forward to a web error-reporting service (e.g. Sentry browser SDK) in a later
 *  web-hardening phase. For now web simply drops reports.
 */
actual fun provideCrashReporter(): CrashReporter = NoOpCrashReporter

private object NoOpCrashReporter : CrashReporter {
    override fun recordException(throwable: Throwable) = Unit
    override fun log(message: String) = Unit
    override fun setCustomKey(key: String, value: String) = Unit
}
