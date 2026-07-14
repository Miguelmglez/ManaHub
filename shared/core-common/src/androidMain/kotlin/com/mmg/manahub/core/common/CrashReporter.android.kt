package com.mmg.manahub.core.common

import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Android [CrashReporter] actual — delegates to Firebase Crashlytics.
 */
actual fun provideCrashReporter(): CrashReporter = FirebaseCrashReporter()

private class FirebaseCrashReporter : CrashReporter {
    private val crashlytics: FirebaseCrashlytics get() = FirebaseCrashlytics.getInstance()

    override fun recordException(throwable: Throwable) {
        crashlytics.recordException(throwable)
    }

    override fun log(message: String) {
        crashlytics.log(message)
    }

    override fun setCustomKey(key: String, value: String) {
        crashlytics.setCustomKey(key, value)
    }
}
