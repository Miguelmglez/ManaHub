package com.mmg.manahub.feature.stats.presentation

import com.mmg.manahub.core.data.local.entity.SurveyStatus
import com.mmg.manahub.core.domain.model.CollectionStats
import com.mmg.manahub.core.domain.model.MagicSet
import com.mmg.manahub.core.domain.model.MtgColor
import com.mmg.manahub.core.domain.model.PreferredCurrency
import com.mmg.manahub.core.util.PriceFormatter

/** Selectable top-level tabs on the Stats screen. */
enum class StatsTab { COLLECTION, GAMES }

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
 */
data class GameStats(
    val totalGames: Int,
    val wins: Int,
    val winrate: Float,
    val avgDurationMs: Long,
    val favoriteMode: String?,
    val mostFrequentLoss: String?,
    val pendingSurveys: Int,
)

/**
 * Single row in the session history list.
 *
 * @param isWin True when [winnerName] matches the app user's player name.
 * @param deckName Resolved deck name from [deckId]; null when no deck associated.
 */
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
data class DeckPerformance(
    val deckId: String,
    val deckName: String,
    val totalGames: Int,
    val wins: Int,
    val winrate: Float,
)

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

    // ── Tab selection ─────────────────────────────────────────────────────────
    val selectedTab:         StatsTab         = StatsTab.COLLECTION,

    // ── Games tab ─────────────────────────────────────────────────────────────
    /** True when at least one game session exists; controls whether tabs are shown. */
    val hasGameStats:        Boolean          = false,
    val gameStats:           GameStats?       = null,
    val sessionHistory:      List<GameHistoryItem>   = emptyList(),
    val deckPerformance:     List<DeckPerformance>   = emptyList(),
)
