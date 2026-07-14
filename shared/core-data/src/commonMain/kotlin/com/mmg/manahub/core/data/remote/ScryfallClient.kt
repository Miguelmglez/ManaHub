package com.mmg.manahub.core.data.remote

import com.mmg.manahub.core.data.remote.dto.CardCollectionRequestDto
import com.mmg.manahub.core.data.remote.dto.CardCollectionResponseDto
import com.mmg.manahub.core.data.remote.dto.CardDto
import com.mmg.manahub.core.data.remote.dto.ScryfallSetsResponseDto
import com.mmg.manahub.core.data.remote.dto.SearchResultDto
import com.mmg.manahub.core.data.remote.dto.SymbologyListDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

/**
 * Ktor-based client for the Scryfall REST API.
 *
 * Replaces the Retrofit [ScryfallApi] interface as part of the KMP migration.
 * Lives in `commonMain` so it can be shared across Android and Web targets.
 * All calls are plain suspend functions -- rate-limiting is handled externally by
 * [com.mmg.manahub.core.data.network.ScryfallRequestQueue].
 *
 * @param httpClient Ktor [HttpClient] preconfigured with content negotiation
 *   (kotlinx.serialization JSON) and, on Android, the OkHttp engine with caching/logging.
 * @param baseUrl    Base URL for the Scryfall API (e.g. `"https://api.scryfall.com/"`).
 *                   Must include a trailing slash.
 */
class ScryfallClient(
    private val httpClient: HttpClient,
    private val baseUrl: String,
) {

    /** Fuzzy card-name search, optionally scoped to a [set]. */
    suspend fun getCardByName(name: String, set: String? = null): CardDto =
        httpClient.get("${baseUrl}cards/named") {
            parameter("fuzzy", name)
            set?.let { parameter("set", it) }
        }.body()

    /** Exact card-name lookup. */
    suspend fun getCardByExactName(name: String): CardDto =
        httpClient.get("${baseUrl}cards/named") {
            parameter("exact", name)
        }.body()

    /** Paginated card search. */
    suspend fun searchCards(
        query: String,
        order: String = "name",
        dir: String = "auto",
        unique: String = "cards",
        page: Int = 1,
    ): SearchResultDto =
        httpClient.get("${baseUrl}cards/search") {
            parameter("q", query)
            parameter("order", order)
            parameter("dir", dir)
            parameter("unique", unique)
            parameter("page", page)
        }.body()

    /**
     * Same as [searchCards] but sends a `Cache-Control: no-cache` request header to bypass
     * the OkHttp disk cache. Used for `order:random` queries where a stable cache key would
     * otherwise return the same results on every "refresh".
     */
    suspend fun searchCardsNoCache(
        query: String,
        order: String = "name",
        dir: String = "auto",
        unique: String = "cards",
        page: Int = 1,
    ): SearchResultDto =
        httpClient.get("${baseUrl}cards/search") {
            header("Cache-Control", "no-cache")
            parameter("q", query)
            parameter("order", order)
            parameter("dir", dir)
            parameter("unique", unique)
            parameter("page", page)
        }.body()

    /** Fetches a single card by its Scryfall UUID. */
    suspend fun getCardById(scryfallId: String): CardDto =
        httpClient.get("${baseUrl}cards/$scryfallId").body()

    /** Fetches a card by set code and collector number. */
    suspend fun getCardBySetAndNumber(setCode: String, collectorNumber: String): CardDto =
        httpClient.get("${baseUrl}cards/$setCode/$collectorNumber").body()

    /** Batch-fetches cards via the /cards/collection endpoint (POST, max 75 identifiers). */
    suspend fun getCardCollection(request: CardCollectionRequestDto): CardCollectionResponseDto =
        httpClient.post("${baseUrl}cards/collection") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    /** Returns the full mana-symbology list. */
    suspend fun getAllSymbols(): SymbologyListDto =
        httpClient.get("${baseUrl}symbology").body()

    /** Returns all Scryfall sets. */
    suspend fun getSets(): ScryfallSetsResponseDto =
        httpClient.get("${baseUrl}sets").body()
}
