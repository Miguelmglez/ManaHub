package com.mmg.manahub.core.data.remote

import com.mmg.manahub.core.data.remote.dto.ArchidektDeckDetailDto
import com.mmg.manahub.core.data.remote.dto.ArchidektDeckTagDto
import com.mmg.manahub.core.data.remote.dto.ArchidektSearchResultDto
import com.mmg.manahub.core.model.CommunityDeckSearchFilters
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter

class ArchidektClient(
    private val httpClient: HttpClient,
    private val baseUrl: String,
) {
    suspend fun getDeckById(id: Int): ArchidektDeckDetailDto =
        httpClient.get("${baseUrl}api/decks/${id}/").body()

    /**
     * Fetches Archidekt's closed deck-tag catalog, `GET /api/decks/tags/v2/` (no auth, no query
     * params — verified live 2026-08-18: 434 entries, ~49 KB, alphabetically ordered). Intended to
     * be fetched ONCE and cached by the caller (see `CommunityDecksSearchViewModel`'s lazy
     * `deckTagsLoaded` guard) rather than re-fetched per picker open.
     */
    suspend fun getDeckTags(): List<ArchidektDeckTagDto> =
        httpClient.get("${baseUrl}api/decks/tags/v2/").body()

    /**
     * Searches `GET /api/decks/v3/` with every filter verified live 2026-07-15 (see
     * `docs/adr/ADR-004-community-api-contracts.md` §1b). Only non-null/non-empty
     * [CommunityDeckSearchFilters] fields are sent. `deckTags`/`tags`/`tagIds` (plural/alternate
     * names) are intentionally never exposed (confirmed to always statement-timeout); the SINGULAR
     * `deckTagName` param, however, is a real, working, validated exact-match filter — verified
     * live 2026-08-18 (see [CommunityDeckSearchFilters.deckTagName]'s KDoc).
     *
     * @throws IllegalArgumentException if [CommunityDeckSearchFilters.cardNames] carries more than
     *   one card — Archidekt's API only accepts a single `cardName` param; repeated `cardName`
     *   params reliably statement-timeout server-side (verified live 2026-07-24, see the ADR §1).
     *   Multi-card filtering must be decomposed by the caller before reaching this client — see
     *   [com.mmg.manahub.core.data.repository.CommunityDecksRepositoryImpl.searchDecksMultiCard].
     */
    suspend fun searchDecks(filters: CommunityDeckSearchFilters): ArchidektSearchResultDto {
        require(filters.cardNames.size <= 1) {
            "ArchidektClient.searchDecks only accepts a single cardName per request (repeated " +
                "cardName params reliably statement-timeout server-side) — caller must decompose " +
                "multi-card filtering into one search per card, see " +
                "CommunityDecksRepositoryImpl.searchDecksMultiCard."
        }
        return httpClient.get("${baseUrl}api/decks/v3/") {
            filters.deckName?.let { parameter("name", it) }
            filters.cardNames.singleOrNull()?.let { parameter("cardName", it) }
            filters.commanderName?.let { parameter("commanderName", it) }
            filters.ownerUsername?.let { parameter("ownerUsername", it) }
            filters.deckFormatId?.let { parameter("deckFormat", it) }
            filters.edhBracket?.let { parameter("edhBracket", it) }
            if (filters.colors.isNotEmpty()) parameter("colors", filters.colors.joinToString(","))
            filters.size?.let { parameter("size", it) }
            if (filters.primersOnly) parameter("primers", true)
            filters.deckTagName?.let { parameter("deckTagName", it) }
            filters.orderBy?.let { parameter("orderBy", it) }
            parameter("page", filters.page)
            parameter("pageSize", filters.pageSize)
        }.body()
    }
}
