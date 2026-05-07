package com.mmg.manahub.feature.trades.domain.usecase

import com.mmg.manahub.feature.trades.domain.repository.OpenForTradeRepository
import com.mmg.manahub.feature.trades.domain.repository.WishlistRepository
import javax.inject.Inject

class MigrateLocalTradeListsUseCase @Inject constructor(
    private val wishlistRepo: WishlistRepository,
    private val openForTradeRepo: OpenForTradeRepository,
) {
    suspend operator fun invoke(userId: String): Result<Int> = runCatching {
        val wishlistCount = wishlistRepo.migrateLocalToRemote(userId).getOrDefault(0)
        val offerCount = openForTradeRepo.migrateLocalToRemote(userId).getOrDefault(0)
        wishlistCount + offerCount
    }
}
