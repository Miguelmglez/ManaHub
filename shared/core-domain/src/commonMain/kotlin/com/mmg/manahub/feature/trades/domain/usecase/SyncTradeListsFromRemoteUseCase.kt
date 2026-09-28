package com.mmg.manahub.feature.trades.domain.usecase

import com.mmg.manahub.core.domain.repository.OpenForTradeRepository
import com.mmg.manahub.core.domain.repository.WishlistRepository

/**
 * Downloads the wishlist and open-for-trade lists from Supabase and persists
 * them to Room for the given [userId].
 *
 * The two lists sync independently: a wishlist failure never skips the open-for-trade sync. The
 * first failure is returned once both have run; the caller decides whether to surface it.
 */
class SyncTradeListsFromRemoteUseCase(
    private val wishlistRepo: WishlistRepository,
    private val openForTradeRepo: OpenForTradeRepository,
) {
    suspend operator fun invoke(userId: String): Result<Unit> {
        val wishlist = wishlistRepo.syncFromRemote(userId)
        val offers = openForTradeRepo.syncFromRemote(userId)
        val failure = wishlist.exceptionOrNull() ?: offers.exceptionOrNull()
        return if (failure != null) Result.failure(failure) else Result.success(Unit)
    }
}
