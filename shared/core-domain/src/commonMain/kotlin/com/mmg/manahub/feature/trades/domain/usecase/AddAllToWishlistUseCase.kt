package com.mmg.manahub.feature.trades.domain.usecase

import com.mmg.manahub.core.domain.auth.AuthRepository
import com.mmg.manahub.core.domain.auth.SessionState
import com.mmg.manahub.core.domain.repository.WishlistRepository
import com.mmg.manahub.core.model.WishlistEntry

/** Batch strategy for `CardQueueActions.addAllToWishlist`. */
fun interface WishlistBatchWriter {
    suspend fun addAll(entries: List<WishlistEntry>): Result<Unit>
}

/**
 * Adds many wishlist entries in ONE local write, then pushes every unsynced row in one remote call
 * when signed in. A failed push is sync lag, not a failure: the rows stay unsynced and are retried
 * by the next migration, exactly like [AddToWishlistUseCase].
 */
class AddAllToWishlistUseCase(
    private val repo: WishlistRepository,
    private val authRepo: AuthRepository,
) : WishlistBatchWriter {

    override suspend fun addAll(entries: List<WishlistEntry>): Result<Unit> {
        if (entries.isEmpty()) return Result.success(Unit)
        val local = repo.addAllLocal(entries)
        if (local.isFailure) return local
        val userId = (authRepo.sessionState.value as? SessionState.Authenticated)?.user?.id
        if (userId != null) repo.migrateLocalToRemote(userId)
        return Result.success(Unit)
    }
}
