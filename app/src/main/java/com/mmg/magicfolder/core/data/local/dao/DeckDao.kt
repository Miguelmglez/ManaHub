package com.mmg.magicfolder.core.data.local.dao

import androidx.room.*
import com.mmg.magicfolder.core.data.local.entity.*
import kotlinx.coroutines.flow.Flow

/** Flat row returned by the deck-summary JOIN query — one row per deck×card. */
data class DeckSummaryRow(
    @ColumnInfo(name = "deckId")        val deckId:        Long,
    @ColumnInfo(name = "name")          val name:          String,
    @ColumnInfo(name = "format")        val format:        String,
    @ColumnInfo(name = "description")   val description:   String?,
    @ColumnInfo(name = "coverCardId")   val coverCardId:   String?,
    @ColumnInfo(name = "createdAt")     val createdAt:     Long,
    @ColumnInfo(name = "updatedAt")     val updatedAt:     Long,
    @ColumnInfo(name = "scryfallId")    val scryfallId:    String?,
    @ColumnInfo(name = "isSideboard")   val isSideboard:   Boolean,
    @ColumnInfo(name = "quantity")      val quantity:      Int,
    @ColumnInfo(name = "colorIdentity") val colorIdentity: String?,
    @ColumnInfo(name = "imageArtCrop")  val imageArtCrop:  String?,
)

@Dao
interface DeckDao {

    @Insert
    suspend fun insertDeck(deck: DeckEntity): Long

    @Update
    suspend fun updateDeck(deck: DeckEntity)

    @Query("DELETE FROM decks WHERE id = :deckId")
    suspend fun deleteDeck(deckId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDeckCard(ref: DeckCardCrossRef)

    @Query("""
        DELETE FROM deck_cards
        WHERE deck_id = :deckId AND scryfall_id = :scryfallId AND is_sideboard = :isSideboard
    """)
    suspend fun removeDeckCard(deckId: Long, scryfallId: String, isSideboard: Boolean)

    @Query("DELETE FROM deck_cards WHERE deck_id = :deckId")
    suspend fun clearDeck(deckId: Long)

    @Query("SELECT * FROM decks ORDER BY updated_at DESC")
    fun observeAllDecks(): Flow<List<DeckEntity>>

    @Query("""
        SELECT d.id            AS deckId,
               d.name          AS name,
               d.format        AS format,
               d.description   AS description,
               d.cover_card_id AS coverCardId,
               d.created_at    AS createdAt,
               d.updated_at    AS updatedAt,
               dc.scryfall_id  AS scryfallId,
               COALESCE(dc.is_sideboard, 0) AS isSideboard,
               COALESCE(dc.quantity, 0)     AS quantity,
               c.color_identity AS colorIdentity,
               c.image_art_crop AS imageArtCrop
        FROM decks d
        LEFT JOIN deck_cards dc ON d.id = dc.deck_id
        LEFT JOIN cards c ON dc.scryfall_id = c.scryfall_id
        ORDER BY d.updated_at DESC
    """)
    fun observeDeckSummaryRows(): Flow<List<DeckSummaryRow>>

    @Query("SELECT COUNT(*) FROM decks")
    fun observeDeckCount(): Flow<Int>

    @Query("""
        SELECT d.* FROM decks d
        INNER JOIN deck_cards dc ON d.id = dc.deck_id
        WHERE dc.scryfall_id = :scryfallId
        ORDER BY d.updated_at DESC
    """)
    fun observeDecksContainingCard(scryfallId: String): Flow<List<DeckEntity>>

    @Transaction
    @Query("SELECT * FROM decks WHERE id = :deckId")
    fun observeDeckWithCards(deckId: Long): Flow<DeckWithCards?>
}

data class DeckWithCards(
    @Embedded val deck: DeckEntity,
    @Relation(parentColumn = "id", entityColumn = "deck_id", entity = DeckCardCrossRef::class)
    val cards: List<DeckCardCrossRef>
)
