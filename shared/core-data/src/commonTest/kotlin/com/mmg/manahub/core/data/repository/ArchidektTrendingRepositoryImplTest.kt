package com.mmg.manahub.core.data.repository

import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.common.DispatcherProvider
import com.mmg.manahub.core.data.cache.CachedAggregateEntry
import com.mmg.manahub.core.data.cache.CommunityAggregateCache
import com.mmg.manahub.core.data.remote.ArchidektClient
import com.mmg.manahub.core.data.remote.dto.ArchidektDeckSummaryDto
import com.mmg.manahub.core.data.remote.dto.ArchidektSearchResultDto
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * Unit tests for [ArchidektTrendingRepositoryImpl] (Home feature overhaul Phase 1.2.c).
 *
 * KMP-first convention: [ArchidektClient] wraps a Ktor [HttpClient], so it is exercised here with
 * a Ktor `MockEngine` (never MockWebServer, which is JVM/OkHttp-only and would not compile for
 * wasmJs) against canned JSON responses — no real network call. [CommunityAggregateCache] and
 * [CrashReporter] are simple hand-written fakes (interfaces; no MockK in `commonTest`, which is
 * JVM-only and unavailable on the wasmJs target).
 */
class ArchidektTrendingRepositoryImplTest {

    private val dtoJson = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    private class FakeAggregateCache(initial: CachedAggregateEntry? = null) : CommunityAggregateCache {
        var stored: CachedAggregateEntry? = initial
        var insertCount = 0
        var lastInsertedKey: String? = null
        override suspend fun get(key: String): CachedAggregateEntry? = stored
        override suspend fun insert(key: String, json: String, cachedAt: Long) {
            insertCount++
            lastInsertedKey = key
            stored = CachedAggregateEntry(key, json, cachedAt)
        }
    }

    private class FakeCrashReporter : CrashReporter {
        val logs = mutableListOf<String>()
        override fun recordException(throwable: Throwable) {}
        override fun log(message: String) { logs.add(message) }
        override fun setCustomKey(key: String, value: String) {}
    }

    private fun deckDto(
        id: Int,
        name: String = "Deck $id",
        viewCount: Int = 100,
        private: Boolean = false,
        unlisted: Boolean = false,
        theorycrafted: Boolean = false,
    ) = ArchidektDeckSummaryDto(
        id = id, name = name, size = 100, deckFormat = 3, owner = null, viewCount = viewCount,
        createdAt = "2026-01-01", updatedAt = "2026-01-01", colors = emptyMap(),
        edhBracket = null, private = private, unlisted = unlisted, theorycrafted = theorycrafted,
    )

    /** Builds a client whose engine always returns [dto] as a 200 JSON response, counting requests. */
    private fun successClient(dto: ArchidektSearchResultDto, requestCounter: IntArray = IntArray(1)): ArchidektClient {
        val engine = MockEngine { _ ->
            requestCounter[0]++
            respond(
                content = dtoJson.encodeToString(ArchidektSearchResultDto.serializer(), dto),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val httpClient = HttpClient(engine) { install(ContentNegotiation) { json(dtoJson) } }
        return ArchidektClient(httpClient, baseUrl = "https://archidekt.com/")
    }

    /** Builds a client whose engine always returns a network-ish 500 failure. */
    private fun failingClient(requestCounter: IntArray = IntArray(1)): ArchidektClient {
        val engine = MockEngine { _ ->
            requestCounter[0]++
            respondError(HttpStatusCode.InternalServerError)
        }
        val httpClient = HttpClient(engine) { install(ContentNegotiation) { json(dtoJson) } }
        return ArchidektClient(httpClient, baseUrl = "https://archidekt.com/")
    }

    /** A client that must NEVER be called — fails the test loudly if the engine is invoked. */
    private fun explodingClient(): ArchidektClient {
        val engine = MockEngine { _ -> throw AssertionError("Network should not be called when cache is fresh") }
        val httpClient = HttpClient(engine) { install(ContentNegotiation) { json(dtoJson) } }
        return ArchidektClient(httpClient, baseUrl = "https://archidekt.com/")
    }

    private fun repo(
        client: ArchidektClient,
        cache: CommunityAggregateCache,
        crashReporter: CrashReporter = FakeCrashReporter(),
        now: () -> Long = { 1_000_000L },
    ) = ArchidektTrendingRepositoryImpl(
        client = client, cache = cache, crashReporter = crashReporter,
        dispatcherProvider = DispatcherProvider(), now = now,
    )

    @Test
    fun `cold start with empty cache fetches and emits mapped decks`() = runTest {
        val dto = ArchidektSearchResultDto(count = 1, results = listOf(deckDto(id = 42, name = "Atraxa Superfriends", viewCount = 5000)))
        val cache = FakeAggregateCache()
        val repository = repo(client = successClient(dto), cache = cache)

        val emissions = repository.observeTrendingDecks().toList()

        val decks = emissions.last()
        assertEquals(1, decks.size)
        assertEquals(42, decks.first().id)
        assertEquals("Atraxa Superfriends", decks.first().name)
        assertEquals(5000, decks.first().viewCount)
        assertEquals("https://archidekt.com/decks/42", decks.first().deckUrl)
        assertEquals(1, cache.insertCount)
    }

    @Test
    fun `private unlisted theorycrafted and blank-name decks are filtered out`() = runTest {
        val dto = ArchidektSearchResultDto(
            count = 5,
            results = listOf(
                deckDto(id = 1, name = "Public Deck"),
                deckDto(id = 2, name = "Private Deck", private = true),
                deckDto(id = 3, name = "Unlisted Deck", unlisted = true),
                deckDto(id = 4, name = "Theorycraft Deck", theorycrafted = true),
                deckDto(id = 5, name = ""),
            ),
        )
        val repository = repo(client = successClient(dto), cache = FakeAggregateCache())

        val decks = repository.observeTrendingDecks().toList().last()

        assertEquals(listOf(1), decks.map { it.id })
    }

    @Test
    fun `slides are capped at 3 even when more valid decks are returned`() = runTest {
        val dto = ArchidektSearchResultDto(
            count = 5,
            results = (1..5).map { deckDto(id = it, name = "Deck $it") },
        )
        val repository = repo(client = successClient(dto), cache = FakeAggregateCache())

        val decks = repository.observeTrendingDecks().toList().last()

        assertEquals(3, decks.size)
        assertEquals(listOf(1, 2, 3), decks.map { it.id })
    }

    @Test
    fun `fresh cache emits cached decks without ever calling the network`() = runTest {
        val cachedDto = ArchidektSearchResultDto(count = 1, results = listOf(deckDto(id = 7, name = "Cached Deck")))
        val cachedJson = dtoJson.encodeToString(ArchidektSearchResultDto.serializer(), cachedDto)
        val now = 10_000_000L
        // cachedAt is well within the 18h freshness window.
        val cache = FakeAggregateCache(CachedAggregateEntry("home_archidekt_trending", cachedJson, cachedAt = now - 1_000L))
        val repository = repo(client = explodingClient(), cache = cache, now = { now })

        val decks = repository.observeTrendingDecks().toList().last()

        assertEquals(listOf(7), decks.map { it.id })
        assertEquals(0, cache.insertCount) // never refreshed
    }

    @Test
    fun `stale cache emits cached value first then the fresh value`() = runTest {
        val cachedDto = ArchidektSearchResultDto(count = 1, results = listOf(deckDto(id = 1, name = "Old Deck")))
        val cachedJson = dtoJson.encodeToString(ArchidektSearchResultDto.serializer(), cachedDto)
        val freshDto = ArchidektSearchResultDto(count = 1, results = listOf(deckDto(id = 2, name = "New Deck")))
        val now = 10_000_000L
        val eighteenHoursMs = 18L * 60 * 60 * 1000
        // cachedAt is older than the 18h freshness window -> triggers a refresh.
        val cache = FakeAggregateCache(CachedAggregateEntry("home_archidekt_trending", cachedJson, cachedAt = now - eighteenHoursMs - 1))
        val repository = repo(client = successClient(freshDto), cache = cache, now = { now })

        val emissions = repository.observeTrendingDecks().toList()

        assertEquals(2, emissions.size)
        assertEquals(listOf(1), emissions[0].map { it.id })
        assertEquals(listOf(2), emissions[1].map { it.id })
    }

    @Test
    fun `fetch failure with no cache emits an empty list and logs, never an inline error`() = runTest {
        val requestCounter = IntArray(1)
        val crashReporter = FakeCrashReporter()
        val repository = repo(client = failingClient(requestCounter), cache = FakeAggregateCache(), crashReporter = crashReporter)

        val decks = repository.observeTrendingDecks().toList().last()

        assertTrue(decks.isEmpty())
        assertTrue(requestCounter[0] >= 1)
        assertTrue(crashReporter.logs.any { it.contains("archidekt") })
    }

    @Test
    fun `fetch failure with a cached value falls back to the cached decks`() = runTest {
        val cachedDto = ArchidektSearchResultDto(count = 1, results = listOf(deckDto(id = 9, name = "Fallback Deck")))
        val cachedJson = dtoJson.encodeToString(ArchidektSearchResultDto.serializer(), cachedDto)
        val now = 10_000_000L
        val eighteenHoursMs = 18L * 60 * 60 * 1000
        val cache = FakeAggregateCache(CachedAggregateEntry("home_archidekt_trending", cachedJson, cachedAt = now - eighteenHoursMs - 1))
        val repository = repo(client = failingClient(), cache = cache, now = { now })

        val emissions = repository.observeTrendingDecks().toList()

        // First emission is the (stale-but-present) cached value; the failed refresh never emits
        // a second (empty) value on top of it, per "never an inline error" — cachedDto != null.
        assertEquals(1, emissions.size)
        assertEquals(listOf(9), emissions.first().map { it.id })
    }

    @Test
    fun `corrupted but STALE cached JSON recovers via a fresh fetch`() = runTest {
        val now = 10_000_000L
        val eighteenHoursMs = 18L * 60 * 60 * 1000
        // cachedAt is older than the freshness window, so isStale=true and fetchFresh() runs
        // even though the cached payload itself failed to decode (cachedDto == null).
        val cache = FakeAggregateCache(
            CachedAggregateEntry("home_archidekt_trending", "{ not valid json", cachedAt = now - eighteenHoursMs - 1),
        )
        val freshDto = ArchidektSearchResultDto(count = 1, results = listOf(deckDto(id = 5, name = "Recovered Deck")))
        val repository = repo(client = successClient(freshDto), cache = cache, now = { now })

        val decks = repository.observeTrendingDecks().toList().last()

        assertEquals(listOf(5), decks.map { it.id })
    }

    @Test
    fun `corrupted but FRESH cached JSON recovers via a fresh fetch (regression)`() = runTest {
        // Regression test for a bug found while writing this suite, fixed in
        // ArchidektTrendingRepositoryImpl.observeTrendingDecks(): a present-but-undecodable cache
        // entry (cachedDto == null) is now treated the same as "no cache" for the isStale
        // computation, even when its cachedAt timestamp is still within the 18h TTL window. Before
        // the fix, isStale evaluated to false for this case (an entry exists AND it's not
        // TTL-expired), so `if (!isStale) return@flow` exited the flow having emitted ZERO items —
        // silently stalling any collector with no other fallback for this source (e.g. Home's
        // socialExtrasFlow combine) until the entry naturally expired, up to 18h later.
        val now = 10_000_000L
        val cache = FakeAggregateCache(
            CachedAggregateEntry("home_archidekt_trending", "{ not valid json", cachedAt = now - 1),
        )
        val freshDto = ArchidektSearchResultDto(count = 1, results = listOf(deckDto(id = 5, name = "Recovered Deck")))
        val repository = repo(client = successClient(freshDto), cache = cache, now = { now })

        val decks = repository.observeTrendingDecks().toList().last()

        assertEquals(listOf(5), decks.map { it.id })
    }

    @Test
    fun `corrupted FRESH cache with a failing fetch still emits an empty list, never stalls silently`() = runTest {
        val now = 10_000_000L
        val crashReporter = FakeCrashReporter()
        val cache = FakeAggregateCache(
            CachedAggregateEntry("home_archidekt_trending", "{ not valid json", cachedAt = now - 1),
        )
        val repository = repo(client = failingClient(), cache = cache, crashReporter = crashReporter, now = { now })

        val emissions = repository.observeTrendingDecks().toList()

        assertEquals(1, emissions.size)
        assertTrue(emissions.first().isEmpty())
    }

    @Test
    fun `deckUrl always uses the canonical Archidekt deck-page prefix`() = runTest {
        val dto = ArchidektSearchResultDto(count = 1, results = listOf(deckDto(id = 12345, name = "Prefix Check")))
        val repository = repo(client = successClient(dto), cache = FakeAggregateCache())

        val decks = repository.observeTrendingDecks().toList().last()

        assertTrue(decks.first().deckUrl.startsWith("https://archidekt.com/decks/"))
        assertFalse(decks.first().deckUrl.endsWith("/"))
    }
}
