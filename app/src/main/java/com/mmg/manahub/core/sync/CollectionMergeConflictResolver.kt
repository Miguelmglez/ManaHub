package com.mmg.manahub.core.sync

import com.mmg.manahub.core.data.local.dao.UserCardCollectionDao
import com.mmg.manahub.core.data.local.entity.UserCardCollectionEntity
import com.mmg.manahub.core.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A guest (offline, `user_id IS NULL`) collection row whose composite tuple
 * `(scryfall_id, is_foil, condition, language)` already matches a LIVE row [accountRow] the
 * currently-signed-in user owns.
 *
 * Write-path hardening audit (Phase 7, 2026-09-06): [UserCardCollectionDao.assignUserId]'s
 * `NOT EXISTS` guard leaves exactly this shape of row behind (still `user_id = NULL`, still
 * visible via `observeAll`/`observeAllLocal`) instead of silently merging or discarding it — this
 * class is what surfaces it to the user for an explicit choice
 * ([CollectionMergeConflictSheet]/[resolve]).
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
 * Deliberately NOT reactive (`Flow`) — collisions are rare (only surfaces when the exact same
 * card/foil/condition/language was added both offline and in a previously-logged-in session), so
 * a one-shot suspend check triggered after login/sync is simpler and sufficient; a live query
 * would need a custom multi-DAO Flow combinator for no real user-facing benefit.
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
     * Applies the user's [resolution] for [conflict]. The guest row is hard-deleted only AFTER
     * the account-row write (if any) completes, and only as part of resolving THIS specific
     * conflict — dismissing the sheet without calling this loses nothing, the guest row simply
     * stays a pending conflict for next time.
     */
    suspend fun resolve(conflict: CollectionMergeConflict, resolution: MergeConflictResolution) =
        withContext(ioDispatcher) {
            val now = System.currentTimeMillis()
            when (resolution) {
                MergeConflictResolution.SUM -> collectionDao.upsert(
                    conflict.accountRow.copy(
                        quantity = conflict.accountRow.quantity + conflict.guestRow.quantity,
                        isForTrade = conflict.accountRow.isForTrade || conflict.guestRow.isForTrade,
                        updatedAt = now,
                    )
                )

                MergeConflictResolution.KEEP_ACCOUNT -> {
                    // Account row is already correct -- nothing to write.
                }

                MergeConflictResolution.KEEP_OFFLINE -> collectionDao.upsert(
                    conflict.accountRow.copy(
                        quantity = conflict.guestRow.quantity,
                        isForTrade = conflict.guestRow.isForTrade,
                        updatedAt = now,
                    )
                )
            }
            collectionDao.deleteById(conflict.guestRow.id)
        }
}
