package core.domain.repository

import core.domain.model.Deck
import core.domain.model.DeckWithCards
import kotlinx.coroutines.flow.Flow

interface DeckRepository {
    fun observeAllDecks(): Flow<List<Deck>>
    fun observeDeckWithCards(deckId: Long): Flow<DeckWithCards?>
    suspend fun createDeck(deck: Deck): Long
    suspend fun updateDeck(deck: Deck)
    suspend fun deleteDeck(deckId: Long)
    suspend fun addCardToDeck(deckId: Long, scryfallId: String, quantity: Int = 1, isSideboard: Boolean = false)
    suspend fun removeCardFromDeck(deckId: Long, scryfallId: String, isSideboard: Boolean)
    suspend fun clearDeck(deckId: Long)
}