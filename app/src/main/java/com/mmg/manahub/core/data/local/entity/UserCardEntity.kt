package com.mmg.manahub.core.data.local.entity

import androidx.room.*

@Entity(
    tableName = "user_cards",
    foreignKeys = [ForeignKey(
        entity        = CardEntity::class,
        parentColumns = ["scryfall_id"],
        childColumns  = ["scryfall_id"],
        // RESTRICT: any attempt to DELETE a card that is still referenced in the
        // user's collection will throw a SQLiteConstraintException immediately,
        // making data loss loud and explicit instead of silent (CASCADE would
        // delete the user's collection entries without warning).
        onDelete      = ForeignKey.RESTRICT,
    )],
    indices = [
        Index("scryfall_id"),
        Index("is_for_trade"),
        Index("is_in_wishlist"),
        // Prevents duplicate logical entries for the same physical card variant.
        // is_in_wishlist is included so the same copy can exist in both collection
        // (is_in_wishlist = 0) and wishlist (is_in_wishlist = 1) simultaneously.
        Index(value = ["scryfall_id", "is_foil", "condition", "language", "is_alternative_art", "is_in_wishlist"], unique = true),
    ],
)
data class UserCardEntity(

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id") val id: Long = 0,

    @ColumnInfo(name = "scryfall_id")       val scryfallId:      String,
    @ColumnInfo(name = "quantity")          val quantity:        Int     = 1,
    @ColumnInfo(name = "is_foil")           val isFoil:          Boolean = false,
    @ColumnInfo(name = "is_alternative_art") val isAlternativeArt: Boolean = false,
    // Condition: NM | LP | MP | HP | DMG
    @ColumnInfo(name = "condition")         val condition:       String  = "NM",
    // Language ISO: en | ja | de | fr | es | pt | it | ko | ru | zhs | zht
    @ColumnInfo(name = "language")          val language:        String  = "en",

    @ColumnInfo(name = "is_for_trade")    val isForTrade:    Boolean = false,
    @ColumnInfo(name = "is_in_wishlist")  val isInWishlist:  Boolean = false,
    @ColumnInfo(name = "min_trade_value") val minTradeValue: Double? = null,

    @ColumnInfo(name = "notes")       val notes:      String? = null,
    @ColumnInfo(name = "acquired_at") val acquiredAt: Long?   = null,
    @ColumnInfo(name = "added_at")    val addedAt:    Long    = System.currentTimeMillis(),
)
