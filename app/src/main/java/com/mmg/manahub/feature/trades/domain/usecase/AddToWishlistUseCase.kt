package com.mmg.manahub.feature.trades.domain.usecase

import com.mmg.manahub.feature.trades.domain.model.WishlistEntry
import com.mmg.manahub.feature.trades.domain.repository.WishlistRepository
import javax.inject.Inject

class AddToWishlistUseCase @Inject constructor(private val repo: WishlistRepository) {
    suspend operator fun invoke(entry: WishlistEntry) = repo.addLocal(entry)
}
