package com.mmg.manahub.feature.draft.domain.repository

import com.mmg.manahub.core.domain.model.DataResult
import com.mmg.manahub.feature.draft.domain.model.DraftResult
import com.mmg.manahub.feature.draft.domain.model.DraftState
import com.mmg.manahub.feature.draft.domain.model.DraftableSet
import kotlinx.coroutines.flow.Flow

interface DraftSimRepository {

    /**
     * Assembles the full [DraftableSet] for simulation: card pool + booster config + ratings.
     * Returns [DataResult.Error] if the set is not draftable, offline, or cards are missing.
     * Also pre-warms Coil image cache for the set's cards (respects ≤10 req/s Scryfall limit).
     */
    suspend fun getDraftableSimSet(setCode: String): DataResult<DraftableSet>

    /** Emits the in-progress [DraftState] for the active session, or null if none. */
    fun observeActiveSession(): Flow<DraftState?>

    /** Persists the current [state] (overwrites any existing active session for the same set). */
    suspend fun saveSession(state: DraftState)

    /**
     * Saves the completed deck via [com.mmg.manahub.core.domain.repository.DeckRepository]
     * and marks the session as COMPLETE.
     * @return UUID of the created deck on success.
     */
    suspend fun completeAndSaveDeck(result: DraftResult): DataResult<String>
}
