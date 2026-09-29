package com.mmg.manahub.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

/** Latest server quantity required for a wishlist row changed by a committed trade. */
@Entity(tableName = "trade_wishlist_cleanup", primaryKeys = ["user_id", "wishlist_id"])
data class TradeWishlistCleanupEntity(
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "wishlist_id") val wishlistId: String,
    @ColumnInfo(name = "target_quantity") val targetQuantity: Int,
)
