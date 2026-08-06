package com.mmg.manahub.feature.home.presentation

import androidx.annotation.StringRes
import com.mmg.manahub.R
import com.mmg.manahub.core.model.DeckSummary
import com.mmg.manahub.core.model.DraftSet
import com.mmg.manahub.core.model.Friend
import com.mmg.manahub.core.model.NudgeTrigger
import com.mmg.manahub.core.model.QuickStartAction
import com.mmg.manahub.core.model.TradeProposal
import com.mmg.manahub.core.model.news.NewsItem
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
    /** True when the persisted news filters differ from the English-only default (drives the Reset CTA). */
    val newsFiltersActive: Boolean = false,
    val accountNudge: AccountNudge? = null,
    val isAuthenticated: Boolean = false,
    /**
     * True once the FIRST real auth-session emission (Authenticated or Unauthenticated, never
     * [com.mmg.manahub.core.domain.auth.SessionState.Loading]) has landed (Home widget board
     * overhaul, TASK 7a). Account-gated widgets must render [isAuthenticated] as a definitive
     * "signed out" state ONLY once this is true — otherwise a still-resolving session is
     * mistaken for signed-out and the account-gated placeholder flashes before real content.
     */
    val authResolved: Boolean = false,
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
    /** The independent Random card widget's card (repurposed from the old card-of-the-day). */
    val cardOfTheDay: DiscoverCard? = null,
    /** Load state of the Discover cards row (drives spinner vs. retry affordance). */
    val discoverLoadState: DiscoverLoadState = DiscoverLoadState.LOADING,
    /** Load state of the independent Random card widget. */
    val randomCardLoadState: DiscoverLoadState = DiscoverLoadState.LOADING,
    /** Set code the Discover cards row is currently scoped to, or null when unfiltered. */
    val discoverSetCode: String? = null,
    /**
     * The full [com.mmg.manahub.core.model.MagicSet] the Discover row is scoped to, or null
     * when unfiltered. Drives the set-icon + code affordance in the widget header.
     */
    val discoverSet: com.mmg.manahub.core.model.MagicSet? = null,
    val latestSets: List<DraftSet> = emptyList(),
    val wishlistStats: WishlistStats? = null,
    /**
     * All of the user's decks, newest-first. Drives the Your Decks shelf widget. Null until the
     * first Room emission lands (Home widget board overhaul, TASK 7b) — distinguishes "still
     * loading" from "the user genuinely has zero decks".
     */
    val decks: List<DeckSummary>? = null,
    /**
     * Newest-first collection additions for the RECENTLY_ADDED widget (Home feature overhaul
     * Phase 2.1). Null until the first emission lands (TASK 7b); empty once loaded means the
     * collection genuinely has no cards yet.
     */
    val recentlyAdded: List<RecentlyAddedCard>? = null,

    // ── Phase 3 data slices ────────────────────────────────────────────────────
    val tradeSummary: TradeSummary? = null,
    /**
     * Actual matched cards for the Trades Hub Suggestions section (Home widget board overhaul,
     * TASK 4b — replaces the old count-only slide). Null while loading.
     */
    val tradeSuggestionPreviews: List<TradeSuggestionPreview>? = null,
    /**
     * Open-for-trade summary (count, estimated value, and a few card thumbnails) for the Trades
     * Hub (TASK 4b). Null while loading.
     */
    val openForTradePreview: OpenForTradePreview? = null,
    val activeTournamentSummary: TournamentSummary? = null,
    /** Real accepted-friend count (Home feature overhaul Phase 1.2.d — replaces the old hardcoded 0). */
    val friendCount: Int = 0,
    /** Newest pending friend request headline for the Friends widget, or null. */
    val latestFriendRequestName: String? = null,

    /**
     * Up to 5 recent friends for the FRIENDS widget (Home widget board overhaul, TASK 5a). Null
     * until the first emission lands — distinguishes "still loading" from "the user genuinely has
     * zero friends" (TASK 7b).
     */
    val friends: List<Friend>? = null,
    /**
     * Up to 3 most recent trade proposals for the Trades Hub. Null until the first emission
     * lands (TASK 7b).
     */
    val recentTrades: List<TradeProposal>? = null,

    // ── Gamification (Phase 2) ──────────────────────────────────────────────────
    /** Master toggle. When false every gamification surface (widgets + hero suggestion) is hidden. */
    val gamificationEnabled: Boolean = true,
    /** Level / XP / streak / quest summary for the Home gamification widgets; null until loaded. */
    val gamification: HomeGamification? = null,
)

/**
 * Compact gamification snapshot for the Home dashboard widgets (Phase 2).
 *
 * Derived from the gamification repository's progression, quest board, and streak flows. All quest
 * counts are across daily + weekly.
 *
 * @param level current player level.
 * @param xpIntoLevel XP earned into the current level.
 * @param xpForNextLevel XP span of the current level (0 → bar reads empty).
 * @param streak current daily-activity streak.
 * @param dailyDone completed daily quests today.
 * @param dailyTotal total daily quests today.
 * @param claimableCount completed-but-unclaimed quests across daily + weekly.
 * @param topQuests up to 3 in-progress/claimable quests for the Quests widget (claimable first).
 */
data class HomeGamification(
    val level: Int,
    val xpIntoLevel: Long,
    val xpForNextLevel: Long,
    val streak: Int,
    val dailyDone: Int,
    val dailyTotal: Int,
    val claimableCount: Int,
    val topQuests: List<HomeQuest>,
) {
    /** Progress into the current level, clamped to [0f, 1f]. */
    val levelProgress: Float
        get() = if (xpForNextLevel <= 0L) 0f
        else (xpIntoLevel.toFloat() / xpForNextLevel).coerceIn(0f, 1f)
}

/** A single quest preview row for the Home Quests widget. */
data class HomeQuest(
    val instanceId: String,
    val title: String,
    val emoji: String,
    val progress: Int,
    val target: Int,
    val isClaimable: Boolean,
) {
    val progressFraction: Float
        get() = if (target <= 0) 0f else (progress.toFloat() / target).coerceIn(0f, 1f)
}

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

/**
 * A single collection row for the RECENTLY_ADDED widget (Home feature overhaul Phase 2.1).
 *
 * @param rowId The unique `user_card_collection` row id — used as the LazyRow key so duplicate
 *   scryfallIds (e.g. two separately-added copies) never collide, unlike [DiscoverCard.id].
 * @param card The resolved card image/name/typeLine for display + tap-through to Card Detail.
 * @param quantity Quantity on the row; drives the small badge when > 1.
 */
data class RecentlyAddedCard(
    val rowId: String,
    val card: DiscoverCard,
    val quantity: Int,
)

/** Wishlist summary (account-gated). */
data class WishlistStats(
    val count: Int,
    val estimatedValueDisplay: String,
    val cards: Set<DiscoverCard> = emptySet(),
)

/**
 * Trade inbox summary (account-gated).
 *
 * @param latestItemCount Item count of the newest pending proposal, rendered via
 *   `R.string.home_trade_inbox_preview` (a formatted template, not a VM-composed string) — null
 *   when there is no pending proposal. [com.mmg.manahub.feature.home.presentation.HomeViewModel]
 *   hydrates this via a bounded [com.mmg.manahub.core.data.repository.TradesRepository
 *   .refreshProposalThread] fan-out (Home widget board overhaul, TASK 4a) — `refreshProposals`
 *   alone only fetches proposal METADATA, never items, so this would otherwise always read 0.
 */
data class TradeSummary(
    val pendingCount: Int,
    val latestItemCount: Int?,
)

/**
 * A single matched-trade card preview for the Trades Hub Suggestions section (Home widget board
 * overhaul, TASK 4b).
 *
 * @param card The matched card, resolved from the local cache for a thumbnail.
 * @param counterpartyName The friend's nickname on the other side of the match, or null when it
 *   could not be resolved locally (never omit the row for this reason — degrade the name instead).
 */
data class TradeSuggestionPreview(
    val id: String,
    val card: DiscoverCard,
    val counterpartyName: String?,
)

/**
 * Open-for-trade summary with real card thumbnails (Home widget board overhaul, TASK 4b —
 * replaces the old count-only slide).
 */
data class OpenForTradePreview(
    val count: Int,
    val valueDisplay: String?,
    val cards: List<DiscoverCard>,
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

    /**
     * One or more completed quests are waiting to be claimed (gamification Phase 2). Highest
     * priority when applicable and gamification is enabled; tapping opens the Profile Quests tab.
     */
    data class QuestsReady(val count: Int) : HomeHeroState

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

/** Simplified news wrapper removed in favor of rich NewsItem. */
// data class NewsItem(val id: String, val title: String, val imageUrl: String?)

/**
 * Category filter for the Home COMMUNITY_DECKS widget (Home widget board overhaul, TASK 5b).
 * Mirrors the Community Hub Discover tab's sort options
 * ([com.mmg.manahub.feature.communitydecks.presentation.CommunityDecksSearchViewModel.loadDiscover])
 * with a scope narrow enough for a single [com.mmg.manahub.core.model.CommunityDeckSearchFilters]
 * request per selection.
 *
 * @param persistedId Stable DataStore persistence key — never rename.
 * @param titleRes Label shown in the category picker sheet.
 * @param descriptionRes Brief summary of what this category surfaces.
 * @param orderBy Archidekt `orderBy` query param value (see [com.mmg.manahub.core.model
 *   .CommunityDeckSearchFilters.orderBy]).
 * @param primersOnly When true, scopes to decks that have a written primer.
 */
enum class HomeCommunityDeckCategory(
    val persistedId: String,
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    val orderBy: String,
    val primersOnly: Boolean = false,
) {
    POPULAR(
        persistedId = "popular",
        titleRes = R.string.home_community_decks_category_popular,
        descriptionRes = R.string.home_community_decks_category_popular_desc,
        orderBy = "-viewCount"
    ),
    RECENT(
        persistedId = "recent",
        titleRes = R.string.home_community_decks_category_recent,
        descriptionRes = R.string.home_community_decks_category_recent_desc,
        orderBy = "-createdAt"
    ),
    UPDATED(
        persistedId = "updated",
        titleRes = R.string.home_community_decks_category_updated,
        descriptionRes = R.string.home_community_decks_category_updated_desc,
        orderBy = "-updatedAt"
    ),
    PRIMERS(
        persistedId = "primers",
        titleRes = R.string.home_community_decks_category_primers,
        descriptionRes = R.string.home_community_decks_category_primers_desc,
        orderBy = "-viewCount",
        primersOnly = true,
    );

    companion object {
        /** Resolves a persisted id back to its category, defaulting to [POPULAR] when unset/unknown. */
        fun fromPersistedId(id: String?): HomeCommunityDeckCategory =
            entries.firstOrNull { it.persistedId == id } ?: POPULAR
    }
}

/**
 * Home DAILY_PUZZLE widget preview state (ADR-006, Batch B2), fed by [HomeViewModel.dailyPuzzleFlow].
 *
 * Deliberate deviation from the [HomeViewModel.trendingFlow]/[HomeViewModel.communityDecksFlow]
 * convention of collapsing every failure to `null` and hiding the widget silently: this widget
 * distinguishes [Loading]/[Loaded]/[Unavailable] so it can show a real "today's puzzle isn't
 * available right now" message instead of vanishing — losing a whole widget silently is a poor
 * experience for a feature the user deliberately added to their board (gallery-only, opt-in), as
 * opposed to [HomeWidgetType.TRENDING_COMMANDERS]/[HomeWidgetType.COMMUNITY_DECKS], which are
 * "nice-to-have if reachable" community-data widgets.
 */
sealed interface DailyPuzzleWidgetState {
    object Loading : DailyPuzzleWidgetState

    /**
     * @param puzzleType the fetched puzzle's type — the widget only shows a play/preview CTA for a
     *   type it recognizes ([com.mmg.manahub.core.model.puzzle.PuzzleType.GUESS_CARD]); any other
     *   type (including UNKNOWN) still counts as "loaded" but the widget degrades its copy rather
     *   than claim there is something playable it cannot actually render.
     * @param attemptsUsed guesses submitted so far today (0 if the user has not attempted yet).
     * @param solved whether today's attempt (if any) already ended in a correct guess.
     */
    data class Loaded(
        val puzzleType: com.mmg.manahub.core.model.puzzle.PuzzleType,
        val attemptsUsed: Int,
        val solved: Boolean,
    ) : DailyPuzzleWidgetState

    /** Today's puzzle could not be fetched (network/server failure, or the feature's DI is absent). */
    object Unavailable : DailyPuzzleWidgetState
}
