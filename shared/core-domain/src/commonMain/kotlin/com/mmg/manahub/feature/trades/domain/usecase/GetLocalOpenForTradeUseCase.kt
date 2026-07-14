package com.mmg.manahub.feature.trades.domain.usecase

import com.mmg.manahub.core.domain.repository.OpenForTradeRepository

/**
 * Retrieves the locally-stored open-for-trade list as a reactive stream.
 */
class GetLocalOpenForTradeUseCase(private val repo: OpenForTradeRepository) {
    operator fun invoke() = repo.observeLocal()
}
