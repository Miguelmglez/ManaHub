package core.data.repository

import core.data.local.dao.DeckDao
import core.data.local.entity.DeckCardCrossRef
import core.data.local.mapper.toDomainDeck
import core.data.local.mapper.toDomainDeckWithCards
import core.data.local.mapper.toEntity
import core.domain.model.Deck
import core.domain.model.DeckWithCards
import core.domain.repository.DeckRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeckRepositoryImpl @Inject constructor(private val deckDao: DeckDao) : DeckRepository {

    override fun observeAllDecks(): Flow<List<Deck>> =
        deckDao.observeAllDecks().map { it.map { d -> d.toDomainDeck() } }

    override fun observeDeckWithCards(deckId: Long): Flow<DeckWithCards?> =
        deckDao.observeDeckWithCards(deckId).map { it?.toDomainDeckWithCards() }

    override suspend fun createDeck(deck: Deck): Long  = deckDao.insertDeck(deck.toEntity())
    override suspend fun updateDeck(deck: Deck)        = deckDao.updateDeck(deck.toEntity())
    override suspend fun deleteDeck(deckId: Long)      = deckDao.deleteDeck(deckId)

    override suspend fun addCardToDeck(
        deckId: Long, scryfallId: String, quantity: Int, isSideboard: Boolean,
    ) = deckDao.upsertDeckCard(DeckCardCrossRef(deckId, scryfallId, quantity, isSideboard))

    override suspend fun removeCardFromDeck(
        deckId: Long, scryfallId: String, isSideboard: Boolean,
    ) = deckDao.removeDeckCard(deckId, scryfallId, isSideboard)

    override suspend fun clearDeck(deckId: Long) = deckDao.clearDeck(deckId)
}
