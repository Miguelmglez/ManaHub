package com.mmg.manahub.feature.communitydecks.data

import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.data.local.dao.CommunityDeckCacheDao
import com.mmg.manahub.core.data.local.entity.CommunityDeckCacheEntity
import com.mmg.manahub.feature.communitydecks.data.remote.ArchidektApi
import com.mmg.manahub.feature.communitydecks.data.remote.ArchidektRequestQueue
import com.mmg.manahub.feature.communitydecks.data.remote.dto.ArchidektCardDto
import com.mmg.manahub.feature.communitydecks.data.remote.dto.ArchidektCardEntryDto
import com.mmg.manahub.feature.communitydecks.data.remote.dto.ArchidektDeckDetailDto
import com.mmg.manahub.feature.communitydecks.data.remote.dto.ArchidektDeckSummaryDto
import com.mmg.manahub.feature.communitydecks.data.remote.dto.ArchidektOracleCardDto
import com.mmg.manahub.feature.communitydecks.data.remote.dto.ArchidektOwnerDto
import com.mmg.manahub.feature.communitydecks.data.remote.dto.ArchidektSearchResultDto
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.feature.communitydecks.data.remote.toCacheEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

/**
 * Unit tests for [CommunityDecksRepositoryImpl].
 *
 * Verifies the cache-first / stale-fallback strategy, HTTP error mapping, and
 * interaction with the DAO and API.
 *
 * The [ArchidektRequestQueue] is mocked so that `execute` simply calls the block
 * directly (no rate-limiting / retry overhead in tests).
 */
class CommunityDecksRepositoryImplTest {

    private val api: ArchidektApi = mockk()
    private val requestQueue: ArchidektRequestQueue = mockk()
    private val cacheDao: CommunityDeckCacheDao = mockk(relaxUnitFun = true)
    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var repository: CommunityDecksRepositoryImpl

    // ── Fixtures ────────────────────────────────────────────────────────────

    private val testDeckId = 12345

    private fun buildDto(id: Int = testDeckId): ArchidektDeckDetailDto =
        ArchidektDeckDetailDto(
            id = id,
            name = "Test Deck",
            description = "A test deck",
            deckFormat = 3, // commander
            owner = ArchidektOwnerDto(id = 1, username = "TestUser", avatar = ""),
            viewCount = 42,
            createdAt = "2026-01-01",
            updatedAt = "2026-06-01",
            cards = listOf(
                ArchidektCardEntryDto(
                    quantity = 1,
                    categories = listOf("Commander"),
                    card = ArchidektCardDto(
                        oracleCard = ArchidektOracleCardDto(
                            name = "Sol Ring",
                            uid = "oracle-001",
                        ),
                    ),
                ),
            ),
        )

    /** Builds a cache entity from a DTO (same mapper the production code uses). */
    private fun buildCacheEntity(
        id: Int = testDeckId,
        cachedAt: Long = System.currentTimeMillis(),
    ): CommunityDeckCacheEntity = buildDto(id).toCacheEntity().copy(cachedAt = cachedAt)

    private fun buildHttpException(code: Int): HttpException {
        val response = Response.error<Any>(code, "".toResponseBody(null))
        return HttpException(response)
    }

    @Before
    fun setUp() {
        // The repository's catch blocks log telemetry via FirebaseCrashlytics (outside any
        // runCatching), which throws "Default FirebaseApp is not initialized" without a static mock.
        mockkStatic(FirebaseCrashlytics::class)
        val crashlytics = mockk<FirebaseCrashlytics>(relaxed = true)
        every { FirebaseCrashlytics.getInstance() } returns crashlytics

        // Make requestQueue.execute transparent — just call the block.
        coEvery { requestQueue.execute(any<suspend () -> Any>()) } coAnswers {
            firstArg<suspend () -> Any>().invoke()
        }

        repository = CommunityDecksRepositoryImpl(
            api = api,
            requestQueue = requestQueue,
            cacheDao = cacheDao,
            ioDispatcher = testDispatcher,
        )
    }

    @After
    fun tearDown() {
        unmockkStatic(FirebaseCrashlytics::class)
    }

    // ── Group 1: Cache hit (fresh) ──────────────────────────────────────────

    @Test
    fun `given fresh cache when getDeckById then returns cached data without API call`() = runTest {
        // Arrange — cache entry created just now (fresh).
        val freshEntity = buildCacheEntity(cachedAt = System.currentTimeMillis())
        coEvery { cacheDao.getById(testDeckId) } returns freshEntity

        // Act
        val result = repository.getDeckById(testDeckId)

        // Assert
        assertTrue(result is DataResult.Success)
        val success = result as DataResult.Success
        assertEquals("Test Deck", success.data.name)
        assertFalse(success.isStale)

        // API should never be called when cache is fresh.
        coVerify(exactly = 0) { api.getDeckById(any()) }
    }

    @Test
    fun `given fresh cache when getDeckById then does not insert into cache`() = runTest {
        // Arrange
        val freshEntity = buildCacheEntity(cachedAt = System.currentTimeMillis())
        coEvery { cacheDao.getById(testDeckId) } returns freshEntity

        // Act
        repository.getDeckById(testDeckId)

        // Assert — no re-caching on a hit.
        coVerify(exactly = 0) { cacheDao.insert(any()) }
    }

    // ── Group 2: Cache miss (empty) — network success ───────────────────────

    @Test
    fun `given no cache when getDeckById then fetches from API and caches result`() = runTest {
        // Arrange — no cache entry.
        coEvery { cacheDao.getById(testDeckId) } returns null
        coEvery { api.getDeckById(testDeckId) } returns buildDto()

        // Act
        val result = repository.getDeckById(testDeckId)

        // Assert
        assertTrue(result is DataResult.Success)
        val success = result as DataResult.Success
        assertEquals("Test Deck", success.data.name)
        assertEquals("commander", success.data.format)
        assertFalse(success.isStale)

        // Must cache the fetched response.
        coVerify(exactly = 1) { cacheDao.insert(any()) }
    }

    @Test
    fun `given stale cache when getDeckById then fetches from API`() = runTest {
        // Arrange — stale cache entry (well beyond 24h).
        val staleTime = System.currentTimeMillis() - 48 * 60 * 60 * 1_000L
        val staleEntity = buildCacheEntity(cachedAt = staleTime)
        coEvery { cacheDao.getById(testDeckId) } returns staleEntity
        coEvery { api.getDeckById(testDeckId) } returns buildDto()

        // Act
        val result = repository.getDeckById(testDeckId)

        // Assert — fresh network data returned.
        assertTrue(result is DataResult.Success)
        assertFalse((result as DataResult.Success).isStale)
        coVerify(exactly = 1) { api.getDeckById(testDeckId) }
    }

    // ── Group 3: Network error with stale cache fallback ────────────────────

    @Test
    fun `given HTTP error with stale cache when getDeckById then returns stale data`() = runTest {
        // Arrange — first call returns null (stale cache check),
        // API throws, then second call returns stale entry.
        val staleTime = System.currentTimeMillis() - 48 * 60 * 60 * 1_000L
        val staleEntity = buildCacheEntity(cachedAt = staleTime)
        coEvery { cacheDao.getById(testDeckId) } returns staleEntity
        coEvery { api.getDeckById(testDeckId) } throws buildHttpException(500)

        // Act
        val result = repository.getDeckById(testDeckId)

        // Assert — stale fallback.
        assertTrue(result is DataResult.Success)
        val success = result as DataResult.Success
        assertTrue(success.isStale)
        assertEquals("Test Deck", success.data.name)
    }

    @Test
    fun `given generic exception with stale cache when getDeckById then returns stale data`() = runTest {
        // Arrange
        val staleTime = System.currentTimeMillis() - 48 * 60 * 60 * 1_000L
        val staleEntity = buildCacheEntity(cachedAt = staleTime)
        coEvery { cacheDao.getById(testDeckId) } returns staleEntity
        coEvery { api.getDeckById(testDeckId) } throws java.io.IOException("Network unreachable")

        // Act
        val result = repository.getDeckById(testDeckId)

        // Assert
        assertTrue(result is DataResult.Success)
        assertTrue((result as DataResult.Success).isStale)
    }

    // ── Group 4: Network error with no cache ────────────────────────────────

    @Test
    fun `given HTTP error with no cache when getDeckById then returns Error`() = runTest {
        // Arrange — no cache at all.
        coEvery { cacheDao.getById(testDeckId) } returns null
        coEvery { api.getDeckById(testDeckId) } throws buildHttpException(500)

        // Act
        val result = repository.getDeckById(testDeckId)

        // Assert
        assertTrue(result is DataResult.Error)
        assertTrue((result as DataResult.Error).message.contains("500"))
    }

    @Test
    fun `given generic exception with no cache when getDeckById then returns Error`() = runTest {
        // Arrange
        coEvery { cacheDao.getById(testDeckId) } returns null
        coEvery { api.getDeckById(testDeckId) } throws RuntimeException("Parse failure")

        // Act
        val result = repository.getDeckById(testDeckId)

        // Assert
        assertTrue(result is DataResult.Error)
        assertEquals("Parse failure", (result as DataResult.Error).message)
    }

    @Test
    fun `given exception with null message and no cache then returns Unknown error`() = runTest {
        // Arrange
        coEvery { cacheDao.getById(testDeckId) } returns null
        coEvery { api.getDeckById(testDeckId) } throws RuntimeException()

        // Act
        val result = repository.getDeckById(testDeckId)

        // Assert
        assertTrue(result is DataResult.Error)
        assertEquals("Unknown error", (result as DataResult.Error).message)
    }

    // ── Group 5: HTTP 404 ───────────────────────────────────────────────────

    @Test
    fun `given HTTP 404 with no cache when getDeckById then returns not found error`() = runTest {
        // Arrange
        coEvery { cacheDao.getById(testDeckId) } returns null
        coEvery { api.getDeckById(testDeckId) } throws buildHttpException(404)

        // Act
        val result = repository.getDeckById(testDeckId)

        // Assert
        assertTrue(result is DataResult.Error)
        assertEquals("Deck not found on Archidekt", (result as DataResult.Error).message)
    }

    @Test
    fun `given HTTP 404 with stale cache when getDeckById then returns stale data not error`() = runTest {
        // Arrange — even on 404, stale cache wins.
        val staleTime = System.currentTimeMillis() - 48 * 60 * 60 * 1_000L
        val staleEntity = buildCacheEntity(cachedAt = staleTime)
        coEvery { cacheDao.getById(testDeckId) } returns staleEntity
        coEvery { api.getDeckById(testDeckId) } throws buildHttpException(404)

        // Act
        val result = repository.getDeckById(testDeckId)

        // Assert
        assertTrue(result is DataResult.Success)
        assertTrue((result as DataResult.Success).isStale)
    }

    // ── Group 6: HTTP 429 rate limit ────────────────────────────────────────

    @Test
    fun `given HTTP 429 with no cache when getDeckById then returns rate limit error`() = runTest {
        // Arrange
        coEvery { cacheDao.getById(testDeckId) } returns null
        coEvery { api.getDeckById(testDeckId) } throws buildHttpException(429)

        // Act
        val result = repository.getDeckById(testDeckId)

        // Assert
        assertTrue(result is DataResult.Error)
        assertEquals("Too many requests. Please try again later.", (result as DataResult.Error).message)
    }

    // ── Group 7: Domain mapping verification ────────────────────────────────

    @Test
    fun `given successful API response then domain model is correctly mapped`() = runTest {
        // Arrange
        coEvery { cacheDao.getById(testDeckId) } returns null
        coEvery { api.getDeckById(testDeckId) } returns buildDto()

        // Act
        val result = repository.getDeckById(testDeckId) as DataResult.Success

        // Assert — verify domain model fields.
        val deck = result.data
        assertEquals(testDeckId, deck.archidektId)
        assertEquals("Test Deck", deck.name)
        assertEquals("A test deck", deck.description)
        assertEquals("commander", deck.format)
        assertEquals("TestUser", deck.owner.username)
        assertEquals(42, deck.viewCount)
        assertEquals(1, deck.cards.size)
        assertEquals("Sol Ring", deck.cards.first().name)
        assertEquals("https://archidekt.com/decks/$testDeckId", deck.sourceUrl)
    }

    @Test
    fun `given API response with no owner then owner defaults to Unknown`() = runTest {
        // Arrange
        val dtoNoOwner = buildDto().copy(owner = null)
        coEvery { cacheDao.getById(testDeckId) } returns null
        coEvery { api.getDeckById(testDeckId) } returns dtoNoOwner

        // Act
        val result = repository.getDeckById(testDeckId) as DataResult.Success

        // Assert
        assertEquals("Unknown", result.data.owner.username)
        assertEquals(0, result.data.owner.id)
    }

    // ── Group 8: searchDecks — success ─────────────────────────────────────

    private fun buildSearchResultDto(
        count: Int = 42,
        next: String? = "https://archidekt.com/api/decks/v3/?page=2",
        results: List<ArchidektDeckSummaryDto> = listOf(
            ArchidektDeckSummaryDto(
                id = 100,
                name = "Test Commander Deck",
                size = 100,
                deckFormat = 3, // commander
                owner = ArchidektOwnerDto(id = 1, username = "testuser", avatar = ""),
                viewCount = 500,
                createdAt = "2024-01-01",
                updatedAt = "2024-06-01",
                colors = mapOf("W" to 1, "U" to 1),
            ),
        ),
    ): ArchidektSearchResultDto = ArchidektSearchResultDto(
        count = count,
        next = next,
        results = results,
    )

    @Test
    fun `given successful API response when searchDecks then returns mapped domain result`() = runTest {
        // Arrange
        coEvery { api.searchDecks(any(), any(), any(), any(), any()) } returns buildSearchResultDto()

        // Act
        val result = repository.searchDecks("Sol Ring", null, null, 1, 20)

        // Assert
        assertTrue(result is DataResult.Success)
        val success = result as DataResult.Success
        assertEquals(42, success.data.totalCount)
        assertTrue(success.data.hasMore)
        assertEquals(1, success.data.decks.size)

        val deck = success.data.decks.first()
        assertEquals(100, deck.archidektId)
        assertEquals("Test Commander Deck", deck.name)
        assertEquals(100, deck.size)
        assertEquals("commander", deck.format)
        assertEquals("testuser", deck.owner.username)
        assertEquals(500, deck.viewCount)
        assertEquals(listOf("W", "U"), deck.colorIdentity)
    }

    @Test
    fun `given last page when searchDecks then hasMore is false`() = runTest {
        // Arrange — next is null → last page.
        coEvery { api.searchDecks(any(), any(), any(), any(), any()) } returns buildSearchResultDto(
            next = null,
        )

        // Act
        val result = repository.searchDecks("Sol Ring", null, null, 1, 20) as DataResult.Success

        // Assert
        assertFalse(result.data.hasMore)
    }

    // ── Group 9: searchDecks — format filter passthrough ───────────────────

    @Test
    fun `given deckFormat filter when searchDecks then passes it to API`() = runTest {
        // Arrange
        coEvery { api.searchDecks(any(), any(), any(), any(), any()) } returns buildSearchResultDto()

        // Act
        repository.searchDecks("Sol Ring", 3, "-viewCount", 1, 20)

        // Assert — verify the exact arguments passed through to the API.
        coVerify(exactly = 1) {
            api.searchDecks("Sol Ring", 3, "-viewCount", 1, 20)
        }
    }

    // ── Group 10: searchDecks — timeout detection (count = -1) ─────────────

    @Test
    fun `given count minus one when searchDecks then returns timeout error`() = runTest {
        // Arrange — Archidekt signals server-side statement timeout with count = -1.
        coEvery { api.searchDecks(any(), any(), any(), any(), any()) } returns buildSearchResultDto(
            count = -1,
            results = emptyList(),
        )

        // Act
        val result = repository.searchDecks("Lightning Bolt", null, null, 1, 20)

        // Assert
        assertTrue(result is DataResult.Error)
        val error = result as DataResult.Error
        assertTrue(error.message.contains("timed out", ignoreCase = true))
    }

    // ── Group 11: searchDecks — HTTP errors ────────────────────────────────

    @Test
    fun `given HTTP 500 when searchDecks then returns search failed error`() = runTest {
        // Arrange
        coEvery { api.searchDecks(any(), any(), any(), any(), any()) } throws buildHttpException(500)

        // Act
        val result = repository.searchDecks("Sol Ring", null, null, 1, 20)

        // Assert
        assertTrue(result is DataResult.Error)
        val error = result as DataResult.Error
        assertTrue(error.message.contains("500"))
    }

    @Test
    fun `given HTTP 429 when searchDecks then returns rate limit error`() = runTest {
        // Arrange
        coEvery { api.searchDecks(any(), any(), any(), any(), any()) } throws buildHttpException(429)

        // Act
        val result = repository.searchDecks("Sol Ring", null, null, 1, 20)

        // Assert
        assertTrue(result is DataResult.Error)
        assertEquals("Too many requests. Please try again later.", (result as DataResult.Error).message)
    }

    // ── Group 12: searchDecks — generic exception ──────────────────────────

    @Test
    fun `given generic exception when searchDecks then returns error with message`() = runTest {
        // Arrange
        coEvery { api.searchDecks(any(), any(), any(), any(), any()) } throws RuntimeException("Parse failed")

        // Act
        val result = repository.searchDecks("Sol Ring", null, null, 1, 20)

        // Assert
        assertTrue(result is DataResult.Error)
        assertEquals("Parse failed", (result as DataResult.Error).message)
    }

    @Test
    fun `given exception with null message when searchDecks then returns fallback error`() = runTest {
        // Arrange
        coEvery { api.searchDecks(any(), any(), any(), any(), any()) } throws RuntimeException()

        // Act
        val result = repository.searchDecks("Sol Ring", null, null, 1, 20)

        // Assert
        assertTrue(result is DataResult.Error)
        assertEquals("Search failed", (result as DataResult.Error).message)
    }

    // ── Group 13: searchDecks — no-owner summary mapping ───────────────────

    @Test
    fun `given search result with no owner then summary owner defaults to Unknown`() = runTest {
        // Arrange
        val noOwnerSummary = ArchidektDeckSummaryDto(
            id = 200,
            name = "Orphan Deck",
            size = 60,
            deckFormat = 1,
            owner = null,
            viewCount = 10,
            createdAt = "2024-01-01",
            updatedAt = "2024-01-01",
            colors = emptyMap(),
        )
        coEvery { api.searchDecks(any(), any(), any(), any(), any()) } returns ArchidektSearchResultDto(
            count = 1,
            next = null,
            results = listOf(noOwnerSummary),
        )

        // Act
        val result = repository.searchDecks("Test", null, null, 1, 20) as DataResult.Success

        // Assert
        assertEquals("Unknown", result.data.decks.first().owner.username)
        assertEquals(0, result.data.decks.first().owner.id)
    }

    @Test
    fun `given search result with empty colors then colorIdentity is empty`() = runTest {
        // Arrange
        val noColorSummary = ArchidektDeckSummaryDto(
            id = 300,
            name = "Colorless Deck",
            size = 100,
            deckFormat = 3,
            owner = ArchidektOwnerDto(id = 1, username = "user", avatar = ""),
            viewCount = 5,
            createdAt = "2024-01-01",
            updatedAt = "2024-01-01",
            colors = emptyMap(),
        )
        coEvery { api.searchDecks(any(), any(), any(), any(), any()) } returns ArchidektSearchResultDto(
            count = 1,
            next = null,
            results = listOf(noColorSummary),
        )

        // Act
        val result = repository.searchDecks("Test", null, null, 1, 20) as DataResult.Success

        // Assert
        assertTrue(result.data.decks.first().colorIdentity.isEmpty())
    }
}
