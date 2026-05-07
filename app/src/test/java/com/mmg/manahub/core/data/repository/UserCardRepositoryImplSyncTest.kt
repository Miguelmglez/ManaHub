package com.mmg.manahub.core.data.repository

import com.mmg.manahub.core.data.local.MtgDatabase
import com.mmg.manahub.core.data.local.dao.UserCardCollectionDao
import com.mmg.manahub.core.data.local.entity.UserCardCollectionEntity
import com.mmg.manahub.core.data.local.paging.RemoteKeyDao
import com.mmg.manahub.core.data.remote.collection.CollectionRemoteDataSource
import com.mmg.manahub.feature.auth.domain.repository.AuthRepository
import io.github.jan.supabase.SupabaseClient
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [UserCardRepositoryImpl].
 *
 * The repository is local-first: all mutations write to Room and bump
 * [UserCardCollectionEntity.updatedAt]. Sync to Supabase is
 * [com.mmg.manahub.core.sync.SyncManager]'s responsibility and is NOT tested here.
 *
 * Covers:
 *  - GROUP 1: addOrIncrement — entity construction, DAO delegation
 *  - GROUP 2: updateAttributes — field propagation, early return when not found
 *  - GROUP 3: deleteCard — soft-delete delegation (not hard delete)
 *  - GROUP 4: getScryfallIds — filters out deleted rows, returns distinct IDs
 */
class UserCardRepositoryImplSyncTest {

    // ── Mocks ─────────────────────────────────────────────────────────────────

    private val userCardCollectionDao  = mockk<UserCardCollectionDao>(relaxed = true)
    private val collectionRemoteDataSource = mockk<CollectionRemoteDataSource>(relaxed = true)
    private val remoteKeyDao           = mockk<RemoteKeyDao>(relaxed = true)
    private val database               = mockk<MtgDatabase>(relaxed = true)
    private val supabaseClient         = mockk<SupabaseClient>(relaxed = true)
    private val authRepository         = mockk<AuthRepository>(relaxed = true)

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
            authRepository             = authRepository,
            ioDispatcher               = UnconfinedTestDispatcher(),
        )
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 1 — addOrIncrement
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given card info when addOrIncrement then userCardCollectionDao upsert is called once`() = runTest {
        repository.addOrIncrement(
            scryfallId       = "card-001",
            isFoil           = false,
            condition        = "NM",
            language         = "en",
            isAlternativeArt = false,
            isForTrade       = false,
            isInWishlist     = false,
            userId           = "user-001",
        )

        verify(exactly = 1) { userCardCollectionDao.upsert(any()) }
    }

    @Test
    fun `given card info when addOrIncrement then entity has correct scryfallId and attributes`() = runTest {
        val captured = slot<UserCardCollectionEntity>()
        every { userCardCollectionDao.upsert(capture(captured)) } returns 1L

        repository.addOrIncrement(
            scryfallId       = "bolt-scryfall-id",
            isFoil           = true,
            condition        = "LP",
            language         = "ja",
            isAlternativeArt = true,
            isForTrade       = true,
            isInWishlist     = false,
            userId           = "user-001",
        )

        val entity = captured.captured
        assertEquals("bolt-scryfall-id", entity.scryfallId)
        assertTrue(entity.isFoil)
        assertEquals("LP", entity.condition)
        assertEquals("ja", entity.language)
        assertTrue(entity.isAlternativeArt)
        assertTrue(entity.isForTrade)
        assertFalse(entity.isInWishlist)
        assertEquals("user-001", entity.userId)
    }

    @Test
    fun `given card info when addOrIncrement then entity isDeleted is false`() = runTest {
        val captured = slot<UserCardCollectionEntity>()
        every { userCardCollectionDao.upsert(capture(captured)) } returns 1L

        repository.addOrIncrement("card-001", false, "NM", "en", false, false, false, "user-001")

        assertFalse(captured.captured.isDeleted)
    }

    @Test
    fun `given card info when addOrIncrement then entity id is a non-blank UUID`() = runTest {
        val captured = slot<UserCardCollectionEntity>()
        every { userCardCollectionDao.upsert(capture(captured)) } returns 1L

        repository.addOrIncrement("card-001", false, "NM", "en", false, false, false, "user-001")

        assertTrue(captured.captured.id.isNotBlank())
        assertEquals(36, captured.captured.id.length)   // UUID length
    }

    @Test
    fun `given null userId when addOrIncrement then entity userId is null (guest session)`() = runTest {
        val captured = slot<UserCardCollectionEntity>()
        every { userCardCollectionDao.upsert(capture(captured)) } returns 1L

        repository.addOrIncrement("card-001", false, "NM", "en", false, false, false, null)

        assertEquals(null, captured.captured.userId)
    }

    @Test
    fun `given quantity when addOrIncrement then entity quantity defaults to 1`() = runTest {
        val captured = slot<UserCardCollectionEntity>()
        every { userCardCollectionDao.upsert(capture(captured)) } returns 1L

        repository.addOrIncrement("card-001", false, "NM", "en", false, false, false, "user-001")

        assertEquals(1, captured.captured.quantity)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 2 — updateAttributes
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given entity exists when updateAttributes then userCardCollectionDao upsert is called`() = runTest {
        every { userCardCollectionDao.getById("entity-001") } returns buildEntity(id = "entity-001")

        repository.updateAttributes(id = "entity-001", isForTrade = true, isInWishlist = false, quantity = 3)

        verify(exactly = 1) { userCardCollectionDao.upsert(any()) }
    }

    @Test
    fun `given entity exists when updateAttributes then updated fields are propagated`() = runTest {
        every { userCardCollectionDao.getById("entity-001") } returns buildEntity(
            id = "entity-001", isForTrade = false, isInWishlist = false, quantity = 1
        )
        val captured = slot<UserCardCollectionEntity>()
        every { userCardCollectionDao.upsert(capture(captured)) } returns 1L

        repository.updateAttributes(id = "entity-001", isForTrade = true, isInWishlist = true, quantity = 5)

        assertTrue(captured.captured.isForTrade)
        assertTrue(captured.captured.isInWishlist)
        assertEquals(5, captured.captured.quantity)
    }

    @Test
    fun `given entity exists when updateAttributes then updatedAt is bumped`() = runTest {
        every { userCardCollectionDao.getById("entity-001") } returns buildEntity(updatedAt = 100L)
        val captured = slot<UserCardCollectionEntity>()
        every { userCardCollectionDao.upsert(capture(captured)) } returns 1L

        repository.updateAttributes("entity-001", false, false, 2)

        assertTrue("updatedAt must be bumped", captured.captured.updatedAt > 100L)
    }

    @Test
    fun `given entity not found when updateAttributes then upsert is NOT called`() = runTest {
        every { userCardCollectionDao.getById("entity-001") } returns null

        repository.updateAttributes("entity-001", true, false, 2)

        verify(exactly = 0) { userCardCollectionDao.upsert(any()) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 3 — deleteCard (soft delete)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given entry id when deleteCard then userCardCollectionDao softDelete is called`() = runTest {
        repository.deleteCard("entity-001")

        verify(exactly = 1) { userCardCollectionDao.softDelete(eq("entity-001"), any()) }
    }

    @Test
    fun `given entry id when deleteCard then upsert is NOT called (no hard replace)`() = runTest {
        repository.deleteCard("entity-001")

        verify(exactly = 0) { userCardCollectionDao.upsert(any()) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 4 — getScryfallIds
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given non-deleted rows when getScryfallIds then returns their scryfallIds`() = runTest {
        every { userCardCollectionDao.getAllSince("", 0L) } returns listOf(
            buildEntity(scryfallId = "card-a", isDeleted = false),
            buildEntity(scryfallId = "card-b", isDeleted = false),
        )

        val ids = repository.getScryfallIds()

        assertTrue(ids.contains("card-a"))
        assertTrue(ids.contains("card-b"))
    }

    @Test
    fun `given deleted rows when getScryfallIds then deleted rows are excluded`() = runTest {
        every { userCardCollectionDao.getAllSince("", 0L) } returns listOf(
            buildEntity(id = "e1", scryfallId = "card-a", isDeleted = false),
            buildEntity(id = "e2", scryfallId = "card-b", isDeleted = true),
        )

        val ids = repository.getScryfallIds()

        assertTrue(ids.contains("card-a"))
        assertFalse(ids.contains("card-b"))
    }

    @Test
    fun `given duplicate scryfallIds when getScryfallIds then result is deduplicated`() = runTest {
        every { userCardCollectionDao.getAllSince("", 0L) } returns listOf(
            buildEntity(id = "e1", scryfallId = "card-a", isFoil = false, isDeleted = false),
            buildEntity(id = "e2", scryfallId = "card-a", isFoil = true,  isDeleted = false),
        )

        val ids = repository.getScryfallIds()

        assertEquals(1, ids.size)
        assertEquals("card-a", ids[0])
    }

    @Test
    fun `given empty collection when getScryfallIds then returns empty list`() = runTest {
        every { userCardCollectionDao.getAllSince("", 0L) } returns emptyList()

        val ids = repository.getScryfallIds()

        assertTrue(ids.isEmpty())
    }
}
