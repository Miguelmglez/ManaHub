package com.mmg.manahub.feature.trades.data

import com.mmg.manahub.core.data.local.dao.LocalWishlistDao
import com.mmg.manahub.core.data.local.dao.TradeCollectionSyncDao
import com.mmg.manahub.core.data.local.entity.TradeWishlistCleanupEntity
import com.mmg.manahub.core.data.remote.trades.WishlistRemoteDataSource
import com.mmg.manahub.core.domain.repository.TradeCollectionLine
import com.mmg.manahub.core.util.recordSafeNonFatal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Commits local wishlist decrements with the collection and retries absolute server targets. */
class TradeWishlistCleanup(
    private val wishlistDao: LocalWishlistDao,
    private val syncDao: TradeCollectionSyncDao,
    private val remote: WishlistRemoteDataSource,
    private val activeUserId: suspend () -> String?,
) {
    private val drainMutex = Mutex()

    /** Called inside the collection's Room transaction. */
    suspend fun stage(userId: String, receivedLines: List<TradeCollectionLine>) {
        require(activeUserId() == userId)
        receivedLines.forEach { line ->
            val entries = wishlistDao.getByScryfallId(line.scryfallId, userId)
            val target = entries.firstOrNull { entry ->
                (entry.isFoil ?: false) == line.isFoil &&
                    (entry.condition == null || entry.condition.equals(line.condition, ignoreCase = true)) &&
                    (entry.language == null || entry.language.equals(line.language, ignoreCase = true))
            } ?: entries.firstOrNull { it.matchAnyVariant }
            if (target == null) {
                if (entries.isNotEmpty()) recordSafeNonFatal(
                    "trade_wishlist_decrement_no_match",
                    IllegalStateException("No matching wishlist variant"),
                )
                return@forEach
            }
            val remaining = (target.quantity - line.quantity).coerceAtLeast(0)
            if (remaining == 0) wishlistDao.deleteById(target.id, userId)
            else wishlistDao.updateQuantity(target.id, remaining, userId)
            if (target.synced) {
                syncDao.upsertWishlistCleanup(TradeWishlistCleanupEntity(userId, target.id, remaining))
            }
        }
    }

    /** Replays remote updates independently of the already committed collection gate. */
    suspend fun drain(userId: String): Result<Unit> = try {
        drainMutex.withLock {
            require(activeUserId() == userId)
            syncDao.getPendingWishlistCleanups(userId).forEach { entry ->
                require(activeUserId() == userId)
                if (entry.targetQuantity == 0) remote.removeWishlistEntry(entry.wishlistId).getOrThrow()
                else remote.updateWishlistQuantity(entry.wishlistId, entry.targetQuantity).getOrThrow()
                require(activeUserId() == userId)
                syncDao.clearWishlistCleanup(userId, entry.wishlistId, entry.targetQuantity)
            }
        }
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        recordSafeNonFatal("trade_wishlist_cleanup_failed", e)
        Result.failure(e)
    }
}
