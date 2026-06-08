package com.mmg.manahub.feature.home.presentation

/**
 * Immutable UI state for the Home dashboard.
 *
 * Everything here is derived from existing repository flows — Home introduces no
 * new Room tables and no network calls solely for startup. The screen is useful
 * offline with cached/local data.
 */
data class HomeUiState(
    val isLoading: Boolean = true,
    val hero: HomeHeroState = HomeHeroState.Welcome,
    val quickStartActions: List<QuickStartAction> = QuickStartAction.defaults,
    val continueItems: List<ContinueItem> = emptyList(),
    val libraryStats: LibraryStats? = null,
    /** Up to 3 latest news/video items. */
    val recentNews: List<NewsItem> = emptyList(),
    val accountNudge: AccountNudge? = null,
    val isAuthenticated: Boolean = false,
    /** User avatar URL from DataStore; null while not set. */
    val avatarUrl: String? = null,
)

/**
 * The Context Hero shows a single primary CTA chosen by priority:
 * active game > active draft/tournament > returning summary > new-user welcome.
 */
sealed interface HomeHeroState {
    /** New-user / zero-data welcome. */
    object Welcome : HomeHeroState

    /** Initial state while the first emission of derived flows is pending. */
    object Loading : HomeHeroState

    /** A game is actively running and can be resumed. */
    data class ActiveGame(val mode: String, val playerCount: Int) : HomeHeroState

    /** A draft simulation is in progress. */
    data class ActiveDraft(val setName: String) : HomeHeroState

    /** Returning-user summary with their name and lifetime game count. */
    data class Summary(val playerName: String, val totalGames: Int) : HomeHeroState
}

/** A resumable item surfaced in the Continue section. */
data class ContinueItem(
    val id: String,
    val label: String,
    val subtitle: String,
    val type: ContinueType,
)

enum class ContinueType { GAME, DRAFT, TOURNAMENT, DECK }

/** Snapshot of the user's local library for the summary card. */
data class LibraryStats(
    val uniqueCards: Int,
    val deckCount: Int,
    val estimatedValueDisplay: String,
)

/**
 * A single contextual account prompt. Only ever one is active at a time, and only
 * when the user is unauthenticated and the prompt is not in its dismissal cooldown.
 */
data class AccountNudge(
    val message: String,
    val trigger: NudgeTrigger,
)

enum class NudgeTrigger {
    COLLECTION_MILESTONE, DECK_MILESTONE, GAME_MILESTONE, SYNC_PENDING, ACTION_REQUIRED
}

/** Lightweight news wrapper for the compact Latest section. */
data class NewsItem(val id: String, val title: String, val imageUrl: String?)
