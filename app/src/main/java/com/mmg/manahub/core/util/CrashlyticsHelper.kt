package com.mmg.manahub.core.util

import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Reports a non-fatal exception to Crashlytics, stripping the original
 * message to avoid leaking user-controlled data (card names, player names).
 * The exception type and stack trace are preserved for diagnosis.
 *
 * Use this for exceptions from external sources or user input where the
 * original message might contain sensitive data.
 */
fun recordSafeNonFatal(tag: String, e: Throwable) {
    // If the error is already an OOM, don't attempt to wrap or report it here.
    // Attempting to process an OOM often triggers another OOM in the Crashlytics
    // background thread during stack trace trimming (secondary crash).
    if (e is OutOfMemoryError) return

    val sanitized = RuntimeException("[$tag] ${e::class.simpleName}", e)
    FirebaseCrashlytics.getInstance().recordException(sanitized)
}

/**
 * Records a non-fatal exception with a developer-controlled message only.
 * Never pass user-supplied strings as the message parameter.
 *
 * Use this for expected error conditions where you want to log a safe,
 * developer-defined message.
 */
fun recordNonFatal(message: String, e: Throwable? = null) {
    val throwable = e ?: RuntimeException(message)
    FirebaseCrashlytics.getInstance().apply {
        log(message)
        recordException(throwable)
    }
}
