package com.mmg.manahub.core.data.remote

import com.mmg.manahub.core.model.FriendshipGoneException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Friendship mutations ask for the affected rows and treat "no row" as a typed failure (F-08). */
class FriendRemoteDataSourceMutationTest {

    private val requests = mutableListOf<HttpRequestData>()

    private fun dataSource(body: String): FriendRemoteDataSource {
        val engine = MockEngine { request ->
            requests += request
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val client = HttpClient(engine) {
            expectSuccess = true
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        return FriendRemoteDataSource(FriendshipClient(client, "https://example.test/rest/v1/"))
    }

    @Test
    fun acceptWithNoRowChanged_failsAsGone() = runTest {
        val result = dataSource("[]").acceptRequestReturning("fs-1")

        assertTrue(result.exceptionOrNull() is FriendshipGoneException)
        val patch = requests.single()
        assertEquals(HttpMethod.Patch, patch.method)
        assertEquals("return=representation", patch.headers["Prefer"])
    }

    @Test
    fun acceptWithRow_returnsTheAcceptedFriendship() = runTest {
        val body = """[{"id":"fs-1","user_id_1":"a","user_id_2":"b","status":"ACCEPTED","created_at":"2026-09-24T00:00:00Z"}]"""

        val row = dataSource(body).acceptRequestReturning("fs-1").getOrThrow()

        assertEquals("a", row.userId1)
        assertEquals("ACCEPTED", row.status)
    }

    @Test
    fun deleteWithNoRowDeleted_failsAsGone() = runTest {
        val result = dataSource("[]").removeFriend("fs-1")

        assertTrue(result.exceptionOrNull() is FriendshipGoneException)
        assertEquals("return=representation", requests.single().headers["Prefer"])
    }

    @Test
    fun deleteWithRow_succeeds() = runTest {
        val body = """[{"id":"fs-1","user_id_1":"a","user_id_2":"b","status":"PENDING","created_at":"2026-09-24T00:00:00Z"}]"""

        assertTrue(dataSource(body).rejectRequest("fs-1").isSuccess)
    }
}
