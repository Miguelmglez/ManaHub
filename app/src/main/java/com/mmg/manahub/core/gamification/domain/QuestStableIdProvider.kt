package com.mmg.manahub.core.gamification.domain

import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.feature.auth.domain.repository.AuthRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves the stable id used to seed deterministic quest generation (ADR-002 §9).
 *
 * - Signed-in: the authenticated user id, so the SAME quests are generated on every device the user
 *   owns for a given period — with zero sync coordination.
 * - Guest: a random per-install device id persisted once in DataStore (NOT `ANDROID_ID`).
 *
 * Anonymous Supabase users have a non-null id; treating them as "signed in" is fine — their quests are
 * still purely local (quests are never synced).
 */
@Singleton
class QuestStableIdProvider @Inject constructor(
    private val authRepository: AuthRepository,
    private val dataStore: UserPreferencesDataStore,
) {

    /** The user id if signed in, otherwise the persisted guest device id. */
    suspend fun stableId(): String =
        authRepository.getCurrentUser()?.id ?: dataStore.getOrCreateGamificationDeviceId()
}
