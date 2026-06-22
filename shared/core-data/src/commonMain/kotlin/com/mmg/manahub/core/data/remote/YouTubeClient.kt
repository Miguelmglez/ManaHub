package com.mmg.manahub.core.data.remote

import com.mmg.manahub.core.data.remote.dto.YouTubeSearchResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter

/**
 * KMP-pure HTTP client for the YouTube Data API v3 search endpoint.
 *
 * Replaces the Retrofit-based `YouTubeApi` interface. Lives in
 * `:shared:core-data` `commonMain` so it compiles for both Android and wasmJs.
 *
 * The API key is injected as a constructor parameter and appended as a query
 * parameter on each request — no OkHttp interceptor needed.
 *
 * @param httpClient A Ktor [HttpClient] with `ContentNegotiation` (JSON) installed.
 *   On Android the OkHttp engine is used; on wasmJs the Js engine is used.
 * @param baseUrl The YouTube API v3 base URL, must end with `/`.
 * @param apiKey The YouTube Data API key.
 */
class YouTubeClient(
    private val httpClient: HttpClient,
    private val baseUrl: String,
    private val apiKey: String,
) {

    /**
     * Searches for YouTube videos matching the given [query].
     *
     * Maps directly to `GET /search` on the YouTube Data API v3.
     */
    suspend fun searchVideos(
        query: String,
        part: String = "snippet",
        type: String = "video",
        maxResults: Int = 15,
        order: String = "relevance",
        language: String = "en",
    ): YouTubeSearchResponse =
        httpClient.get("${baseUrl}search") {
            parameter("part", part)
            parameter("q", query)
            parameter("type", type)
            parameter("maxResults", maxResults)
            parameter("order", order)
            parameter("relevanceLanguage", language)
            parameter("key", apiKey)
        }.body()
}
