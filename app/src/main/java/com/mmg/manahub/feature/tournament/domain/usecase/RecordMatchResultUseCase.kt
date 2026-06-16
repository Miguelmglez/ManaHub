package com.mmg.manahub.feature.tournament.domain.usecase

import com.mmg.manahub.core.domain.repository.MatchResultOutcome
import com.mmg.manahub.core.domain.repository.TournamentRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The ONE entry point for recording a tournament match result (audit C1/C2).
 *
 * Both the game-played flow (`GameViewModel`) and the manual result dialog (`TournamentViewModel`)
 * call these methods, and the repository's [TournamentRepository.finishMatch] performs the entire
 * finish + round-advancement + tournament-finish (XP-emitting) sequence atomically. There is no
 * separate advancement path in any ViewModel.
 */
@Singleton
class RecordMatchResultUseCase @Inject constructor(
    private val repository: TournamentRepository,
) {

    /** Records a win and advances the tournament. Returns what happened. */
    suspend fun recordWin(
        matchId:    Long,
        winnerId:   Long,
        sessionId:  Long?,
        lifeTotals: Map<Long, Int>,
    ): MatchResultOutcome = repository.finishMatch(
        matchId    = matchId,
        winnerId   = winnerId,
        sessionId  = sessionId,
        lifeTotals = lifeTotals,
    )

    /** Records a draw (winnerId = null) and advances the tournament. Returns what happened. */
    suspend fun recordDraw(
        matchId:    Long,
        sessionId:  Long?,
        lifeTotals: Map<Long, Int>,
    ): MatchResultOutcome = repository.finishMatch(
        matchId    = matchId,
        winnerId   = null,
        sessionId  = sessionId,
        lifeTotals = lifeTotals,
    )
}
