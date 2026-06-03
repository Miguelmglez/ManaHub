package com.mmg.manahub.feature.draft.domain.engine

import com.mmg.manahub.feature.draft.domain.model.DraftConfig
import com.mmg.manahub.feature.draft.domain.model.DraftState
import com.mmg.manahub.feature.draft.domain.model.DraftableSet

/**
 * Pure, synchronous draft state machine. No IO; all operations return a new [DraftState].
 * Callers are responsible for running this on [kotlinx.coroutines.Dispatchers.Default].
 *
 * DRAFT: distributes packs, runs one full pick cycle (human + all bots) per [applyHumanPick].
 * SEALED: in [start], immediately allocates all 6 packs to the human seat → status = BUILDING.
 */
interface DraftEngine {
    fun start(set: DraftableSet, config: DraftConfig): DraftState

    /**
     * Records the human's pick for [scryfallId], runs bot picks for all other seats,
     * rotates packs (LEFT for odd rounds, RIGHT for even), and advances pickNumber/round.
     */
    fun applyHumanPick(state: DraftState, scryfallId: String): DraftState

    /**
     * Auto-picks the card with the lowest [DraftCard.pickOrderRank] from the human's current
     * pack, then delegates to [applyHumanPick]. Deterministic — no randomness.
     */
    fun autoPick(state: DraftState): DraftState

    fun isComplete(state: DraftState): Boolean
}
