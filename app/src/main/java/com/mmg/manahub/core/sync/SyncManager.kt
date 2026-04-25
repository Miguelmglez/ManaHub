package com.mmg.manahub.core.sync

import com.mmg.manahub.core.data.local.SyncPreferencesStore
import com.mmg.manahub.core.data.local.dao.UserCardCollectionDao
import com.mmg.manahub.core.data.local.dao.DeckDao
import com.mmg.manahub.core.data.remote.collection.CollectionRemoteDataSource
import com.mmg.manahub.core.data.remote.collection.toDto
import com.mmg.manahub.core.data.remote.collection.toEntity
import com.mmg.manahub.core.data.remote.decks.DeckCardSyncDto
import com.mmg.manahub.core.data.remote.decks.DeckRemoteDataSource
import com.mmg.manahub.core.data.remote.decks.toDto
import com.mmg.manahub.core.data.remote.decks.toEntity
import com.mmg.manahub.core.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Represents the current state of a sync operation. */
enum class SyncState { IDLE, SYNCING, SUCCESS, ERROR }

/**
 * Holds the outcome of a completed sync cycle.
 *
 * @property state Final [SyncState] after the operation.
 * @property error Human-readable error message when [state] is [SyncState.ERROR].
 * @property collectionPushed Number of collection rows pushed to Supabase.
 * @property collectionPulled Number of collection rows pulled from Supabase.
 * @property decksPushed Number of deck rows pushed to Supabase.
 * @property decksPulled Number of deck rows pulled from Supabase.
 */
data class SyncResult(
    val state: SyncState,
    val error: String? = null,
    val collectionPushed: Int = 0,
    val collectionPulled: Int = 0,
    val decksPushed: Int = 0,
    val decksPulled: Int = 0,
)

/**
 * Central orchestrator for bidirectional Room ↔ Supabase sync.
 *
 * Strategy: Last-Write-Wins (LWW) based on [updatedAt] epoch millis.
 * A [Mutex] prevents concurrent sync runs so that push and pull phases
 * are always executed as a single atomic pair.
 *
 * The [sync] function handles both collection entries and decks in a single call.
 * Offline-to-online transition is handled by [assignUserIdAndSync].
 */
@Singleton
class SyncManager @Inject constructor(
    private val collectionDao: UserCardCollectionDao,
    private val deckDao: DeckDao,
    private val collectionRemote: CollectionRemoteDataSource,
    private val deckRemote: DeckRemoteDataSource,
    private val syncPrefs: SyncPreferencesStore,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    private val syncMutex = Mutex()

    private val _syncState = MutableStateFlow(SyncState.IDLE)

    /** Observable sync state for UI consumption. */
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    /**
     * Runs a full push-then-pull sync cycle for [userId].
     *
     * Phase 1 — PUSH:
     *   - Reads local rows where [updatedAt] > lastSyncMillis.
     *   - Batch-upserts them to Supabase.
     *
     * Phase 2 — PULL:
     *   - Fetches remote rows changed since lastSyncMillis.
     *   - Applies LWW: skips rows where remote.updatedAt <= local.updatedAt.
     *   - Upserts winning remote rows into Room.
     *
     * Phase 3 — Saves the current timestamp as the new watermark.
     *
     * The [Mutex] prevents a second concurrent call from starting while this one
     * is in progress. If already locked, the second call waits and then executes.
     */
    suspend fun sync(userId: String): SyncResult = withContext(ioDispatcher) {
        syncMutex.withLock {
            _syncState.value = SyncState.SYNCING
            runCatching {
                val lastSync = syncPrefs.getLastSyncMillis(userId)

                // ── PUSH: collection ─────────────────────────────────────────────

                val localCollection = collectionDao.getAllSince(userId, lastSync)
                var collectionPushed = 0
                if (localCollection.isNotEmpty()) {
                    collectionRemote.batchUpsert(localCollection.map { it.toDto() }).getOrThrow()
                    collectionPushed = localCollection.size
                }

                // ── PUSH: decks ──────────────────────────────────────────────────

                // Only push rows that have a real userId — orphaned (null) rows must be
                // migrated first via assignUserIdAndSync before they are safe to push.
                val localDecks = deckDao.getDecksSince(userId, lastSync)
                    .filter { it.userId?.isNotEmpty() == true }
                var decksPushed = 0
                if (localDecks.isNotEmpty()) {
                    deckRemote.batchUpsertDecks(localDecks.map { it.toDto() }).getOrThrow()
                    // Push card slots for each dirty deck.
                    for (deck in localDecks) {
                        val cards = deckDao.getDeckCards(deck.id).map { card ->
                            DeckCardSyncDto(
                                scryfallId = card.scryfallId,
                                quantity = card.quantity,
                                isSideboard = card.isSideboard,
                            )
                        }
                        deckRemote.upsertDeckCards(deck.id, cards).getOrThrow()
                    }
                    decksPushed = localDecks.size
                }

                // ── PULL: collection ─────────────────────────────────────────────

                val remoteCollection = collectionRemote.getChangesSince(lastSync).getOrThrow()
                var collectionPulled = 0
                for (dto in remoteCollection) {
                    val local = collectionDao.getById(dto.id)
                    // LWW: skip if local row is strictly newer (or equal — local wins ties).
                    if (local != null && dto.updatedAt <= local.updatedAt) continue
                    collectionDao.upsert(dto.toEntity())
                    collectionPulled++
                }

                // ── PULL: decks ──────────────────────────────────────────────────

                val remoteDecks = deckRemote.getDeckChangesSince(lastSync).getOrThrow()
                var decksPulled = 0
                for (dto in remoteDecks) {
                    // Use getDeckByIdForSync so local soft-deletes are visible and not
                    // falsely overwritten by an older remote row (bug: LWW tombstone reversal).
                    val local = deckDao.getDeckByIdForSync(dto.id)
                    // LWW: skip if local row is strictly newer.
                    if (local != null && dto.updatedAt <= local.updatedAt) continue
                    deckDao.upsertDeck(dto.toEntity())
                    // Also replace card slots so the pulled deck is fully usable on this device.
                    if (!dto.isDeleted) {
                        val remoteCards = deckRemote.getDeckCardsForDeck(dto.id).getOrThrow()
                        deckDao.replaceAllCards(
                            dto.id,
                            remoteCards.map {
                                com.mmg.manahub.core.data.local.entity.DeckCardEntity(
                                    deckId = dto.id,
                                    scryfallId = it.scryfallId,
                                    quantity = it.quantity,
                                    isSideboard = it.isSideboard,
                                )
                            }
                        )
                    }
                    decksPulled++
                }

                // ── COMMIT: save watermark ───────────────────────────────────────

                syncPrefs.saveLastSyncMillis(userId, System.currentTimeMillis())

                SyncResult(
                    state = SyncState.SUCCESS,
                    collectionPushed = collectionPushed,
                    collectionPulled = collectionPulled,
                    decksPushed = decksPushed,
                    decksPulled = decksPulled,
                )
            }.getOrElse { error ->
                SyncResult(state = SyncState.ERROR, error = error.message)
            }.also { result ->
                _syncState.value = result.state
            }
        }
    }

    /**
     * Assigns [newUserId] to all guest rows (null userId) in Room, then forces
     * a full upload by clearing the last-sync watermark before calling [sync].
     *
     * This is the entry point for the offline-to-online transition: when a guest
     * creates local data and then logs in, all their local rows must be uploaded
     * and associated with their new account.
     *
     * Safe to call even if there are no orphaned rows — in that case it runs a
     * normal incremental sync without clearing the watermark.
     */
    suspend fun assignUserIdAndSync(newUserId: String): SyncResult = withContext(ioDispatcher) {
        // Protected by the same mutex as sync() to prevent concurrent execution.
        syncMutex.withLock {
            val now = System.currentTimeMillis()

            val collectionMigrated = collectionDao.assignUserId(newUserId, now)
            val decksMigrated = deckDao.assignDeckUserId(newUserId, now)

            // Only clear the watermark when there were orphaned rows to migrate.
            // Avoids forcing an unnecessary full re-upload on every app startup for
            // users who are already authenticated.
            if (collectionMigrated > 0 || decksMigrated > 0) {
                syncPrefs.clearLastSyncMillis(newUserId)
            }

            _syncState.value = SyncState.SYNCING
            runCatching {
                val lastSync = syncPrefs.getLastSyncMillis(newUserId)

                val localCollection = collectionDao.getAllSince(newUserId, lastSync)
                    .filter { it.userId?.isNotEmpty() == true }
                var collectionPushed = 0
                if (localCollection.isNotEmpty()) {
                    collectionRemote.batchUpsert(localCollection.map { it.toDto() }).getOrThrow()
                    collectionPushed = localCollection.size
                }

                val localDecks = deckDao.getDecksSince(newUserId, lastSync)
                    .filter { it.userId?.isNotEmpty() == true }
                var decksPushed = 0
                if (localDecks.isNotEmpty()) {
                    deckRemote.batchUpsertDecks(localDecks.map { it.toDto() }).getOrThrow()
                    for (deck in localDecks) {
                        val cards = deckDao.getDeckCards(deck.id).map { card ->
                            DeckCardSyncDto(
                                scryfallId = card.scryfallId,
                                quantity = card.quantity,
                                isSideboard = card.isSideboard,
                            )
                        }
                        deckRemote.upsertDeckCards(deck.id, cards).getOrThrow()
                    }
                    decksPushed = localDecks.size
                }

                syncPrefs.saveLastSyncMillis(newUserId, System.currentTimeMillis())

                SyncResult(
                    state = SyncState.SUCCESS,
                    collectionPushed = collectionPushed,
                    decksPushed = decksPushed,
                )
            }.getOrElse { error ->
                SyncResult(state = SyncState.ERROR, error = error.message)
            }.also { result ->
                _syncState.value = result.state
            }
        }
    }
}
