package com.mmg.manahub.feature.tournament.domain.engine

import com.mmg.manahub.core.data.local.entity.TournamentMatchEntity
import com.mmg.manahub.core.data.local.entity.TournamentPlayerEntity
import com.mmg.manahub.core.data.local.entity.projection.TournamentStanding
import kotlin.math.max

/**
 * Computes DCI-standard tournament standings.
 *
 * Sorting criteria (descending):
 *   1. Points  (win=3, draw=1, bye=3)
 *   2. OMW%    (Opponent Match Win %, floor 33%, bye opponents excluded)
 *   3. GW%     (Game Win %, floor 33%)
 *   4. OGW%    (Opponent Game Win %, floor 33%)
 *
 * Life totals are retained in the model for display but NOT used for sorting.
 * A draw is a finished match with winnerId == null (and status == "FINISHED").
 * A bye is a finished match with a single player in playerIds.
 */
object StandingsCalculator {

    private const val OMW_FLOOR = 0.33

    fun calculate(
        players: List<TournamentPlayerEntity>,
        finishedMatches: List<TournamentMatchEntity>,
    ): List<TournamentStanding> {
        val matchWinRate = computeMatchWinRates(players, finishedMatches)
        val gameWinRate  = computeGameWinRates(players, finishedMatches)

        val standings = players.map { player ->
            var wins      = 0
            var losses    = 0
            var draws     = 0
            var lifeTotal = 0

            for (match in finishedMatches) {
                val ids = parseIds(match.playerIds)
                if (player.id !in ids) continue

                val isBye = ids.size == 1
                if (isBye) {
                    wins++
                    continue
                }

                lifeTotal += parseLifeTotals(match.finalLifeTotals)[player.id] ?: 0
                when {
                    match.winnerId == player.id -> wins++
                    match.winnerId == null       -> draws++
                    else                         -> losses++
                }
            }

            val omwPercent  = computeOmwPercent(player.id, finishedMatches, matchWinRate)
            val gwPercent   = max(gameWinRate[player.id] ?: 0.0, OMW_FLOOR)
            val ogwPercent  = computeOgwPercent(player.id, finishedMatches, gameWinRate)

            TournamentStanding(
                player        = player,
                wins          = wins,
                losses        = losses,
                draws         = draws,
                points        = wins * 3 + draws,
                lifeTotal     = lifeTotal,
                matchesPlayed = wins + losses + draws,
                position      = 0,
                omwPercent    = omwPercent,
                gwPercent     = gwPercent,
                ogwPercent    = ogwPercent,
            )
        }

        return standings
            .sortedWith(
                compareByDescending<TournamentStanding> { it.points }
                    .thenByDescending { it.omwPercent }
                    .thenByDescending { it.gwPercent }
                    .thenByDescending { it.ogwPercent }
            )
            .mapIndexed { index, standing -> standing.copy(position = index + 1) }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun computeMatchWinRates(
        players: List<TournamentPlayerEntity>,
        finishedMatches: List<TournamentMatchEntity>,
    ): Map<Long, Double> = players.associate { player ->
        var wins   = 0
        var played = 0
        for (match in finishedMatches) {
            val ids = parseIds(match.playerIds)
            if (player.id !in ids) continue
            if (ids.size == 1) continue // bye: excluded from MWR calculation
            played++
            when {
                match.winnerId == player.id -> wins++
                match.winnerId == null       -> { /* draw contributes 0.5 but for simplicity use 0 wins */ }
            }
        }
        player.id to if (played == 0) OMW_FLOOR else max(wins.toDouble() / played, OMW_FLOOR)
    }

    private fun computeGameWinRates(
        players: List<TournamentPlayerEntity>,
        finishedMatches: List<TournamentMatchEntity>,
    ): Map<Long, Double> {
        // Phase 1: single-game matches only. GW% == MW% for best-of-1 with no draws = same logic.
        // Phase 2 will track per-game scores once tournament_match_players is added.
        return computeMatchWinRates(players, finishedMatches)
    }

    private fun computeOmwPercent(
        playerId: Long,
        finishedMatches: List<TournamentMatchEntity>,
        matchWinRate: Map<Long, Double>,
    ): Double {
        val opponentIds = finishedMatches
            .filter { match ->
                val ids = parseIds(match.playerIds)
                playerId in ids && ids.size > 1  // exclude byes
            }
            .flatMap { parseIds(it.playerIds) }
            .filter { it != playerId }
            .distinct()

        if (opponentIds.isEmpty()) return OMW_FLOOR
        val avg = opponentIds.sumOf { matchWinRate[it] ?: OMW_FLOOR } / opponentIds.size
        return max(avg, OMW_FLOOR)
    }

    private fun computeOgwPercent(
        playerId: Long,
        finishedMatches: List<TournamentMatchEntity>,
        gameWinRate: Map<Long, Double>,
    ): Double {
        val opponentIds = finishedMatches
            .filter { match ->
                val ids = parseIds(match.playerIds)
                playerId in ids && ids.size > 1
            }
            .flatMap { parseIds(it.playerIds) }
            .filter { it != playerId }
            .distinct()

        if (opponentIds.isEmpty()) return OMW_FLOOR
        val avg = opponentIds.sumOf { gameWinRate[it] ?: OMW_FLOOR } / opponentIds.size
        return max(avg, OMW_FLOOR)
    }

    internal fun parseIds(json: String): List<Long> =
        json.trim('[', ']').split(",").mapNotNull { it.trim().toLongOrNull() }

    internal fun parseLifeTotals(json: String): Map<Long, Int> {
        if (json.isBlank()) return emptyMap()
        return json.trim('{', '}').split(",").mapNotNull { entry ->
            val colonIdx = entry.indexOf(':')
            if (colonIdx == -1) return@mapNotNull null
            val id   = entry.substring(0, colonIdx).trim().toLongOrNull()
            val life = entry.substring(colonIdx + 1).trim().toIntOrNull()
            if (id != null && life != null) id to life else null
        }.toMap()
    }
}
