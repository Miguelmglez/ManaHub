package com.mmg.manahub.feature.tournament.domain.usecase

import com.mmg.manahub.core.domain.repository.TournamentRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecordMatchResultUseCase @Inject constructor(
    private val repository: TournamentRepository,
) {

    /** Records a win. */
    suspend fun recordWin(
        matchId:    Long,
        winnerId:   Long,
        sessionId:  Long?,
        lifeTotals: Map<Long, Int>,
    ) = repository.finishMatch(
        matchId    = matchId,
        winnerId   = winnerId,
        sessionId  = sessionId,
        lifeTotals = lifeTotals,
    )

    /** Records a draw (winnerId = null, match status → FINISHED). */
    suspend fun recordDraw(
        matchId:    Long,
        sessionId:  Long?,
        lifeTotals: Map<Long, Int>,
    ) = repository.finishMatch(
        matchId    = matchId,
        winnerId   = null,
        sessionId  = sessionId,
        lifeTotals = lifeTotals,
    )
}
