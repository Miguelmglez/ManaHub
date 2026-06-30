package com.mmg.manahub.feature.game.domain.usecase

import com.mmg.manahub.feature.game.domain.model.GameMode
import com.mmg.manahub.feature.game.domain.model.PlayerState

/**
 * Determines whether a [PlayerState] meets any defeat condition for the given [GameMode].
 *
 * Defeat conditions:
 * - Life total reaches 0 or below (all modes).
 * - Poison counters reach 10 or above (all modes).
 * - A single commander has dealt 21 or more combat damage to the player (Commander only).
 *
 * This use case is platform-agnostic (no Android framework imports) and lives in `commonMain`
 * so it can be tested as a plain Kotlin unit test without Robolectric or an instrumented runner.
 * [com.mmg.manahub.feature.game.domain.model.Player] implements [PlayerState], so callers can
 * pass a [Player] directly without modification.
 *
 * @return `true` if the player should be prompted for defeat confirmation.
 */
class EvaluatePlayerEliminationUseCase {

    /**
     * Evaluates defeat conditions for [player] under the given [mode].
     *
     * @param player The [PlayerState] whose state is being evaluated.
     * @param mode   The active [GameMode] (affects whether commander-damage applies).
     * @return `true` if at least one defeat condition is met.
     */
    operator fun invoke(player: PlayerState, mode: GameMode): Boolean {
        if (player.life <= 0) return true
        if (player.poison >= POISON_THRESHOLD) return true
        if (mode == GameMode.COMMANDER && player.commanderDamage.values.any { it >= COMMANDER_DAMAGE_THRESHOLD }) return true
        return false
    }

    companion object {
        /** Poison counter threshold that triggers defeat. */
        const val POISON_THRESHOLD = 10

        /** Commander damage threshold from a single source that triggers defeat. */
        const val COMMANDER_DAMAGE_THRESHOLD = 21
    }
}
