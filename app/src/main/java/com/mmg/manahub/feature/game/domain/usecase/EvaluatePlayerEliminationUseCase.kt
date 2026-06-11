package com.mmg.manahub.feature.game.domain.usecase

import com.mmg.manahub.feature.game.domain.model.GameMode
import com.mmg.manahub.feature.game.domain.model.Player
import javax.inject.Inject

/**
 * Determines whether a [Player] meets any defeat condition for the given [GameMode].
 *
 * Defeat conditions:
 * - Life total reaches 0 or below (all modes).
 * - Poison counters reach 10 or above (all modes).
 * - A single commander has dealt 21 or more combat damage to the player (Commander only).
 *
 * This use case is intentionally free of Android framework imports so it can be
 * tested as a plain Kotlin unit test without Robolectric or an instrumented runner.
 *
 * @return `true` if the player should be prompted for defeat confirmation.
 */
class EvaluatePlayerEliminationUseCase @Inject constructor() {

    /**
     * Evaluates defeat conditions for [player] under the given [mode].
     *
     * @param player The [Player] whose state is being evaluated.
     * @param mode   The active [GameMode] (affects whether commander-damage applies).
     * @return `true` if at least one defeat condition is met.
     */
    operator fun invoke(player: Player, mode: GameMode): Boolean {
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
