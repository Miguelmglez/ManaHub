package com.mmg.manahub.feature.trades.domain.usecase

import com.mmg.manahub.core.data.local.dao.TradeCollectionSyncDao
import com.mmg.manahub.core.data.local.entity.TradeCollectionSyncEntity
import com.mmg.manahub.core.domain.repository.OpenForTradeRepository
import com.mmg.manahub.core.domain.repository.TradeCollectionLine
import com.mmg.manahub.core.domain.repository.UserCardRepository
import com.mmg.manahub.core.domain.repository.WishlistRepository
import com.mmg.manahub.core.model.TradeItem
import com.mmg.manahub.core.util.recordNonFatal
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
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
 * Network follow-ups (remote open-for-trade removal, wishlist decrement) run only after the local
 * commit; their failures are reported but do not fail the result. The whole operation runs under
 * [NonCancellable] so leaving the screen can never split the local commit from those follow-ups.
 */
class UpdateTradeCollectionUseCase(
    private val userCardRepository: UserCardRepository,
    private val wishlistRepository: WishlistRepository,
    private val openForTradeRepository: OpenForTradeRepository,
    private val syncDao: TradeCollectionSyncDao,
    private val ioDispatcher: CoroutineDispatcher,
) {

    /**
     * @param proposalId    ID of the completed trade proposal.
     * @param userId        ID of the user whose collection is updated.
     * @param sentItems     Items the user traded away.
     * @param receivedItems Items the user received.
     * @param reverse       When `true`, undoes a previously applied sync (revoke flows).
     * @return [Result.success] when applied or when there was nothing to do (already applied, or
     *   nothing to reverse); [Result.failure] when the local write failed and nothing was written.
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
                    onApplied = { syncDao.markSynced(TradeCollectionSyncEntity(proposalId = proposalId, userId = userId)) },
                )
            } ?: return@withContext Result.success(Unit)

            if (!reverse) {
                applied.remoteOfferRemovals.forEach { collectionId ->
                    runCatchingNonCancellation { openForTradeRepository.removeByCollectionIdAndSync(collectionId).getOrThrow() }
                        .onFailure { e -> recordNonFatal("trade_collection_remove_open_for_trade_failed", e) }
                }
                receivedLines.forEach { line ->
                    runCatchingNonCancellation {
                        wishlistRepository.decrementByAttributes(
                            scryfallId = line.scryfallId,
                            quantity = line.quantity,
                            isFoil = line.isFoil,
                            condition = line.condition,
                            language = line.language,
                        ).getOrThrow()
                    }.onFailure { e -> recordNonFatal("trade_collection_wishlist_decrement_failed", e) }
                }
            }
            if (applied.unmatchedDeductionCount > 0) {
                recordNonFatal("trade_collection_deduction_unmatched")
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            recordNonFatal("trade_collection_apply_failed", e)
            Result.failure(e)
        }
    }

    private fun TradeItem.toLine(keepRef: Boolean) = TradeCollectionLine(
        scryfallId = cardId,
        isFoil = isFoil ?: false,
        condition = condition?.uppercase()?.trim() ?: "NM",
        language = language?.lowercase()?.trim() ?: "en",
        quantity = quantity ?: 1,
        userCardIdRef = if (keepRef) userCardIdRef?.takeIf { it.isNotBlank() } else null,
    )

    private inline fun <T> runCatchingNonCancellation(block: () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Result.failure(e)
        }
}
