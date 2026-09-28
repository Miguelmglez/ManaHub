package com.mmg.manahub.feature.game.presentation

import com.mmg.manahub.feature.game.domain.model.GameResult
import com.mmg.manahub.feature.game.domain.model.Player
import com.mmg.manahub.feature.game.domain.model.PlayerResult

/**
 * Final standings order: the winner first, then survivors before eliminated players, each group
 * by descending final life.
 *
 * Ranking by life alone let a seat eliminated by poison at 18 life outrank a survivor at 5.
 */
internal fun orderedStandings(gameResult: GameResult): List<Player> =
    listOf(gameResult.winner) +
        gameResult.playerResults
            .filter { it.player.id != gameResult.winner.id }
            .sortedWith(
                compareByDescending<PlayerResult> { it.eliminationReason == null }
                    .thenByDescending { it.finalLife }
            )
            .map { it.player }

/**
 * The winner's life margin over the best rival who was still standing, or null when everyone else
 * was eliminated (there is no "close game" to report against a dead player).
 *
 * The previous version subtracted the two LOWEST life totals in the game, which described two
 * losers' totals rather than how close the winner came to losing.
 */
internal fun closestGap(gameResult: GameResult): Int? {
    val runnerUp = gameResult.playerResults
        .filter { it.player.id != gameResult.winner.id && it.eliminationReason == null }
        .maxByOrNull { it.finalLife }
        ?: return null
    val winnerResult = gameResult.playerResults
        .firstOrNull { it.player.id == gameResult.winner.id }
        ?: return null
    return winnerResult.finalLife - runnerUp.finalLife
}
