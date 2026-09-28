package com.mmg.manahub.tools.tagpipeline.catalog

import com.mmg.manahub.core.data.remote.dto.CardDto
import com.mmg.manahub.core.data.remote.dto.LegalitiesDto
import com.mmg.manahub.core.data.remote.dto.PricesDto
import java.nio.file.Files
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MechanicCatalogPublisherTest {
    private fun response(status: Int, body: String = "{}", retryAfter: String? = null): HttpResponse<String> =
        object : HttpResponse<String> {
            override fun statusCode() = status
            override fun request(): HttpRequest = throw UnsupportedOperationException()
            override fun previousResponse() = java.util.Optional.empty<HttpResponse<String>>()
            override fun headers(): java.net.http.HttpHeaders = java.net.http.HttpHeaders.of(
                retryAfter?.let { mapOf("Retry-After" to listOf(it)) } ?: emptyMap(),
            ) { _, _ -> true }
            override fun body() = body
            override fun sslSession() = java.util.Optional.empty<javax.net.ssl.SSLSession>()
            override fun uri() = java.net.URI.create("https://api.scryfall.com")
            override fun version() = java.net.http.HttpClient.Version.HTTP_1_1
        }

    @Test
    fun `Scryfall validation honors Retry-After then accepts exact keyword`() {
        var now = 1_000L
        val requestTimes = mutableListOf<Long>()
        val publisher = MechanicCatalogPublisher(
            "https://example.test", "key",
            send = {
                requestTimes += now
                if (requestTimes.size == 1) response(429, retryAfter = "2")
                else response(200, """{"data":[{"keywords":["New Mechanic"]}]}""")
            },
            nowMillis = { now },
            sleepMillis = { now += it },
        )

        assertEquals("keyword:\"New Mechanic\"", publisher.verifyQuery("New Mechanic"))
        assertEquals(listOf(1_000L, 3_000L), requestTimes)
    }

    @Test
    fun `Scryfall validation exhausts repeated 503 without accepting a query`() {
        var now = 1_000L
        var calls = 0
        val publisher = MechanicCatalogPublisher(
            "https://example.test", "key",
            send = { calls++; response(503) },
            nowMillis = { now },
            sleepMillis = { now += it },
        )

        val error = assertFailsWith<IllegalStateException> { publisher.verifyQuery("New Mechanic") }
        assertTrue(error.message.orEmpty().contains("exhausted"))
        assertEquals(5, calls)
    }

    @Test
    fun `Scryfall validation spaces fallback queries after a no-match response`() {
        var now = 1_000L
        val requestTimes = mutableListOf<Long>()
        val publisher = MechanicCatalogPublisher(
            "https://example.test", "key",
            send = {
                requestTimes += now
                if (requestTimes.size == 1) response(404)
                else response(200, """{"data":[{"keywords":["New Mechanic"]}]}""")
            },
            nowMillis = { now },
            sleepMillis = { now += it },
        )

        assertEquals("o:\"New Mechanic\"", publisher.verifyQuery("New Mechanic"))
        assertEquals(listOf(1_000L, 1_250L), requestTimes)
    }

    @Test
    fun `keyword keys preserve multiword ability names`() {
        assertEquals("empower_jace", keywordKey("Empower Jace"))
        assertEquals("double_strike", keywordKey("Double strike"))
    }

    @Test
    fun `oracle only Empower Jace appears in review expressions`() {
        val oracle = "Empower Jace 6. Then surveil 2."

        assertEquals(setOf("Empower Jace"), mechanicLeadIns(oracle))
        assertFalse("Empower Jace" in listOf("Surveil"))
        assertTrue(mechanicLeadIns("Put a +1/+1 counter on target creature.").isEmpty())
    }

    @Test
    fun `oracle only Empower Jace is reported without publishing a keyword`() {
        val card = CardDto(
            id = "proteges-awakening", oracleId = "oracle-proteges-awakening", name = "Protege's Awakening",
            lang = "en", colorIdentity = listOf("U"), oracleText = "Empower Jace 6. Surveil 2.",
            keywords = listOf("Surveil"), setCode = "rft", setName = "Reality Fracture",
            collectorNumber = "1", rarity = "rare", releasedAt = "2026-09-01",
            prices = PricesDto(), legalities = LegalitiesDto(
                standard = "legal", pioneer = "legal", modern = "legal", legacy = "legal",
                vintage = "legal", commander = "legal", pauper = "legal",
            ), scryfallUri = "https://scryfall.com/card/rft/1",
        )
        val reportPath = Files.createTempFile("mechanic-catalog-", ".json")
        try {
            val publisher = MechanicCatalogPublisher("https://example.test", "dry-run") {
                error("No network request expected for known Surveil keyword")
            }

            val report = publisher.publish(sequenceOf(card), reportPath, dryRun = true)

            assertTrue(report.published.isEmpty())
            assertTrue(report.unknownExpressions.any { it.expression == "Empower Jace" })
            assertTrue(Files.readString(reportPath).contains("Empower Jace"))
        } finally {
            Files.deleteIfExists(reportPath)
        }
    }
}
