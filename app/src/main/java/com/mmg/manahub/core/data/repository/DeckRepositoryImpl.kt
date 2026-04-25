package com.mmg.manahub.core.data.repository

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mmg.manahub.core.data.local.dao.DeckDao
import com.mmg.manahub.core.data.local.dao.DeckSummaryRow
import com.mmg.manahub.core.data.local.entity.DeckCardEntity
import com.mmg.manahub.core.data.local.entity.DeckEntity
import com.mmg.manahub.core.di.IoDispatcher
import com.mmg.manahub.core.domain.model.Deck
import com.mmg.manahub.core.domain.model.DeckSlot
import com.mmg.manahub.core.domain.model.DeckSummary
import com.mmg.manahub.core.domain.model.DeckWithCards
import com.mmg.manahub.core.domain.repository.DeckRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local-first implementation of [DeckRepository].
 *
 * All mutations write to Room first and bump [DeckEntity.updatedAt].
 * The [com.mmg.manahub.core.sync.SyncManager] detects dirty rows via [updatedAt]
 * and pushes them to Supabase on the next sync cycle.
 *
 * Soft-deletes set [DeckEntity.isDeleted] = true rather than removing the row,
 * so deletions are propagated to Supabase on the next push.
 *
 * RPCs have been removed entirely from this class — they are SyncManager's responsibility.
 */
@Singleton
class DeckRepositoryImpl @Inject constructor(
    private val deckDao: DeckDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : DeckRepository {

    private val gson = Gson()
    private val listType = object : TypeToken<List<String>>() {}.type

    // ── Observables ───────────────────────────────────────────────────────────

    override fun observeAllDecks(): Flow<List<Deck>> =
        deckDao.observeAllDecks(null).map { entities ->
            entities.filter { !it.isDeleted }.map { it.toDomain() }
        }

    override fun observeAllDeckSummaries(): Flow<List<DeckSummary>> =
        deckDao.observeDeckSummaryRows().map { rows ->
            rows.groupBy { it.deckId }
                .values
                .sortedByDescending { it.first().updatedAt }
                .map { deckRows ->
                    buildDeckSummary(deckRows)
                }
        }

    override fun observeDecksContainingCard(scryfallId: String): Flow<List<Deck>> =
        deckDao.observeDecksContainingCard(scryfallId).map { entities ->
            entities.filter { !it.isDeleted }.map { it.toDomain() }
        }

    override fun observeDeckWithCards(deckId: String): Flow<DeckWithCards?> =
        deckDao.observeDeckWithCards(deckId).map { entity ->
            entity?.let {
                DeckWithCards(
                    deck = it.deck.toDomain(),
                    mainboard = it.cards.filter { c -> !c.isSideboard }
                        .map { c -> DeckSlot(c.scryfallId, c.quantity) },
                    sideboard = it.cards.filter { c -> c.isSideboard }
                        .map { c -> DeckSlot(c.scryfallId, c.quantity) },
                )
            }
        }

    // ── Mutations ─────────────────────────────────────────────────────────────

    override suspend fun createDeck(
        name: String,
        description: String,
        format: String,
    ): String = withContext(ioDispatcher) {
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        deckDao.upsertDeck(
            DeckEntity(
                id = id,
                userId = null,          // SyncManager will assign userId on login
                name = name,
                description = description,
                format = format,
                coverCardId = null,
                commanderCardId = null,
                isDeleted = false,
                updatedAt = now,
                createdAt = now,
            )
        )
        id
    }

    override suspend fun updateDeck(deck: Deck) = withContext(ioDispatcher) {
        val existing = deckDao.getDeckByIdForSync(deck.id) ?: return@withContext
        deckDao.upsertDeck(
            existing.copy(
                name = deck.name,
                description = deck.description,
                format = deck.format,
                coverCardId = deck.coverCardId,
                commanderCardId = deck.commanderCardId,
                isDeleted = false,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    override suspend fun deleteDeck(deckId: String) = withContext(ioDispatcher) {
        // Soft delete — the row stays so SyncManager can push the deletion to Supabase.
        deckDao.softDeleteDeck(deckId, System.currentTimeMillis())
    }

    override suspend fun addCardToDeck(
        deckId: String,
        scryfallId: String,
        quantity: Int,
        isSideboard: Boolean,
    ) {
        withContext(ioDispatcher) {
            deckDao.upsertDeckCard(
                DeckCardEntity(
                    deckId = deckId,
                    scryfallId = scryfallId,
                    quantity = quantity,
                    isSideboard = isSideboard,
                )
            )
            deckDao.getDeckById(deckId)?.let { deck ->
                deckDao.upsertDeck(deck.copy(updatedAt = System.currentTimeMillis()))
            }
        }
    }

    override suspend fun removeCardFromDeck(
        deckId: String,
        scryfallId: String,
        isSideboard: Boolean,
    ) {
        withContext(ioDispatcher) {
            deckDao.removeDeckCard(deckId, scryfallId, isSideboard)
            deckDao.getDeckById(deckId)?.let { deck ->
                deckDao.upsertDeck(deck.copy(updatedAt = System.currentTimeMillis()))
            }
        }
    }

    override suspend fun clearDeck(deckId: String) {
        withContext(ioDispatcher) {
            deckDao.clearDeckCards(deckId)
            deckDao.getDeckById(deckId)?.let { deck ->
                deckDao.upsertDeck(deck.copy(updatedAt = System.currentTimeMillis()))
            }
        }
    }

    override suspend fun replaceAllCards(deckId: String, slots: List<Triple<String, Int, Boolean>>) {
        withContext(ioDispatcher) {
            val entities = slots.map { (scryfallId, quantity, isSideboard) ->
                DeckCardEntity(deckId = deckId, scryfallId = scryfallId, quantity = quantity, isSideboard = isSideboard)
            }
            deckDao.replaceAllCards(deckId, entities)
            deckDao.getDeckById(deckId)?.let { deck ->
                deckDao.upsertDeck(deck.copy(updatedAt = System.currentTimeMillis()))
            }
        }
    }

    // ── Mapping helpers ───────────────────────────────────────────────────────

    private fun DeckEntity.toDomain(): Deck = Deck(
        id = id,
        name = name,
        description = description,
        format = format,
        coverCardId = coverCardId,
        commanderCardId = commanderCardId,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun buildDeckSummary(deckRows: List<DeckSummaryRow>): DeckSummary {
        val first = deckRows.first()
        val mainboard = deckRows.filter { it.scryfallId != null && !it.isSideboard }
        val cardCount = mainboard.sumOf { it.quantity }
        val colorIdentity = deckRows
            .mapNotNull { it.colorIdentity }
            .flatMap { jsonStr ->
                runCatching { gson.fromJson<List<String>>(jsonStr, listType) ?: emptyList() }
                    .getOrDefault(emptyList())
            }
            .toSet()
        val coverImageUrl = first.coverCardId
            ?.let { coverId -> deckRows.firstOrNull { it.scryfallId == coverId }?.imageArtCrop }
            ?: mainboard.firstOrNull()?.imageArtCrop

        return DeckSummary(
            id = first.deckId,
            name = first.name,
            description = first.description,
            format = first.format,
            coverCardId = first.coverCardId,
            createdAt = first.createdAt,
            updatedAt = first.updatedAt,
            cardCount = cardCount,
            colorIdentity = colorIdentity,
            coverImageUrl = coverImageUrl,
        )
    }
}
