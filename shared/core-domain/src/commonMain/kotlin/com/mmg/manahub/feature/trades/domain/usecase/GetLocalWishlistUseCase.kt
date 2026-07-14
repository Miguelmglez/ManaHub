package com.mmg.manahub.feature.trades.domain.usecase

import com.mmg.manahub.core.domain.repository.WishlistRepository

/**
 * Retrieves the locally-stored wishlist as a reactive stream.
 */
class GetLocalWishlistUseCase(private val repo: WishlistRepository) {
    operator fun invoke() = repo.observeLocal()
}
