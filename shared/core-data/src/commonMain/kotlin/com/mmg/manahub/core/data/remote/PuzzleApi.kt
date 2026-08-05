package com.mmg.manahub.core.data.remote

import com.mmg.manahub.core.data.remote.dto.PuzzleTodayResponseDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get

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
        httpClient.get("${baseUrl}puzzle/today").body()
}
