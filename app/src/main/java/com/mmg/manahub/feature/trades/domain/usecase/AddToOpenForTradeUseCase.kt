package com.mmg.manahub.feature.trades.domain.usecase

import com.mmg.manahub.feature.trades.domain.repository.OpenForTradeRepository
import javax.inject.Inject

class AddToOpenForTradeUseCase @Inject constructor(private val repo: OpenForTradeRepository) {
    suspend operator fun invoke(
        scryfallId: String,
        localCollectionId: String,
        quantity: Int,
        isFoil: Boolean,
        condition: String,
        language: String,
        isAltArt: Boolean,
    ) = repo.addLocal(scryfallId, localCollectionId, quantity, isFoil, condition, language, isAltArt)
}
