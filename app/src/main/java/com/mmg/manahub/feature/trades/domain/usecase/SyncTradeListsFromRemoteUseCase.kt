package com.mmg.manahub.feature.trades.domain.usecase

import com.mmg.manahub.core.domain.repository.OpenForTradeRepository
import com.mmg.manahub.core.domain.repository.WishlistRepository
import javax.inject.Inject

/**
 * Downloads the wishlist and open-for-trade lists from Supabase and persists
 * them to Room for the given [userId].
 *
 * Both operations run sequentially. Failures from either repository are
 * propagated to the caller; the caller decides whether to surface them.
 */
class SyncTradeListsFromRemoteUseCase @Inject constructor(
    private val wishlistRepo: WishlistRepository,
    private val openForTradeRepo: OpenForTradeRepository,
) {
    suspend operator fun invoke(userId: String): Result<Unit> = runCatching {
        wishlistRepo.syncFromRemote(userId).getOrThrow()
        openForTradeRepo.syncFromRemote(userId).getOrThrow()
    }
}
