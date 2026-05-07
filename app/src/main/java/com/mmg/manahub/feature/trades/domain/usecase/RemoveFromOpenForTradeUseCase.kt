package com.mmg.manahub.feature.trades.domain.usecase

import com.mmg.manahub.feature.trades.domain.repository.OpenForTradeRepository
import javax.inject.Inject

class RemoveFromOpenForTradeUseCase @Inject constructor(private val repo: OpenForTradeRepository) {
    suspend operator fun invoke(id: String) = repo.removeLocal(id)
}
