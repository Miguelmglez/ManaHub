package com.mmg.manahub.feature.trades.data.repository

import com.mmg.manahub.feature.trades.data.local.dao.LocalOpenForTradeDao
import com.mmg.manahub.feature.trades.data.local.entity.LocalOpenForTradeEntity
import com.mmg.manahub.feature.trades.data.remote.OpenForTradeRemoteDataSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [OpenForTradeRepositoryImpl].
 *
 * [LocalOpenForTradeDao] and [OpenForTradeRemoteDataSource] are fully mocked.
 *
 * Covers:
 *  - GROUP 1: addLocal — UUID generation, synced=false, scryfallId and localCollectionId
 *  - GROUP 2: removeLocal — delegates to dao.deleteById
 *  - GROUP 3: migrateLocalToRemote — uses localCollectionId as the userCardId for remote
 *  - GROUP 4: migrateLocalToRemote — empty local → success(0), no remote calls
 *  - GROUP 5: migrateLocalToRemote — remote failure → Result.failure, local not cleared
 */
class OpenForTradeRepositoryImplTest {

    // ── Mocks ─────────────────────────────────────────────────────────────────

    private val dao    = mockk<LocalOpenForTradeDao>(relaxed = true)
    private val remote = mockk<OpenForTradeRemoteDataSource>(relaxed = true)

    private lateinit var repository: OpenForTradeRepositoryImpl

    // ── Constants ─────────────────────────────────────────────────────────────

    private val USER_ID = "user-uuid-001"

    // ── Fixture helpers ───────────────────────────────────────────────────────

    private fun buildEntity(
        id: String = "local-oft-id-001",
        localCollectionId: String = "collection-uuid-001",
        scryfallId: String = "scryfall-card-001",
        synced: Boolean = false,
        createdAt: Long = 1_000L,
    ) = LocalOpenForTradeEntity(
        id = id,
        localCollectionId = localCollectionId,
        scryfallId = scryfallId,
        synced = synced,
        createdAt = createdAt,
    )

    // ── Setup ─────────────────────────────────────────────────────────────────

    @Before
    fun setUp() {
        repository = OpenForTradeRepositoryImpl(dao = dao, remote = remote)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 1 — addLocal: entity construction
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given scryfallId and localCollectionId when addLocal then entity is inserted with those values`() = runTest {
        // Arrange
        val capturedEntity = slot<LocalOpenForTradeEntity>()
        coEvery { dao.insert(capture(capturedEntity)) } returns Unit

        // Act
        val result = repository.addLocal(
            scryfallId = "card-scryfall-001",
            localCollectionId = "collection-uuid-888",
        )

        // Assert
        assertTrue(result.isSuccess)
        val entity = capturedEntity.captured
        assertEquals("card-scryfall-001", entity.scryfallId)
        assertEquals("collection-uuid-888", entity.localCollectionId)
    }

    @Test
    fun `given addLocal when called then entity is inserted with synced false`() = runTest {
        val capturedEntity = slot<LocalOpenForTradeEntity>()
        coEvery { dao.insert(capture(capturedEntity)) } returns Unit

        repository.addLocal(scryfallId = "card-001", localCollectionId = "col-001")

        assertFalse("Entity must be unsynced after addLocal", capturedEntity.captured.synced)
    }

    @Test
    fun `given addLocal when called then entity receives a non-blank UUID id`() = runTest {
        val capturedEntity = slot<LocalOpenForTradeEntity>()
        coEvery { dao.insert(capture(capturedEntity)) } returns Unit

        repository.addLocal(scryfallId = "card-001", localCollectionId = "col-001")

        val generatedId = capturedEntity.captured.id
        assertTrue("id must be non-blank", generatedId.isNotBlank())
        // UUID format: 8-4-4-4-12 chars = 36 total
        assertEquals(36, generatedId.length)
    }

    @Test
    fun `given two addLocal calls then two distinct UUIDs are generated`() = runTest {
        val capturedFirst  = slot<LocalOpenForTradeEntity>()
        val capturedSecond = slot<LocalOpenForTradeEntity>()

        coEvery { dao.insert(capture(capturedFirst)) } returns Unit
        repository.addLocal(scryfallId = "card-a", localCollectionId = "col-a")

        coEvery { dao.insert(capture(capturedSecond)) } returns Unit
        repository.addLocal(scryfallId = "card-b", localCollectionId = "col-b")

        assertTrue(
            "Each addLocal call must produce a unique UUID",
            capturedFirst.captured.id != capturedSecond.captured.id,
        )
    }

    @Test
    fun `given dao insert throws when addLocal then returns Result failure`() = runTest {
        coEvery { dao.insert(any()) } throws RuntimeException("constraint violation")

        val result = repository.addLocal("card-001", "col-001")

        assertTrue(result.isFailure)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 2 — removeLocal
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given valid id when removeLocal then dao deleteById is called with that id`() = runTest {
        val result = repository.removeLocal("oft-entry-id-001")

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { dao.deleteById("oft-entry-id-001") }
    }

    @Test
    fun `given dao deleteById throws when removeLocal then returns Result failure`() = runTest {
        coEvery { dao.deleteById(any()) } throws RuntimeException("not found")

        val result = repository.removeLocal("oft-entry-id-001")

        assertTrue(result.isFailure)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 3 — migrateLocalToRemote: localCollectionId maps to remote userCardId
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given 2 unsynced rows when migrateLocalToRemote then batchAddOpenForTradeEntries receives localCollectionIds`() = runTest {
        // Arrange: localCollectionId is what gets sent to Supabase as the userCardId
        val unsyncedRows = listOf(
            buildEntity(id = "oft-1", localCollectionId = "col-uuid-A"),
            buildEntity(id = "oft-2", localCollectionId = "col-uuid-B"),
        )
        coEvery { dao.getUnsynced() } returns unsyncedRows
        val capturedIds = slot<List<String>>()
        coEvery { remote.batchAddOpenForTradeEntries(capture(capturedIds)) } returns Result.success(Unit)

        // Act
        val result = repository.migrateLocalToRemote(USER_ID)

        // Assert
        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrThrow())
        assertEquals(listOf("col-uuid-A", "col-uuid-B"), capturedIds.captured)
    }

    @Test
    fun `given 3 unsynced rows when migrateLocalToRemote then markSynced is called with all entity ids`() = runTest {
        val unsyncedRows = listOf(
            buildEntity(id = "oft-1", localCollectionId = "col-1"),
            buildEntity(id = "oft-2", localCollectionId = "col-2"),
            buildEntity(id = "oft-3", localCollectionId = "col-3"),
        )
        coEvery { dao.getUnsynced() } returns unsyncedRows
        coEvery { remote.batchAddOpenForTradeEntries(any()) } returns Result.success(Unit)
        val capturedSyncedIds = slot<List<String>>()
        coEvery { dao.markSynced(capture(capturedSyncedIds)) } returns Unit

        repository.migrateLocalToRemote(USER_ID)

        assertEquals(listOf("oft-1", "oft-2", "oft-3"), capturedSyncedIds.captured)
    }

    @Test
    fun `given unsynced rows when migrateLocalToRemote succeeds then clearSynced is called`() = runTest {
        coEvery { dao.getUnsynced() } returns listOf(buildEntity(id = "oft-1"))
        coEvery { remote.batchAddOpenForTradeEntries(any()) } returns Result.success(Unit)

        repository.migrateLocalToRemote(USER_ID)

        coVerify(exactly = 1) { dao.clearSynced() }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 4 — migrateLocalToRemote empty local
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given no unsynced rows when migrateLocalToRemote then returns Result success with 0`() = runTest {
        coEvery { dao.getUnsynced() } returns emptyList()

        val result = repository.migrateLocalToRemote(USER_ID)

        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrThrow())
    }

    @Test
    fun `given no unsynced rows when migrateLocalToRemote then batchAddOpenForTradeEntries is NOT called`() = runTest {
        coEvery { dao.getUnsynced() } returns emptyList()

        repository.migrateLocalToRemote(USER_ID)

        coVerify(exactly = 0) { remote.batchAddOpenForTradeEntries(any()) }
    }

    @Test
    fun `given no unsynced rows when migrateLocalToRemote then markSynced and clearSynced are NOT called`() = runTest {
        coEvery { dao.getUnsynced() } returns emptyList()

        repository.migrateLocalToRemote(USER_ID)

        coVerify(exactly = 0) { dao.markSynced(any()) }
        coVerify(exactly = 0) { dao.clearSynced() }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 5 — migrateLocalToRemote remote failure
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given remote batchAdd fails when migrateLocalToRemote then returns Result failure`() = runTest {
        val unsyncedRows = listOf(buildEntity(id = "oft-1"), buildEntity(id = "oft-2"))
        coEvery { dao.getUnsynced() } returns unsyncedRows
        coEvery { remote.batchAddOpenForTradeEntries(any()) } returns Result.failure(RuntimeException("503"))

        val result = repository.migrateLocalToRemote(USER_ID)

        assertTrue(result.isFailure)
    }

    @Test
    fun `given remote batchAdd fails when migrateLocalToRemote then markSynced is NOT called`() = runTest {
        // getOrThrow() throws inside runCatching → markSynced is never reached
        coEvery { dao.getUnsynced() } returns listOf(buildEntity(id = "oft-1"))
        coEvery { remote.batchAddOpenForTradeEntries(any()) } returns Result.failure(RuntimeException("network"))

        repository.migrateLocalToRemote(USER_ID)

        coVerify(exactly = 0) { dao.markSynced(any()) }
        coVerify(exactly = 0) { dao.clearSynced() }
    }
}
