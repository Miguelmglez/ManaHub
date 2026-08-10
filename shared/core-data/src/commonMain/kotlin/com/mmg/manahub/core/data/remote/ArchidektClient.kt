package com.mmg.manahub.core.data.remote

import com.mmg.manahub.core.data.remote.dto.ArchidektDeckDetailDto
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
     * Searches `GET /api/decks/v3/` with every filter verified live 2026-07-15 (see
     * `docs/adr/ADR-004-community-api-contracts.md` §1b). Only non-null/non-empty
     * [CommunityDeckSearchFilters] fields are sent — `deckTags` is intentionally never exposed
     * (confirmed to always statement-timeout).
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
            filters.orderBy?.let { parameter("orderBy", it) }
            parameter("page", filters.page)
            parameter("pageSize", filters.pageSize)
        }.body()
    }
}
