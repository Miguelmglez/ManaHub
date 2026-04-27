package com.mmg.manahub.feature.trades.domain.usecase

import com.mmg.manahub.feature.trades.domain.repository.TradeSuggestionsRepository
import javax.inject.Inject

class GetSuggestionsForCardUseCase @Inject constructor(private val repo: TradeSuggestionsRepository) {
    suspend operator fun invoke(cardId: String) = repo.getSuggestionsForCard(cardId)
}
