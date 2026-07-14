package com.mmg.manahub.core.domain.usecase.card

import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.domain.repository.CardRepository

import com.mmg.manahub.core.model.PaginatedCards

/**
 * Full-text search returning a paginated list of matching cards from Scryfall.
 * Results are cached in Room by the repository.
 */
class SearchCardsUseCase(
    private val repository: CardRepository,
) {
    suspend operator fun invoke(query: String, page: Int = 1): DataResult<PaginatedCards> =
        repository.searchCardsPaginated(query, page)
}
