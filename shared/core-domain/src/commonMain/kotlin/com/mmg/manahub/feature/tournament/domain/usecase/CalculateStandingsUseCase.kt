package com.mmg.manahub.feature.tournament.domain.usecase

import com.mmg.manahub.core.model.TournamentStanding
import com.mmg.manahub.feature.tournament.domain.repository.TournamentRepository

/**
 * Returns current DCI standings for a tournament.
 *
 * Delegates to [TournamentRepository.calculateStandings] — the layering violation of injecting
 * [com.mmg.manahub.core.data.local.dao.TournamentDao] directly has been removed as part of the
 * KMP Phase 4 domain extraction.
 *
 * Moved from :app to :shared:core-domain (KMP Phase 4); package preserved so no consumer import
 * changes. Constructed by [com.mmg.manahub.feature.tournament.di.TournamentModule].
 */
class CalculateStandingsUseCase(
    private val repository: TournamentRepository,
) {

    suspend operator fun invoke(tournamentId: Long): List<TournamentStanding> =
        repository.calculateStandings(tournamentId)
}
