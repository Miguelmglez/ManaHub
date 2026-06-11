package com.mmg.manahub.feature.home.presentation

import androidx.annotation.StringRes
import com.mmg.manahub.core.domain.model.DeckSummary
import com.mmg.manahub.core.domain.model.DraftSet
import com.mmg.manahub.core.domain.model.news.NewsItem
// FirstStepItem is defined in the same package — no explicit import needed.

/**
 * Immutable UI state for the Home dashboard.
 *
 * Everything here is derived from existing repository flows — Home introduces no
 * new Room tables and no network calls solely for startup. The screen is useful
 * offline with cached/local data.
 */
data class HomeUiState(
    val isLoading: Boolean = true,
    val hero: HomeHeroState = HomeHeroState.Welcome(steps = emptyList()),
    val quickStartActions: List<QuickStartAction> = QuickStartAction.defaults,
    val libraryStats: LibraryStats? = null,
    /** Up to 3 latest news/video items. Null while the initial DB query is still pending. */
    val recentNews: List<NewsItem>? = null,
    val accountNudge: AccountNudge? = null,
    val isAuthenticated: Boolean = false,
    /** User's display name from DataStore. */
    val playerName: String? = null,
    /** User avatar URL from DataStore; null while not set. */
    val avatarUrl: String? = null,

    // ── Widget board ──────────────────────────────────────────────────────────
    /** Ordered list of placed widgets. Empty until the first layout emission. */
    val layout: List<WidgetInstance> = emptyList(),

    // ── Phase 2 data slices (null/empty = not loaded or widget not in layout) ──
    val lastGameRecap: LastGameRecap? = null,
    val playStreak: PlayStreak? = null,
    val winRate: WinRateStats? = null,
    val bestDeck: BestDeckStats? = null,
    val nemesis: NemesisStats? = null,
    val performanceDetails: PerformanceDetails? = null,
    val collectionByColor: Map<String, Int> = emptyMap(),
    val collectionByRarity: Map<String, Int> = emptyMap(),
    val discoverCards: List<DiscoverCard> = emptyList(),
    val cardOfTheDay: DiscoverCard? = null,
    val latestSets: List<DraftSet> = emptyList(),
    val wishlistStats: WishlistStats? = null,
    /** All of the user's decks, newest-first. Drives the Your Decks shelf widget. */
    val decks: List<DeckSummary> = emptyList(),

    // ── Phase 3 data slices ────────────────────────────────────────────────────
    val communityStats: com.mmg.manahub.core.domain.model.CommunityStats? = null,
    val tradeSummary: TradeSummary? = null,
    val activeTournamentSummary: TournamentSummary? = null,
)

// ─────────────────────────────────────────────────────────────────────────────
//  Phase 2 / Phase 3 widget data models
// ─────────────────────────────────────────────────────────────────────────────

/** Outcome and metadata of the most recent tracked game. */
data class LastGameRecap(
    val won: Boolean,
    val deckName: String?,
    val mode: String,
    val durationMs: Long,
    val opponentCount: Int,
)

/** Current and best play streak derived from the local session history. */
data class PlayStreak(
    val current: Int,
    val longest: Int,
    /** True if [current] counts consecutive wins; false if it counts consecutive plays. */
    val isWinStreak: Boolean,
)

/** Win-rate summary resolved against the local seat. */
data class WinRateStats(
    val wins: Int,
    val totalGames: Int,
    /** Most-recent-first list of game outcomes for the sparkline (true = win). */
    val recentResults: List<Boolean>,
) {
    /** Win rate in the 0..100 range; 0 when no games are tracked. */
    val percentage: Int
        get() = if (totalGames == 0) 0 else (wins * 100) / totalGames
}

/** The local seat's best-performing deck. */
data class BestDeckStats(
    val deckId: String?,
    val deckName: String,
    val wins: Int,
    val losses: Int,
    val colorIdentity: Set<String> = emptySet(),
) {
    val winRate: Int
        get() {
            val total = wins + losses
            return if (total == 0) 0 else (wins * 100) / total
        }
}

/** The opponent archetype that eliminates the local seat most often. */
data class NemesisStats(
    val archetype: String,
    val count: Int,
    val totalLosses: Int,
) {
    val percentage: Int
        get() = if (totalLosses == 0) 0 else (count * 100) / totalLosses
}

/** Aggregate performance figures shown by the performance & records widgets. */
data class PerformanceDetails(
    val avgWinTurn: Double?,
    val avgLifeOnWin: Double?,
    val avgLifeOnLoss: Double?,
    val longestGameMs: Long?,
    val mostGamesInOneDay: Int,
)

/** A discover/spotlight card surfaced from Scryfall or a cached pool. */
data class DiscoverCard(
    val id: String,
    val scryfallId: String,
    val name: String,
    val imageUrl: String?,
    val typeLine: String? = null,
)

/** Wishlist summary (account-gated). */
data class WishlistStats(
    val count: Int,
    val estimatedValueDisplay: String,
    val cards: Set<DiscoverCard> = emptySet(),
)

/** Trade inbox summary (account-gated). */
data class TradeSummary(
    val pendingCount: Int,
    val latestPreview: String?,
)

/** Active-tournament summary for the tournament widget. */
data class TournamentSummary(
    val tournamentId: Long,
    val name: String,
    val round: Int,
    val standing: Int?,
)

/**
 * The Context Hero shows a single primary CTA chosen by priority:
 * active game > active draft/tournament > new-user first steps > returning summary.
 *
 * [Welcome] is shown while the user still has pending first steps (non-empty [steps]),
 * OR when all steps are complete and [steps] is empty (shows the completion card).
 */
sealed interface HomeHeroState {
    /**
     * New-user / first-steps welcome.
     *
     * @param steps The filtered list of visible, non-skipped steps to show in the carousel.
     *              An empty list means the user has completed (or skipped) all first steps —
     *              the widget will render a "You're all set!" completion card.
     */
    data class Welcome(val steps: List<FirstStepItem>) : HomeHeroState

    /** Initial state while the first emission of derived flows is pending. */
    object Loading : HomeHeroState

    /** A game is actively running and can be resumed. */
    data class ActiveGame(val mode: String, val playerCount: Int) : HomeHeroState

    /** A draft simulation is in progress. */
    data class ActiveDraft(val setName: String) : HomeHeroState

    /** Returning-user summary with their name and lifetime game count. */
    data class Summary(val playerName: String, val totalGames: Int) : HomeHeroState
}

/** Snapshot of the user's local library for the summary card. */
data class LibraryStats(
    val totalCards: Int,
    val uniqueCards: Int,
    val deckCount: Int,
    val estimatedValueDisplay: String,
)

/**
 * A single contextual account prompt. Only ever one is active at a time, and only
 * when the user is unauthenticated and the prompt is not in its dismissal cooldown.
 *
 * For milestone nudges the message is a string resource ([messageRes]). For
 * ACTION_REQUIRED nudges the caller passes an arbitrary runtime [message] string
 * (e.g. a context-specific call-to-action) and [messageRes] defaults to 0.
 * Consumers should use [message] when non-null, otherwise resolve [messageRes].
 */
data class AccountNudge(
    /** Runtime message override — used for ACTION_REQUIRED where context is needed. */
    val message: String? = null,
    /** String resource for milestone nudges. Resolve with [stringResource] in Compose. */
    @StringRes val messageRes: Int = 0,
    val trigger: NudgeTrigger,
)

enum class NudgeTrigger {
    COLLECTION_MILESTONE, DECK_MILESTONE, GAME_MILESTONE, SYNC_PENDING, ACTION_REQUIRED
}

/** Simplified news wrapper removed in favor of rich NewsItem. */
// data class NewsItem(val id: String, val title: String, val imageUrl: String?)
