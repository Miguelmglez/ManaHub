package com.mmg.manahub.core.sync

import com.mmg.manahub.core.data.local.SyncPreferencesStore
import com.mmg.manahub.core.data.local.dao.DeckDao
import com.mmg.manahub.core.data.local.dao.UserCardCollectionDao
import com.mmg.manahub.core.data.local.entity.UserCardCollectionEntity
import com.mmg.manahub.core.data.remote.collection.CollectionRemoteDataSource
import com.mmg.manahub.core.data.remote.collection.UserCardCollectionDto
import com.mmg.manahub.core.data.remote.decks.DeckRemoteDataSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Focused unit tests for the **collection sync path** inside [SyncManager].
 *
 * These tests complement [SyncManagerTest] by adding deeper collection-specific
 * coverage:
 *  - GROUP 1: Push — only new/modified rows reach Supabase
 *  - GROUP 2: Pull — deleted remote cards propagate into Room
 *  - GROUP 3: Guest → Login merge (assignUserIdAndSync collection path)
 *  - GROUP 4: Multiple-card scenarios and boundary conditions
 *
 * All dependencies are mocked with MockK — no real database or network calls.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CollectionSyncTest {

    // ── Dispatcher ────────────────────────────────────────────────────────────

    private val testDispatcher = StandardTestDispatcher()

    // ── Mocks ─────────────────────────────────────────────────────────────────

    private val collectionDao    = mockk<UserCardCollectionDao>(relaxed = true)
    private val deckDao          = mockk<DeckDao>(relaxed = true)
    private val collectionRemote = mockk<CollectionRemoteDataSource>(relaxed = true)
    private val deckRemote       = mockk<DeckRemoteDataSource>(relaxed = true)
    private val syncPrefs        = mockk<SyncPreferencesStore>(relaxed = true)

    // ── Constants ─────────────────────────────────────────────────────────────

    private val USER_ID   = "user-uuid-001"
    private val LAST_SYNC = 1_000L
    private val CARD_ID_A = "scryfall-id-a"
    private val CARD_ID_B = "scryfall-id-b"
    private val CARD_ID_C = "scryfall-id-c"

    // ── Fixture helpers ───────────────────────────────────────────────────────

    private fun buildEntity(
        id:        String  = "col-uuid-001",
        userId:    String? = USER_ID,
        scryfallId: String = CARD_ID_A,
        quantity:  Int     = 2,
        updatedAt: Long    = LAST_SYNC + 100L,
        isDeleted: Boolean = false,
    ) = UserCardCollectionEntity(
        id               = id,
        userId           = userId,
        scryfallId       = scryfallId,
        quantity         = quantity,
        isFoil           = false,
        condition        = "NM",
        language         = "en",
        isAlternativeArt = false,
        isForTrade       = false,
        isInWishlist     = false,
        isDeleted        = isDeleted,
        updatedAt        = updatedAt,
        createdAt        = 500L,
    )

    private fun buildDto(
        id:        String = "col-uuid-001",
        userId:    String = USER_ID,
        scryfallId: String = CARD_ID_A,
        quantity:  Int    = 2,
        updatedAt: Long   = LAST_SYNC + 100L,
        isDeleted: Boolean = false,
    ) = UserCardCollectionDto(
        id               = id,
        userId           = userId,
        scryfallId       = scryfallId,
        quantity         = quantity,
        isFoil           = false,
        condition        = "NM",
        language         = "en",
        isAlternativeArt = false,
        isForTrade       = false,
        isInWishlist     = false,
        isDeleted        = isDeleted,
        updatedAt        = updatedAt,
        createdAt        = 500L,
    )

    // ── Setup ─────────────────────────────────────────────────────────────────

    private lateinit var syncManager: SyncManager

    @Before
    fun setUp() {
        // Watermark: LAST_SYNC; no local changes; empty remote responses by default
        coEvery { syncPrefs.getLastSyncMillis(any()) } returns LAST_SYNC
        every { collectionDao.getAllSince(any(), any()) } returns emptyList()
        every { deckDao.getDecksSince(any(), any()) } returns emptyList()
        every { deckDao.getDeckCards(any()) } returns emptyList()
        coEvery { collectionRemote.getChangesSince(any()) } returns Result.success(emptyList())
        coEvery { deckRemote.getDeckChangesSince(any()) } returns Result.success(emptyList())
        coEvery { collectionRemote.batchUpsert(any()) } returns Result.success(Unit)
        coEvery { deckRemote.batchUpsertDecks(any()) } returns Result.success(Unit)
        coEvery { deckRemote.upsertDeckCards(any(), any()) } returns Result.success(Unit)

        syncManager = SyncManager(
            collectionDao    = collectionDao,
            deckDao          = deckDao,
            collectionRemote = collectionRemote,
            deckRemote       = deckRemote,
            syncPrefs        = syncPrefs,
            ioDispatcher     = testDispatcher,
        )
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 1 — Push: new / modified collection cards
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given new collection cards when sync then batchUpsert sends them to Supabase`() = runTest(testDispatcher) {
        // Arrange: two new local cards modified after the watermark
        val entityA = buildEntity(id = "id-a", scryfallId = CARD_ID_A, updatedAt = LAST_SYNC + 10L)
        val entityB = buildEntity(id = "id-b", scryfallId = CARD_ID_B, updatedAt = LAST_SYNC + 20L)
        every { collectionDao.getAllSince(USER_ID, LAST_SYNC) } returns listOf(entityA, entityB)
        val capturedDtos = slot<List<UserCardCollectionDto>>()
        coEvery { collectionRemote.batchUpsert(capture(capturedDtos)) } returns Result.success(Unit)

        // Act
        val result = syncManager.sync(USER_ID)
        advanceUntilIdle()

        // Assert
        assertEquals(SyncState.SUCCESS, result.state)
        assertEquals(2, result.collectionPushed)
        val ids = capturedDtos.captured.map { it.id }
        assertTrue(ids.contains("id-a"))
        assertTrue(ids.contains("id-b"))
    }

    @Test
    fun `given no local changes when sync then batchUpsert is NOT called and collectionPushed is 0`() = runTest(testDispatcher) {
        // Arrange: getAllSince returns empty (nothing modified since watermark)
        every { collectionDao.getAllSince(USER_ID, LAST_SYNC) } returns emptyList()

        // Act
        val result = syncManager.sync(USER_ID)
        advanceUntilIdle()

        // Assert
        assertEquals(0, result.collectionPushed)
        coVerify(exactly = 0) { collectionRemote.batchUpsert(any()) }
    }

    @Test
    fun `given collection push fails when sync then result is ERROR and watermark is NOT saved`() = runTest(testDispatcher) {
        // Arrange
        val entity = buildEntity()
        every { collectionDao.getAllSince(USER_ID, LAST_SYNC) } returns listOf(entity)
        coEvery { collectionRemote.batchUpsert(any()) } returns Result.failure(RuntimeException("503 Service Unavailable"))

        // Act
        val result = syncManager.sync(USER_ID)
        advanceUntilIdle()

        // Assert
        assertEquals(SyncState.ERROR, result.state)
        assertEquals("503 Service Unavailable", result.error)
        coVerify(exactly = 0) { syncPrefs.saveLastSyncMillis(any(), any()) }
    }

    @Test
    fun `given multiple cards with different updatedAt when sync then all modified cards are included in push`() = runTest(testDispatcher) {
        // Arrange: only rows where updatedAt > LAST_SYNC are returned by getAllSince
        // We trust the DAO filter; here we verify all returned rows reach the remote
        val entities = (1..5).map { i ->
            buildEntity(
                id         = "id-$i",
                scryfallId = "scryfall-$i",
                updatedAt  = LAST_SYNC + (i * 100L),
            )
        }
        every { collectionDao.getAllSince(USER_ID, LAST_SYNC) } returns entities
        val capturedDtos = slot<List<UserCardCollectionDto>>()
        coEvery { collectionRemote.batchUpsert(capture(capturedDtos)) } returns Result.success(Unit)

        // Act
        syncManager.sync(USER_ID)
        advanceUntilIdle()

        // Assert: all 5 rows pushed
        assertEquals(5, capturedDtos.captured.size)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 2 — Pull: remote deleted cards propagate into Room
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given remote deleted card newer than local when sync then Room entity is upserted with isDeleted true`() = runTest(testDispatcher) {
        // Arrange: server sends a soft-delete tombstone that is newer than the local row
        val localEntity  = buildEntity(updatedAt = 2_000L, isDeleted = false)
        val remoteDeleted = buildDto(updatedAt = 5_000L, isDeleted = true)
        every { collectionDao.getById(remoteDeleted.id) } returns localEntity
        coEvery { collectionRemote.getChangesSince(LAST_SYNC) } returns Result.success(listOf(remoteDeleted))
        val capturedEntity = slot<UserCardCollectionEntity>()
        every { collectionDao.upsert(capture(capturedEntity)) } returns 1L

        // Act
        val result = syncManager.sync(USER_ID)
        advanceUntilIdle()

        // Assert: tombstone written to Room
        assertEquals(1, result.collectionPulled)
        assertTrue("Room entity must have isDeleted=true after pull", capturedEntity.captured.isDeleted)
    }

    @Test
    fun `given remote deleted card older than local when sync then Room entity is NOT overwritten`() = runTest(testDispatcher) {
        // Arrange: local row is newer → LWW says local wins
        val localEntity   = buildEntity(updatedAt = 9_000L, isDeleted = false)
        val remoteDeleted = buildDto(updatedAt = 1_000L, isDeleted = true)
        every { collectionDao.getById(remoteDeleted.id) } returns localEntity
        coEvery { collectionRemote.getChangesSince(LAST_SYNC) } returns Result.success(listOf(remoteDeleted))

        // Act
        val result = syncManager.sync(USER_ID)
        advanceUntilIdle()

        // Assert: local wins, upsert skipped
        assertEquals(0, result.collectionPulled)
        verify(exactly = 0) { collectionDao.upsert(any()) }
    }

    @Test
    fun `given new remote card not present locally when sync then it is inserted into Room`() = runTest(testDispatcher) {
        // Arrange: server sends a card that does not exist locally yet (first pull)
        val remoteNew = buildDto(id = "brand-new-id", scryfallId = CARD_ID_C)
        every { collectionDao.getById("brand-new-id") } returns null    // not in Room
        coEvery { collectionRemote.getChangesSince(LAST_SYNC) } returns Result.success(listOf(remoteNew))

        // Act
        val result = syncManager.sync(USER_ID)
        advanceUntilIdle()

        // Assert: new row inserted
        assertEquals(1, result.collectionPulled)
        verify(exactly = 1) { collectionDao.upsert(any()) }
    }

    @Test
    fun `given multiple remote cards with mixed LWW outcomes when sync then only newer remote rows are upserted`() = runTest(testDispatcher) {
        // Arrange:
        //  - dto-A: remote is newer → should be upserted
        //  - dto-B: local is newer  → should be skipped
        //  - dto-C: no local row    → should be inserted
        val dtoA = buildDto(id = "id-a", scryfallId = CARD_ID_A, updatedAt = 9_000L)
        val dtoB = buildDto(id = "id-b", scryfallId = CARD_ID_B, updatedAt = 1_000L)
        val dtoC = buildDto(id = "id-c", scryfallId = CARD_ID_C, updatedAt = 4_000L)

        every { collectionDao.getById("id-a") } returns buildEntity(id = "id-a", updatedAt = 2_000L)   // remote newer
        every { collectionDao.getById("id-b") } returns buildEntity(id = "id-b", updatedAt = 8_000L)   // local newer
        every { collectionDao.getById("id-c") } returns null                                             // new

        coEvery { collectionRemote.getChangesSince(LAST_SYNC) } returns Result.success(listOf(dtoA, dtoB, dtoC))

        // Act
        val result = syncManager.sync(USER_ID)
        advanceUntilIdle()

        // Assert: 2 rows pulled (A and C), B was skipped
        assertEquals(2, result.collectionPulled)
        verify(exactly = 2) { collectionDao.upsert(any()) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 3 — Guest → Login: collection path
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given guest collection rows when assignUserIdAndSync then collectionDao assignUserId is called`() = runTest(testDispatcher) {
        // Act
        syncManager.assignUserIdAndSync(USER_ID)
        advanceUntilIdle()

        // Assert: guest rows (userId=null) receive the new userId
        verify(exactly = 1) { collectionDao.assignUserId(eq(USER_ID), any()) }
    }

    @Test
    fun `given guest-to-login transition when assignUserIdAndSync then watermark is cleared`() = runTest(testDispatcher) {
        // clearLastSyncMillis is only called when there are migrated rows (collectionMigrated > 0)
        every { collectionDao.assignUserId(any(), any()) } returns 1

        // Act
        syncManager.assignUserIdAndSync(USER_ID)
        advanceUntilIdle()

        // Assert: clear watermark forces full push
        coVerify(exactly = 1) { syncPrefs.clearLastSyncMillis(USER_ID) }
    }

    @Test
    fun `given guest local cards when assignUserIdAndSync then those cards are pushed to Supabase after userId assignment`() = runTest(testDispatcher) {
        // Arrange: after clearLastSyncMillis, getLastSyncMillis returns 0L (full upload)
        coEvery { syncPrefs.getLastSyncMillis(USER_ID) } returns 0L
        val guestEntities = listOf(
            buildEntity(id = "g-1", userId = USER_ID, scryfallId = CARD_ID_A),
            buildEntity(id = "g-2", userId = USER_ID, scryfallId = CARD_ID_B),
        )
        // getAllSince(userId, 0L) — full pull
        every { collectionDao.getAllSince(USER_ID, 0L) } returns guestEntities
        val capturedDtos = slot<List<UserCardCollectionDto>>()
        coEvery { collectionRemote.batchUpsert(capture(capturedDtos)) } returns Result.success(Unit)

        // Act
        val result = syncManager.assignUserIdAndSync(USER_ID)
        advanceUntilIdle()

        // Assert: both guest cards were pushed
        assertEquals(SyncState.SUCCESS, result.state)
        assertEquals(2, result.collectionPushed)
        val ids = capturedDtos.captured.map { it.id }
        assertTrue(ids.contains("g-1"))
        assertTrue(ids.contains("g-2"))
    }

    @Test
    fun `given remote has newer card when sync then remote card is merged into Room via LWW`() = runTest(testDispatcher) {
        // assignUserIdAndSync only pushes; pull behaviour is tested via sync()
        val remoteDto = buildDto(id = "server-id", scryfallId = CARD_ID_C, updatedAt = 99_999L)
        every { collectionDao.getById("server-id") } returns null   // not present locally
        coEvery { collectionRemote.getChangesSince(LAST_SYNC) } returns Result.success(listOf(remoteDto))

        // Act
        val result = syncManager.sync(USER_ID)
        advanceUntilIdle()

        // Assert: remote card inserted locally
        assertEquals(1, result.collectionPulled)
        verify(exactly = 1) { collectionDao.upsert(any()) }
    }

    @Test
    fun `given conflicting local and remote card when sync then LWW decides winner`() = runTest(testDispatcher) {
        // Scenario: same card exists both locally and remotely; remote is newer → remote wins
        // assignUserIdAndSync only pushes; LWW pull behaviour is tested via sync()
        val localEntity  = buildEntity(id = "conflict-id", scryfallId = CARD_ID_A, updatedAt = 3_000L)
        val remoteDto    = buildDto(id = "conflict-id", scryfallId = CARD_ID_A, updatedAt = 7_000L)

        every { collectionDao.getAllSince(USER_ID, LAST_SYNC) } returns listOf(localEntity)
        every { collectionDao.getById("conflict-id") } returns localEntity
        coEvery { collectionRemote.getChangesSince(LAST_SYNC) } returns Result.success(listOf(remoteDto))
        val capturedEntity = slot<UserCardCollectionEntity>()
        every { collectionDao.upsert(capture(capturedEntity)) } returns 1L

        // Act
        syncManager.sync(USER_ID)
        advanceUntilIdle()

        // Assert: remote row (updatedAt=7000) was written into Room
        assertEquals(7_000L, capturedEntity.captured.updatedAt)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 4 — Boundary / edge cases
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given pull returns empty list when sync then collectionPulled is 0`() = runTest(testDispatcher) {
        // Arrange: remote has no changes since watermark
        coEvery { collectionRemote.getChangesSince(LAST_SYNC) } returns Result.success(emptyList())

        // Act
        val result = syncManager.sync(USER_ID)
        advanceUntilIdle()

        // Assert
        assertEquals(0, result.collectionPulled)
        verify(exactly = 0) { collectionDao.upsert(any()) }
    }

    @Test
    fun `given pull fails when sync then result is ERROR`() = runTest(testDispatcher) {
        // Arrange
        coEvery { collectionRemote.getChangesSince(any()) } returns
            Result.failure(RuntimeException("connection refused"))

        // Act
        val result = syncManager.sync(USER_ID)
        advanceUntilIdle()

        // Assert
        assertEquals(SyncState.ERROR, result.state)
        assertEquals("connection refused", result.error)
    }

    @Test
    fun `given successful full cycle when sync then SyncResult has correct pushed and pulled counts`() = runTest(testDispatcher) {
        // Arrange: 2 pushed, 1 pulled
        val localRows = listOf(
            buildEntity(id = "local-1", updatedAt = LAST_SYNC + 10L),
            buildEntity(id = "local-2", updatedAt = LAST_SYNC + 20L),
        )
        val remoteNew = buildDto(id = "remote-new", scryfallId = CARD_ID_C)
        every { collectionDao.getAllSince(USER_ID, LAST_SYNC) } returns localRows
        every { collectionDao.getById("remote-new") } returns null
        coEvery { collectionRemote.getChangesSince(LAST_SYNC) } returns Result.success(listOf(remoteNew))

        // Act
        val result = syncManager.sync(USER_ID)
        advanceUntilIdle()

        // Assert
        assertEquals(SyncState.SUCCESS, result.state)
        assertEquals(2, result.collectionPushed)
        assertEquals(1, result.collectionPulled)
        assertNull(result.error)
    }

    @Test
    fun `given getLastSyncMillis returns 0 when first sync then getAllSince is queried with 0L watermark`() = runTest(testDispatcher) {
        // Arrange: first ever sync → watermark = 0
        coEvery { syncPrefs.getLastSyncMillis(USER_ID) } returns 0L
        val capturedSince = slot<Long>()
        every { collectionDao.getAllSince(USER_ID, capture(capturedSince)) } returns emptyList()

        // Act
        syncManager.sync(USER_ID)
        advanceUntilIdle()

        // Assert: DAO queried with watermark 0L → full upload
        assertEquals(0L, capturedSince.captured)
    }

    @Test
    fun `given single collection row with isFoil true when sync then isFoil is preserved in pushed DTO`() = runTest(testDispatcher) {
        // Arrange: foil card
        val foilEntity = UserCardCollectionEntity(
            id               = "foil-id",
            userId           = USER_ID,
            scryfallId       = CARD_ID_A,
            quantity         = 1,
            isFoil           = true,
            condition        = "NM",
            language         = "ja",
            isAlternativeArt = true,
            isForTrade       = false,
            isInWishlist     = false,
            isDeleted        = false,
            updatedAt        = LAST_SYNC + 1L,
            createdAt        = 100L,
        )
        every { collectionDao.getAllSince(USER_ID, LAST_SYNC) } returns listOf(foilEntity)
        val capturedDtos = slot<List<UserCardCollectionDto>>()
        coEvery { collectionRemote.batchUpsert(capture(capturedDtos)) } returns Result.success(Unit)

        // Act
        syncManager.sync(USER_ID)
        advanceUntilIdle()

        // Assert: all attributes preserved through toDto() mapping
        val dto = capturedDtos.captured.first()
        assertTrue(dto.isFoil)
        assertTrue(dto.isAlternativeArt)
        assertEquals("ja", dto.language)
        assertEquals(USER_ID, dto.userId)
    }
}
