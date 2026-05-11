package com.mmg.manahub.feature.trades.data.repository

import app.cash.turbine.test
import com.mmg.manahub.feature.trades.data.local.dao.LocalWishlistDao
import com.mmg.manahub.feature.trades.data.local.entity.LocalWishlistEntity
import com.mmg.manahub.feature.trades.data.remote.WishlistRemoteDataSource
import com.mmg.manahub.feature.trades.data.remote.dto.WishlistEntryDto
import com.mmg.manahub.feature.trades.domain.model.WishlistEntry
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [WishlistRepositoryImpl].
 *
 * [LocalWishlistDao] and [WishlistRemoteDataSource] are fully mocked — no Room or network.
 *
 * Covers:
 *  - GROUP 1: observeLocal — entity → domain mapping
 *  - GROUP 2: addLocal — entity construction (synced=false, UUID, correct scryfallId)
 *  - GROUP 3: removeLocal — delegates to dao.deleteById
 *  - GROUP 4: migrateLocalToRemote — success path (3 unsynced rows)
 *  - GROUP 5: migrateLocalToRemote — empty local → returns 0, no remote calls
 *  - GROUP 6: migrateLocalToRemote — remote failure → Result.failure, local NOT marked synced
 */
class WishlistRepositoryImplTest {

    // ── Mocks ─────────────────────────────────────────────────────────────────

    private val dao    = mockk<LocalWishlistDao>(relaxed = true)
    private val remote = mockk<WishlistRemoteDataSource>(relaxed = true)

    private lateinit var repository: WishlistRepositoryImpl

    // ── Constants ─────────────────────────────────────────────────────────────

    private val USER_ID = "user-uuid-001"

    // ── Fixture helpers ───────────────────────────────────────────────────────

    private fun buildEntity(
        id: String = "local-id-001",
        scryfallId: String = "scryfall-card-001",
        quantity: Int = 1,
        matchAnyVariant: Boolean = true,
        isFoil: Boolean? = null,
        condition: String? = null,
        language: String? = null,
        isAltArt: Boolean? = null,
        synced: Boolean = false,
        createdAt: Long = 1_000L,
    ) = LocalWishlistEntity(
        id = id,
        scryfallId = scryfallId,
        quantity = quantity,
        matchAnyVariant = matchAnyVariant,
        isFoil = isFoil,
        condition = condition,
        language = language,
        isAltArt = isAltArt,
        synced = synced,
        createdAt = createdAt,
    )

    private fun buildEntry(
        id: String = "entry-id-001",
        cardId: String = "scryfall-card-001",
        quantity: Int = 1,
        matchAnyVariant: Boolean = true,
        isFoil: Boolean? = null,
        condition: String? = null,
        language: String? = null,
        isAltArt: Boolean? = null,
        createdAt: Long = 1_000L,
    ) = WishlistEntry(
        id = id,
        userId = USER_ID,
        cardId = cardId,
        quantity = quantity,
        matchAnyVariant = matchAnyVariant,
        isFoil = isFoil,
        condition = condition,
        language = language,
        isAltArt = isAltArt,
        createdAt = createdAt,
    )

    // ── Setup ─────────────────────────────────────────────────────────────────

    @Before
    fun setUp() {
        repository = WishlistRepositoryImpl(dao = dao, remote = remote)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 1 — observeLocal: entity → domain mapping
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given dao emits entity with matchAnyVariant true when observeLocal then domain entry has matchAnyVariant true`() = runTest {
        // Arrange
        val entity = buildEntity(id = "e-001", scryfallId = "card-xyz", matchAnyVariant = true)
        every { dao.observeAll() } returns flowOf(listOf(entity))

        // Act + Assert
        repository.observeLocal().test {
            val items = awaitItem()
            assertEquals(1, items.size)
            val entry = items.first()
            assertEquals("e-001", entry.id)
            assertEquals("card-xyz", entry.cardId)
            assertTrue(entry.matchAnyVariant)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given dao emits entity with matchAnyVariant false when observeLocal then domain entry has matchAnyVariant false`() = runTest {
        // Arrange: specific variant requested — narrowing fields are populated
        val entity = buildEntity(
            id = "e-002",
            scryfallId = "card-abc",
            matchAnyVariant = false,
            isFoil = true,
            condition = "LP",
            language = "ja",
            isAltArt = false,
        )
        every { dao.observeAll() } returns flowOf(listOf(entity))

        // Act + Assert
        repository.observeLocal().test {
            val items = awaitItem()
            val entry = items.first()
            assertFalse(entry.matchAnyVariant)
            assertEquals(true, entry.isFoil)
            assertEquals("LP", entry.condition)
            assertEquals("ja", entry.language)
            assertEquals(false, entry.isAltArt)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given dao emits empty list when observeLocal then emits empty list`() = runTest {
        every { dao.observeAll() } returns flowOf(emptyList())

        repository.observeLocal().test {
            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given entity when observeLocal then userId in domain entry is empty string`() = runTest {
        // LocalWishlistEntity has no userId column; toDomain() sets userId = ""
        val entity = buildEntity(id = "e-001")
        every { dao.observeAll() } returns flowOf(listOf(entity))

        repository.observeLocal().test {
            val entry = awaitItem().first()
            assertEquals("", entry.userId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 2 — addLocal: entity construction
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given entry with blank id when addLocal then a UUID is generated for the entity`() = runTest {
        // Arrange: blank id triggers UUID.randomUUID()
        val entry = buildEntry(id = "")
        val capturedEntity = slot<LocalWishlistEntity>()
        coEvery { dao.insert(capture(capturedEntity)) } returns Unit

        // Act
        val result = repository.addLocal(entry)

        // Assert
        assertTrue(result.isSuccess)
        val entity = capturedEntity.captured
        assertTrue("id must be a non-blank UUID", entity.id.isNotBlank())
        // UUID format: 8-4-4-4-12 chars = 36 total
        assertEquals(36, entity.id.length)
    }

    @Test
    fun `given entry with non-blank id when addLocal then that id is preserved in entity`() = runTest {
        val entry = buildEntry(id = "predefined-id-001")
        val capturedEntity = slot<LocalWishlistEntity>()
        coEvery { dao.insert(capture(capturedEntity)) } returns Unit

        repository.addLocal(entry)

        assertEquals("predefined-id-001", capturedEntity.captured.id)
    }

    @Test
    fun `given entry when addLocal then entity has synced false`() = runTest {
        val entry = buildEntry()
        val capturedEntity = slot<LocalWishlistEntity>()
        coEvery { dao.insert(capture(capturedEntity)) } returns Unit

        repository.addLocal(entry)

        assertFalse("Entity must be unsynced after addLocal", capturedEntity.captured.synced)
    }

    @Test
    fun `given entry with specific cardId when addLocal then entity scryfallId matches cardId`() = runTest {
        val entry = buildEntry(cardId = "target-card-scryfall-001")
        val capturedEntity = slot<LocalWishlistEntity>()
        coEvery { dao.insert(capture(capturedEntity)) } returns Unit

        repository.addLocal(entry)

        assertEquals("target-card-scryfall-001", capturedEntity.captured.scryfallId)
    }

    @Test
    fun `given existing entry with same attributes when addLocal then increment quantity and call update`() = runTest {
        // Arrange
        val existing = buildEntity(id = "existing-id", quantity = 1)
        val entry = buildEntry(cardId = existing.scryfallId)
        
        coEvery { dao.getByAttributes(any(), any(), any(), any(), any(), any()) } returns existing
        val capturedEntity = slot<LocalWishlistEntity>()
        coEvery { dao.update(capture(capturedEntity)) } returns Unit

        // Act
        repository.addLocal(entry)

        // Assert
        coVerify(exactly = 1) { dao.update(any()) }
        coVerify(exactly = 0) { dao.insert(any()) }
        assertEquals(2, capturedEntity.captured.quantity)
    }

    @Test
    fun `given no existing entry with same attributes when addLocal then call insert`() = runTest {
        // Arrange
        val entry = buildEntry()
        coEvery { dao.getByAttributes(any(), any(), any(), any(), any(), any()) } returns null
        coEvery { dao.insert(any()) } returns Unit

        // Act
        repository.addLocal(entry)

        // Assert
        coVerify(exactly = 1) { dao.insert(any()) }
        coVerify(exactly = 0) { dao.update(any()) }
    }

    @Test
    fun `given dao insert throws when addLocal then returns Result failure`() = runTest {
        val entry = buildEntry()
        coEvery { dao.insert(any()) } throws RuntimeException("disk full")

        val result = repository.addLocal(entry)

        assertTrue(result.isFailure)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 3 — removeLocal
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given valid id when removeLocal then dao deleteById is called with correct id`() = runTest {
        // Act
        val result = repository.removeLocal("entry-to-remove")

        // Assert
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { dao.deleteById("entry-to-remove") }
    }

    @Test
    fun `given dao deleteById throws when removeLocal then returns Result failure`() = runTest {
        coEvery { dao.deleteById(any()) } throws RuntimeException("sqlite error")

        val result = repository.removeLocal("some-id")

        assertTrue(result.isFailure)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 4 — migrateLocalToRemote success path
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given 3 unsynced rows when migrateLocalToRemote then returns Result success with count 3`() = runTest {
        // Arrange
        val unsyncedRows = listOf(
            buildEntity(id = "u-1", scryfallId = "card-1"),
            buildEntity(id = "u-2", scryfallId = "card-2"),
            buildEntity(id = "u-3", scryfallId = "card-3"),
        )
        coEvery { dao.getUnsynced() } returns unsyncedRows
        coEvery { remote.batchAddWishlistEntries(any()) } returns Result.success(Unit)

        // Act
        val result = repository.migrateLocalToRemote(USER_ID)

        // Assert
        assertTrue(result.isSuccess)
        assertEquals(3, result.getOrThrow())
    }

    @Test
    fun `given 3 unsynced rows when migrateLocalToRemote then batchAddWishlistEntries receives correct DTOs`() = runTest {
        // Arrange
        val unsyncedRows = listOf(
            buildEntity(id = "u-1", scryfallId = "card-1", matchAnyVariant = true),
            buildEntity(id = "u-2", scryfallId = "card-2", matchAnyVariant = false, isFoil = true),
            buildEntity(id = "u-3", scryfallId = "card-3"),
        )
        coEvery { dao.getUnsynced() } returns unsyncedRows
        val capturedDtos = slot<List<WishlistEntryDto>>()
        coEvery { remote.batchAddWishlistEntries(capture(capturedDtos)) } returns Result.success(Unit)

        // Act
        repository.migrateLocalToRemote(USER_ID)

        // Assert: all 3 DTOs sent, userId injected, scryfallId mapped to cardId
        val dtos = capturedDtos.captured
        assertEquals(3, dtos.size)
        assertTrue(dtos.all { it.userId == USER_ID })
        assertEquals("card-1", dtos[0].cardId)
        assertEquals("card-2", dtos[1].cardId)
        assertEquals("card-3", dtos[2].cardId)
        assertTrue(dtos[1].isFoil == true)
    }

    @Test
    fun `given 3 unsynced rows when migrateLocalToRemote then markSynced is called with all ids`() = runTest {
        // Arrange
        val unsyncedRows = listOf(
            buildEntity(id = "u-1"),
            buildEntity(id = "u-2"),
            buildEntity(id = "u-3"),
        )
        coEvery { dao.getUnsynced() } returns unsyncedRows
        coEvery { remote.batchAddWishlistEntries(any()) } returns Result.success(Unit)
        val capturedIds = slot<List<String>>()
        coEvery { dao.markSynced(capture(capturedIds)) } returns Unit

        // Act
        repository.migrateLocalToRemote(USER_ID)

        // Assert
        assertEquals(listOf("u-1", "u-2", "u-3"), capturedIds.captured)
    }

    @Test
    fun `given unsynced rows when migrateLocalToRemote succeeds then clearSynced is called`() = runTest {
        // Arrange
        val unsyncedRows = listOf(buildEntity(id = "u-1"), buildEntity(id = "u-2"))
        coEvery { dao.getUnsynced() } returns unsyncedRows
        coEvery { remote.batchAddWishlistEntries(any()) } returns Result.success(Unit)

        // Act
        repository.migrateLocalToRemote(USER_ID)

        // Assert: both markSynced and clearSynced were called
        coVerify(exactly = 1) { dao.markSynced(any()) }
        coVerify(exactly = 1) { dao.clearSynced() }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 5 — migrateLocalToRemote empty local
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given no unsynced rows when migrateLocalToRemote then returns Result success with count 0`() = runTest {
        coEvery { dao.getUnsynced() } returns emptyList()

        val result = repository.migrateLocalToRemote(USER_ID)

        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrThrow())
    }

    @Test
    fun `given no unsynced rows when migrateLocalToRemote then batchAddWishlistEntries is NOT called`() = runTest {
        coEvery { dao.getUnsynced() } returns emptyList()

        repository.migrateLocalToRemote(USER_ID)

        coVerify(exactly = 0) { remote.batchAddWishlistEntries(any()) }
    }

    @Test
    fun `given no unsynced rows when migrateLocalToRemote then markSynced and clearSynced are NOT called`() = runTest {
        coEvery { dao.getUnsynced() } returns emptyList()

        repository.migrateLocalToRemote(USER_ID)

        coVerify(exactly = 0) { dao.markSynced(any()) }
        coVerify(exactly = 0) { dao.clearSynced() }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 6 — migrateLocalToRemote remote failure
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given remote batchAdd fails when migrateLocalToRemote then returns Result failure`() = runTest {
        // Arrange
        val unsyncedRows = listOf(buildEntity(id = "u-1"), buildEntity(id = "u-2"))
        coEvery { dao.getUnsynced() } returns unsyncedRows
        coEvery { remote.batchAddWishlistEntries(any()) } returns Result.failure(RuntimeException("network error"))

        // Act
        val result = repository.migrateLocalToRemote(USER_ID)

        // Assert
        assertTrue(result.isFailure)
    }

    @Test
    fun `given remote batchAdd fails when migrateLocalToRemote then markSynced is NOT called`() = runTest {
        // getOrThrow() throws inside runCatching → markSynced is never reached
        val unsyncedRows = listOf(buildEntity(id = "u-1"))
        coEvery { dao.getUnsynced() } returns unsyncedRows
        coEvery { remote.batchAddWishlistEntries(any()) } returns Result.failure(RuntimeException("500"))

        // Act
        repository.migrateLocalToRemote(USER_ID)

        // Assert: local state unchanged — rows remain unsynced
        coVerify(exactly = 0) { dao.markSynced(any()) }
        coVerify(exactly = 0) { dao.clearSynced() }
    }
}
