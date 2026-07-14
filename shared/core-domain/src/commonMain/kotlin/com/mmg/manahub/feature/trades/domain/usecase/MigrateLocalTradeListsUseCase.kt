package com.mmg.manahub.feature.trades.domain.usecase

import com.mmg.manahub.core.domain.repository.OpenForTradeRepository
import com.mmg.manahub.core.domain.repository.WishlistRepository

/**
 * Migrates locally-stored wishlist and open-for-trade entries to the remote
 * backend for the given [userId]. Returns the total number of entries migrated.
 */
class MigrateLocalTradeListsUseCase(
    private val wishlistRepo: WishlistRepository,
    private val openForTradeRepo: OpenForTradeRepository,
) {
    suspend operator fun invoke(userId: String): Result<Int> = runCatching {
        val wishlistCount = wishlistRepo.migrateLocalToRemote(userId).getOrDefault(0)
        val offerCount = openForTradeRepo.migrateLocalToRemote(userId).getOrDefault(0)
        wishlistCount + offerCount
    }
}
