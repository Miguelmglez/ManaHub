package com.mmg.manahub.core.data.local.entity

import androidx.room.*

@Entity(tableName = "decks")
data class DeckEntity(
    @PrimaryKey
    @ColumnInfo(name = "id") val id: String,               // UUID, client-generated
    @ColumnInfo(name = "user_id") val userId: String?,     // null = guest session
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "description") val description: String = "",
    @ColumnInfo(name = "format") val format: String = "casual",
    @ColumnInfo(name = "cover_card_id") val coverCardId: String? = null,
    @ColumnInfo(name = "commander_card_id") val commanderCardId: String? = null,
    @ColumnInfo(name = "is_deleted") val isDeleted: Boolean = false,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
)

/** Cross-reference: which cards belong to which deck (mainboard + sideboard). */
@Entity(
    tableName = "deck_cards",
    primaryKeys = ["deck_id", "scryfall_id", "is_sideboard"],
    foreignKeys = [ForeignKey(
        entity = DeckEntity::class,
        parentColumns = ["id"],
        childColumns = ["deck_id"],
        // CASCADE: deleting a deck removes all its card rows automatically,
        // which is safe because deck_cards has no further dependents.
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("deck_id")]
)
data class DeckCardEntity(
    @ColumnInfo(name = "deck_id") val deckId: String,
    @ColumnInfo(name = "scryfall_id") val scryfallId: String,
    @ColumnInfo(name = "quantity") val quantity: Int = 1,
    @ColumnInfo(name = "is_sideboard") val isSideboard: Boolean = false,
)
