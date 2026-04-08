package com.mmg.magicfolder.core.data.repository

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mmg.magicfolder.core.data.local.dao.DeckDao
import com.mmg.magicfolder.core.data.local.entity.DeckCardCrossRef
import com.mmg.magicfolder.core.data.local.mapper.toDomainDeck
import com.mmg.magicfolder.core.data.local.mapper.toDomainDeckWithCards
import com.mmg.magicfolder.core.data.local.mapper.toEntity
import com.mmg.magicfolder.core.domain.model.Deck
import com.mmg.magicfolder.core.domain.model.DeckSummary
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
