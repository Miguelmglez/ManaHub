package com.mmg.manahub.feature.trades.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "local_wishlists",
    indices = [Index("scryfall_id")],
)
data class LocalWishlistEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "scryfall_id")
    val scryfallId: String,

    @ColumnInfo(name = "quantity")
    val quantity: Int = 1,

    // When true the trade matches any printing/condition/language variant of the card.
    // When false the narrowing fields below are consulted.
    @ColumnInfo(name = "match_any_variant")
    val matchAnyVariant: Boolean = true,

    @ColumnInfo(name = "is_foil")
    val isFoil: Boolean?,

    @ColumnInfo(name = "condition")
    val condition: String?,

    @ColumnInfo(name = "language")
    val language: String?,

    @ColumnInfo(name = "is_alt_art")
    val isAltArt: Boolean?,

    // 0 = pending cloud migration; 1 = migrated to Supabase after user logs in
    @ColumnInfo(name = "synced")
    val synced: Boolean = false,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
)
