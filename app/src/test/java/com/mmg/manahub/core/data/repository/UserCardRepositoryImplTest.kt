package com.mmg.manahub.core.data.repository

import com.mmg.manahub.core.data.local.MtgDatabase
import com.mmg.manahub.core.data.local.dao.UserCardCollectionDao
import com.mmg.manahub.core.data.local.entity.UserCardCollectionEntity
import com.mmg.manahub.core.data.local.paging.RemoteKeyDao
import com.mmg.manahub.core.data.remote.collection.CollectionRemoteDataSource
import io.github.jan.supabase.SupabaseClient
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [UserCardRepositoryImpl] — complementary edge cases.
 *
 * Core behaviors (addOrIncrement, updateAttributes, deleteCard, getScryfallIds)
 * are tested in depth in [UserCardRepositoryImplSyncTest].
 * This class focuses on edge cases around entity construction and attribute handling.
 *
 * Covers:
 *  - addOrIncrement: UUID uniqueness, timestamp fields, per-call isolation
 *  - updateAttributes: individual attribute changes, updatedAt independence from createdAt
 *  - deleteCard: soft-delete is idempotent (no crash on repeated call)
 */
class UserCardRepositoryImplTest {

    // ── Mocks ─────────────────────────────────────────────────────────────────

    private val userCardCollectionDao      = mockk<UserCardCollectionDao>(relaxed = true)
    private val collectionRemoteDataSource = mockk<CollectionRemoteDataSource>(relaxed = true)
    private val remoteKeyDao               = mockk<RemoteKeyDao>(relaxed = true)
    private val database                   = mockk<MtgDatabase>(relaxed = true)
    private val supabaseClient             = mockk<SupabaseClient>(relaxed = true)

    private lateinit var repository: UserCardRepositoryImpl

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private fun buildEntity(
        id:               String  = "entity-uuid-001",
        scryfallId:       String  = "card-001",
        userId:           String? = "user-uuid-001",
        quantity:         Int     = 1,
        isFoil:           Boolean = false,
        condition:        String  = "NM",
        language:         String  = "en",
        isAlternativeArt: Boolean = false,
        isForTrade:       Boolean = false,
        isInWishlist:     Boolean = false,
        isDeleted:        Boolean = false,
        updatedAt:        Long    = 1_000L,
        createdAt:        Long    = 1_000L,
    ) = UserCardCollectionEntity(
        id               = id,
        userId           = userId,
        scryfallId       = scryfallId,
        quantity         = quantity,
        isFoil           = isFoil,
        condition        = condition,
        language         = language,
        isAlternativeArt = isAlternativeArt,
        isForTrade       = isForTrade,
        isInWishlist     = isInWishlist,
        isDeleted        = isDeleted,
        updatedAt        = updatedAt,
        createdAt        = createdAt,
    )

    // ── Setup ─────────────────────────────────────────────────────────────────

    @Before
    fun setUp() {
        repository = UserCardRepositoryImpl(
            userCardCollectionDao      = userCardCollectionDao,
            collectionRemoteDataSource = collectionRemoteDataSource,
            remoteKeyDao               = remoteKeyDao,
            database                   = database,
            supabaseClient             = supabaseClient,
            ioDispatcher               = UnconfinedTestDispatcher(),
        )
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 1 — addOrIncrement: UUID and timestamp construction
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given addOrIncrement called twice then two distinct UUIDs are generated`() = runTest {
        val captured1 = slot<UserCardCollectionEntity>()
        val captured2 = slot<UserCardCollectionEntity>()

        every { userCardCollectionDao.upsert(capture(captured1)) } returns 1L andThen 1L

        repository.addOrIncrement("card-a", false, "NM", "en", false, false, false, "user-001")

        every { userCardCollectionDao.upsert(capture(captured2)) } returns 1L

        repository.addOrIncrement("card-b", false, "NM", "en", false, false, false, "user-001")

        assertNotEquals(captured1.captured.id, captured2.captured.id)
    }

    @Test
    fun `given addOrIncrement when entity is built then createdAt and updatedAt are both set`() = runTest {
        val captured = slot<UserCardCollectionEntity>()
        every { userCardCollectionDao.upsert(capture(captured)) } returns 1L

        val before = System.currentTimeMillis()
        repository.addOrIncrement("card-001", false, "NM", "en", false, false, false, "user-001")
        val after = System.currentTimeMillis()

        val entity = captured.captured
        assertTrue("createdAt must be in test window", entity.createdAt in before..after)
        assertTrue("updatedAt must be in test window", entity.updatedAt in before..after)
    }

    @Test
    fun `given addOrIncrement with all boolean flags true then entity reflects all flags`() = runTest {
        val captured = slot<UserCardCollectionEntity>()
        every { userCardCollectionDao.upsert(capture(captured)) } returns 1L

        repository.addOrIncrement(
            scryfallId       = "card-001",
            isFoil           = true,
            condition        = "LP",
            language         = "fr",
            isAlternativeArt = true,
            isForTrade       = true,
            isInWishlist     = true,
            userId           = "user-001",
        )

        val entity = captured.captured
        assertTrue(entity.isFoil)
        assertTrue(entity.isAlternativeArt)
        assertTrue(entity.isForTrade)
        assertTrue(entity.isInWishlist)
        assertEquals("LP", entity.condition)
        assertEquals("fr", entity.language)
    }

    @Test
    fun `given addOrIncrement with all boolean flags false then entity reflects all flags false`() = runTest {
        val captured = slot<UserCardCollectionEntity>()
        every { userCardCollectionDao.upsert(capture(captured)) } returns 1L

        repository.addOrIncrement("card-001", false, "NM", "en", false, false, false, "user-001")

        val entity = captured.captured
        assertFalse(entity.isFoil)
        assertFalse(entity.isAlternativeArt)
        assertFalse(entity.isForTrade)
        assertFalse(entity.isInWishlist)
        assertFalse(entity.isDeleted)
    }

    @Test
    fun `given null userId in addOrIncrement then entity userId is null`() = runTest {
        val captured = slot<UserCardCollectionEntity>()
        every { userCardCollectionDao.upsert(capture(captured)) } returns 1L

        repository.addOrIncrement("card-001", false, "NM", "en", false, false, false, null)

        assertNull(captured.captured.userId)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 2 — updateAttributes: individual field isolation
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given only isForTrade changes when updateAttributes then other fields are preserved`() = runTest {
        val original = buildEntity(
            id           = "e-001",
            scryfallId   = "card-001",
            condition    = "LP",
            language     = "de",
            isFoil       = true,
            isForTrade   = false,
            isInWishlist = false,
            quantity     = 3,
        )
        every { userCardCollectionDao.getById("e-001") } returns original
        val captured = slot<UserCardCollectionEntity>()
        every { userCardCollectionDao.upsert(capture(captured)) } returns 1L

        repository.updateAttributes(id = "e-001", isForTrade = true, isInWishlist = false, quantity = 3)

        val entity = captured.captured
        // Changed field:
        assertTrue(entity.isForTrade)
        // Unchanged fields:
        assertEquals("card-001", entity.scryfallId)
        assertEquals("LP",       entity.condition)
        assertEquals("de",       entity.language)
        assertTrue(entity.isFoil)
        assertEquals(3, entity.quantity)
    }

    @Test
    fun `given updatedAt when updateAttributes then updatedAt is bumped but createdAt is preserved`() = runTest {
        val original = buildEntity(id = "e-001", createdAt = 500L, updatedAt = 100L)
        every { userCardCollectionDao.getById("e-001") } returns original
        val captured = slot<UserCardCollectionEntity>()
        every { userCardCollectionDao.upsert(capture(captured)) } returns 1L

        repository.updateAttributes("e-001", false, false, 1)

        val entity = captured.captured
        assertTrue("updatedAt must be bumped", entity.updatedAt > 100L)
        assertEquals("createdAt must be preserved", 500L, entity.createdAt)
    }

    @Test
    fun `given quantity reduced when updateAttributes then new quantity is stored`() = runTest {
        every { userCardCollectionDao.getById("e-001") } returns buildEntity(id = "e-001", quantity = 10)
        val captured = slot<UserCardCollectionEntity>()
        every { userCardCollectionDao.upsert(capture(captured)) } returns 1L

        repository.updateAttributes("e-001", false, false, 2)

        assertEquals(2, captured.captured.quantity)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 3 — deleteCard: repeated soft-delete is safe
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given deleteCard called twice then softDelete is called twice without crash`() = runTest {
        repository.deleteCard("entity-001")
        repository.deleteCard("entity-001")

        verify(exactly = 2) { userCardCollectionDao.softDelete(eq("entity-001"), any()) }
    }

    @Test
    fun `given deleteCard when called then upsert is never invoked`() = runTest {
        repository.deleteCard("entity-001")

        verify(exactly = 0) { userCardCollectionDao.upsert(any()) }
    }
}
