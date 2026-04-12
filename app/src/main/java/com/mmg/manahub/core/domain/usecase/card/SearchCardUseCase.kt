package com.mmg.manahub.core.domain.usecase.card

import com.mmg.manahub.core.domain.model.Card
import com.mmg.manahub.core.domain.model.DataResult
import com.mmg.manahub.core.domain.repository.CardRepository
import javax.inject.Inject

class SearchCardUseCase @Inject constructor(
    private val repository: CardRepository,
) {
    suspend operator fun invoke(query: String): DataResult<Card> =
        repository.searchCardByName(query)
}