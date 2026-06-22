package com.mmg.manahub.feature.communitydecks.domain.usecase

import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.model.CommunityDeckSearchResult
import com.mmg.manahub.core.domain.repository.CommunityDecksRepository

/**
 * Searches community decks on Archidekt.
 *
 * A thin pass-through to [CommunityDecksRepository.searchDecks] that keeps the
 * presentation layer decoupled from the repository contract. Every parameter is
 * optional; omitted (null) parameters are dropped from the request.
 */
class SearchCommunityDecksUseCase(
    private val repository: CommunityDecksRepository,
) {
    suspend operator fun invoke(
        cardName: String? = null,
        deckFormat: Int? = null,
        orderBy: String? = null,
        page: Int = 1,
        pageSize: Int = 20,
    ): DataResult<CommunityDeckSearchResult> =
        repository.searchDecks(cardName, deckFormat, orderBy, page, pageSize)
}
