package com.mmg.manahub.core.sync

import com.mmg.manahub.core.data.local.SyncPreferencesStore
import com.mmg.manahub.core.data.local.dao.CardDao
import com.mmg.manahub.core.data.local.dao.DeckDao
import com.mmg.manahub.core.data.local.dao.UserCardCollectionDao
import com.mmg.manahub.core.data.local.entity.DeckCardEntity
import com.mmg.manahub.core.data.local.mapper.toEntityCard
import com.mmg.manahub.core.data.remote.ScryfallRemoteDataSource
import com.mmg.manahub.core.data.remote.collection.CollectionRemoteDataSource
import com.mmg.manahub.core.data.remote.collection.UserCardCollectionDto
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
     * Resets the sync state to [SyncState.IDLE]. Call this on logout so that a stale
     * ERROR state from a previous background sync is not shown as fresh on the next
     * app open or login.
     */
    fun resetSyncState() {
        _syncState.value = SyncState.IDLE
    }

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
                    pullCollectionRow(dto) { collectionPulled++ }
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

            // Resolve any offline↔account duplicates BEFORE calling assignUserId so that
            // updating user_id on guest rows cannot violate the composite UNIQUE constraint.
            mergeGuestRowsIntoUser(newUserId)

            val collectionMigrated = collectionDao.assignUserId(newUserId, now)
            val decksMigrated = deckDao.assignDeckUserId(newUserId, now)

            // Count rows that now belong to this user AFTER the migration above.
            val localCollectionCount = collectionDao.getCountForUser(newUserId)
            val localDeckCount = deckDao.getDeckCountForUser(newUserId)

            // Clear the watermark in two scenarios:
            //
            // 1. Guest rows were migrated (collectionMigrated > 0 || decksMigrated > 0):
            //    The watermark must be reset so the PUSH phase re-uploads the migrated rows
            //    and the PULL phase fetches the full account history.
            //
            // 2. Room has no data for this user even though no rows were migrated
            //    (localCollectionCount == 0 && localDeckCount == 0):
            //    This indicates Room was wiped (destructive migration, user cleared app data,
            //    or fresh install on a device where DataStore survived). The DataStore watermark
            //    may still hold a stale timestamp from a previous installation, causing
            //    getChangesSince() to return 0 rows because all Supabase data pre-dates the
            //    watermark. Clearing it forces a full pull and restores the user's data.
            //
            //    Note: if the user genuinely has an empty collection (0 local + 0 remote),
            //    clearing the watermark is harmless — the PULL will simply return 0 rows.
            if (collectionMigrated > 0 || decksMigrated > 0 ||
                (localCollectionCount == 0 && localDeckCount == 0)
            ) {
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
                    pullCollectionRow(dto) { collectionPulled++ }
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
     * Resolves UNIQUE-constraint conflicts between offline (null-userId) rows and the
     * user's existing rows BEFORE [assignUserId] is called.
     *
     * When the same card was added both offline and in a previous logged-in session,
     * calling `assignUserId(X)` would try to UPDATE the null-userId row to userId X,
     * but a row with userId X and the same composite key already exists — this violates
     * the UNIQUE constraint and crashes outside the runCatching safety net.
     *
     * Resolution per-conflict (LWW):
     * - If the offline row is NEWER: copy its quantity and updatedAt into the user row
     *   (the offline addition took precedence — e.g. user added more copies while logged out).
     * - If the user row is NEWER or equal: keep the user row unchanged.
     * - In both cases the null-userId row is hard-deleted to eliminate the duplicate.
     *
     * Non-conflicting null-userId rows are left intact for [assignUserId] to migrate.
     */
    private fun mergeGuestRowsIntoUser(userId: String) {
        val guestRows = collectionDao.getAllGuestRows()
        for (guestRow in guestRows) {
            val userRow = collectionDao.getByCompositeKey(
                userId, guestRow.scryfallId, guestRow.isFoil,
                guestRow.condition, guestRow.language, guestRow.isAlternativeArt,
            )
            if (userRow != null) {
                if (guestRow.updatedAt > userRow.updatedAt) {
                    collectionDao.upsert(
                        userRow.copy(
                            quantity = guestRow.quantity,
                            isForTrade = guestRow.isForTrade || userRow.isForTrade,
                            updatedAt = guestRow.updatedAt,
                        )
                    )
                }
                collectionDao.deleteById(guestRow.id)
            }
        }
    }

    /**
     * Applies Last-Write-Wins (LWW) logic for a single remote collection [dto] and
     * upserts it into Room if it wins.
     *
     * Resolution order:
     * 1. Look up the row by its Supabase UUID (exact match, includes tombstones).
     * 2. If not found, look up by composite key — the same card variant may exist locally
     *    under a different (guest-generated) UUID. When found:
     *    a. If the remote row loses the LWW comparison, skip entirely.
     *    b. Otherwise, hard-delete the stale local row so the Supabase-canonical UUID
     *       can be inserted without violating the composite UNIQUE constraint.
     * 3. Skip if the local row is strictly newer (LWW).
     * 4. Upsert the remote row and invoke [onInserted].
     *
     * Note: the `local` variable after a UUID-mismatch resolution intentionally points
     * to the entity that was just hard-deleted. At that point [dto].updatedAt is always
     * strictly greater than [local].updatedAt (guaranteed by the check in step 2a), so
     * step 3 never incorrectly skips the upsert.
     */
    private fun pullCollectionRow(
        dto: UserCardCollectionDto,
        onInserted: () -> Unit,
    ) {
        val byId = collectionDao.getByIdIncludingDeleted(dto.id)
        // When non-null, this is the stale guest-UUID row that must be removed atomically
        // with the canonical-UUID upsert (see reconcileAndUpsert below).
        var staleId: String? = null

        val local = if (byId != null) {
            byId
        } else {
            // UUID mismatch: the same card variant may exist locally under a
            // different (guest-generated) UUID. Find it by composite key.
            val byComposite = collectionDao.getByCompositeKey(
                dto.userId, dto.scryfallId, dto.isFoil,
                dto.condition, dto.language, dto.isAlternativeArt,
            )
            if (byComposite != null) {
                // LWW: if the local row is strictly newer, skip this remote row entirely.
                if (dto.updatedAt <= byComposite.updatedAt) return
                // Remote row wins — mark the stale row for atomic deletion.
                staleId = byComposite.id
            }
            byComposite
        }
        // LWW: skip if local row is strictly newer. When local came from the UUID-mismatch
        // branch, dto.updatedAt > local.updatedAt is always true here (ensured above).
        if (local != null && dto.updatedAt <= local.updatedAt) return

        // Use the atomic reconcileAndUpsert when a stale-UUID row must be replaced so a
        // process-kill between the delete and the insert never orphans the collection entry.
        if (staleId != null) {
            collectionDao.reconcileAndUpsert(staleId, dto.toEntity())
        } else {
            collectionDao.upsert(dto.toEntity())
        }
        onInserted()
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
     * be skipped this cycle and retried on the next sync.
     *
     * DATA SAFETY: skipping a collection entry when its card cannot be fetched from
     * Scryfall does NOT cause data loss. The row still exists in Supabase and will be
     * pulled again on the next sync cycle (the watermark is only advanced after a
     * fully successful sync). If Scryfall returns 404 for a card indefinitely, the
     * user's ownership record remains safe in Supabase — only the local display is
     * affected until the card becomes available again in the Scryfall catalog.
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
