package com.mmg.manahub.core.data.local

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Dedicated DataStore accessor for the sync watermark (epoch millis per userId).
 *
 * Kept separate from [UserPreferencesDataStore] to avoid coupling the general
 * preferences class to sync internals. Both share the same underlying DataStore
 * file (`user_prefs`) via the [Context.userPrefsDataStore] extension property.
 *
 * Key format: `"sync_millis_<userId>"` — distinct from the legacy
 * `"sync_ts_<userId>"` ISO-string keys used by the old sync engine,
 * so both can coexist during the migration window.
 */
@Singleton
class SyncPreferencesStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /**
     * Returns the last successful sync timestamp for [userId] as epoch millis.
     * Returns 0L if no sync has ever completed (forces a full pull on the first cycle).
     */
    suspend fun getLastSyncMillis(userId: String): Long =
        context.userPrefsDataStore.data
            .map { prefs -> prefs[milliKey(userId)] ?: 0L }
            .first()

    /**
     * Saves [millis] as the last successful sync watermark for [userId].
     * Called by [com.mmg.manahub.core.sync.SyncManager] after each successful cycle.
     */
    suspend fun saveLastSyncMillis(userId: String, millis: Long) {
        context.userPrefsDataStore.edit { prefs ->
            prefs[milliKey(userId)] = millis
        }
    }

    /**
     * Clears the sync watermark for [userId], forcing a full push on the next sync.
     * Used by [com.mmg.manahub.core.sync.SyncManager.assignUserIdAndSync] during
     * the offline-to-online transition.
     */
    suspend fun clearLastSyncMillis(userId: String) {
        context.userPrefsDataStore.edit { prefs ->
            prefs.remove(milliKey(userId))
        }
    }

    /**
     * Clears ALL sync watermarks across every user ID. Called by the Room
     * [onDestructiveMigration] callback when Room wipes the database so that the
     * DataStore watermark (which survives Room wipes) cannot cause a stale-watermark
     * pull (where getChangesSince returns 0 rows because all Supabase data pre-dates
     * the watermark from the previous install).
     */
    suspend fun clearAllWatermarks() {
        context.userPrefsDataStore.edit { prefs ->
            val keysToRemove = prefs.asMap().keys
                .filter { it.name.startsWith("sync_millis_") }
            @Suppress("UNCHECKED_CAST")
            keysToRemove.forEach { prefs.remove(it as Preferences.Key<Any>) }
        }
    }

    private fun milliKey(userId: String) = longPreferencesKey("sync_millis_$userId")
}
