package com.mmg.manahub.feature.tournament.domain.usecase

import com.mmg.manahub.core.data.local.dao.TournamentDao
import com.mmg.manahub.core.data.local.entity.TournamentMatchEntity
import com.mmg.manahub.core.di.IoDispatcher
import com.mmg.manahub.feature.tournament.domain.engine.SingleEliminationEngine
import com.mmg.manahub.feature.tournament.domain.engine.StandingsCalculator
import com.mmg.manahub.feature.tournament.domain.engine.SwissEngine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

sealed class NextRoundResult {
    object RoundNotComplete : NextRoundResult()
    data class RoundGenerated(val round: Int) : NextRoundResult()
    object TournamentFinished : NextRoundResult()
}

@Singleton
class GenerateNextRoundUseCase @Inject constructor(
    private val dao: TournamentDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    suspend operator fun invoke(tournamentId: Long): NextRoundResult = withContext(ioDispatcher) {
        val tournament = dao.getTournamentById(tournamentId) ?: return@withContext NextRoundResult.TournamentFinished
        val allMatches = dao.getAllMatches(tournamentId)

        if (allMatches.isEmpty()) return@withContext NextRoundResult.TournamentFinished

        val currentRound = allMatches.maxOf { it.round }
        val currentRoundMatches = allMatches.filter { it.round == currentRound }

        // If any match in current round is still PENDING or ACTIVE, round isn't done yet
        if (currentRoundMatches.any { it.status == "PENDING" || it.status == "ACTIVE" }) {
            return@withContext NextRoundResult.RoundNotComplete
        }

        val finishedMatches = allMatches.filter { it.status == "FINISHED" }

        return@withContext when (tournament.structure) {
            "ROUND_ROBIN" -> NextRoundResult.TournamentFinished

            "SWISS" -> {
                val players   = dao.getPlayers(tournamentId)
                val maxRounds = SwissEngine.totalRounds(players.size)

                if (currentRound >= maxRounds) {
                    NextRoundResult.TournamentFinished
                } else {
                    val standings = StandingsCalculator.calculate(players, finishedMatches)
                    val pairings  = SwissEngine.generateNextRound(standings, finishedMatches)
                    val nextRound = currentRound + 1
                    val matches   = buildMatches(tournamentId, nextRound, pairings)
                    dao.insertMatches(matches)
                    NextRoundResult.RoundGenerated(nextRound)
                }
            }

            "SINGLE_ELIM" -> {
                val winnersLeft = currentRoundMatches.count { match ->
                    val ids = parseIds(match.playerIds)
                    ids.size == 1 || match.winnerId != null
                }

                if (SingleEliminationEngine.isFinalRoundComplete(currentRoundMatches)) {
                    NextRoundResult.TournamentFinished
                } else {
                    val pairings  = SingleEliminationEngine.generateNextRound(currentRoundMatches)
                    val nextRound = currentRound + 1
                    val matches   = buildMatches(tournamentId, nextRound, pairings)
                    dao.insertMatches(matches)
                    NextRoundResult.RoundGenerated(nextRound)
                }
            }

            else -> NextRoundResult.TournamentFinished
        }
    }

    private fun buildMatches(
        tournamentId: Long,
        round: Int,
        pairings: List<Pair<Long, Long?>>,
    ): List<TournamentMatchEntity> = pairings.mapIndexed { index, (playerA, playerB) ->
        if (playerB == null) {
            // Bye: single-player match, auto-finished
            TournamentMatchEntity(
                tournamentId   = tournamentId,
                round          = round,
                playerIds      = "[$playerA]",
                winnerId       = playerA,
                status         = "FINISHED",
                scheduledOrder = index,
            )
        } else {
            TournamentMatchEntity(
                tournamentId   = tournamentId,
                round          = round,
                playerIds      = "[$playerA,$playerB]",
                scheduledOrder = index,
            )
        }
    }

    private fun parseIds(json: String): List<Long> =
        json.trim('[', ']').split(",").mapNotNull { it.trim().toLongOrNull() }
}
