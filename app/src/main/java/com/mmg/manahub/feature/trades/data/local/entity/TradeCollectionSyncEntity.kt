package com.mmg.manahub.feature.trades.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

/**
 * Tracks whether the current device user has already run "Update Collection"
 * for a specific [TradeProposal].
 *
 * The composite primary key (proposal_id, user_id) ensures one record per user
 * per proposal. [OnConflictStrategy.IGNORE] in the DAO prevents double-syncs.
 */
@Entity(
    tableName = "trade_collection_sync",
    primaryKeys = ["proposal_id", "user_id"],
)
data class TradeCollectionSyncEntity(
    @ColumnInfo(name = "proposal_id") val proposalId: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "synced_at") val syncedAt: Long = System.currentTimeMillis(),
)
