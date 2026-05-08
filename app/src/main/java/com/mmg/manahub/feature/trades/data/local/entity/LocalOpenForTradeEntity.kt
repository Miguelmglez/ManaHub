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

    // How many copies of this collection group are offered for trade.
    @ColumnInfo(name = "quantity")
    val quantity: Int = 1,

    // Denormalized card attributes for display on TradesScreen without a JOIN
    // to user_card_collection.
    @ColumnInfo(name = "is_foil")
    val isFoil: Boolean = false,

    @ColumnInfo(name = "condition")
    val condition: String = "NM",

    @ColumnInfo(name = "language")
    val language: String = "en",

    @ColumnInfo(name = "is_alt_art")
    val isAltArt: Boolean = false,

    // 0 = pending cloud migration; 1 = migrated to Supabase after user logs in
    @ColumnInfo(name = "synced")
    val synced: Boolean = false,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
)
