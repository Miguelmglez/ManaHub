package com.mmg.manahub.core.data.remote

import com.mmg.manahub.core.data.remote.dto.LimitedRatingsSnapshotDto
import com.mmg.manahub.core.data.remote.dto.MetaSnapshotDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter

/**
 * Contract for [CompetitiveApi] — [com.mmg.manahub.core.data.repository.CompetitiveRepositoryImpl]
 * depends on this interface (not the concrete Ktor-backed class) so its cache/Worker layering
 * logic is unit testable with a trivial fake, without needing a Ktor engine mock in tests.
 * Mirrors [CommunityAggregateApiContract]'s contract-first shape.
 */
interface CompetitiveApiContract {
    suspend fun getWeeklyMeta(format: String): MetaSnapshotDto
    suspend fun getLimitedRatings(setCode: String, format: String): LimitedRatingsSnapshotDto
}

/**
 * KMP-pure HTTP client for the `manahub-competitive` Cloudflare Worker (Competitive feature,
 * Phase 3). Lives in `:shared:core-data` `commonMain` so it compiles for both Android and
 * wasmJs — mirrors the [CommunityAggregateApi] pattern already established for this project's
 * other Cloudflare Workers.
 *
 * Both endpoints return a non-2xx status with `{ status: "error", message: string }` on
 * failure; the injected [httpClient] is expected to be configured with `expectSuccess = true`
 * (the codebase-wide convention — see [CommunityAggregateApi]'s KDoc), so a non-2xx response
 * throws [io.ktor.client.plugins.ResponseException] rather than deserializing the error body.
 *
 * @param httpClient A Ktor [HttpClient] with `ContentNegotiation` (JSON) installed.
 * @param baseUrl The Worker's base URL, must end with `/`.
 */
class CompetitiveApi(
    private val httpClient: HttpClient,
    private val baseUrl: String,
) : CompetitiveApiContract {
    override suspend fun getWeeklyMeta(format: String): MetaSnapshotDto =
        httpClient.get("${baseUrl}meta/$format/weekly").body()

    override suspend fun getLimitedRatings(setCode: String, format: String): LimitedRatingsSnapshotDto =
        httpClient.get("${baseUrl}limited/$setCode/ratings") {
            parameter("format", format)
        }.body()
}
