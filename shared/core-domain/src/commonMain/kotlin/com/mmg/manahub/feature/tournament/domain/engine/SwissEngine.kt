package com.mmg.manahub.feature.tournament.domain.engine

import com.mmg.manahub.core.model.TournamentMatch
import com.mmg.manahub.core.model.TournamentStanding
import kotlin.math.ceil
import kotlin.math.log2

/**
 * Swiss pairing engine.
 *
 * Round count: ceil(log2(playerCount)), minimum 1.
 * Byes: the lowest-ranked player without a prior bye receives one each odd round.
 * Rematches: avoided by swapping within point groups (greedy, not Blossom algorithm).
 */
/**
 * Outcome of one Swiss pairing pass.
 *
 * @property pairings player-id pairs (second is null for a bye).
 * @property forcedRematches pairings that had to repeat a previous matchup because every remaining
 *   opponent had already been played.
 */
data class SwissPairingResult(
    val pairings: List<Pair<Long, Long?>>,
    val forcedRematches: Int,
)

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
        finishedMatches: List<TournamentMatch>,
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
                // All remaining have played first — take the next to avoid a dead-lock, and COUNT it:
                // a silent rematch is indistinguishable from correct pairing when standings look odd
                forcedRematches++
                unpaired.removeAt(0)
            }
            pairings.add(first.id to partner.id)
        }

        return pairings
    }

    /**
     * Pairs the next round and reports how many pairings had to repeat a previous matchup.
     *
     * A forced rematch is legal (it beats dead-locking) but it is a real quality signal, so the
     * caller can log it rather than leaving players wondering why they met twice.
     */
    fun generateNextRoundWithDiagnostics(
        standings: List<TournamentStanding>,
        finishedMatches: List<TournamentMatch>,
    ): SwissPairingResult {
        forcedRematches = 0
        val pairings = generateNextRound(standings, finishedMatches)
        return SwissPairingResult(pairings = pairings, forcedRematches = forcedRematches)
    }

    /** Rematches forced during the most recent pairing pass; reset by [generateNextRoundWithDiagnostics]. */
    private var forcedRematches: Int = 0

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildPriorOpponentsMap(
        finishedMatches: List<TournamentMatch>,
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

    private fun findByeRecipients(finishedMatches: List<TournamentMatch>): Set<Long> =
        finishedMatches
            .filter { parseIds(it.playerIds).size == 1 }
            .mapNotNull { parseIds(it.playerIds).firstOrNull() }
            .toSet()

    private fun parseIds(json: String): List<Long> = TournamentIdCodec.decodeIds(json)
}
