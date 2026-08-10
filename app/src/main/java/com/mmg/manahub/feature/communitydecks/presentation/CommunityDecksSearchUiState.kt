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
 * Maximum number of cards the CARD advanced filter accepts (Archidekt multi-card search
 * expansion, 2026-07-24). Mirrors `MULTI_CARD_MAX` in `CommunityDecksRepositoryImpl` (data layer)
 * — kept as a SEPARATE constant rather than a shared import because this one gates UI selection
 * (picker hide, cap enforcement in the ViewModel) while the data-layer one bounds request fan-out;
 * both must independently agree on `3`, but they answer different questions.
 */
const val MAX_COMMUNITY_CARD_FILTERS = 3
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
    val selectedDiscoveryFormat: CommunityDeckFormatFilter = CommunityDeckFormatFilter.COMMANDER,

    // ── Search — advanced filters (Phase 2) ────────────────────────────────────────
    val advancedFilters: CommunityAdvancedFilters = CommunityAdvancedFilters(),
    val selectedFormats: List<CommunityDeckFormatFilter> = emptyList(),
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
 *
 * @property cards up to [MAX_COMMUNITY_CARD_FILTERS] cards the deck must ALL contain (Archidekt
 *   multi-card search expansion, 2026-07-24) — mapped 1:1 onto
 *   [CommunityDeckSearchFilters.cardNames] by [toSearchFilters]. `size <= 1` reaches Archidekt as
 *   a direct request; `size > 1` is fanned out and intersected client-side (see
 *   `com.mmg.manahub.core.data.repository.CommunityDecksRepositoryImpl.searchDecksMultiCard`).
 */
data class CommunityAdvancedFilters(
    val formats: CommunityDeckFormatFilter =CommunityDeckFormatFilter.COMMANDER,
    val colors: Set<String> = emptySet(),
    val edhBracket: Int? = null,
    val commander: Card? = null,
    val cards: List<Card> = emptyList(),
    val ownerUsername: String = "",
    val deckSize: String = "",
    val primersOnly: Boolean = false,
) {
    /**
     * Number of distinct filters currently active — drives the Tune-icon badge. Each selected
     * [cards] entry counts individually (2 cards selected contributes `2`, not `1`) so the badge
     * reflects true filter weight rather than collapsing to a single boolean like every other
     * filter here (Archidekt multi-card search expansion, 2026-07-24).
     */
    val activeCount: Int
        get() = listOf(
            colors.isNotEmpty(),
            edhBracket != null,
            commander != null,
            ownerUsername.isNotBlank(),
            deckSize.isNotBlank(),
            primersOnly,
        ).count { it } + cards.size
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
    cardNames = cards.map { it.name },
    commanderName = commander?.name,
    ownerUsername = ownerUsername.takeIf { it.isNotBlank() },
    deckFormatId = formats.apiId,
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
 * Declaration order IS the chip display order (curated 2026-07-24, Archidekt multi-card/more-
 * formats expansion): the 6 "big" constructed formats first, then Commander variants grouped
 * together, then the remaining casual/niche formats — rather than a flat numeric-id or
 * alphabetical order, which would scatter e.g. Commander/Commander 1v1/Duel Commander/Pauper
 * EDH/PreDH apart from each other.
 *
 * @property apiId the Archidekt `deckFormat` id. There is no "ALL / no filter" case — every entry
 *   maps to a concrete Archidekt format id, and [CommunityAdvancedFilters.formats] always holds a
 *   real selection (defaulting to [COMMANDER]).
 */
enum class CommunityDeckFormatFilter(val apiId: Int, val label: String) {
    STANDARD(1, "Standard"),
    PIONEER(15, "Pioneer"),
    MODERN(2, "Modern"),
    LEGACY(4, "Legacy"),
    VINTAGE(5, "Vintage"),
    PAUPER(6, "Pauper"),
    COMMANDER(3, "Commander") ;


    companion object {
        /**
         * The non-Commander, non-ALL formats eligible for Discover's weekly-rotating "Featured
         * format" section (Discover/Search overhaul, Phase 1). Order is fixed so the ISO week
         * number deterministically indexes into it.
         */
        val FEATURED_ROTATION: List<CommunityDeckFormatFilter> = listOf(
            STANDARD, MODERN, PIONEER, PAUPER, LEGACY, VINTAGE)
    }
}
