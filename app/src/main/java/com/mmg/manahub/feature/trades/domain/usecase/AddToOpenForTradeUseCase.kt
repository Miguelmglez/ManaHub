package com.mmg.manahub.feature.trades.domain.usecase

import com.mmg.manahub.feature.trades.domain.repository.OpenForTradeRepository
import javax.inject.Inject

class AddToOpenForTradeUseCase @Inject constructor(private val repo: OpenForTradeRepository) {
    suspend operator fun invoke(scryfallId: String, localCollectionId: String) =
        repo.addLocal(scryfallId, localCollectionId)
}
