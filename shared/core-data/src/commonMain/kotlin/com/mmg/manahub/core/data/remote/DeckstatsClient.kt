package com.mmg.manahub.core.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess

/**
 * The RAW response from deckstats.net's `get_deck` endpoint: an HTTP status flag plus the raw
 * body text. Deliberately NOT pre-parsed here — [com.mmg.manahub.core.data.remote.DeckstatsClient]'s
 * KDoc explains why the caller must attempt JSON parsing defensively (the endpoint returns
 * PLAIN TEXT, not JSON, on error, despite `response_type=json` being requested).
 */
data class DeckstatsRawResponse(val isSuccess: Boolean, val body: String)

/**
 * Client for deckstats.net's undocumented `api.php?action=get_deck` endpoint (D17 import-by-URL
 * adapter, Deck Doctor Community/Archetype plan, Phase 6). Reachability and the ERROR-path shape
 * were verified live in the Phase 3 spike (`docs/adr/ADR-004-community-api-contracts.md` §4); the
 * SUCCESS-path JSON shape was NOT — see [com.mmg.manahub.core.data.remote.dto.DeckstatsGetDeckResponseDto]'s
 * KDoc.
 *
 * ## Why this client returns raw text, not a parsed DTO
 * A confirmed-live probe returned an HTTP 400 with a PLAIN-TEXT body (`"Deck not found (N)."`), not
 * JSON, even though `response_type=json` was requested. A Ktor client configured with
 * `expectSuccess = true` (the convention every OTHER client in this codebase uses — see
 * [ArchidektClient]'s Koin-wired [io.ktor.client.HttpClient]) would THROW on that 400 before this
 * code could ever read the body. The [io.ktor.client.HttpClient] injected here MUST be configured
 * with `expectSuccess = false` (a documented DEVIATION from the rest of this codebase's client
 * convention, required specifically for this endpoint's non-JSON error shape) — see the Koin wiring
 * in `feature.decks.di.DecksKoinModule` for the concrete client this note refers to.
 */
class DeckstatsClient(
    private val httpClient: HttpClient,
    private val baseUrl: String = "https://deckstats.net/",
) {
    suspend fun getDeckRaw(ownerId: String, deckId: String): DeckstatsRawResponse {
        val response = httpClient.get("${baseUrl}api.php") {
            parameter("action", "get_deck")
            parameter("id_type", "saved")
            parameter("owner_id", ownerId)
            parameter("id", deckId)
            parameter("response_type", "json")
        }
        return DeckstatsRawResponse(isSuccess = response.status.isSuccess(), body = response.bodyAsText())
    }
}

/**
 * Extracts `(ownerId, deckId)` from a pasted deckstats.net deck URL
 * (`https://deckstats.net/decks/<ownerId>/<deckId>-<slug>`), or `null` when [url] doesn't match
 * that pattern. Deliberately permissive about scheme/trailing path/query — deckstats URLs are
 * user-pasted free text.
 */
fun parseDeckstatsUrl(url: String): Pair<String, String>? {
    val match = DECKSTATS_URL_REGEX.find(url) ?: return null
    val (ownerId, deckId) = match.destructured
    return ownerId to deckId
}

private val DECKSTATS_URL_REGEX = Regex("""deckstats\.net/decks/(\d+)/(\d+)""")
