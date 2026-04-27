package com.mmg.manahub.feature.trades.domain.usecase

import com.mmg.manahub.feature.trades.domain.repository.WishlistRepository
import javax.inject.Inject

class GetLocalWishlistUseCase @Inject constructor(private val repo: WishlistRepository) {
    operator fun invoke() = repo.observeLocal()
}
