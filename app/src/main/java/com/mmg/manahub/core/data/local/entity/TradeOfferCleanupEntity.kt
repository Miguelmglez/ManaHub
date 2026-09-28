package com.mmg.manahub.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/** A committed trade's remote offer deletion that still needs confirmation. */
@Entity(
    tableName = "trade_offer_cleanup",
    primaryKeys = ["proposal_id", "user_id", "collection_id"],
    indices = [Index("user_id")],
)
data class TradeOfferCleanupEntity(
    @ColumnInfo(name = "proposal_id") val proposalId: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "collection_id") val collectionId: String,
)
