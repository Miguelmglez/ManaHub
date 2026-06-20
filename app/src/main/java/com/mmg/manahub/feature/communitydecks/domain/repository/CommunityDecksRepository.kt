package com.mmg.manahub.feature.communitydecks.domain.repository

import com.mmg.manahub.core.domain.model.DataResult
import com.mmg.manahub.feature.communitydecks.domain.model.CommunityDeck
import com.mmg.manahub.feature.communitydecks.domain.model.CommunityDeckSearchResult

/**
 * Contract for fetching community decks (Archidekt).
 *
 * Implementations are responsible for the cache-first / network-fallback strategy
 * and for surfacing a stale-cache hit via [DataResult.Success.isStale] when the
 * network refresh fails.
 */
interface CommunityDecksRepository {

    /**
     * Fetches the full detail of a single Archidekt deck.
     *
     * Returns a fresh cache hit, then a network result (which is cached), then — on
     * network failure — a stale cache hit flagged with `isStale = true`. Only when
     * there is no cache at all does it surface a [DataResult.Error].
     */
    suspend fun getDeckById(archidektId: Int): DataResult<CommunityDeck>

    /**
     * Searches community decks on Archidekt (paged, not cached).
     *
     * Search results are intentionally NOT cached — pagination makes a coherent cache
     * complex, and individual decks are still cached on the detail view. Every
     * parameter is optional and omitted from the request when null.
     *
     * Surfaces a [DataResult.Error] on a server timeout (Archidekt returns
     * `count = -1` with empty results when a `cardName + deckFormat` query on a popular
     * card exceeds its statement timeout) so the UI can suggest a narrower query.
     */
    suspend fun searchDecks(
        cardName: String? = null,
        deckFormat: Int? = null,
        orderBy: String? = null,
        page: Int = 1,
        pageSize: Int = 20,
    ): DataResult<CommunityDeckSearchResult>
}
