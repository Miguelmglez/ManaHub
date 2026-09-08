package com.mmg.manahub.core.sync
// COMMENTS_REVIEWED: 2026-09-06

import com.mmg.manahub.core.data.local.dao.UserCardCollectionDao
import com.mmg.manahub.core.data.local.entity.UserCardCollectionEntity
import com.mmg.manahub.core.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A guest (offline, `user_id IS NULL`) collection row whose composite tuple
 * `(scryfall_id, is_foil, condition, language)` already matches a row [accountRow] the
 * currently-signed-in user owns. [accountRow] may itself be a tombstone (soft-deleted) --
 * [UserCardCollectionDao.assignUserId]'s guard parks a guest row equally in that case, since a
 * tombstone still occupies its tuple at the DB level.
 */
data class CollectionMergeConflict(
    val guestRow: UserCardCollectionEntity,
    val accountRow: UserCardCollectionEntity,
)

/** The user's explicit choice for one [CollectionMergeConflict]. */
enum class MergeConflictResolution {
    /** Combine both quantities into the account row. */
    SUM,

    /** Keep the account row exactly as-is; discard the guest row's data. */
    KEEP_ACCOUNT,

    /** Overwrite the account row's quantity/trade flag with the guest row's; discard the guest row. */
    KEEP_OFFLINE,
}

/**
 * Finds and resolves [CollectionMergeConflict]s left behind by
 * [UserCardCollectionDao.assignUserId]'s collision guard.
 *
 * Deliberately NOT reactive (`Flow`) -- collisions are rare, so a one-shot suspend check
 * triggered after login/sync is simpler and sufficient.
 */
@Singleton
class CollectionMergeConflictResolver @Inject constructor(
    private val collectionDao: UserCardCollectionDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    /** Returns every pending conflict for [userId]. Empty when there is nothing to resolve. */
    suspend fun getPendingConflicts(userId: String): List<CollectionMergeConflict> =
        withContext(ioDispatcher) {
            collectionDao.getAllGuestRows().mapNotNull { guestRow ->
                val accountRow = collectionDao.getByCompositeKey(
                    userId, guestRow.scryfallId, guestRow.isFoil,
                    guestRow.condition, guestRow.language,
                )
                accountRow?.let { CollectionMergeConflict(guestRow = guestRow, accountRow = it) }
            }
        }

    /**
     * Applies the user's [resolution] for [conflict] atomically
     * ([UserCardCollectionDao.resolveMergeConflict]) so a process-kill mid-resolve can never
     * leave the account row written and the guest row still pending (which would double-count on
     * the next resolve). Dismissing the sheet without calling this loses nothing -- the guest row
     * simply stays a pending conflict for next time.
     *
     * A tombstoned [CollectionMergeConflict.accountRow] contributes nothing to SUM (its stale
     * pre-deletion quantity/trade-flag reflect data the user already discarded) and is always
     * revived (`isDeleted = false`) for SUM/KEEP_OFFLINE -- otherwise the merged result would
     * write into a row `is_deleted = 1` still filters out of every observe query, silently
     * losing the guest row's data into a dead tombstone.
     */
    suspend fun resolve(conflict: CollectionMergeConflict, resolution: MergeConflictResolution) =
        withContext(ioDispatcher) {
            val now = System.currentTimeMillis()
            val accountQuantity = if (conflict.accountRow.isDeleted) 0 else conflict.accountRow.quantity
            val accountIsForTrade = !conflict.accountRow.isDeleted && conflict.accountRow.isForTrade

            val resolvedAccountRow = when (resolution) {
                MergeConflictResolution.SUM -> conflict.accountRow.copy(
                    quantity = accountQuantity + conflict.guestRow.quantity,
                    isForTrade = accountIsForTrade || conflict.guestRow.isForTrade,
                    isDeleted = false,
                    updatedAt = now,
                )

                MergeConflictResolution.KEEP_ACCOUNT -> null

                MergeConflictResolution.KEEP_OFFLINE -> conflict.accountRow.copy(
                    quantity = conflict.guestRow.quantity,
                    isForTrade = conflict.guestRow.isForTrade,
                    isDeleted = false,
                    updatedAt = now,
                )
            }
            collectionDao.resolveMergeConflict(resolvedAccountRow, conflict.guestRow.id)
        }
}
