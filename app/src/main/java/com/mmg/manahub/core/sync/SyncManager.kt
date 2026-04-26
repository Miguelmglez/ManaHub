package com.mmg.manahub.core.sync

import com.mmg.manahub.core.data.local.SyncPreferencesStore
import com.mmg.manahub.core.data.local.dao.CardDao
import com.mmg.manahub.core.data.local.dao.UserCardCollectionDao
import com.mmg.manahub.core.data.local.dao.DeckDao
import com.mmg.manahub.core.data.local.entity.DeckCardEntity
import com.mmg.manahub.core.data.local.mapper.toEntityCard
import com.mmg.manahub.core.data.remote.ScryfallRemoteDataSource
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
    private val cardDao: CardDao,
    private val collectionRemote: CollectionRemoteDataSource,
    private val deckRemote: DeckRemoteDataSource,
    private val scryfallRemote: ScryfallRemoteDataSource,
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
     *   - Pre-fetches any cards missing from Room to satisfy FK constraints.
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

                // Snapshot the clock before issuing the PULL RPCs. Any row written to
                // Supabase between this instant and when getChangesSince() resolves would
                // have server_updatedAt >= syncStartTime. Saving syncStartTime (not
                // System.currentTimeMillis() at the END) as the new watermark ensures those
                // rows are re-fetched on the next cycle rather than permanently skipped.
                val syncStartTime = System.currentTimeMillis()

                // ── PULL: collection ─────────────────────────────────────────────

                val remoteCollection = collectionRemote.getChangesSince(lastSync).getOrThrow()
                // Pre-fetch any cards missing from Room before upserting collection rows.
                // UserCardCollectionEntity has a FK to CardEntity (RESTRICT), so the card
                // must exist in Room before we can insert a collection entry that references it.
                val cachedIds = ensureCardsExist(remoteCollection.map { it.scryfallId }.distinct())
                var collectionPulled = 0
                for (dto in remoteCollection) {
                    if (dto.scryfallId !in cachedIds) continue
                    val byId = collectionDao.getByIdIncludingDeleted(dto.id)
                    val local = if (byId != null) {
                        byId
                    } else {
                        // UUID mismatch: the same card variant may exist locally under a
                        // different (guest-generated) UUID. Find it by composite key and
                        // hard-delete it so we can insert with Supabase's canonical UUID.
                        val byComposite = collectionDao.getByCompositeKey(
                            dto.userId, dto.scryfallId, dto.isFoil,
                            dto.condition, dto.language, dto.isAlternativeArt,
                        )
                        if (byComposite != null) {
                            if (dto.updatedAt <= byComposite.updatedAt) continue
                            collectionDao.deleteById(byComposite.id)
                        }
                        byComposite
                    }
                    if (local != null && dto.updatedAt <= local.updatedAt) continue
                    collectionDao.upsert(dto.toEntity())
                    collectionPulled++
                }

                // ── PULL: decks ──────────────────────────────────────────────────

                val remoteDecks = deckRemote.getDeckChangesSince(lastSync).getOrThrow()
                var decksPulled = 0
                for (dto in remoteDecks) {
                    // getDeckByIdForSync includes soft-deleted rows (same tombstone fix as above).
                    val local = deckDao.getDeckByIdForSync(dto.id)
                    // LWW: skip if local row is strictly newer.
                    if (local != null && dto.updatedAt <= local.updatedAt) continue
                    deckDao.upsertDeck(dto.toEntity())
                    // Also replace card slots so the pulled deck is fully usable on this device.
                    if (!dto.isDeleted) {
                        val remoteCards = deckRemote.getDeckCardsForDeck(dto.id).getOrThrow()
                        // Pre-fetch any cards missing from Room so deck images resolve correctly.
                        // deck_cards has a FK to cards (scryfallId), and coverImageUrl is derived
                        // from a JOIN — missing card rows silently produce null images.
                        val deckCardIds = remoteCards.map { it.scryfallId }.distinct()
                        val cachedDeckIds = ensureCardsExist(deckCardIds)
                        deckDao.replaceAllCards(
                            dto.id,
                            remoteCards
                                .filter { it.scryfallId in cachedDeckIds }
                                .map {
                                    DeckCardEntity(
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

                // Use syncStartTime (snapshotted before the PULL) so rows written to
                // Supabase while our PULL was in-flight are caught by the next sync cycle.
                syncPrefs.saveLastSyncMillis(userId, syncStartTime)

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
     * Assigns [newUserId] to all guest rows (null userId) in Room, pushes them to
     * Supabase, then pulls the account's existing remote data and merges via LWW.
     *
     * This is the entry point for the offline-to-online transition: when a guest
     * creates local data and then logs in, all their local rows must be uploaded
     * and the account's existing Supabase data must be pulled and merged.
     *
     * Safe to call even if there are no orphaned rows — in that case it runs a
     * normal full sync without clearing the watermark.
     *
     * IMPORTANT: the watermark is saved only AFTER both push AND pull complete so
     * that a subsequent incremental sync() does not re-fetch what we just pulled.
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

                // ── PUSH: collection ─────────────────────────────────────────────

                val localCollection = collectionDao.getAllSince(newUserId, lastSync)
                    .filter { it.userId?.isNotEmpty() == true }
                var collectionPushed = 0
                if (localCollection.isNotEmpty()) {
                    collectionRemote.batchUpsert(localCollection.map { it.toDto() }).getOrThrow()
                    collectionPushed = localCollection.size
                }

                // ── PUSH: decks ──────────────────────────────────────────────────

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

                val syncStartTime = System.currentTimeMillis()

                // ── PULL: collection ─────────────────────────────────────────────
                // Must happen AFTER push so that the server already has the local
                // rows and the LWW comparison below sees the merged state.

                val remoteCollection = collectionRemote.getChangesSince(lastSync).getOrThrow()
                val cachedIds = ensureCardsExist(remoteCollection.map { it.scryfallId }.distinct())
                var collectionPulled = 0
                for (dto in remoteCollection) {
                    if (dto.scryfallId !in cachedIds) continue
                    val byId = collectionDao.getByIdIncludingDeleted(dto.id)
                    val local = if (byId != null) {
                        byId
                    } else {
                        val byComposite = collectionDao.getByCompositeKey(
                            dto.userId, dto.scryfallId, dto.isFoil,
                            dto.condition, dto.language, dto.isAlternativeArt,
                        )
                        if (byComposite != null) {
                            if (dto.updatedAt <= byComposite.updatedAt) continue
                            collectionDao.deleteById(byComposite.id)
                        }
                        byComposite
                    }
                    if (local != null && dto.updatedAt <= local.updatedAt) continue
                    collectionDao.upsert(dto.toEntity())
                    collectionPulled++
                }

                // ── PULL: decks ──────────────────────────────────────────────────

                val remoteDecks = deckRemote.getDeckChangesSince(lastSync).getOrThrow()
                var decksPulled = 0
                for (dto in remoteDecks) {
                    val local = deckDao.getDeckByIdForSync(dto.id)
                    if (local != null && dto.updatedAt <= local.updatedAt) continue
                    deckDao.upsertDeck(dto.toEntity())
                    if (!dto.isDeleted) {
                        val remoteCards = deckRemote.getDeckCardsForDeck(dto.id).getOrThrow()
                        val deckCardIds = remoteCards.map { it.scryfallId }.distinct()
                        val cachedDeckIds = ensureCardsExist(deckCardIds)
                        deckDao.replaceAllCards(
                            dto.id,
                            remoteCards
                                .filter { it.scryfallId in cachedDeckIds }
                                .map {
                                    DeckCardEntity(
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

                syncPrefs.saveLastSyncMillis(newUserId, syncStartTime)

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
     * Ensures all [scryfallIds] are present in Room's [CardEntity] table.
     *
     * This is required before upserting [UserCardCollectionEntity] rows because the
     * table has a RESTRICT FK to [CardEntity]. When pulling from Supabase, the
     * referenced cards may not exist locally yet (e.g. first install, new device,
     * or cards added from another device).
     *
     * Strategy:
     * 1. Query Room for which IDs already exist.
     * 2. Batch-fetch the missing ones from the Scryfall /cards/collection endpoint
     *    (75 IDs per request, rate-limited via [ScryfallRemoteDataSource]).
     * 3. Upsert fetched cards into Room.
     *
     * Returns the set of [scryfallIds] that are now present in Room (either
     * pre-existing or successfully fetched). IDs that couldn't be fetched (Scryfall
     * 404 or network error for a chunk) are excluded — their collection entries will
     * be silently skipped and retried on the next sync.
     */
    private suspend fun ensureCardsExist(scryfallIds: List<String>): Set<String> {
        if (scryfallIds.isEmpty()) return emptySet()

        val existingIds = cardDao.getByIds(scryfallIds).map { it.scryfallId }.toMutableSet()
        val missingIds = scryfallIds.filterNot { it in existingIds }
        if (missingIds.isEmpty()) return existingIds

        // Fetch in chunks of 75 (Scryfall /cards/collection hard limit).
        missingIds.chunked(75).forEach { chunk ->
            scryfallRemote.getCardsBatch(chunk)
                .onSuccess { cards ->
                    cards.forEach { card ->
                        runCatching { cardDao.upsert(card.toEntityCard()) }
                            .onSuccess { existingIds.add(card.scryfallId) }
                    }
                }
            // If a chunk fails (network error, Scryfall down), we silently skip it.
            // Those cards won't be in existingIds and their collection entries will
            // be skipped this cycle and retried on the next sync.
        }

        return existingIds
    }
}
