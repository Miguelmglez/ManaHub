package com.mmg.magicfolder.core.data.repository

import com.mmg.magicfolder.core.data.local.dao.DeckDao
import com.mmg.magicfolder.core.data.local.entity.DeckCardCrossRef
import com.mmg.magicfolder.core.data.local.mapper.toDomainDeck
import com.mmg.magicfolder.core.data.local.mapper.toDomainDeckWithCards
import com.mmg.magicfolder.core.data.local.mapper.toEntity
import com.mmg.magicfolder.core.domain.model.Deck
import com.mmg.magicfolder.core.domain.model.DeckWithCards
import com.mmg.magicfolder.core.domain.repository.CardRepository
import com.mmg.magicfolder.core.domain.repository.DeckRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeckRepositoryImpl @Inject constructor(
    private val deckDao: DeckDao,
    private val cardRepository: CardRepository,
) : DeckRepository {

    override fun observeAllDecks(): Flow<List<Deck>> =
        deckDao.observeAllDecks().map { it.map { d -> d.toDomainDeck() } }

    override fun observeDeckWithCards(deckId: Long): Flow<DeckWithCards?> =
        deckDao.observeDeckWithCards(deckId).map { it?.toDomainDeckWithCards() }

    override suspend fun createDeck(deck: Deck): Long  = deckDao.insertDeck(deck.toEntity())
    override suspend fun updateDeck(deck: Deck)        = deckDao.updateDeck(deck.toEntity())
    override suspend fun deleteDeck(deckId: Long)      = deckDao.deleteDeck(deckId)

    override suspend fun addCardToDeck(
        deckId: Long, scryfallId: String, quantity: Int, isSideboard: Boolean,
    ) {
        // Ensure the card exists in the local 'cards' table to satisfy Foreign Key constraints
        cardRepository.getCardById(scryfallId)
        deckDao.upsertDeckCard(DeckCardCrossRef(deckId, scryfallId, quantity, isSideboard))
    }

    override suspend fun removeCardFromDeck(
        deckId: Long, scryfallId: String, isSideboard: Boolean,
    ) = deckDao.removeDeckCard(deckId, scryfallId, isSideboard)

    override suspend fun clearDeck(deckId: Long) = deckDao.clearDeck(deckId)
}
