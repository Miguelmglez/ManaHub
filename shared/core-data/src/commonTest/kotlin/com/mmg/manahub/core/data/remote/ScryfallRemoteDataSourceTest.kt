package com.mmg.manahub.core.data.remote

import com.mmg.manahub.core.common.DispatcherProvider
import com.mmg.manahub.core.data.network.ScryfallCache
import com.mmg.manahub.core.data.network.ScryfallRequestQueue
import com.mmg.manahub.core.data.remote.dto.CardDto
import com.mmg.manahub.core.data.remote.dto.LegalitiesDto
import com.mmg.manahub.core.data.remote.dto.PricesDto
import com.mmg.manahub.core.data.remote.dto.SearchResultDto
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for [ScryfallRemoteDataSource.searchCardsPaginated]'s caching behaviour (Backend &
 * Performance Optimization plan, WS4a finding 1, 2026-07-28) -- sole owning suite for this cache
 * addition. Previously this method bypassed [ScryfallCache] entirely on every non-`bypassCache`
 * call (feeding every debounced Add Card keystroke and [com.mmg.manahub.core.data.usecase.card
 * .GetSpotlightFeedUseCase]'s page-walk over an entire Scryfall set), now it routes through the
 * new [ScryfallCache.paginatedSearches].
 *
 * Uses a Ktor `MockEngine` (never MockWebServer, which is JVM/OkHttp-only) so this runs on every
 * KMP target, matching [com.mmg.manahub.core.data.usecase.collection.RefreshCollectionPricesUseCaseTest]'s
 * established pattern.
 */
class ScryfallRemoteDataSourceTest {

    private val dtoJson = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    private fun cardDto(id: String) = CardDto(
        id = id, name = "Card $id", lang = "en", colorIdentity = emptyList(), keywords = emptyList(),
        setCode = "tst", setName = "Test Set", collectorNumber = "1", rarity = "common",
        releasedAt = "2020-01-01",
        prices = PricesDto(usd = "1.00", usdFoil = null, eur = null, eurFoil = null),
        legalities = LegalitiesDto(
            standard = "legal", pioneer = "legal", modern = "legal", legacy = "legal",
            vintage = "legal", commander = "legal", pauper = "legal",
        ),
        scryfallUri = "https://scryfall.com/card/$id",
    )

    /** A [ScryfallRemoteDataSource] whose engine returns [responses] IN ORDER, counting calls. */
    private fun dataSource(
        responses: List<SearchResultDto>,
        requestCounter: IntArray = IntArray(1),
        cache: ScryfallCache = ScryfallCache(),
    ): ScryfallRemoteDataSource {
        var callIndex = 0
        val engine = MockEngine { _ ->
            requestCounter[0]++
            val response = responses[callIndex]
            callIndex++
            respond(
                content = dtoJson.encodeToString(SearchResultDto.serializer(), response),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val httpClient = HttpClient(engine) { install(ContentNegotiation) { json(dtoJson) } }
        val client = ScryfallClient(httpClient, baseUrl = "https://api.scryfall.com/")
        return ScryfallRemoteDataSource(
            api = client,
            requestQueue = ScryfallRequestQueue(),
            cache = cache,
            dispatcherProvider = DispatcherProvider(),
        )
    }

    @Test
    fun `given the same query and page when searchCardsPaginated is called twice within the TTL then the second call is served from cache with zero network calls`() =
        runTest {
            val requestCounter = IntArray(1)
            val response = SearchResultDto(totalCards = 1, hasMore = true, data = listOf(cardDto("id-1")))
            val ds = dataSource(responses = listOf(response), requestCounter = requestCounter)

            val first = ds.searchCardsPaginated("lightning bolt", page = 1)
            val second = ds.searchCardsPaginated("lightning bolt", page = 1)

            assertEquals(1, requestCounter[0], "the 2nd identical call must be served from cache, not the network")
            assertTrue(first.isSuccess)
            assertTrue(second.isSuccess)
            assertEquals(first.getOrNull(), second.getOrNull())
            assertTrue(second.getOrNull()!!.hasMore, "the cached hasMore flag must round-trip correctly")
        }

    @Test
    fun `given bypassCache is true when searchCardsPaginated is called twice then both calls hit the network`() = runTest {
        val requestCounter = IntArray(1)
        val response1 = SearchResultDto(totalCards = 1, hasMore = false, data = listOf(cardDto("id-1")))
        val response2 = SearchResultDto(totalCards = 1, hasMore = false, data = listOf(cardDto("id-2")))
        val ds = dataSource(responses = listOf(response1, response2), requestCounter = requestCounter)

        ds.searchCardsPaginated("random query", page = 1, bypassCache = true)
        ds.searchCardsPaginated("random query", page = 1, bypassCache = true)

        assertEquals(2, requestCounter[0], "bypassCache must always re-fetch, never serve a cached page")
    }

    @Test
    fun `given a successful searchCardsPaginated call when it completes then individual cards still populate the cards cache`() =
        runTest {
            val cache = ScryfallCache()
            val response = SearchResultDto(totalCards = 1, hasMore = false, data = listOf(cardDto("id-1")))
            val ds = dataSource(responses = listOf(response), cache = cache)

            ds.searchCardsPaginated("lightning bolt", page = 1)

            // Both the new paginatedSearches cache AND the pre-existing per-card cards cache must
            // be populated -- individual card lookups elsewhere should benefit too.
            assertTrue(cache.cards.get("id-1") != null, "individual result cards must still populate cache.cards")
        }

    // ── W2.8 (scanner-reliability-plan.md, 2026-08-24): searchCardPrintedName / include_multilingual ──

    /**
     * A [ScryfallRemoteDataSource] whose engine returns [responses] IN ORDER and records every
     * outgoing [HttpRequestData] into [captured] -- used to assert on the actual query parameters
     * sent (`include_multilingual`, `q`), not just the response shape.
     */
    private fun dataSourceCapturingRequests(
        responses: List<SearchResultDto>,
        captured: MutableList<HttpRequestData>,
        cache: ScryfallCache = ScryfallCache(),
    ): ScryfallRemoteDataSource {
        var callIndex = 0
        val engine = MockEngine { request ->
            captured.add(request)
            val response = responses[callIndex]
            callIndex++
            respond(
                content = dtoJson.encodeToString(SearchResultDto.serializer(), response),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val httpClient = HttpClient(engine) { install(ContentNegotiation) { json(dtoJson) } }
        val client = ScryfallClient(httpClient, baseUrl = "https://api.scryfall.com/")
        return ScryfallRemoteDataSource(
            api = client,
            requestQueue = ScryfallRequestQueue(),
            cache = cache,
            dispatcherProvider = DispatcherProvider(),
        )
    }

    @Test
    fun `given a printed-name lookup when searchCardPrintedName is called then the request carries include_multilingual=true and the localized name+lang query`() =
        runTest {
            val captured = mutableListOf<HttpRequestData>()
            val response = SearchResultDto(totalCards = 1, hasMore = false, data = listOf(cardDto("id-es-1")))
            val ds = dataSourceCapturingRequests(responses = listOf(response), captured = captured)

            val result = ds.searchCardPrintedName("Rayo", "es")

            assertTrue(result.isSuccess)
            assertEquals(1, captured.size)
            val params = captured.single().url.parameters
            assertEquals("true", params["include_multilingual"])
            assertEquals("prints", params["unique"])
            assertTrue(params["q"]!!.contains("lang:es"), "query must scope the search to the selected language")
            assertTrue(params["q"]!!.contains("Rayo"), "query must embed the OCR'd printed name")
        }

    @Test
    fun `given an existing searchCards caller when it runs then include_multilingual is NEVER sent`() = runTest {
        val captured = mutableListOf<HttpRequestData>()
        val response = SearchResultDto(totalCards = 1, hasMore = false, data = listOf(cardDto("id-1")))
        val ds = dataSourceCapturingRequests(responses = listOf(response), captured = captured)

        ds.searchCards("lightning bolt", page = 1)

        assertEquals(1, captured.size)
        assertNull(
            captured.single().url.parameters["include_multilingual"],
            "pre-existing callers must stay byte-identical -- include_multilingual defaults to false/absent",
        )
    }

    @Test
    fun `given the same raw text when searched via searchCards and searchCardPrintedName then they issue SEPARATE network calls and never share a cache entry`() =
        runTest {
            val cache = ScryfallCache()
            val captured = mutableListOf<HttpRequestData>()
            val englishOnly = SearchResultDto(totalCards = 1, hasMore = false, data = listOf(cardDto("id-en")))
            val localized = SearchResultDto(totalCards = 1, hasMore = false, data = listOf(cardDto("id-es")))
            val ds = dataSourceCapturingRequests(
                responses = listOf(englishOnly, localized),
                captured = captured,
                cache = cache,
            )

            val plain = ds.searchCards("Rayo")
            val printed = ds.searchCardPrintedName("Rayo", "es")

            // Two DISTINCT network calls -- the multilingual lookup never reuses (nor pollutes) the
            // plain search's cache entry, and vice versa (highest-risk item in the plan).
            assertEquals(2, captured.size)
            assertTrue(plain.isSuccess)
            assertTrue(printed.isSuccess)
            assertEquals("id-en", plain.getOrNull()!!.first().scryfallId)
            assertEquals("id-es", printed.getOrNull()!!.scryfallId)
        }

    @Test
    fun `given the same printed-name lookup when called twice within the TTL then the second call is served from cache with zero network calls`() =
        runTest {
            val captured = mutableListOf<HttpRequestData>()
            val response = SearchResultDto(totalCards = 1, hasMore = false, data = listOf(cardDto("id-es-1")))
            val ds = dataSourceCapturingRequests(responses = listOf(response), captured = captured)

            ds.searchCardPrintedName("Rayo", "es")
            ds.searchCardPrintedName("Rayo", "es")

            assertEquals(1, captured.size, "the 2nd identical printed-name lookup must be cached")
        }

    @Test
    fun `given no printing exists in the requested language when searchCardPrintedName is called then it fails without throwing`() =
        runTest {
            val captured = mutableListOf<HttpRequestData>()
            val empty = SearchResultDto(totalCards = 0, hasMore = false, data = emptyList())
            val ds = dataSourceCapturingRequests(responses = listOf(empty), captured = captured)

            val result = ds.searchCardPrintedName("Nonexistent Card", "de")

            assertTrue(result.isFailure)
            assertFalse(result.exceptionOrNull() is NullPointerException)
        }

    @Test
    fun `given the same query but different pages when searchCardsPaginated is called then each page issues its own network call`() =
        runTest {
            val requestCounter = IntArray(1)
            val page1 = SearchResultDto(totalCards = 2, hasMore = true, data = listOf(cardDto("id-1")))
            val page2 = SearchResultDto(totalCards = 2, hasMore = false, data = listOf(cardDto("id-2")))
            val ds = dataSource(responses = listOf(page1, page2), requestCounter = requestCounter)

            ds.searchCardsPaginated("lightning bolt", page = 1)
            ds.searchCardsPaginated("lightning bolt", page = 2)

            assertEquals(2, requestCounter[0], "distinct pages must not share a cache entry")
        }
}
