package com.mmg.manahub.core.util

import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Helper para centralizar el registro de eventos en Firebase Analytics.
 * Puede ser inyectado en ViewModels, Repositorios o cualquier clase gestionada por Hilt.
 */
@Singleton
class AnalyticsHelper @Inject constructor(
    private val firebaseAnalytics: FirebaseAnalytics
) {

    /**
     * Registra un evento personalizado.
     * @param name Nombre del evento (máximo 40 caracteres, usar guiones bajos).
     * @param params Mapa opcional de parámetros (String, Int, Long, Double, Boolean).
     */
    fun logEvent(name: String, params: Map<String, Any?>? = null) {
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

    /**
     * Establece una propiedad de usuario persistente.
     */
    fun setUserProperty(name: String, value: String?) {
        firebaseAnalytics.setUserProperty(name, value)
    }

    /**
     * Identifica al usuario para el seguimiento cross-device.
     */
    fun setUserId(id: String?) {
        firebaseAnalytics.setUserId(id)
    }

    /**
     * Helper para eventos comunes de navegación o pantalla.
     */
    fun logScreenView(screenName: String, screenClass: String? = null) {
        logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, mapOf(
            FirebaseAnalytics.Param.SCREEN_NAME to screenName,
            FirebaseAnalytics.Param.SCREEN_CLASS to (screenClass ?: screenName)
        ))
    }
}
