package com.mmg.manahub.core.data.remote.edhrec

import com.mmg.manahub.feature.decks.domain.engine.ThemeId
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
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Coverage for [EdhrecCardTagEnrichmentSource] (Deck Engine Unification plan §8a addendum) — the
 * on-device, single-card, shortlist-bounded EDHREC theme-page verification. Every fetch must be
 * fallible (a failed/404/undecodable page for one candidate must never abort the whole shortlist),
 * and repeated candidates sharing the same mapped slug must hit the network only once per session.
 */
class EdhrecCardTagEnrichmentSourceTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val aristocratsPageJson = """
        {"header":"Aristocrats","container":{"json_dict":{"cardlists":[
          {"tag":"topcards","header":"Top Cards","cardviews":[
            {"id":"a1","name":"Blood Artist","synergy":0.05,"num_decks":10000,"potential_decks":73512}
          ]}
        ]}}}
    """.trimIndent()

    private fun clientWith(engine: MockEngine): HttpClient =
        HttpClient(engine) {
            expectSuccess = true
            install(ContentNegotiation) { json(json) }
        }

    @Test
    fun `given a matching candidate then confirmThemes returns its EDHREC weight`() = runTest {
        val engine = MockEngine { _ ->
            respond(aristocratsPageJson, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val source = EdhrecCardTagEnrichmentSource(clientWith(engine))

        val result = source.confirmThemes("Blood Artist", listOf(ThemeId.ARISTOCRATS))

        assertEquals(mapOf(ThemeId.ARISTOCRATS to 0.05f), result)
    }

    @Test
    fun `given a non-matching card name then confirmThemes returns empty`() = runTest {
        val engine = MockEngine { _ ->
            respond(aristocratsPageJson, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val source = EdhrecCardTagEnrichmentSource(clientWith(engine))

        val result = source.confirmThemes("Some Other Card", listOf(ThemeId.ARISTOCRATS))

        assertTrue(result.isEmpty())
    }

    @Test
    fun `given the page fetch fails (network error) then confirmThemes degrades to empty, never throws`() = runTest {
        val engine = MockEngine { _ -> respondError(HttpStatusCode.InternalServerError) }
        val source = EdhrecCardTagEnrichmentSource(clientWith(engine))

        val result = source.confirmThemes("Blood Artist", listOf(ThemeId.ARISTOCRATS))

        assertTrue(result.isEmpty())
    }

    @Test
    fun `given a 404 for one candidate then the rest of the shortlist is still evaluated`() = runTest {
        val engine = MockEngine { request ->
            if (request.url.encodedPath.contains("aristocrats")) {
                respondError(HttpStatusCode.NotFound)
            } else {
                respond(aristocratsPageJson, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }
        val source = EdhrecCardTagEnrichmentSource(clientWith(engine))

        // TOKENS maps to slug "tokens" (not "aristocrats") — its page is the fallback 200 response
        // above, which lists "Blood Artist" — this is purely to prove ARISTOCRATS's 404 doesn't
        // poison the rest of the shortlist, not a claim about real per-theme data.
        val result = source.confirmThemes("Blood Artist", listOf(ThemeId.ARISTOCRATS, ThemeId.TOKENS))

        assertTrue(ThemeId.ARISTOCRATS !in result)
        assertEquals(0.05f, result[ThemeId.TOKENS])
    }

    @Test
    fun `given a candidate with no mapped EDHREC slug then it is skipped without a network call`() = runTest {
        var requestCount = 0
        val engine = MockEngine { _ ->
            requestCount++
            respond(aristocratsPageJson, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val source = EdhrecCardTagEnrichmentSource(clientWith(engine))

        // TRIBAL has no EDHREC slug mapping (deliberately unmapped — see EdhrecSlugMapping.kt).
        val result = source.confirmThemes("Blood Artist", listOf(ThemeId.TRIBAL))

        assertTrue(result.isEmpty())
        assertEquals(0, requestCount)
    }

    @Test
    fun `given more candidates than MAX_CANDIDATES then only the cap is fetched`() = runTest {
        var requestCount = 0
        val engine = MockEngine { _ ->
            requestCount++
            respond(aristocratsPageJson, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val source = EdhrecCardTagEnrichmentSource(clientWith(engine))
        val manyCandidates = listOf(
            ThemeId.ARISTOCRATS, ThemeId.TOKENS, ThemeId.STAX, ThemeId.MILL, ThemeId.BLINK,
        )

        source.confirmThemes("Blood Artist", manyCandidates)

        assertEquals(EdhrecCardTagEnrichmentSource.MAX_CANDIDATES, requestCount)
    }

    @Test
    fun `given two candidates mapping to the same slug then the page is fetched only once`() = runTest {
        var requestCount = 0
        val engine = MockEngine { _ ->
            requestCount++
            respond(aristocratsPageJson, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val source = EdhrecCardTagEnrichmentSource(clientWith(engine))

        // A duplicate ThemeId in the candidate list (defensive — real callers already `.distinct()`
        // upstream, but this class must not double-fetch even if a caller doesn't).
        source.confirmThemes("Blood Artist", listOf(ThemeId.ARISTOCRATS, ThemeId.ARISTOCRATS))

        assertEquals(1, requestCount)
    }

    @Test
    fun `given a blank card name then confirmThemes returns empty without any network call`() = runTest {
        var requestCount = 0
        val engine = MockEngine { _ ->
            requestCount++
            respond(aristocratsPageJson, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val source = EdhrecCardTagEnrichmentSource(clientWith(engine))

        val result = source.confirmThemes("", listOf(ThemeId.ARISTOCRATS))

        assertTrue(result.isEmpty())
        assertEquals(0, requestCount)
    }
}
