package com.mmg.magicfolder.code.core.data.local.entity


import androidx.room.*

@Entity(tableName = "decks")
data class DeckEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id") val id: Long = 0,
    @ColumnInfo(name = "name")          val name:        String,
    @ColumnInfo(name = "description")   val description: String? = null,
    @ColumnInfo(name = "format")        val format:      String  = "casual",
    @ColumnInfo(name = "cover_card_id") val coverCardId: String? = null,
    @ColumnInfo(name = "created_at")    val createdAt:   Long    = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at")    val updatedAt:   Long    = System.currentTimeMillis(),
)

/** Cross-reference: which cards belong to which deck (mainboard + sideboard). */
@Entity(
    tableName = "deck_cards",
    primaryKeys = ["deck_id", "scryfall_id", "is_sideboard"],
    foreignKeys = [
        ForeignKey(
            entity        = DeckEntity::class,
            parentColumns = ["id"],
            childColumns  = ["deck_id"],
            onDelete      = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity        = CardEntity::class,
            parentColumns = ["scryfall_id"],
            childColumns  = ["scryfall_id"],
            onDelete      = ForeignKey.RESTRICT,  // don't delete a card used in a deck
        ),
    ],
    indices = [Index("deck_id"), Index("scryfall_id")],
)
data class DeckCardCrossRef(
    @ColumnInfo(name = "deck_id")      val deckId:      Long,
    @ColumnInfo(name = "scryfall_id")  val scryfallId:  String,
    @ColumnInfo(name = "quantity")     val quantity:    Int     = 1,
    @ColumnInfo(name = "is_sideboard") val isSideboard: Boolean = false,
)