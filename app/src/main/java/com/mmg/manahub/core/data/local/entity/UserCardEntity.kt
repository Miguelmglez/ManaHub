package com.mmg.manahub.core.data.local.entity

import androidx.room.*

@Entity(
    tableName = "user_card_collection",
    foreignKeys = [ForeignKey(
        entity = CardEntity::class,
        parentColumns = ["scryfall_id"],
        childColumns = ["scryfall_id"],
        // RESTRICT: any attempt to DELETE a card still referenced in the user's
        // collection will throw SQLiteConstraintException immediately, making data
        // loss loud and explicit instead of silent.
        onDelete = ForeignKey.RESTRICT,
    )],
    indices = [
        Index("scryfall_id"),
        Index("user_id"),
        Index("updated_at"),
        Index("is_deleted"),
        // Composite unique key mirrors the Supabase unique constraint so that the same
        // physical card variant (foil, condition, language, art variant) cannot be
        // inserted twice for the same user.
        Index(
            value = ["user_id", "scryfall_id", "is_foil", "condition", "language", "is_alternative_art"],
            unique = true
        ),
    ]
)
data class UserCardCollectionEntity(
    @PrimaryKey
    @ColumnInfo(name = "id") val id: String,                               // UUID, client-generated
    @ColumnInfo(name = "user_id") val userId: String?,                     // null = guest session
    @ColumnInfo(name = "scryfall_id") val scryfallId: String,
    @ColumnInfo(name = "quantity") val quantity: Int = 1,
    @ColumnInfo(name = "is_foil") val isFoil: Boolean = false,
    @ColumnInfo(name = "condition") val condition: String = "NM",          // NM | LP | MP | HP | DMG
    @ColumnInfo(name = "language") val language: String = "en",            // ISO: en | ja | de | …
    @ColumnInfo(name = "is_alternative_art") val isAlternativeArt: Boolean = false,
    @ColumnInfo(name = "is_for_trade") val isForTrade: Boolean = false,
    @ColumnInfo(name = "is_in_wishlist") val isInWishlist: Boolean = false,
    @ColumnInfo(name = "is_deleted") val isDeleted: Boolean = false,       // soft-delete flag
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
)
