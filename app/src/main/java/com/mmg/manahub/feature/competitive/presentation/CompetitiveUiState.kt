package com.mmg.manahub.feature.competitive.presentation

import com.mmg.manahub.core.model.news.NewsItem

/**
 * The six constructed formats used to filter the static deep-link catalog
 * (see [CompetitiveResourceCatalog]) and, historically, the now-removed live weekly-meta section.
 */
enum class CompetitiveFormat(val id: String, val displayName: String) {
    STANDARD("standard", "Standard"),
    PIONEER("pioneer", "Pioneer"),
    MODERN("modern", "Modern"),
    LEGACY("legacy", "Legacy"),
    VINTAGE("vintage", "Vintage"),
    PAUPER("pauper", "Pauper");

    companion object {
        fun fromId(id: String): CompetitiveFormat = entries.firstOrNull { it.id == id } ?: STANDARD
    }
}

/**
 * The three top-level tabs the Competitive screen groups [ResourceCategory] into (2026-08
 * redesign). Replaces the old single flat scroll of all 8 categories stacked vertically —
 * grouping lives here (screen/state layer) rather than on [ResourceCategory] itself, which stays
 * unchanged.
 *
 * [showsFormatSelector] is `false` only for [HUB]: none of Hub's three categories
 * (`STORES_HUBS`/`WATCH_LIVE`/`DECK_BUILDING`) ever branch on [CompetitiveFormat] in
 * [CompetitiveResourceCatalog], so showing the format chip row there would be purely decorative —
 * the exact problem this redesign fixes for the other two tabs.
 */
enum class CompetitiveTab(val id: String, val displayName: String, val categories: List<ResourceCategory>) {
    TOURNAMENTS(
        id = "tournaments",
        displayName = "Tournaments",
        categories = listOf(
            ResourceCategory.TOURNAMENT_DECKLISTS,
            ResourceCategory.STANDINGS_STATS,
            ResourceCategory.POWER_RANKINGS,
        ),
    ),
    METAGAME(
        id = "metagame",
        displayName = "Metagame",
        categories = listOf(
            ResourceCategory.TRENDING_DECKS_CARDS,
            ResourceCategory.LIMITED_RATINGS,
        ),
    ),
    HUB(
        id = "hub",
        displayName = "Hub",
        categories = listOf(
            ResourceCategory.STORES_HUBS,
            ResourceCategory.WATCH_LIVE,
            ResourceCategory.DECK_BUILDING,
        ),
    ),
    ;

    val showsFormatSelector: Boolean get() = this != HUB

    companion object {
        fun fromId(id: String): CompetitiveTab = entries.firstOrNull { it.id == id } ?: TOURNAMENTS

        /** Default expanded set: the first (highest-priority) category of every tab, so switching
         * into a tab for the first time never lands on an all-collapsed accordion. */
        val DEFAULT_EXPANDED_CATEGORIES: Set<String> = entries.map { it.categories.first().name }.toSet()
    }
}

/**
 * UI state for the Competitive screen: a tab selector grouping the static deep-link catalog into
 * Tournaments/Metagame/Hub, a format selector (filters the catalog, shown only on the two tabs
 * that have format-scoped entries), per-category accordion expand/collapse state, a persisted
 * event-locator postal code, and a Pro Tour news filter. As of the 2026-08 pivot to a 100% static,
 * zero-live-API deep-link catalog, this state carries NO network-backed loading/error sections for
 * the catalog — [CompetitiveResourceCatalog] is compiled-in data, not fetched. See
 * `feature/competitive/CLAUDE.md` for the full pivot rationale.
 */
data class CompetitiveUiState(
    val selectedTab: CompetitiveTab = CompetitiveTab.TOURNAMENTS,

    val selectedFormat: CompetitiveFormat = CompetitiveFormat.STANDARD,

    /** [ResourceCategory.name]s currently expanded in the accordion. In-memory UI state only —
     * nothing here is persisted; a fresh screen entry always resets to
     * [CompetitiveTab.DEFAULT_EXPANDED_CATEGORIES]. */
    val expandedCategories: Set<String> = CompetitiveTab.DEFAULT_EXPANDED_CATEGORIES,

    /** Persisted postal-code input for the event-locator CTA (DataStore-backed, see
     * [com.mmg.manahub.core.data.local.UserPreferencesDataStore.competitivePostalCodeFlow]). */
    val postalCode: String = "",

    /** Pro Tour / high-level competitive news+videos, filtered from the already-cached News feed.
     * Null while the first emission is pending; an empty list once loaded means no cached article
     * currently matches the Pro Tour keyword filter (not an error — the News feed simply hasn't
     * covered a Pro Tour recently). */
    val proTourContent: List<NewsItem>? = null,
)
