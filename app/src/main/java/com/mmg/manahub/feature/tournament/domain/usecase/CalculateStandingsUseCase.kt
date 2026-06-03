package com.mmg.manahub.feature.tournament.domain.usecase

import com.mmg.manahub.core.data.local.dao.TournamentDao
import com.mmg.manahub.core.data.local.entity.projection.TournamentStanding
import com.mmg.manahub.core.di.IoDispatcher
import com.mmg.manahub.feature.tournament.domain.engine.StandingsCalculator
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CalculateStandingsUseCase @Inject constructor(
    private val dao: TournamentDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    suspend operator fun invoke(tournamentId: Long): List<TournamentStanding> = withContext(ioDispatcher) {
        val players         = dao.getPlayers(tournamentId)
        val finishedMatches = dao.getFinishedMatches(tournamentId)
        StandingsCalculator.calculate(players, finishedMatches)
    }
}
