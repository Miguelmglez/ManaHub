package com.mmg.manahub.feature.trades.domain.usecase

import com.mmg.manahub.core.di.IoDispatcher
import com.mmg.manahub.core.domain.repository.UserCardRepository
import com.mmg.manahub.feature.trades.data.local.dao.TradeCollectionSyncDao
import com.mmg.manahub.feature.trades.data.local.entity.TradeCollectionSyncEntity
import com.mmg.manahub.feature.trades.domain.model.TradeItem
import com.mmg.manahub.feature.trades.domain.repository.OpenForTradeRepository
import com.mmg.manahub.feature.trades.domain.repository.WishlistRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Syncs the local collection after a trade completes or is reversed on revoke.
 *
 * **Normal mode** (`reverse = false`):
 * - **Sent items**: deletes the [UserCardEntity] row identified by [TradeItem.userCardIdRef].
 *   Items with a null ref are silently skipped.
 * - **Received items**: calls [UserCardRepository.addOrIncrement] unconditionally,
 *   creating a new collection entry or incrementing an existing one.
 * - **Sync record**: writes a [TradeCollectionSyncEntity] so the UI can replace the
 *   "Update Collection" button with a static confirmation label.
 *
 * **Reverse mode** (`reverse = true`):
 * - **Sent items**: restored via [UserCardRepository.addOrIncrement] (cards given away come back).
 * - **Received items**: removed via [UserCardRepository.decrementOrRemove] (cards received are returned).
 * - **Sync record**: deleted with [TradeCollectionSyncDao.removeSyncRecord].
 *
 * Individual card failures are swallowed via [runCatching] so a single bad item
 * cannot block the rest of the sync or leave the sync record unwritten.
 *
 * Additionally, in normal mode sent items also remove the corresponding [OpenForTradeRepository]
 * entry (the card is no longer in the collection so it should not remain offered for trade), and
 * received items are matched against the best-fitting wishlist entry by attributes before
 * decrementing (foil / condition / language), falling back to a matchAnyVariant entry
 * or any entry for that scryfallId when no exact match exists.
 */
@Singleton
class UpdateTradeCollectionUseCase @Inject constructor(
    private val userCardRepository: UserCardRepository,
    private val wishlistRepository: WishlistRepository,
    private val openForTradeRepository: OpenForTradeRepository,
    private val syncDao: TradeCollectionSyncDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    /**
     * @param proposalId    ID of the completed [TradeProposal].
     * @param userId        ID of the user performing the sync.
     * @param sentItems     Items the user traded away.
     * @param receivedItems Items the user received.
     * @param reverse       When `true`, undoes a previously applied sync (for revoke flows).
     * @return [Result.success] on completion; [Result.failure] only if the outer
     *         coroutine block itself throws (not for individual card failures).
     */
    suspend operator fun invoke(
        proposalId: String,
        userId: String,
        sentItems: List<TradeItem>,
        receivedItems: List<TradeItem>,
        reverse: Boolean = false,
    ): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            if (reverse) {
                // Restore cards the user previously sent (they come back to the collection).
                sentItems.forEach { item ->
                    runCatching {
                        userCardRepository.addOrIncrement(
                            scryfallId       = item.cardId,
                            isFoil           = item.isFoil ?: false,
                            condition        = item.condition?.uppercase()?.trim() ?: "NM",
                            language         = item.language?.lowercase()?.trim() ?: "en",
                            isForTrade       = false,
                            userId           = userId,
                            quantity         = item.quantity ?: 1,
                        )
                    }
                }

                // Remove cards the user previously received (they are returned).
                receivedItems.forEach { item ->
                    runCatching {
                        userCardRepository.decrementOrRemove(
                            userId           = userId,
                            scryfallId       = item.cardId,
                            isFoil           = item.isFoil ?: false,
                            condition        = item.condition?.uppercase()?.trim() ?: "NM",
                            language         = item.language?.lowercase()?.trim() ?: "en",
                            quantityToDeduct = item.quantity ?: 1,
                        )
                    }
                }

                // Remove the sync record so the UI reverts to the "Update Collection" button.
                syncDao.removeSyncRecord(proposalId, userId)
            } else {
                // Delete the specific UserCard rows that were included in the trade.
                // Items without a userCardIdRef are silently skipped — the card may
                // have been deleted from the collection already.
                sentItems.forEach { item ->
                    val ref = item.userCardIdRef ?: return@forEach
                    runCatching { userCardRepository.deleteCard(ref) }
                    // Removes the open_for_trade entry both locally and from Supabase.
                    // The card is no longer owned so it must not remain offered for trade.
                    runCatching { openForTradeRepository.removeByCollectionIdAndSync(ref) }
                    // Individual failures are intentionally swallowed — a missing row
                    // (card already sold/deleted) must not abort the whole sync.
                }

                // Add cards the user received and decrement their wishlist accordingly.
                receivedItems.forEach { item ->
                    runCatching {
                        userCardRepository.addOrIncrement(
                            scryfallId       = item.cardId,
                            isFoil           = item.isFoil ?: false,
                            condition        = item.condition?.uppercase()?.trim() ?: "NM",
                            language         = item.language?.lowercase()?.trim() ?: "en",
                            isForTrade       = false,
                            userId           = userId,
                            quantity         = item.quantity ?: 1,
                        )
                    }
                    runCatching {
                        wishlistRepository.decrementByAttributes(
                            scryfallId = item.cardId,
                            quantity   = item.quantity ?: 1,
                            isFoil     = item.isFoil ?: false,
                            condition  = item.condition?.uppercase()?.trim() ?: "NM",
                            language   = item.language?.lowercase()?.trim() ?: "en",
                        )
                    }
                }

                // Mark this proposal as synced so the UI updates immediately.
                syncDao.markSynced(
                    TradeCollectionSyncEntity(proposalId = proposalId, userId = userId),
                )
            }
        }
    }
}
