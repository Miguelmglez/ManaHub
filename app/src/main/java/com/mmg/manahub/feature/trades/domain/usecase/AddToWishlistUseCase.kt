package com.mmg.manahub.feature.trades.domain.usecase

import com.mmg.manahub.core.domain.auth.SessionState
import com.mmg.manahub.core.domain.auth.AuthRepository
import com.mmg.manahub.core.domain.model.WishlistEntry
import com.mmg.manahub.feature.trades.domain.repository.WishlistRepository
import javax.inject.Inject

/**
 * Adds a [WishlistEntry] to the wishlist.
 *
 * When the user is authenticated the entry is persisted locally **and** pushed to
 * Supabase in a single atomic operation via [WishlistRepository.addAndSync], so the
 * remote list stays up-to-date without waiting for the next migration sweep.
 *
 * When the user is unauthenticated the entry is stored locally only (offline-first)
 * and will be migrated on next login by [com.mmg.manahub.feature.trades.domain.usecase.MigrateLocalTradeListsUseCase].
 */
class AddToWishlistUseCase @Inject constructor(
    private val repo: WishlistRepository,
    private val authRepo: AuthRepository,
) {
    suspend operator fun invoke(entry: WishlistEntry): Result<Unit> {
        val userId = (authRepo.sessionState.value as? SessionState.Authenticated)?.user?.id
        return if (userId != null) {
            repo.addAndSync(entry, userId)
        } else {
            repo.addLocal(entry)
        }
    }
}
