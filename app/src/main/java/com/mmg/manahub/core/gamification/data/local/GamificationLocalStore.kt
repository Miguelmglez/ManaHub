package com.mmg.manahub.core.gamification.data.local

import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.data.local.dao.GamificationDao

/**
 * The single routine that forgets all local gamification state: the six Room tables (one
 * transaction) plus every account-scoped DataStore key, owner included.
 *
 * Used on an account switch, on successful account deletion, and (DataStore half only) by the Room
 * destructive-migration callback.
 */
class GamificationLocalStore(
    private val dao: GamificationDao,
    private val userPreferencesDataStore: UserPreferencesDataStore,
) {

    /** Wipes Room first so a crash in between leaves stale prefs, never stale progress under a new owner. */
    suspend fun wipe() {
        dao.wipeAll()
        userPreferencesDataStore.clearGamificationLocalState()
    }
}
