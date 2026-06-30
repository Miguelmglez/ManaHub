package com.mmg.manahub.feature.game.domain.model

/**
 * Minimal game-state contract for a player during an active session.
 *
 * Contains only the primitive counters needed by pure domain logic (defeat-condition evaluation,
 * scoring). UI-specific concerns such as theme colours and display names are kept in the concrete
 * [com.mmg.manahub.feature.game.domain.model.Player] class in `:shared:core-ui`.
 *
 * [Player] implements this interface so callers can pass a [Player] anywhere a [PlayerState] is
 * expected without modification.
 */
interface PlayerState {

    /** Current life total. Defeat when this reaches 0 or below. */
    val life: Int

    /** Poison counter. Defeat at [com.mmg.manahub.feature.game.domain.usecase.EvaluatePlayerEliminationUseCase.POISON_THRESHOLD]. */
    val poison: Int

    /**
     * Map from source commander id → cumulative combat damage dealt to this player by that commander.
     * Relevant only in Commander mode; defeat at
     * [com.mmg.manahub.feature.game.domain.usecase.EvaluatePlayerEliminationUseCase.COMMANDER_DAMAGE_THRESHOLD].
     */
    val commanderDamage: Map<Int, Int>
}
