package com.mmg.manahub.feature.stats.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.manahub.core.data.local.entity.SurveyStatus
import com.mmg.manahub.core.data.remote.ScryfallRemoteDataSource
import com.mmg.manahub.core.data.repository.TradesRepository
import com.mmg.manahub.core.data.usecase.stats.GetTradeStatsUseCase
import com.mmg.manahub.core.domain.auth.AuthRepository
import com.mmg.manahub.core.domain.auth.SessionState
import com.mmg.manahub.core.model.CollectionStats
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
import com.mmg.manahub.core.domain.usecase.stats.GetCollectionSetCodesUseCase
import com.mmg.manahub.core.domain.usecase.stats.GetCollectionStatsUseCase
import com.mmg.manahub.core.domain.usecase.stats.GetSetCompletionCountsUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException

/** Collection tab's "available sets + set completion" pipeline result (see [StatsViewModel]). */
private typealias CollectionSetsResult = Pair<List<MagicSet>, List<SetCompletion>>

/**
 * ViewModel for the Stats screen.
 *
 * KMP migration — Phase 1 Hilt→Koin cutover: Stats is the second "Koin island". This ViewModel is no
 * longer `@HiltViewModel`; it is constructed by the `viewModel { }` factory in `statsKoinModule` and
 * resolved at the call site via `koinViewModel()`. Its dependencies are still Hilt-owned singletons,
 * bridged into Koin by `ManaHubApp` (see `statsKoinModule` / `coreBridgeKoinModule`).
 *
 * ## Tab-scoped subscriptions (2026-07-28 backend perf plan, WS5a)
 * The Games tab's 12-flow `combine` ([gameStatsPipeline]), the Collection tab's stats query
 * ([collectionStatsPipeline]), and the Collection tab's "available sets + set completion" query
 * ([collectionSetsPipeline]) are all gated behind [_selectedTab] via `flatMapLatest` — switching
 * tabs cancels the previously-active tab's Room subscription instead of running all three
 * concurrently for the ViewModel's whole lifetime. Each pipeline is exposed as a
 * `stateIn(WhileSubscribed(5_000))` [StateFlow] (mirrors `HomeViewModel`'s established pattern)
 * rather than a bare cold [Flow] collected via a permanently-running `launch { collect }`.
 *
 * Two signals are DELIBERATELY kept always-on/cheap rather than tab-scoped: [hasGameStatsFlow] and
 * [hasTradeStatsFlow]. Both control whether their tab even APPEARS in the tab row
 * ([StatsUiState.hasGameStats] / [StatsUiState.hasTradeStats]) — gating them behind "the tab is
 * already selected" would be a chicken-and-egg bug (the tab could never be selected, because it
 * would never appear in the row in the first place). Each is a single cheap query (a Room count /
 * a metadata-only proposal check), not the expensive per-tab pipeline, so leaving them always-on
 * does not reintroduce the problem this workstream fixes. [allSetsSharedFlow] is similarly kept
 * alive for the ViewModel's whole life (`SharingStarted.Lazily`, not tab-scoped) because it is a
 * ONE-TIME Scryfall network fetch — tab-scoping it would silently turn a single fetch into a
 * repeated network call every time the user revisits the Collection tab.
 *
 * Price refresh (2026-07-28 backend perf plan, WS1+WS3 item 7b — user decision: prices refresh
 * automatically once a day via `PriceRefreshWorker`, the user is never asked). Stats only ever
 * DISPLAYS the last-refreshed timestamp (read-only, see [observeLastPriceRefresh] /
 * [StatsUiState.lastRefreshedAt]) — it never triggers a refresh itself. The former manual
 * `refreshPrices()` entry point and its `isRefreshingPrices`/`refreshProgress`/`refreshResult`/
 * `refreshError` UI state were removed entirely, along with the [RefreshCollectionPricesUseCase]
 * [com.mmg.manahub.core.data.usecase.collection.RefreshCollectionPricesUseCase] dependency.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class StatsViewModel(
    private val getStats:                 GetCollectionStatsUseCase,
    private val getSetCodes:              GetCollectionSetCodesUseCase,
    private val getSetCompletionCounts:   GetSetCompletionCountsUseCase,
    private val scryfallDataSource:       ScryfallRemoteDataSource,
    private val userPreferencesDataStore: UserPreferencesRepository,
    private val gameSessionRepository:    GameSessionRepository,
    private val deckRepository:           DeckRepository,
    private val authRepository:           AuthRepository,
    private val tradesRepository:         TradesRepository,
    private val getTradeStats:            GetTradeStatsUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatsUiState())
    private var tradeJob: Job? = null
    private var lastTradeVisibilityRefreshAt = 0L
    private var tradeOwnerUserId: String? = null

    private companion object {
        /** "Recent form" strip shows up to the last 10 games (Phase 3, 2026-07 stats expansion). */
        const val RECENT_FORM_LIMIT = 10
    }

    /**
     * Single source of truth for the active tab. Every tab-scoped pipeline below `flatMapLatest`s
     * off this flow (see class doc). [onTabSelected] is the only writer.
     */
    private val _selectedTab = MutableStateFlow(StatsTab.COLLECTION)
    private val collectionRetry = MutableStateFlow(0)
    private val gameRetry = MutableStateFlow(0)

    // ── Collection tab pipelines (tab-scoped to StatsTab.COLLECTION) ───────────

    /**
     * The Collection tab's stats query. `null` while off-tab or before the first value has
     * arrived — the bridging collector in [init] ignores `null` so switching tabs away and back
     * never wipes out the last-displayed [StatsUiState.stats] (Room resubscribes fast; no flicker).
     */
    private val collectionStatsPipeline: Flow<Pair<com.mmg.manahub.core.model.PreferredCurrency, CollectionStats>?> =
        _selectedTab.flatMapLatest { tab ->
            if (tab != StatsTab.COLLECTION) return@flatMapLatest flowOf<Pair<com.mmg.manahub.core.model.PreferredCurrency, CollectionStats>?>(null)
            combine(
                userPreferencesDataStore.preferredCurrencyFlow,
                _uiState.map { it.selectedColor }.distinctUntilChanged(),
                _uiState.map { it.selectedSet?.code }.distinctUntilChanged(),
            ) { currency, color, setCode -> Triple(currency, color, setCode) }
                .combine(collectionRetry) { filters, _ -> filters }
                .flatMapLatest { (currency, color, setCode) ->
                    getStats(currency, color, setCode)
                        .debounce(300)
                        .map { currency to it }
                        .catch { e ->
                            recordSafeNonFatal("stats_collection_pipeline", e)
                            _uiState.update { it.copy(error = e.message, isLoading = false) }
                        }
                }
        }.onStart { emit(null) }

    /**
     * Fetches every Scryfall set ONCE for the ViewModel's whole life (`SharingStarted.Lazily`,
     * deliberately NOT tab-scoped — see class doc: re-tab-scoping this would turn one network
     * fetch into a repeated one every time the user revisits the Collection tab). On failure
     * degrades to an empty list rather than crashing.
     */
    private val allSetsSharedFlow: StateFlow<List<MagicSet>> =
        flow { emit(scryfallDataSource.getAllSets()) }
            .catch { e ->
                recordSafeNonFatal("stats_available_sets_fetch", e)
                emit(emptyList())
            }
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    /**
     * Wires the set filter's [StatsUiState.availableSets] AND Phase 2's "Set completion" section
     * off the SAME cached [allSetsSharedFlow], so the network call is made once, not twice. Both
     * are global/unfiltered — set completion ignores the active color/set filter (Phase 2 spec).
     * The two Room queries here ([getSetCodes], [getSetCompletionCounts]) ARE tab-scoped to
     * [StatsTab.COLLECTION] — only the Scryfall fetch itself is shared/always-on.
     */
    private val collectionSetsPipeline: Flow<CollectionSetsResult?> =
        _selectedTab.flatMapLatest { tab ->
            if (tab != StatsTab.COLLECTION) return@flatMapLatest flowOf<CollectionSetsResult?>(null)
            combine(
                getSetCodes(),
                allSetsSharedFlow,
                getSetCompletionCounts(),
            ) { codes, allSets, ownedCounts ->
                val codeSet = codes.toSet()
                val available = if (codeSet.isEmpty()) emptyList() else allSets.filter { it.code in codeSet }
                val completions = available
                    .mapNotNull { set -> ownedCounts[set.code]?.let { owned -> SetCompletion(set, owned) } }
                    .filter { it.set.cardCount > 0 }
                    .sortedByDescending { it.completionRatio }
                    .take(5)
                available to completions
            }
        }.onStart { emit(null) }

    private fun observePreferredCurrency() {
        viewModelScope.launch {
            var isFirstEmission = true
            userPreferencesDataStore.preferredCurrencyFlow.collect { currency ->
                _uiState.update { it.copy(currency = currency) }
                if (!isFirstEmission) {
                    val tradeState = _uiState.value.tradeStats
                    val cached = (tradeState as? TradeStatsUiState.Content)?.stats
                    val cachedDelta = when (currency) {
                        com.mmg.manahub.core.model.PreferredCurrency.USD -> cached?.netValueDeltaUsd
                        com.mmg.manahub.core.model.PreferredCurrency.EUR -> cached?.netValueDeltaEur
                    }
                    when {
                        cached != null && cachedDelta != null -> _uiState.update {
                            it.copy(tradeStats = TradeStatsUiState.Content(
                                cached.copy(currency = currency, netValueDelta = cachedDelta)
                            ))
                        }
                        tradeState !is TradeStatsUiState.Idle && _uiState.value.selectedTab == StatsTab.TRADES -> loadTradeStats()
                        tradeState !is TradeStatsUiState.Idle -> _uiState.update { it.copy(tradeStats = TradeStatsUiState.Idle) }
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

    // ── Games tab ────────────────────────────────────────────────────────────

    /**
     * Cheap, ALWAYS-ON gate for [StatsUiState.hasGameStats] — a single Room count query, not the
     * heavy 12-flow combine below. Kept off the tab-scoping so the GAMES tab can appear in the tab
     * row before it has ever been selected (see class doc).
     */
    private val hasGameStatsFlow: Flow<Boolean> =
        gameSessionRepository.observeTotalGames()
            .map { it > 0 }
            .distinctUntilChanged()
            .onEach { has ->
                if (!has && _selectedTab.value == StatsTab.GAMES) onTabSelected(StatsTab.COLLECTION)
            }
            .onStart { emit(uiState.value.hasGameStats) }

    /**
     * The Games tab's 12-flow `combine`, gated to [StatsTab.GAMES] via `flatMapLatest` — this is
     * the pipeline named in the backend perf plan (WS5a) as the primary Room/memory offender when
     * left running regardless of the selected tab. `null` while off-tab; the bridging collector in
     * [init] ignores `null`.
     */
    private val gameStatsPipeline: Flow<GameStatsResult?> =
        combine(_selectedTab, gameRetry) { tab, _ -> tab }.flatMapLatest { tab ->
            if (tab != StatsTab.GAMES) return@flatMapLatest flowOf<GameStatsResult?>(null)
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
                gameSessionRepository.observeLocalDraws().distinctUntilChanged(),
                gameSessionRepository.observeLocalSessionOutcomes().distinctUntilChanged(),
            ) { args ->
                // combine with multiple flows uses the array variant
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
                val draws = args[12] as Int
                val outcomes = args[13] as List<Boolean?>

                val deckNameById = allDecks.associate { it.id to it.name }

                val gameStats = GameStats(
                    totalGames       = totalGames,
                    wins             = wins,
                    winrate          = if (totalGames > draws) wins.toFloat() / (totalGames - draws) else 0f,
                    avgDurationMs    = avgDuration?.toLong() ?: 0L,
                    favoriteMode     = favoriteMode?.mode,
                    mostFrequentLoss = mostLoss?.eliminationReason,
                    pendingSurveys   = pending,
                    currentStreak    = computeCurrentStreak(outcomes),
                    bestStreak       = computeBestStreak(outcomes),
                )

                val historyItems = history.map { row ->
                    GameHistoryItem(
                        sessionId     = row.sessionId,
                        playedAt      = row.playedAt,
                        mode          = row.mode,
                        durationMs    = row.durationMs,
                        winnerName    = row.winnerName,
                        isWin         = row.localIsWinner,
                        isDraw        = row.isDraw,
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
                    .map { RecentFormEntry(sessionId = it.sessionId, isWin = it.localIsWinner, isDraw = it.isDraw) }

                GameStatsResult(gameStats, historyItems, deckPerf, matchups, modeWinrateItems, playerCountItems, recentForm)
            }
                .catch { e ->
                    recordSafeNonFatal("stats_game_pipeline", e)
                    _uiState.update { it.copy(gameError = true) }
                }
        }.onStart { emit(null) }

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
    private fun computeCurrentStreak(historyDesc: List<Boolean?>): Int {
        var streak = 0
        for (outcome in historyDesc) {
            if (outcome == false) break
            if (outcome == true) streak++
        }
        return streak
    }

    /**
     * Longest run of consecutive wins anywhere in [history]. Order doesn't affect the result (a
     * contiguous run is a contiguous run regardless of scan direction), so this works the same
     * whether [history] is ascending or descending.
     */
    private fun computeBestStreak(history: List<Boolean?>): Int {
        var best = 0
        var running = 0
        for (outcome in history) {
            if (outcome == true) {
                running++
                best = maxOf(best, running)
            } else if (outcome == false) {
                running = 0
            }
        }
        return best
    }

    // ── Trades tab (Phase 4, 2026-07 stats expansion) ─────────────────────────

    /**
     * Cheap, ALWAYS-ON gate for [StatsUiState.hasTradeStats] — mirrors [hasGameStatsFlow]'s
     * "not tab-scoped" reasoning (see class doc): the TRADES tab must be able to appear before it
     * has ever been selected. Performs a ONE-SHOT resolution of the auth session
     * (`.first { it !is Loading }`), matching `HomeViewModel`'s established pattern — a
     * sign-in/sign-out that happens LATER in the same Stats session is not picked up. Deliberately
     * does NOT trigger the expensive per-thread item fetch (that stays fully lazy — see
     * [loadTradeStats]), only the cheap metadata call needed to know whether any COMPLETED
     * proposal exists at all.
     */
    private val hasTradeStatsFlow: Flow<Boolean> = authRepository.sessionState
        .filter { it !is SessionState.Loading }
        .map { session ->
            (session as? SessionState.Authenticated)?.takeIf { !it.user.isAnonymous }?.user?.id
        }
        .distinctUntilChanged()
        .onEach { userId ->
            if (userId != tradeOwnerUserId) {
                tradeJob?.cancel()
                tradeOwnerUserId = userId
                lastTradeVisibilityRefreshAt = 0L
                _uiState.update { it.copy(tradeStats = TradeStatsUiState.Idle) }
            }
        }
        .flatMapLatest { userId ->
            if (userId == null) return@flatMapLatest flowOf(false)
            flow {
                emit(false)
                val now = System.currentTimeMillis()
                if (now - lastTradeVisibilityRefreshAt >= 5 * 60_000L) {
                    runCatching { tradesRepository.refreshProposals(userId) }
                        .onSuccess { lastTradeVisibilityRefreshAt = now }
                        .onFailure { e ->
                            if (e is CancellationException) throw e
                            recordSafeNonFatal("stats_trades_visibility_refresh", e)
                        }
                }
                emitAll(tradesRepository.observeAllProposals()
                    .map { proposals -> proposals.any { proposal ->
                        proposal.status == TradeStatus.COMPLETED &&
                            (proposal.proposerId == userId || proposal.receiverId == userId)
                    } }
                    .distinctUntilChanged())
            }
        }.onEach { has ->
        if (!has && _selectedTab.value == StatsTab.TRADES) onTabSelected(StatsTab.COLLECTION)
    }.onStart { emit(false) }

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
        tradeJob?.cancel()
        tradeJob = viewModelScope.launch {
            _uiState.update { it.copy(tradeStats = TradeStatsUiState.Loading) }
            val currency = _uiState.value.currency
            getTradeStats(userId, currency)
                .onSuccess { stats ->
                    if (_uiState.value.currency == currency && currentAuthenticatedUserId() == userId) {
                        _uiState.update { it.copy(tradeStats = TradeStatsUiState.Content(stats)) }
                    }
                }
                .onFailure { e ->
                    if (currentAuthenticatedUserId() == userId) {
                        recordSafeNonFatal("stats_trade_pipeline", e)
                        _uiState.update { it.copy(tradeStats = TradeStatsUiState.Error) }
                    }
                }
        }
    }

    /** Retries the TRADES tab fetch after an [TradeStatsUiState.Error]. */
    fun retryTradeStats() = loadTradeStats()

    val uiState: StateFlow<StatsUiState> = combine(
        combine(_uiState, collectionStatsPipeline, collectionSetsPipeline) { base, stats, sets ->
            val currentStats = stats?.takeIf { it.first == base.currency }?.second
            base.copy(
                stats = currentStats,
                error = if (currentStats != null) null else base.error,
                isLoading = currentStats == null && base.error == null,
                availableSets = sets?.first ?: base.availableSets,
                setCompletions = sets?.second ?: base.setCompletions,
            )
        },
        hasGameStatsFlow,
        gameStatsPipeline,
        hasTradeStatsFlow,
    ) { base, hasGames, games, hasTrades ->
        base.copy(
            hasGameStats = hasGames,
            hasTradeStats = hasTrades,
            gameStats = games?.gameStats ?: base.gameStats,
            gameError = if (games != null) false else base.gameError,
            sessionHistory = games?.history ?: base.sessionHistory,
            deckPerformance = games?.deckPerformance ?: base.deckPerformance,
            archetypeMatchups = games?.matchups ?: base.archetypeMatchups,
            modeWinrates = games?.modeWinrates ?: base.modeWinrates,
            playerCountWinrates = games?.playerCountWinrates ?: base.playerCountWinrates,
            recentForm = games?.recentForm ?: base.recentForm,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

    init {
        observePreferredCurrency()
        observeLastPriceRefresh()
    }

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

    fun onErrorDismissed() = retryCollectionStats()

    fun retryCollectionStats() {
        _uiState.update { it.copy(error = null, isLoading = true) }
        collectionRetry.value++
    }

    fun retryGameStats() {
        _uiState.update { it.copy(gameError = false) }
        gameRetry.value++
    }

    /**
     * Switches the visible tab. Updates [_selectedTab] — the single source of truth every
     * tab-scoped pipeline `flatMapLatest`s off (see class doc) — as well as the mirrored
     * [StatsUiState.selectedTab] the UI reads for the `TabRow`. Activating TRADES for the first
     * time (or after an error/currency invalidation) lazily triggers [loadTradeStats] — the
     * expensive per-thread item fetch never runs from a shared `combine` pipeline (Phase 0
     * feasibility caveat).
     */
    fun onTabSelected(tab: StatsTab) {
        _selectedTab.value = tab
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
}
