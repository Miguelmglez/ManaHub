package com.mmg.manahub.feature.communitydecks.presentation

import androidx.annotation.StringRes
import com.mmg.manahub.R
import com.mmg.manahub.feature.communitydecks.domain.model.CommunityDeckSummary

/**
 * UI state for the Community Decks search / browse screen.
 *
 * Holds the raw query text, the active format/sort filters, the accumulated
 * result list (appended across pages), and the orthogonal loading flags
 * ([isLoading] for a fresh search, [isLoadingMore] for pagination).
 *
 * @property hasSearched flips to `true` after the first search is issued so the
 *   screen can distinguish "initial / nothing searched yet" from "searched but
 *   no results".
 */
data class CommunityDecksSearchUiState(
    val query: String = "",
    val selectedFormat: CommunityDeckFormatFilter = CommunityDeckFormatFilter.ALL,
    val selectedSort: CommunityDeckSort = CommunityDeckSort.POPULAR,
    val results: List<CommunityDeckSummary> = emptyList(),
    val totalCount: Int = 0,
    val hasMore: Boolean = false,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val hasSearched: Boolean = false,
)

/**
 * Sort options exposed to the user, mapped to Archidekt's `orderBy` API values.
 *
 * @property apiValue the Archidekt `orderBy` query value (leading `-` = descending).
 * @property labelRes the user-facing label resource.
 */
enum class CommunityDeckSort(val apiValue: String, @StringRes val labelRes: Int) {
    POPULAR("-viewCount", R.string.community_deck_sort_popular),
    RECENT("-createdAt", R.string.community_deck_sort_recent),
    UPDATED("-updatedAt", R.string.community_deck_sort_updated),
}

/**
 * Format filter options, mapped to Archidekt's numeric `deckFormat` ids.
 *
 * Labels are plain English strings (the app is English-only per CLAUDE.md and
 * these are standard MTG format names that are never translated), so they are
 * used directly rather than via string resources.
 *
 * @property apiId the Archidekt `deckFormat` id, or `null` for [ALL] (no filter).
 */
enum class CommunityDeckFormatFilter(val apiId: Int?, val label: String) {
    ALL(null, "All Formats"),
    STANDARD(1, "Standard"),
    MODERN(2, "Modern"),
    COMMANDER(3, "Commander"),
    LEGACY(4, "Legacy"),
    VINTAGE(5, "Vintage"),
    PAUPER(6, "Pauper"),
    PIONEER(15, "Pioneer"),
    OATHBREAKER(14, "Oathbreaker"),
    BRAWL(13, "Brawl"),
    HISTORIC(16, "Historic"),
}
