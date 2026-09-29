package com.mmg.manahub.core.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CloudflareContentClientTest {

    @Test
    fun `guide and tier requests attach the exact published content version`() = runTest {
        val requests = mutableListOf<Pair<String, String?>>()
        val engine = MockEngine { request ->
            requests += request.url.encodedPath to request.url.parameters["v"]
            respond(
                content = "{}",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = CloudflareContentClient(
            httpClient = HttpClient(engine),
            baseUrl = "https://content.example/",
        )

        client.getSetGuide("tdm", "2026-09-23-v2")
        client.getSetTierList("tdm", "2026-09-23-v2")

        assertEquals(
            listOf<Pair<String, String?>>(
                "/draft/tdm/guide.json" to "2026-09-23-v2",
                "/draft/tdm/tier-list.json" to "2026-09-23-v2",
            ),
            requests,
        )
    }

    @Test
    fun `guide request omits version parameter when manifest has no set entry`() = runTest {
        var versionParameter: String? = "unexpected"
        val engine = MockEngine { request ->
            versionParameter = request.url.parameters["v"]
            respond("{}", HttpStatusCode.OK)
        }
        val client = CloudflareContentClient(
            httpClient = HttpClient(engine),
            baseUrl = "https://content.example/",
        )

        client.getSetGuide("tdm", contentVersion = null)

        assertNull(versionParameter)
    }

    @Test
    fun `reserved characters in content version are decoded as one exact query value`() = runTest {
        val expectedVersion = "release/2026?phase=2&label=A B+#"
        var decodedVersion: String? = null
        val engine = MockEngine { request ->
            decodedVersion = request.url.parameters["v"]
            respond("{}", HttpStatusCode.OK)
        }
        val client = CloudflareContentClient(
            httpClient = HttpClient(engine),
            baseUrl = "https://content.example/",
        )

        client.getSetTierList("tdm", expectedVersion)

        assertEquals(expectedVersion, decodedVersion)
    }
}
