package com.mmg.manahub.core.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Domain-level contract for per-event-type push notification preferences.
 *
 * Preferences are stored as a `event_type -> boolean` map. A missing key is treated
 * as **enabled** (opt-out model): the user only ever stores an explicit `false` to
 * silence an event type, so a fresh account receives every notification by default.
 */
interface NotificationPrefsRepository {

    /**
     * Enables or disables push notifications for a single [eventType] and persists the change.
     *
     * @param eventType The backend event identifier (e.g. `"trade_proposed"`).
     * @param enabled `true` to receive notifications for this event, `false` to silence them.
     */
    suspend fun setEventEnabled(eventType: String, enabled: Boolean)

    /**
     * Returns whether notifications for [eventType] are enabled.
     *
     * @return `true` when the key is explicitly enabled **or absent** (default), `false` only
     *   when the user has explicitly opted out.
     */
    suspend fun isEventEnabled(eventType: String): Boolean

    /**
     * Emits the current `event_type -> boolean` preference map.
     *
     * Starts with an empty map and loads the persisted values lazily on first subscription.
     * Re-emits after every successful [setEventEnabled] call so observers stay in sync.
     */
    val prefsFlow: Flow<Map<String, Boolean>>
}
