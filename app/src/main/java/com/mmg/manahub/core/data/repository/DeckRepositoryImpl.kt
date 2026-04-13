package com.mmg.manahub.core.data.repository

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mmg.manahub.core.data.local.dao.DeckDao
import com.mmg.manahub.core.data.local.entity.DeckCardCrossRef
import com.mmg.manahub.core.data.local.mapper.toDomainDeck
import com.mmg.manahub.core.data.local.mapper.toDomainDeckWithCards
import com.mmg.manahub.core.data.local.mapper.toEntity
import com.mmg.manahub.core.domain.model.Deck
import com.mmg.manahub.core.domain.model.DeckSummary
import com.mmg.manahub.core.domain.model.DeckWithCards
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.di.IoDispatcher
import com.mmg.manahub.core.domain.repository.DeckRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeckRepositoryImpl @Inject constructor(
    private val deckDao: DeckDao,
    private val cardRepository: CardRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : DeckRepository {

    private val gson     = Gson()
    private val listType = object : TypeToken<List<String>>() {}.type

    override fun observeAllDecks(): Flow<List<Deck>> =
        deckDao.observeAllDecks().map { it.map { d -> d.toDomainDeck() } }

    override fun observeDecksContainingCard(scryfallId: String): Flow<List<Deck>> =
        deckDao.observeDecksContainingCard(scryfallId).map { it.map { d -> d.toDomainDeck() } }

    override fun observeAllDeckSummaries(): Flow<List<DeckSummary>> =
        deckDao.observeDeckSummaryRows().map { rows ->
            rows.groupBy { it.deckId }
                .values
                .sortedByDescending { it.first().updatedAt }
                .map { deckRows ->
                    val first        = deckRows.first()
                    val mainboard    = deckRows.filter { it.scryfallId != null && !it.isSideboard }
                    val cardCount    = mainboard.sumOf { it.quantity }
                    val colorIdentity = deckRows
                        .mapNotNull { it.colorIdentity }
                        .flatMap { json ->
                            runCatching { gson.fromJson<List<String>>(json, listType) ?: emptyList() }
                                .getOrDefault(emptyList())
                        }
                        .toSet()
                    val coverImageUrl = first.coverCardId
                        ?.let { coverId -> deckRows.firstOrNull { it.scryfallId == coverId }?.imageArtCrop }
                        ?: mainboard.firstOrNull()?.imageArtCrop
                    DeckSummary(
                        id            = first.deckId,
                        name          = first.name,
                        description   = first.description,
                        format        = first.format,
                        coverCardId   = first.coverCardId,
                        createdAt     = first.createdAt,
                        updatedAt     = first.updatedAt,
                        cardCount     = cardCount,
                        colorIdentity = colorIdentity,
                        coverImageUrl = coverImageUrl,
                    )
                }
        }

    override fun observeDeckWithCards(deckId: Long): Flow<DeckWithCards?> =
        deckDao.observeDeckWithCards(deckId).map { it?.toDomainDeckWithCards() }

    override suspend fun createDeck(deck: Deck): Long = withContext(ioDispatcher) {
        deckDao.insertDeck(deck.toEntity())
    }

    override suspend fun updateDeck(deck: Deck) = withContext(ioDispatcher) {
        deckDao.updateDeck(deck.toEntity())
    }

    override suspend fun deleteDeck(deckId: Long) = withContext(ioDispatcher) {
        deckDao.deleteDeck(deckId)
    }

    override suspend fun addCardToDeck(
        deckId: Long, scryfallId: String, quantity: Int, isSideboard: Boolean,
    ) = withContext(ioDispatcher) {
        // Ensure the card exists in the local 'cards' table to satisfy the FK constraint
        // on deck_cards.scryfall_id → cards.scryfall_id (RESTRICT).
        // If the card cannot be fetched (network error and no cache), abort early instead
        // of letting the FK constraint throw a SQLiteConstraintException at the DAO level.
        val result = cardRepository.getCardById(scryfallId)
        if (result is com.mmg.manahub.core.domain.model.DataResult.Error) return@withContext
        deckDao.upsertDeckCard(DeckCardCrossRef(deckId, scryfallId, quantity, isSideboard))
    }

    override suspend fun removeCardFromDeck(
        deckId: Long, scryfallId: String, isSideboard: Boolean,
    ) = withContext(ioDispatcher) {
        deckDao.removeDeckCard(deckId, scryfallId, isSideboard)
    }

    override suspend fun clearDeck(deckId: Long) = withContext(ioDispatcher) {
        deckDao.clearDeck(deckId)
    }
}
