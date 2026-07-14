package com.mmg.manahub.core.model

/**
 * A single page of community-deck search results.
 *
 * @property totalCount total number of decks matching the query across all pages.
 * @property hasMore `true` when another page is available (the Archidekt response
 *   carried a non-null `next` URL), used by the UI to drive incremental paging.
 * @property decks the deck summaries for this page.
 */
data class CommunityDeckSearchResult(
    val totalCount: Int,
    val hasMore: Boolean,
    val decks: List<CommunityDeckSummary>,
)
