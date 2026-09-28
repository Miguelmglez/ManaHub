package com.mmg.manahub.core.data.remote

import com.mmg.manahub.core.model.FriendRequestException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Bounded friendship reads (F-22) and typed friend-request failures (F-07). */
class FriendRemoteDataSourceReadTest {

    private val requests = mutableListOf<HttpRequestData>()

    private fun dataSource(handler: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): FriendRemoteDataSource {
        val engine = MockEngine { request ->
            requests += request
            handler(request)
        }
        val client = HttpClient(engine) {
            expectSuccess = true
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        return FriendRemoteDataSource(FriendshipClient(client, "https://example.test/rest/v1/"))
    }

    private fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

    private fun friendship(id: String, other: String) =
        """{"id":"$id","user_id_1":"me","user_id_2":"$other","status":"ACCEPTED","created_at":"2026-09-24T10:00:00.123456+00:00"}"""

    private fun pagedId(index: Int) = "fs-" + index.toString().padStart(4, '0')

    @Test
    fun friendsAreKeysetPagedUntilAShortPage() = runTest {
        val size = FriendRemoteDataSource.FRIENDSHIP_PAGE_SIZE
        val firstPage = (0 until size).joinToString(",", "[", "]") { friendship(pagedId(it), "u$it") }
        val source = dataSource { request ->
            when {
                request.url.encodedPath.endsWith("user_profiles") -> json("[]")
                request.url.parameters["id"] == null -> json(firstPage)
                else -> json("[${friendship("fs-9999", "last")}]")
            }
        }

        val friends = source.getFriends("me").getOrThrow()

        assertEquals(size + 1, friends.size)
        val pages = requests.filter { it.url.encodedPath.endsWith("friendships") }
        assertEquals(2, pages.size)
        assertEquals("id.asc", pages[0].url.parameters["order"])
        assertEquals("$size", pages[0].url.parameters["limit"])
        assertEquals("gt.${pagedId(size - 1)}", pages[1].url.parameters["id"])
        // One bounded in.() lookup per chunk of profile ids.
        val profileLookups = requests.count { it.url.encodedPath.endsWith("user_profiles") }
        assertEquals((size + 1 + FriendRemoteDataSource.PROFILE_LOOKUP_CHUNK - 1) / FriendRemoteDataSource.PROFILE_LOOKUP_CHUNK, profileLookups)
    }

    @Test
    fun requestCreatedAtIsParsedFromTheServerTimestamp() = runTest {
        val source = dataSource { request ->
            if (request.url.encodedPath.endsWith("user_profiles")) json("[]")
            else json("""[{"id":"fs-1","user_id_1":"other","user_id_2":"me","status":"PENDING","created_at":"2026-09-24T10:00:00Z"}]""")
        }

        val request = source.getPendingRequests("me").getOrThrow().single()

        assertEquals(1_790_244_000_000L, request.createdAt)
    }

    @Test
    fun aDuplicatePairConflictIsAlreadyLinked() = runTest {
        val source = dataSource {
            json(
                """{"code":"23505","message":"duplicate key value violates unique constraint \"friendships_canonical_pair_uidx\""}""",
                HttpStatusCode.Conflict,
            )
        }

        val result = source.sendFriendRequest("me", "other")

        assertTrue(result.exceptionOrNull() is FriendRequestException.AlreadyLinked)
    }

    @Test
    fun aSelfRequestCheckViolationIsSelfRequest() = runTest {
        val source = dataSource {
            json(
                """{"code":"23514","message":"new row for relation \"friendships\" violates check constraint \"friendships_check\""}""",
                HttpStatusCode.BadRequest,
            )
        }

        val result = source.sendFriendRequest("me", "me")

        assertTrue(result.exceptionOrNull() is FriendRequestException.SelfRequest)
    }

    @Test
    fun anRlsRefusalIsNotPermitted() = runTest {
        val source = dataSource {
            json("""{"code":"42501","message":"new row violates row-level security policy"}""", HttpStatusCode.Forbidden)
        }

        val result = source.sendFriendRequest("me", "other")

        assertTrue(result.exceptionOrNull() is FriendRequestException.NotPermitted)
    }

    @Test
    fun anUnknownServerErrorKeepsItsOriginalException() = runTest {
        val source = dataSource { json("""{"message":"boom"}""", HttpStatusCode.InternalServerError) }

        val result = source.sendFriendRequest("me", "other")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() !is FriendRequestException)
    }
}
