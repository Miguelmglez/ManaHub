package com.mmg.manahub.feature.trades.domain.usecase

import com.mmg.manahub.feature.trades.domain.repository.WishlistRepository
import javax.inject.Inject

class RemoveFromWishlistUseCase @Inject constructor(private val repo: WishlistRepository) {
    suspend operator fun invoke(id: String) = repo.removeLocal(id)
}
