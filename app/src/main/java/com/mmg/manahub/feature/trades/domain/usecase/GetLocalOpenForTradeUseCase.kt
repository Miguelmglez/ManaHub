package com.mmg.manahub.feature.trades.domain.usecase

import com.mmg.manahub.core.domain.repository.OpenForTradeRepository
import javax.inject.Inject

class GetLocalOpenForTradeUseCase @Inject constructor(private val repo: OpenForTradeRepository) {
    operator fun invoke() = repo.observeLocal()
}
