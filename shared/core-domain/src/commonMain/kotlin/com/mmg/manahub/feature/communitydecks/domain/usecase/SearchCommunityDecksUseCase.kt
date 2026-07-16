package com.mmg.manahub.feature.communitydecks.domain.usecase

import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.model.CommunityDeckSearchFilters
import com.mmg.manahub.core.model.CommunityDeckSearchResult
import com.mmg.manahub.core.domain.repository.CommunityDecksRepository

/**
 * Searches community decks on Archidekt.
 *
 * A thin pass-through to [CommunityDecksRepository.searchDecks] that keeps the
 * presentation layer decoupled from the repository contract. Every [CommunityDeckSearchFilters]
 * field is optional; omitted (null/empty) fields are dropped from the request.
 */
class SearchCommunityDecksUseCase(
    private val repository: CommunityDecksRepository,
) {
    suspend operator fun invoke(filters: CommunityDeckSearchFilters): DataResult<CommunityDeckSearchResult> =
        repository.searchDecks(filters)
}
