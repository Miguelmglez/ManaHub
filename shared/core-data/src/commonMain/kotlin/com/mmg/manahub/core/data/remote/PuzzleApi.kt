package com.mmg.manahub.core.data.remote

import com.mmg.manahub.core.data.remote.dto.PuzzleTodayResponseDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders

/**
 * Contract for [PuzzleApi] — [PuzzleRepositoryImpl] depends on this interface (not the concrete
 * Ktor-backed class) so its logic is unit testable with a trivial fake, without needing a Ktor
 * engine mock in tests. Mirrors [com.mmg.manahub.core.data.remote.CommunityAggregateApiContract]'s
 * contract-first shape.
 */
interface PuzzleApiContract {
    suspend fun getTodayPuzzle(): PuzzleTodayResponseDto
}

/**
 * KMP-pure HTTP client for the `manahub-draft-api` Cloudflare Worker's daily-puzzle endpoint (Daily
 * Puzzle feature, Batch B1 foundation). Lives in `:shared:core-data` `commonMain` so it compiles for
 * both Android and wasmJs — mirrors the [CloudflareContentClient] / [CommunityAggregateApi] pattern
 * already established for this project's other Cloudflare Workers. The puzzle endpoint is served by
 * the SAME Worker as [CloudflareContentClient]'s draft content, on a different path
 * (`puzzle/today`), so [baseUrl] is the same `CLOUDFLARE_WORKER_URL` build config value.
 *
 * @param httpClient A Ktor [HttpClient] with `ContentNegotiation` (JSON) installed.
 * @param baseUrl The Worker's base URL, must end with `/`.
 */
class PuzzleApi(
    private val httpClient: HttpClient,
    private val baseUrl: String,
) : PuzzleApiContract {
    override suspend fun getTodayPuzzle(): PuzzleTodayResponseDto =
        httpClient.get("${baseUrl}puzzle/today") {
            // Force a real network round-trip on EVERY call. The Worker sets
            // `Cache-Control: public, max-age=60, stale-while-revalidate=60/300` on this endpoint,
            // and PuzzleKoinModule configures a real OkHttp disk cache for the Android client -- inside
            // that ~60s freshness window OkHttp would otherwise serve the cached body with ZERO
            // network round-trip, so a device whose last fetch landed just before 00:00 UTC could be
            // served yesterday's puzzle for up to ~60s into the new day (undermining ADR-006 Decision
            // 2's clock-skew-is-architecturally-impossible claim). `no-cache` (NOT `no-store`) still
            // lets the Worker's own ETag/If-None-Match handling save bandwidth via a 304 -- this only
            // removes the *blind* local-cache window that skips the network entirely. This header is
            // request-scoped (not a client-wide cache disable), so a future `getPuzzle(date)` for an
            // immutable past-date puzzle (`Cache-Control: public, max-age=31536000, immutable` on the
            // Worker) must NOT copy this -- it should stay cacheable.
            header(HttpHeaders.CacheControl, "no-cache")
        }.body()
}
