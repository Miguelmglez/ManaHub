package com.mmg.manahub.core.domain.repository

/**
 * Registers and revokes this device's FCM push token with the backend.
 *
 * Implementations are responsible for resilient delivery: a failed network call
 * should be retried in the background rather than silently dropped.
 */
interface PushTokenRepository {

    /** Registers [token] for the current user, associating it with the active locale. */
    suspend fun register(token: String)

    /** Removes [token] for the current user (e.g. on sign-out for this device only). */
    suspend fun unregister(token: String)

    /** Removes every token belonging to the current user (e.g. before account deletion). */
    suspend fun unregisterAll()

    /** Re-registers the current device token with a new [locale] after a language change. */
    suspend fun updateLocale(locale: String)
}
