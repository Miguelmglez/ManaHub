package com.mmg.manahub.feature.home.presentation

// Step ID constants are top-level in FirstStepItem.kt (same package — no import needed).
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.R
import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.data.local.dao.LocalSessionHistoryRow
import com.mmg.manahub.core.data.remote.ScryfallRemoteDataSource
import com.mmg.manahub.core.model.CollectionStats
import com.mmg.manahub.core.model.CommunityStats
import com.mmg.manahub.core.model.DeckSummary
import com.mmg.manahub.core.model.DraftSet
import com.mmg.manahub.core.model.MagicSet
import com.mmg.manahub.core.model.MtgColor
import com.mmg.manahub.core.model.PLAYABLE_SET_TYPES
import com.mmg.manahub.core.model.PreferredCurrency
import com.mmg.manahub.core.model.QuickStartAction
import com.mmg.manahub.core.model.Rarity
import com.mmg.manahub.core.model.WidgetSize
import com.mmg.manahub.core.model.news.NewsFilterPrefs
import com.mmg.manahub.core.model.news.NewsItem
import com.mmg.manahub.core.model.news.SourceType
import com.mmg.manahub.core.domain.repository.CommunityStatsRepository
import com.mmg.manahub.core.domain.repository.DeckRepository
import com.mmg.manahub.feature.game.domain.repository.GameSessionRepository
import com.mmg.manahub.core.domain.repository.StatsRepository
import com.mmg.manahub.feature.tournament.domain.repository.TournamentRepository
import com.mmg.manahub.core.gamification.domain.model.PlayerProgression
import com.mmg.manahub.core.gamification.domain.model.QuestBoard
import com.mmg.manahub.core.gamification.domain.model.QuestUiModel
import com.mmg.manahub.core.gamification.domain.model.StreakUiModel
import com.mmg.manahub.core.gamification.domain.repository.GamificationRepository
import com.mmg.manahub.core.util.PriceFormatter
import com.mmg.manahub.core.util.recordSafeNonFatal
import com.mmg.manahub.core.domain.auth.SessionState
import com.mmg.manahub.core.domain.auth.AuthRepository
import com.mmg.manahub.core.model.DraftState
import com.mmg.manahub.core.model.DraftStatus
import com.mmg.manahub.core.domain.repository.DraftRepository
import com.mmg.manahub.core.domain.repository.DraftSimRepository
import com.mmg.manahub.feature.home.domain.usecase.GetAccountNudgeUseCase
import com.mmg.manahub.feature.home.presentation.HomeViewModel.Companion.DISCOVER_RANDOM_QUERY
import com.mmg.manahub.feature.home.presentation.HomeViewModel.Companion.MAX_NEWS
import com.mmg.manahub.core.model.news.ContentSource
import com.mmg.manahub.feature.news.domain.usecase.GetNewsFeedUseCase
import com.mmg.manahub.feature.news.domain.usecase.ManageSourcesUseCase
import com.mmg.manahub.feature.news.domain.usecase.RefreshNewsFeedUseCase
import com.mmg.manahub.core.domain.repository.WishlistRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Drives the customizable Home widget board.
 *
 * It composes existing repository flows (stats, decks, games, draft, tournaments,
 * news, auth, preferences) into a single [HomeUiState]. The dashboard layout is a
 * persisted, reorderable list of [WidgetInstance]s; edit mode is transient
 * per-session and never persisted.
 *
 * No new Room tables and no startup-only network calls are introduced. Phase 2/3
 * data slices are derived from existing repositories where available and degrade
 * to empty/null where a backend source does not exist yet.
 *
 * The live in-memory active-game state is NOT injected here (the GameViewModel is
 * activity-scoped); [com.mmg.manahub.app.navigation.AppNavGraph] passes it into the
 * screen instead.
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
    private val communityStatsRepository: CommunityStatsRepository,
    private val draftRepository: DraftRepository,
    private val wishlistRepository: WishlistRepository,
    private val getAccountNudgeUseCase: GetAccountNudgeUseCase,
    private val gamificationRepository: GamificationRepository,
) : ViewModel() {

    /**
     * Crashlytics handle for additive telemetry (breadcrumb logs + custom keys) on the Home board.
     * Non-fatal exception reporting goes through [recordSafeNonFatal]; this instance is reserved for
     * [FirebaseCrashlytics.log] / [FirebaseCrashlytics.setCustomKey], which have no helper wrapper.
     * Telemetry is purely observational: it never alters control flow and never carries PII (only
     * enum ids, counts, set codes, and exception type names are recorded — never free-text queries).
     */
    private val crashlytics = FirebaseCrashlytics.getInstance()

    /** Set once after the first [uiState] resolves, so session context keys are attached lazily. */
    private var sessionContextKeysSet = false

    /**
     * Externally-triggered ACTION_REQUIRED nudge (highest priority). Set when the
     * user attempts an account-gated action while signed out. Cleared on dismissal
     * or successful authentication.
     */
    private val actionRequiredMessage = MutableStateFlow<String?>(null)

    /**
     * Holds the random Scryfall cards fetched for the Discover/Card-of-the-day widgets.
     * Populated lazily by [fetchRandomCardsIfNeeded]; empty until the first fetch
     * succeeds, at which point [discoverSnapshotFlow] re-emits with the new cards.
     */
    private val discoverCardsFlow = MutableStateFlow<List<DiscoverCard>>(emptyList())

    /**
     * Load state for the Discover / Card-of-the-day slice. Lets the widgets distinguish
     * "still loading" (spinner) from "failed / empty" (retry affordance) — without it a
     * failed fetch would spin forever. [HomeAction.RetryDiscover] resets this to Loading.
     */
    private val discoverLoadStateFlow = MutableStateFlow(DiscoverLoadState.LOADING)

    /**
     * Set the Discover cards row is currently scoped to. Null = unfiltered (uses the default
     * random query). Selecting a set re-fetches the row scoped to that set.
     */
    private val discoverSetFlow = MutableStateFlow<MagicSet?>(null)

    /**
     * The single random card shown by the Random card widget. INDEPENDENT of the Discover row:
     * it is re-fetchable on demand via [fetchRandomCard] with no once-guard, so every refresh
     * surfaces a fresh card.
     */
    private val randomCardFlow = MutableStateFlow<DiscoverCard?>(null)

    /** Load state for the independent Random card widget (spinner vs. retry affordance). */
    private val randomCardLoadStateFlow = MutableStateFlow(DiscoverLoadState.LOADING)

    // ── First Steps carousel ──────────────────────────────────────────────────

    /** Emits the set of step IDs the user has explicitly skipped. */
    private val skippedFirstStepsFlow: Flow<Set<String>> =
        userPrefsDataStore.observeSkippedFirstSteps()

    // ── Authentication ────────────────────────────────────────────────────────

    private val isAuthenticatedFlow: StateFlow<Boolean> =
        authRepository.sessionState
            .map { it is SessionState.Authenticated && !it.user.isAnonymous }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    // ── Derived source flows ────────────────────────────────────────────────────

    private val currencyFlow: StateFlow<PreferredCurrency> =
        userPrefsDataStore.preferredCurrencyFlow
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PreferredCurrency.USD)

    private val collectionStatsFlow =
        currencyFlow.flatMapLatest { currency ->
            statsRepository.observeCollectionStats(currency)
        }

    /** Persisted news filter selection, shared with the full News screen. */
    private val newsFiltersFlow: StateFlow<NewsFilterPrefs> =
        userPrefsDataStore.observeNewsFilters()
            .catch {
                crashlytics.setCustomKey("home_flow_error_source", "news_filters")
                recordSafeNonFatal("home_flow_news_filters", it)
                crashlytics.log("home_flow_error: news_filters")
                emit(NewsFilterPrefs.DEFAULT)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NewsFilterPrefs.DEFAULT)

    /**
     * Recent news for the Home widget, filtered by the SAME persisted filters the News
     * screen uses (enabled sources ∩ language ∩ type ∩ explicit source allowlist). The
     * default is English-only. Capped at [MAX_NEWS]. Catch-isolated so a failing source
     * never collapses the board.
     */
    private val recentNewsFlow: StateFlow<List<NewsItem>?> =
        combine(
            getNewsFeedUseCase(),
            manageSourcesUseCase.observeSources(),
            newsFiltersFlow,
        ) { items, sources, filters ->
            applyNewsFilters(items, sources, filters).take(MAX_NEWS)
        }
            .map<List<NewsItem>, List<NewsItem>?> { it }
            .catch {
                crashlytics.setCustomKey("home_flow_error_source", "recent_news")
                recordSafeNonFatal("home_flow_recent_news", it)
                crashlytics.log("home_flow_error: recent_news")
                emit(emptyList())
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** True when the persisted news filters differ from the English-only default. */
    private val newsFiltersActiveFlow: StateFlow<Boolean> =
        newsFiltersFlow
            .map { it != NewsFilterPrefs.DEFAULT }
            .catch { emit(false) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /**
     * The persisted layout. The default is auth-dependent, so this re-resolves
     * whenever auth state flips. Edit-mode is overlaid downstream, not here.
     */
    private val layoutFlow: Flow<List<WidgetInstance>> =
        isAuthenticatedFlow.flatMapLatest { authed ->
            userPrefsDataStore
                .homeLayoutFlow(defaultLayoutFor(authed).map { it.toPersisted() })
                // Map persisted widgets back to UI instances; drop any whose persistedId
                // no longer maps to a known widget type (removed in a newer app version).
                .map { persisted -> persisted.mapNotNull { it.toInstanceOrNull() } }
        }

    // ── Stats / discover / social snapshots (catch-isolated) ────────────────────

    /**
     * Shared session history [StateFlow]. Declared before [performanceFlow] and
     * [statsSnapshotFlow] (property init order) so both consumers can reference it
     * without subscribing to the same Room live-query twice.
     */
    private val historyFlow: StateFlow<List<LocalSessionHistoryRow>> =
        gameSessionRepository.observeLocalSessionHistory(HISTORY_LIMIT)
            .catch {
                crashlytics.setCustomKey("home_flow_error_source", "session_history")
                recordSafeNonFatal("home_flow_session_history", it)
                crashlytics.log("home_flow_error: session_history")
                emit(emptyList())
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val performanceFlow: Flow<PerformanceDetails> = combine(
        gameSessionRepository.observeAvgWinTurn(GLOBAL_SEAT).catch { emit(null) },
        gameSessionRepository.observeAvgLifeOnWin().catch { emit(null) },
        gameSessionRepository.observeAvgLifeOnLoss().catch { emit(null) },
        historyFlow,
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

    private val statsSnapshotFlow: Flow<StatsSnapshot> = combine(
        gameSessionRepository.observeLocalWins().catch { emit(0) },
        historyFlow,
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
    }

    /**
     * Latest draftable sets feed the spotlight/sets widgets.
     *
     * Converted to a [StateFlow] via [stateIn] so it never completes. A cold [flow] that emits
     * once and completes would terminate the downstream [combine] block, silently freezing
     * [uiState] after its first emission.
     */
    private val latestSetsFlow: StateFlow<List<DraftSet>> =
        flow {
            // Latest sets are cached locally by DraftRepository; failure degrades to empty.
            val sets = runCatching {
                when (val result = draftRepository.getDraftableSets()) {
                    is com.mmg.manahub.core.model.DataResult.Success -> result.data.take(LATEST_SETS_LIMIT)
                    else -> emptyList()
                }
            }.getOrDefault(emptyList())
            emit(sets)
        }
        .catch {
            crashlytics.setCustomKey("home_flow_error_source", "latest_sets")
            recordSafeNonFatal("home_flow_latest_sets", it)
            crashlytics.log("home_flow_error: latest_sets")
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Discover-row slice: scoped set + cards + load state, bundled so the parent combine stays
     * a typed (non-vararg) overload (no Array<Any?> erasure).
     */
    private val discoverRowFlow: Flow<DiscoverRow> =
        combine(discoverSetFlow, discoverCardsFlow, discoverLoadStateFlow) { set, cards, loadState ->
            DiscoverRow(selectedSet = set, cards = cards, loadState = loadState)
        }

    /**
     * Discover slice: latest sets, the scoped Discover cards row, and the independent single
     * random card for the Random card widget. The random card is fully decoupled from the row
     * and re-fetchable on demand.
     */
    private val discoverSnapshotFlow: Flow<DiscoverSnapshot> =
        combine(
            latestSetsFlow,
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

    /** Social/community slice. Community stats are stubbed; trade summary not wired yet. */
    private val socialSnapshotFlow: Flow<SocialSnapshot> =
        isAuthenticatedFlow.flatMapLatest { authed ->
            combine(
                communityStatsRepository.observeCommunityStats().catch { emit(null) },
                tradeSummaryFlow(authed),
                activeTournamentFlow(authed),
                wishlistFlow(authed),
            ) { community, trade, tournament, wishlist ->
                SocialSnapshot(
                    community = community,
                    tradeSummary = trade,
                    activeTournament = tournament,
                    wishlist = wishlist,
                )
            }
        }

    /**
     * Gamification slice (Phase 2): level/XP, streak, and quest summary for the Home widgets +
     * CONTEXT_HERO claim suggestion. Gated by the master toggle — when disabled the snapshot is null
     * so every gamification surface disappears. Each source is catch-isolated so a single failure
     * degrades to a null snapshot rather than collapsing the board.
     */
    private val gamificationSnapshotFlow: Flow<GamificationSnapshot> =
        userPrefsDataStore.gamificationEnabledFlow.flatMapLatest { enabled ->
            if (!enabled) {
                flowOf(GamificationSnapshot(enabled = false, data = null))
            } else {
                combine(
                    gamificationRepository.observeProgression().catch { emit(DEFAULT_PROGRESSION) },
                    gamificationRepository.observeActiveQuests().catch { emit(QuestBoard.empty) },
                    gamificationRepository.observeDailyActivityStreak().catch { emit(DEFAULT_STREAK) },
                ) { progression, board, streak ->
                    GamificationSnapshot(
                        enabled = true,
                        data = toHomeGamification(progression, board, streak),
                    )
                }.catch {
                    crashlytics.setCustomKey("home_flow_error_source", "gamification")
                    recordSafeNonFatal("home_flow_gamification", it)
                    crashlytics.log("home_flow_error: gamification")
                    emit(GamificationSnapshot(enabled = true, data = null))
                }
            }
        }

    // TODO(home-trades): wire to TradesRepository pending-proposal count once exposed as a Flow.
    private fun tradeSummaryFlow(authed: Boolean): Flow<TradeSummary?> = flowOf(null)

    // Tournaments are local, so this is available regardless of auth state.
    private fun activeTournamentFlow(authed: Boolean): Flow<TournamentSummary?> =
        tournamentRepository.observeTournaments()
            .map { tournaments -> tournaments.firstActiveSummary() }
            .catch { emit(null) }

    // TODO(home-wishlist): wire to WishlistRepository count/value once exposed as a Flow.
    private fun wishlistFlow(authed: Boolean): Flow<WishlistStats?> =
        if (!authed) flowOf(null)
        else combine(
            wishlistRepository.observeLocal(),
            currencyFlow,
        ) { entries, currency ->
            if (entries.isEmpty()) return@combine WishlistStats(0, "", emptySet())

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
                    }.toSet()
            )
        }.map<WishlistStats, WishlistStats?> { it }
        .catch {
            crashlytics.setCustomKey("home_flow_error_source", "wishlist")
            recordSafeNonFatal("home_flow_wishlist", it)
            crashlytics.log("home_flow_error: wishlist")
            emit(null)
        }

    private val uiState: StateFlow<HomeUiState> = run {
        val libraryFlow = combine(
            collectionStatsFlow,
            deckRepository.observeAllDeckSummaries(),
            currencyFlow,
        ) { stats, decks, currency ->
            LibrarySnapshot(stats = stats, decks = decks, currency = currency)
        }

        val activityFlow = combine(
            gameSessionRepository.observeTotalGames(),
            draftSimRepository.observeActiveSession(),
            tournamentRepository.observeTournaments(),
        ) { totalGames, draft, tournaments ->
            ActivitySnapshot(
                totalGames = totalGames,
                activeDraft = draft,
                activeTournaments = tournaments.count { it.status == "ACTIVE" || it.status == "SETUP" },
            )
        }

        val accountFlow = combine(
            authRepository.sessionState,
            userPrefsDataStore.isNudgeCoolingDown(),
            actionRequiredMessage,
            userPrefsDataStore.avatarUrlFlow,
        ) { session, coolingDown, actionRequired, avatarUrl ->
            val user = (session as? SessionState.Authenticated)?.user
            AccountSnapshot(
                isAuthenticated = session is SessionState.Authenticated && !session.user.isAnonymous,
                isCoolingDown = coolingDown,
                actionRequiredMessage = actionRequired,
                avatarUrl = avatarUrl ?: user?.avatarUrl,
                nickname = user?.nickname,
            )
        }

        val coreFlow = combine(
            libraryFlow,
            activityFlow,
            accountFlow,
            userPrefsDataStore.observeQuickStartActions(),
            userPrefsDataStore.playerNameFlow,
            skippedFirstStepsFlow,
        ) { args ->
            // combine(6 flows) uses the vararg overload — destructure manually.
            @Suppress("UNCHECKED_CAST")
            val library    = args[0] as LibrarySnapshot
            @Suppress("UNCHECKED_CAST")
            val activity   = args[1] as ActivitySnapshot
            @Suppress("UNCHECKED_CAST")
            val account    = args[2] as AccountSnapshot
            @Suppress("UNCHECKED_CAST")
            val quickStart = args[3] as List<QuickStartAction>
            @Suppress("UNCHECKED_CAST")
            val playerName = args[4] as String
            @Suppress("UNCHECKED_CAST")
            val skipped    = args[5] as Set<String>

            val effectivePlayerName = account.nickname ?: playerName
            CoreSnapshot(library, activity, account, quickStart, effectivePlayerName, skipped)
        }

        // Bundle the Phase 2/3 data slices into one typed combine, then fold that
        // bundle into the core slice. Using only the typed (non-vararg) combine
        // overloads keeps element types intact (no Array<Any?> erasure / casts).
        val dataFlow = combine(
            layoutFlow,
            statsSnapshotFlow,
            discoverSnapshotFlow,
            socialSnapshotFlow,
            gamificationSnapshotFlow,
        ) { layout, stats, discover, social, gamification ->
            DataBundle(
                layout = layout,
                stats = stats,
                discover = discover,
                social = social,
                gamification = gamification,
            )
        }

        // Bundle the news list with the "filters active" flag so the final combine stays a
        // typed 3-arg overload (no Array<Any?> erasure).
        val newsFlow = combine(recentNewsFlow, newsFiltersActiveFlow) { news, filtersActive ->
            NewsBundle(items = news, filtersActive = filtersActive)
        }

        combine(coreFlow, newsFlow, dataFlow) { core, news, data ->
            buildUiState(
                core = core,
                news = news.items,
                newsFiltersActive = news.filtersActive,
                layout = data.layout,
                stats = data.stats,
                discover = data.discover,
                social = data.social,
                gamification = data.gamification,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HomeUiState(isLoading = true, hero = HomeHeroState.Loading),
        )
    }

    /** Public UI state. */
    val state: StateFlow<HomeUiState> get() = uiState

    init {
        authRepository.sessionState
            .onEach { session ->
                if (session is SessionState.Authenticated) actionRequiredMessage.value = null
            }
            .launchIn(viewModelScope)
        // Trigger a background news refresh so the widget has data even on first open,
        // before the user has ever visited the News tab. The freshness check inside
        // refreshAll() (1-hour window) prevents redundant network calls.
        viewModelScope.launch {
            // Additive telemetry: report a failed background refresh without surfacing it to the user.
            runCatching { refreshNewsFeedUseCase() }.exceptionOrNull()?.let { error ->
                recordSafeNonFatal("home_news_refresh", error)
                crashlytics.log("home_news_refresh_failed")
            }
        }
        // Pick a random playable set (>10 cards) to seed the Discover row BEFORE the first fetch,
        // then populate the Discover row (lazy once-guard) and the independent Random card widget.
        // On failure/empty the set stays null and the fetch falls back to the global random query.
        viewModelScope.launch {
            seedRandomDiscoverSet()
            fetchDiscoverCards(forceRefresh = false)
            fetchRandomCard()
        }
    }

    /**
     * Chooses a random playable set with more than 10 cards and assigns it to [discoverSetFlow]
     * so the FIRST Discover fetch is already scoped to it. Runs off the main thread and is fully
     * guarded: any failure (or an empty set list) leaves the set null, falling back to the global
     * [DISCOVER_RANDOM_QUERY]. Best-effort — never throws.
     */
    private suspend fun seedRandomDiscoverSet() {
        val outcome = runCatching {
            withContext(Dispatchers.IO) {
                scryfallRemoteDataSource.getAllSets()
                    .filter { it.setType in PLAYABLE_SET_TYPES && it.cardCount > MIN_DISCOVER_SET_CARDS }
                    .randomOrNull()
            }
        }
        // Additive telemetry: report the swallowed seed failure without altering the fallback behaviour.
        outcome.exceptionOrNull()?.let { error ->
            recordSafeNonFatal("home_seed_discover_set", error)
            crashlytics.log("home_seed_discover_set_failed")
        }
        val chosen = outcome.getOrNull()
        if (chosen != null) discoverSetFlow.value = chosen
    }

    /**
     * Fetches the random Scryfall cards for the Discover row.
     *
     * When [forceRefresh] is false this is lazy: it no-ops if the row already holds cards (the
     * init / auto path). When true it always re-runs the query (manual refresh / set change).
     * The query is scoped to [discoverSetFlow] when a set is selected, otherwise the default
     * random query.
     *
     * Rate limiting is handled inside [CardRepository] (every Scryfall call is wrapped in
     * [com.mmg.manahub.core.network.ScryfallRequestQueue]), so no extra guard is needed here.
     * An empty/failed result degrades to [DiscoverLoadState.FAILED] (never an endless spinner).
     */
    private fun fetchDiscoverCards(forceRefresh: Boolean) {
        if (!forceRefresh && discoverCardsFlow.value.isNotEmpty()) return
        val set = discoverSetFlow.value
        val query = if (set != null) {
            "set:${set.code} -is:digital order:random"
        } else {
            DISCOVER_RANDOM_QUERY
        }
        discoverLoadStateFlow.value = DiscoverLoadState.LOADING
        // Clear the row so the spinner shows immediately on a refresh and cards stream in fresh.
        discoverCardsFlow.value = emptyList()
        viewModelScope.launch {
            // bypassCache = true avoids the in-memory map, but Scryfall's CDN still caches the page.
            // Taking the first N of a CDN-cached page means refresh does nothing; we must shuffle client-side.
            val outcome = runCatching { cardRepository.searchCards(query, page = 1, bypassCache = true) }
            val result = outcome.getOrNull()
            val fetched = (result as? com.mmg.manahub.core.model.DataResult.Success)
                ?.data
                ?.shuffled()
                ?.take(DISCOVER_CARD_COUNT)
                .orEmpty()
            if (fetched.isEmpty()) {
                // An empty/failed result must NOT leave the widget spinning forever.
                // Additive telemetry: record the silent failure WITHOUT changing control flow. Never log
                // the raw query (free-text / potential PII) — only its length, the scoped set, and the type.
                val error = outcome.exceptionOrNull()
                crashlytics.setCustomKey("home_discover_query_length", query.length)
                crashlytics.setCustomKey("home_discover_scoped_set", set?.code ?: "none")
                crashlytics.setCustomKey(
                    "home_discover_error_type",
                    error?.let { it::class.simpleName ?: "Unknown" } ?: "EmptyResult",
                )
                if (error != null) recordSafeNonFatal("home_discover_fetch", error)
                crashlytics.log("home_discover_fetch_failed: query_length=${query.length}")
                discoverLoadStateFlow.value = DiscoverLoadState.FAILED
                return@launch
            }
            // Stream each card into the row as it is mapped so the LazyRow starts rendering with
            // the first card rather than waiting for all of them. De-dup by id (a repeated
            // scryfallId would crash the LazyRow's stable-key contract).
            val seen = HashSet<String>()
            fetched.forEach { card ->
                if (seen.add(card.scryfallId)) {
                    val mapped = DiscoverCard(
                        id = card.scryfallId,
                        scryfallId = card.scryfallId,
                        name = card.name,
                        // Full card image now (was art-crop), fall back to art-crop if absent.
                        imageUrl = card.imageNormal ?: card.imageArtCrop,
                        typeLine = card.typeLine,
                    )
                    discoverCardsFlow.update { it + mapped }
                    // Flip to LOADED on the FIRST appended card so the row renders while the rest stream in.
                    if (discoverLoadStateFlow.value != DiscoverLoadState.LOADED) {
                        discoverLoadStateFlow.value = DiscoverLoadState.LOADED
                    }
                }
            }
        }
    }

    /**
     * Fetches a fresh single random card for the Random card widget. Always re-fetches (no
     * once-guard) so a manual refresh always surfaces a new card. Failures degrade to
     * [DiscoverLoadState.FAILED] without clearing the previously-shown card.
     */
    private fun fetchRandomCard() {
        randomCardLoadStateFlow.value = DiscoverLoadState.LOADING
        viewModelScope.launch {
            // bypassCache = true avoids the in-memory map, but Scryfall's CDN still caches the page.
            // Taking the first N of a CDN-cached page means refresh does nothing; we must shuffle client-side.
            val outcome = runCatching { cardRepository.searchCards(DISCOVER_RANDOM_QUERY, page = 1, bypassCache = true) }
            val result = outcome.getOrNull()
            val card = (result as? com.mmg.manahub.core.model.DataResult.Success)
                ?.data
                ?.shuffled()
                ?.firstOrNull()
                ?.let { c ->
                    DiscoverCard(
                        id = c.scryfallId,
                        scryfallId = c.scryfallId,
                        name = c.name,
                        // Full card image, fall back to art-crop.
                        imageUrl = c.imageNormal ?: c.imageArtCrop,
                        typeLine = c.typeLine,
                    )
                }
            if (card != null) {
                randomCardFlow.value = card
                randomCardLoadStateFlow.value = DiscoverLoadState.LOADED
            } else {
                // Additive telemetry: surface the silent failure without changing control flow.
                val error = outcome.exceptionOrNull()
                crashlytics.setCustomKey(
                    "home_random_card_error_type",
                    error?.let { it::class.simpleName ?: "Unknown" } ?: "EmptyResult",
                )
                if (error != null) recordSafeNonFatal("home_random_card_fetch", error)
                crashlytics.log("home_random_card_fetch_failed")
                randomCardLoadStateFlow.value = DiscoverLoadState.FAILED
            }
        }
    }

    /**
     * Scopes the Discover row to [set] (or clears the filter when null) and re-fetches the row
     * for the new scope.
     */
    private fun selectDiscoverSet(set: MagicSet?) {
        // Set code is a public Scryfall identifier (not PII); "cleared" when the filter is removed.
        crashlytics.log("home_discover_set_selected: ${set?.code ?: "cleared"}")
        discoverSetFlow.value = set
        fetchDiscoverCards(forceRefresh = true)
    }

    // ── Public intents ──────────────────────────────────────────────────────────

    /** Routes a board-mutating [HomeAction]; navigation actions are handled by the caller. */
    fun onAction(action: HomeAction) {
        when (action) {
            is HomeAction.MoveWidget -> moveWidget(action.from, action.to)
            is HomeAction.UpdateLayout -> persistLayout(action.layout)
            is HomeAction.AddWidget -> addWidget(action.type)
            is HomeAction.RemoveWidget -> removeWidget(action.type)
            HomeAction.ResetLayout -> resetLayout()
            is HomeAction.SkipFirstStep -> skipFirstStep(action.stepId)
            HomeAction.RetryDiscover -> {
                // Distinguish a user-initiated retry-after-failure from a normal manual refresh.
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
            HomeAction.ResetNewsFilters -> resetNewsFilters()
            HomeAction.RateApp -> Unit // no-op; UI handles the store deep link
            else -> Unit // navigation intents are resolved by AppNavGraph
        }
    }

    /** Persists a newly chosen set of exactly four Quick Start actions. */
    fun saveQuickStartActions(actions: List<QuickStartAction>) {
        // Log the chosen action enum ids (CSV) — these are fixed enum names, never free text.
        crashlytics.log("home_quick_start_saved: ${actions.joinToString(",") { it.name }}")
        viewModelScope.launch { userPrefsDataStore.saveQuickStartActions(actions) }
    }

    /**
     * Persists [stepId] as skipped. The DataStore re-emits [skippedFirstStepsFlow],
     * which causes [buildVisibleSteps] to drop this step from the carousel on the
     * next [uiState] emission.
     */
    fun skipFirstStep(stepId: String) {
        // stepId is a fixed STEP_FIRST_* constant — safe to log (no PII).
        crashlytics.log("home_first_step_skipped: $stepId")
        viewModelScope.launch { userPrefsDataStore.skipFirstStep(stepId) }
    }

    /** Clears the persisted News filters back to the English-only default. */
    fun resetNewsFilters() {
        crashlytics.log("home_news_filters_reset")
        viewModelScope.launch { userPrefsDataStore.resetNewsFilters() }
    }

    /** Dismisses the current account nudge, starting its 48-hour cooldown. */
    fun dismissAccountNudge() {
        // Record WHICH nudge trigger the user dismissed (enum name only — no PII).
        val trigger = uiState.value.accountNudge?.trigger?.name ?: "unknown"
        crashlytics.setCustomKey("home_nudge_trigger", trigger)
        crashlytics.log("home_nudge_dismissed: $trigger")
        actionRequiredMessage.value = null
        viewModelScope.launch { userPrefsDataStore.dismissAccountNudge() }
    }

    /** Raises a high-priority ACTION_REQUIRED nudge. */
    fun triggerActionRequiredNudge(message: String) {
        // Log only that an action-required nudge fired — never the message text (may be user-facing/PII).
        crashlytics.log("home_nudge_action_required_triggered")
        actionRequiredMessage.value = message
    }

    // ── Layout mutations ──────────────────────────────────────────────────────
    //
    // Each reducer reads the latest layout from uiState (the single source of truth),
    // produces a new list, and immediately persists it. DataStore then re-emits and
    // the new layout flows back into uiState — there is no separate in-memory copy
    // to drift out of sync.

    private fun moveWidget(from: Int, to: Int) {
        crashlytics.log("home_widget_moved: from=$from to=$to")
        val current = uiState.value.layout
        if (from == to) return
        if (from !in current.indices || to !in current.indices) return
        val mutable = current.toMutableList()
        val item = mutable.removeAt(from)
        mutable.add(to, item)
        persistLayout(mutable)
    }

    private fun addWidget(type: HomeWidgetType) {
        crashlytics.log("home_widget_added: ${type.persistedId}")
        val current = uiState.value.layout
        if (current.any { it.type == type }) return
        val newInstance = WidgetInstance(type, type.defaultSize())
        // Insert so the layout stays grouped by the canonical WidgetCategory order: place the
        // new widget at the END of its own category's run (after the last existing widget whose
        // category ordinal <= the new type's). This keeps each category contiguous and ordered.
        val newOrdinal = type.category.ordinal
        val insertIndex = current.indexOfLast { it.type.category.ordinal <= newOrdinal } + 1
        val mutable = current.toMutableList()
        mutable.add(insertIndex, newInstance)
        persistLayout(mutable)
    }

    private fun removeWidget(type: HomeWidgetType) {
        crashlytics.log("home_widget_removed: ${type.persistedId}")
        if (type.isAlwaysPresent) return
        persistLayout(uiState.value.layout.filterNot { it.type == type })
    }

    private fun resetLayout() {
        crashlytics.setCustomKey("home_layout_widget_count_before_reset", uiState.value.layout.size)
        crashlytics.log("home_layout_reset")
        persistLayout(defaultLayoutFor(isAuthenticatedFlow.value))
    }

    private fun persistLayout(layout: List<WidgetInstance>) {
        viewModelScope.launch {
            userPrefsDataStore.saveHomeLayout(layout.map { it.toPersisted() })
        }
    }

    // ── Reduction ─────────────────────────────────────────────────────────────

    private fun buildUiState(
        core: CoreSnapshot,
        news: List<NewsItem>?,
        newsFiltersActive: Boolean,
        layout: List<WidgetInstance>,
        stats: StatsSnapshot,
        discover: DiscoverSnapshot,
        social: SocialSnapshot,
        gamification: GamificationSnapshot,
    ): HomeUiState {
        val collectionStats = core.library.stats
        val deckCount = core.library.decks.size

        val libraryStats = LibraryStats(
            totalCards = collectionStats.totalCards,
            uniqueCards = collectionStats.uniqueCards,
            deckCount = deckCount,
            estimatedValueDisplay = PriceFormatter.formatFromScryfall(
                priceUsd = collectionStats.totalValueUsd,
                priceEur = collectionStats.totalValueEur,
                preferredCurrency = core.library.currency,
            ),
        )

        val cardCount = collectionStats.uniqueCards
        val deckCountForSteps = deckCount
        val friendCount = 0 // Phase 3: communityStats friend count not yet exposed as a Flow.
        val isProfileComplete = !core.account.avatarUrl.isNullOrBlank()
            && core.playerName != "Wizard"
            && core.playerName.isNotBlank()

        val visibleSteps = buildVisibleSteps(
            isAuthenticated = core.account.isAuthenticated,
            cardCount = cardCount,
            deckCount = deckCountForSteps,
            friendCount = friendCount,
            isProfileComplete = isProfileComplete,
            skipped = core.skippedFirstSteps,
        )

        val hero = resolveHero(core.activity, core.playerName, visibleSteps, gamification)
        val nudge = resolveNudge(core.account, collectionStats, deckCount, core.activity.totalGames)

        // Attach session-context custom keys ONCE, the first time the board resolves. These are
        // observational only (auth flag, widget count, hero type name) — no PII. The widget count is
        // refreshed here on every emission so add/reset keep it current without a separate hook.
        if (!sessionContextKeysSet) {
            crashlytics.setCustomKey("home_is_authenticated", core.account.isAuthenticated)
            crashlytics.setCustomKey("home_hero_type", hero::class.simpleName ?: "Unknown")
            sessionContextKeysSet = true
        }
        crashlytics.setCustomKey("home_layout_widget_count", layout.size)

        return HomeUiState(
            isLoading = false,
            hero = hero,
            quickStartActions = core.quickStart,
            libraryStats = libraryStats,
            recentNews = news,
            newsFiltersActive = newsFiltersActive,
            accountNudge = nudge,
            isAuthenticated = core.account.isAuthenticated,
            playerName = core.playerName,
            avatarUrl = core.account.avatarUrl,
            // Board
            layout = layout,
            // Phase 2
            lastGameRecap = stats.history.firstOrNull()?.toRecap(),
            playStreak = stats.history.toPlayStreak(),
            winRate = toWinRate(stats),
            bestDeck = toBestDeck(stats),
            nemesis = toNemesis(stats),
            performanceDetails = stats.performance,
            collectionByColor = collectionStats.byColor.toColorMap(),
            collectionByRarity = collectionStats.byRarity.toRarityMap(),
            discoverCards = discover.discoverCards,
            cardOfTheDay = discover.randomCard,
            discoverLoadState = discover.loadState,
            randomCardLoadState = discover.randomCardLoadState,
            discoverSetCode = discover.selectedSet?.code,
            discoverSet = discover.selectedSet,
            latestSets = discover.latestSets,
            wishlistStats = social.wishlist,
            decks = core.library.decks,
            // Phase 3
            communityStats = social.community,
            tradeSummary = social.tradeSummary,
            activeTournamentSummary = social.activeTournament,
            // Gamification (Phase 2)
            gamificationEnabled = gamification.enabled,
            gamification = gamification.data,
        )
    }

    // ── Stats → widget model mappers (members so they can see StatsSnapshot) ────

    private fun toWinRate(stats: StatsSnapshot): WinRateStats? {
        val total = stats.history.size
        if (total == 0) return null
        return WinRateStats(
            wins = stats.localWins,
            totalGames = total,
            recentResults = stats.history.take(WIN_SPARK_COUNT).map { it.localIsWinner },
        )
    }

    private fun toBestDeck(stats: StatsSnapshot): BestDeckStats? {
        val best = stats.deckStats
            .filter { it.totalGames > 0 && !it.deckName.isNullOrBlank() }
            .maxByOrNull { row ->
                // Rank by win rate, breaking ties by total games played.
                (row.wins.toDouble() / row.totalGames) * 1000 + row.totalGames
            } ?: return null
        return BestDeckStats(
            deckId = best.deckId,
            deckName = best.deckName ?: "",
            wins = best.wins,
            losses = (best.totalGames - best.wins).coerceAtLeast(0),
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
     * Filters [ALL_FIRST_STEPS] down to the steps that:
     *   1. Have not been skipped by the user ([skipped] set), and
     *   2. Meet their show condition based on the current app state.
     *
     * The output list preserves the canonical order defined in [ALL_FIRST_STEPS].
     *
     * @param isAuthenticated Whether the user is signed in (non-anonymous).
     * @param cardCount       Number of unique cards in the local collection.
     * @param deckCount       Number of decks the user has created.
     * @param friendCount     Number of accepted friends; default 0 when not yet loaded.
     * @param isProfileComplete True when avatar + non-default name are set.
     * @param skipped         Set of step IDs the user has already skipped.
     */
    private fun buildVisibleSteps(
        isAuthenticated: Boolean,
        cardCount: Int,
        deckCount: Int,
        friendCount: Int,
        isProfileComplete: Boolean,
        skipped: Set<String>,
    ): List<FirstStepItem> = ALL_FIRST_STEPS.filter { step ->
        if (step.id in skipped) return@filter false
        when (step.id) {
            STEP_FIRST_ADD_CARD        -> cardCount == 0
            STEP_FIRST_SCAN_CARD       -> true
            STEP_FIRST_CREATE_ACCOUNT  -> !isAuthenticated
            STEP_FIRST_CREATE_DECK     -> deckCount == 0
            STEP_FIRST_PLAYTEST_DECK   -> deckCount > 0
            STEP_FIRST_ADD_FRIEND      -> isAuthenticated
            STEP_FIRST_REVIEW_FRIEND   -> isAuthenticated && friendCount > 0
            STEP_FIRST_PLAY_GAME       -> true
            STEP_FIRST_COLLECTION_STATS -> cardCount > 0
            STEP_FIRST_DRAFT_GUIDE     -> true
            STEP_FIRST_NEWS            -> true
            STEP_FIRST_CREATE_TRADE    -> isAuthenticated && friendCount > 0 && cardCount > 0
            STEP_FIRST_ADD_WISHLIST    -> true
            STEP_FIRST_OPEN_FOR_TRADE  -> cardCount > 0
            STEP_FIRST_PREFERENCES     -> true
            STEP_FIRST_COMPLETE_PROFILE -> isAuthenticated && !isProfileComplete
            STEP_FIRST_RATE_APP        -> true
            else                       -> false
        }
    }

    /**
     * Resolves the hero state following the priority order:
     *   ActiveGame (injected by caller) > ActiveDraft > Welcome(steps non-empty)
     *   > Summary > Welcome(empty = completion card).
     *
     * [visibleSteps] drives the Welcome branch: when non-empty the carousel is shown;
     * when empty and the user has played games the Summary is preferred; when empty and
     * no games have been played we fall back to the Welcome completion card.
     */
    private fun resolveHero(
        activity: ActivitySnapshot,
        playerName: String,
        visibleSteps: List<FirstStepItem>,
        gamification: GamificationSnapshot,
    ): HomeHeroState {
        // Highest priority (when gamification is enabled): completed quests waiting to be claimed.
        val claimable = gamification.data?.claimableCount ?: 0
        if (gamification.enabled && claimable > 0) {
            return HomeHeroState.QuestsReady(count = claimable)
        }
        val draft = activity.activeDraft
        if (draft != null && draft.status == DraftStatus.DRAFTING) {
            return HomeHeroState.ActiveDraft(setName = draft.config.setCode.uppercase())
        }
        // Show the First Steps carousel whenever the user still has things to discover.
        if (visibleSteps.isNotEmpty()) {
            return HomeHeroState.Welcome(steps = visibleSteps)
        }
        // All steps done: prefer the Summary if the user has any games tracked.
        return if (activity.totalGames > 0) {
            val name = if (playerName.isNotBlank()) playerName else "Wizard"
            HomeHeroState.Summary(playerName = name, totalGames = activity.totalGames)
        } else {
            // No games and no pending steps: show the completion card.
            HomeHeroState.Welcome(steps = emptyList())
        }
    }

    private fun resolveNudge(
        account: AccountSnapshot,
        stats: CollectionStats,
        deckCount: Int,
        totalGames: Int,
    ): AccountNudge? {
        val trigger = getAccountNudgeUseCase(
            isAuthenticated = account.isAuthenticated,
            isCoolingDown = account.isCoolingDown,
            actionRequired = account.actionRequiredMessage,
            uniqueCards = stats.uniqueCards,
            deckCount = deckCount,
            totalGames = totalGames,
        ) ?: return null

        return when (trigger) {
            NudgeTrigger.ACTION_REQUIRED ->
                AccountNudge(message = account.actionRequiredMessage, trigger = trigger)
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
        if (authenticated) defaultLayoutSignedIn else defaultLayoutSignedOut

    private val defaultLayoutSignedOut = listOf(
        WidgetInstance(HomeWidgetType.CONTEXT_HERO, WidgetSize.MEDIUM),
        WidgetInstance(HomeWidgetType.QUICK_ACTIONS, WidgetSize.MEDIUM),
        WidgetInstance(HomeWidgetType.DISCOVER_CARDS, WidgetSize.MEDIUM),
        WidgetInstance(HomeWidgetType.CARD_OF_THE_DAY, WidgetSize.LARGE),
        WidgetInstance(HomeWidgetType.RULES_TIP, WidgetSize.MEDIUM),
        WidgetInstance(HomeWidgetType.LATEST_SETS, WidgetSize.MEDIUM),
        WidgetInstance(HomeWidgetType.MTG_NEWS, WidgetSize.MEDIUM),
    )

    private val defaultLayoutSignedIn = listOf(
        WidgetInstance(HomeWidgetType.CONTEXT_HERO, WidgetSize.MEDIUM),
        WidgetInstance(HomeWidgetType.QUICK_ACTIONS, WidgetSize.MEDIUM),
        WidgetInstance(HomeWidgetType.GAME_STATS_HUB, WidgetSize.MEDIUM),
        WidgetInstance(HomeWidgetType.COLLECTION_STATS_HUB, WidgetSize.MEDIUM),
        WidgetInstance(HomeWidgetType.YOUR_DECKS_SHELF, WidgetSize.MEDIUM),
        WidgetInstance(HomeWidgetType.SOCIAL_HUB, WidgetSize.MEDIUM),
        WidgetInstance(HomeWidgetType.TRADES_HUB, WidgetSize.MEDIUM),
        WidgetInstance(HomeWidgetType.MTG_NEWS, WidgetSize.MEDIUM),
        WidgetInstance(HomeWidgetType.LATEST_SETS, WidgetSize.MEDIUM),
        WidgetInstance(HomeWidgetType.RULES_TIP, WidgetSize.MEDIUM),
    )

    // ── Internal snapshots ──────────────────────────────────────────────────────

    private data class CoreSnapshot(
        val library: LibrarySnapshot,
        val activity: ActivitySnapshot,
        val account: AccountSnapshot,
        val quickStart: List<QuickStartAction>,
        val playerName: String,
        /** Step IDs the user has explicitly skipped in the First Steps carousel. */
        val skippedFirstSteps: Set<String> = emptySet(),
    )

    private data class LibrarySnapshot(
        val stats: CollectionStats,
        val decks: List<DeckSummary>,
        val currency: PreferredCurrency,
    )

    private data class ActivitySnapshot(
        val totalGames: Int,
        val activeDraft: DraftState?,
        val activeTournaments: Int,
    )

    private data class AccountSnapshot(
        val isAuthenticated: Boolean,
        val isCoolingDown: Boolean,
        val actionRequiredMessage: String?,
        val avatarUrl: String? = null,
        val nickname: String? = null,
    )

    private data class DataBundle(
        val layout: List<WidgetInstance>,
        val stats: StatsSnapshot,
        val discover: DiscoverSnapshot,
        val social: SocialSnapshot,
        val gamification: GamificationSnapshot,
    )

    /**
     * Gamification slice wrapper. [enabled] mirrors the master toggle; [data] is null when disabled
     * or when the underlying flows have not produced a snapshot yet.
     */
    private data class GamificationSnapshot(
        val enabled: Boolean,
        val data: HomeGamification?,
    )

    private data class StatsSnapshot(
        val localWins: Int,
        val history: List<LocalSessionHistoryRow>,
        val deckStats: List<com.mmg.manahub.core.data.local.dao.DeckStatsRow>,
        val nemesis: com.mmg.manahub.core.data.local.dao.EliminationCount?,
        val performance: PerformanceDetails,
    )

    /** Bundles the Discover-row sources (scoped set + cards + load state) for the parent combine. */
    private data class DiscoverRow(
        val selectedSet: MagicSet?,
        val cards: List<DiscoverCard>,
        val loadState: DiscoverLoadState,
    )

    private data class DiscoverSnapshot(
        val latestSets: List<DraftSet>,
        val discoverCards: List<DiscoverCard> = emptyList(),
        val loadState: DiscoverLoadState = DiscoverLoadState.LOADING,
        /** Set the Discover row is scoped to, or null when unfiltered. */
        val selectedSet: MagicSet? = null,
        /** The independent single random card for the Random card widget. */
        val randomCard: DiscoverCard? = null,
        /** Load state of the independent Random card widget. */
        val randomCardLoadState: DiscoverLoadState = DiscoverLoadState.LOADING,
    )

    /** Bundles the filtered news list with the "filters active" flag for the final combine. */
    private data class NewsBundle(
        val items: List<NewsItem>?,
        val filtersActive: Boolean,
    )

    private data class SocialSnapshot(
        val community: CommunityStats?,
        val tradeSummary: TradeSummary?,
        val activeTournament: TournamentSummary?,
        val wishlist: WishlistStats?,
    )

    companion object {
        /** Maximum number of news items shown on the Home dashboard widget. */
        const val MAX_NEWS = 10

        private const val HISTORY_LIMIT = 60
        private const val LATEST_SETS_LIMIT = 8
        private const val WIN_SPARK_COUNT = 5
        private const val DAY_MS = 24L * 60L * 60L * 1000L
        private const val GLOBAL_SEAT = "Wizard"

        /**
         * Scryfall query used to surface random cards in the Discover widget. A bare
         * `order:random` is REJECTED by Scryfall (no real filter term), so it must include a
         * concrete predicate. This restricts to real, non-digital, Commander-legal cards
         * (which reliably have art) and randomises the order.
         */
        private const val DISCOVER_RANDOM_QUERY = "-is:digital legal:commander order:random"

        /** Number of random cards fetched for the Discover widget. */
        private const val DISCOVER_CARD_COUNT = 10

        /** Minimum card count for a set to be eligible as the default random Discover scope. */
        private const val MIN_DISCOVER_SET_CARDS = 10

        private const val WISHLIST_PREVIEW_LIMIT = 10

        /** Max quests previewed in the Home Quests widget. */
        private const val HOME_QUEST_PREVIEW_LIMIT = 3

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
    }
}

/** Projects a [QuestUiModel] down to the compact [HomeQuest] preview model. */
private fun QuestUiModel.toHomeQuest(): HomeQuest = HomeQuest(
    instanceId = instanceId,
    titleRes = titleRes,
    emoji = emoji,
    progress = progress,
    target = target,
    isClaimable = isClaimable,
)

// ─────────────────────────────────────────────────────────────────────────────
//  Mapping helpers (top-level, pure)
// ─────────────────────────────────────────────────────────────────────────────

// Mapping helper removed — Home now uses rich NewsItem directly.

private fun LocalSessionHistoryRow.toRecap(): LastGameRecap = LastGameRecap(
    won = localIsWinner,
    deckName = localDeckName,
    mode = mode,
    durationMs = durationMs,
    opponentCount = 0, // opponent count is not exposed by the history row
)

/** Streak from most-recent-first history: counts the leading run of wins; longest run anywhere. */
private fun List<LocalSessionHistoryRow>.toPlayStreak(): PlayStreak? {
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

/** Resolves the first active/setup tournament into a compact summary, or null. */
private fun List<com.mmg.manahub.core.data.local.entity.TournamentEntity>.firstActiveSummary(): TournamentSummary? {
    val active = firstOrNull { it.status == "ACTIVE" || it.status == "SETUP" } ?: return null
    return TournamentSummary(
        tournamentId = active.id,
        name = active.name,
        round = 0, // current round is not denormalised on the entity
        standing = null,
    )
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

/**
 * Applies the persisted [NewsFilterPrefs] to [items] using the SAME logic as the full News
 * screen: keep only items from enabled sources, whose source language is selected, whose
 * content type is selected, and (when an explicit allowlist is set) whose source id is in it.
 *
 * @param sources the known content sources (supplies the enabled set + per-source language).
 */
private fun applyNewsFilters(
    items: List<NewsItem>,
    sources: List<ContentSource>,
    filters: NewsFilterPrefs,
): List<NewsItem> {
    val languageMap = sources.associate { it.id to it.language }
    val enabledSourceIds = sources.filter { it.isEnabled }.map { it.id }.toSet()
    return items
        .filter { it.sourceId in enabledSourceIds }
        .filter { (languageMap[it.sourceId] ?: "en") in filters.languages }
        .filter { item ->
            when (item) {
                is NewsItem.Article -> SourceType.ARTICLE in filters.types
                is NewsItem.Video -> SourceType.VIDEO in filters.types
            }
        }
        .filter { item ->
            // `sourceIds` lives in :shared:core-model, so it cannot be smart-cast across the module
            // boundary — capture it in a local val before the null check.
            val allowedSourceIds = filters.sourceIds
            allowedSourceIds == null || item.sourceId in allowedSourceIds
        }
}
