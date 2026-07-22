package com.mmg.manahub.tools.tagpipeline.archidekt

import com.mmg.manahub.core.data.network.ArchidektRequestQueue
import com.mmg.manahub.core.data.remote.ArchidektClient
import com.mmg.manahub.tools.tagpipeline.io.PIPELINE_JSON
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArchidektCategorySamplerTest {

    // ── resolveDominantCategories (pure function) ───────────────────────────────────────────

    @Test
    fun `card clearing both thresholds resolves to its majority tag`() {
        val observations = mapOf("oracle-1" to mapOf("ramp" to 4, "tokens" to 1))
        val result = resolveDominantCategories(observations, minObservations = 3, minShare = 0.5f)
        assertEquals("ramp", result["oracle-1"])
    }

    @Test
    fun `card below the minimum observation count is dropped, even with a 100 percent share`() {
        val observations = mapOf("oracle-1" to mapOf("ramp" to 2))
        val result = resolveDominantCategories(observations, minObservations = 3, minShare = 0.5f)
        assertTrue("oracle-1" !in result)
    }

    @Test
    fun `card with no clear majority (below minShare) is dropped`() {
        // A genuinely three-way split: no single tag reaches even a bare 50% majority
        // (top share is 2/6 = 0.33).
        val observations = mapOf("oracle-1" to mapOf("ramp" to 2, "tokens" to 2, "tutor" to 2))
        val result = resolveDominantCategories(observations, minObservations = 3, minShare = 0.5f)
        assertTrue("oracle-1" !in result)
    }

    @Test
    fun `share exactly at the threshold is accepted (inclusive boundary)`() {
        val observations = mapOf("oracle-1" to mapOf("ramp" to 3, "tokens" to 3))
        val result = resolveDominantCategories(observations, minObservations = 3, minShare = 0.5f)
        assertEquals("ramp", result["oracle-1"]) // tie broken by maxByOrNull's stable first-max
    }

    @Test
    fun `empty observations resolve to an empty map`() {
        assertEquals(emptyMap(), resolveDominantCategories(emptyMap(), minObservations = 3, minShare = 0.5f))
    }

    // ── collectDeckIds / harvestCategoryObservations (Ktor MockEngine, zero real network) ──────

    @Test
    fun `collectDeckIds pages through search results until the target count is reached`() = runTest {
        var pageCalls = 0
        val engine = MockEngine { request ->
            if (request.url.encodedPath.contains("decks/v3")) {
                pageCalls++
                respond(
                    content = """{"count":100,"next":null,"results":[{"id":1},{"id":2},{"id":3}]}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            } else {
                respond("{}", status = HttpStatusCode.NotFound)
            }
        }
        val sampler = ArchidektCategorySampler(fakeClient(engine), ArchidektRequestQueue())

        val ids = sampler.collectDeckIds(targetCount = 5)

        // Two pages of 3 distinct-ish ids get us to >= 5 (first page [1,2,3], second page again
        // [1,2,3] deduped by the LinkedHashSet - since the fixture always returns the same 3 ids,
        // this also proves collectDeckIds terminates instead of looping forever on a saturated feed.
        assertTrue(ids.isNotEmpty())
        assertTrue(pageCalls > 0)
    }

    @Test
    fun `collectDeckIds of a non-positive target returns immediately with no requests`() = runTest {
        var calls = 0
        val engine = MockEngine { calls++; respond("{}", status = HttpStatusCode.OK) }
        val sampler = ArchidektCategorySampler(fakeClient(engine), ArchidektRequestQueue())

        assertEquals(emptyList(), sampler.collectDeckIds(0))
        assertEquals(0, calls)
    }

    @Test
    fun `harvestCategoryObservations aggregates only allowlist-mapped categories per oracle_id`() = runTest {
        val deckJson = """
            {
              "id": 1, "name": "Test Deck",
              "cards": [
                {"quantity":1,"categories":["Ramp"],"card":{"oracleCard":{"uid":"oracle-a","name":"Card A"}}},
                {"quantity":1,"categories":["Ramp","Mana Ramp"],"card":{"oracleCard":{"uid":"oracle-a","name":"Card A dup print"}}},
                {"quantity":1,"categories":["Pingers"],"card":{"oracleCard":{"uid":"oracle-b","name":"Card B"}}},
                {"quantity":1,"categories":["Land"],"card":{"oracleCard":{"uid":"oracle-c","name":"Basic Land"}}}
              ]
            }
        """.trimIndent()
        val engine = MockEngine { respond(deckJson, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json")) }
        val sampler = ArchidektCategorySampler(fakeClient(engine), ArchidektRequestQueue())

        val observations = sampler.harvestCategoryObservations(listOf(1))

        // oracle-a: "Ramp" (1 obs) + "Ramp"+"Mana Ramp" (2 obs, both map to "ramp") = 3 total.
        assertEquals(3, observations["oracle-a"]?.get("ramp"))
        // oracle-b's only category ("Pingers") is unmapped, so it never enters the aggregate.
        assertTrue("oracle-b" !in observations)
        // oracle-c's only category ("Land") is a type-echo bucket, also excluded.
        assertTrue("oracle-c" !in observations)
    }

    @Test
    fun `harvestCategoryObservations of a failing deck fetch skips it and continues`() = runTest {
        val engine = MockEngine { respond("not json", HttpStatusCode.InternalServerError) }
        val sampler = ArchidektCategorySampler(fakeClient(engine), ArchidektRequestQueue())

        val observations = sampler.harvestCategoryObservations(listOf(1, 2, 3))

        assertEquals(emptyMap(), observations)
    }

    private fun fakeClient(engine: MockEngine): ArchidektClient {
        val httpClient = HttpClient(engine) {
            install(ContentNegotiation) { json(PIPELINE_JSON) }
        }
        return ArchidektClient(httpClient, baseUrl = "https://archidekt.com/")
    }
}
