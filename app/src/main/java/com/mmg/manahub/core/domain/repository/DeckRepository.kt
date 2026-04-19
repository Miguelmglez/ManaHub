package com.mmg.manahub.core.domain.repository

import com.mmg.manahub.core.domain.model.Deck
import com.mmg.manahub.core.domain.model.DeckSummary
import com.mmg.manahub.core.domain.model.DeckWithCards
import kotlinx.coroutines.flow.Flow

interface DeckRepository {
    fun observeAllDecks(): Flow<List<Deck>>
    fun observeAllDeckSummaries(): Flow<List<DeckSummary>>
    fun observeDecksContainingCard(scryfallId: String): Flow<List<Deck>>
    fun observeDeckWithCards(deckId: Long): Flow<DeckWithCards?>
    suspend fun createDeck(deck: Deck): Long
    suspend fun updateDeck(deck: Deck)
    suspend fun deleteDeck(deckId: Long)
    suspend fun addCardToDeck(deckId: Long, scryfallId: String, quantity: Int = 1, isSideboard: Boolean = false)
    suspend fun removeCardFromDeck(deckId: Long, scryfallId: String, isSideboard: Boolean)
    suspend fun clearDeck(deckId: Long)

    /** Push a single deck (metadata + cards) to Supabase if it has pending local changes. */
    suspend fun syncDeckNow(deckId: Long)

    /** Push all decks with sync_status = PENDING_UPLOAD to Supabase. */
    suspend fun syncAllDirtyDecks()

    /**
     * Checks whether the remote deck catalogue is newer than the local one (lightweight
     * timestamp comparison) and pulls incremental changes if so.
     */
    suspend fun pullIfStale()
}