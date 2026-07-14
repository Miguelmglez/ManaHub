package com.mmg.manahub.feature.news.data.remote

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [NewsFeedService] — News feature improvements Phase 1 (conditional GET support).
 *
 * Uses [MockWebServer] (this class stays OkHttp/androidMain-only per its own KDoc, so
 * `ktor-client-mock` doesn't apply here — MockWebServer is the correct tool per the project's
 * testing conventions).
 *
 * GROUP 1 — 200 success: body + caching headers captured into [FeedFetchResult.Fetched]
 * GROUP 2 — 304 Not Modified: short-circuits to [FeedFetchResult.NotModified], no body read
 * GROUP 3 — conditional-GET request headers: sent only when etag/lastModified are non-blank
 * GROUP 4 — failure paths: non-2xx/non-304 status, and network-level exceptions
 */
class NewsFeedServiceTest {

    private lateinit var server: MockWebServer
    private lateinit var service: NewsFeedService

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        service = NewsFeedService(OkHttpClient())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun url(path: String = "/feed") = server.url(path).toString()

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 1 — 200 success
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given a 200 response then the body and caching headers are captured as Fetched`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("<rss><channel></channel></rss>")
                .addHeader("ETag", "\"abc123\"")
                .addHeader("Last-Modified", "Mon, 01 Jan 2024 00:00:00 GMT"),
        )

        val result = service.fetchFeed(url())

        assertTrue(result.isSuccess)
        val fetched = result.getOrThrow() as FeedFetchResult.Fetched
        assertEquals("<rss><channel></channel></rss>", fetched.body)
        assertEquals("\"abc123\"", fetched.etag)
        assertEquals("Mon, 01 Jan 2024 00:00:00 GMT", fetched.lastModified)
    }

    @Test
    fun `given a 200 response with no caching headers then etag and lastModified are null`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("<rss></rss>"))

        val result = service.fetchFeed(url())

        val fetched = result.getOrThrow() as FeedFetchResult.Fetched
        assertNull(fetched.etag)
        assertNull(fetched.lastModified)
    }

    @Test
    fun `given a 200 response with an empty body then the result is a failure`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(""))

        val result = service.fetchFeed(url())

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("Empty response body"))
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 2 — 304 Not Modified
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given a 304 response then the result is NotModified`() = runTest {
        server.enqueue(MockResponse().setResponseCode(304))

        val result = service.fetchFeed(url(), etag = "\"abc123\"", lastModified = "Mon, 01 Jan 2024 00:00:00 GMT")

        assertTrue(result.isSuccess)
        assertEquals(FeedFetchResult.NotModified, result.getOrThrow())
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 3 — conditional-GET request headers
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given a non-blank etag when fetching then If-None-Match is sent`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("<rss></rss>"))

        service.fetchFeed(url(), etag = "\"abc123\"")

        val request = server.takeRequest()
        assertEquals("\"abc123\"", request.getHeader("If-None-Match"))
    }

    @Test
    fun `given a non-blank lastModified when fetching then If-Modified-Since is sent`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("<rss></rss>"))

        service.fetchFeed(url(), lastModified = "Mon, 01 Jan 2024 00:00:00 GMT")

        val request = server.takeRequest()
        assertEquals("Mon, 01 Jan 2024 00:00:00 GMT", request.getHeader("If-Modified-Since"))
    }

    @Test
    fun `given null etag and lastModified when fetching then no conditional headers are sent`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("<rss></rss>"))

        service.fetchFeed(url())

        val request = server.takeRequest()
        assertNull(request.getHeader("If-None-Match"))
        assertNull(request.getHeader("If-Modified-Since"))
    }

    @Test
    fun `given blank (not null) etag and lastModified when fetching then no conditional headers are sent`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("<rss></rss>"))

        service.fetchFeed(url(), etag = "", lastModified = "   ")

        val request = server.takeRequest()
        assertNull("a blank etag must not be sent as If-None-Match", request.getHeader("If-None-Match"))
        assertNull("a blank lastModified must not be sent as If-Modified-Since", request.getHeader("If-Modified-Since"))
    }

    @Test
    fun `given a fetch call then the Accept header requests feed content types`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("<rss></rss>"))

        service.fetchFeed(url())

        val request = server.takeRequest()
        val accept = request.getHeader("Accept")
        assertTrue(accept != null && accept.contains("rss"))
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 4 — failure paths
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given a 500 response then the result is a failure mentioning the status code`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))

        val result = service.fetchFeed(url())

        assertTrue(result.isFailure)
        val message = result.exceptionOrNull()?.message.orEmpty()
        assertTrue("failure message must mention the HTTP status code", message.contains("500"))
    }

    @Test
    fun `given a 404 response then the result is a failure`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))

        val result = service.fetchFeed(url())

        assertTrue(result.isFailure)
    }

    @Test
    fun `given the server is unreachable then the result is a failure, never a thrown exception`() = runTest {
        server.shutdown()

        val result = service.fetchFeed(url())

        assertTrue("a network-level failure must be captured as Result.failure, not thrown", result.isFailure)
    }
}
