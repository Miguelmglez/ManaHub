package com.mmg.manahub.core.util

import android.os.Bundle
import android.util.Log
import com.google.firebase.analytics.FirebaseAnalytics
import com.mmg.manahub.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Centralizes Firebase Analytics event logging.
 * Can be injected into ViewModels, Repositories, or any Hilt-managed class.
 */
@Singleton
class AnalyticsHelper @Inject constructor(
    private val firebaseAnalytics: FirebaseAnalytics
) {

    /**
     * Logs a custom event.
     * @param name Event name (max 40 characters, use underscores).
     * @param params Optional map of parameters (String, Int, Long, Double, Boolean).
     */
    fun logEvent(name: String, params: Map<String, Any?>? = null) {
        if (BuildConfig.DEBUG) {
            Log.d("AnalyticsHelper", "Event: $name, Params: $params")
        }

        val bundle = params?.let {
            Bundle().apply {
                it.forEach { (key, value) ->
                    when (value) {
                        is String -> putString(key, value)
                        is Int -> putInt(key, value)
                        is Long -> putLong(key, value)
                        is Double -> putDouble(key, value)
                        is Boolean -> putBoolean(key, value)
                        is Enum<*> -> putString(key, value.name)
                        null -> putString(key, "null")
                        else -> putString(key, value.toString())
                    }
                }
            }
        }
        firebaseAnalytics.logEvent(name, bundle)
    }

    /** Sets a persistent user property. */
    fun setUserProperty(name: String, value: String?) {
        if (BuildConfig.DEBUG) {
            Log.d("AnalyticsHelper", "UserProperty: $name = $value")
        }
        firebaseAnalytics.setUserProperty(name, value)
    }

    /** Sets the user ID for cross-device tracking. */
    fun setUserId(id: String?) {
        if (BuildConfig.DEBUG) {
            Log.d("AnalyticsHelper", "UserId: $id")
        }
        firebaseAnalytics.setUserId(id)
    }

    /** Logs a screen view event. */
    fun logScreenView(screenName: String, screenClass: String? = null) {
        logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, mapOf(
            FirebaseAnalytics.Param.SCREEN_NAME to screenName,
            FirebaseAnalytics.Param.SCREEN_CLASS to (screenClass ?: screenName)
        ))
    }
}
