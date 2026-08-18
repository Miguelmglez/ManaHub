package com.mmg.manahub.core.domain.repository

import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.model.CommunityDeck
import com.mmg.manahub.core.model.CommunityDeckSearchFilters
import com.mmg.manahub.core.model.CommunityDeckSearchResult

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
     * [CommunityDeckSearchFilters] field is optional and omitted from the request when null/empty.
     *
     * Surfaces a [DataResult.Error] on a server timeout (Archidekt returns
     * `count = -1` with empty results when a query on a popular card/commander exceeds its
     * statement timeout) so the UI can suggest a narrower query.
     *
     * ## Multi-card contract (Archidekt multi-card search expansion, 2026-07-24)
     * When [CommunityDeckSearchFilters.cardNames] carries more than one card, the implementation
     * decomposes the request into one Archidekt search per card (Archidekt's `cardName` filter
     * accepts only a single value — repeated params reliably statement-timeout server-side, see
     * `docs/adr/ADR-004-community-api-contracts.md` §1/§1b) and intersects the resulting deck ids
     * client-side. In that mode: [CommunityDeckSearchResult.hasMore] is always `false` (no
     * deepening pagination on an intersected result — it would both break sort order and multiply
     * requests), `totalCount` is the honest intersected deck count (not any single card's
     * Archidekt `count`), and a per-card server-side timeout fails the WHOLE search with an
     * actionable error naming the offending card rather than silently dropping that constraint.
     * `[CommunityDeckSearchFilters.page]`/`pageSize` are ignored in this mode — see
     * `com.mmg.manahub.core.data.repository.CommunityDecksRepositoryImpl.searchDecksMultiCard` for
     * the full algorithm and its known top-~180-per-card approximation.
     */
    suspend fun searchDecks(filters: CommunityDeckSearchFilters): DataResult<CommunityDeckSearchResult>

    /**
     * Fetches Archidekt's closed deck-tag catalog (434 entries as of 2026-08-18, alphabetically
     * ordered) for the Advanced Search sheet's "Deck tag" picker, which filters this list
     * client-side. Not cached by the repository — the caller (`CommunityDecksSearchViewModel`)
     * fetches this once, lazily, and holds it in UI state for the ViewModel's lifetime.
     */
    suspend fun getDeckTags(): DataResult<List<String>>
}
