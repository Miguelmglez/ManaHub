package com.mmg.manahub.feature.stats.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.manahub.core.data.local.entity.SurveyStatus
import com.mmg.manahub.core.data.remote.ScryfallRemoteDataSource
import com.mmg.manahub.core.data.repository.TradesRepository
import com.mmg.manahub.core.data.usecase.stats.GetTradeStatsUseCase
import com.mmg.manahub.core.domain.auth.AuthRepository
import com.mmg.manahub.core.domain.auth.SessionState
import com.mmg.manahub.core.model.MagicSet
import com.mmg.manahub.core.model.MtgColor
import com.mmg.manahub.core.model.TradeStatus
import com.mmg.manahub.core.domain.repository.DeckRepository
import com.mmg.manahub.core.util.recordSafeNonFatal
import com.mmg.manahub.feature.game.domain.model.ArchetypeMatchupData
import com.mmg.manahub.feature.game.domain.model.DeckStats
import com.mmg.manahub.feature.game.domain.model.EliminationStats
import com.mmg.manahub.feature.game.domain.model.GameModeCount
import com.mmg.manahub.feature.game.domain.model.ModeWinrate
import com.mmg.manahub.feature.game.domain.model.PlayerCountWinrate
import com.mmg.manahub.feature.game.domain.model.SessionHistoryEntry
import com.mmg.manahub.feature.game.domain.repository.GameSessionRepository
import com.mmg.manahub.core.domain.repository.UserPreferencesRepository
import com.mmg.manahub.core.data.usecase.collection.RefreshCollectionPricesUseCase
import com.mmg.manahub.core.domain.usecase.stats.GetCollectionSetCodesUseCase
import com.mmg.manahub.core.domain.usecase.stats.GetCollectionStatsUseCase
import com.mmg.manahub.core.domain.usecase.stats.GetSetCompletionCountsUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for the Stats screen.
 *
 * KMP migration — Phase 1 Hilt→Koin cutover: Stats is the second "Koin island". This ViewModel is no
 * longer `@HiltViewModel`; it is constructed by the `viewModel { }` factory in `statsKoinModule` and
 * resolved at the call site via `koinViewModel()`. Its dependencies are still Hilt-owned singletons,
 * bridged into Koin by `ManaHubApp` (see `statsKoinModule` / `coreBridgeKoinModule`).
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class StatsViewModel(
    private val getStats:                 GetCollectionStatsUseCase,
    private val getSetCodes:              GetCollectionSetCodesUseCase,
    private val getSetCompletionCounts:   GetSetCompletionCountsUseCase,
    private val scryfallDataSource:       ScryfallRemoteDataSource,
    private val refreshPricesUseCase:     RefreshCollectionPricesUseCase,
    private val userPreferencesDataStore: UserPreferencesRepository,
    private val gameSessionRepository:    GameSessionRepository,
    private val deckRepository:           DeckRepository,
    private val authRepository:           AuthRepository,
    private val tradesRepository:         TradesRepository,
    private val getTradeStats:            GetTradeStatsUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    private companion object {
        /** "Recent form" strip shows up to the last 10 games (Phase 3, 2026-07 stats expansion). */
        const val RECENT_FORM_LIMIT = 10
    }

    init {
        observeCollectionStats()
        observeAvailableSetsAndCompletion()
        observePreferredCurrency()
        observeLastPriceRefresh()
        observeGameStats()
        observeTradeTabVisibility()
    }

    // ── Collection tab observers ──────────────────────────────────────────────

    private fun observeCollectionStats() {
        viewModelScope.launch {
            combine(
                userPreferencesDataStore.preferredCurrencyFlow,
                _uiState.map { it.selectedColor }.distinctUntilChanged(),
                _uiState.map { it.selectedSet?.code }.distinctUntilChanged(),
            ) { currency, color, setCode -> Triple(currency, color, setCode) }
            .flatMapLatest { (currency, color, setCode) ->
                getStats(currency, color, setCode)
                    .debounce(300)
                    .catch { e ->
                        recordSafeNonFatal("stats_collection_pipeline", e)
                        _uiState.update { it.copy(error = e.message, isLoading = false) }
                    }
            }
            .collect { stats ->
                _uiState.update { it.copy(stats = stats, isLoading = false) }
            }
        }
    }

    /** Fetches every Scryfall set once; on failure degrades to an empty list rather than crashing. */
    private fun allSetsFlow(): Flow<List<MagicSet>> = flow { emit(scryfallDataSource.getAllSets()) }
        .catch { e ->
            recordSafeNonFatal("stats_available_sets_fetch", e)
            emit(emptyList())
        }

    /**
     * Wires the set filter's [StatsUiState.availableSets] AND Phase 2's "Set completion" section
     * off the SAME Scryfall sets fetch, so the network call is made once, not twice. Both are
     * global/unfiltered — set completion ignores the active color/set filter (Phase 2 spec).
     */
    private fun observeAvailableSetsAndCompletion() {
        viewModelScope.launch {
            combine(
                getSetCodes(),
                allSetsFlow(),
                getSetCompletionCounts(),
            ) { codes, allSets, ownedCounts ->
                val available = if (codes.isEmpty()) emptyList() else allSets.filter { it.code in codes }
                val completions = available
                    .mapNotNull { set -> ownedCounts[set.code]?.let { owned -> SetCompletion(set, owned) } }
                    .filter { it.set.cardCount > 0 }
                    .sortedByDescending { it.completionRatio }
                    .take(5)
                available to completions
            }.collect { (available, completions) ->
                _uiState.update { it.copy(availableSets = available, setCompletions = completions) }
            }
        }
    }

    private fun observePreferredCurrency() {
        viewModelScope.launch {
            var isFirstEmission = true
            userPreferencesDataStore.preferredCurrencyFlow.collect { currency ->
                _uiState.update { it.copy(currency = currency) }
                // Trade net-value is currency-denominated. A currency change AFTER the trade
                // stats were already fetched (skip the initial emission) must not silently keep
                // showing a figure in the old currency: re-fetch immediately if the TRADES tab is
                // the one currently visible, otherwise just invalidate so the next activation
                // re-fetches (mirrors the lazy-fetch-on-activation contract).
                if (!isFirstEmission && _uiState.value.tradeStats !is TradeStatsUiState.Idle) {
                    if (_uiState.value.selectedTab == StatsTab.TRADES) {
                        loadTradeStats()
                    } else {
                        _uiState.update { it.copy(tradeStats = TradeStatsUiState.Idle) }
                    }
                }
                isFirstEmission = false
            }
        }
    }

    private fun observeLastPriceRefresh() {
        viewModelScope.launch {
            userPreferencesDataStore.lastPriceRefreshFlow.collect { lastRefresh ->
                _uiState.update { it.copy(lastRefreshedAt = lastRefresh) }
            }
        }
    }

    // ── Games tab observer ────────────────────────────────────────────────────

    private fun observeGameStats() {
        viewModelScope.launch {
            // Win/loss, history, and per-deck stats are resolved against the local seat
            // (is_local = 1), not a playerName match (see ADR-001). The stored seat name
            // can diverge from the current UserPreferences name (default "Wizard"), which
            // would silently zero out the win-rate and W/L badges.
            combine(
                gameSessionRepository.observeTotalGames().distinctUntilChanged(),
                gameSessionRepository.observeLocalWins().distinctUntilChanged(),
                gameSessionRepository.observeAvgDurationMs().distinctUntilChanged(),
                gameSessionRepository.observeFavoriteMode().distinctUntilChanged(),
                gameSessionRepository.observeMostFrequentElimination().distinctUntilChanged(),
                gameSessionRepository.observePendingSurveyCount().distinctUntilChanged(),
                gameSessionRepository.observeLocalSessionHistory().distinctUntilChanged(),
                gameSessionRepository.observeLocalDeckGameStats().distinctUntilChanged(),
                gameSessionRepository.observeArchetypeMatchups().distinctUntilChanged(),
                deckRepository.observeAllDecks().distinctUntilChanged(),
                gameSessionRepository.observeWinrateByMode().distinctUntilChanged(),
                gameSessionRepository.observeWinrateByPlayerCount().distinctUntilChanged(),
            ) { args ->
                // combine with 12 flows uses the array variant
                @Suppress("UNCHECKED_CAST")
                val totalGames   = args[0] as Int
                val wins         = args[1] as Int
                val avgDuration  = args[2] as Double?
                val favoriteMode = args[3] as GameModeCount?
                val mostLoss     = args[4] as EliminationStats?
                val pending      = args[5] as Int
                val history      = args[6] as List<SessionHistoryEntry>
                val deckStats    = args[7] as List<DeckStats>
                val matchups     = args[8] as List<ArchetypeMatchupData>
                val allDecks     = args[9] as List<com.mmg.manahub.core.model.Deck>
                val modeWinrates = args[10] as List<ModeWinrate>
                val playerCountWinrates = args[11] as List<PlayerCountWinrate>

                val deckNameById = allDecks.associate { it.id to it.name }

                val gameStats = GameStats(
                    totalGames       = totalGames,
                    wins             = wins,
                    winrate          = if (totalGames > 0) wins.toFloat() / totalGames else 0f,
                    avgDurationMs    = avgDuration?.toLong() ?: 0L,
                    favoriteMode     = favoriteMode?.mode,
                    mostFrequentLoss = mostLoss?.eliminationReason,
                    pendingSurveys   = pending,
                    currentStreak    = computeCurrentStreak(history),
                    bestStreak       = computeBestStreak(history),
                )

                val historyItems = history.map { row ->
                    GameHistoryItem(
                        sessionId     = row.sessionId,
                        playedAt      = row.playedAt,
                        mode          = row.mode,
                        durationMs    = row.durationMs,
                        winnerName    = row.winnerName,
                        isWin         = row.localIsWinner,
                        surveyStatus  = runCatching { SurveyStatus.valueOf(row.surveyStatus) }.getOrDefault(SurveyStatus.PENDING),
                        deckId        = row.localDeckId,
                        deckName      = row.localDeckId?.let { deckNameById[it] },
                    )
                }

                val deckPerf = deckStats.mapNotNull { row ->
                    val deckId = row.deckId ?: return@mapNotNull null
                    val name = deckNameById[deckId] ?: return@mapNotNull null
                    if (row.totalGames == 0) return@mapNotNull null
                    DeckPerformance(
                        deckId     = deckId,
                        deckName   = name,
                        totalGames = row.totalGames,
                        wins       = row.wins,
                        winrate    = if (row.totalGames > 0) row.wins.toFloat() / row.totalGames else 0f,
                    )
                }.sortedByDescending { it.totalGames }

                val modeWinrateItems = modeWinrates
                    .map { ModeWinrateItem(mode = it.mode, totalGames = it.totalGames, wins = it.wins) }
                    .sortedByDescending { it.totalGames }

                // Bucket the exact persisted player count into 2 / 3 / 4+ (spec: "winrate for 2 / 3 /
                // 4+ player games"). coerceAtLeast(2) folds any theoretical <2-player row into the
                // "2" bucket defensively — the app never records a session with fewer than 2 seats,
                // so this never fires in practice.
                val playerCountItems = playerCountWinrates
                    .groupBy { row -> row.playerCount.coerceAtLeast(2).coerceAtMost(4) }
                    .toSortedMap()
                    .map { (bucket, rows) ->
                        PlayerCountWinrateItem(
                            playerCount = bucket,
                            totalGames  = rows.sumOf { it.totalGames },
                            wins        = rows.sumOf { it.wins },
                        )
                    }

                // history is DESC (most-recent first); take the newest RECENT_FORM_LIMIT and
                // reverse so the strip renders chronologically with the most-recent game last.
                val recentForm = history.take(RECENT_FORM_LIMIT)
                    .reversed()
                    .map { RecentFormEntry(sessionId = it.sessionId, isWin = it.localIsWinner) }

                GameStatsResult(gameStats, historyItems, deckPerf, matchups, modeWinrateItems, playerCountItems, recentForm)
            }
            .catch { e -> recordSafeNonFatal("stats_game_pipeline", e) }
            .collect { result ->
                _uiState.update {
                    it.copy(
                        hasGameStats        = result.gameStats.totalGames > 0,
                        gameStats           = result.gameStats,
                        sessionHistory      = result.history,
                        deckPerformance     = result.deckPerformance,
                        archetypeMatchups   = result.matchups,
                        modeWinrates        = result.modeWinrates,
                        playerCountWinrates = result.playerCountWinrates,
                        recentForm          = result.recentForm,
                    )
                }
            }
        }
    }

    /** Internal carrier for the game-stats pipeline output (combine emits a single object). */
    private data class GameStatsResult(
        val gameStats: GameStats,
        val history: List<GameHistoryItem>,
        val deckPerformance: List<DeckPerformance>,
        val matchups: List<ArchetypeMatchupData>,
        val modeWinrates: List<ModeWinrateItem>,
        val playerCountWinrates: List<PlayerCountWinrateItem>,
        val recentForm: List<RecentFormEntry>,
    )

    /**
     * Consecutive wins ending at the most recent game (front of [historyDesc], which is ordered
     * most-recent-first). 0 if the most recent game was a loss or there is no history.
     */
    private fun computeCurrentStreak(historyDesc: List<SessionHistoryEntry>): Int {
        var streak = 0
        for (row in historyDesc) {
            if (row.localIsWinner) streak++ else break
        }
        return streak
    }

    /**
     * Longest run of consecutive wins anywhere in [history]. Order doesn't affect the result (a
     * contiguous run is a contiguous run regardless of scan direction), so this works the same
     * whether [history] is ascending or descending.
     */
    private fun computeBestStreak(history: List<SessionHistoryEntry>): Int {
        var best = 0
        var running = 0
        for (row in history) {
            if (row.localIsWinner) {
                running++
                best = maxOf(best, running)
            } else {
                running = 0
            }
        }
        return best
    }

    // ── Trades tab observer (Phase 4, 2026-07 stats expansion) ───────────────

    /**
     * Determines Games-tab-style TRADES tab visibility via a lightweight, metadata-only
     * [TradesRepository.refreshProposals] warm-up (mirrors `HomeViewModel`'s own trades warm-up,
     * see memory `feedback_home_dashboard_audit_fixes_2026-07-13`), THEN derives
     * [StatsUiState.hasTradeStats] reactively from the resulting in-memory cache.
     *
     * This is a ONE-SHOT resolution of the auth session (`.first { it !is Loading }`), matching
     * `HomeViewModel`'s established pattern — a sign-in/sign-out that happens LATER in the same
     * Stats session is not picked up. Deliberately does NOT trigger the expensive per-thread item
     * fetch (that stays fully lazy — see [loadTradeStats]), only the cheap metadata call needed to
     * know whether any COMPLETED proposal exists at all.
     */
    private fun observeTradeTabVisibility() {
        viewModelScope.launch {
            val session = authRepository.sessionState.first { it !is SessionState.Loading }
            val userId = (session as? SessionState.Authenticated)?.takeIf { !it.user.isAnonymous }?.user?.id
                ?: return@launch
            runCatching { tradesRepository.refreshProposals(userId) }
                .onFailure { e -> recordSafeNonFatal("stats_trades_visibility_refresh", e) }
            tradesRepository.observeAllProposals()
                .map { proposals -> proposals.any { it.status == TradeStatus.COMPLETED } }
                .distinctUntilChanged()
                .collect { hasCompleted -> _uiState.update { it.copy(hasTradeStats = hasCompleted) } }
        }
    }

    /** Resolves the current non-anonymous authenticated user id, or null if not eligible. */
    private fun currentAuthenticatedUserId(): String? =
        (authRepository.sessionState.value as? SessionState.Authenticated)
            ?.takeIf { !it.user.isAnonymous }
            ?.user?.id

    /**
     * The heavy fetch: fans out [com.mmg.manahub.core.data.repository.TradesRepository
     * .refreshProposalThread] over every COMPLETED thread (via [GetTradeStatsUseCase]), never
     * capped to a small N (Phase 0 feasibility caveat — a "newest N" cap would silently undercount
     * a stats screen, unlike Home's bounded dashboard-summary hydration). Triggered lazily, only
     * on TRADES-tab activation (see [onTabSelected]) or an explicit [retryTradeStats].
     */
    private fun loadTradeStats() {
        val userId = currentAuthenticatedUserId()
        if (userId == null) {
            _uiState.update { it.copy(tradeStats = TradeStatsUiState.Error) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(tradeStats = TradeStatsUiState.Loading) }
            getTradeStats(userId, _uiState.value.currency)
                .onSuccess { stats -> _uiState.update { it.copy(tradeStats = TradeStatsUiState.Content(stats)) } }
                .onFailure { e ->
                    recordSafeNonFatal("stats_trade_pipeline", e)
                    _uiState.update { it.copy(tradeStats = TradeStatsUiState.Error) }
                }
        }
    }

    /** Retries the TRADES tab fetch after an [TradeStatsUiState.Error]. */
    fun retryTradeStats() = loadTradeStats()

    // ── Public actions ────────────────────────────────────────────────────────

    fun onColorSelected(color: MtgColor?) {
        _uiState.update { it.copy(selectedColor = if (_uiState.value.selectedColor == color) null else color) }
    }

    fun onSetSelected(set: MagicSet?) {
        _uiState.update { it.copy(selectedSet = set) }
    }

    fun onCurrencyToggle() {
        viewModelScope.launch {
            val next = if (_uiState.value.currency == com.mmg.manahub.core.model.PreferredCurrency.USD)
                com.mmg.manahub.core.model.PreferredCurrency.EUR
            else
                com.mmg.manahub.core.model.PreferredCurrency.USD
            userPreferencesDataStore.setPreferredCurrency(next)
        }
    }

    fun onErrorDismissed() = _uiState.update { it.copy(error = null) }

    /**
     * Switches the visible tab. Activating TRADES for the first time (or after an error/currency
     * invalidation) lazily triggers [loadTradeStats] — the expensive per-thread item fetch never
     * runs from the shared `combine` pipeline (Phase 0 feasibility caveat).
     */
    fun onTabSelected(tab: StatsTab) {
        _uiState.update { it.copy(selectedTab = tab) }
        if (tab == StatsTab.TRADES && _uiState.value.tradeStats == TradeStatsUiState.Idle) {
            loadTradeStats()
        }
    }

    /**
     * Permanently removes a game session (and its linked survey via FK cascade). The success
     * toast is shown ONLY after the Room delete actually succeeds — [GameSessionRepository
     * .deleteSession] never throws (its Android impl wraps the DAO call in `runCatching` and
     * reports failures via Crashlytics), so a `false` result here is a real, surfaced failure.
     */
    fun deleteSession(sessionId: Long) {
        viewModelScope.launch {
            val success = gameSessionRepository.deleteSession(sessionId)
            _uiState.update { it.copy(deleteSessionSuccess = success) }
        }
    }

    /** Clears the one-shot delete-session toast state after it has been shown. */
    fun clearDeleteSessionMessage() = _uiState.update { it.copy(deleteSessionSuccess = null) }

    fun refreshPrices() {
        if (_uiState.value.isRefreshingPrices) return
        viewModelScope.launch {
            refreshPricesUseCase.invoke().collect { result ->
                when (result) {
                    is RefreshCollectionPricesUseCase.Result.Progress -> {
                        _uiState.update { it.copy(
                            isRefreshingPrices = true,
                            refreshProgress    = result.current to result.total,
                        )}
                    }
                    is RefreshCollectionPricesUseCase.Result.Success -> {
                        val now = System.currentTimeMillis()
                        userPreferencesDataStore.saveLastPriceRefresh(now)
                        val message = buildString {
                            append("Updated ${result.updatedCount} prices")
                            if (result.notFoundCount > 0)
                                append(" (${result.notFoundCount} not found)")
                        }
                        _uiState.update { it.copy(
                            isRefreshingPrices = false,
                            refreshProgress    = null,
                            lastRefreshedAt    = now,
                            refreshResult      = message,
                        )}
                    }
                    is RefreshCollectionPricesUseCase.Result.Error -> {
                        _uiState.update { it.copy(
                            isRefreshingPrices = false,
                            refreshProgress    = null,
                            refreshError       = result.message,
                        )}
                    }
                }
            }
        }
    }

    fun clearRefreshMessage() {
        _uiState.update { it.copy(refreshResult = null, refreshError = null) }
    }
}
