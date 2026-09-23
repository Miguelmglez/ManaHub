package com.mmg.manahub.core.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Domain-level contract for per-event-type push notification preferences.
 *
 * Preferences are stored as a `event_type -> boolean` map. A missing key is treated
 * as **enabled** (opt-out model): the user only ever stores an explicit `false` to
 * silence an event type, so a fresh account receives every notification by default.
 *
 * Writes never throw: a network or backend failure is returned as [Result.failure] and the
 * cached map is rolled back to its previous value. Writing without a signed-in user fails with
 * [NotificationPrefsUnauthenticatedException].
 */
interface NotificationPrefsRepository {

    /**
     * Enables or disables push notifications for a single [eventType] and persists the change.
     *
     * @param eventType The backend event identifier (e.g. `"trade_proposed"`).
     * @param enabled `true` to receive notifications for this event, `false` to silence them.
     */
    suspend fun setEventEnabled(eventType: String, enabled: Boolean): Result<Unit>

    /**
     * Applies [enabled] to every event in [eventTypes] as ONE atomic read-merge-upsert, so a
     * group toggle can never leave the group half-written.
     */
    suspend fun setEventsEnabled(eventTypes: List<String>, enabled: Boolean): Result<Unit>

    /**
     * Returns whether notifications for [eventType] are enabled.
     *
     * @return `true` when the key is explicitly enabled **or absent** (default), `false` only
     *   when the user has explicitly opted out.
     */
    suspend fun isEventEnabled(eventType: String): Boolean

    /**
     * Re-reads the current user's preferences from the backend, replacing the cache. Callers
     * invoke this when the signed-in user changes so the map never shows another account's prefs.
     */
    suspend fun refresh(): Result<Unit>

    /**
     * Emits the current `event_type -> boolean` preference map for the signed-in user.
     *
     * Starts empty (all enabled) and loads the persisted values lazily on subscription; emits an
     * empty map while no user is signed in. Re-emits after every write (optimistically) and
     * after every successful [refresh].
     */
    val prefsFlow: Flow<Map<String, Boolean>>
}

/** Thrown (as a [Result.failure]) when a preference write is attempted without a signed-in user. */
class NotificationPrefsUnauthenticatedException : IllegalStateException("notification_prefs_unauthenticated")
