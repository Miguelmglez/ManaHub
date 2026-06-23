package com.mmg.manahub.core.domain.engine

import com.mmg.manahub.core.model.DraftConfig
import com.mmg.manahub.core.model.DraftState
import com.mmg.manahub.core.model.DraftableSet
import com.mmg.manahub.core.model.EngineConfig

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
     *
     * @param engine The set's archetype decision engine, or null when the set has none (bots then
     *   fall back to the heuristic drafter). The same [engine] is passed into every bot pick.
     */
    fun applyHumanPick(state: DraftState, scryfallId: String, engine: EngineConfig?): DraftState

    /**
     * Auto-picks for the human seat using the archetype-aware drafter, then delegates to
     * [applyHumanPick]. Deterministic — no randomness.
     *
     * @param engine The set's archetype decision engine, or null (heuristic fallback).
     */
    fun autoPick(state: DraftState, engine: EngineConfig?): DraftState

    fun isComplete(state: DraftState): Boolean
}
