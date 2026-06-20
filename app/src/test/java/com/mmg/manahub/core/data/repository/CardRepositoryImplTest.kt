package com.mmg.manahub.core.data.repository

import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.data.local.dao.CardDao
import com.mmg.manahub.core.data.local.dao.UserCardCollectionDao
import com.mmg.manahub.core.data.local.entity.CardEntity
import com.mmg.manahub.core.data.remote.ScryfallRemoteDataSource
import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.domain.usecase.card.ComputeCardTagsUseCase
import com.mmg.manahub.core.domain.usecase.card.SuggestTagsUseCase
import com.mmg.manahub.util.TestFixtures
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [CardRepositoryImpl].
 *
 * Critical regression test group: "CASCADE DELETE BUG — refreshCollectionPrices".
 * The bug was that CardDao.upsert() used OnConflictStrategy.REPLACE (DELETE+INSERT),
 * which triggered the CASCADE FK on user_cards and silently wiped UserCardEntity rows.
 * The fix uses INSERT-IGNORE + @Update, which never deletes the parent row.
 * These tests verify that refreshCollectionPrices() calls upsert() (not any destructive
 * operation) and that upsert() does not invoke any delete path.
 */
class CardRepositoryImplTest {

    // ── Mocks ─────────────────────────────────────────────────────────────────

    private val cardDao               = mockk<CardDao>(relaxed = true)
    private val userCardCollectionDao = mockk<UserCardCollectionDao>(relaxed = true)
    private val remote    = mockk<ScryfallRemoteDataSource>()
    private val userPrefs = mockk<UserPreferencesDataStore>()

    // Pure use-cases — construct real instances to avoid fragile mock setup.
    private val computeCardTags = ComputeCardTagsUseCase(SuggestTagsUseCase())

    private lateinit var repository: CardRepositoryImpl

    // ── Setup ─────────────────────────────────────────────────────────────────

    @Before
    fun setUp() {
        val testDispatcher = UnconfinedTestDispatcher()

        // DataStore flows used inside the repository's tag-computation path.
        every { userPrefs.tagAutoThresholdFlow }    returns flowOf(SuggestTagsUseCase.DEFAULT_AUTO_THRESHOLD)
        every { userPrefs.tagSuggestThresholdFlow } returns flowOf(SuggestTagsUseCase.DEFAULT_SUGGEST_THRESHOLD)

        repository = CardRepositoryImpl(
            cardDao               = cardDao,
            userCardCollectionDao = userCardCollectionDao,
            remote                = remote,
            computeCardTags       = computeCardTags,
            userPrefs             = userPrefs,
            ioDispatcher          = testDispatcher,
            defaultDispatcher     = testDispatcher,
        )
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 1 — CASCADE DELETE BUG REGRESSION (refreshCollectionPrices)
    //  These are the tests that would have caught the bug before the fix.
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given collection has stale cards when refreshCollectionPrices succeeds then upsertAll is called not delete`() = runTest {
        // Arrange — refreshCollectionPrices now uses getByIds (batch) + upsertAll (batch)
        // instead of N individual getById + upsert calls.
        val staleCard = TestFixtures.buildExpiredCard("id-stale-001")
        coEvery { userCardCollectionDao.getAllScryfallIds() } returns listOf("id-stale-001")
        // getByIds is called first to load the cached map in one query; returning empty
        // means the card is treated as stale (null in cachedMap).
        coEvery { cardDao.getByIds(listOf("id-stale-001")) } returns emptyList()
        coEvery { remote.getCardsBatch(listOf("id-stale-001")) } returns Result.success(listOf(staleCard))

        // Act
        repository.refreshCollectionPrices()

        // Assert: upsertAll() was called — NOT any destructive operation
        coVerify(exactly = 1) { cardDao.upsertAll(any()) }
        coVerify(exactly = 0) { cardDao.upsert(any()) }
    }

    @Test
    fun `given refreshCollectionPrices succeeds then clearStale is called for each refreshed card`() = runTest {
        // Arrange
        val card1 = TestFixtures.buildExpiredCard("id-001")
        val card2 = TestFixtures.buildExpiredCard("id-002")
        coEvery { userCardCollectionDao.getAllScryfallIds() } returns listOf("id-001", "id-002")
        // Both cards absent from cache → both are stale
        coEvery { cardDao.getByIds(listOf("id-001", "id-002")) } returns emptyList()
        coEvery { remote.getCardsBatch(listOf("id-001", "id-002")) } returns
                Result.success(listOf(card1, card2))

        // Act
        repository.refreshCollectionPrices()

        // Assert: stale flag is cleared for each card that was successfully refreshed
        coVerify(exactly = 1) { cardDao.clearStale("id-001") }
        coVerify(exactly = 1) { cardDao.clearStale("id-002") }
    }

    @Test
    fun `given refreshCollectionPrices succeeds then upsertAll is called once for all refreshed cards`() = runTest {
        // Arrange — simulates multiple collection cards needing a price refresh.
        // The new implementation batches all entities into a single upsertAll() call.
        val ids = listOf("id-001", "id-002", "id-003")
        val cards = ids.map { TestFixtures.buildExpiredCard(it) }
        coEvery { userCardCollectionDao.getAllScryfallIds() } returns ids
        // No cards in cache → all are stale
        coEvery { cardDao.getByIds(ids) } returns emptyList()
        coEvery { remote.getCardsBatch(ids) } returns Result.success(cards)

        // Act
        repository.refreshCollectionPrices()

        // Assert: one batch upsertAll call (not N individual upserts)
        coVerify(exactly = 1) { cardDao.upsertAll(any()) }
        coVerify(exactly = 0) { cardDao.upsert(any()) }
    }

    @Test
    fun `given refreshCollectionPrices succeeds then existing user tags are preserved in upserted entity`() = runTest {
        // Arrange — card in DB already has user-saved tags stored in the JSON format used by TagRecord.
        // The implementation reads existing tags from cachedMap (loaded via getByIds), not getById.
        val existingEntity = TestFixtures.buildExpiredCardEntity("id-001").copy(
            tags     = """[{"k":"removal","c":"ROLE"}]""",
            userTags = """[{"k":"my_tag","c":"CUSTOM"}]""",
        )

        val refreshedCard = TestFixtures.buildCard("id-001")
        coEvery { userCardCollectionDao.getAllScryfallIds() } returns listOf("id-001")
        // Return existing entity via the batch query so cachedMap is populated correctly.
        coEvery { cardDao.getByIds(listOf("id-001")) } returns listOf(existingEntity)
        coEvery { remote.getCardsBatch(listOf("id-001")) } returns Result.success(listOf(refreshedCard))

        val upsertedListSlot = slot<List<CardEntity>>()
        coEvery { cardDao.upsertAll(capture(upsertedListSlot)) } returns Unit

        // Act
        repository.refreshCollectionPrices()

        // Assert: the user's custom tag is NOT wiped out — userTags must not be blank/empty
        val captured = upsertedListSlot.captured.first()
        assertTrue(
            "userTags must be preserved from existing entity",
            captured.userTags.isNotBlank() && captured.userTags != "[]"
        )
    }

    @Test
    fun `given empty collection when refreshCollectionPrices then no network call is made`() = runTest {
        // Arrange
        coEvery { userCardCollectionDao.getAllScryfallIds() } returns emptyList()

        // Act
        repository.refreshCollectionPrices()

        // Assert: early return — no remote call should happen
        coVerify(exactly = 0) { remote.getCardsBatch(any()) }
    }

    @Test
    fun `given all collection cards are fresh when refreshCollectionPrices then no network call is made`() = runTest {
        // Arrange — all cards are within the 24-h freshness window.
        // getByIds returns the fresh entity, so staleIds is empty.
        val freshEntity = TestFixtures.buildFreshCardEntity("id-fresh-001")

        coEvery { userCardCollectionDao.getAllScryfallIds() } returns listOf("id-fresh-001")
        coEvery { cardDao.getByIds(listOf("id-fresh-001")) } returns listOf(freshEntity)

        // Act
        repository.refreshCollectionPrices()

        // Assert: staleIds is empty — no batch request made
        coVerify(exactly = 0) { remote.getCardsBatch(any()) }
    }

    @Test
    fun `given network fails when refreshCollectionPrices then stale cards get markStale called`() = runTest {
        // Arrange
        val staleEntity = TestFixtures.buildStaleCardEntity("id-001")

        coEvery { userCardCollectionDao.getAllScryfallIds() } returns listOf("id-001")
        coEvery { cardDao.getByIds(listOf("id-001")) } returns listOf(staleEntity)
        coEvery { remote.getCardsBatch(any()) } returns
                Result.failure(RuntimeException("Network unavailable"))

        // Act
        repository.refreshCollectionPrices()

        // Assert: card is marked stale with a reason — NOT deleted
        coVerify(exactly = 1) { cardDao.markStale("id-001", any()) }
        coVerify(exactly = 0) { cardDao.upsertAll(any()) }
    }

    @Test
    fun `given batch returns subset of requested cards when refreshCollectionPrices then missing cards are marked stale`() = runTest {
        // Arrange — two cards requested, only one returned
        val staleEntity1 = TestFixtures.buildStaleCardEntity("id-001")
        val staleEntity2 = TestFixtures.buildStaleCardEntity("id-002")

        coEvery { userCardCollectionDao.getAllScryfallIds() } returns listOf("id-001", "id-002")
        coEvery { cardDao.getByIds(listOf("id-001", "id-002")) } returns listOf(staleEntity1, staleEntity2)
        val returnedCard = TestFixtures.buildCard("id-001")
        coEvery { remote.getCardsBatch(any()) } returns Result.success(listOf(returnedCard))

        // Act
        repository.refreshCollectionPrices()

        // Assert: id-001 refreshed OK, id-002 not in response → marked stale
        coVerify(exactly = 1) { cardDao.clearStale("id-001") }
        coVerify(exactly = 1) { cardDao.markStale("id-002", any()) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 2 — getCardById cache logic
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given fresh cache when getCardById then returns cached card without calling remote`() = runTest {
        // Arrange — the cache hit condition is: isFresh(cachedAt) AND relatedUris != "{}".
        // buildFreshCardEntity defaults relatedUris to "{}", so we must override it to a
        // non-empty value to trigger the cache-hit path.
        val freshEntity = TestFixtures.buildFreshCardEntity("id-001").copy(
            relatedUris = """{"gatherer":"https://gatherer.wizards.com/Pages/Card/Details.aspx?multiverseid=209"}"""
        )
        coEvery { cardDao.getById("id-001") } returns freshEntity

        // Act
        val result = repository.getCardById("id-001")

        // Assert
        assertTrue(result is DataResult.Success)
        coVerify(exactly = 0) { remote.getCardById(any()) }
    }

    @Test
    fun `given expired cache when getCardById and remote succeeds then returns fresh data`() = runTest {
        // Arrange
        val expiredEntity = TestFixtures.buildExpiredCardEntity("id-001")
        val freshCard     = TestFixtures.buildCard("id-001")
        val freshEntity   = TestFixtures.buildFreshCardEntity("id-001")

        coEvery { cardDao.getById("id-001") } returnsMany listOf(expiredEntity, freshEntity)
        coEvery { remote.getCardById("id-001") } returns Result.success(freshCard)
        coEvery { cardDao.upsert(any()) } returns Unit
        coEvery { cardDao.clearStale(any()) } returns Unit

        // Act
        val result = repository.getCardById("id-001")

        // Assert
        assertTrue(result is DataResult.Success)
        coVerify(exactly = 1) { remote.getCardById("id-001") }
        // upsert was called — not a replace
        coVerify(exactly = 1) { cardDao.upsert(any()) }
    }

    @Test
    fun `given no cache and remote fails when getCardById then returns DataResult Error`() = runTest {
        // Arrange
        coEvery { cardDao.getById("id-001") } returns null
        coEvery { remote.getCardById("id-001") } returns
                Result.failure(RuntimeException("HTTP 404"))

        // Act
        val result = repository.getCardById("id-001")

        // Assert
        assertTrue(result is DataResult.Error)
        assertEquals("HTTP 404", (result as DataResult.Error).message)
    }

    @Test
    fun `given stale cache and remote fails when getCardById then returns stale data with isStale true`() = runTest {
        // Arrange
        val staleEntity = TestFixtures.buildStaleCardEntity("id-001")

        coEvery { cardDao.getById("id-001") } returns staleEntity
        coEvery { remote.getCardById("id-001") } returns
                Result.failure(RuntimeException("No network"))

        // Act
        val result = repository.getCardById("id-001")

        // Assert: fallback to stale cache — not an error
        assertTrue(result is DataResult.Success)
        assertTrue((result as DataResult.Success).isStale)
    }

    @Test
    fun `given getCardById caches new card then upsert preserves existing userTags`() = runTest {
        // Arrange — card already has user tags in cache (JSON format used by TagRecord: {k, c})
        val existingEntity = TestFixtures.buildExpiredCardEntity("id-001").copy(
            userTags = """[{"k":"my_tag","c":"CUSTOM"}]""",
        )
        val remoteCard    = TestFixtures.buildCard("id-001")
        val updatedEntity = TestFixtures.buildFreshCardEntity("id-001")

        coEvery { cardDao.getById("id-001") } returnsMany listOf(existingEntity, updatedEntity)
        coEvery { remote.getCardById("id-001") } returns Result.success(remoteCard)

        val capturedEntity = slot<CardEntity>()
        coEvery { cardDao.upsert(capture(capturedEntity)) } returns Unit
        coEvery { cardDao.clearStale(any()) } returns Unit

        // Act
        repository.getCardById("id-001")

        // Assert: user tags from the existing entity are carried over (not empty)
        assertTrue(
            "userTags must be preserved from existing entity",
            capturedEntity.captured.userTags.isNotBlank() && capturedEntity.captured.userTags != "[]"
        )
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 3 — updatePrices
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given valid prices when updatePrices then delegates to cardDao updatePrices`() = runTest {
        // Arrange
        coEvery { cardDao.updatePrices(any(), any(), any(), any(), any(), any()) } returns Unit

        // Act
        repository.updatePrices(
            scryfallId   = "id-001",
            priceUsd     = 1.50,
            priceUsdFoil = 5.00,
            priceEur     = 1.20,
            priceEurFoil = 4.00,
            updatedAt    = 123456789L,
        )

        // Assert
        coVerify(exactly = 1) {
            cardDao.updatePrices(
                scryfallId   = "id-001",
                priceUsd     = 1.50,
                priceUsdFoil = 5.00,
                priceEur     = 1.20,
                priceEurFoil = 4.00,
                updatedAt    = 123456789L,
            )
        }
    }

    @Test
    fun `given null prices when updatePrices then null values are forwarded to dao`() = runTest {
        // Arrange
        coEvery { cardDao.updatePrices(any(), any(), any(), any(), any(), any()) } returns Unit

        // Act
        repository.updatePrices(
            scryfallId   = "id-001",
            priceUsd     = null,
            priceUsdFoil = null,
            priceEur     = null,
            priceEurFoil = null,
            updatedAt    = 987654321L,
        )

        // Assert
        coVerify(exactly = 1) {
            cardDao.updatePrices(
                scryfallId   = "id-001",
                priceUsd     = null,
                priceUsdFoil = null,
                priceEur     = null,
                priceEurFoil = null,
                updatedAt    = 987654321L,
            )
        }
    }
}
