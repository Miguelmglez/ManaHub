package com.mmg.magicfolder.core.domain.usecase.card

import com.mmg.magicfolder.core.domain.model.Card
import com.mmg.magicfolder.core.domain.model.DataResult
import com.mmg.magicfolder.core.domain.repository.CardRepository
import javax.inject.Inject

/**
 * Full-text search returning a list of matching cards from Scryfall.
 * Results are cached in Room by the repository.
 */
class SearchCardsUseCase @Inject constructor(
    private val repository: CardRepository,
) {
    suspend operator fun invoke(query: String, page: Int = 1): DataResult<List<Card>> =
        repository.searchCards(query, page)
}
