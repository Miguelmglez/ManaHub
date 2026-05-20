package com.mmg.manahub.feature.trades.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mmg.manahub.feature.trades.data.local.entity.TradeCollectionSyncEntity
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
     * Records that [entity.userId] has completed a collection sync for [entity.proposalId].
     * [OnConflictStrategy.IGNORE] prevents double-inserts from re-triggering any side effects.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun markSynced(entity: TradeCollectionSyncEntity)

    /**
     * Returns a count > 0 if [userId] has already synced [proposalId].
     * Use `isSynced(...) > 0` to test for the synced state.
     */
    @Query("SELECT COUNT(*) FROM trade_collection_sync WHERE proposal_id = :proposalId AND user_id = :userId")
    suspend fun isSynced(proposalId: String, userId: String): Int

    /**
     * Emits the set of proposal IDs that [userId] has already synced.
     * Observed reactively so the UI updates immediately after [markSynced] is called.
     */
    @Query("SELECT proposal_id FROM trade_collection_sync WHERE user_id = :userId")
    fun observeSyncedProposalIds(userId: String): Flow<List<String>>

    /**
     * Removes the sync record for [proposalId] and [userId], used when the user
     * revokes a trade and reverses the collection changes.
     */
    @Query("DELETE FROM trade_collection_sync WHERE proposal_id = :proposalId AND user_id = :userId")
    suspend fun removeSyncRecord(proposalId: String, userId: String)
}
