package com.mmg.manahub.core.gamification.data.sync

import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.gamification.data.local.GamificationLocalStore
import com.mmg.manahub.core.gamification.domain.GamificationAccountScope
import com.mmg.manahub.core.gamification.domain.GamificationAccountScopeResult

/**
 * Keeps the local gamification store bound to one account via the persisted owner id (D3 / G-02).
 *
 * - Verified guest store: claim it, then merge it into the account with [GamificationSyncManager.reconcileOnSignIn].
 * - Owner null without guest proof: quarantine legacy rows, then pull only the account's data.
 * - Owner is the same account: a normal [GamificationSyncManager.sync].
 * - Owner is another account: wipe, re-own, then a full pull (the wipe cleared the watermarks).
 *
 * Signing out never wipes; the next account decides what happens to the store.
 */
class GamificationAccountScopeImpl(
    private val userPreferencesDataStore: UserPreferencesDataStore,
    private val localStore: GamificationLocalStore,
    private val syncManager: GamificationSyncManager,
) : GamificationAccountScope {

    override suspend fun onGuestActive(): Boolean {
        if (userPreferencesDataStore.getGamificationOwnerUserId() != null) return false
        if (userPreferencesDataStore.isGamificationVerifiedGuest()) return false
        localStore.wipe()
        userPreferencesDataStore.markGamificationVerifiedGuest()
        return true
    }

    override suspend fun onSignedIn(userId: String): GamificationAccountScopeResult {
        val owner = userPreferencesDataStore.getGamificationOwnerUserId()
        return when (owner) {
            null -> {
                if (userPreferencesDataStore.isGamificationVerifiedGuest()) {
                    userPreferencesDataStore.setGamificationOwnerUserId(userId)
                    syncManager.reconcileOnSignIn(userId)
                    GamificationAccountScopeResult.CLAIMED
                } else {
                    localStore.wipe()
                    userPreferencesDataStore.setGamificationOwnerUserId(userId)
                    syncManager.sync(userId)
                    GamificationAccountScopeResult.SWITCHED
                }
            }
            userId -> {
                syncManager.sync(userId)
                GamificationAccountScopeResult.SYNCED
            }
            else -> {
                localStore.wipe()
                userPreferencesDataStore.setGamificationOwnerUserId(userId)
                syncManager.sync(userId)
                GamificationAccountScopeResult.SWITCHED
            }
        }
    }
}
