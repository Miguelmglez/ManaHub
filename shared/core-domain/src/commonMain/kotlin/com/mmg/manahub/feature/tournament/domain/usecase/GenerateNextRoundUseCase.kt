package com.mmg.manahub.feature.tournament.domain.usecase

import com.mmg.manahub.core.model.Tournament
import com.mmg.manahub.core.model.TournamentMatch
import com.mmg.manahub.core.model.TournamentPlayer
import com.mmg.manahub.feature.tournament.domain.engine.SingleEliminationEngine
import com.mmg.manahub.feature.tournament.domain.engine.StandingsCalculator
import com.mmg.manahub.feature.tournament.domain.engine.SwissEngine
import com.mmg.manahub.feature.tournament.domain.engine.TournamentIdCodec

sealed class NextRoundResult {
    object RoundNotComplete : NextRoundResult()
    data class RoundGenerated(val round: Int) : NextRoundResult()
    object TournamentFinished : NextRoundResult()
}

/**
 * Pure outcome of an advancement decision: the [result] summary plus the matches that must be
 * inserted to realise it ([matchesToInsert]) and whether the tournament is now complete
 * ([tournamentFinished]). Returned by [GenerateNextRoundUseCase.plan] so the repository can run the
 * decision and the insert/finish INSIDE one Room transaction (audit C1/C3) without re-querying.
 */
data class AdvancementPlan(
    val result: NextRoundResult,
    val matchesToInsert: List<TournamentMatch> = emptyList(),
    val tournamentFinished: Boolean = false,
)

/**
 * Pure advancement decision engine. No database access, no coroutine dispatch.
 *
 * The [plan] function is the single canonical advancement decision in the codebase (audit C1).
 * The repository calls it inside a Room transaction via [com.mmg.manahub.core.data.local.dao.TournamentDao]
 * `finishMatchAndAdvanceAtomically`, converting domain types back to entities for persistence.
 */
class GenerateNextRoundUseCase {

    /**
     * PURE advancement decision over the post-finish state. Touches no DB; engine pairing generation
     * is itself pure. The repository's atomic finish path calls this so there is exactly ONE
     * advancement decision in the codebase (audit C1).
     *
     * @param players required for SWISS (round count + pairing); may be empty for other structures.
     */
    fun plan(
        tournament: Tournament,
        players:    List<TournamentPlayer>,
        allMatches: List<TournamentMatch>,
    ): AdvancementPlan {
        if (allMatches.isEmpty()) return AdvancementPlan(NextRoundResult.TournamentFinished, tournamentFinished = true)

        // Round-aware (audit H2): advance the LOWEST round that is fully resolved (no PENDING/ACTIVE)
        // and has no successor round yet — never blindly maxOf { round }. A round with a successor was
        // already advanced; a round with stragglers is not complete.
        // Note: toSortedSet() is JVM-only; use distinct().sorted() for KMP (wasmJs) compatibility.
        val roundsSorted = allMatches.map { it.round }.distinct().sorted()
        val roundsSet = roundsSorted.toSet()
        val advanceableRound = roundsSorted.firstOrNull { round ->
            val roundMatches = allMatches.filter { it.round == round }
            val fullyFinished = roundMatches.isNotEmpty() &&
                roundMatches.none { it.status == "PENDING" || it.status == "ACTIVE" }
            val hasSuccessor = (round + 1) in roundsSet
            fullyFinished && !hasSuccessor
        }

        if (advanceableRound == null) {
            // No fully-finished round awaiting a successor → either a round still has pending matches
            // (not complete), or every round already advanced and nothing is pending (finished).
            val anyUnfinished = allMatches.any { it.status == "PENDING" || it.status == "ACTIVE" }
            return if (anyUnfinished) {
                AdvancementPlan(NextRoundResult.RoundNotComplete)
            } else {
                AdvancementPlan(NextRoundResult.TournamentFinished, tournamentFinished = true)
            }
        }

        val currentRound = advanceableRound
        val currentRoundMatches = allMatches.filter { it.round == currentRound }
        val finishedMatches = allMatches.filter { it.status == "FINISHED" }
        // Globally-monotonic scheduledOrder for the next round (audit M1): offset past every existing
        // match so (round, scheduledOrder) ordering — and `getNextPendingMatch` — never tie across
        // rounds. byRound grouping in the UI still holds (it groups by `round`).
        val orderOffset = (allMatches.maxOfOrNull { it.scheduledOrder } ?: -1) + 1

        return when (tournament.structure) {
            "ROUND_ROBIN" -> AdvancementPlan(NextRoundResult.TournamentFinished, tournamentFinished = true)

            "SWISS" -> {
                val maxRounds = SwissEngine.totalRounds(players.size)
                if (currentRound >= maxRounds) {
                    AdvancementPlan(NextRoundResult.TournamentFinished, tournamentFinished = true)
                } else {
                    val standings = StandingsCalculator.calculate(players, finishedMatches)
                    val pairings  = SwissEngine.generateNextRound(standings, finishedMatches)
                    val nextRound = currentRound + 1
                    val matches   = buildMatches(tournament.id, nextRound, pairings, orderOffset)
                    AdvancementPlan(NextRoundResult.RoundGenerated(nextRound), matchesToInsert = matches)
                }
            }

            "SINGLE_ELIM" -> {
                if (SingleEliminationEngine.isFinalRoundComplete(currentRoundMatches)) {
                    AdvancementPlan(NextRoundResult.TournamentFinished, tournamentFinished = true)
                } else {
                    val pairings  = SingleEliminationEngine.generateNextRound(currentRoundMatches)
                    val nextRound = currentRound + 1
                    val matches   = buildMatches(tournament.id, nextRound, pairings, orderOffset)
                    AdvancementPlan(NextRoundResult.RoundGenerated(nextRound), matchesToInsert = matches)
                }
            }

            else -> AdvancementPlan(NextRoundResult.TournamentFinished, tournamentFinished = true)
        }
    }

    private fun buildMatches(
        tournamentId: Long,
        round: Int,
        pairings: List<Pair<Long, Long?>>,
        orderOffset: Int,
    ): List<TournamentMatch> = pairings.mapIndexed { index, (playerA, playerB) ->
        if (playerB == null) {
            // Bye: single-player match, auto-finished. id=0 = "not yet persisted".
            TournamentMatch(
                tournamentId   = tournamentId,
                round          = round,
                playerIds      = TournamentIdCodec.encodeIds(listOf(playerA)),
                winnerId       = playerA,
                status         = "FINISHED",
                scheduledOrder = orderOffset + index,
            )
        } else {
            TournamentMatch(
                tournamentId   = tournamentId,
                round          = round,
                playerIds      = TournamentIdCodec.encodeIds(listOf(playerA, playerB)),
                scheduledOrder = orderOffset + index,
            )
        }
    }
}
