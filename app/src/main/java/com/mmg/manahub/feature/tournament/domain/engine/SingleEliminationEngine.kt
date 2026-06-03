package com.mmg.manahub.feature.tournament.domain.engine

import com.mmg.manahub.core.data.local.entity.TournamentMatchEntity
import kotlin.math.ceil
import kotlin.math.log2

/**
 * Single-elimination bracket engine.
 *
 * First round: seeds to the nearest power of 2 with byes for lower-seeded players.
 * Subsequent rounds: winners of previous round paired in bracket order (scheduledOrder).
 */
object SingleEliminationEngine {

    /**
     * Returns round-1 pairings. Players beyond the nearest power of 2 get byes
     * (highest seeds get the byes — they advance automatically).
     *
     * Returns (playerA, playerB?) where null = bye for playerA.
     */
    fun generateFirstRound(players: List<Long>): List<Pair<Long, Long?>> {
        if (players.isEmpty()) return emptyList()
        if (players.size == 1) return listOf(players[0] to null)

        val nextPow2 = nextPowerOf2(players.size)
        val byeCount = nextPow2 - players.size
        val pairings = mutableListOf<Pair<Long, Long?>>()

        // Top seeds (indices 0..byeCount-1) get byes; rest play round 1
        var byesAssigned = 0
        val playing = mutableListOf<Long>()
        for ((index, player) in players.withIndex()) {
            if (byesAssigned < byeCount) {
                pairings.add(player to null)
                byesAssigned++
            } else {
                playing.add(player)
            }
        }

        // Pair remaining players
        var order = pairings.size
        var i = 0
        while (i + 1 < playing.size) {
            pairings.add(playing[i] to playing[i + 1])
            i += 2
        }
        if (i < playing.size) {
            // Odd leftover after byes — should not happen if byeCount is correct, but guard anyway
            pairings.add(playing[i] to null)
        }

        return pairings
    }

    /**
     * Generates pairings for the next bracket round from the winners (and bye recipients)
     * of [previousRoundMatches], preserving bracket position (scheduledOrder).
     */
    fun generateNextRound(
        previousRoundMatches: List<TournamentMatchEntity>,
    ): List<Pair<Long, Long?>> {
        // Collect winners in bracket order
        val winners = previousRoundMatches
            .sortedBy { it.scheduledOrder }
            .mapNotNull { match ->
                val ids = parseIds(match.playerIds)
                when {
                    ids.size == 1 -> ids[0]          // bye: solo player advances
                    match.winnerId != null -> match.winnerId
                    else -> null                     // draw or unfinished — skip
                }
            }

        if (winners.size < 2) return emptyList()

        val pairings = mutableListOf<Pair<Long, Long?>>()
        var i = 0
        while (i + 1 < winners.size) {
            pairings.add(winners[i] to winners[i + 1])
            i += 2
        }
        if (i < winners.size) {
            pairings.add(winners[i] to null)
        }
        return pairings
    }

    /**
     * True when a single champion can be determined from the last round's results.
     */
    fun isFinalRoundComplete(lastRoundMatches: List<TournamentMatchEntity>): Boolean {
        val winners = lastRoundMatches.mapNotNull { match ->
            val ids = parseIds(match.playerIds)
            when {
                ids.size == 1 -> ids[0]
                match.winnerId != null -> match.winnerId
                else -> null
            }
        }
        return winners.size == 1
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun nextPowerOf2(n: Int): Int {
        var pow = 1
        while (pow < n) pow = pow shl 1
        return pow
    }

    private fun parseIds(json: String): List<Long> =
        json.trim('[', ']').split(",").mapNotNull { it.trim().toLongOrNull() }
}
