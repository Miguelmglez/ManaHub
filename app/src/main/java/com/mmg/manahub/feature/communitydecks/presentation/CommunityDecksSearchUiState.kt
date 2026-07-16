package com.mmg.manahub.feature.communitydecks.presentation

import androidx.annotation.StringRes
import com.mmg.manahub.R
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.CommunityDeckSearchFilters
import com.mmg.manahub.core.model.CommunityDeckSummary

/**
 * The Community Hub's two sections (Deck Doctor Community/Archetype plan, Phase 5, D8: "no new
 * bottom-nav destination — CommunityDecksScreen evolves into a Community Hub (Discover + Search)").
 * [SEARCH] is the pre-Phase-5 screen (now deck-name search + advanced filters, Discover/Search
 * overhaul 2026-07-15). [DISCOVER] is only reachable when
 * [CommunityDecksSearchUiState.discoverEnabled] is true (`communityEngineEnabledFlow`, D4) — when
 * that flag is off there is no tab row at all and the screen renders [SEARCH] directly, byte-for-byte
 * identical to the pre-Phase-5 screen (the hard regression bar the plan requires).
 */
enum class CommunityHubTab { DISCOVER, SEARCH }

/**
 * UI state for the Community Decks Hub (Discover + Search).
 *
 * @property query the DECK NAME search bar text (Discover/Search overhaul 2026-07-15 — this used
 *   to be a card-name query; card/commander filtering now lives in [advancedFilters]).
 * @property hasSearched flips to `true` after the first search is issued so the
 *   screen can distinguish "initial / nothing searched yet" from "searched but
 *   no results".
 * @property discoverEnabled `communityEngineEnabledFlow` (D4) — gates the Discover tab entirely
 *   (Phase 5). `false` means the screen shows no tab row, just [CommunityHubTab.SEARCH].
 * @property hubTab the active Hub section. Defaults to [CommunityHubTab.SEARCH] when opened via the
 *   `CommunityDecksByCard` deep-link — the route contract is unchanged — else
 *   [CommunityHubTab.DISCOVER] when [discoverEnabled].
 * @property isDiscoverLoading true only for the very first Discover load (all sections are fetched
 *   in parallel; per-section failures degrade silently to an empty/hidden section).
 * @property discoverUnavailable true only when EVERY Discover section came back empty/failed —
 *   shown as one inline error instead of seven silently-empty sections.
 * @property trendingCommanderCards / [trendingCardCards] full [Card]s resolved from
 *   `TrendingSnapshot.topCommanders`/`topCards` names (Scryfall `getCardByExactName`); unresolved
 *   names are dropped rather than shown as broken tiles.
 * @property advancedFilters the currently APPLIED advanced-search filter selection (Phase 2); its
 *   [CommunityAdvancedFilters.activeCount] drives the Tune-icon badge.
 */
data class CommunityDecksSearchUiState(
    val query: String = "",
    val selectedSort: CommunityDeckSort = CommunityDeckSort.POPULAR,
    val results: List<CommunityDeckSummary> = emptyList(),
    val totalCount: Int = 0,
    val hasMore: Boolean = false,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val hasSearched: Boolean = false,

    // ── Community Hub — Discover (Phase 5, overhauled 2026-07-15) ─────────────────
    val discoverEnabled: Boolean = false,
    val hubTab: CommunityHubTab = CommunityHubTab.DISCOVER,
    val isDiscoverLoading: Boolean = false,
    val discoverUnavailable: Boolean = false,
    val trendingCommanderCards: List<Card> = emptyList(),
    val trendingCardCards: List<Card> = emptyList(),
    val popularDecks: List<CommunityDeckSummary> = emptyList(),
    val recentDecks: List<CommunityDeckSummary> = emptyList(),
    val updatedDecks: List<CommunityDeckSummary> = emptyList(),
    val primerDecks: List<CommunityDeckSummary> = emptyList(),
    val featuredFormat: CommunityDeckFormatFilter = CommunityDeckFormatFilter.STANDARD,
    val featuredFormatDecks: List<CommunityDeckSummary> = emptyList(),

    // ── Search — advanced filters (Phase 2) ────────────────────────────────────────
    val advancedFilters: CommunityAdvancedFilters = CommunityAdvancedFilters(),
    val commanderQuery: String = "",
    val commanderResults: List<Card> = emptyList(),
    val isCommanderSearching: Boolean = false,
    val cardQuery: String = "",
    val cardResults: List<Card> = emptyList(),
    val isCardSearching: Boolean = false,
)

/**
 * The applied advanced-search selection (Community Hub Discover/Search overhaul, Phase 2).
 *
 * Every field maps to a verified-working Archidekt `api/decks/v3/` filter (see
 * `docs/adr/ADR-004-community-api-contracts.md` §1b) except [deckSize], which is free text parsed
 * to an `Int?` only at request-build time (Archidekt's `size` filter is exact-equality only, there
 * is no comparator). Deliberately omits any deck-tag filter — `deckTags` always statement-timeouts.
 */
data class CommunityAdvancedFilters(
    val format: CommunityDeckFormatFilter = CommunityDeckFormatFilter.ALL,
    val colors: Set<String> = emptySet(),
    val edhBracket: Int? = null,
    val commander: Card? = null,
    val card: Card? = null,
    val ownerUsername: String = "",
    val deckSize: String = "",
    val primersOnly: Boolean = false,
) {
    /** Number of distinct filters currently active — drives the Tune-icon badge. */
    val activeCount: Int
        get() = listOf(
            format != CommunityDeckFormatFilter.ALL,
            colors.isNotEmpty(),
            edhBracket != null,
            commander != null,
            card != null,
            ownerUsername.isNotBlank(),
            deckSize.isNotBlank(),
            primersOnly,
        ).count { it }
}

/**
 * Maps the presentation-level [CommunityAdvancedFilters] + the deck-name query bar + the active
 * sort/page into the data-layer [CommunityDeckSearchFilters] sent to Archidekt.
 */
fun CommunityAdvancedFilters.toSearchFilters(
    deckName: String?,
    orderBy: String?,
    page: Int,
    pageSize: Int,
): CommunityDeckSearchFilters = CommunityDeckSearchFilters(
    deckName = deckName?.takeIf { it.isNotBlank() },
    cardName = card?.name,
    commanderName = commander?.name,
    ownerUsername = ownerUsername.takeIf { it.isNotBlank() },
    deckFormatId = format.apiId,
    edhBracket = edhBracket,
    colors = colors,
    size = deckSize.toIntOrNull(),
    primersOnly = primersOnly,
    orderBy = orderBy,
    page = page,
    pageSize = pageSize,
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
    ;

    companion object {
        /**
         * The non-Commander, non-ALL formats eligible for Discover's weekly-rotating "Featured
         * format" section (Discover/Search overhaul, Phase 1). Order is fixed so the ISO week
         * number deterministically indexes into it.
         */
        val FEATURED_ROTATION: List<CommunityDeckFormatFilter> = listOf(
            STANDARD, MODERN, PIONEER, PAUPER, LEGACY, VINTAGE, OATHBREAKER, BRAWL, HISTORIC,
        )
    }
}
