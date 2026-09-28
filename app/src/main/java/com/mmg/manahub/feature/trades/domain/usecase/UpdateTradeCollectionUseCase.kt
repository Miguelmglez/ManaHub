package com.mmg.manahub.feature.trades.domain.usecase

import com.mmg.manahub.core.data.local.dao.TradeCollectionSyncDao
import com.mmg.manahub.core.data.local.entity.TradeCollectionSyncEntity
import com.mmg.manahub.core.data.local.entity.TradeOfferCleanupEntity
import com.mmg.manahub.core.domain.repository.OpenForTradeRepository
import com.mmg.manahub.core.domain.repository.TradeCollectionLine
import com.mmg.manahub.core.domain.repository.UserCardRepository
import com.mmg.manahub.feature.trades.data.TradeWishlistCleanup
import com.mmg.manahub.core.model.TradeItem
import com.mmg.manahub.core.util.recordNonFatal
import com.mmg.manahub.core.util.recordSafeNonFatal
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.cancellation.CancellationException

/**
 * Applies a trade's effect on the local collection, or reverses it after a revoke.
 *
 * **Normal mode** (`reverse = false`): sent items are deducted (from their own collection row when
 * [TradeItem.userCardIdRef] identifies it, by attributes otherwise — only the traded copies, never
 * the whole row) and received items are added. The completion marker is written in the SAME local
 * transaction, so the apply runs at most once per proposal and user.
 *
 * **Reverse mode** (`reverse = true`): sent items are added back and received items are deducted by
 * attributes (their refs belong to the other party). Only runs when a completion marker exists, and
 * removes it in the same transaction.
 *
 * Remote offer removal and wishlist updates are queued within the local transaction, then retried
 * independently of the apply gate. A remote failure leaves cleanup pending. The whole operation
 * runs under [NonCancellable] so leaving the screen cannot interrupt the local commit.
 */
class UpdateTradeCollectionUseCase(
    private val userCardRepository: UserCardRepository,
    private val wishlistCleanup: TradeWishlistCleanup,
    private val openForTradeRepository: OpenForTradeRepository,
    private val syncDao: TradeCollectionSyncDao,
    private val ioDispatcher: CoroutineDispatcher,
    private val activeUserId: (suspend () -> String?)? = null,
) {
    private val cleanupMutex = Mutex()

    /**
     * @param proposalId    ID of the completed trade proposal.
     * @param userId        ID of the user whose collection is updated.
     * @param sentItems     Items the user traded away.
     * @param receivedItems Items the user received.
     * @param reverse       When `true`, undoes a previously applied sync (revoke flows).
     * @return [Result.success] when applied or already applied with no pending cleanup;
     *   [Result.failure] when a local write or remote offer cleanup fails. A remote failure may
     *   follow a successful local commit and remains retryable through the durable outbox.
     */
    suspend operator fun invoke(
        proposalId: String,
        userId: String,
        sentItems: List<TradeItem>,
        receivedItems: List<TradeItem>,
        reverse: Boolean = false,
    ): Result<Unit> = withContext(ioDispatcher + NonCancellable) {
        try {
            val sentLines = sentItems.filterNot { it.isReviewCollectionPlaceholder }.map { it.toLine(keepRef = true) }
            val receivedLines = receivedItems.filterNot { it.isReviewCollectionPlaceholder }.map { it.toLine(keepRef = false) }

            val applied = if (reverse) {
                userCardRepository.applyTradeCollectionChanges(
                    userId = userId,
                    deductions = receivedLines,
                    additions = sentLines.map { it.copy(userCardIdRef = null) },
                    shouldApply = { syncDao.isSynced(proposalId, userId) > 0 },
                    onApplied = { syncDao.removeSyncRecord(proposalId, userId) },
                )
            } else {
                userCardRepository.applyTradeCollectionChanges(
                    userId = userId,
                    deductions = sentLines,
                    additions = receivedLines,
                    shouldApply = { syncDao.isSynced(proposalId, userId) == 0 },
                    onApplied = {
                        wishlistCleanup.stage(userId, receivedLines)
                        syncDao.markSynced(TradeCollectionSyncEntity(proposalId = proposalId, userId = userId))
                    },
                    onOfferRemovals = { collectionIds ->
                        syncDao.enqueueOfferCleanups(collectionIds.map { collectionId ->
                            TradeOfferCleanupEntity(proposalId, userId, collectionId)
                        })
                    },
                )
            }

            val cleanupResult = if (!reverse) retryPendingOfferCleanup(userId) else Result.success(Unit)
            val wishlistCleanupResult = if (!reverse) wishlistCleanup.drain(userId) else Result.success(Unit)
            if (applied != null && applied.unmatchedDeductionCount > 0) {
                recordNonFatal("trade_collection_deduction_unmatched")
            }
            cleanupResult.getOrThrow()
            wishlistCleanupResult.getOrThrow()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            recordSafeNonFatal("trade_collection_apply_failed", e)
            Result.failure(e)
        }
    }

    /** Retries committed offer deletions without reapplying collection changes. */
    suspend fun retryPendingOfferCleanup(userId: String): Result<Unit> = withContext(ioDispatcher) {
        try {
            cleanupMutex.withLock {
                activeUserId?.let { require(it() == userId) }
                for (entry in syncDao.getPendingOfferCleanups(userId)) {
                    activeUserId?.let { require(it() == userId) }
                    openForTradeRepository.removeByCollectionIdAndSync(entry.collectionId).getOrThrow()
                    activeUserId?.let { require(it() == userId) }
                    syncDao.clearOfferCleanup(entry.proposalId, entry.userId, entry.collectionId)
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            recordSafeNonFatal("trade_collection_remove_open_for_trade_failed", e)
            Result.failure(e)
        }
    }

    suspend fun retryPendingWishlistCleanup(userId: String): Result<Unit> = withContext(ioDispatcher) {
        wishlistCleanup.drain(userId)
    }

    private fun TradeItem.toLine(keepRef: Boolean) = TradeCollectionLine(
        scryfallId = cardId,
        isFoil = isFoil ?: false,
        condition = condition?.uppercase()?.trim() ?: "NM",
        language = language?.lowercase()?.trim() ?: "en",
        quantity = quantity ?: 1,
        userCardIdRef = if (keepRef) userCardIdRef?.takeIf { it.isNotBlank() } else null,
    )

}
