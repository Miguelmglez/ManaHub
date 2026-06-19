package com.mmg.manahub.feature.tournament.domain.engine

import com.mmg.manahub.core.data.local.entity.TournamentMatchEntity
import com.mmg.manahub.core.data.local.entity.projection.TournamentStanding
import kotlin.math.ceil
import kotlin.math.log2

/**
 * Swiss pairing engine.
 *
 * Round count: ceil(log2(playerCount)), minimum 1.
 * Byes: the lowest-ranked player without a prior bye receives one each odd round.
 * Rematches: avoided by swapping within point groups (greedy, not Blossom algorithm).
 */
object SwissEngine {

    fun totalRounds(playerCount: Int): Int {
        if (playerCount <= 1) return 1
        return ceil(log2(playerCount.toDouble())).toInt().coerceAtLeast(1)
    }

    /**
     * Returns pairings for the next Swiss round.
     *
     * Each entry is (playerAId, playerBId?), where null for the second element = bye.
     * The caller is responsible for persisting the results as TournamentMatchEntity rows.
     *
     * @param standings     Current standings sorted by points descending (determines pairing groups).
     * @param finishedMatches All finished matches so far (used to detect prior opponents and byes).
     */
    fun generateNextRound(
        standings: List<TournamentStanding>,
        finishedMatches: List<TournamentMatchEntity>,
    ): List<Pair<Long, Long?>> {
        if (standings.isEmpty()) return emptyList()

        val priorOpponents = buildPriorOpponentsMap(finishedMatches)
        val priorByeRecipients = findByeRecipients(finishedMatches)

        // Work with a mutable ordered list (highest points first)
        val queue = standings.map { it.player }.toMutableList()
        val pairings = mutableListOf<Pair<Long, Long?>>()

        // Assign bye if count is odd
        if (queue.size % 2 != 0) {
            val byeCandidate = queue.lastOrNull { it.id !in priorByeRecipients }
            val byePlayer = byeCandidate ?: queue.last()
            queue.remove(byePlayer)
            pairings.add(byePlayer.id to null)
        }

        // Pair remaining players (greedy, avoid rematches)
        val unpaired = queue.toMutableList()
        while (unpaired.size >= 2) {
            val first = unpaired.removeAt(0)
            val opponentsOfFirst = priorOpponents[first.id] ?: emptySet()

            // Find first available partner who hasn't played first
            val partnerIndex = unpaired.indexOfFirst { it.id !in opponentsOfFirst }
            val partner = if (partnerIndex >= 0) {
                unpaired.removeAt(partnerIndex)
            } else {
                // All remaining have played first — just take next to avoid dead-lock
                unpaired.removeAt(0)
            }
            pairings.add(first.id to partner.id)
        }

        return pairings
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildPriorOpponentsMap(
        finishedMatches: List<TournamentMatchEntity>,
    ): Map<Long, Set<Long>> {
        val map = mutableMapOf<Long, MutableSet<Long>>()
        for (match in finishedMatches) {
            val ids = parseIds(match.playerIds)
            if (ids.size < 2) continue
            for (id in ids) {
                map.getOrPut(id) { mutableSetOf() }.addAll(ids.filter { it != id })
            }
        }
        return map
    }

    private fun findByeRecipients(finishedMatches: List<TournamentMatchEntity>): Set<Long> =
        finishedMatches
            .filter { parseIds(it.playerIds).size == 1 }
            .mapNotNull { parseIds(it.playerIds).firstOrNull() }
            .toSet()

    private fun parseIds(json: String): List<Long> = TournamentIdCodec.decodeIds(json)
}
