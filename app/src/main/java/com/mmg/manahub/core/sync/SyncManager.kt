package com.mmg.manahub.core.sync

import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.data.local.SyncPreferencesStore
import com.mmg.manahub.core.data.local.dao.CardDao
import com.mmg.manahub.core.data.local.dao.DeckDao
import com.mmg.manahub.core.data.local.dao.UserCardCollectionDao
import com.mmg.manahub.core.data.local.entity.CardEntity
import com.mmg.manahub.core.data.local.entity.DeckCardEntity
import com.mmg.manahub.core.data.local.mapper.toEntityCard
import com.mmg.manahub.core.data.remote.ScryfallRemoteDataSource
import com.mmg.manahub.core.data.remote.collection.CollectionRemoteDataSource
import com.mmg.manahub.core.data.remote.collection.UserCardCollectionDto
import com.mmg.manahub.core.data.remote.collection.toDto
import com.mmg.manahub.core.data.remote.collection.toEntity
import com.mmg.manahub.core.data.remote.decks.DeckRemoteDataSource
import com.mmg.manahub.core.data.remote.decks.DeckSyncDto
import com.mmg.manahub.core.data.remote.decks.toDto
import com.mmg.manahub.core.data.remote.decks.toEntity
import com.mmg.manahub.core.data.remote.decks.toSyncDto
import com.mmg.manahub.core.data.sync.SERVER_PAGE_CAP
import com.mmg.manahub.core.data.sync.drainPages
import com.mmg.manahub.core.di.IoDispatcher
import kotlinx.coroutines.CancellationException
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
 *
 * ## Collection sync data-loss fix (`linear-moseying-yeti` plan, Phase 3)
 * The PULL phase used to call `get_collection_changes_since`/`get_deck_changes_since`, which have
 * no `LIMIT` — PostgREST silently truncates the response at `db-max-rows` (1000) with no signal to
 * the client. A user with more than 1000 changed rows since their watermark had the excess rows
 * permanently stranded: the (then-unconditional) watermark save advanced past `updated_at` values
 * that were never actually delivered. Both PULL phases now drain the keyset-paginated
 * `get_collection_changes_page`/`get_deck_changes_page` RPCs via [drainPages] (server-capped at
 * 500 rows/page), looping until a page returns fewer than [PAGE_SIZE] rows.
 *
 * **Safe watermark rule** (the core of the fix): the new watermark is
 * `min(syncStartTime, minUnappliedUpdatedAt - 1).coerceAtLeast(lastSync)`, where
 * `minUnappliedUpdatedAt` is the lowest across BOTH drains' [com.mmg.manahub.core.data.sync
 * .PageDrainResult.minUnappliedUpdatedAt] — `Long.MAX_VALUE` when a drain fully completed with
 * every row applied, otherwise the `updatedAt` of the last row that WAS safely applied before a
 * page-fetch or per-row apply failure stopped that drain. The watermark can therefore never move
 * past data this cycle did not actually persist, and `coerceAtLeast(lastSync)` guarantees it never
 * regresses below the previously-committed watermark either.
 *
 * The `if (dto.scryfallId !in cachedIds) continue` gate that used to skip (and thereby permanently
 * strand, via the watermark) a collection row whose card metadata couldn't be fetched from
 * Scryfall is GONE — [ensureCardsExist] is now a best-effort metadata warm-up whose result no
 * longer gates insertion; it writes a `stale_reason = "pending_hydration"` placeholder
 * [CardEntity] for anything Scryfall didn't return (Phase 4), so the collection row ALWAYS
 * inserts. [CardHydrationWorker] resolves placeholders in the background. Deck card slots still
 * filter by the fetched/placeholder-backed id set as defence in depth (unchanged from before).
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
    private val crashReporter: CrashReporter,
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
     * Returns the number of local collection rows that have been modified since the
     * last successful sync watermark for [userId].
     *
     * A count > 0 means there are local changes not yet pushed to Supabase and the
     * "Sync your collection" banner should be shown to the user.
     *
     * Note: this query includes soft-deleted (tombstone) rows because those also need
     * to be pushed so that deletions propagate to Supabase.
     */
    suspend fun countPendingChanges(userId: String): Int = withContext(ioDispatcher) {
        val lastSync = syncPrefs.getLastSyncMillis(userId)
        collectionDao.countPendingSync(userId, lastSync)
    }

    /**
     * Runs a full push-then-pull sync cycle for [userId].
     *
     * Phase 1 — PUSH: reads local rows where [updatedAt] > lastSyncMillis and pushes them to
     * Supabase in chunks (collection rows sliced to [PUSH_CHUNK_SIZE] so one oversized `jsonb`
     * payload cannot time out the whole cycle).
     *
     * Phase 2 — PULL: drains every page of the remote changes since lastSyncMillis (see class
     * KDoc), applying LWW per row.
     *
     * Phase 3 — Saves the SAFE watermark (see class KDoc).
     *
     * The [Mutex] prevents a second concurrent call from starting while this one
     * is in progress. If already locked, the second call waits and then executes.
     *
     * A [CancellationException] thrown by any suspend call inside this cycle propagates to the
     * caller as a real cancellation — it is never downgraded to [SyncResult] with
     * [SyncState.ERROR].
     */
    suspend fun sync(userId: String): SyncResult = withContext(ioDispatcher) {
        syncMutex.withLock {
            _syncState.value = SyncState.SYNCING
            try {
                runCatching {
                    val lastSync = syncPrefs.getLastSyncMillis(userId)

                    // ── PUSH: collection ─────────────────────────────────────────────

                    val localCollection = collectionDao.getAllSince(userId, lastSync)
                    var collectionPushed = 0
                    localCollection.chunked(PUSH_CHUNK_SIZE).forEach { chunk ->
                        collectionRemote.batchUpsert(chunk.map { it.toDto() }).getOrThrow()
                        collectionPushed += chunk.size
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
                            // Deck Engine Unification (D4): .toSyncDto() carries provenance
                            // (source) through the push -- see DeckSyncDto.kt.
                            val cards = deckDao.getDeckCards(deck.id).map { card -> card.toSyncDto() }
                            deckRemote.upsertDeckCards(deck.id, cards).getOrThrow()
                        }
                        decksPushed = localDecks.size
                    }

                    // Snapshot the clock before issuing the PULL RPCs. Any row written to
                    // Supabase between this instant and when the drain resolves would have
                    // server_updatedAt >= syncStartTime. The safe-watermark formula below never
                    // lets the saved watermark exceed this snapshot, so such a row is caught on
                    // the next sync cycle rather than permanently skipped.
                    val syncStartTime = System.currentTimeMillis()

                    // ── PULL: collection + decks (paginated, see class KDoc) ─────────

                    var collectionPagesAttempted = 0
                    var collectionPulled = 0
                    var collectionUnappliedRows = 0
                    val collectionDrain = drainPages(
                        since = lastSync,
                        limit = PAGE_SIZE,
                        fetchPage = { after, afterId, limit ->
                            collectionPagesAttempted++
                            collectionRemote.getChangesPage(lastSync, after, afterId, limit)
                                .onFailure { e ->
                                    if (e !is CancellationException) {
                                        crashReporter.apply {
                                            log("collection_pull_page_failed")
                                            setCustomKey("sync_pages", collectionPagesAttempted.toString())
                                            setCustomKey("sync_error_type", e::class.simpleName ?: "Unknown")
                                        }
                                    }
                                }
                        },
                        onPage = { page ->
                            applyCollectionPage(page, { collectionPulled++ }, { collectionUnappliedRows++ })
                        },
                    )
                    crashReporter.apply {
                        log("collection_pull_paged_completed")
                        setCustomKey("sync_pages", collectionDrain.pagesDrained.toString())
                        setCustomKey("sync_rows", collectionPulled.toString())
                    }
                    if (collectionUnappliedRows > 0) {
                        crashReporter.apply {
                            log("collection_pull_rows_unapplied")
                            setCustomKey("sync_unapplied_count", collectionUnappliedRows.toString())
                        }
                    }

                    var decksPulled = 0
                    val deckDrain = drainPages(
                        since = lastSync,
                        limit = PAGE_SIZE,
                        fetchPage = { after, afterId, limit ->
                            deckRemote.getDeckChangesPage(lastSync, after, afterId, limit)
                        },
                        onPage = { page -> applyDeckPage(page) { decksPulled++ } },
                    )

                    // ── COMMIT: save the SAFE watermark ──────────────────────────────

                    val minUnapplied = minOf(
                        collectionDrain.minUnappliedUpdatedAt,
                        deckDrain.minUnappliedUpdatedAt,
                    )
                    val newWatermark = minOf(syncStartTime, minUnapplied - 1).coerceAtLeast(lastSync)
                    syncPrefs.saveLastSyncMillis(userId, newWatermark)

                    SyncResult(
                        state = SyncState.SUCCESS,
                        collectionPushed = collectionPushed,
                        collectionPulled = collectionPulled,
                        decksPushed = decksPushed,
                        decksPulled = decksPulled,
                    )
                }.getOrElse { error ->
                    if (error is CancellationException) throw error
                    crashReporter.apply {
                        log("sync_failed: userId=$userId")
                        setCustomKey("sync_error_type", error::class.simpleName ?: "Unknown")
                        recordException(error)
                    }
                    SyncResult(state = SyncState.ERROR, error = error.message)
                }.also { result ->
                    _syncState.value = result.state
                    if (result.state == SyncState.SUCCESS) {
                        // Best-effort integrity self-check (Phase 6) -- never lets a transient
                        // RPC failure here downgrade an already-successful sync to ERROR.
                        runCatching { checkCollectionIntegrity(userId) }
                            .onFailure { e -> if (e is CancellationException) throw e }
                    }
                }
            } catch (e: CancellationException) {
                _syncState.value = SyncState.IDLE
                throw e
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
     *
     * See [sync]'s class-level KDoc for the paginated-pull + safe-watermark contract, which is
     * identical here. A [CancellationException] propagates the same way as in [sync].
     */
    suspend fun assignUserIdAndSync(newUserId: String): SyncResult = withContext(ioDispatcher) {
        // Protected by the same mutex as sync() to prevent concurrent execution.
        syncMutex.withLock {
            _syncState.value = SyncState.SYNCING
            try {
                runCatching {
                    val now = System.currentTimeMillis()

                    // assignUserId's own NOT EXISTS guard (UserCardDao) parks a colliding guest
                    // row (user_id stays NULL) instead of throwing -- CollectionMergeConflictResolver
                    // surfaces it. This call now runs INSIDE the runCatching boundary (write-path
                    // hardening audit, 2026-09-06): it used to run before _syncState was even set
                    // to SYNCING, so any unexpected constraint failure here escaped uncaught to
                    // CollectionSyncWorker, leaving _syncState stuck at IDLE with no ERROR ever
                    // surfaced to the UI.
                    val collectionMigrated = collectionDao.assignUserId(newUserId, now)
                    val decksMigrated = deckDao.assignDeckUserId(newUserId, now)

                    val localCollectionCount = collectionDao.getCountForUser(newUserId)
                    val localDeckCount = deckDao.getDeckCountForUser(newUserId)

                    // Clear the watermark when rows were migrated (PUSH must re-upload them and
                    // PULL must fetch the full account history), OR when Room has no data at all
                    // for this user despite no migration (a wiped Room DB with a stale DataStore
                    // watermark would otherwise return 0 rows forever). Harmless no-op if the user
                    // genuinely has an empty collection.
                    if (collectionMigrated > 0 || decksMigrated > 0 ||
                        (localCollectionCount == 0 && localDeckCount == 0)
                    ) {
                        syncPrefs.clearLastSyncMillis(newUserId)
                    }

                    val lastSync = syncPrefs.getLastSyncMillis(newUserId)

                    // ── PUSH: collection ─────────────────────────────────────────────

                    val localCollection = collectionDao.getAllSince(newUserId, lastSync)
                        .filter { it.userId?.isNotEmpty() == true }
                    var collectionPushed = 0
                    localCollection.chunked(PUSH_CHUNK_SIZE).forEach { chunk ->
                        collectionRemote.batchUpsert(chunk.map { it.toDto() }).getOrThrow()
                        collectionPushed += chunk.size
                    }

                    // ── PUSH: decks ──────────────────────────────────────────────────

                    val localDecks = deckDao.getDecksSince(newUserId, lastSync)
                        .filter { it.userId?.isNotEmpty() == true }
                    var decksPushed = 0
                    if (localDecks.isNotEmpty()) {
                        deckRemote.batchUpsertDecks(localDecks.map { it.toDto() }).getOrThrow()
                        for (deck in localDecks) {
                            // Deck Engine Unification (D4): .toSyncDto() carries provenance
                            // (source) through the push -- see DeckSyncDto.kt.
                            val cards = deckDao.getDeckCards(deck.id).map { card -> card.toSyncDto() }
                            deckRemote.upsertDeckCards(deck.id, cards).getOrThrow()
                        }
                        decksPushed = localDecks.size
                    }

                    val syncStartTime = System.currentTimeMillis()

                    // ── PULL: collection + decks (paginated, see class KDoc) ─────────
                    // Must happen AFTER push so that the server already has the local rows and
                    // the LWW comparison below sees the merged state.

                    var collectionPulled = 0
                    val collectionDrain = drainPages(
                        since = lastSync,
                        limit = PAGE_SIZE,
                        fetchPage = { after, afterId, limit ->
                            collectionRemote.getChangesPage(lastSync, after, afterId, limit)
                        },
                        onPage = { page ->
                            applyCollectionPage(page, onInserted = { collectionPulled++ }, onRowFailed = {})
                        },
                    )

                    var decksPulled = 0
                    val deckDrain = drainPages(
                        since = lastSync,
                        limit = PAGE_SIZE,
                        fetchPage = { after, afterId, limit ->
                            deckRemote.getDeckChangesPage(lastSync, after, afterId, limit)
                        },
                        onPage = { page -> applyDeckPage(page) { decksPulled++ } },
                    )

                    // ── COMMIT: save the SAFE watermark ──────────────────────────────

                    val minUnapplied = minOf(
                        collectionDrain.minUnappliedUpdatedAt,
                        deckDrain.minUnappliedUpdatedAt,
                    )
                    val newWatermark = minOf(syncStartTime, minUnapplied - 1).coerceAtLeast(lastSync)
                    syncPrefs.saveLastSyncMillis(newUserId, newWatermark)

                    SyncResult(
                        state = SyncState.SUCCESS,
                        collectionPushed = collectionPushed,
                        collectionPulled = collectionPulled,
                        decksPushed = decksPushed,
                        decksPulled = decksPulled,
                    )
                }.getOrElse { error ->
                    if (error is CancellationException) throw error
                    crashReporter.apply {
                        log("assign_user_sync_failed: userId=$newUserId")
                        setCustomKey("sync_error_type", error::class.simpleName ?: "Unknown")
                        recordException(error)
                    }
                    SyncResult(state = SyncState.ERROR, error = error.message)
                }.also { result ->
                    _syncState.value = result.state
                }
            } catch (e: CancellationException) {
                _syncState.value = SyncState.IDLE
                throw e
            }
        }
    }

    /**
     * Applies one page of collection rows: warms the card cache for the page (writing a
     * pending-hydration placeholder for anything Scryfall doesn't return — see
     * [ensureCardsExist]) then applies each row via LWW ([pullCollectionRow]).
     *
     * @return `true` if every row in [page] applied without throwing, `false` if at least one
     *   did — the caller ([drainPages]) uses this to decide whether the drain may keep advancing
     *   past this page's rows for watermark purposes.
     */
    private suspend fun applyCollectionPage(
        page: List<UserCardCollectionDto>,
        onInserted: () -> Unit,
        onRowFailed: () -> Unit,
    ): Boolean {
        // Best-effort metadata warm-up. Its result is intentionally unused for gating — every
        // row below always inserts, whether Scryfall resolved it this cycle or not (see class
        // KDoc). Placeholders make the ownership record visible to the UI immediately.
        ensureCardsExist(page.map { it.scryfallId }.distinct())

        var allApplied = true
        for (dto in page) {
            runCatching { pullCollectionRow(dto, onInserted) }
                .onFailure { e ->
                    allApplied = false
                    onRowFailed()
                    crashReporter.apply {
                        setCustomKey("sync_error_type", e::class.simpleName ?: "Unknown")
                        recordException(RuntimeException("[sync] pullCollectionRow failed, id=${dto.id}", e))
                    }
                }
        }
        return allApplied
    }

    /**
     * Applies one page of deck rows via LWW ([pullDeckRow]).
     *
     * @return `true` if every row in [page] applied without throwing (including its card-slot
     *   replacement), `false` if at least one did not.
     */
    private suspend fun applyDeckPage(
        page: List<DeckSyncDto>,
        onInserted: () -> Unit,
    ): Boolean {
        var allApplied = true
        for (dto in page) {
            runCatching { pullDeckRow(dto, onInserted) }
                .onFailure { e ->
                    allApplied = false
                    crashReporter.apply {
                        setCustomKey("sync_error_type", e::class.simpleName ?: "Unknown")
                        recordException(RuntimeException("[sync] pullDeckRow failed, id=${dto.id}", e))
                    }
                }
        }
        return allApplied
    }

    /**
     * Applies Last-Write-Wins (LWW) logic for a single remote collection [dto] and
     * upserts it into Room if it wins.
     *
     * Resolution order:
     * 1. Look up the row by its Supabase UUID (exact match, includes tombstones) — this is
     *    [dto]'s own lineage on this device, if any.
     * 2. Independently look up whichever row (ANY id, live or tombstoned) currently occupies the
     *    TARGET tuple `(userId, scryfallId, isFoil, condition, language)`. This catches two
     *    distinct scenarios with the SAME mechanism:
     *    a. Step 1 found nothing (`byId == null`) — the same card variant may exist locally
     *       under a different (guest-generated) UUID ("UUID mismatch").
     *    b. Step 1 found a row (`byId != null`) but ITS tuple no longer matches [dto]'s tuple
     *       (e.g. another device re-pointed [dto]'s row via
     *       [com.mmg.manahub.core.data.repository.UserCardRepositoryImpl.updateEntryWithMerge],
     *       and the target tuple happens to already be occupied by a DIFFERENT pre-existing
     *       local row) — edge-case audit A1 (2026-07-15). Previously this composite-key lookup
     *       only ran in case (a), so applying [dto]'s new tuple as-is in case (b) could throw a
     *       `SQLiteConstraintException` (the composite unique index has NO partial/WHERE clause —
     *       a soft-deleted row still occupies its tuple, see `UserCardCollectionEntity`'s `Index`
     *       list — so a colliding row is a collision regardless of its `is_deleted` flag),
     *       aborting the ENTIRE pull loop with the sync watermark never advancing: a permanent
     *       "poison-pill" row that blocks every future sync cycle for this user.
     * 3. LWW: skip [dto] entirely if the row tracked under its own id (or, absent that, the
     *    tuple's current occupant) is strictly newer.
     * 4. Resolution: [dto] is authoritative once it wins LWW — Supabase's own upsert conflict
     *    resolution already ran during this cycle's PUSH phase (which always executes before
     *    PULL, see [sync]), so the server-held quantity is already the merged one; we never
     *    re-sum locally. When a DIFFERENT row occupies the target tuple, atomically delete it and
     *    upsert [dto] under its own id via [collectionDao.reconcileAndUpsert] — a pure
     *    soft-delete cannot free the tuple slot (see the Index note above), and a hard-delete is
     *    safe here specifically because PUSH already ran this cycle: any not-yet-communicated
     *    local state on that stale row was flushed to Supabase (and itself resolved server-side
     *    if IT collided too) before this PULL began.
     */
    private fun pullCollectionRow(
        dto: UserCardCollectionDto,
        onInserted: () -> Unit,
    ) {
        val byId = collectionDao.getByIdIncludingDeleted(dto.id)
        val tupleOwner = collectionDao.getByCompositeKey(
            dto.userId, dto.scryfallId, dto.isFoil,
            dto.condition, dto.language,
        )
        // A different physical row (not the one tracked under dto's own id) already occupies the
        // target tuple — null when there is no collision, or when the tuple owner IS byId itself
        // (its current tuple already matches dto's, e.g. a quantity-only change).
        val staleId: String? = tupleOwner?.id?.takeIf { it != dto.id }

        // LWW driver: prefer the row tracked under dto's own id (the most direct lineage); fall
        // back to the tuple owner when this device has never seen dto.id before.
        val local = byId ?: tupleOwner
        if (local != null && dto.updatedAt <= local.updatedAt) return

        if (staleId != null) {
            crashReporter.log("collection_pull_tuple_collision_resolved")
            // Use the atomic reconcileAndUpsert when a stale row must be replaced so a
            // process-kill between the delete and the insert never orphans the collection entry.
            collectionDao.reconcileAndUpsert(staleId, dto.toEntity())
        } else {
            collectionDao.upsert(dto.toEntity())
        }
        onInserted()
    }

    /**
     * Applies Last-Write-Wins (LWW) logic for a single remote deck [dto], upserting it and
     * replacing its card slots into Room if it wins. Extracted from the former inline duplicate
     * in [sync]/[assignUserIdAndSync] (collection sync data-loss fix, Phase 3) so both call
     * through [applyDeckPage] without repeating the logic.
     */
    private suspend fun pullDeckRow(
        dto: DeckSyncDto,
        onInserted: () -> Unit,
    ) {
        // getDeckByIdForSync includes soft-deleted rows (same tombstone fix as the collection
        // path above).
        val local = deckDao.getDeckByIdForSync(dto.id)
        // LWW: skip if local row is strictly newer.
        if (local != null && dto.updatedAt <= local.updatedAt) return
        deckDao.upsertDeck(dto.toEntity())
        // Also replace card slots so the pulled deck is fully usable on this device.
        if (!dto.isDeleted) {
            val remoteCards = deckRemote.getDeckCardsForDeck(dto.id).getOrThrow()
            // Pre-fetch any cards missing from Room so deck images resolve correctly. deck_cards
            // still has a FK to cards (unchanged by the Phase 2 collection FK drop), and
            // coverImageUrl is derived from a JOIN — missing card rows silently produce null
            // images. ensureCardsExist now always resolves every id (real metadata or a
            // pending-hydration placeholder), so this filter is defence in depth rather than a
            // gate that can strand data.
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
                            // Deck Engine Unification (D4): carry provenance through the pull too
                            // -- tolerant default "USER" on the DTO side already covers a server
                            // not yet returning this column.
                            source = it.source,
                        )
                    }
            )
        }
        onInserted()
    }

    /**
     * Ensures all [scryfallIds] are present in Room's [CardEntity] table.
     *
     * Collection sync data-loss fix (Phase 3/4): this is now a best-effort metadata WARM-UP, not
     * a gate. Every id in [scryfallIds] is GUARANTEED to exist in `cards` by the time this
     * returns — either with real Scryfall metadata, or with a
     * [buildPendingHydrationPlaceholder] row (`is_stale = true`, `stale_reason =
     * "pending_hydration"`) for anything Scryfall did not return this cycle.
     *
     * Strategy:
     * 1. Query Room for which IDs already exist.
     * 2. Batch-fetch the missing ones from the Scryfall /cards/collection endpoint
     *    (75 IDs per request, rate-limited via [ScryfallRemoteDataSource]).
     * 3. Upsert fetched cards into Room.
     * 4. Write a pending-hydration placeholder for any id STILL missing after step 3 — an
     *    ownership record (`user_card_collection`) must never depend on cache metadata being
     *    available (this is also why the Phase 2 migration dropped the RESTRICT FK). This
     *    placeholder is what makes the caller's insertion unconditional: there is no longer any
     *    id this method can return without a `cards` row backing it.
     *
     * Returns the set of [scryfallIds] that have REAL (non-placeholder) metadata — real
     * pre-existing or successfully fetched this cycle. Callers must NOT use this to skip
     * inserting a collection row; [CardHydrationWorker] is what upgrades a placeholder to real
     * metadata later. (The deck-cards path still uses this set to decide whether a card image can
     * render meaningfully — see [pullDeckRow] — but every id is present in `cards` either way.)
     */
    private suspend fun ensureCardsExist(scryfallIds: List<String>): Set<String> {
        if (scryfallIds.isEmpty()) return emptySet()

        val existingIds = cardDao.getByIds(scryfallIds).map { it.scryfallId }.toMutableSet()
        val missingIds = scryfallIds.filterNot { it in existingIds }
        if (missingIds.isEmpty()) return existingIds

        // Fetch in chunks of 75 (Scryfall /cards/collection hard limit). This `forEach` is
        // sequential (not `async`/`awaitAll`), so chunks are never fired concurrently from this
        // loop; each chunk's single `getCardsBatch` call is itself routed through
        // `ScryfallRequestQueue.execute` (shared cooldown + bounded concurrency + escalating
        // back-off, WS2) — no additional explicit pacing is needed here on top of that.
        missingIds.chunked(75).forEach { chunk ->
            scryfallRemote.getCardsBatch(chunk)
                .onSuccess { cards ->
                    // Backend & Performance Optimization plan, WS1+WS3 Part B item 6: ONE
                    // `upsertAll` per chunk instead of a per-card `upsert` loop (each call was its
                    // own Room transaction + table invalidation). `upsertAll` is itself
                    // `@Transaction`-wrapped (INSERT-OR-IGNORE + `@Update`, never
                    // `OnConflictStrategy.REPLACE` — see the CardDao class KDoc for why REPLACE
                    // would CASCADE-delete UserCardEntity rows), so this stays exactly as safe as
                    // the per-card path, just one Room write instead of up to 75.
                    runCatching { cardDao.upsertAll(cards.map { it.toEntityCard() }) }
                        .onSuccess { existingIds.addAll(cards.map { it.scryfallId }) }
                        .onFailure { e ->
                            // Non-fatal: a Room-level failure across the whole chunk is exceedingly
                            // rare (malformed data would already have failed to parse upstream) —
                            // the chunk's ids simply stay "missing" and get a placeholder below,
                            // then retried by CardHydrationWorker.
                            crashReporter.apply {
                                setCustomKey("scryfall_batch_chunk_size", chunk.size.toString())
                                setCustomKey("sync_error_type", e::class.simpleName ?: "Unknown")
                                recordException(RuntimeException("[ensureCardsExist] Room upsertAll failed", e))
                            }
                        }
                }
                .onFailure { e ->
                    // Non-fatal: entries for this chunk get a placeholder below and are retried by
                    // CardHydrationWorker.
                    crashReporter.apply {
                        setCustomKey("scryfall_batch_chunk_size", chunk.size.toString())
                        setCustomKey("sync_error_type", e::class.simpleName ?: "Unknown")
                        recordException(RuntimeException("[ensureCardsExist] Scryfall batch fetch failed", e))
                    }
                }
        }

        // Write-path hardening audit (2026-09-06): uses insertAllIgnore, NOT upsertAll -- a
        // concurrent writer (manual AddCard, CardBackfillWorker) may cache real metadata for one
        // of these ids during the Scryfall round-trip above. upsertAll's @Update fallback would
        // overwrite that real row with placeholder junk (cmc=0, "Unresolved card", ...);
        // INSERT-OR-IGNORE is structurally incapable of touching an existing row, so the race
        // window closes itself regardless of timing.
        val stillMissing = scryfallIds.filterNot { it in existingIds }
        if (stillMissing.isNotEmpty()) {
            runCatching {
                cardDao.insertAllIgnore(stillMissing.map { buildPendingHydrationPlaceholder(it) })
            }.onSuccess {
                crashReporter.apply {
                    log("collection_rows_awaiting_card_metadata")
                    setCustomKey("sync_unhydrated_count", stillMissing.size.toString())
                }
            }.onFailure { e ->
                crashReporter.apply {
                    setCustomKey("sync_error_type", e::class.simpleName ?: "Unknown")
                    recordException(RuntimeException("[ensureCardsExist] placeholder insertAllIgnore failed", e))
                }
            }
        }

        return existingIds
    }

    /**
     * Collection sync data-loss fix, Phase 6: post-sync integrity self-check.
     *
     * Compares the server's authoritative row count ([CollectionRemoteDataSource.getIntegrity],
     * which counts tombstones) against the local total
     * ([UserCardCollectionDao.getTotalRowCountForUser], same shape). A mismatch where the server
     * reports MORE rows than local means this device is missing data the server has — exactly the
     * failure mode this whole plan fixes (a truncated pull, or any future bug shaped like it). On
     * mismatch, clears the watermark so the NEXT [sync] cycle performs a full paginated re-pull —
     * safe now that `batch_upsert_collection`'s idempotency fix is deployed (a re-push of
     * already-synced rows no longer inflates quantity).
     *
     * Rate-limited to once per [REPAIR_RATE_LIMIT_MS] (24h) per user so a genuine, permanent
     * client/server disagreement cannot become an infinite full-pull loop. If a repair already ran
     * inside the window and the mismatch persists, only a non-fatal is recorded.
     *
     * Called only from [sync] (never [assignUserIdAndSync], whose own zero-local-rows watermark
     * clear already covers the equivalent "Room was wiped" case at login time). Any failure here
     * (RPC error, DataStore error) is a no-op — this is a safety net on an already-successful
     * sync, never a gate on it. A [CancellationException] still propagates (the caller's
     * `runCatching` wrapper rethrows it).
     */
    private suspend fun checkCollectionIntegrity(userId: String) {
        val integrity = collectionRemote.getIntegrity().getOrElse { error ->
            if (error is CancellationException) throw error
            return
        }
        val localTotal = collectionDao.getTotalRowCountForUser(userId)
        // Local ahead of (or equal to) remote is expected mid-push, or simply in sync -- only a
        // remote SURPLUS is the data-loss shape this check exists to catch.
        if (integrity.totalRows <= localTotal) return

        crashReporter.apply {
            log("collection_integrity_mismatch")
            setCustomKey("sync_local_rows", localTotal.toString())
            setCustomKey("sync_remote_rows", integrity.totalRows.toString())
        }

        val now = System.currentTimeMillis()
        val lastRepair = syncPrefs.getLastCollectionRepairMillis(userId)
        if (lastRepair != null && now - lastRepair < REPAIR_RATE_LIMIT_MS) {
            crashReporter.log("collection_repair_skipped_ratelimited")
            crashReporter.recordException(RuntimeException("collection_integrity_unresolved"))
            return
        }

        crashReporter.log("collection_repair_started")
        syncPrefs.saveLastCollectionRepairMillis(userId, now)
        syncPrefs.clearLastSyncMillis(userId)
    }

    companion object {
        /**
         * Requested page size for both `get_collection_changes_page` and `get_deck_changes_page`.
         *
         * MUST stay `<= ` the server-side cap those RPCs enforce (`LIMIT least(coalesce(p_limit,
         * 500), 500)`) — deliberately derived from [SERVER_PAGE_CAP] rather than a separate
         * literal so the two can never drift apart. [drainPages] additionally clamps to
         * [SERVER_PAGE_CAP] on its own, so even a future edit that reintroduces a raw literal here
         * cannot resurrect the short-page-means-done misdetection this constant used to risk — but
         * keep deriving from [SERVER_PAGE_CAP] anyway so there is exactly ONE number to reason
         * about across both modules.
         */
        private const val PAGE_SIZE = SERVER_PAGE_CAP

        /** Collection push slice size — keeps one `batch_upsert_collection` jsonb payload bounded. */
        private const val PUSH_CHUNK_SIZE = 200

        /** Phase 6: minimum gap between two automatic integrity repairs for the same user. */
        private const val REPAIR_RATE_LIMIT_MS = 24 * 60 * 60 * 1000L
    }
}

/**
 * Builds a placeholder [CardEntity] for a `scryfall_id` Scryfall did not resolve this cycle.
 *
 * Collection sync data-loss fix, Phase 4: an ownership record ([com.mmg.manahub.core.data.local
 * .entity.UserCardCollectionEntity]) must never depend on cache metadata being available. This
 * placeholder carries safe, neutral values everywhere a real [CardEntity] would have real data —
 * `cmc = 0.0`, empty color/type/keyword strings, all-null price fields, `"not_legal"` for every
 * format — so a naive numeric consumer degrades to "as if this card weren't scored" rather than
 * skewing an average. Real aggregation consumers (Stats, price refresh, deck analysis) instead
 * filter these rows out entirely via `stale_reason == "pending_hydration"` — see this plan's
 * Section C guard audit for the exact call sites.
 *
 * [com.mmg.manahub.core.data.local.dao.CardDao.upsertAll]'s INSERT-OR-IGNORE + `@Update`
 * transaction means [com.mmg.manahub.core.sync.CardHydrationWorker] safely overwrites this row in
 * place once Scryfall resolves it — never a delete+insert, so it can never CASCADE-strip the
 * `user_card_collection`/`deck_cards` rows that reference it.
 */
internal fun buildPendingHydrationPlaceholder(scryfallId: String): CardEntity = CardEntity(
    scryfallId = scryfallId,
    name = "Unresolved card (${scryfallId.take(8)})",
    printedName = null,
    lang = "en",
    manaCost = null,
    cmc = 0.0,
    colors = "[]",
    colorIdentity = "[]",
    typeLine = "",
    printedTypeLine = null,
    oracleText = null,
    printedText = null,
    keywords = "[]",
    power = null,
    toughness = null,
    loyalty = null,
    setCode = "",
    setName = "",
    collectorNumber = "",
    rarity = "",
    releasedAt = "",
    imageNormal = null,
    imageArtCrop = null,
    imageBackNormal = null,
    priceUsd = null,
    priceUsdFoil = null,
    priceEur = null,
    priceEurFoil = null,
    legalityStandard = "not_legal",
    legalityPioneer = "not_legal",
    legalityModern = "not_legal",
    legalityCommander = "not_legal",
    flavorText = null,
    artist = null,
    scryfallUri = "",
    isStale = true,
    staleReason = "pending_hydration",
)
