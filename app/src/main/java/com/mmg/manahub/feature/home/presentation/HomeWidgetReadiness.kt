package com.mmg.manahub.feature.home.presentation

import com.mmg.manahub.core.model.CommunityDeckSummary

/**
 * Widget data that lives outside [HomeUiState] (independent `StateFlow`s on [HomeViewModel]) but
 * still decides whether a widget is ready to render.
 *
 * @param trendingLoaded true once the trending fetch resolved (its value may be null = hidden).
 * @param communityDecks the COMMUNITY_DECKS list; null while loading.
 * @param dailyPuzzle the DAILY_PUZZLE preview state.
 * @param competitiveEnabled the COMPETITIVE flag; null until the persisted value is read.
 */
data class HomeWidgetExtras(
    val trendingLoaded: Boolean = false,
    val communityDecks: List<CommunityDeckSummary>? = null,
    val dailyPuzzle: DailyPuzzleWidgetState = DailyPuzzleWidgetState.Loading,
    val competitiveEnabled: Boolean? = null,
)

/**
 * The single readiness predicate for a widget body: true only once what the widget would show is
 * certain, so it can swap its skeleton for real content exactly once.
 *
 * - Board not ready (layout not decoded or auth [AuthGate.Unknown]) → not ready.
 * - Account-gated widget while signed out → ready (renders its gated placeholder).
 * - Otherwise ready once the widget's own data slice has resolved (content, empty or failed).
 */
fun HomeWidgetType.isReady(state: HomeUiState, extras: HomeWidgetExtras): Boolean {
    if (!state.boardReady) return false
    if (audience == WidgetAudience.ACCOUNT_GATED && state.auth !is AuthGate.SignedIn) return true
    return when (this) {
        HomeWidgetType.CONTEXT_HERO -> state.hero !is HomeHeroState.Loading
        HomeWidgetType.QUICK_ACTIONS -> state.quickStartLoaded
        HomeWidgetType.PROGRESSION_HUB,
        HomeWidgetType.QUESTS_HUB -> state.gamificationLoaded
        HomeWidgetType.GAME_STATS_HUB -> state.gameStatsLoaded
        HomeWidgetType.COLLECTION_STATS_HUB -> state.libraryStats != null
        HomeWidgetType.YOUR_DECKS_SHELF -> state.decks != null
        HomeWidgetType.WISHLIST_PROGRESS -> state.wishlistStats != null
        HomeWidgetType.RECENTLY_ADDED -> state.recentlyAdded != null
        HomeWidgetType.DISCOVER_CARDS -> state.discoverLoadState != DiscoverLoadState.LOADING
        // A re-roll keeps the previous card on screen, so an existing card is always ready.
        HomeWidgetType.CARD_OF_THE_DAY ->
            state.cardOfTheDay != null || state.randomCardLoadState == DiscoverLoadState.FAILED
        HomeWidgetType.LATEST_SETS -> state.latestSets != null
        HomeWidgetType.MTG_NEWS -> state.recentNews != null
        HomeWidgetType.RULES_TIP -> true
        HomeWidgetType.TRADES_HUB ->
            state.recentTrades != null &&
                state.openForTradePreview != null &&
                state.tradeSuggestionPreviews != null
        HomeWidgetType.FRIENDS -> state.friends != null
        HomeWidgetType.TRENDING_COMMANDERS -> extras.trendingLoaded
        HomeWidgetType.COMMUNITY_DECKS -> extras.communityDecks != null
        HomeWidgetType.DAILY_PUZZLE -> extras.dailyPuzzle !is DailyPuzzleWidgetState.Loading
        HomeWidgetType.COMPETITIVE -> extras.competitiveEnabled != null
    }
}
