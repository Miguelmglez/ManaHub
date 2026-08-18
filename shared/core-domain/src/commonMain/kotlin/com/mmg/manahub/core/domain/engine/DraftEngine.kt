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
     * Records the human's picks for [scryfallIds] (one call = one turn's worth of picks, i.e.
     * `1..config.picksPerTurn` ids — see [DraftState.picksTakenInTurn]). Once the human's picks
     * for this turn are complete (either `config.picksPerTurn` cards were taken, or the pack ran
     * out first), every other seat's bot takes the SAME number of cards this call took — so every
     * seat's pack shrinks in lockstep — before packs rotate (LEFT for odd rounds, RIGHT for even)
     * and pickNumber/round advance. If the turn is not yet complete (a partial-list call), only
     * the human's pack/pool are updated and [DraftState.picksTakenInTurn] increments — no bot
     * picks, no rotation, until a later call completes the turn.
     *
     * @param engine The set's archetype decision engine, or null when the set has none (bots then
     *   fall back to the heuristic drafter). The same [engine] is passed into every bot pick.
     */
    fun applyHumanPick(state: DraftState, scryfallIds: List<String>, engine: EngineConfig?): DraftState

    /**
     * Auto-picks for the human seat using the archetype-aware drafter, then delegates to
     * [applyHumanPick]. Deterministic — no randomness.
     *
     * @param engine The set's archetype decision engine, or null (heuristic fallback).
     */
    fun autoPick(state: DraftState, engine: EngineConfig?): DraftState

    fun isComplete(state: DraftState): Boolean
}
