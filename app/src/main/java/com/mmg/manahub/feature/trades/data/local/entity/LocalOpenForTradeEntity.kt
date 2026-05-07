package com.mmg.manahub.feature.trades.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "local_open_for_trade",
    indices = [
        Index("local_collection_id"),
        Index("scryfall_id"),
    ],
)
data class LocalOpenForTradeEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    // Soft FK to user_card_collection.id — not enforced so guest users without
    // a synced collection can still populate this table.
    @ColumnInfo(name = "local_collection_id")
    val localCollectionId: String,

    // Denormalized to allow display without a JOIN when the collection row may be absent.
    @ColumnInfo(name = "scryfall_id")
    val scryfallId: String,

    // 0 = pending cloud migration; 1 = migrated to Supabase after user logs in
    @ColumnInfo(name = "synced")
    val synced: Boolean = false,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
)
