package com.mmg.manahub.core.data.repository

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mmg.manahub.core.data.local.dao.DeckDao
import com.mmg.manahub.core.data.local.entity.DeckCardCrossRef
import com.mmg.manahub.core.data.local.entity.SyncStatus
import com.mmg.manahub.core.data.local.mapper.toDomainDeck
import com.mmg.manahub.core.data.local.mapper.toDomainDeckWithCards
import com.mmg.manahub.core.data.local.mapper.toEntity
import com.mmg.manahub.core.data.remote.decks.DeckCardDto
import com.mmg.manahub.core.data.remote.decks.DeckRemoteDataSource
import com.mmg.manahub.core.data.remote.decks.toUpsertParams
import com.mmg.manahub.core.domain.model.Deck
import com.mmg.manahub.core.domain.model.DeckSummary
import com.mmg.manahub.core.domain.model.DeckWithCards
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.di.IoDispatcher
import com.mmg.manahub.core.domain.repository.DeckRepository
import io.github.jan.supabase.auth.Auth
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeckRepositoryImpl @Inject constructor(
    private val deckDao:          DeckDao,
    private val cardRepository:   CardRepository,
    private val remoteDataSource: DeckRemoteDataSource,
    private val supabaseAuth:     Auth,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : DeckRepository {

    private val gson     = Gson()
    private val listType = object : TypeToken<List<String>>() {}.type

    // Serialises concurrent sync calls for the same session so that two coroutines
    // (e.g. syncAllDirtyDecks from DeckViewModel.init and syncDeckNow from onNavigatingBack)
    // don't race on the TOCTOU check of sync_status.
    private val syncMutex = Mutex()

    private fun userId(): String? = supabaseAuth.currentUserOrNull()?.id

    // ── Observables ───────────────────────────────────────────────────────────

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

    // ── Mutations (local-first; sync is handled separately) ───────────────────

    override suspend fun createDeck(deck: Deck): Long = withContext(ioDispatcher) {
        // sync_status defaults to PENDING_UPLOAD in the entity
        deckDao.insertDeck(deck.toEntity())
    }

    override suspend fun updateDeck(deck: Deck) = withContext(ioDispatcher) {
        // Abort if the deck was deleted concurrently — avoids clearing remoteId.
        val current = deckDao.getDeckById(deck.id) ?: return@withContext
        deckDao.updateDeck(
            deck.toEntity().copy(
                syncStatus = SyncStatus.PENDING_UPLOAD,
                remoteId   = current.remoteId,   // preserve the Supabase UUID
                updatedAt  = System.currentTimeMillis(),
            )
        )
    }

    override suspend fun deleteDeck(deckId: Long) = withContext(ioDispatcher) {
        deckDao.deleteDeck(deckId)
        // Best-effort soft-delete in Supabase. The RPC uses auth.uid() + local_id so
        // it no-ops safely if the deck was never uploaded.
        if (userId() != null) {
            runCatching { remoteDataSource.deleteDeck(deckId) }
        }
    }

    override suspend fun addCardToDeck(
        deckId: Long, scryfallId: String, quantity: Int, isSideboard: Boolean,
    ) = withContext(ioDispatcher) {
        val result = cardRepository.getCardById(scryfallId)
        if (result is com.mmg.manahub.core.domain.model.DataResult.Error) return@withContext
        deckDao.upsertDeckCard(DeckCardCrossRef(deckId, scryfallId, quantity, isSideboard))
        deckDao.markDeckDirty(deckId, SyncStatus.PENDING_UPLOAD, System.currentTimeMillis())
    }

    override suspend fun removeCardFromDeck(
        deckId: Long, scryfallId: String, isSideboard: Boolean,
    ) = withContext(ioDispatcher) {
        deckDao.removeDeckCard(deckId, scryfallId, isSideboard)
        deckDao.markDeckDirty(deckId, SyncStatus.PENDING_UPLOAD, System.currentTimeMillis())
    }

    override suspend fun clearDeck(deckId: Long) = withContext(ioDispatcher) {
        deckDao.clearDeck(deckId)
        deckDao.markDeckDirty(deckId, SyncStatus.PENDING_UPLOAD, System.currentTimeMillis())
    }

    // ── Sync ──────────────────────────────────────────────────────────────────

    override suspend fun syncDeckNow(deckId: Long) = withContext(ioDispatcher) {
        if (userId() == null) return@withContext

        // Mutex prevents TOCTOU race: if two coroutines both see PENDING_UPLOAD before
        // either writes SYNCED, only the first inside the lock proceeds; the second
        // re-reads SYNCED and returns early.
        syncMutex.withLock {
            val entity = deckDao.getDeckById(deckId) ?: return@withLock
            if (entity.syncStatus == SyncStatus.SYNCED) return@withLock

            // Push deck metadata; upsert_deck returns the Supabase UUID.
            val remoteId = remoteDataSource.upsertDeck(entity.toUpsertParams())
                .getOrNull()
                ?.takeIf { it.isNotEmpty() }   // guard: empty string = RPC returned nothing
                ?: return@withLock

            // Push all cards atomically. If this fails, sync_status stays PENDING_UPLOAD
            // and the next syncAllDirtyDecks call will retry the full push.
            val cards = deckDao.getDeckCards(deckId).map {
                DeckCardDto(it.scryfallId, it.quantity, it.isSideboard)
            }
            val cardsResult = remoteDataSource.upsertDeckCards(deckId, cards)
            if (cardsResult.isFailure) return@withLock

            deckDao.updateSyncStatusAndRemoteId(deckId, SyncStatus.SYNCED, remoteId)
        }
    }

    override suspend fun syncAllDirtyDecks() = withContext(ioDispatcher) {
        if (userId() == null) return@withContext
        // Snapshot of pending decks at call time. Any deck dirtied after this point
        // will be picked up on the next syncAllDirtyDecks invocation.
        deckDao.getPendingUploadDecks().forEach { entity ->
            runCatching { syncDeckNow(entity.id) }
        }
    }

    override suspend fun pullIfStale() = withContext(ioDispatcher) {
        if (userId() == null) return@withContext

        val remoteLastModified = remoteDataSource.getLastModified() ?: return@withContext
        val localMaxUpdatedAt  = deckDao.getMaxUpdatedAt()

        // First-launch guard: if there are no local decks, skip the pull entirely.
        // There is nothing to merge and downloading potentially hundreds of remote decks
        // to then discard them (no local remoteId match) wastes bandwidth.
        if (localMaxUpdatedAt == null) return@withContext

        val localLastModified = Instant.ofEpochMilli(localMaxUpdatedAt)
        if (!remoteLastModified.isAfter(localLastModified)) return@withContext

        val remoteDeck = remoteDataSource.getDecksChangedSince(localLastModified)
            .getOrNull() ?: return@withContext

        // Update existing local decks that have a matching remote_id.
        // Decks only present on the remote (no local counterpart) are skipped —
        // cross-device pull / multi-device support is a future iteration.
        remoteDeck.filter { !it.isDeleted }.forEach { dto ->
            val remoteId    = dto.id ?: return@forEach
            val localEntity = deckDao.getDeckByRemoteId(remoteId) ?: return@forEach
            val remoteUpdatedAt = dto.updatedAt
                ?.let { runCatching { Instant.parse(it) }.getOrNull() }
                ?.toEpochMilli() ?: return@forEach
            // Only overwrite if remote timestamp is strictly newer than local
            if (remoteUpdatedAt <= localEntity.updatedAt) return@forEach
            deckDao.updateDeck(
                localEntity.copy(
                    name        = dto.name,
                    description = dto.description,
                    format      = dto.format,
                    coverCardId = dto.coverCardId,
                    updatedAt   = remoteUpdatedAt,
                    syncStatus  = SyncStatus.SYNCED,
                )
            )
        }
    }
}
