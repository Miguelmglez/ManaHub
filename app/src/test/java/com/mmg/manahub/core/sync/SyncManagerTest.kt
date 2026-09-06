package com.mmg.manahub.core.sync

import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.data.local.SyncPreferencesStore
import com.mmg.manahub.core.data.local.dao.CardDao
import com.mmg.manahub.core.data.local.dao.DeckDao
import com.mmg.manahub.core.data.local.dao.UserCardCollectionDao
import com.mmg.manahub.core.data.local.entity.CardEntity
import com.mmg.manahub.core.data.local.entity.DeckCardEntity
import com.mmg.manahub.core.data.local.entity.DeckEntity
import com.mmg.manahub.core.data.local.entity.UserCardCollectionEntity
import com.mmg.manahub.core.data.local.mapper.toEntityCard
import com.mmg.manahub.core.data.remote.ScryfallRemoteDataSource
import com.mmg.manahub.core.data.remote.collection.CollectionRemoteDataSource
import com.mmg.manahub.core.data.remote.collection.UserCardCollectionDto
import com.mmg.manahub.core.data.remote.decks.DeckCardSyncDto
import com.mmg.manahub.core.data.remote.decks.DeckRemoteDataSource
import com.mmg.manahub.core.data.remote.decks.DeckSyncDto
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [SyncManager].
 *
 * Strategy under test:
 *   - Push-then-Pull with Last-Write-Wins (LWW) conflict resolution.
 *   - Watermark is saved only after a fully successful cycle.
 *   - A [kotlinx.coroutines.sync.Mutex] prevents concurrent runs.
 *   - [assignUserIdAndSync] assigns userId to orphaned rows and clears the watermark
 *     before doing a full push.
 *
 * All external dependencies (DAOs, remote data sources, DataStore) are mocked with MockK.
 * No real database or network calls are made.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SyncManagerTest {

    // ── Dispatcher ────────────────────────────────────────────────────────────

    private val testDispatcher = StandardTestDispatcher()

    // ── Mocks ─────────────────────────────────────────────────────────────────

    private val collectionDao    = mockk<UserCardCollectionDao>(relaxed = true)
    private val deckDao          = mockk<DeckDao>(relaxed = true)
    private val cardDao          = mockk<CardDao>(relaxed = true)
    private val collectionRemote = mockk<CollectionRemoteDataSource>(relaxed = true)
    private val deckRemote       = mockk<DeckRemoteDataSource>(relaxed = true)
    private val scryfallRemote   = mockk<ScryfallRemoteDataSource>(relaxed = true)
    private val syncPrefs        = mockk<SyncPreferencesStore>(relaxed = true)
    private val crashReporter    = mockk<CrashReporter>(relaxed = true)

    // ── Constants ─────────────────────────────────────────────────────────────

    private val USER_ID      = "user-uuid-001"
    private val LAST_SYNC    = 1_000L
    private val DECK_ID      = "deck-uuid-001"
    private val CARD_ID_A    = "scryfall-id-a"
    private val CARD_ID_B    = "scryfall-id-b"
    private val COMMANDER_ID = "commander-scryfall-id"

    // ── Fixture helpers ───────────────────────────────────────────────────────

    private fun buildCollectionEntity(
        id:        String  = "col-uuid-001",
        userId:    String? = USER_ID,
        scryfallId: String = CARD_ID_A,
        updatedAt: Long    = LAST_SYNC + 1L,
        isDeleted: Boolean = false,
    ) = UserCardCollectionEntity(
        id               = id,
        userId           = userId,
        scryfallId       = scryfallId,
        quantity         = 2,
        isFoil           = false,
        condition        = "NM",
        language         = "en",
        isForTrade       = false,
        isDeleted        = isDeleted,
        updatedAt        = updatedAt,
        createdAt        = 500L,
    )

    private fun buildCollectionDto(
        id:        String = "col-uuid-001",
        userId:    String = USER_ID,
        scryfallId: String = CARD_ID_A,
        updatedAt: Long   = LAST_SYNC + 1L,
        isDeleted: Boolean = false,
    ) = UserCardCollectionDto(
        id               = id,
        userId           = userId,
        scryfallId       = scryfallId,
        quantity         = 2,
        isFoil           = false,
        condition        = "NM",
        language         = "en",
        isForTrade       = false,
        isDeleted        = isDeleted,
        updatedAt        = updatedAt,
        createdAt        = 500L,
    )

    private fun buildDeckEntity(
        id:              String  = DECK_ID,
        userId:          String? = USER_ID,
        updatedAt:       Long    = LAST_SYNC + 1L,
        isDeleted:       Boolean = false,
        commanderCardId: String? = null,
    ) = DeckEntity(
        id              = id,
        userId          = userId,
        name            = "Test Deck",
        description     = "",
        format          = "commander",
        coverCardId     = null,
        commanderCardId = commanderCardId,
        isDeleted       = isDeleted,
        updatedAt       = updatedAt,
        createdAt       = 500L,
    )

    private fun buildDeckSyncDto(
        id:              String  = DECK_ID,
        userId:          String  = USER_ID,
        updatedAt:       Long    = LAST_SYNC + 1L,
        isDeleted:       Boolean = false,
        commanderCardId: String? = null,
    ) = DeckSyncDto(
        id              = id,
        userId          = userId,
        name            = "Test Deck",
        description     = "",
        format          = "commander",
        coverCardId     = null,
        commanderCardId = commanderCardId,
        isDeleted       = isDeleted,
        updatedAt       = updatedAt,
        createdAt       = 500L,
    )

    // ── Setup ─────────────────────────────────────────────────────────────────

    private lateinit var syncManager: SyncManager

    @Before
    fun setUp() {
        // Default: watermark = LAST_SYNC, no local rows, empty remote responses
        coEvery { syncPrefs.getLastSyncMillis(any()) } returns LAST_SYNC
        every { collectionDao.getAllSince(any(), any()) } returns emptyList()
        every { deckDao.getDecksSince(any(), any()) } returns emptyList()
        every { deckDao.getDeckCards(any()) } returns emptyList()
        coEvery { collectionRemote.getChangesPage(any(), any(), any(), any()) } returns Result.success(emptyList())
        coEvery { deckRemote.getDeckChangesPage(any(), any(), any(), any()) } returns Result.success(emptyList())
        coEvery { collectionRemote.batchUpsert(any()) } returns Result.success(Unit)
        coEvery { deckRemote.batchUpsertDecks(any()) } returns Result.success(Unit)
        coEvery { deckRemote.upsertDeckCards(any(), any()) } returns Result.success(Unit)
        coEvery { deckRemote.getDeckCardsForDeck(any()) } returns Result.success(emptyList())
        // ensureCardsExist() pre-fetches any card missing from Room before a collection/deck
        // pull can upsert (RESTRICT FK). Without this stub, cardDao.getByIds(any()) is an
        // unstubbed relaxed call returning an EMPTY list (MockK's relaxed default for a List
        // return type), so every scryfallId is treated as "missing" and the Scryfall batch
        // fetch (also unstubbed/relaxed) never populates it either — cachedIds ends up empty
        // and pullCollectionRow/deck-card resolution is silently skipped for every row,
        // regardless of any LWW stubbing below. Pretend every requested id is already cached.
        coEvery { cardDao.getByIds(any()) } answers {
            firstArg<List<String>>().map { id -> mockk<CardEntity>(relaxed = true) { every { scryfallId } returns id } }
        }

        // Phase 6 integrity self-check: default to "in sync" (0 remote rows, matching the
        // relaxed-mock default of 0 for collectionDao.getTotalRowCountForUser) so it is a no-op
        // unless a test explicitly overrides it.
        coEvery { collectionRemote.getIntegrity() } returns Result.success(
            com.mmg.manahub.core.data.remote.collection.CollectionIntegrityDto(
                totalRows = 0, liveRows = 0, liveQuantity = 0, maxUpdatedAt = null,
            )
        )

        syncManager = SyncManager(
            collectionDao    = collectionDao,
            deckDao          = deckDao,
            cardDao          = cardDao,
            collectionRemote = collectionRemote,
            deckRemote       = deckRemote,
            scryfallRemote   = scryfallRemote,
            syncPrefs        = syncPrefs,
            ioDispatcher     = testDispatcher,
            crashReporter    = crashReporter,
        )
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 1 — PUSH: collection
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given local collection row newer than watermark when sync then batchUpsert is called with that row`() = runTest(testDispatcher) {
        // Arrange: one local row modified after LAST_SYNC
        val localRow = buildCollectionEntity(updatedAt = LAST_SYNC + 100L)
        every { collectionDao.getAllSince(USER_ID, LAST_SYNC) } returns listOf(localRow)
        val capturedDtos = slot<List<UserCardCollectionDto>>()
        coEvery { collectionRemote.batchUpsert(capture(capturedDtos)) } returns Result.success(Unit)

        // Act
        val result = syncManager.sync(USER_ID)

        // Assert
        assertEquals(SyncState.SUCCESS, result.state)
        assertEquals(1, result.collectionPushed)
        assertEquals(1, capturedDtos.captured.size)
        assertEquals(localRow.id, capturedDtos.captured[0].id)
    }

    @Test
    fun `given no local collection changes when sync then batchUpsert is NOT called`() = runTest(testDispatcher) {
        // Arrange: no rows modified since LAST_SYNC
        every { collectionDao.getAllSince(USER_ID, LAST_SYNC) } returns emptyList()

        // Act
        syncManager.sync(USER_ID)

        // Assert
        coVerify(exactly = 0) { collectionRemote.batchUpsert(any()) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 2 — PUSH: decks
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given local deck newer than watermark when sync then batchUpsertDecks is called with that deck`() = runTest(testDispatcher) {
        // Arrange
        val localDeck = buildDeckEntity(updatedAt = LAST_SYNC + 100L)
        val deckCard  = DeckCardEntity(deckId = DECK_ID, scryfallId = CARD_ID_A, quantity = 4)
        every { deckDao.getDecksSince(USER_ID, LAST_SYNC) } returns listOf(localDeck)
        every { deckDao.getDeckCards(DECK_ID) } returns listOf(deckCard)
        val capturedDecks = slot<List<DeckSyncDto>>()
        coEvery { deckRemote.batchUpsertDecks(capture(capturedDecks)) } returns Result.success(Unit)

        // Act
        val result = syncManager.sync(USER_ID)

        // Assert
        assertEquals(SyncState.SUCCESS, result.state)
        assertEquals(1, result.decksPushed)
        assertEquals(1, capturedDecks.captured.size)
        assertEquals(DECK_ID, capturedDecks.captured[0].id)
    }

    @Test
    fun `given local deck dirty when sync then upsertDeckCards is called for each dirty deck`() = runTest(testDispatcher) {
        // Arrange
        val localDeck = buildDeckEntity()
        val deckCard  = DeckCardEntity(deckId = DECK_ID, scryfallId = CARD_ID_A, quantity = 2)
        every { deckDao.getDecksSince(USER_ID, LAST_SYNC) } returns listOf(localDeck)
        every { deckDao.getDeckCards(DECK_ID) } returns listOf(deckCard)
        val capturedDeckId = slot<String>()
        val capturedCards  = slot<List<DeckCardSyncDto>>()
        coEvery { deckRemote.upsertDeckCards(capture(capturedDeckId), capture(capturedCards)) } returns Result.success(Unit)

        // Act
        syncManager.sync(USER_ID)

        // Assert
        assertEquals(DECK_ID, capturedDeckId.captured)
        assertEquals(1, capturedCards.captured.size)
        assertEquals(CARD_ID_A, capturedCards.captured[0].scryfallId)
        assertEquals(2, capturedCards.captured[0].quantity)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 3 — PULL: collection — LWW
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given remote collection row newer than local when sync then it is upserted into Room`() = runTest(testDispatcher) {
        // Arrange
        val localRow  = buildCollectionEntity(updatedAt = 2_000L)
        val remoteDto = buildCollectionDto(updatedAt = 3_000L)          // remote is newer
        // pullCollectionRow resolves the local row via getByIdIncludingDeleted (own lineage)
        // AND getByCompositeKey (tuple occupant) — both must point at the same local row
        // (same id as remoteDto) so the tuple-collision path is not spuriously triggered.
        every { collectionDao.getByIdIncludingDeleted(remoteDto.id) } returns localRow
        every {
            collectionDao.getByCompositeKey(remoteDto.userId, remoteDto.scryfallId, remoteDto.isFoil, remoteDto.condition, remoteDto.language)
        } returns localRow
        coEvery { collectionRemote.getChangesPage(LAST_SYNC, any(), any(), any()) } returns Result.success(listOf(remoteDto))

        // Act
        val result = syncManager.sync(USER_ID)

        // Assert
        assertEquals(1, result.collectionPulled)
        verify(exactly = 1) { collectionDao.upsert(any()) }
    }

    @Test
    fun `given local collection row strictly newer than remote when sync then local row is NOT overwritten`() = runTest(testDispatcher) {
        // Arrange: local wins (local is newer)
        val localRow  = buildCollectionEntity(updatedAt = 5_000L)
        val remoteDto = buildCollectionDto(updatedAt = 3_000L)           // remote is older
        every { collectionDao.getByIdIncludingDeleted(remoteDto.id) } returns localRow
        every {
            collectionDao.getByCompositeKey(remoteDto.userId, remoteDto.scryfallId, remoteDto.isFoil, remoteDto.condition, remoteDto.language)
        } returns localRow
        coEvery { collectionRemote.getChangesPage(LAST_SYNC, any(), any(), any()) } returns Result.success(listOf(remoteDto))

        // Act
        val result = syncManager.sync(USER_ID)

        // Assert: LWW → skip remote (remote.updatedAt <= local.updatedAt)
        assertEquals(0, result.collectionPulled)
        verify(exactly = 0) { collectionDao.upsert(any()) }
    }

    @Test
    fun `given local and remote with equal timestamp when sync then local wins and remote is NOT applied`() = runTest(testDispatcher) {
        // Arrange: tie → local wins (condition: dto.updatedAt <= local.updatedAt skips the row)
        val sameTimestamp = 4_000L
        val localRow  = buildCollectionEntity(updatedAt = sameTimestamp)
        val remoteDto = buildCollectionDto(updatedAt = sameTimestamp)
        every { collectionDao.getByIdIncludingDeleted(remoteDto.id) } returns localRow
        every {
            collectionDao.getByCompositeKey(remoteDto.userId, remoteDto.scryfallId, remoteDto.isFoil, remoteDto.condition, remoteDto.language)
        } returns localRow
        coEvery { collectionRemote.getChangesPage(LAST_SYNC, any(), any(), any()) } returns Result.success(listOf(remoteDto))

        // Act
        val result = syncManager.sync(USER_ID)

        // Assert: tie → local wins → upsert NOT called
        assertEquals(0, result.collectionPulled)
        verify(exactly = 0) { collectionDao.upsert(any()) }
    }

    @Test
    fun `given remote collection row with no local counterpart when sync then it is inserted into Room`() = runTest(testDispatcher) {
        // Arrange: new row from server (no local row exists)
        val remoteDto = buildCollectionDto(id = "new-server-id")
        every { collectionDao.getByIdIncludingDeleted("new-server-id") } returns null      // not present locally
        every {
            collectionDao.getByCompositeKey(remoteDto.userId, remoteDto.scryfallId, remoteDto.isFoil, remoteDto.condition, remoteDto.language)
        } returns null
        coEvery { collectionRemote.getChangesPage(LAST_SYNC, any(), any(), any()) } returns Result.success(listOf(remoteDto))

        // Act
        val result = syncManager.sync(USER_ID)

        // Assert
        assertEquals(1, result.collectionPulled)
        verify(exactly = 1) { collectionDao.upsert(any()) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 4 — PULL: decks — LWW
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given remote deck newer than local when sync then it is upserted into Room`() = runTest(testDispatcher) {
        // Arrange
        val localDeck  = buildDeckEntity(updatedAt = 2_000L)
        val remoteDeck = buildDeckSyncDto(updatedAt = 3_000L)
        every { deckDao.getDeckByIdForSync(DECK_ID) } returns localDeck
        coEvery { deckRemote.getDeckChangesPage(LAST_SYNC, any(), any(), any()) } returns Result.success(listOf(remoteDeck))

        // Act
        val result = syncManager.sync(USER_ID)

        // Assert
        assertEquals(1, result.decksPulled)
        verify(exactly = 1) { deckDao.upsertDeck(any()) }
    }

    @Test
    fun `given local deck strictly newer than remote when sync then Room is NOT overwritten`() = runTest(testDispatcher) {
        // Arrange: local is newer → skip remote
        val localDeck  = buildDeckEntity(updatedAt = 9_000L)
        val remoteDeck = buildDeckSyncDto(updatedAt = 1_000L)
        every { deckDao.getDeckByIdForSync(DECK_ID) } returns localDeck
        coEvery { deckRemote.getDeckChangesPage(LAST_SYNC, any(), any(), any()) } returns Result.success(listOf(remoteDeck))

        // Act
        val result = syncManager.sync(USER_ID)

        // Assert
        assertEquals(0, result.decksPulled)
        verify(exactly = 0) { deckDao.upsertDeck(any()) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 5 — WATERMARK
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given successful sync when sync completes then watermark is saved for userId`() = runTest(testDispatcher) {
        // Arrange: defaults produce a successful cycle
        val savedMillis = slot<Long>()
        coEvery { syncPrefs.saveLastSyncMillis(USER_ID, capture(savedMillis)) } returns Unit

        // Act
        val result = syncManager.sync(USER_ID)

        // Assert
        assertEquals(SyncState.SUCCESS, result.state)
        coVerify(exactly = 1) { syncPrefs.saveLastSyncMillis(eq(USER_ID), any()) }
        // Saved millis must be a plausible "now" (greater than LAST_SYNC)
        assert(savedMillis.captured > LAST_SYNC) {
            "Saved watermark ${savedMillis.captured} should be > $LAST_SYNC"
        }
    }

    @Test
    fun `given push fails when sync then watermark is NOT saved`() = runTest(testDispatcher) {
        // Arrange: make collection push throw
        val localRow = buildCollectionEntity()
        every { collectionDao.getAllSince(USER_ID, LAST_SYNC) } returns listOf(localRow)
        coEvery { collectionRemote.batchUpsert(any()) } returns Result.failure(RuntimeException("network error"))

        // Act
        val result = syncManager.sync(USER_ID)

        // Assert
        assertEquals(SyncState.ERROR, result.state)
        assertNotNull(result.error)
        coVerify(exactly = 0) { syncPrefs.saveLastSyncMillis(any(), any()) }
    }

    @Test
    fun `given pull page fetch fails when sync then sync still succeeds and watermark is capped at lastSync`() = runTest(testDispatcher) {
        // Arrange: the first collection page fetch fails (network/RPC error). Collection sync
        // data-loss fix, Phase 3: a page-FETCH failure no longer aborts the whole cycle as
        // SyncState.ERROR (push already succeeded) -- it caps the safe watermark instead, per the
        // drainPages/safe-watermark contract (min(syncStartTime, minUnappliedUpdatedAt - 1)
        // .coerceAtLeast(lastSync)). With zero pages ever consumed, minUnappliedUpdatedAt resolves
        // to lastSync itself, so the saved watermark is exactly LAST_SYNC -- unchanged, never
        // regressed, never advanced past the untried window.
        coEvery { collectionRemote.getChangesPage(any(), any(), any(), any()) } returns Result.failure(RuntimeException("timeout"))

        // Act
        val result = syncManager.sync(USER_ID)

        // Assert
        assertEquals(SyncState.SUCCESS, result.state)
        assertEquals(0, result.collectionPulled)
        coVerify(exactly = 1) { syncPrefs.saveLastSyncMillis(USER_ID, LAST_SYNC) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 6 — Soft-deleted decks
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given soft-deleted deck when sync then tombstone is pushed to Supabase`() = runTest(testDispatcher) {
        // Arrange: getDecksSince returns is_deleted = true rows (tombstones are pushed)
        val deletedDeck = buildDeckEntity(isDeleted = true)
        every { deckDao.getDecksSince(USER_ID, LAST_SYNC) } returns listOf(deletedDeck)
        val capturedDecks = slot<List<DeckSyncDto>>()
        coEvery { deckRemote.batchUpsertDecks(capture(capturedDecks)) } returns Result.success(Unit)

        // Act
        syncManager.sync(USER_ID)

        // Assert: is_deleted propagated to the DTO
        assertEquals(1, capturedDecks.captured.size)
        assert(capturedDecks.captured[0].isDeleted) {
            "is_deleted must be true in the pushed DTO"
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 7 — commanderCardId round-trip
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given deck with commanderCardId when sync then commanderCardId is preserved in pushed DTO`() = runTest(testDispatcher) {
        // Arrange: local deck has a commander
        val localDeck = buildDeckEntity(commanderCardId = COMMANDER_ID)
        every { deckDao.getDecksSince(USER_ID, LAST_SYNC) } returns listOf(localDeck)
        val capturedDecks = slot<List<DeckSyncDto>>()
        coEvery { deckRemote.batchUpsertDecks(capture(capturedDecks)) } returns Result.success(Unit)

        // Act
        syncManager.sync(USER_ID)

        // Assert: commanderCardId survives Entity → DTO mapping
        assertEquals(COMMANDER_ID, capturedDecks.captured[0].commanderCardId)
    }

    @Test
    fun `given remote DeckSyncDto with commanderCardId when sync then commanderCardId is persisted in Room`() = runTest(testDispatcher) {
        // Arrange: server returns a deck with a commander that is newer than local
        val remoteDeck = buildDeckSyncDto(
            updatedAt       = 9_000L,
            commanderCardId = COMMANDER_ID,
        )
        every { deckDao.getDeckById(DECK_ID) } returns buildDeckEntity(updatedAt = 1_000L)
        coEvery { deckRemote.getDeckChangesPage(any(), any(), any(), any()) } returns Result.success(listOf(remoteDeck))
        val capturedEntity = slot<DeckEntity>()
        every { deckDao.upsertDeck(capture(capturedEntity)) } returns Unit

        // Act
        syncManager.sync(USER_ID)

        // Assert: commanderCardId survives DTO → Entity mapping (round-trip)
        assertEquals(COMMANDER_ID, capturedEntity.captured.commanderCardId)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 8 — Mutex: no concurrent runs
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given two concurrent sync calls when first is in flight then second waits and watermark is saved twice`() = runTest(testDispatcher) {
        // Arrange: defaults produce clean success cycles

        // Act: launch two concurrent sync coroutines
        val job1 = launch { syncManager.sync(USER_ID) }
        val job2 = launch { syncManager.sync(USER_ID) }
        advanceUntilIdle()
        job1.join()
        job2.join()

        // Assert: watermark must be saved twice — both cycles completed (serially)
        coVerify(exactly = 2) { syncPrefs.saveLastSyncMillis(eq(USER_ID), any()) }
    }

    @Test
    fun `given sync is in flight when second sync starts then syncState transitions to SYNCING`() = runTest(testDispatcher) {
        // Inject a virtual delay so syncJob suspends after setting SYNCING, giving collectJob
        // a chance to process the SYNCING emission before SUCCESS is set.
        coEvery { syncPrefs.getLastSyncMillis(any()) } coAnswers { delay(1); LAST_SYNC }

        // Arrange: collect states during sync
        val states = mutableListOf<SyncState>()
        val collectJob = launch { syncManager.syncState.collect { states.add(it) } }
        val syncJob = launch { syncManager.sync(USER_ID) }

        // Act
        advanceUntilIdle()
        syncJob.join()
        collectJob.cancel()

        // Assert: SYNCING must appear before SUCCESS
        assert(states.contains(SyncState.SYNCING)) { "Expected SYNCING state during sync" }
        assert(states.contains(SyncState.SUCCESS)) { "Expected SUCCESS state after sync" }
        val syncingIdx = states.indexOf(SyncState.SYNCING)
        val successIdx = states.lastIndexOf(SyncState.SUCCESS)
        assert(syncingIdx < successIdx) { "SYNCING must precede SUCCESS" }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 9 — assignUserIdAndSync
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given guest rows when assignUserIdAndSync then collectionDao assignUserId is called with newUserId`() = runTest(testDispatcher) {
        // Act
        syncManager.assignUserIdAndSync(USER_ID)
        advanceUntilIdle()

        // Assert: userId assigned to orphaned collection rows
        verify(exactly = 1) { collectionDao.assignUserId(eq(USER_ID), any()) }
    }

    @Test
    fun `given guest rows when assignUserIdAndSync then deckDao assignDeckUserId is called with newUserId`() = runTest(testDispatcher) {
        // Act
        syncManager.assignUserIdAndSync(USER_ID)
        advanceUntilIdle()

        // Assert: userId assigned to orphaned deck rows
        verify(exactly = 1) { deckDao.assignDeckUserId(eq(USER_ID), any()) }
    }

    @Test
    fun `given offline-to-online transition when assignUserIdAndSync then watermark is cleared before sync`() = runTest(testDispatcher) {
        // clearLastSyncMillis is only called when there are migrated rows (collectionMigrated > 0)
        every { collectionDao.assignUserId(any(), any()) } returns 1
        // We need to verify clearLastSyncMillis is called before saveLastSyncMillis.
        val callOrder = mutableListOf<String>()
        coEvery { syncPrefs.clearLastSyncMillis(USER_ID) } answers { callOrder.add("clear"); Unit }
        coEvery { syncPrefs.saveLastSyncMillis(USER_ID, any()) } answers { callOrder.add("save"); Unit }

        // Act
        syncManager.assignUserIdAndSync(USER_ID)
        advanceUntilIdle()

        // Assert
        val clearIdx = callOrder.indexOf("clear")
        val saveIdx  = callOrder.indexOf("save")
        assert(clearIdx >= 0) { "clearLastSyncMillis must be called" }
        assert(saveIdx  >= 0) { "saveLastSyncMillis must be called (sync completed)" }
        assert(clearIdx < saveIdx) { "Watermark must be cleared BEFORE saving the new one" }
    }

    @Test
    fun `given offline-to-online transition when assignUserIdAndSync then sync is executed and watermark saved`() = runTest(testDispatcher) {
        // Act
        val result = syncManager.assignUserIdAndSync(USER_ID)
        advanceUntilIdle()

        // Assert: full sync cycle ran and succeeded
        assertEquals(SyncState.SUCCESS, result.state)
        assertNull(result.error)
        coVerify(exactly = 1) { syncPrefs.saveLastSyncMillis(eq(USER_ID), any()) }
    }

    @Test
    fun `given no local data when assignUserIdAndSync then batchUpsert is not called and sync succeeds`() = runTest(testDispatcher) {
        // assignUserIdAndSync only pushes — with empty local data, no batchUpsert calls expected.

        // Act
        val result = syncManager.assignUserIdAndSync(USER_ID)
        advanceUntilIdle()

        // Assert: push skipped (nothing to push), sync completed cleanly
        assertEquals(SyncState.SUCCESS, result.state)
        assertNull(result.error)
        coVerify(exactly = 0) { collectionRemote.batchUpsert(any()) }
        coVerify(exactly = 0) { deckRemote.batchUpsertDecks(any()) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 10 — SyncState transitions
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given initial state when syncManager is created then syncState is IDLE`() = runTest(testDispatcher) {
        assertEquals(SyncState.IDLE, syncManager.syncState.value)
    }

    @Test
    fun `given successful sync when sync completes then syncState is SUCCESS`() = runTest(testDispatcher) {
        val result = syncManager.sync(USER_ID)
        advanceUntilIdle()

        assertEquals(SyncState.SUCCESS, result.state)
        assertEquals(SyncState.SUCCESS, syncManager.syncState.value)
    }

    @Test
    fun `given failing sync when sync completes then syncState is ERROR`() = runTest(testDispatcher) {
        // Arrange: deck push fails
        val localDeck = buildDeckEntity()
        every { deckDao.getDecksSince(USER_ID, LAST_SYNC) } returns listOf(localDeck)
        coEvery { deckRemote.batchUpsertDecks(any()) } returns Result.failure(RuntimeException("DB error"))

        val result = syncManager.sync(USER_ID)
        advanceUntilIdle()

        assertEquals(SyncState.ERROR, result.state)
        assertEquals(SyncState.ERROR, syncManager.syncState.value)
    }

    @Test
    fun `given soft-deleted collection card when sync then tombstone is pushed to Supabase`() = runTest(testDispatcher) {
        // Arrange: getAllSince returns tombstone rows (is_deleted = true is included)
        val tombstone = buildCollectionEntity(isDeleted = true)
        every { collectionDao.getAllSince(USER_ID, LAST_SYNC) } returns listOf(tombstone)
        val capturedDtos = slot<List<UserCardCollectionDto>>()
        coEvery { collectionRemote.batchUpsert(capture(capturedDtos)) } returns Result.success(Unit)

        // Act
        syncManager.sync(USER_ID)
        advanceUntilIdle()

        // Assert: tombstone flag propagated to remote
        assertEquals(1, capturedDtos.captured.size)
        assert(capturedDtos.captured[0].isDeleted) { "is_deleted must be true in the pushed DTO" }
    }

    @Test
    fun `given remote deleted card when sync then it is upserted into Room with isDeleted true`() = runTest(testDispatcher) {
        // Arrange: server returns a deleted card newer than local
        val remoteDeleted = buildCollectionDto(updatedAt = 9_000L, isDeleted = true)
        val localRow = buildCollectionEntity(updatedAt = 1_000L)
        every { collectionDao.getByIdIncludingDeleted(remoteDeleted.id) } returns localRow
        every {
            collectionDao.getByCompositeKey(remoteDeleted.userId, remoteDeleted.scryfallId, remoteDeleted.isFoil, remoteDeleted.condition, remoteDeleted.language)
        } returns localRow
        coEvery { collectionRemote.getChangesPage(LAST_SYNC, any(), any(), any()) } returns Result.success(listOf(remoteDeleted))
        val capturedEntity = slot<UserCardCollectionEntity>()
        every { collectionDao.upsert(capture(capturedEntity)) } returns 1L

        // Act
        syncManager.sync(USER_ID)
        advanceUntilIdle()

        // Assert: soft-delete propagated into Room
        assert(capturedEntity.captured.isDeleted) { "Local entity must be marked as deleted" }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP — ensureCardsExist: chunked upsertAll (Backend & Performance
    //  Optimization plan, WS1+WS3 Part B item 6, 2026-07-28)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given one card missing from Room during a collection pull when ensureCardsExist fetches it then upsertAll is called once and upsert is never called`() =
        runTest(testDispatcher) {
            // Arrange: override the default setUp() stub (which pretends every id is cached) so
            // CARD_ID_A genuinely reports missing, forcing the Scryfall-fetch-and-persist path.
            val remoteRow = buildCollectionDto(scryfallId = CARD_ID_A, updatedAt = 9_000L)
            coEvery { collectionRemote.getChangesPage(LAST_SYNC, any(), any(), any()) } returns Result.success(listOf(remoteRow))
            coEvery { cardDao.getByIds(listOf(CARD_ID_A)) } returns emptyList()
            every { collectionDao.getByIdIncludingDeleted(any()) } returns null
            every { collectionDao.getByCompositeKey(any(), any(), any(), any(), any()) } returns null
            val fetchedCard = com.mmg.manahub.util.TestFixtures.buildCard(scryfallId = CARD_ID_A)
            coEvery { scryfallRemote.getCardsBatch(listOf(CARD_ID_A)) } returns Result.success(listOf(fetchedCard))

            // Act
            syncManager.sync(USER_ID)
            advanceUntilIdle()

            // Assert: ONE upsertAll call for the single (1-id) chunk; the old per-card upsert loop
            // this replaced must never fire from this path.
            coVerify(exactly = 1) { cardDao.upsertAll(listOf(fetchedCard.toEntityCard())) }
            coVerify(exactly = 0) { cardDao.upsert(any()) }
        }

    @Test
    fun `given more than 75 missing cards during a collection pull when ensureCardsExist fetches them then getCardsBatch and upsertAll are each called once per 75-id chunk`() =
        runTest(testDispatcher) {
            // Arrange: 80 distinct missing scryfallIds -> 2 chunks (75 + 5), per the Scryfall
            // /cards/collection hard limit ensureCardsExist chunks against.
            val missingIds = (1..80).map { "missing-card-$it" }
            val remoteRows = missingIds.map { id -> buildCollectionDto(id = "col-$id", scryfallId = id, updatedAt = 9_000L) }
            coEvery { collectionRemote.getChangesPage(LAST_SYNC, any(), any(), any()) } returns Result.success(remoteRows)
            coEvery { cardDao.getByIds(missingIds) } returns emptyList()
            every { collectionDao.getByIdIncludingDeleted(any()) } returns null
            every { collectionDao.getByCompositeKey(any(), any(), any(), any(), any()) } returns null

            val chunkCaptures = mutableListOf<List<String>>()
            coEvery { scryfallRemote.getCardsBatch(capture(chunkCaptures)) } answers {
                val ids = firstArg<List<String>>()
                Result.success(ids.map { id -> com.mmg.manahub.util.TestFixtures.buildCard(scryfallId = id) })
            }

            // Act
            syncManager.sync(USER_ID)
            advanceUntilIdle()

            // Assert: exactly 2 Scryfall batch calls (75 + 5) and exactly 2 upsertAll calls (one per
            // chunk, never one per card and never a single 80-card write).
            assertEquals(2, chunkCaptures.size)
            assertEquals(setOf(75, 5), chunkCaptures.map { it.size }.toSet())
            coVerify(exactly = 2) { cardDao.upsertAll(any()) }
            coVerify(exactly = 0) { cardDao.upsert(any()) }
        }

    @Test
    fun `given a Scryfall batch failure for one chunk when ensureCardsExist runs then a placeholder is written and the collection row still inserts`() =
        runTest(testDispatcher) {
            // Collection sync data-loss fix, Phase 4 (2026-09-06): this test used to assert the
            // OPPOSITE of what it asserts now. Before Phase 4, a card Scryfall couldn't resolve
            // meant ensureCardsExist's cachedIds set stayed empty, and SyncManager's
            // `if (dto.scryfallId !in cachedIds) continue` gate SKIPPED the collection row
            // entirely -- the row was silently never inserted, and (via the pre-Phase-3 watermark
            // bug) permanently stranded. That gate is now GONE: ensureCardsExist always writes a
            // `stale_reason = "pending_hydration"` placeholder CardEntity for anything Scryfall
            // didn't return, so the ownership row ALWAYS inserts. The row is treated as
            // successfully APPLIED (not skipped), so the safe watermark advances past it exactly
            // like any other applied row -- CardHydrationWorker resolves the placeholder later,
            // completely decoupled from the sync watermark.
            val remoteRow = buildCollectionDto(scryfallId = CARD_ID_A, updatedAt = 9_000L)
            coEvery { collectionRemote.getChangesPage(LAST_SYNC, any(), any(), any()) } returns Result.success(listOf(remoteRow))
            coEvery { cardDao.getByIds(listOf(CARD_ID_A)) } returns emptyList()
            every { collectionDao.getByIdIncludingDeleted(any()) } returns null
            every { collectionDao.getByCompositeKey(any(), any(), any(), any(), any()) } returns null
            coEvery { scryfallRemote.getCardsBatch(listOf(CARD_ID_A)) } returns
                Result.failure(RuntimeException("Scryfall down"))

            // Act: must not throw -- a chunk-level Scryfall failure is non-fatal, logged, and the
            // id gets a placeholder immediately rather than staying "missing".
            val result = syncManager.sync(USER_ID)
            advanceUntilIdle()

            // Assert: sync completes successfully, the collection row is inserted despite the
            // unresolved card, ONE placeholder CardEntity is written (never the real-card
            // upsertAll path, never the legacy per-card cardDao.upsert), and the watermark
            // advances all the way to syncStartTime (nothing was left unapplied this cycle).
            assertEquals(SyncState.SUCCESS, result.state)
            assertEquals(1, result.collectionPulled)
            verify(exactly = 1) { collectionDao.upsert(any()) }
            coVerify(exactly = 1) {
                cardDao.upsertAll(match { cards -> cards.size == 1 && cards[0].staleReason == "pending_hydration" })
            }
            coVerify(exactly = 0) { cardDao.upsert(any()) }
            val savedMillis = slot<Long>()
            coVerify(exactly = 1) { syncPrefs.saveLastSyncMillis(eq(USER_ID), capture(savedMillis)) }
            assert(savedMillis.captured > LAST_SYNC) {
                "Watermark should advance past LAST_SYNC since the row was successfully applied (via placeholder)"
            }
        }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP — Pagination + safe watermark (collection sync data-loss fix,
    //  linear-moseying-yeti plan, Phase 3/8)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given 1377 remote collection rows across 3 pages when sync then all rows are applied and the drain completes`() =
        runTest(testDispatcher) {
            // Arrange: 500 + 500 + 377 = 1377, matching the forensic 2026-09 incident's exact
            // row count. Each page's size relative to the 500 limit is what drives drainPages'
            // termination (the 3rd, short page signals "no more").
            val page1 = (1..500).map { i -> buildCollectionDto(id = "row-$i", scryfallId = "scryfall-$i", updatedAt = LAST_SYNC + i) }
            val page2 = (501..1000).map { i -> buildCollectionDto(id = "row-$i", scryfallId = "scryfall-$i", updatedAt = LAST_SYNC + i) }
            val page3 = (1001..1377).map { i -> buildCollectionDto(id = "row-$i", scryfallId = "scryfall-$i", updatedAt = LAST_SYNC + i) }
            coEvery { collectionRemote.getChangesPage(LAST_SYNC, any(), any(), any()) } returnsMany listOf(
                Result.success(page1), Result.success(page2), Result.success(page3),
            )
            every { collectionDao.getByIdIncludingDeleted(any()) } returns null
            every { collectionDao.getByCompositeKey(any(), any(), any(), any(), any()) } returns null

            // Act
            val result = syncManager.sync(USER_ID)
            advanceUntilIdle()

            // Assert: every row applied, the drain reached the short (< limit) 3rd page and
            // stopped there (never a 4th fetch), and the watermark advances all the way to
            // syncStartTime since nothing was left unapplied.
            assertEquals(SyncState.SUCCESS, result.state)
            assertEquals(1377, result.collectionPulled)
            verify(exactly = 1377) { collectionDao.upsert(any()) }
            coVerify(exactly = 3) { collectionRemote.getChangesPage(LAST_SYNC, any(), any(), any()) }
            val savedMillis = slot<Long>()
            coVerify(exactly = 1) { syncPrefs.saveLastSyncMillis(eq(USER_ID), capture(savedMillis)) }
            assert(savedMillis.captured > LAST_SYNC + 1377) {
                "A fully-drained, fully-applied pull should advance the watermark to syncStartTime"
            }
        }

    @Test
    fun `given a page-2 fetch failure when sync then the watermark is capped below page-2's lowest updated_at`() =
        runTest(testDispatcher) {
            // Arrange: page 1 succeeds (500 rows), page 2 fails outright (network/RPC error).
            val page1 = (1..500).map { i -> buildCollectionDto(id = "row-$i", scryfallId = "scryfall-$i", updatedAt = LAST_SYNC + i) }
            val page2LowestUpdatedAt = LAST_SYNC + 501L
            coEvery { collectionRemote.getChangesPage(LAST_SYNC, any(), any(), any()) } returnsMany listOf(
                Result.success(page1),
                Result.failure(RuntimeException("network blip on page 2")),
            )
            every { collectionDao.getByIdIncludingDeleted(any()) } returns null
            every { collectionDao.getByCompositeKey(any(), any(), any(), any(), any()) } returns null

            // Act
            val result = syncManager.sync(USER_ID)
            advanceUntilIdle()

            // Assert: page 1's 500 rows applied; sync still reports SUCCESS (push succeeded, pull
            // partially degraded); the saved watermark is strictly below page 2's lowest
            // updated_at (so page 2 -- and anything sharing its boundary millisecond -- is
            // re-fetched next cycle) and never regresses below the original lastSync.
            assertEquals(SyncState.SUCCESS, result.state)
            assertEquals(500, result.collectionPulled)
            val savedMillis = slot<Long>()
            coVerify(exactly = 1) { syncPrefs.saveLastSyncMillis(eq(USER_ID), capture(savedMillis)) }
            assert(savedMillis.captured < page2LowestUpdatedAt) {
                "Watermark ${savedMillis.captured} must stay below page-2's lowest updated_at ($page2LowestUpdatedAt)"
            }
            assert(savedMillis.captured >= LAST_SYNC) { "Watermark must never regress below lastSync" }
        }

    @Test
    fun `given a CancellationException during sync when sync runs then it propagates instead of becoming SyncResult ERROR`() =
        runTest(testDispatcher) {
            // Arrange: simulate real coroutine cancellation partway through the push phase.
            every { collectionDao.getAllSince(any(), any()) } throws CancellationException("scope cancelled")

            // Act + Assert: the CancellationException must propagate out of sync() itself --
            // never be downgraded to a SyncResult(state = ERROR), which would let a cancelled
            // caller (e.g. a screen navigated away from) misinterpret cooperative cancellation as
            // a real sync failure.
            var thrown: CancellationException? = null
            try {
                syncManager.sync(USER_ID)
            } catch (e: CancellationException) {
                thrown = e
            }
            assertNotNull("A CancellationException must propagate out of sync()", thrown)
        }
}
