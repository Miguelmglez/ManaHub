package com.mmg.magicfolder.code.core.data.local.dao

import androidx.room.*
import com.mmg.magicfolder.code.core.data.local.entity.*
import kotlinx.coroutines.flow.Flow

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
    fun observeAllDecks(): Flow>

    @Query("SELECT COUNT(*) FROM decks")
    fun observeDeckCount(): Flow

    @Transaction
    @Query("SELECT * FROM decks WHERE id = :deckId")
    fun observeDeckWithCards(deckId: Long): Flow
}

data class DeckWithCards(
    @Embedded val deck: DeckEntity,
    @Relation(parentColumn = "id", entityColumn = "deck_id", entity = DeckCardCrossRef::class)
    val cards: List,
)