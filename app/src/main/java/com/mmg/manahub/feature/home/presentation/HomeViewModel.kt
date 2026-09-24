package com.mmg.manahub.feature.home.presentation

// Step ID constants are top-level in FirstStepItem.kt (same package — no import needed).
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.R
import com.mmg.manahub.core.FeatureFlags
import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.data.remote.ScryfallRemoteDataSource
import com.mmg.manahub.core.data.repository.TradesRepository
import com.mmg.manahub.core.domain.auth.AuthRepository
import com.mmg.manahub.core.domain.auth.SessionState
import com.mmg.manahub.core.domain.repository.DeckRepository
import com.mmg.manahub.core.domain.repository.DraftRepository
import com.mmg.manahub.core.domain.repository.DraftSimRepository
import com.mmg.manahub.core.domain.repository.FriendRepository
import com.mmg.manahub.core.domain.repository.OpenForTradeRepository
import com.mmg.manahub.core.domain.repository.PlaytestRepository
import com.mmg.manahub.core.domain.repository.StatsRepository
import com.mmg.manahub.core.domain.repository.TradeSuggestionsRepository
import com.mmg.manahub.core.domain.repository.UserCardRepository
import com.mmg.manahub.core.domain.repository.WishlistRepository
import com.mmg.manahub.core.domain.usecase.home.GetAccountNudgeUseCase
import com.mmg.manahub.core.gamification.domain.model.PlayerProgression
import com.mmg.manahub.core.gamification.domain.model.QuestBoard
import com.mmg.manahub.core.gamification.domain.model.QuestUiModel
import com.mmg.manahub.core.gamification.domain.model.StreakUiModel
import com.mmg.manahub.core.gamification.domain.GamificationAvailability
import com.mmg.manahub.core.gamification.domain.repository.GamificationRepository
import com.mmg.manahub.core.model.CollectionSummary
import com.mmg.manahub.core.model.CommunityDeckSearchFilters
import com.mmg.manahub.core.model.CommunityDeckSummary
import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.model.DeckSummary
import com.mmg.manahub.core.model.DraftSet
import com.mmg.manahub.core.model.DraftState
import com.mmg.manahub.core.model.Friend
import com.mmg.manahub.core.model.MagicSet
import com.mmg.manahub.core.model.MtgColor
import com.mmg.manahub.core.model.NudgeTrigger
import com.mmg.manahub.core.model.PLAYABLE_SET_TYPES
import com.mmg.manahub.core.model.PreferredCurrency
import com.mmg.manahub.core.model.QuickStartAction
import com.mmg.manahub.core.model.Rarity
import com.mmg.manahub.core.model.Tournament
import com.mmg.manahub.core.model.TradeProposal
import com.mmg.manahub.core.model.TradeStatus
import com.mmg.manahub.core.model.TradeSuggestion
import com.mmg.manahub.core.model.TrendingSnapshot
import com.mmg.manahub.core.model.WidgetSize
import com.mmg.manahub.core.model.news.FeedContentFilter
import com.mmg.manahub.core.model.news.NewsItem
import com.mmg.manahub.core.model.puzzle.Puzzle
import com.mmg.manahub.core.sync.SyncManager
import com.mmg.manahub.core.sync.SyncState
import com.mmg.manahub.core.util.PriceFormatter
import com.mmg.manahub.core.util.recordSafeNonFatal
import com.mmg.manahub.feature.communitydecks.domain.usecase.SearchCommunityDecksUseCase
import com.mmg.manahub.feature.communitydecks.presentation.CommunityDeckFormatFilter
import com.mmg.manahub.feature.game.domain.model.DeckStats
import com.mmg.manahub.feature.game.domain.model.EliminationStats
import com.mmg.manahub.feature.game.domain.model.SessionHistoryEntry
import com.mmg.manahub.feature.game.domain.repository.GameSessionRepository
import com.mmg.manahub.feature.news.domain.feed.filterFeed
import com.mmg.manahub.feature.news.domain.usecase.GetNewsFeedUseCase
import com.mmg.manahub.feature.news.domain.usecase.ManageSourcesUseCase
import com.mmg.manahub.feature.news.domain.usecase.RefreshNewsFeedUseCase
import com.mmg.manahub.feature.tournament.domain.repository.TournamentRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.toLocalDateTime

/**
 * Drives the customizable Home widget board.
 *
 * Loading contract: every widget data source is its own `StateFlow` whose ONLY null/loading value is
 * the `stateIn` initial value, so a `WhileSubscribed` restart (leaving and returning to Home without
 * process death) keeps showing the last known data instead of flashing a loading or signed-out state.
 * Auth-dependent sources key off one [AuthGate] and only reset when the signed-in user actually
 * changes; network one-shots live in ViewModel-owned [OneShotCache]s so a resubscribe never refetches.
 * [HomeUiState.boardReady] and [HomeWidgetType.isReady] tell the UI exactly when each part is certain.
 *
 * Backend work is gated on the widget being on the board (ADR-005): trending, community decks, trade
 * suggestions, the daily puzzle and the trades warm-up only run while their widget is placed.
 *
 * The live in-memory active-game state is NOT injected here (the GameViewModel is activity-scoped);
 * [com.mmg.manahub.app.navigation.AppNavGraph] passes it into the screen instead.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val userPrefsDataStore: UserPreferencesDataStore,
    private val statsRepository: StatsRepository,
    private val deckRepository: DeckRepository,
    private val gameSessionRepository: GameSessionRepository,
    private val draftSimRepository: DraftSimRepository,
    private val tournamentRepository: TournamentRepository,
    private val authRepository: AuthRepository,
    private val cardRepository: com.mmg.manahub.core.domain.repository.CardRepository,
    private val scryfallRemoteDataSource: ScryfallRemoteDataSource,
    private val getNewsFeedUseCase: GetNewsFeedUseCase,
    private val refreshNewsFeedUseCase: RefreshNewsFeedUseCase,
    private val manageSourcesUseCase: ManageSourcesUseCase,
    private val draftRepository: DraftRepository,
    private val wishlistRepository: WishlistRepository,
    private val getAccountNudgeUseCase: GetAccountNudgeUseCase,
    private val gamificationRepository: GamificationRepository,
    private val gamificationAvailability: GamificationAvailability,
    private val userCardRepository: UserCardRepository,
    private val tradesRepository: TradesRepository,
    private val openForTradeRepository: OpenForTradeRepository,
    private val tradeSuggestionsRepository: TradeSuggestionsRepository,
    private val friendRepository: FriendRepository,
    private val playtestRepository: PlaytestRepository,
    private val syncManager: SyncManager,
    private val searchCommunityDecksUseCase: SearchCommunityDecksUseCase,
    // Optional params are appended last so positional call sites keep compiling.
    private val communityAggregateRepository: com.mmg.manahub.core.domain.repository.CommunityAggregateRepository? = null,
    private val getTodayPuzzleUseCase: com.mmg.manahub.feature.puzzle.domain.usecase.GetTodayPuzzleUseCase? = null,
    private val getPuzzleResultUseCase: com.mmg.manahub.feature.puzzle.domain.usecase.GetPuzzleResultUseCase? = null,
    private val puzzleEnabled: Boolean = FeatureFlags.Puzzle.PUZZLE_ENABLED,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    /**
     * Crashlytics handle for additive telemetry (breadcrumb logs + custom keys) on the Home board.
     * Non-fatal exception reporting goes through [recordSafeNonFatal]. Telemetry never alters control
     * flow and never carries PII (only enum ids, counts, set codes and exception type names).
     */
    private val crashlytics = FirebaseCrashlytics.getInstance()

    /** Set once after the board first resolves, so session context keys are attached lazily. */
    private var sessionContextKeysSet = false

    /** Guards the First Steps completion-flag write so one app session issues it at most once. */
    private var firstStepsCompletionMarkSeenDispatched = false

    /**
     * Externally-triggered ACTION_REQUIRED nudge (highest priority). Cleared on dismissal or on a
     * successful sign-in.
     */
    private val actionRequiredMessage = MutableStateFlow<String?>(null)

    /** Random Scryfall cards for the Discover row; empty until the first fetch succeeds. */
    private val discoverCardsFlow = MutableStateFlow<List<DiscoverCard>>(emptyList())

    /** Discover row load state: spinner vs. retry affordance instead of an endless spinner. */
    private val discoverLoadStateFlow = MutableStateFlow(DiscoverLoadState.LOADING)

    /** Set the Discover row is scoped to; null = the default random query. */
    private val discoverSetFlow = MutableStateFlow<MagicSet?>(null)

    /** True once the user picked (or cleared) a Discover set, so the random seed never overrides it. */
    @Volatile
    private var discoverSetTouchedByUser = false

    /** The Random card widget's card, re-fetchable on demand and independent of the Discover row. */
    private val randomCardFlow = MutableStateFlow<DiscoverCard?>(null)

    /** Load state for the Random card widget. */
    private val randomCardLoadStateFlow = MutableStateFlow(DiscoverLoadState.LOADING)

    private var fetchDiscoverJob: Job? = null
    private var fetchRandomCardJob: Job? = null

    /** Serializes layout mutations so rapid add/remove/move taps can never interleave. */
    private val layoutMutex = Mutex()

    private val latestSetsCache = OneShotCache<Unit, List<DraftSet>>(nowMs)
    private val trendingCache = OneShotCache<Unit, TrendingSnapshot?>(nowMs)
    private val communityDecksCache = OneShotCache<CommunityDecksKey, List<CommunityDeckSummary>>(nowMs)
    private val suggestionsCache = OneShotCache<String, List<TradeSuggestion>>(nowMs)
    private val puzzleCache = OneShotCache<kotlinx.datetime.LocalDate, Puzzle?>(nowMs)

    private val rulesTipIndex = MutableStateFlow(dailyRulesTipIndex(nowMs()))

    /**
     * Index into [RULES_TIPS_DAILY_ORDER] of the Rules Tip currently shown: starts at today's tip and
     * changes only on [HomeAction.RollRulesTip]. Memory-only by design.
     */
    val rulesTipIndexFlow: StateFlow<Int> = rulesTipIndex.asStateFlow()

    /** Widgets that already played their first reveal; outlives the screen so a return never replays it. */
    val revealTracker = WidgetRevealTracker()

    // ── Flow helpers ──────────────────────────────────────────────────────────

    /** `stateIn` whose ONLY null is the initial value: a restart keeps the last emitted value. */
    private fun <T> Flow<T>.stateInLoading(): StateFlow<T?> =
        map<T, T?> { it }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    private fun reportFlowError(source: String, error: Throwable) {
        crashlytics.setCustomKey("home_flow_error_source", source)
        recordSafeNonFatal("home_flow_$source", error)
        crashlytics.log("home_flow_error: $source")
    }

    // ── Authentication ────────────────────────────────────────────────────────

    /**
     * The board's single auth source. Eager so it never re-derives on a restart; the repository
     * already keeps a signed-in session through transient refresh failures.
     */
    private val authGate: StateFlow<AuthGate> = authRepository.sessionState
        .map { it.toAuthGate() }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, AuthGate.Unknown)

    /** [authGate] once resolved; auth-dependent flows never observe [AuthGate.Unknown]. */
    private val resolvedAuthGate: Flow<AuthGate> = authGate.filter { it != AuthGate.Unknown }

    /** The signed-in user id (null while signed out), emitted only when it actually changes. */
    private val signedInUserIdFlow: Flow<String?> =
        resolvedAuthGate.map { it.signedInUserId }.distinctUntilChanged()

    /** A value together with the [AuthGate] it was loaded for. */
    private data class Scoped<T>(val gate: AuthGate, val value: T)

    /**
     * An auth-dependent source that resolves to [signedOut] while signed out and to [signedIn] for
     * the current user. The cached value survives restarts; it reads as null (loading) only until the
     * first value lands for the CURRENT gate, so a user switch shows loading and a resume does not.
     * A failing source degrades to [fallback] instead of leaving the widget loading forever.
     */
    private fun <T : Any> userScopedFlow(
        source: String,
        signedOut: T,
        fallback: T,
        signedIn: (userId: String) -> Flow<T>,
    ): Flow<T?> {
        val scoped: StateFlow<Scoped<T>?> = resolvedAuthGate
            .flatMapLatest { gate ->
                when (gate) {
                    is AuthGate.SignedIn -> signedIn(gate.userId)
                        .catch { reportFlowError(source, it); emit(fallback) }
                        .map { Scoped(gate, it) }

                    else -> flowOf(Scoped(gate, signedOut))
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)
        return combine(authGate, scoped) { gate, loaded -> loaded?.takeIf { it.gate == gate }?.value }
            .distinctUntilChanged()
    }

    // ── Layout ────────────────────────────────────────────────────────────────

    /**
     * The persisted layout; null until decoded. The default is auth-dependent, so this waits for the
     * gate to resolve instead of briefly emitting the signed-out default to a signed-in user.
     */
    private val layoutState: StateFlow<List<WidgetInstance>?> = resolvedAuthGate
        .map { it is AuthGate.SignedIn }
        .distinctUntilChanged()
        .flatMapLatest { signedIn ->
            userPrefsDataStore
                .homeLayoutFlow(defaultLayoutFor(signedIn).map { it.toPersisted() })
                // Legacy "social_hub" expands to FRIENDS + COMMUNITY_DECKS; unknown ids are dropped.
                .map { persisted -> persisted.toInstancesWithMigration() }
                .catch { reportFlowError("layout", it); emit(defaultLayoutFor(signedIn)) }
        }
        .stateInLoading()

    /** Whether [type] is currently placed on the board (never emits before the layout is decoded). */
    private fun isOnBoard(type: HomeWidgetType): Flow<Boolean> =
        layoutState.filterNotNull()
            .map { layout -> layout.any { it.type == type } }
            .distinctUntilChanged()

    // ── First Steps / preferences ───────────────────────────────────────────────

    private data class PrefsSnapshot(
        val quickStart: List<QuickStartAction>,
        val playerName: String,
        val skippedFirstSteps: Set<String>,
        val totalPlaytestCount: Int,
        val firstStepsCompletionSeen: Boolean,
    )

    private val prefsState: StateFlow<PrefsSnapshot?> = combine(
        userPrefsDataStore.observeQuickStartActions().catch { emit(QuickStartAction.defaults) },
        userPrefsDataStore.playerNameFlow.catch { emit(DEFAULT_PLAYER_NAME) },
        userPrefsDataStore.observeSkippedFirstSteps().distinctUntilChanged().catch { emit(emptySet()) },
        playtestRepository.observeTotalTestCount().distinctUntilChanged().catch { emit(0) },
        userPrefsDataStore.firstStepsCompletionSeenFlow.distinctUntilChanged().catch { emit(false) },
    ) { quickStart, playerName, skipped, playtestTotal, completionSeen ->
        PrefsSnapshot(quickStart, playerName, skipped, playtestTotal, completionSeen)
    }.stateInLoading()

    private data class AccountPrefs(val isCoolingDown: Boolean, val avatarUrl: String?)

    private val accountPrefsState: StateFlow<AccountPrefs?> = combine(
        userPrefsDataStore.isNudgeCoolingDown().catch { emit(false) },
        userPrefsDataStore.avatarUrlFlow.catch { emit(null) },
    ) { coolingDown, avatarUrl -> AccountPrefs(coolingDown, avatarUrl) }
        .stateInLoading()

    // ── Library / activity ────────────────────────────────────────────────────

    private val currencyFlow: StateFlow<PreferredCurrency> =
        userPrefsDataStore.preferredCurrencyFlow
            .distinctUntilChanged()
            .catch { emit(PreferredCurrency.USD) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), PreferredCurrency.USD)

    /** Lightweight collection headline (not the full 30-query stats pipeline). */
    private val collectionSummaryState: StateFlow<CollectionSummary?> =
        statsRepository.observeCollectionSummary()
            .catch { reportFlowError("collection_stats", it); emit(EMPTY_COLLECTION_SUMMARY) }
            .stateInLoading()

    private val decksState: StateFlow<List<DeckSummary>?> =
        deckRepository.observeAllDeckSummaries()
            .catch { reportFlowError("decks", it); emit(emptyList()) }
            .stateInLoading()

    /** Single tournaments subscription shared by the activity counter and the active-tournament slide. */
    private val tournamentsState: StateFlow<List<Tournament>?> =
        tournamentRepository.observeTournaments()
            .catch { reportFlowError("tournaments", it); emit(emptyList()) }
            .stateInLoading()

    private val activityState: StateFlow<ActivitySnapshot?> = combine(
        gameSessionRepository.observeTotalGames()
            .catch { reportFlowError("total_games", it); emit(0) },
        draftSimRepository.observeActiveSession()
            .catch { reportFlowError("active_draft", it); emit(null) },
        tournamentsState.filterNotNull(),
    ) { totalGames, draft, tournaments ->
        ActivitySnapshot(
            totalGames = totalGames,
            activeDraft = draft,
            activeTournaments = tournaments.count { it.isOngoing() },
        )
    }.stateInLoading()

    private data class TournamentSlot(val summary: TournamentSummary?)

    /**
     * Current-round-aware active tournament (read-only round query). Standing is intentionally
     * omitted rather than faked.
     */
    private val activeTournamentState: StateFlow<TournamentSlot?> =
        tournamentsState.filterNotNull()
            .map { tournaments -> tournaments.firstOrNull { it.isOngoing() } }
            .distinctUntilChanged()
            .flatMapLatest { active ->
                if (active == null) {
                    flowOf(TournamentSlot(null))
                } else {
                    tournamentRepository.observeCurrentRound(active.id)
                        .map { round ->
                            TournamentSlot(
                                TournamentSummary(
                                    tournamentId = active.id,
                                    name = active.name,
                                    round = round,
                                    standing = null,
                                )
                            )
                        }
                        .catch { reportFlowError("active_tournament", it); emit(TournamentSlot(null)) }
                }
            }
            .stateInLoading()

    // ── Game stats ───────────────────────────────────────────────────────────

    private val historyState: StateFlow<List<SessionHistoryEntry>?> =
        gameSessionRepository.observeLocalSessionHistory(HISTORY_LIMIT)
            .distinctUntilChanged()
            .catch { reportFlowError("session_history", it); emit(emptyList()) }
            .stateInLoading()

    private val performanceFlow: Flow<PerformanceDetails> = combine(
        gameSessionRepository.observeAvgWinTurn().catch { emit(null) },
        gameSessionRepository.observeAvgLifeOnWin().catch { emit(null) },
        gameSessionRepository.observeAvgLifeOnLoss().catch { emit(null) },
        historyState.filterNotNull(),
    ) { avgWinTurn, avgLifeWin, avgLifeLoss, history ->
        PerformanceDetails(
            avgWinTurn = avgWinTurn,
            avgLifeOnWin = avgLifeWin,
            avgLifeOnLoss = avgLifeLoss,
            longestGameMs = history.maxOfOrNull { it.durationMs },
            mostGamesInOneDay = history
                .groupBy { it.playedAt / DAY_MS }
                .maxOfOrNull { it.value.size } ?: 0,
        )
    }

    private val statsSnapshotState: StateFlow<StatsSnapshot?> = combine(
        gameSessionRepository.observeLocalWins().catch { emit(0) },
        historyState.filterNotNull(),
        gameSessionRepository.observeDeckStats().catch { emit(emptyList()) },
        gameSessionRepository.observeMostFrequentElimination().catch { emit(null) },
        performanceFlow,
    ) { wins, history, deckStats, nemesis, performance ->
        StatsSnapshot(
            localWins = wins,
            history = history,
            deckStats = deckStats,
            nemesis = nemesis,
            performance = performance,
        )
    }.stateInLoading()

    // ── News ───────────────────────────────────────────────────────────────────

    /** Latest items from FOLLOWED sources (same rule as the MTG Today feed), capped at [MAX_NEWS]. Null while loading. */
    private val recentNewsFlow: StateFlow<List<NewsItem>?> =
        combine(
            getNewsFeedUseCase(),
            manageSourcesUseCase.observeSources(),
        ) { items, sources ->
            val followedIds = sources.filter { it.isEnabled }.map { it.id }.toSet()
            filterFeed(items, followedIds, FeedContentFilter.ALL, selectedSourceId = null, query = "", sourceNames = emptyMap())
                .take(MAX_NEWS)
        }
            .catch {
                reportFlowError("recent_news", it)
                emit(emptyList())
            }
            .stateInLoading()

    // ── Discover ───────────────────────────────────────────────────────────────

    /**
     * Latest draftable sets for the LATEST_SETS widget; null while loading. Fetched once into a
     * ViewModel-owned cache and only while the widget is on the board.
     */
    private val latestSetsState: StateFlow<List<DraftSet>?> =
        isOnBoard(HomeWidgetType.LATEST_SETS)
            .flatMapLatest { onBoard ->
                latestSetsCache.observe(Unit, load = onBoard) {
                    latestSetsCache.ensureLoaded(Unit, LATEST_SETS_MAX_AGE_MS, FAILED_FETCH_RETRY_MS) {
                        fetchLatestSets()
                    }
                }.map { it?.value }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    private val discoverRowFlow: Flow<DiscoverRow> =
        combine(discoverSetFlow, discoverCardsFlow, discoverLoadStateFlow) { set, cards, loadState ->
            DiscoverRow(selectedSet = set, cards = cards, loadState = loadState)
        }

    private val discoverSnapshotFlow: Flow<DiscoverSnapshot> =
        combine(
            latestSetsState,
            discoverRowFlow,
            randomCardFlow,
            randomCardLoadStateFlow,
        ) { sets, row, randomCard, randomCardLoadState ->
            DiscoverSnapshot(
                latestSets = sets,
                discoverCards = row.cards,
                loadState = row.loadState,
                selectedSet = row.selectedSet,
                randomCard = randomCard,
                randomCardLoadState = randomCardLoadState,
            )
        }

    // ── Social / trades (auth-dependent) ────────────────────────────────────────

    /** Up to 5 recent friends, shared by the FRIENDS widget and suggestion counterparty names. */
    private val friendsFlow: Flow<List<Friend>?> =
        userScopedFlow("friends", signedOut = emptyList(), fallback = emptyList()) {
            friendRepository.observeFriends()
        }

    private data class FriendActivity(val friendCount: Int, val latestFriendRequestName: String?)

    private val friendActivityFlow: Flow<FriendActivity?> =
        userScopedFlow("friend_activity", signedOut = NO_FRIEND_ACTIVITY, fallback = NO_FRIEND_ACTIVITY) {
            combine(
                friendRepository.observeFriendCount().catch { emit(0) },
                friendRepository.observePendingRequests().catch { emit(emptyList()) },
            ) { count, pending -> FriendActivity(count, pending.firstOrNull()?.fromNickname) }
        }

    /** Inbox summary + most recent proposals from ONE observeActiveProposals subscription. */
    private data class ProposalsView(val summary: TradeSummary?, val recent: List<TradeProposal>)

    private val proposalsFlow: Flow<ProposalsView?> =
        userScopedFlow("trade_summary", signedOut = NO_PROPOSALS, fallback = NO_PROPOSALS) { userId ->
            tradesRepository.observeActiveProposals().map { proposals ->
                ProposalsView(
                    summary = tradeSummaryFor(proposals, userId),
                    recent = proposals.sortedByDescending { it.updatedAt }.take(RECENT_TRADES_LIMIT),
                )
            }
        }

    /** Open-for-Trade count + estimated value + a few thumbnails for the Trades Hub. */
    private data class OpenForTradeSummary(
        val count: Int,
        val valueDisplay: String?,
        val cards: List<DiscoverCard> = emptyList(),
    )

    private val openForTradeFlow: Flow<OpenForTradeSummary?> =
        userScopedFlow("open_for_trade", signedOut = NO_OPEN_FOR_TRADE, fallback = NO_OPEN_FOR_TRADE) {
            combine(openForTradeRepository.observeLocal(), currencyFlow) { entries, currency ->
                if (entries.isEmpty()) return@combine NO_OPEN_FOR_TRADE
                val totalUsd = entries.sumOf { (it.card?.priceUsd ?: 0.0) * it.quantity }
                val totalEur = entries.sumOf { (it.card?.priceEur ?: 0.0) * it.quantity }
                val display = if (totalUsd <= 0.0 && totalEur <= 0.0) {
                    null
                } else {
                    PriceFormatter.formatFromScryfall(
                        priceUsd = totalUsd,
                        priceEur = totalEur,
                        preferredCurrency = currency,
                    )
                }
                val cards = entries
                    .distinctBy { it.scryfallId }
                    .take(HOME_OPEN_FOR_TRADE_PREVIEW_LIMIT)
                    .mapNotNull { entry ->
                        val card = entry.card ?: return@mapNotNull null
                        DiscoverCard(
                            id = card.scryfallId,
                            scryfallId = card.scryfallId,
                            name = card.name,
                            imageUrl = card.imageNormal ?: card.imageArtCrop,
                            typeLine = card.typeLine,
                        )
                    }
                OpenForTradeSummary(count = entries.sumOf { it.quantity }, valueDisplay = display, cards = cards)
            }
        }

    /** Wishlist count + currency-formatted value + preview cards (account-gated). */
    private val wishlistFlow: Flow<WishlistStats?> =
        userScopedFlow("wishlist", signedOut = EMPTY_WISHLIST, fallback = EMPTY_WISHLIST) {
            combine(wishlistRepository.observeLocal(), currencyFlow) { entries, currency ->
                if (entries.isEmpty()) return@combine EMPTY_WISHLIST
                val totalValueUsd = entries.sumOf { (it.card?.priceUsd ?: 0.0) * it.quantity }
                val totalValueEur = entries.sumOf { (it.card?.priceEur ?: 0.0) * it.quantity }
                WishlistStats(
                    count = entries.sumOf { it.quantity },
                    estimatedValueDisplay = PriceFormatter.formatFromScryfall(
                        priceUsd = totalValueUsd,
                        priceEur = totalValueEur,
                        preferredCurrency = currency,
                    ),
                    cards = entries
                        .distinctBy { it.cardId }
                        .take(WISHLIST_PREVIEW_LIMIT)
                        .mapNotNull { entry ->
                            val card = entry.card ?: return@mapNotNull null
                            DiscoverCard(
                                id = card.scryfallId,
                                scryfallId = card.scryfallId,
                                name = card.name,
                                imageUrl = card.imageNormal,
                                typeLine = card.typeLine,
                            )
                        }.toSet(),
                )
            }
        }

    /**
     * Matched-card previews for the Trades Hub Suggestions section. The Supabase RPC runs at most
     * once per [SUGGESTIONS_MAX_AGE_MS] per user and only while TRADES_HUB is on the board; a friends
     * update only re-resolves names/cards locally, it never refetches.
     */
    private val suggestionPreviewsState: StateFlow<List<TradeSuggestionPreview>?> =
        combine(resolvedAuthGate, isOnBoard(HomeWidgetType.TRADES_HUB)) { gate, onBoard -> gate to onBoard }
            .flatMapLatest { (gate, onBoard) ->
                when (gate) {
                    is AuthGate.SignedIn -> suggestionsCache
                        .observe(gate.userId, load = onBoard) {
                            suggestionsCache.ensureLoaded(
                                gate.userId,
                                SUGGESTIONS_MAX_AGE_MS,
                                FAILED_FETCH_RETRY_MS,
                            ) { fetchSuggestions() }
                        }
                        .combine(friendsFlow.filterNotNull()) { entry, friends -> entry to friends }
                        .mapLatest { (entry, friends) ->
                            entry?.let { buildSuggestionPreviews(it.value, friends, gate.userId) }
                        }

                    else -> flowOf(emptyList())
                }
            }
            .catch {
                reportFlowError("trade_suggestions", it)
                emit(emptyList())
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    // ── Gamification / recently added ───────────────────────────────────────────

    /**
     * Gamification slice (level/XP, streak, quests), gated by [GamificationAvailability] — unavailable
     * means a null snapshot so every gamification surface disappears.
     */
    private val gamificationState: StateFlow<GamificationSnapshot?> =
        gamificationAvailability.availableFlow
            .catch { emit(false) }
            .distinctUntilChanged()
            .flatMapLatest { enabled ->
                if (!enabled) {
                    flowOf(GamificationSnapshot(enabled = false, data = null))
                } else {
                    combine(
                        gamificationRepository.observeProgression().catch { emit(DEFAULT_PROGRESSION) },
                        gamificationRepository.observeActiveQuests().catch { emit(QuestBoard.empty) },
                        gamificationRepository.observeDailyActivityStreak().catch { emit(DEFAULT_STREAK) },
                    ) { progression, board, streak ->
                        GamificationSnapshot(enabled = true, data = toHomeGamification(progression, board, streak))
                    }.catch {
                        reportFlowError("gamification", it)
                        emit(GamificationSnapshot(enabled = true, data = null))
                    }
                }
            }
            .stateInLoading()

    /** Newest-first collection additions (Room join, no network). */
    private val recentlyAddedState: StateFlow<List<RecentlyAddedCard>?> =
        userCardRepository.observeRecentlyAdded(RECENTLY_ADDED_LIMIT)
            .map { rows ->
                rows.map { row ->
                    RecentlyAddedCard(
                        rowId = row.userCard.id,
                        quantity = row.userCard.quantity,
                        card = DiscoverCard(
                            id = row.card.scryfallId,
                            scryfallId = row.card.scryfallId,
                            name = row.card.name,
                            imageUrl = row.card.imageNormal ?: row.card.imageArtCrop,
                            typeLine = row.card.typeLine,
                        ),
                    )
                }
            }
            .catch { reportFlowError("recently_added", it); emit(emptyList()) }
            .stateInLoading()

    // ── UI state ───────────────────────────────────────────────────────────────

    private val uiState: StateFlow<HomeUiState> = run {
        val libraryFlow = combine(collectionSummaryState, decksState, currencyFlow) { summary, decks, currency ->
            LibrarySnapshot(summary = summary, decks = decks, currency = currency)
        }

        val accountFlow = combine(
            authGate,
            authRepository.sessionState,
            accountPrefsState,
            actionRequiredMessage,
        ) { gate, session, prefs, actionRequired ->
            val user = (session as? SessionState.Authenticated)?.user
            AccountSnapshot(
                gate = gate,
                prefs = prefs,
                actionRequiredMessage = actionRequired,
                avatarUrl = prefs?.avatarUrl ?: user?.avatarUrl,
                nickname = user?.nickname,
            )
        }

        val coreFlow = combine(libraryFlow, activityState, accountFlow, prefsState) { library, activity, account, prefs ->
            CoreSnapshot(library = library, activity = activity, account = account, prefs = prefs)
        }

        val gameStatsFlow = combine(statsSnapshotState, activeTournamentState) { stats, tournament ->
            GameStatsBundle(stats = stats, tournament = tournament)
        }

        val tradesFlow = combine(proposalsFlow, suggestionPreviewsState, openForTradeFlow) { proposals, suggestions, openForTrade ->
            TradesSnapshot(proposals = proposals, suggestionPreviews = suggestions, openForTrade = openForTrade)
        }

        val socialFlow = combine(tradesFlow, wishlistFlow, friendsFlow, friendActivityFlow) { trades, wishlist, friends, activity ->
            SocialSnapshot(trades = trades, wishlist = wishlist, friends = friends, friendActivity = activity)
        }

        val extrasFlow = combine(gamificationState, recentlyAddedState) { gamification, recentlyAdded ->
            gamification to recentlyAdded
        }

        val dataFlow = combine(layoutState, gameStatsFlow, discoverSnapshotFlow, socialFlow, extrasFlow) { layout, gameStats, discover, social, extras ->
            DataBundle(
                layout = layout,
                gameStats = gameStats,
                discover = discover,
                social = social,
                gamification = extras.first,
                recentlyAdded = extras.second,
            )
        }

        combine(coreFlow, recentNewsFlow, dataFlow) { core, news, data -> buildUiState(core, news, data) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = HomeUiState(hero = HomeHeroState.Loading),
            )
    }

    /** Public UI state. */
    val state: StateFlow<HomeUiState> get() = uiState

    // ── Independent widget flows (outside the HomeUiState combine) ───────────────

    private val trendingEntryState: StateFlow<OneShotCache.Entry<Unit, TrendingSnapshot?>?> =
        isOnBoard(HomeWidgetType.TRENDING_COMMANDERS)
            .flatMapLatest { onBoard ->
                trendingCache.observe(Unit, load = onBoard) {
                    trendingCache.ensureLoaded(Unit, TRENDING_MAX_AGE_MS, FAILED_FETCH_RETRY_MS) {
                        fetchTrending()
                    }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    /**
     * Top commanders of the week from the community Worker. Null means "don't render" — both while
     * loading and after any failure (log only, never an error UI); see [trendingLoadedFlow].
     */
    val trendingFlow: StateFlow<TrendingSnapshot?> =
        trendingEntryState
            .map { it?.value }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    /** True once the trending fetch resolved (success or failure) — distinguishes loading from hidden. */
    val trendingLoadedFlow: StateFlow<Boolean> =
        trendingEntryState
            .map { it != null }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

    private data class CommunityDecksKey(
        val category: HomeCommunityDeckCategory,
        val format: CommunityDeckFormatFilter?,
    )

    /** The persisted COMMUNITY_DECKS selection; null until DataStore has been read. */
    private val communityDecksKeyState: StateFlow<CommunityDecksKey?> = combine(
        userPrefsDataStore.homeCommunityDecksCategoryFlow
            .map { HomeCommunityDeckCategory.fromPersistedId(it) }
            .catch { emit(HomeCommunityDeckCategory.POPULAR) },
        userPrefsDataStore.homeCommunityDecksFormatFlow
            .map { parseCommunityDecksFormat(it) }
            .catch { emit(null) },
    ) { category, format -> CommunityDecksKey(category, format) }
        .distinctUntilChanged()
        .stateInLoading()

    /** Home COMMUNITY_DECKS widget's selected category. */
    val communityDecksCategoryFlow: StateFlow<HomeCommunityDeckCategory> =
        communityDecksKeyState
            .map { it?.category ?: HomeCommunityDeckCategory.POPULAR }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HomeCommunityDeckCategory.POPULAR)

    /** Home COMMUNITY_DECKS widget's format filter; null means every format. */
    val communityDecksFormatFlow: StateFlow<CommunityDeckFormatFilter?> =
        communityDecksKeyState
            .map { it?.format }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    /**
     * Decks for the selected category + format; null while loading. Changing the selection is the
     * one user action that may show loading again; resubscribing never refetches.
     */
    val communityDecksFlow: StateFlow<List<CommunityDeckSummary>?> =
        combine(
            communityDecksKeyState.filterNotNull(),
            isOnBoard(HomeWidgetType.COMMUNITY_DECKS),
        ) { key, onBoard -> key to onBoard }
            .flatMapLatest { (key, onBoard) ->
                communityDecksCache.observe(key, load = onBoard) {
                    communityDecksCache.ensureLoaded(key, COMMUNITY_DECKS_MAX_AGE_MS, FAILED_FETCH_RETRY_MS) {
                        fetchCommunityDecks(key)
                    }
                }.map { it?.value }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    /**
     * DAILY_PUZZLE preview. Distinguishes Loading / Loaded / Unavailable so the widget can explain an
     * outage instead of vanishing. Never touches the network while the feature flag is off or the
     * widget is not on the board; today's local progress is re-read on every resubscription.
     */
    val dailyPuzzleFlow: StateFlow<DailyPuzzleWidgetState> =
        if (!puzzleEnabled) {
            MutableStateFlow<DailyPuzzleWidgetState>(DailyPuzzleWidgetState.Unavailable).asStateFlow()
        } else {
            isOnBoard(HomeWidgetType.DAILY_PUZZLE)
                .flatMapLatest { onBoard -> if (onBoard) dailyPuzzleUpdates() else emptyFlow() }
                .catch {
                    crashlytics.setCustomKey("puzzle_widget_unavailable_reason", "exception")
                    crashlytics.log("home_daily_puzzle_widget_failed")
                    recordSafeNonFatal("home_daily_puzzle_widget_failed", it)
                    emit(DailyPuzzleWidgetState.Unavailable)
                }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), DailyPuzzleWidgetState.Loading)
        }

    private val competitiveEnabledState: StateFlow<Boolean?> =
        userPrefsDataStore.competitiveEnabledFlow
            .catch { emit(false) }
            .stateInLoading()

    /** Whether the [HomeWidgetType.COMPETITIVE] tile is visible (runtime DataStore flag). */
    val competitiveEnabledFlow: StateFlow<Boolean> =
        competitiveEnabledState
            .map { it ?: false }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

    /** The out-of-[HomeUiState] inputs of [HomeWidgetType.isReady]. */
    val widgetExtrasFlow: StateFlow<HomeWidgetExtras> =
        combine(trendingLoadedFlow, communityDecksFlow, dailyPuzzleFlow, competitiveEnabledState) { trendingLoaded, decks, puzzle, competitive ->
            HomeWidgetExtras(
                trendingLoaded = trendingLoaded,
                communityDecks = decks,
                dailyPuzzle = puzzle,
                competitiveEnabled = competitive,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HomeWidgetExtras())

    /**
     * Per-widget readiness for the UI: a widget renders its skeleton while false and swaps to its
     * real body (content, empty, error or gated placeholder) once true. See [HomeWidgetType.isReady].
     */
    val widgetReadiness: StateFlow<Map<HomeWidgetType, Boolean>> =
        combine(uiState, widgetExtrasFlow) { state, extras ->
            HomeWidgetType.entries.associateWith { it.isReady(state, extras) }
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            HomeWidgetType.entries.associateWith { false },
        )

    // ── Loaders ────────────────────────────────────────────────────────────────

    /**
     * Mirrors the cache slot for [key]; while [load] is true it also asks [loader] to fill it. The
     * loader runs concurrently so the current (possibly stale) entry is emitted immediately.
     */
    private fun <K : Any, T> OneShotCache<K, T>.observe(
        key: K,
        load: Boolean,
        loader: suspend () -> Unit,
    ): Flow<OneShotCache.Entry<K, T>?> = channelFlow {
        if (load) launch { loader() }
        entryFor(key).collect { send(it) }
    }

    private suspend fun fetchLatestSets(): Fetched<List<DraftSet>> =
        try {
            when (val result = draftRepository.getDraftableSets()) {
                is DataResult.Success -> Fetched(result.data.take(LATEST_SETS_LIMIT), succeeded = true)
                else -> Fetched(emptyList(), succeeded = false)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            reportFlowError("latest_sets", e)
            Fetched(emptyList(), succeeded = false)
        }

    private suspend fun fetchTrending(): Fetched<TrendingSnapshot?> {
        val repo = communityAggregateRepository ?: return Fetched(null, succeeded = true)
        awaitSyncWindow("trending")
        return try {
            val result = repo.getTrending()
            val snapshot = (result as? DataResult.Success)?.data
            if (snapshot == null) crashlytics.log("home_trending_widget_failed")
            Fetched(snapshot, succeeded = snapshot != null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            crashlytics.log("home_trending_widget_failed")
            Fetched(null, succeeded = false)
        }
    }

    private suspend fun fetchCommunityDecks(key: CommunityDecksKey): Fetched<List<CommunityDeckSummary>> {
        awaitSyncWindow("community_decks")
        val filters = CommunityDeckSearchFilters(
            deckFormatId = key.format?.apiId,
            orderBy = key.category.orderBy,
            primersOnly = key.category.primersOnly,
            page = 1,
            pageSize = HOME_COMMUNITY_DECKS_LIMIT,
        )
        return try {
            val result = searchCommunityDecksUseCase(filters)
            if (result is DataResult.Success) {
                Fetched(result.data.decks.take(HOME_COMMUNITY_DECKS_LIMIT), succeeded = true)
            } else {
                crashlytics.log("home_community_decks_load_failed")
                Fetched(emptyList(), succeeded = false)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            crashlytics.log("home_community_decks_load_failed")
            Fetched(emptyList(), succeeded = false)
        }
    }

    private suspend fun fetchSuggestions(): Fetched<List<TradeSuggestion>> =
        try {
            val result = tradeSuggestionsRepository.getSuggestions()
            Fetched(result.getOrNull().orEmpty(), succeeded = result.isSuccess)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            reportFlowError("trade_suggestions", e)
            Fetched(emptyList(), succeeded = false)
        }

    /** Today's puzzle (cached per UTC date) joined with the local attempt row for that exact date. */
    private fun dailyPuzzleUpdates(): Flow<DailyPuzzleWidgetState> = flow {
        val getTodayPuzzle = getTodayPuzzleUseCase
        if (getTodayPuzzle == null) {
            crashlytics.setCustomKey("puzzle_widget_unavailable_reason", "no_usecase")
            emit(DailyPuzzleWidgetState.Unavailable)
            return@flow
        }
        awaitSyncWindow("daily_puzzle")
        // Mirrors the server's UTC rollover boundary (ADR-006 Decision 2).
        val today = kotlinx.datetime.Instant.fromEpochMilliseconds(nowMs())
            .toLocalDateTime(kotlinx.datetime.TimeZone.UTC).date
        puzzleCache.ensureLoaded(today, PUZZLE_MAX_AGE_MS, FAILED_FETCH_RETRY_MS) {
            try {
                val puzzle = (getTodayPuzzle() as? DataResult.Success)?.data
                Fetched(puzzle, succeeded = puzzle != null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                recordSafeNonFatal("home_daily_puzzle_widget_failed", e)
                Fetched(null, succeeded = false)
            }
        }
        emitAll(
            puzzleCache.entryFor(today).filterNotNull().map { entry ->
                val puzzle = entry.value
                if (puzzle == null) {
                    crashlytics.setCustomKey("puzzle_widget_unavailable_reason", "fetch_error")
                    crashlytics.log("home_daily_puzzle_widget_failed")
                    DailyPuzzleWidgetState.Unavailable
                } else {
                    // Read for the server-confirmed date so a stale/guessed-date row never counts.
                    val local = getPuzzleResultUseCase?.let { useCase ->
                        try {
                            useCase(puzzle.date)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            null
                        }
                    }
                    DailyPuzzleWidgetState.Loaded(
                        puzzleType = puzzle.type,
                        attemptsUsed = local?.attempts ?: 0,
                        solved = local?.solved ?: false,
                    )
                }
            }
        )
    }

    /**
     * Waits for any in-progress collection sync to finish before a non-essential network call, so it
     * never competes with the login-window burst. Logs `home_sync_window_deferred` only for a real wait.
     *
     * @param source `"trending"` / `"community_decks"` / `"discover_seed"` / `"daily_puzzle"` — no free text.
     */
    private suspend fun awaitSyncWindow(source: String) {
        val start = System.currentTimeMillis()
        syncManager.syncState.first { it != SyncState.SYNCING }
        val elapsed = System.currentTimeMillis() - start
        if (elapsed > SYNC_WINDOW_LOG_THRESHOLD_MS) {
            crashlytics.log("home_sync_window_deferred")
            crashlytics.setCustomKey("home_sync_window_deferred_ms", elapsed)
            crashlytics.setCustomKey("home_sync_window_deferred_source", source)
        }
    }

    private suspend fun warmTrades(userId: String) {
        val refreshed = try {
            tradesRepository.refreshProposals(userId).also { result ->
                result.exceptionOrNull()?.let { error ->
                    recordSafeNonFatal("home_trades_refresh", error)
                    crashlytics.log("home_trades_refresh_failed")
                }
            }.isSuccess
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            recordSafeNonFatal("home_trades_refresh", e)
            crashlytics.log("home_trades_refresh_failed")
            false
        }
        // refreshProposals only fetches METADATA; without item hydration the Inbox reads "0 items".
        if (refreshed) hydrateTradeItemCounts()
    }

    /**
     * Hydrates real item counts for the newest few proposal threads surfaced on Home, via the
     * item-only [TradesRepository.refreshItemsForThread] (metadata was just refreshed by the caller).
     */
    private suspend fun hydrateTradeItemCounts() {
        val cached = try {
            tradesRepository.observeAllProposals().first()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
        val rootIds = cached
            .sortedByDescending { it.updatedAt }
            .map { it.rootProposalId }
            .distinct()
            .take(HOME_TRADE_THREAD_HYDRATE_LIMIT)
        if (rootIds.isEmpty()) return
        coroutineScope {
            rootIds.map { rootId ->
                async {
                    try {
                        tradesRepository.refreshItemsForThread(rootId).exceptionOrNull()?.let { error ->
                            recordSafeNonFatal("home_trades_hydrate_items", error)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        recordSafeNonFatal("home_trades_hydrate_items", e)
                    }
                }
            }.awaitAll()
        }
    }

    /**
     * Resolves up to [HOME_TRADE_SUGGESTION_PREVIEW_LIMIT] suggestions to previews: the card from the
     * LOCAL cache only (a suggestion whose card is not cached is dropped — a thumbnail row is useless
     * without an image) and the counterparty's nickname from [friends].
     */
    private suspend fun buildSuggestionPreviews(
        suggestions: List<TradeSuggestion>,
        friends: List<Friend>,
        myUserId: String?,
    ): List<TradeSuggestionPreview> {
        if (suggestions.isEmpty()) return emptyList()
        val capped = suggestions
            .distinctBy { it.previewId() }
            .take(HOME_TRADE_SUGGESTION_PREVIEW_LIMIT)
        val cardIds = capped.map { it.cardId }.distinct()
        val cardsById = try {
            cardRepository.getCardsByIds(cardIds)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }.associateBy { it.scryfallId }
        val friendsByUserId = friends.associateBy { it.userId }
        return capped.mapNotNull { suggestion ->
            val card = cardsById[suggestion.cardId] ?: return@mapNotNull null
            val counterpartyId = if (suggestion.wishingUserId == myUserId) {
                suggestion.offeringUserId
            } else {
                suggestion.wishingUserId
            }
            val uniqueId = suggestion.previewId()
            TradeSuggestionPreview(
                id = uniqueId,
                card = DiscoverCard(
                    id = uniqueId,
                    scryfallId = card.scryfallId,
                    name = card.name,
                    imageUrl = card.imageNormal ?: card.imageArtCrop,
                    typeLine = card.typeLine,
                ),
                counterpartyName = friendsByUserId[counterpartyId]?.nickname,
            )
        }
    }

    /**
     * Chooses a random playable set with more than [MIN_DISCOVER_SET_CARDS] cards to scope the first
     * Discover fetch. Never overrides a set the user already picked; failure leaves the global query.
     */
    private suspend fun seedRandomDiscoverSet() {
        if (discoverSetTouchedByUser) return
        val chosen = try {
            withContext(ioDispatcher) {
                scryfallRemoteDataSource.getAllSets()
                    .filter { it.setType in PLAYABLE_SET_TYPES && it.cardCount > MIN_DISCOVER_SET_CARDS }
                    .randomOrNull()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            recordSafeNonFatal("home_seed_discover_set", e)
            crashlytics.log("home_seed_discover_set_failed")
            null
        }
        if (chosen != null && !discoverSetTouchedByUser) discoverSetFlow.value = chosen
    }

    /**
     * Fetches random Scryfall cards for the Discover row. Lazy unless [forceRefresh]; scoped to
     * [discoverSetFlow] when set. An empty/failed result degrades to [DiscoverLoadState.FAILED]; a
     * superseded (cancelled) fetch never writes state.
     */
    private fun fetchDiscoverCards(forceRefresh: Boolean) {
        if (!forceRefresh && discoverCardsFlow.value.isNotEmpty()) return
        fetchDiscoverJob?.cancel()
        val set = discoverSetFlow.value
        val query = if (set != null) "set:${set.code} -is:digital order:random" else DISCOVER_RANDOM_QUERY
        discoverLoadStateFlow.value = DiscoverLoadState.LOADING
        discoverCardsFlow.value = emptyList()
        fetchDiscoverJob = viewModelScope.launch {
            var error: Throwable? = null
            val result = try {
                cardRepository.searchCards(query, page = 1, bypassCache = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e
                null
            }
            // A newer fetch may have superseded this one while the repository swallowed the cancel.
            ensureActive()
            // Scryfall's CDN caches the page, so shuffle client-side or a refresh changes nothing.
            val fetched = (result as? DataResult.Success)
                ?.data
                ?.shuffled()
                ?.take(DISCOVER_CARD_COUNT)
                .orEmpty()
            if (fetched.isEmpty()) {
                // Never log the raw query (free text); only its length, the scoped set and the type.
                crashlytics.setCustomKey("home_discover_query_length", query.length)
                crashlytics.setCustomKey("home_discover_scoped_set", set?.code ?: "none")
                crashlytics.setCustomKey(
                    "home_discover_error_type",
                    error?.let { it::class.simpleName ?: "Unknown" } ?: "EmptyResult",
                )
                error?.let { recordSafeNonFatal("home_discover_fetch", it) }
                crashlytics.log("home_discover_fetch_failed: query_length=${query.length}")
                discoverLoadStateFlow.value = DiscoverLoadState.FAILED
                return@launch
            }
            // A repeated scryfallId would break the LazyRow's stable-key contract.
            val mappedCards = fetched.distinctBy { it.scryfallId }.map { card ->
                DiscoverCard(
                    id = card.scryfallId,
                    scryfallId = card.scryfallId,
                    name = card.name,
                    imageUrl = card.imageNormal ?: card.imageArtCrop,
                    typeLine = card.typeLine,
                )
            }
            discoverCardsFlow.value = mappedCards
            discoverLoadStateFlow.value = DiscoverLoadState.LOADED
        }
    }

    /**
     * Fetches a fresh random card for the Random card widget. A failure keeps the previous card; a
     * superseded (cancelled) fetch never writes state.
     */
    private fun fetchRandomCard() {
        fetchRandomCardJob?.cancel()
        randomCardLoadStateFlow.value = DiscoverLoadState.LOADING
        fetchRandomCardJob = viewModelScope.launch {
            var error: Throwable? = null
            val result = try {
                cardRepository.getRandomCard(CARD_OF_THE_DAY_QUERY)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e
                null
            }
            ensureActive()
            val card = (result as? DataResult.Success)?.data?.let { c ->
                DiscoverCard(
                    id = c.scryfallId,
                    scryfallId = c.scryfallId,
                    name = c.name,
                    imageUrl = c.imageNormal ?: c.imageArtCrop,
                    typeLine = c.typeLine,
                )
            }
            if (card != null) {
                randomCardFlow.value = card
                randomCardLoadStateFlow.value = DiscoverLoadState.LOADED
            } else {
                crashlytics.setCustomKey(
                    "home_random_card_error_type",
                    when {
                        error != null -> error::class.simpleName ?: "Unknown"
                        result is DataResult.Error -> "DataResultError"
                        else -> "EmptyResult"
                    },
                )
                error?.let { recordSafeNonFatal("home_random_card_fetch", it) }
                crashlytics.log("home_random_card_fetch_failed")
                randomCardLoadStateFlow.value = DiscoverLoadState.FAILED
            }
        }
    }

    /** Scopes the Discover row to [set] (null clears the filter) and re-fetches it. */
    private fun selectDiscoverSet(set: MagicSet?) {
        // Set code is a public Scryfall identifier (not PII).
        crashlytics.log("home_discover_set_selected: ${set?.code ?: "cleared"}")
        discoverSetTouchedByUser = true
        discoverSetFlow.value = set
        fetchDiscoverCards(forceRefresh = true)
    }

    /** Persists the Home COMMUNITY_DECKS widget's category selection. */
    private fun selectCommunityDecksCategory(category: HomeCommunityDeckCategory) {
        crashlytics.log("home_community_decks_category_selected: ${category.persistedId}")
        persistPreference("home_community_decks_category") {
            userPrefsDataStore.saveHomeCommunityDecksCategory(category.persistedId)
        }
    }

    /** Persists the Home COMMUNITY_DECKS widget's format filter (null = every format). */
    private fun selectCommunityDecksFormat(format: CommunityDeckFormatFilter?) {
        crashlytics.log("home_community_decks_format_selected: ${format?.name ?: "all"}")
        persistPreference("home_community_decks_format") {
            userPrefsDataStore.saveHomeCommunityDecksFormat(format?.name)
        }
    }

    private fun rollRulesTip() {
        crashlytics.log("home_rules_tip_rolled")
        rulesTipIndex.value = rollRulesTipIndex(rulesTipIndex.value)
    }

    /** Runs a DataStore write; a failing write is reported instead of crashing the ViewModel scope. */
    private fun persistPreference(source: String, write: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                write()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                recordSafeNonFatal("home_pref_write_$source", e)
            }
        }
    }

    // ── Public intents ──────────────────────────────────────────────────────────

    /** Routes a board-mutating [HomeAction]; navigation actions are handled by the caller. */
    fun onAction(action: HomeAction) {
        when (action) {
            is HomeAction.MoveWidget -> moveWidget(action.from, action.to)
            is HomeAction.UpdateLayout -> replaceLayout(action.layout)
            is HomeAction.AddWidget -> addWidget(action.type)
            is HomeAction.RemoveWidget -> removeWidget(action.type)
            HomeAction.ResetLayout -> resetLayout()
            is HomeAction.SkipFirstStep -> skipFirstStep(action.stepId)
            HomeAction.RetryDiscover -> {
                crashlytics.log("home_discover_retry_after_failure")
                fetchDiscoverCards(forceRefresh = true)
            }

            HomeAction.RefreshDiscover -> {
                crashlytics.log("home_discover_refresh_manual")
                fetchDiscoverCards(forceRefresh = true)
            }

            HomeAction.RefreshRandomCard -> {
                crashlytics.log("home_random_card_refresh_manual")
                fetchRandomCard()
            }

            is HomeAction.SelectDiscoverSet -> selectDiscoverSet(action.set)
            is HomeAction.SelectCommunityDecksCategory -> selectCommunityDecksCategory(action.category)
            is HomeAction.SelectCommunityDecksFormat -> selectCommunityDecksFormat(action.format)
            HomeAction.RollRulesTip -> rollRulesTip()
            HomeAction.RateApp -> Unit // the UI handles the store deep link
            else -> Unit // navigation intents are resolved by AppNavGraph
        }
    }

    /** Persists a newly chosen set of exactly four Quick Start actions. */
    fun saveQuickStartActions(actions: List<QuickStartAction>) {
        crashlytics.log("home_quick_start_saved: ${actions.joinToString(",") { it.name }}")
        persistPreference("quick_start") { userPrefsDataStore.saveQuickStartActions(actions) }
    }

    /** Persists [stepId] as skipped; the First Steps carousel drops it on the next emission. */
    fun skipFirstStep(stepId: String) {
        crashlytics.log("home_first_step_skipped: $stepId")
        persistPreference("first_step_skip") { userPrefsDataStore.skipFirstStep(stepId) }
    }

    /** Dismisses the current account nudge, starting its 48-hour cooldown. */
    fun dismissAccountNudge() {
        val trigger = uiState.value.accountNudge?.trigger?.name ?: "unknown"
        crashlytics.setCustomKey("home_nudge_trigger", trigger)
        crashlytics.log("home_nudge_dismissed: $trigger")
        actionRequiredMessage.value = null
        persistPreference("nudge_dismiss") { userPrefsDataStore.dismissAccountNudge() }
    }

    /** Raises a high-priority ACTION_REQUIRED nudge. */
    fun triggerActionRequiredNudge(message: String) {
        // Never log the message text (user-facing, may carry PII).
        crashlytics.log("home_nudge_action_required_triggered")
        actionRequiredMessage.value = message
    }

    // ── Layout mutations ──────────────────────────────────────────────────────
    //
    // Every mutation is a transform applied INSIDE the DataStore edit to the currently stored
    // layout, serialized by [layoutMutex]: two fast taps can never both start from one stale snapshot.

    private fun mutateLayout(transform: (List<WidgetInstance>) -> List<WidgetInstance>) {
        viewModelScope.launch {
            try {
                layoutMutex.withLock {
                    val default = defaultLayoutFor(authGate.value is AuthGate.SignedIn)
                    userPrefsDataStore.updateHomeLayout(default.map { it.toPersisted() }) { stored ->
                        transform(stored.toInstancesWithMigration())
                            .distinctBy { it.type.persistedId }
                            .map { it.toPersisted() }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                recordSafeNonFatal("home_layout_write", e)
                crashlytics.log("home_layout_write_failed")
            }
        }
    }

    private fun moveWidget(from: Int, to: Int) {
        crashlytics.log("home_widget_moved: from=$from to=$to")
        if (from == to) return
        mutateLayout { current ->
            if (from !in current.indices || to !in current.indices) return@mutateLayout current
            current.toMutableList().apply { add(to, removeAt(from)) }
        }
    }

    private fun addWidget(type: HomeWidgetType) {
        crashlytics.log("home_widget_added: ${type.persistedId}")
        mutateLayout { current -> current.withWidgetAdded(type) }
    }

    private fun removeWidget(type: HomeWidgetType) {
        crashlytics.log("home_widget_removed: ${type.persistedId}")
        if (type.isAlwaysPresent) return
        mutateLayout { current -> current.filterNot { it.type == type } }
    }

    private fun replaceLayout(layout: List<WidgetInstance>) {
        mutateLayout { layout }
    }

    private fun resetLayout() {
        crashlytics.setCustomKey("home_layout_widget_count_before_reset", uiState.value.layout.size)
        crashlytics.log("home_layout_reset")
        mutateLayout { defaultLayoutFor(authGate.value is AuthGate.SignedIn) }
    }

    // ── Reduction ─────────────────────────────────────────────────────────────

    private fun buildUiState(core: CoreSnapshot, recentNews: List<NewsItem>?, data: DataBundle): HomeUiState {
        val gate = core.account.gate
        val signedIn = gate is AuthGate.SignedIn
        val summary = core.library.summary
        val decks = core.library.decks
        val prefs = core.prefs
        val social = data.social
        // Null until known, so the greeting never flips from the signed-out copy to a name.
        val playerName = core.account.nickname ?: prefs?.playerName
        val boardReady = data.layout != null && gate != AuthGate.Unknown

        val libraryStats = summary?.let {
            LibraryStats(
                totalCards = it.totalCards,
                uniqueCards = it.uniqueCards,
                deckCount = decks?.size ?: 0,
                estimatedValueDisplay = PriceFormatter.formatFromScryfall(
                    priceUsd = it.totalValueUsd,
                    priceEur = it.totalValueEur,
                    preferredCurrency = core.library.currency,
                ),
            )
        }

        // First steps are computed only once every input is certain, so the hero never flashes a
        // step (e.g. "Create account") that the next emission removes.
        val friendActivity = social.friendActivity
        val openForTrade = social.trades.openForTrade
        val wishlist = social.wishlist
        val visibleSteps = if (
            gate != AuthGate.Unknown && summary != null && decks != null && prefs != null &&
            core.account.prefs != null && friendActivity != null && openForTrade != null &&
            wishlist != null
        ) {
            val effectiveName = core.account.nickname ?: prefs.playerName
            buildVisibleSteps(
                isAuthenticated = signedIn,
                cardCount = summary.uniqueCards,
                deckCount = decks.size,
                friendCount = friendActivity.friendCount,
                isProfileComplete = !core.account.avatarUrl.isNullOrBlank() &&
                    effectiveName != DEFAULT_PLAYER_NAME && effectiveName.isNotBlank(),
                totalPlaytestCount = prefs.totalPlaytestCount,
                openForTradeCount = openForTrade.count,
                wishlistCount = if (signedIn) wishlist.count else 0,
                skipped = prefs.skippedFirstSteps,
            )
        } else {
            null
        }
        val hero = visibleSteps?.let { resolveHero(it) } ?: HomeHeroState.Loading

        val activity = core.activity
        val accountPrefs = core.account.prefs
        val nudgeResolved = gate != AuthGate.Unknown && summary != null && decks != null &&
            activity != null && accountPrefs != null
        val nudge = if (
            gate != AuthGate.Unknown && summary != null && decks != null &&
            activity != null && accountPrefs != null
        ) {
            resolveNudge(
                isAuthenticated = signedIn,
                isCoolingDown = accountPrefs.isCoolingDown,
                actionRequiredMessage = core.account.actionRequiredMessage,
                uniqueCards = summary.uniqueCards,
                deckCount = decks.size,
                totalGames = activity.totalGames,
            )
        } else {
            null
        }

        // Persist the completion flag exactly once, the first time the hero resolves to the empty
        // completion card while it hasn't been seen before.
        if (
            prefs != null && hero is HomeHeroState.Welcome && hero.steps.isEmpty() &&
            !prefs.firstStepsCompletionSeen && !firstStepsCompletionMarkSeenDispatched
        ) {
            firstStepsCompletionMarkSeenDispatched = true
            crashlytics.log("home_first_steps_completion_seen")
            persistPreference("first_steps_completion") { userPrefsDataStore.markFirstStepsCompletionSeen() }
        }

        if (boardReady && !sessionContextKeysSet) {
            crashlytics.setCustomKey("home_is_authenticated", signedIn)
            crashlytics.setCustomKey("home_hero_type", hero::class.simpleName ?: "Unknown")
            sessionContextKeysSet = true
        }
        data.layout?.let { crashlytics.setCustomKey("home_layout_widget_count", it.size) }

        val stats = data.gameStats.stats
        val tournament = data.gameStats.tournament
        val gamification = data.gamification
        val proposals = social.trades.proposals

        return HomeUiState(
            boardReady = boardReady,
            auth = gate,
            hero = hero,
            firstStepsCompletionSeen = prefs?.firstStepsCompletionSeen,
            quickStartActions = prefs?.quickStart ?: QuickStartAction.defaults,
            quickStartLoaded = prefs != null,
            libraryStats = libraryStats,
            recentNews = recentNews,
            accountNudge = nudge,
            accountNudgeResolved = nudgeResolved,
            playerName = playerName,
            avatarUrl = core.account.avatarUrl,
            layout = data.layout.orEmpty(),
            lastGameRecap = stats?.history?.firstOrNull()?.toRecap(),
            playStreak = stats?.history?.toPlayStreak(),
            winRate = stats?.let { toWinRate(it) },
            bestDeck = stats?.let { toBestDeck(it, decks.orEmpty()) },
            nemesis = stats?.let { toNemesis(it) },
            performanceDetails = stats?.performance,
            gameStatsLoaded = stats != null && tournament != null,
            collectionByColor = summary?.byColor?.toColorMap().orEmpty(),
            collectionByRarity = summary?.byRarity?.toRarityMap().orEmpty(),
            discoverCards = data.discover.discoverCards,
            cardOfTheDay = data.discover.randomCard,
            discoverLoadState = data.discover.loadState,
            randomCardLoadState = data.discover.randomCardLoadState,
            discoverSetCode = data.discover.selectedSet?.code,
            discoverSet = data.discover.selectedSet,
            latestSets = data.discover.latestSets,
            wishlistStats = if (signedIn) wishlist else null,
            decks = decks,
            recentlyAdded = data.recentlyAdded,
            tradeSummary = proposals?.summary,
            tradeSuggestionPreviews = social.trades.suggestionPreviews,
            openForTradePreview = openForTrade?.let {
                OpenForTradePreview(count = it.count, valueDisplay = it.valueDisplay, cards = it.cards)
            },
            activeTournamentSummary = tournament?.summary,
            friendCount = friendActivity?.friendCount ?: 0,
            latestFriendRequestName = friendActivity?.latestFriendRequestName,
            friends = social.friends,
            recentTrades = proposals?.recent,
            gamificationEnabled = gamification?.enabled ?: false,
            gamification = gamification?.data,
            gamificationLoaded = gamification != null,
        )
    }

    // ── Stats → widget model mappers ────────────────────────────────────────────

    private fun toWinRate(stats: StatsSnapshot): WinRateStats? {
        val total = stats.history.size
        if (total == 0) return null
        return WinRateStats(
            wins = stats.localWins,
            totalGames = total,
            recentResults = stats.history.take(WIN_SPARK_COUNT).map { it.localIsWinner },
        )
    }

    /** [DeckStats] carries no colors, so the identity is joined by id from the loaded decks. */
    private fun toBestDeck(stats: StatsSnapshot, decks: List<DeckSummary>): BestDeckStats? {
        val best = stats.deckStats
            .filter { it.totalGames > 0 && !it.deckName.isNullOrBlank() }
            .maxByOrNull { row ->
                // Rank by win rate, breaking ties by total games played.
                (row.wins.toDouble() / row.totalGames) * 1000 + row.totalGames
            } ?: return null
        val colorIdentity = decks.firstOrNull { it.id == best.deckId }?.colorIdentity ?: emptySet()
        return BestDeckStats(
            deckId = best.deckId,
            deckName = best.deckName ?: "",
            wins = best.wins,
            losses = (best.totalGames - best.wins).coerceAtLeast(0),
            colorIdentity = colorIdentity,
        )
    }

    private fun toNemesis(stats: StatsSnapshot): NemesisStats? {
        val elimination = stats.nemesis ?: return null
        val totalLosses = stats.history.count { !it.localIsWinner }
        return NemesisStats(
            archetype = elimination.eliminationReason,
            count = elimination.count,
            totalLosses = totalLosses,
        )
    }

    /** Folds progression + quest board + streak into the compact Home gamification snapshot. */
    private fun toHomeGamification(
        progression: PlayerProgression,
        board: QuestBoard,
        streak: StreakUiModel,
    ): HomeGamification {
        val all = board.daily + board.weekly
        val claimable = all.filter { it.isClaimable }
        // Preview = claimable first, then in-progress (not yet claimed). Cap at 3.
        val preview = (claimable + all.filterNot { it.isClaimable || it.isClaimed })
            .take(HOME_QUEST_PREVIEW_LIMIT)
            .map { it.toHomeQuest() }
        return HomeGamification(
            level = progression.level,
            xpIntoLevel = progression.xpIntoLevel,
            xpForNextLevel = progression.xpForNextLevel,
            streak = streak.current,
            dailyDone = board.daily.count { it.isClaimed || it.isClaimable },
            dailyTotal = board.daily.size,
            claimableCount = claimable.size,
            topQuests = preview,
        )
    }

    /**
     * Filters [ALL_FIRST_STEPS] to the steps not dismissed via [skipped] that meet their show
     * condition, preserving the canonical activation-funnel order. Each condition is documented as
     * DATA-DRIVEN or DISMISS-ONLY alongside its [FirstStepItem] declaration.
     */
    private fun buildVisibleSteps(
        isAuthenticated: Boolean,
        cardCount: Int,
        deckCount: Int,
        friendCount: Int,
        isProfileComplete: Boolean,
        totalPlaytestCount: Int,
        openForTradeCount: Int,
        wishlistCount: Int,
        skipped: Set<String>,
    ): List<FirstStepItem> = ALL_FIRST_STEPS.filter { step ->
        if (step.id in skipped) return@filter false
        when (step.id) {
            STEP_FIRST_ADD_CARD -> cardCount == 0
            STEP_FIRST_SCAN_CARD -> true
            STEP_FIRST_CREATE_DECK -> deckCount == 0
            STEP_FIRST_PLAY_GAME -> true
            STEP_FIRST_PLAYTEST_DECK -> deckCount > 0 && totalPlaytestCount == 0
            STEP_FIRST_CREATE_ACCOUNT -> !isAuthenticated
            STEP_FIRST_COMPLETE_PROFILE -> isAuthenticated && !isProfileComplete
            STEP_FIRST_ADD_FRIEND -> isAuthenticated && friendCount == 0
            STEP_FIRST_REVIEW_FRIEND -> isAuthenticated && friendCount > 0
            STEP_FIRST_CREATE_TRADE -> isAuthenticated && friendCount > 0 && cardCount > 0
            STEP_FIRST_OPEN_FOR_TRADE -> cardCount > 0 && openForTradeCount == 0
            // The wishlist is account-gated: without the auth gate this step could never auto-hide
            // for a signed-out user.
            STEP_FIRST_ADD_WISHLIST -> isAuthenticated && wishlistCount == 0
            STEP_FIRST_COLLECTION_STATS -> cardCount > 0
            STEP_FIRST_DRAFT_GUIDE -> true
            STEP_FIRST_NEWS -> true
            STEP_FIRST_PREFERENCES -> true
            STEP_FIRST_RATE_APP -> true
            else -> false
        }
    }

    /**
     * The hero is pinned to the First Steps welcome (product decision: only Welcome and Loading are
     * shown). Non-empty [visibleSteps] shows the carousel; empty shows the one-time completion card.
     */
    private fun resolveHero(visibleSteps: List<FirstStepItem>): HomeHeroState =
        HomeHeroState.Welcome(steps = visibleSteps)

    private fun resolveNudge(
        isAuthenticated: Boolean,
        isCoolingDown: Boolean,
        actionRequiredMessage: String?,
        uniqueCards: Int,
        deckCount: Int,
        totalGames: Int,
    ): AccountNudge? {
        val trigger = getAccountNudgeUseCase(
            isAuthenticated = isAuthenticated,
            isCoolingDown = isCoolingDown,
            actionRequired = actionRequiredMessage,
            uniqueCards = uniqueCards,
            deckCount = deckCount,
            totalGames = totalGames,
        ) ?: return null

        return when (trigger) {
            NudgeTrigger.ACTION_REQUIRED ->
                AccountNudge(message = actionRequiredMessage, trigger = trigger)

            NudgeTrigger.COLLECTION_MILESTONE ->
                AccountNudge(messageRes = R.string.home_nudge_collection, trigger = trigger)

            NudgeTrigger.DECK_MILESTONE ->
                AccountNudge(messageRes = R.string.home_nudge_decks, trigger = trigger)

            NudgeTrigger.GAME_MILESTONE ->
                AccountNudge(messageRes = R.string.home_nudge_games, trigger = trigger)

            NudgeTrigger.SYNC_PENDING ->
                AccountNudge(messageRes = R.string.home_nudge_games, trigger = trigger)
        }
    }

    // ── Default layouts ──────────────────────────────────────────────────────

    private fun defaultLayoutFor(authenticated: Boolean): List<WidgetInstance> =
        if (authenticated) DEFAULT_LAYOUT_SIGNED_IN else DEFAULT_LAYOUT_SIGNED_OUT

    // ── Internal snapshots ──────────────────────────────────────────────────────

    private data class CoreSnapshot(
        val library: LibrarySnapshot,
        val activity: ActivitySnapshot?,
        val account: AccountSnapshot,
        val prefs: PrefsSnapshot?,
    )

    private data class LibrarySnapshot(
        val summary: CollectionSummary?,
        val decks: List<DeckSummary>?,
        val currency: PreferredCurrency,
    )

    private data class ActivitySnapshot(
        val totalGames: Int,
        val activeDraft: DraftState?,
        val activeTournaments: Int,
    )

    private data class AccountSnapshot(
        val gate: AuthGate,
        val prefs: AccountPrefs?,
        val actionRequiredMessage: String?,
        val avatarUrl: String?,
        val nickname: String?,
    )

    private data class GameStatsBundle(
        val stats: StatsSnapshot?,
        val tournament: TournamentSlot?,
    )

    private data class DataBundle(
        val layout: List<WidgetInstance>?,
        val gameStats: GameStatsBundle,
        val discover: DiscoverSnapshot,
        val social: SocialSnapshot,
        val gamification: GamificationSnapshot?,
        val recentlyAdded: List<RecentlyAddedCard>?,
    )

    /** [enabled] mirrors the master toggle; [data] is null when disabled or on failure. */
    private data class GamificationSnapshot(
        val enabled: Boolean,
        val data: HomeGamification?,
    )

    private data class StatsSnapshot(
        val localWins: Int,
        val history: List<SessionHistoryEntry>,
        val deckStats: List<DeckStats>,
        val nemesis: EliminationStats?,
        val performance: PerformanceDetails,
    )

    private data class DiscoverRow(
        val selectedSet: MagicSet?,
        val cards: List<DiscoverCard>,
        val loadState: DiscoverLoadState,
    )

    private data class DiscoverSnapshot(
        val latestSets: List<DraftSet>?,
        val discoverCards: List<DiscoverCard> = emptyList(),
        val loadState: DiscoverLoadState = DiscoverLoadState.LOADING,
        val selectedSet: MagicSet? = null,
        val randomCard: DiscoverCard? = null,
        val randomCardLoadState: DiscoverLoadState = DiscoverLoadState.LOADING,
    )

    private data class TradesSnapshot(
        val proposals: ProposalsView?,
        val suggestionPreviews: List<TradeSuggestionPreview>?,
        val openForTrade: OpenForTradeSummary?,
    )

    private data class SocialSnapshot(
        val trades: TradesSnapshot,
        val wishlist: WishlistStats?,
        val friends: List<Friend>?,
        val friendActivity: FriendActivity?,
    )

    // Declared after every property: with Dispatchers.Main.immediate these launches run during
    // construction, so anything they touch must already be initialized.
    init {
        authGate
            .onEach { gate -> if (gate is AuthGate.SignedIn) actionRequiredMessage.value = null }
            .launchIn(viewModelScope)
        // Background news refresh so the widget has data before the News tab is ever opened; the
        // refresh itself skips sources fetched within the last hour.
        viewModelScope.launch {
            try {
                refreshNewsFeedUseCase().exceptionOrNull()?.let { error ->
                    recordSafeNonFatal("home_news_refresh", error)
                    crashlytics.log("home_news_refresh_failed")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                recordSafeNonFatal("home_news_refresh", e)
                crashlytics.log("home_news_refresh_failed")
            }
        }
        // Trades warm-up: observeActiveProposals() is in-memory and starts empty on every process
        // start. Runs for each newly signed-in user (not only the first session state) and only
        // while TRADES_HUB is on the board.
        viewModelScope.launch {
            combine(signedInUserIdFlow, isOnBoard(HomeWidgetType.TRADES_HUB)) { userId, onBoard ->
                userId.takeIf { onBoard }
            }
                .distinctUntilChanged()
                .collectLatest { userId -> if (userId != null) warmTrades(userId) }
        }
        // Seed a random playable set before the first Discover fetch, then load the Discover row and
        // the independent Random card, after any in-progress sync (login-window serialization).
        viewModelScope.launch {
            awaitSyncWindow("discover_seed")
            seedRandomDiscoverSet()
            if (!discoverSetTouchedByUser) fetchDiscoverCards(forceRefresh = false)
            fetchRandomCard()
        }
    }

    companion object {
        /** Maximum number of news items shown on the Home dashboard widget. */
        const val MAX_NEWS = 10

        /** How long a stopped subscriber keeps upstream flows alive (config changes, quick returns). */
        private const val STOP_TIMEOUT_MS = 5_000L

        private const val HISTORY_LIMIT = 60
        private const val LATEST_SETS_LIMIT = 8
        private const val WIN_SPARK_COUNT = 5
        private const val DAY_MS = 24L * 60L * 60L * 1000L
        private const val RECENT_TRADES_LIMIT = 3
        private const val DEFAULT_PLAYER_NAME = "Wizard"

        /**
         * Scryfall query for the Discover row. A bare `order:random` is rejected by Scryfall, so it
         * carries a concrete predicate (real, non-digital, Commander-legal cards).
         */
        private const val DISCOVER_RANDOM_QUERY = "-is:digital legal:commander order:random"

        private const val CARD_OF_THE_DAY_QUERY = "lang:en"

        private const val DISCOVER_CARD_COUNT = 10

        /** Minimum card count for a set to be eligible as the default random Discover scope. */
        private const val MIN_DISCOVER_SET_CARDS = 10

        private const val WISHLIST_PREVIEW_LIMIT = 10

        /** [awaitSyncWindow] logs only waits longer than this (an idle sync still costs a dispatch). */
        private const val SYNC_WINDOW_LOG_THRESHOLD_MS = 100L

        private const val RECENTLY_ADDED_LIMIT = 10
        private const val HOME_QUEST_PREVIEW_LIMIT = 3

        /** Max proposal threads hydrated with real items on the trades warm-up. */
        private const val HOME_TRADE_THREAD_HYDRATE_LIMIT = 5

        private const val HOME_TRADE_SUGGESTION_PREVIEW_LIMIT = 6
        private const val HOME_OPEN_FOR_TRADE_PREVIEW_LIMIT = 8
        private const val HOME_COMMUNITY_DECKS_LIMIT = 10

        private const val LATEST_SETS_MAX_AGE_MS = 6L * 60L * 60L * 1000L
        private const val TRENDING_MAX_AGE_MS = 60L * 60L * 1000L
        private const val COMMUNITY_DECKS_MAX_AGE_MS = 30L * 60L * 1000L
        private const val SUGGESTIONS_MAX_AGE_MS = 10L * 60L * 1000L
        private const val PUZZLE_MAX_AGE_MS = 30L * 60L * 1000L

        /** A failed one-shot is retried on the next resubscription after this long. */
        private const val FAILED_FETCH_RETRY_MS = 60L * 1000L

        private val EMPTY_COLLECTION_SUMMARY = CollectionSummary(
            totalCards = 0,
            uniqueCards = 0,
            totalValueUsd = 0.0,
            totalValueEur = 0.0,
            byColor = emptyMap(),
            byRarity = emptyMap(),
        )

        private val NO_FRIEND_ACTIVITY = FriendActivity(friendCount = 0, latestFriendRequestName = null)
        private val NO_PROPOSALS = ProposalsView(summary = null, recent = emptyList())
        private val NO_OPEN_FOR_TRADE = OpenForTradeSummary(count = 0, valueDisplay = null)
        private val EMPTY_WISHLIST = WishlistStats(count = 0, estimatedValueDisplay = "", cards = emptySet())

        /**
         * Default widget layouts, applied ONLY when nothing has been persisted yet. Signed-out leads with
         * what works without an account; signed-in with the user's own data. Gallery-only widgets
         * (wishlist, gamification, trending, puzzle, competitive...) keep the board short.
         */
        private val DEFAULT_LAYOUT_SIGNED_OUT = listOf(
            WidgetInstance(HomeWidgetType.CONTEXT_HERO, WidgetSize.MEDIUM),
            WidgetInstance(HomeWidgetType.QUICK_ACTIONS, WidgetSize.MEDIUM),
            WidgetInstance(HomeWidgetType.COLLECTION_STATS_HUB, WidgetSize.MEDIUM),
            WidgetInstance(HomeWidgetType.COMMUNITY_DECKS, WidgetSize.MEDIUM),
            WidgetInstance(HomeWidgetType.CARD_OF_THE_DAY, WidgetSize.MEDIUM),
            WidgetInstance(HomeWidgetType.DISCOVER_CARDS, WidgetSize.MEDIUM),
            WidgetInstance(HomeWidgetType.LATEST_SETS, WidgetSize.MEDIUM),
            WidgetInstance(HomeWidgetType.RULES_TIP, WidgetSize.MEDIUM),
            WidgetInstance(HomeWidgetType.MTG_NEWS, WidgetSize.MEDIUM),
        )

        private val DEFAULT_LAYOUT_SIGNED_IN = listOf(
            WidgetInstance(HomeWidgetType.CONTEXT_HERO, WidgetSize.MEDIUM),
            WidgetInstance(HomeWidgetType.QUICK_ACTIONS, WidgetSize.MEDIUM),
            WidgetInstance(HomeWidgetType.COMMUNITY_DECKS, WidgetSize.MEDIUM),
            WidgetInstance(HomeWidgetType.YOUR_DECKS_SHELF, WidgetSize.MEDIUM),
            WidgetInstance(HomeWidgetType.COLLECTION_STATS_HUB, WidgetSize.MEDIUM),
            WidgetInstance(HomeWidgetType.RECENTLY_ADDED, WidgetSize.MEDIUM),
            WidgetInstance(HomeWidgetType.LATEST_SETS, WidgetSize.MEDIUM),
            WidgetInstance(HomeWidgetType.MTG_NEWS, WidgetSize.MEDIUM),
            WidgetInstance(HomeWidgetType.RULES_TIP, WidgetSize.MEDIUM),
            WidgetInstance(HomeWidgetType.DISCOVER_CARDS, WidgetSize.MEDIUM),
            WidgetInstance(HomeWidgetType.CARD_OF_THE_DAY, WidgetSize.MEDIUM),
        )

        /** Level-1 default progression used when the progression flow errors. */
        private val DEFAULT_PROGRESSION = PlayerProgression(
            totalXp = 0L,
            level = 1,
            xpIntoLevel = 0L,
            xpForNextLevel = 0L,
            updatedAt = kotlinx.datetime.Instant.fromEpochMilliseconds(0L),
        )

        /** Zeroed streak used when the streak flow errors. */
        private val DEFAULT_STREAK = StreakUiModel(current = 0, longest = 0, freezeTokens = 0)

        /** Pending proposals addressed TO [userId]; the newest one's item count drives the preview. */
        private fun tradeSummaryFor(proposals: List<TradeProposal>, userId: String): TradeSummary {
            val pending = proposals.filter { it.status == TradeStatus.PROPOSED && it.receiverId == userId }
            if (pending.isEmpty()) return TradeSummary(pendingCount = 0, latestItemCount = null)
            val newest = pending.maxByOrNull { it.createdAt }
            return TradeSummary(pendingCount = pending.size, latestItemCount = newest?.items?.size)
        }

        /** Resolves a persisted `CommunityDeckFormatFilter` name; unknown or absent = every format. */
        private fun parseCommunityDecksFormat(name: String?): CommunityDeckFormatFilter? =
            CommunityDeckFormatFilter.entries.firstOrNull { it.name == name }
    }
}

/** Tournaments in ACTIVE or SETUP count as ongoing for the Home board. */
private fun Tournament.isOngoing(): Boolean = status == "ACTIVE" || status == "SETUP"

/** Stable id of a trade suggestion match (also its preview's lazy key). */
private fun TradeSuggestion.previewId(): String =
    "$offeringUserId|$wishingUserId|$cardId|$userCardId"

/**
 * Inserts [type] right after the LAST entry of its category wherever that run sits, or appends when
 * the category is absent. Ordinal comparison would be wrong: the layout is not sorted by ordinal, and
 * the gallery's drag and drop relies on each category staying contiguous. No-op when already placed.
 */
internal fun List<WidgetInstance>.withWidgetAdded(type: HomeWidgetType): List<WidgetInstance> {
    if (any { it.type == type }) return this
    val lastSameCategoryIndex = indexOfLast { it.type.category == type.category }
    val insertIndex = if (lastSameCategoryIndex >= 0) lastSameCategoryIndex + 1 else size
    return toMutableList().apply { add(insertIndex, WidgetInstance(type, type.defaultSize())) }
}

/** Projects a [QuestUiModel] down to the compact [HomeQuest] preview model. */
private fun QuestUiModel.toHomeQuest(): HomeQuest = HomeQuest(
    instanceId = instanceId,
    title = title,
    emoji = emoji,
    progress = progress,
    target = target,
    isClaimable = isClaimable,
)

// ─────────────────────────────────────────────────────────────────────────────
//  Mapping helpers (top-level, pure)
// ─────────────────────────────────────────────────────────────────────────────

// Mapping helper removed — Home now uses rich NewsItem directly.

private fun SessionHistoryEntry.toRecap(): LastGameRecap = LastGameRecap(
    won = localIsWinner,
    deckName = localDeckName,
    mode = mode,
    durationMs = durationMs,
    opponentCount = opponentCount,
)

/** Streak from most-recent-first history: counts the leading run of wins; longest run anywhere. */
private fun List<SessionHistoryEntry>.toPlayStreak(): PlayStreak? {
    if (isEmpty()) return null
    val current = takeWhile { it.localIsWinner }.size
    var longest = 0
    var run = 0
    for (row in this) {
        if (row.localIsWinner) {
            run++
            longest = maxOf(longest, run)
        } else {
            run = 0
        }
    }
    return PlayStreak(current = current, longest = longest, isWinStreak = true)
}

/** Stable WUBRG-ordered string keys for the collection-by-color widget. */
private fun Map<MtgColor, Int>.toColorMap(): Map<String, Int> =
    buildMap {
        // Preserve WUBRG order; include colorless (token 'C').
        listOf(MtgColor.W, MtgColor.U, MtgColor.B, MtgColor.R, MtgColor.G).forEach { color ->
            put(color.name, this@toColorMap[color] ?: 0)
        }
        put("C", this@toColorMap[MtgColor.COLORLESS] ?: 0)
    }

/** Rarity counts keyed by canonical rarity name for the collection-by-rarity widget. */
private fun Map<Rarity, Int>.toRarityMap(): Map<String, Int> =
    buildMap {
        listOf(Rarity.COMMON, Rarity.UNCOMMON, Rarity.RARE, Rarity.MYTHIC).forEach { rarity ->
            put(rarity.name, this@toRarityMap[rarity] ?: 0)
        }
    }

/** The size used when a widget is added from the gallery: MEDIUM if supported, else its first size. */
private fun HomeWidgetType.defaultSize(): WidgetSize =
    when {
        WidgetSize.MEDIUM in supportedSizes -> WidgetSize.MEDIUM
        WidgetSize.SMALL in supportedSizes -> WidgetSize.SMALL
        else -> supportedSizes.first()
    }

/**
 * Load state for the Home Discover / Card-of-the-day slice.
 *
 * [LOADING] → show a spinner; [LOADED] → show the cards; [FAILED] → show a retry
 * affordance instead of an endless spinner (a bare `order:random` Scryfall query, or any
 * network failure, would otherwise leave the widgets spinning forever).
 */
enum class DiscoverLoadState { LOADING, LOADED, FAILED }

