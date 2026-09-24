package com.mmg.manahub.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.mmg.manahub.core.data.local.entity.TradeCollectionSyncEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for the [TradeCollectionSyncEntity] table.
 *
 * Tracks which proposals have had their collection synced by the current user,
 * so the UI can show "Collection updated" instead of the sync button after the
 * user has already tapped it (even across screen re-compositions or process kills).
 */
@Dao
interface TradeCollectionSyncDao {

    /**
     * Records that [entity.userId] has applied the collection changes for [entity.proposalId].
     * An upsert so a pending-apply record becomes an applied one; the caller's idempotency gate
     * ([isSynced]) runs in the same transaction, so this never double-applies.
     */
    @Upsert
    suspend fun markSynced(entity: TradeCollectionSyncEntity)

    /**
     * Returns a count > 0 if [userId] has already APPLIED [proposalId]'s collection changes.
     * Pending-apply records do not count.
     */
    @Query("SELECT COUNT(*) FROM trade_collection_sync WHERE proposal_id = :proposalId AND user_id = :userId AND pending_apply = 0")
    suspend fun isSynced(proposalId: String, userId: String): Int

    /**
     * Emits the proposal IDs whose collection changes [userId] has applied.
     * Observed reactively so the UI updates immediately after [markSynced] is called.
     */
    @Query("SELECT proposal_id FROM trade_collection_sync WHERE user_id = :userId AND pending_apply = 0")
    fun observeSyncedProposalIds(userId: String): Flow<List<String>>

    /** Records the user's choice to update the collection once the trade is COMPLETED. Never downgrades an applied record. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun markPendingApply(entity: TradeCollectionSyncEntity)

    /** Emits the proposal IDs whose collection update [userId] requested but that are not applied yet. */
    @Query("SELECT proposal_id FROM trade_collection_sync WHERE user_id = :userId AND pending_apply = 1")
    fun observePendingApplyProposalIds(userId: String): Flow<List<String>>

    /** Drops a pending-apply request (e.g. the trade was revoked before completion). */
    @Query("DELETE FROM trade_collection_sync WHERE proposal_id = :proposalId AND user_id = :userId AND pending_apply = 1")
    suspend fun clearPendingApply(proposalId: String, userId: String)

    /**
     * Removes the applied sync record for [proposalId] and [userId], used when the user
     * reverses the collection changes of a revoked trade.
     */
    @Query("DELETE FROM trade_collection_sync WHERE proposal_id = :proposalId AND user_id = :userId AND pending_apply = 0")
    suspend fun removeSyncRecord(proposalId: String, userId: String)
}
