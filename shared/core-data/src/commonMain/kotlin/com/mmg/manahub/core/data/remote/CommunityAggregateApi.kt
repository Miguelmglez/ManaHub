package com.mmg.manahub.core.data.remote

import com.mmg.manahub.core.data.remote.dto.CommanderAggregateResponseDto
import com.mmg.manahub.core.data.remote.dto.SimilarDecksResponseDto
import com.mmg.manahub.core.data.remote.dto.SixtyAggregateResponseDto
import com.mmg.manahub.core.data.remote.dto.TrendingResponseDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter

/**
 * Contract for [CommunityAggregateApi] — [CommunityAggregateRepositoryImpl] depends on this
 * interface (not the concrete Ktor-backed class) so its cache/Worker layering logic is unit
 * testable with a trivial fake, without needing a Ktor engine mock in tests.
 */
interface CommunityAggregateApiContract {
    suspend fun getCommanderAggregate(commanderName: String): CommanderAggregateResponseDto
    suspend fun getSixtyAggregate(signatureCards: List<String>, format: Int): SixtyAggregateResponseDto
    suspend fun getSimilar(commanderName: String, limit: Int): SimilarDecksResponseDto
    suspend fun getTrending(week: String?): TrendingResponseDto
}

/**
 * KMP-pure HTTP client for the `manahub-community` Cloudflare Worker (Phase 3.3). Lives in
 * `:shared:core-data` `commonMain` so it compiles for both Android and wasmJs — mirrors the
 * [ArchidektClient] / [CloudflareContentClient] pattern already established for this project's
 * other Cloudflare Workers.
 *
 * @param httpClient A Ktor [HttpClient] with `ContentNegotiation` (JSON) installed.
 * @param baseUrl The Worker's base URL, must end with `/`.
 */
class CommunityAggregateApi(
    private val httpClient: HttpClient,
    private val baseUrl: String,
) : CommunityAggregateApiContract {
    override suspend fun getCommanderAggregate(commanderName: String): CommanderAggregateResponseDto =
        httpClient.get("${baseUrl}v1/aggregate") {
            parameter("commander", commanderName)
        }.body()

    override suspend fun getSixtyAggregate(signatureCards: List<String>, format: Int): SixtyAggregateResponseDto =
        httpClient.get("${baseUrl}v1/aggregate") {
            parameter("cards", signatureCards.joinToString(","))
            parameter("format", format)
        }.body()

    override suspend fun getSimilar(commanderName: String, limit: Int): SimilarDecksResponseDto =
        httpClient.get("${baseUrl}v1/similar") {
            parameter("commander", commanderName)
            parameter("limit", limit)
        }.body()

    override suspend fun getTrending(week: String?): TrendingResponseDto =
        httpClient.get("${baseUrl}v1/trending") {
            week?.let { parameter("week", it) }
        }.body()
}
