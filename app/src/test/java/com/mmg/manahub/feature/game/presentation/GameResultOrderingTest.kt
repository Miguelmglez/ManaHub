package com.mmg.manahub.feature.game.presentation

import com.mmg.manahub.core.ui.theme.PlayerTheme
import com.mmg.manahub.feature.game.domain.model.EliminationReason
import com.mmg.manahub.feature.game.domain.model.GameMode
import com.mmg.manahub.feature.game.domain.model.GameResult
import com.mmg.manahub.feature.game.domain.model.Player
import com.mmg.manahub.feature.game.domain.model.PlayerResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for the game-result standings order and the "closest game" highlight.
 *
 * GROUP 1 — standings: survivors outrank eliminated players regardless of life total
 * GROUP 2 — closest gap: the winner's margin over the best SURVIVING rival
 */
class GameResultOrderingTest {

    private fun player(id: Int, life: Int) = Player(
        id = id,
        name = "P$id",
        life = life,
        theme = PlayerTheme.ALL[id % PlayerTheme.ALL.size],
    )

    private fun result(player: Player, reason: EliminationReason? = null) = PlayerResult(
        player = player,
        finalLife = player.life,
        finalPoison = 0,
        totalCommanderDamageDealt = 0,
        totalCommanderDamageReceived = 0,
        eliminationReason = reason,
    )

    private fun gameResult(winner: Player, results: List<PlayerResult>) = GameResult(
        winner = winner,
        allPlayers = results.map { it.player },
        gameMode = GameMode.COMMANDER,
        totalTurns = 8,
        durationMs = 60_000L,
        playerResults = results,
    )

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 1 — standings order
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given a poisoned-out player on high life then a lower-life survivor still outranks them`() {
        val winner = player(0, 20)
        val poisoned = player(1, 18)
        val survivor = player(2, 5)
        val gr = gameResult(
            winner,
            listOf(
                result(winner),
                result(poisoned, EliminationReason.POISON),
                result(survivor),
            ),
        )

        val order = orderedStandings(gr).map { it.id }

        assertEquals(listOf(0, 2, 1), order)
    }

    @Test
    fun `given only survivors then they are ordered by descending life`() {
        val winner = player(0, 20)
        val a = player(1, 3)
        val b = player(2, 14)
        val gr = gameResult(winner, listOf(result(winner), result(a), result(b)))

        assertEquals(listOf(0, 2, 1), orderedStandings(gr).map { it.id })
    }

    @Test
    fun `given several eliminated players then they keep descending life order among themselves`() {
        val winner = player(0, 11)
        val deadHigh = player(1, 0)
        val deadLow = player(2, -6)
        val gr = gameResult(
            winner,
            listOf(
                result(winner),
                result(deadLow, EliminationReason.LIFE),
                result(deadHigh, EliminationReason.LIFE),
            ),
        )

        assertEquals(listOf(0, 1, 2), orderedStandings(gr).map { it.id })
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 2 — closest gap
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given a surviving runner-up then the gap is the winner's margin over them`() {
        val winner = player(0, 7)
        val survivor = player(1, 4)
        val dead = player(2, -3)
        val gr = gameResult(
            winner,
            listOf(result(winner), result(survivor), result(dead, EliminationReason.LIFE)),
        )

        // The old implementation subtracted the two LOWEST totals (4 - (-3) = 7)
        assertEquals(3, closestGap(gr))
    }

    @Test
    fun `given every rival eliminated then there is no closest-game highlight`() {
        val winner = player(0, 20)
        val dead = player(1, 0)
        val gr = gameResult(winner, listOf(result(winner), result(dead, EliminationReason.LIFE)))

        assertNull(closestGap(gr))
    }

    @Test
    fun `given the winner has less life than a survivor then the gap is negative`() {
        // Possible when the winner survives on poison/commander-damage grounds
        val winner = player(0, 2)
        val survivor = player(1, 9)
        val gr = gameResult(winner, listOf(result(winner), result(survivor)))

        assertEquals(-7, closestGap(gr))
    }
}
