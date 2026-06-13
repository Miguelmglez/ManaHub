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
            // Includes the gamification watermarks (gam_sync_ms_* / gam_pushed_ledger_id_*): if Room is
            // wiped the local ledger is gone, so a stale "already pushed up to id N" or "synced up to T"
            // watermark must NOT survive — otherwise the next sync would skip re-pulling the account's
            // history and never re-push the (now re-created) local rows.
            val prefixes = listOf("sync_millis_", "gam_sync_ms_", "gam_pushed_ledger_id_")
            val keysToRemove = prefs.asMap().keys
                .filter { key -> prefixes.any { key.name.startsWith(it) } }
            @Suppress("UNCHECKED_CAST")
            keysToRemove.forEach { prefs.remove(it as Preferences.Key<Any>) }
        }
    }

    private fun milliKey(userId: String) = longPreferencesKey("sync_millis_$userId")

    // ── Collection-stats sync gating ─────────────────────────────────────────

    /**
     * Returns the epoch-millis timestamp of the last successful stats sync for
     * [userId]. Returns 0L if stats have never been synced, which causes the
     * [CollectionStatsSyncWorker] to treat it as expired and trigger a full push.
     */
    suspend fun getLastStatsSyncMillis(userId: String): Long =
        context.userPrefsDataStore.data
            .map { prefs -> prefs[statsMilliKey(userId)] ?: 0L }
            .first()

    /**
     * Records [millis] as the last successful stats sync watermark for [userId].
     * Called by [CollectionStatsSyncWorker] after the `upsert_collection_stats`
     * RPC completes without error.
     */
    suspend fun saveLastStatsSyncMillis(userId: String, millis: Long) {
        context.userPrefsDataStore.edit { prefs ->
            prefs[statsMilliKey(userId)] = millis
        }
    }

    /**
     * Clears the stats sync watermark for [userId], forcing a full push on the
     * next worker run. Called when the user signs out or when the database is
     * wiped via destructive migration.
     */
    suspend fun clearLastStatsSyncMillis(userId: String) {
        context.userPrefsDataStore.edit { prefs ->
            prefs.remove(statsMilliKey(userId))
        }
    }

    private fun statsMilliKey(userId: String) = longPreferencesKey("stats_sync_ms_$userId")

    // ── Gamification sync (Phase 4) ──────────────────────────────────────────

    /**
     * Returns the gamification PULL watermark for [userId] as epoch millis. Returns 0L if
     * gamification has never synced, forcing a full pull (and the `id`-based push covers all local
     * ledger rows). Used by
     * [com.mmg.manahub.core.gamification.data.sync.GamificationSyncManager].
     */
    suspend fun getGamificationSyncMillis(userId: String): Long =
        context.userPrefsDataStore.data
            .map { prefs -> prefs[gamSyncMilliKey(userId)] ?: 0L }
            .first()

    /** Records [millis] as the gamification PULL watermark for [userId]. */
    suspend fun saveGamificationSyncMillis(userId: String, millis: Long) {
        context.userPrefsDataStore.edit { prefs ->
            prefs[gamSyncMilliKey(userId)] = millis
        }
    }

    /**
     * Returns the highest local XP-ledger autoincrement `id` already pushed for [userId].
     * Returns 0L if nothing has been pushed (so the first push sends every local row, since ledger
     * ids start at 1). This is the strictly-increasing PUSH watermark — never an epoch-millis value.
     */
    suspend fun getGamificationPushedLedgerId(userId: String): Long =
        context.userPrefsDataStore.data
            .map { prefs -> prefs[gamPushedLedgerIdKey(userId)] ?: 0L }
            .first()

    /** Records [ledgerId] as the highest local XP-ledger `id` already pushed for [userId]. */
    suspend fun saveGamificationPushedLedgerId(userId: String, ledgerId: Long) {
        context.userPrefsDataStore.edit { prefs ->
            prefs[gamPushedLedgerIdKey(userId)] = ledgerId
        }
    }

    /**
     * Clears BOTH gamification watermarks for [userId], forcing a full push + pull on the next sync.
     *
     * Called by
     * [com.mmg.manahub.core.gamification.data.sync.GamificationSyncManager.reconcileOnSignIn] so an
     * anonymous/guest's local progress merges INTO the account: the push re-sends every local ledger
     * row and the pull re-fetches the account's full history. Monotonic server-side merges
     * (GREATEST/earliest/union) make this idempotent — no XP is double-counted (ledger UNIQUE key) and
     * no state is lost.
     */
    suspend fun clearGamificationWatermarks(userId: String) {
        context.userPrefsDataStore.edit { prefs ->
            prefs.remove(gamSyncMilliKey(userId))
            prefs.remove(gamPushedLedgerIdKey(userId))
        }
    }

    private fun gamSyncMilliKey(userId: String) = longPreferencesKey("gam_sync_ms_$userId")

    private fun gamPushedLedgerIdKey(userId: String) =
        longPreferencesKey("gam_pushed_ledger_id_$userId")
}
