package com.mmg.manahub.feature.stats.presentation

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.mmg.manahub.feature.game.domain.model.ArchetypeMatchupData
import com.mmg.manahub.core.data.local.entity.SurveyStatus
import com.mmg.manahub.core.model.CollectionStats
import com.mmg.manahub.core.model.MagicSet
import com.mmg.manahub.core.model.MtgColor
import com.mmg.manahub.core.model.PreferredCurrency
import com.mmg.manahub.core.model.TradeStats
import com.mmg.manahub.core.util.PriceFormatter

/** Selectable top-level tabs on the Stats screen. */
enum class StatsTab { COLLECTION, GAMES, TRADES }

/**
 * Aggregated KPI snapshot for the Games tab.
 *
 * @param totalGames Total number of recorded sessions.
 * @param wins Number of sessions where the app user won.
 * @param winrate Fraction in [0f, 1f]; 0f when totalGames == 0.
 * @param avgDurationMs Rounded average game duration in milliseconds.
 * @param favoriteMode Mode string played most often, null when no data.
 * @param mostFrequentLoss Most common elimination reason, null when no data.
 * @param pendingSurveys Count of sessions with PENDING or PARTIAL survey status.
 * @param currentStreak Consecutive wins ending at the most recent game; 0 if the most recent game
 *   was a loss (or there is no history). Derived from the local-seat session history (Phase 3).
 * @param bestStreak Longest run of consecutive wins across the fetched session history (Phase 3).
 */
@Immutable
data class GameStats(
    val totalGames: Int,
    val wins: Int,
    val winrate: Float,
    val avgDurationMs: Long,
    val favoriteMode: String?,
    val mostFrequentLoss: String?,
    val pendingSurveys: Int,
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
)

/**
 * Win-rate breakdown row for one game mode (Phase 3, 2026-07 stats expansion). Presentation
 * wrapper over [com.mmg.manahub.feature.game.domain.model.ModeWinrate] that exposes a computed
 * [winrate] fraction.
 */
@Immutable
data class ModeWinrateItem(
    val mode: String,
    val totalGames: Int,
    val wins: Int,
) {
    /** Fraction in [0f, 1f]; 0f when [totalGames] == 0. */
    val winrate: Float get() = if (totalGames > 0) wins.toFloat() / totalGames else 0f
}

/**
 * Win-rate breakdown row for one player-count bucket — 2, 3, or 4 (4 represents "4+")
 * (Phase 3, 2026-07 stats expansion).
 */
@Immutable
data class PlayerCountWinrateItem(
    /** 2, 3, or 4; 4 represents the "4+" bucket. */
    val playerCount: Int,
    val totalGames: Int,
    val wins: Int,
) {
    /** Fraction in [0f, 1f]; 0f when [totalGames] == 0. */
    val winrate: Float get() = if (totalGames > 0) wins.toFloat() / totalGames else 0f
    val isFourPlus: Boolean get() = playerCount >= 4
}

/**
 * One entry in the "recent form" strip — up to the last 10 local-seat games, ordered
 * chronologically (most-recent LAST). Keyed by [sessionId] for stable Compose keys
 * (Phase 3, 2026-07 stats expansion).
 */
@Immutable
data class RecentFormEntry(
    val sessionId: Long,
    val isWin: Boolean,
)

/**
 * Loading/content/error state for the lazily-fetched TRADES tab (Phase 4, 2026-07 stats
 * expansion). Unlike every other Stats source, trade stats require a real network round-trip
 * (see [com.mmg.manahub.core.data.usecase.stats.GetTradeStatsUseCase]) triggered on tab
 * activation, not a cheap local Room read — hence the explicit state machine rather than a
 * plain nullable field.
 */
@Immutable
sealed class TradeStatsUiState {
    /** Not yet fetched — the TRADES tab has never been opened this session. */
    data object Idle : TradeStatsUiState()
    data object Loading : TradeStatsUiState()
    data class Content(val stats: TradeStats) : TradeStatsUiState()
    /** No embedded message — the ViewModel cannot call `stringResource()` (mirrors [StatsUiState.deleteSessionSuccess]'s Boolean-not-String pattern). */
    data object Error : TradeStatsUiState()
}

/**
 * Single row in the session history list.
 *
 * @param isWin True when the local seat (`player_sessions.is_local = 1`) won this session —
 *   derived from [com.mmg.manahub.feature.game.domain.model.SessionHistoryEntry.localIsWinner]
 *   (ADR-001), never from a `winnerName` string match (see memory `feedback_survey_winloss_isLocal`).
 * @param deckName Resolved deck name from [deckId]; null when no deck associated.
 */
@Immutable
data class GameHistoryItem(
    val sessionId: Long,
    val playedAt: Long,
    val mode: String,
    val durationMs: Long,
    val winnerName: String,
    val isWin: Boolean,
    val surveyStatus: SurveyStatus,
    val deckId: String?,
    val deckName: String?,
)

/**
 * Per-deck win/loss summary for the deck performance section.
 *
 * @param winrate Fraction in [0f, 1f].
 */
@Immutable
data class DeckPerformance(
    val deckId: String,
    val deckName: String,
    val totalGames: Int,
    val wins: Int,
    val winrate: Float,
)

/**
 * One set's collection-completion ratio, joining a distinct-owned-card count (Room) against the
 * set's Scryfall card_count. Presentation-only — computed in the ViewModel, not part of
 * [CollectionStats], since it requires Scryfall set metadata the (Room-only) StatsRepository does
 * not have. Global/unfiltered — ignores the active color/set filters (Phase 2 spec).
 */
@Immutable
data class SetCompletion(
    val set: MagicSet,
    val ownedCount: Int,
) {
    /** Fraction [0f, 1f]; 0f when the set's card_count is unknown/zero. */
    val completionRatio: Float
        get() = if (set.cardCount > 0) (ownedCount.toFloat() / set.cardCount).coerceIn(0f, 1f) else 0f
}

/** The stats UI state. [Stable] because all field changes go through [copy] and StateFlow. */
@Stable
data class StatsUiState(
    // ── Collection tab ────────────────────────────────────────────────────────
    val selectedColor:       MtgColor?        = null,
    val selectedSet:         MagicSet?        = null,
    val stats:               CollectionStats? = null,
    val availableSets:       List<MagicSet>   = emptyList(),
    val currency:            PreferredCurrency = if (PriceFormatter.isEuropeanLocale()) PreferredCurrency.EUR else PreferredCurrency.USD,
    val isLoading:           Boolean          = true,
    val error:               String?          = null,
    val isRefreshingPrices:  Boolean          = false,
    val refreshProgress:     Pair<Int, Int>?  = null,
    val lastRefreshedAt:     Long?            = null,
    val refreshError:        String?          = null,
    val refreshResult:       String?          = null,

    // ── Collection tab — Phase 2 additions (2026-07 stats expansion) ───────────
    /** Top sets by completion ratio (owned-distinct / card_count), global/unfiltered, capped. */
    val setCompletions:      List<SetCompletion>    = emptyList(),

    // ── Tab selection ─────────────────────────────────────────────────────────
    val selectedTab:         StatsTab         = StatsTab.COLLECTION,

    // ── Games tab ─────────────────────────────────────────────────────────────
    /** True when at least one game session exists; controls whether tabs are shown. */
    val hasGameStats:        Boolean          = false,
    val gameStats:           GameStats?       = null,
    val sessionHistory:      List<GameHistoryItem>   = emptyList(),
    val deckPerformance:     List<DeckPerformance>   = emptyList(),
    /** Matchup win-rate grouped by opponent archetype; empty until games are classified. */
    val archetypeMatchups:   List<ArchetypeMatchupData> = emptyList(),
    /**
     * One-shot delete-session outcome: `true` = success toast, `false` = error toast, `null` =
     * no pending event. Shown ONLY after the Room delete actually completes (Phase 1 audit fix —
     * previously the success toast fired optimistically at confirm-click, before the delete ran).
     */
    val deleteSessionSuccess: Boolean? = null,

    // ── Games tab — Phase 3 additions (2026-07 stats expansion) ────────────────
    /** Win-rate breakdown by game mode, sorted by games-played descending; every recorded game. */
    val modeWinrates:        List<ModeWinrateItem>       = emptyList(),
    /** Win-rate breakdown by player-count bucket (2 / 3 / 4+); only buckets with ≥1 game. */
    val playerCountWinrates: List<PlayerCountWinrateItem> = emptyList(),
    /** Up to the last 10 local-seat games, chronological (most-recent last). */
    val recentForm:          List<RecentFormEntry>       = emptyList(),

    // ── Trades tab — Phase 4 additions (2026-07 stats expansion) ───────────────
    /**
     * True when the local (non-anonymous) user has at least one COMPLETED trade proposal —
     * controls whether the TRADES tab is shown at all, mirroring the GAMES tab's
     * `hasGameStats`/zero-session gating. Derived from a lightweight, metadata-only
     * [com.mmg.manahub.core.data.repository.TradesRepository.refreshProposals] warm-up at
     * ViewModel init, NOT the heavier per-thread item fetch (that stays lazy — see [tradeStats]).
     */
    val hasTradeStats:       Boolean          = false,
    /** Lazily fetched on first TRADES-tab activation; see [TradeStatsUiState]. */
    val tradeStats:          TradeStatsUiState = TradeStatsUiState.Idle,
)
