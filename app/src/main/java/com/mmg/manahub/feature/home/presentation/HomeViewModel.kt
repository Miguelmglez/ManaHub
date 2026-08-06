package com.mmg.manahub.feature.home.presentation

// Step ID constants are top-level in FirstStepItem.kt (same package — no import needed).
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.R
import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.sync.SyncManager
import com.mmg.manahub.core.sync.SyncState
import com.mmg.manahub.feature.game.domain.model.DeckStats
import com.mmg.manahub.feature.game.domain.model.EliminationStats
import com.mmg.manahub.feature.game.domain.model.SessionHistoryEntry
import com.mmg.manahub.core.data.remote.ScryfallRemoteDataSource
import com.mmg.manahub.core.model.CollectionStats
import com.mmg.manahub.core.model.CommunityDeckSearchFilters
import com.mmg.manahub.core.model.CommunityDeckSummary
import com.mmg.manahub.core.model.DeckSummary
import com.mmg.manahub.core.model.DraftSet
import com.mmg.manahub.core.model.Friend
import com.mmg.manahub.core.model.MagicSet
import com.mmg.manahub.core.model.MtgColor
import com.mmg.manahub.core.model.NudgeTrigger
import com.mmg.manahub.core.model.PLAYABLE_SET_TYPES
import com.mmg.manahub.core.model.PreferredCurrency
import com.mmg.manahub.core.model.QuickStartAction
import com.mmg.manahub.core.model.Rarity
import com.mmg.manahub.core.model.TradeSuggestion
import com.mmg.manahub.core.model.WidgetSize
import com.mmg.manahub.core.model.news.NewsFilterPrefs
import com.mmg.manahub.core.model.news.NewsItem
import com.mmg.manahub.core.model.news.SourceType
import com.mmg.manahub.feature.communitydecks.domain.usecase.SearchCommunityDecksUseCase
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
import com.mmg.manahub.core.domain.usecase.home.GetAccountNudgeUseCase
import com.mmg.manahub.feature.home.presentation.HomeViewModel.Companion.DISCOVER_RANDOM_QUERY
import com.mmg.manahub.feature.home.presentation.HomeViewModel.Companion.MAX_NEWS
import com.mmg.manahub.core.model.news.ContentSource
import com.mmg.manahub.feature.news.domain.usecase.GetNewsFeedUseCase
import com.mmg.manahub.feature.news.domain.usecase.ManageSourcesUseCase
import com.mmg.manahub.feature.news.domain.usecase.RefreshNewsFeedUseCase
import com.mmg.manahub.core.domain.repository.WishlistRepository
import com.mmg.manahub.core.domain.repository.UserCardRepository
import com.mmg.manahub.core.domain.repository.OpenForTradeRepository
import com.mmg.manahub.core.domain.repository.TradeSuggestionsRepository
import com.mmg.manahub.core.domain.repository.FriendRepository
import com.mmg.manahub.core.domain.repository.PlaytestRepository
import com.mmg.manahub.core.data.repository.TradesRepository
import com.mmg.manahub.core.model.TradeProposal
import com.mmg.manahub.core.model.TradeStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.toLocalDateTime

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
    private val draftRepository: DraftRepository,
    private val wishlistRepository: WishlistRepository,
    private val getAccountNudgeUseCase: GetAccountNudgeUseCase,
    private val gamificationRepository: GamificationRepository,
    // Home feature overhaul (2026-07-13) — real data sources replacing Phase 1/2/3 stubs.
    private val userCardRepository: UserCardRepository,
    private val tradesRepository: TradesRepository,
    private val openForTradeRepository: OpenForTradeRepository,
    private val tradeSuggestionsRepository: TradeSuggestionsRepository,
    private val friendRepository: FriendRepository,
    private val playtestRepository: PlaytestRepository,
    // Backend & Performance Optimization plan, WS1+WS3 Part B item 9 (2026-07-28) — see the
    // `syncManager.syncState` gate in `init` below.
    private val syncManager: SyncManager,
    // Home widget board overhaul, TASK 5b — reused as-is from the Community Decks island
    // (communityDecksKoinModule), no parallel data path.
    private val searchCommunityDecksUseCase: SearchCommunityDecksUseCase,
    // Deck Doctor Community/Archetype plan, Phase 5 — appended last (see `project_archetype_engine`
    // memory's "append new optional params at the end" rule for classes with positional-arg call
    // sites; this project's tests use named args throughout, but the convention is kept anyway).
    private val communityAggregateRepository: com.mmg.manahub.core.domain.repository.CommunityAggregateRepository? = null,
    // Daily Puzzle (ADR-006), Batch B2 — appended last, same convention as above. Nullable with a
    // null default (rather than required) so HomeViewModelTest's existing `buildViewModel()` (which
    // does not pass these two) keeps compiling unchanged; a null value degrades dailyPuzzleFlow to
    // Unavailable, same graceful-degradation shape as communityAggregateRepository above.
    private val getTodayPuzzleUseCase: com.mmg.manahub.feature.puzzle.domain.usecase.GetTodayPuzzleUseCase? = null,
    private val getPuzzleResultUseCase: com.mmg.manahub.feature.puzzle.domain.usecase.GetPuzzleResultUseCase? = null,
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
     * Guards the First Steps completion-flag DataStore write (TASK 3) so a single app session
     * never issues it more than once, even if [buildUiState] re-runs multiple times while the
     * write is still in flight.
     */
    private var firstStepsCompletionMarkSeenDispatched = false

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

    private var fetchDiscoverJob: Job? = null
    private var fetchRandomCardJob: Job? = null

    // ── First Steps carousel ──────────────────────────────────────────────────

    /** Emits the set of step IDs the user has explicitly skipped. */
    private val skippedFirstStepsFlow: Flow<Set<String>> =
        userPrefsDataStore.observeSkippedFirstSteps()
            .distinctUntilChanged()

    /**
     * Whether the First Steps "You're all set!" completion card has already been shown once
     * (Home widget board overhaul, TASK 3).
     */
    private val firstStepsCompletionSeenFlow: Flow<Boolean> =
        userPrefsDataStore.firstStepsCompletionSeenFlow
            .distinctUntilChanged()

    // ── Authentication ────────────────────────────────────────────────────────

    private val isAuthenticatedFlow: StateFlow<Boolean> =
        authRepository.sessionState
            .map { it is SessionState.Authenticated && !it.user.isAnonymous }
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Current authenticated user id, or null while signed out. Used for Trades inbox filtering. */
    private val currentUserIdFlow: StateFlow<String?> =
        authRepository.sessionState
            .map { (it as? SessionState.Authenticated)?.user?.id }
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Up to 5 recent friends, shared by [socialExtrasFlow] (FRIENDS widget) AND
     * [suggestionPreviewsFlow] (counterparty-name resolution) so both consumers subscribe to a
     * SINGLE upstream fetch instead of duplicating [FriendRepository.observeFriends]. Null while
     * loading (Home widget board overhaul, TASK 7b); declared BEFORE both consumers (property
     * init order).
     */
    private val friendsFlow: StateFlow<List<Friend>?> =
        isAuthenticatedFlow.flatMapLatest { authed ->
            if (!authed) {
                flowOf(emptyList())
            } else {
                friendRepository.observeFriends()
                    .map<List<Friend>, List<Friend>?> { it }
                    .onStart { emit(null) }
                    .catch { emit(emptyList()) }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // ── Derived source flows ────────────────────────────────────────────────────

    private val currencyFlow: StateFlow<PreferredCurrency> =
        userPrefsDataStore.preferredCurrencyFlow
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PreferredCurrency.USD)

    private val collectionStatsFlow =
        currencyFlow.flatMapLatest { currency ->
            statsRepository.observeCollectionStats(currency)
        }.distinctUntilChanged()

    /** Persisted news filter selection, shared with the full News screen. */
    private val newsFiltersFlow: StateFlow<NewsFilterPrefs> =
        userPrefsDataStore.observeNewsFilters()
            .distinctUntilChanged()
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
                // Map persisted widgets back to UI instances, migrating the retired legacy
                // "social_hub" token into [HomeWidgetType.FRIENDS] + [HomeWidgetType.COMMUNITY_DECKS]
                // (Home widget board overhaul, TASK 5c) and dropping any other id that no longer
                // maps to a known widget type (removed in a newer app version).
                .map { persisted -> persisted.toInstancesWithMigration() }
        }

    // ── Stats / discover / social snapshots (catch-isolated) ────────────────────

    /**
     * Shared session history [StateFlow]. Declared before [performanceFlow] and
     * [statsSnapshotFlow] (property init order) so both consumers can reference it
     * without subscribing to the same Room live-query twice.
     */
    private val historyFlow: StateFlow<List<SessionHistoryEntry>> =
        gameSessionRepository.observeLocalSessionHistory(HISTORY_LIMIT)
            .distinctUntilChanged()
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
                    is com.mmg.manahub.core.model.DataResult.Success -> result.data.take(
                        LATEST_SETS_LIMIT
                    )

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
        combine(
            discoverSetFlow,
            discoverCardsFlow,
            discoverLoadStateFlow
        ) { set, cards, loadState ->
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

    /**
     * Actual matched-card previews for the Trades Hub Suggestions section (Home widget board
     * overhaul, TASK 4b — replaces the old count-only [TradeSuggestionsRepository.getSuggestions]
     * size read). [TradeSuggestionsRepository.getSuggestions] is suspend-only, so this re-fetches
     * whenever [friendsFlow] (for counterparty-name resolution) or the current user id settles —
     * both are near-static after their first load, so this does not hot-loop. Declared as a member
     * [StateFlow] for the SAME reason documented on [latestSetsFlow]: a cold `flow{}` invoked fresh
     * per combine never refreshes once collected. Declared BEFORE [socialSnapshotFlow] (property
     * init order — referenced transitively through [tradesSnapshotFlow]).
     */
    private val suggestionPreviewsFlow: StateFlow<List<TradeSuggestionPreview>?> =
        isAuthenticatedFlow.flatMapLatest { authed ->
            if (!authed) {
                flowOf(emptyList())
            } else {
                combine(friendsFlow, currentUserIdFlow) { friends, userId -> friends to userId }
                    .flatMapLatest { (friends, userId) ->
                        flow {
                            emit(null)
                            val suggestions =
                                runCatching { tradeSuggestionsRepository.getSuggestions() }
                                    .getOrNull()?.getOrNull().orEmpty()
                            emit(buildSuggestionPreviews(suggestions, friends.orEmpty(), userId))
                        }
                    }
            }
        }.catch {
            crashlytics.setCustomKey("home_flow_error_source", "trade_suggestions")
            recordSafeNonFatal("home_flow_trade_suggestions", it)
            emit(emptyList())
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Resolves up to [HOME_TRADE_SUGGESTION_PREVIEW_LIMIT] [TradeSuggestion]s to rich previews: the
     * matched card (from the LOCAL cache only — never a network fetch here) and the counterparty's
     * nickname (from [friends], matched by whichever side of the suggestion is NOT [myUserId]). A
     * suggestion whose card cannot be resolved locally is dropped — unlike the community-wishlist
     * widget's degrade-instead-of-drop rule, a thumbnail row is meaningless without an image, so
     * dropping is the correct behaviour here.
     */
    private suspend fun buildSuggestionPreviews(
        suggestions: List<TradeSuggestion>,
        friends: List<Friend>,
        myUserId: String?,
    ): List<TradeSuggestionPreview> {
        if (suggestions.isEmpty()) return emptyList()
        val distinctSuggestions = suggestions.distinctBy {
            "${it.offeringUserId}|${it.wishingUserId}|${it.cardId}|${it.userCardId}"
        }
        val capped = distinctSuggestions.take(HOME_TRADE_SUGGESTION_PREVIEW_LIMIT)
        val cardIds = capped.map { it.cardId }.distinct()
        val cardsById = runCatching { cardRepository.getCardsByIds(cardIds) }
            .getOrElse { emptyList() }
            .associateBy { it.scryfallId }
        val friendsByUserId = friends.associateBy { it.userId }
        return capped.mapNotNull { suggestion ->
            val card = cardsById[suggestion.cardId] ?: return@mapNotNull null
            val counterpartyId = if (suggestion.wishingUserId == myUserId) {
                suggestion.offeringUserId
            } else {
                suggestion.wishingUserId
            }
            // Generate a unique ID for the suggestion match (TASK 4b crash fix).
            val uniqueId =
                "${suggestion.offeringUserId}|${suggestion.wishingUserId}|${suggestion.cardId}|${suggestion.userCardId}"
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
     * Social/trades slice. Every source here is real (Home feature overhaul Phase 1): the Trades
     * Hub bundle, the round-aware active tournament, the wishlist snapshot, and the
     * friend-count/pending-request extras. The old community-stats (most-wishlisted/milestones)
     * source was RETIRED (Home widget board overhaul, TASK 5c — low-value slides dropped when
     * SOCIAL_HUB was split into FRIENDS + COMMUNITY_DECKS); `CommunityStatsRepositoryImpl` stays
     * registered in Koin (dormant) but Home no longer consumes it.
     */
    private val socialSnapshotFlow: Flow<SocialSnapshot> =
        isAuthenticatedFlow.flatMapLatest { authed ->
            combine(
                tradesSnapshotFlow(authed),
                activeTournamentFlow(authed),
                wishlistFlow(authed),
                socialExtrasFlow(authed),
            ) { trades, tournament, wishlist, extras ->
                SocialSnapshot(
                    trades = trades,
                    activeTournament = tournament,
                    wishlist = wishlist,
                    extras = extras,
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
                    gamificationRepository.observeDailyActivityStreak()
                        .catch { emit(DEFAULT_STREAK) },
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

    /**
     * Trade inbox summary: pending proposals addressed TO the current user
     * (`status == PROPOSED && receiverId == currentUserId`), reusing the SAME
     * [TradesRepository.observeActiveProposals] flow the Trades screen itself observes — no
     * second data path (Home feature overhaul Phase 1.1, fixes F-1).
     */
    private fun tradeSummaryFlow(authed: Boolean): Flow<TradeSummary?> =
        if (!authed) flowOf(null)
        else currentUserIdFlow.flatMapLatest { userId ->
            if (userId == null) flowOf(null)
            else tradesRepository.observeActiveProposals().map { proposals ->
                val pending =
                    proposals.filter { it.status == TradeStatus.PROPOSED && it.receiverId == userId }
                if (pending.isEmpty()) {
                    TradeSummary(pendingCount = 0, latestItemCount = null)
                } else {
                    val newest = pending.maxByOrNull { it.createdAt }
                    TradeSummary(pendingCount = pending.size, latestItemCount = newest?.items?.size)
                }
            }
        }.catch {
            crashlytics.setCustomKey("home_flow_error_source", "trade_summary")
            recordSafeNonFatal("home_flow_trade_summary", it)
            emit(null)
        }

    /**
     * Bundles the Open-for-Trade count + estimated value + a few card thumbnails for
     * [tradesSnapshotFlow]'s inner combine (Home widget board overhaul, TASK 4b).
     */
    private data class OpenForTradeSummary(
        val count: Int,
        val valueDisplay: String?,
        val cards: List<DiscoverCard> = emptyList(),
    )

    /**
     * Null while loading (Home widget board overhaul, TASK 7b) — an unauthenticated user or a
     * genuinely-empty local list both resolve immediately to a non-null, zeroed summary (a
     * RESOLVED state, not "still loading").
     */
    private fun openForTradeSummaryFlow(authed: Boolean): Flow<OpenForTradeSummary?> =
        if (!authed) {
            flowOf(OpenForTradeSummary(0, null))
        } else {
            combine(openForTradeRepository.observeLocal(), currencyFlow) { entries, currency ->
                if (entries.isEmpty()) return@combine OpenForTradeSummary(0, null)
                val totalUsd = entries.sumOf { (it.card?.priceUsd ?: 0.0) * it.quantity }
                val totalEur = entries.sumOf { (it.card?.priceEur ?: 0.0) * it.quantity }
                val display = if (totalUsd <= 0.0 && totalEur <= 0.0) {
                    null
                } else {
                    PriceFormatter.formatFromScryfall(
                        priceUsd = totalUsd,
                        priceEur = totalEur,
                        preferredCurrency = currency
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
                OpenForTradeSummary(
                    count = entries.sumOf { it.quantity },
                    valueDisplay = display,
                    cards = cards
                )
            }
                .map<OpenForTradeSummary, OpenForTradeSummary?> { it }
                .onStart { emit(null) }
                .catch {
                    crashlytics.setCustomKey("home_flow_error_source", "open_for_trade")
                    recordSafeNonFatal("home_flow_open_for_trade", it)
                    emit(OpenForTradeSummary(0, null))
                }
        }

    /** Bundles the four Trades Hub data sources into one typed combine. */
    private data class TradesSnapshot(
        val summary: TradeSummary?,
        val suggestionPreviews: List<TradeSuggestionPreview>?,
        val openForTrade: OpenForTradeSummary?,
        val recentTrades: List<TradeProposal>?,
    )

    private fun tradesSnapshotFlow(authed: Boolean): Flow<TradesSnapshot> = combine(
        tradeSummaryFlow(authed),
        suggestionPreviewsFlow,
        openForTradeSummaryFlow(authed),
        recentTradesFlow(authed),
    ) { summary, suggestionPreviews, openForTrade, recentTrades ->
        TradesSnapshot(summary, suggestionPreviews, openForTrade, recentTrades)
    }

    /** Null while loading (Home widget board overhaul, TASK 7b). */
    private fun recentTradesFlow(authed: Boolean): Flow<List<TradeProposal>?> =
        if (!authed) {
            flowOf(emptyList())
        } else {
            tradesRepository.observeActiveProposals()
                .map { it.sortedByDescending { p -> p.updatedAt }.take(3) }
                .map<List<TradeProposal>, List<TradeProposal>?> { it }
                .onStart { emit(null) }
                .catch { emit(emptyList()) }
        }

    /** Bundles the friend count + latest pending friend request + the shared [friendsFlow]. */
    private data class SocialExtras(
        val friendCount: Int,
        val latestFriendRequestName: String?,
        val friends: List<Friend>?,
    )

    private fun socialExtrasFlow(authed: Boolean): Flow<SocialExtras> =
        if (!authed) {
            flowOf(
                SocialExtras(
                    friendCount = 0,
                    latestFriendRequestName = null,
                    friends = emptyList()
                )
            )
        } else {
            combine(
                friendRepository.observeFriendCount().catch { emit(0) },
                friendRepository.observePendingRequests().catch { emit(emptyList()) },
                friendsFlow,
            ) { friendCount, pendingRequests, friends ->
                SocialExtras(
                    friendCount = friendCount,
                    latestFriendRequestName = pendingRequests.firstOrNull()?.fromNickname,
                    friends = friends,
                )
            }
        }

    /**
     * Current-round-aware active-tournament summary (Home feature overhaul Phase 1.2.e, fixes
     * F-3). Tournaments are local, so this is available regardless of auth state. The round is a
     * READ-ONLY query ([TournamentRepository.observeCurrentRound]) — never a write inside this
     * combine transformer. Standing is intentionally omitted (no cheap per-player standing
     * computation is available here) rather than showing a fake value.
     */
    private fun activeTournamentFlow(authed: Boolean): Flow<TournamentSummary?> =
        tournamentRepository.observeTournaments()
            .map { tournaments -> tournaments.firstOrNull { it.status == "ACTIVE" || it.status == "SETUP" } }
            .flatMapLatest { active ->
                if (active == null) {
                    flowOf(null)
                } else {
                    tournamentRepository.observeCurrentRound(active.id)
                        .map { round ->
                            TournamentSummary(
                                tournamentId = active.id,
                                name = active.name,
                                round = round,
                                standing = null
                            )
                        }
                }
            }
            .catch { emit(null) }

    // Wired to WishlistRepository.observeLocal() (count + currency-formatted estimated value).
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

    /**
     * Newest-first collection additions for the RECENTLY_ADDED widget (Home feature overhaul
     * Phase 2.1). Card image/name resolve through the existing Room join
     * ([UserCardRepository.observeRecentlyAdded]) — no network call. Null until the first emission
     * lands (Home widget board overhaul, TASK 7b); catch-isolated so a failing source never
     * collapses the board.
     */
    private val recentlyAddedFlow: Flow<List<RecentlyAddedCard>?> =
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
            .map<List<RecentlyAddedCard>, List<RecentlyAddedCard>?> { it }
            .onStart { emit(null) }
            .catch {
                crashlytics.setCustomKey("home_flow_error_source", "recently_added")
                recordSafeNonFatal("home_flow_recently_added", it)
                emit(emptyList())
            }

    /**
     * Total saved playtest sessions across every deck — drives STEP_FIRST_PLAYTEST_DECK's
     * auto-hide condition (Home feature overhaul Phase 2.2). Catch-isolated: a failure degrades
     * to 0 (step stays visible) rather than collapsing the board.
     */
    private val totalPlaytestCountFlow: Flow<Int> =
        playtestRepository.observeTotalTestCount()
            .distinctUntilChanged()
            .catch { emit(0) }

    private val uiState: StateFlow<HomeUiState> = run {
        val libraryFlow = combine(
            collectionStatsFlow,
            // Null until the first Room emission lands (Home widget board overhaul, TASK 7b) —
            // distinguishes "still loading" from "the user genuinely has zero decks" for
            // DecksShelfWidget/buildVisibleSteps.
            deckRepository.observeAllDeckSummaries()
                .map<List<DeckSummary>, List<DeckSummary>?> { it }
                .onStart { emit(null) }
                .catch { emit(emptyList()) },
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
                // True once the FIRST real (non-Loading) session state lands (Home widget board
                // overhaul, TASK 7a) — gates account-gated widgets so a still-resolving session is
                // never mistaken for a definitive "signed out".
                authResolved = session !is SessionState.Loading,
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
            totalPlaytestCountFlow,
            firstStepsCompletionSeenFlow,
        ) { args ->
            // combine(8 flows) uses the vararg overload — destructure manually.
            @Suppress("UNCHECKED_CAST")
            val library = args[0] as LibrarySnapshot

            @Suppress("UNCHECKED_CAST")
            val activity = args[1] as ActivitySnapshot

            @Suppress("UNCHECKED_CAST")
            val account = args[2] as AccountSnapshot

            @Suppress("UNCHECKED_CAST")
            val quickStart = args[3] as List<QuickStartAction>

            @Suppress("UNCHECKED_CAST")
            val playerName = args[4] as String

            @Suppress("UNCHECKED_CAST")
            val skipped = args[5] as Set<String>

            @Suppress("UNCHECKED_CAST")
            val playtestTotal = args[6] as Int

            @Suppress("UNCHECKED_CAST")
            val firstStepsCompletionSeen = args[7] as Boolean

            val effectivePlayerName = account.nickname ?: playerName
            CoreSnapshot(
                library, activity, account, quickStart, effectivePlayerName, skipped, playtestTotal,
                firstStepsCompletionSeen,
            )
        }

        // Bundle the Phase 2/3 data slices (+ recentlyAddedFlow, Home feature overhaul Phase 2.1)
        // into one combine, then fold that bundle into the core slice. Six flows exceeds the
        // typed combine overloads (max 5) — uses the SAME vararg-destructure pattern as coreFlow
        // above, kept internally typed via the manual casts.
        val dataFlow = combine(
            layoutFlow,
            statsSnapshotFlow,
            discoverSnapshotFlow,
            socialSnapshotFlow,
            gamificationSnapshotFlow,
            recentlyAddedFlow,
        ) { args ->
            @Suppress("UNCHECKED_CAST")
            val layout = args[0] as List<WidgetInstance>

            @Suppress("UNCHECKED_CAST")
            val stats = args[1] as StatsSnapshot

            @Suppress("UNCHECKED_CAST")
            val discover = args[2] as DiscoverSnapshot

            @Suppress("UNCHECKED_CAST")
            val social = args[3] as SocialSnapshot

            @Suppress("UNCHECKED_CAST")
            val gamification = args[4] as GamificationSnapshot

            @Suppress("UNCHECKED_CAST")
            val recentlyAdded = args[5] as List<RecentlyAddedCard>?

            DataBundle(
                layout = layout,
                stats = stats,
                discover = discover,
                social = social,
                gamification = gamification,
                recentlyAdded = recentlyAdded,
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
                recentlyAdded = data.recentlyAdded,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HomeUiState(isLoading = true, hero = HomeHeroState.Loading),
        )
    }

    /** Public UI state. */
    val state: StateFlow<HomeUiState> get() = uiState

    /**
     * Home trending widget (Deck Doctor Community/Archetype plan, Phase 5) — the top-3 commanders
     * of the week from the `manahub-community` Worker's `/v1/trending`. Kept as an INDEPENDENT
     * `StateFlow`, NOT threaded into the [uiState] combine chain: that chain is already
     * documented as the most error-prone surface in this ViewModel (CLAUDE.md's "Home widget
     * board" note — combine arity / property-init-order gotchas), and this widget's data has no
     * dependency on anything else in [HomeUiState]. Mirrors the `deckStatsFlow`/`playerNameFlow`
     * sibling-StateFlow precedent in `DeckStudioViewModel`.
     *
     * "Silently hidden (not an error state) on Worker failure — log only" (plan Phase 5): any
     * failure (`communityAggregateRepository` null, the repo call throwing, or a
     * [com.mmg.manahub.core.model.DataResult.Error]) resolves to `null`, which
     * [com.mmg.manahub.feature.home.presentation.TrendingCommandersWidget] treats as "don't render
     * this widget" — never an inline error UI.
     */
    val trendingFlow: StateFlow<com.mmg.manahub.core.model.TrendingSnapshot?> =
        flow {
            val repo = communityAggregateRepository
            if (repo == null) {
                emit(null)
                return@flow
            }
            // WS1+WS3 Part B item 9 (2026-07-28) — see the Discover-row `init` gate above for why.
            awaitSyncWindow("trending")
            val result = runCatching { repo.getTrending() }.getOrElse {
                crashlytics.log("home_trending_widget_failed")
                null
            }
            emit((result as? com.mmg.manahub.core.model.DataResult.Success)?.data)
        }.catch {
            crashlytics.log("home_trending_widget_failed")
            emit(null)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    /**
     * Home COMMUNITY_DECKS widget's selected category (TASK 5b). Kept as an INDEPENDENT
     * `StateFlow`, mirroring [trendingFlow]'s documented rationale: this widget's data has no
     * dependency on anything else in [HomeUiState], and the main combine chain is already the
     * most error-prone surface in this ViewModel.
     */
    val communityDecksCategoryFlow: StateFlow<HomeCommunityDeckCategory> =
        userPrefsDataStore.homeCommunityDecksCategoryFlow
            .map { HomeCommunityDeckCategory.fromPersistedId(it) }
            .catch { emit(HomeCommunityDeckCategory.POPULAR) }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                HomeCommunityDeckCategory.POPULAR
            )

    /**
     * Home COMMUNITY_DECKS widget's deck list for the currently-selected category (TASK 5b). Null
     * while loading (TASK 7b); an empty list once loaded means the category genuinely returned no
     * decks. Re-fetches whenever [communityDecksCategoryFlow] changes.
     */
    val communityDecksFlow: StateFlow<List<CommunityDeckSummary>?> =
        communityDecksCategoryFlow.flatMapLatest { category ->
            flow {
                emit(null)
                // WS1+WS3 Part B item 9 (2026-07-28) — see the Discover-row `init` gate above for why.
                awaitSyncWindow("community_decks")
                val filters = CommunityDeckSearchFilters(
                    orderBy = category.orderBy,
                    primersOnly = category.primersOnly,
                    page = 1,
                    pageSize = HOME_COMMUNITY_DECKS_LIMIT,
                )
                val result = runCatching { searchCommunityDecksUseCase(filters) }.getOrNull()
                val decks =
                    (result as? com.mmg.manahub.core.model.DataResult.Success)?.data?.decks.orEmpty()
                if (result !is com.mmg.manahub.core.model.DataResult.Success) {
                    crashlytics.log("home_community_decks_load_failed")
                }
                emit(decks.take(HOME_COMMUNITY_DECKS_LIMIT))
            }
        }.catch {
            crashlytics.log("home_community_decks_load_failed")
            emit(emptyList())
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Home DAILY_PUZZLE widget preview (ADR-006, Batch B2). Kept as an INDEPENDENT `StateFlow`,
     * mirroring [trendingFlow]/[communityDecksFlow]'s documented rationale: this widget's data has
     * no dependency on anything else in [HomeUiState], and the main combine chain is already the
     * most error-prone surface in this ViewModel.
     *
     * Unlike [trendingFlow]/[communityDecksFlow] (which collapse every failure to `null`/empty and
     * hide the widget silently), this flow distinguishes [DailyPuzzleWidgetState.Loading] /
     * [DailyPuzzleWidgetState.Loaded] / [DailyPuzzleWidgetState.Unavailable] so the widget can show
     * a real unavailable message — see [DailyPuzzleWidgetState]'s KDoc for why that deviation is
     * intentional here.
     */
    val dailyPuzzleFlow: StateFlow<DailyPuzzleWidgetState> =
        flow {
            emit(DailyPuzzleWidgetState.Loading)
            val getTodayPuzzle = getTodayPuzzleUseCase
            if (getTodayPuzzle == null) {
                emit(DailyPuzzleWidgetState.Unavailable)
                return@flow
            }
            // WS1+WS3 Part B item 9 (2026-07-28) — see the Discover-row `init` gate above for why.
            awaitSyncWindow("daily_puzzle")

            // Mirrors the server's UTC rollover boundary (ADR-006 Decision 2) so the local-progress
            // read below targets the right row even before the network fetch confirms the real date.
            val today = kotlinx.datetime.Clock.System.now()
                .toLocalDateTime(kotlinx.datetime.TimeZone.UTC).date
            val localResult = getPuzzleResultUseCase?.let { useCase ->
                runCatching { useCase(today) }.getOrNull()
            }

            when (val result = runCatching { getTodayPuzzle() }.getOrNull()) {
                is com.mmg.manahub.core.model.DataResult.Success -> {
                    val puzzle = result.data
                    // Only trust a local row that matches THIS server-confirmed puzzle date — a
                    // stale/guessed-date row must never be reported as today's progress.
                    val matchingLocal = localResult?.takeIf { it.puzzleDate == puzzle.date }
                    emit(
                        DailyPuzzleWidgetState.Loaded(
                            puzzleType = puzzle.type,
                            attemptsUsed = matchingLocal?.attempts ?: 0,
                            solved = matchingLocal?.solved ?: false,
                        )
                    )
                }
                else -> {
                    crashlytics.log("home_daily_puzzle_widget_failed")
                    emit(DailyPuzzleWidgetState.Unavailable)
                }
            }
        }.catch {
            crashlytics.log("home_daily_puzzle_widget_failed")
            emit(DailyPuzzleWidgetState.Unavailable)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DailyPuzzleWidgetState.Loading,
        )

    /**
     * Waits for [syncManager]'s sync state to leave [SyncState.SYNCING] before the caller proceeds
     * (WS1+WS3 Part B item 9's "serialize the login window" gate), logging a `home_sync_window_deferred`
     * breadcrumb ONLY when the wait was real (elapsed strictly greater than
     * [SYNC_WINDOW_LOG_THRESHOLD_MS]) — the common case (sync already IDLE/SUCCESS/ERROR) resolves
     * `first{}` immediately and would just be noise.
     *
     * WS7 telemetry (backend-performance-optimization-plan.md, 2026-07-29): this is the empirical
     * proof that the gate is actually deferring real work in the field, not just theoretically
     * present in code — a source-tagged breadcrumb + duration, not a per-call event flood.
     *
     * @param source one of `"trending"` / `"community_decks"` / `"discover_seed"` / `"daily_puzzle"`
     *   (ADR-006, Batch B2) — identifies which call site deferred, without embedding any free text.
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
        // Trigger a background trades refresh so TRADES_HUB's Inbox has real pending-proposal data
        // even on first open, before the user has ever visited Trades or a friend's profile (the
        // only other callers of TradesRepository.refreshProposals — observeActiveProposals() is
        // in-memory-only and starts empty on every process start). Guarded to authenticated,
        // non-anonymous users (mirrors isAuthenticatedFlow's gate); waits past the initial Loading
        // session state so a still-resolving session isn't mistaken for signed-out. Mirrors the news
        // warm-up above — never blocks startup.
        viewModelScope.launch {
            val session = authRepository.sessionState.first { it !is SessionState.Loading }
            val userId = (session as? SessionState.Authenticated)
                ?.takeIf { !it.user.isAnonymous }
                ?.user?.id
            if (userId != null) {
                val refreshResult = runCatching { tradesRepository.refreshProposals(userId) }
                refreshResult.exceptionOrNull()?.let { error ->
                    recordSafeNonFatal("home_trades_refresh", error)
                    crashlytics.log("home_trades_refresh_failed")
                }
                // TASK 4a: refreshProposals only fetches proposal METADATA (no items) — hydrate the
                // small set of proposals actually surfaced on Home so the Inbox/RecentActivity
                // sections never render a known-wrong "0 items" for a real pending trade.
                if (refreshResult.isSuccess) {
                    hydrateTradeItemCounts()
                }
            }
        }
        // Pick a random playable set (>10 cards) to seed the Discover row BEFORE the first fetch,
        // then populate the Discover row (lazy once-guard) and the independent Random card widget.
        // On failure/empty the set stays null and the fetch falls back to the global random query.
        viewModelScope.launch {
            // Backend & Performance Optimization plan, WS1+WS3 Part B item 9 (2026-07-28):
            // "serialize the login window" — defer this non-essential Discover/Random-card Scryfall
            // fetch until any in-progress collection sync (`ensureCardsExist`, deck cards) finishes,
            // so it never competes with the login-window burst for the same rate-limit budget. A
            // `StateFlow.first{}` on a sync that is already IDLE/SUCCESS/ERROR resolves immediately
            // (no delay in the common case — sync only runs right after sign-in/app-open, briefly).
            awaitSyncWindow("discover_seed")
            seedRandomDiscoverSet()
            fetchDiscoverCards(forceRefresh = false)
            fetchRandomCard()
        }
    }

    /**
     * Hydrates real item counts for the proposals actually surfaced on Home (Home widget board
     * overhaul, TASK 4a).
     *
     * Root cause: [TradesRepository.refreshProposals] fetches proposal METADATA ONLY — it never
     * touches the `trade_items` table, so every proposal's `items` list stays whatever was already
     * cached (empty on a cold app start). The ONLY sanctioned path that fetches real items is
     * [TradesRepository.refreshProposalThread] (per-thread), normally triggered by opening a
     * thread in the Trades screen. Home never did that, so `TradeSummary.latestItemCount` (and any
     * `TradeProposal.items.size` read) silently reported 0 even for a real pending trade with
     * items.
     *
     * Fix: after the metadata refresh lands, fan out [TradesRepository.refreshItemsForThread] over
     * the newest few distinct root-proposal threads (bounded by [HOME_TRADE_THREAD_HYDRATE_LIMIT])
     * — the same threads the Inbox/RecentActivity sections actually render — concurrently. No
     * backend/RPC change is required; this stays entirely within the existing repository contract.
     *
     * Backend & Performance Optimization plan, WS4a finding 2 (2026-07-28): this used to call
     * [TradesRepository.refreshProposalThread], which ALSO re-fetches the caller's full proposal
     * table before hydrating items — up to [HOME_TRADE_THREAD_HYDRATE_LIMIT] duplicate full-table
     * reads within milliseconds of the [refreshProposals] call right above this function's only
     * call site. [TradesRepository.refreshItemsForThread] skips that redundant metadata re-fetch;
     * it is safe here specifically because metadata was just refreshed by [refreshProposals].
     */
    private suspend fun hydrateTradeItemCounts() {
        val cached =
            runCatching { tradesRepository.observeAllProposals().first() }.getOrElse { emptyList() }
        val rootIds = cached
            .sortedByDescending { it.updatedAt }
            .map { it.rootProposalId }
            .distinct()
            .take(HOME_TRADE_THREAD_HYDRATE_LIMIT)
        if (rootIds.isEmpty()) return
        coroutineScope {
            rootIds.map { rootId ->
                async {
                    runCatching { tradesRepository.refreshItemsForThread(rootId) }
                        .exceptionOrNull()?.let { error ->
                            recordSafeNonFatal("home_trades_hydrate_items", error)
                        }
                }
            }.awaitAll()
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
        fetchDiscoverJob?.cancel()
        val set = discoverSetFlow.value
        val query = if (set != null) {
            "set:${set.code} -is:digital order:random"
        } else {
            DISCOVER_RANDOM_QUERY
        }
        discoverLoadStateFlow.value = DiscoverLoadState.LOADING
        // Clear the row so the spinner shows immediately on a refresh and cards stream in fresh.
        discoverCardsFlow.value = emptyList()
        fetchDiscoverJob = viewModelScope.launch {
            // bypassCache = true avoids the in-memory map, but Scryfall's CDN still caches the page.
            // Taking the first N of a CDN-cached page means refresh does nothing; we must shuffle client-side.
            val outcome =
                runCatching { cardRepository.searchCards(query, page = 1, bypassCache = true) }
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
            // Map all cards first to update the flow once (batching).
            // De-dup by id (a repeated scryfallId would crash the LazyRow's stable-key contract).
            val seen = HashSet<String>()
            val mappedCards = fetched.mapNotNull { card ->
                if (seen.add(card.scryfallId)) {
                    DiscoverCard(
                        id = card.scryfallId,
                        scryfallId = card.scryfallId,
                        name = card.name,
                        // Full card image now (was art-crop), fall back to art-crop if absent.
                        imageUrl = card.imageNormal ?: card.imageArtCrop,
                        typeLine = card.typeLine,
                    )
                } else null
            }
            
            if (mappedCards.isNotEmpty()) {
                discoverCardsFlow.value = mappedCards
                discoverLoadStateFlow.value = DiscoverLoadState.LOADED
            } else {
                discoverLoadStateFlow.value = DiscoverLoadState.FAILED
            }
        }
    }

    /**
     * Fetches a fresh single random card for the Random card widget. Always re-fetches (no
     * once-guard) so a manual refresh always surfaces a new card. Failures degrade to
     * [DiscoverLoadState.FAILED] without clearing the previously-shown card.
     */
    private fun fetchRandomCard() {
        fetchRandomCardJob?.cancel()
        randomCardLoadStateFlow.value = DiscoverLoadState.LOADING
        fetchRandomCardJob = viewModelScope.launch {
            // bypassCache = true avoids the in-memory map, but Scryfall's CDN still caches the page.
            // Taking the first N of a CDN-cached page means refresh does nothing; we must shuffle client-side.
            val outcome = runCatching {
                cardRepository.searchCards(
                    DISCOVER_RANDOM_QUERY,
                    page = 1,
                    bypassCache = true
                )
            }
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

    /** Persists the Home COMMUNITY_DECKS widget's category selection (TASK 5b). */
    private fun selectCommunityDecksCategory(category: HomeCommunityDeckCategory) {
        // Category id is a fixed enum persistedId — safe to log (no PII).
        crashlytics.log("home_community_decks_category_selected: ${category.persistedId}")
        viewModelScope.launch { userPrefsDataStore.saveHomeCommunityDecksCategory(category.persistedId) }
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
            is HomeAction.SelectCommunityDecksCategory -> selectCommunityDecksCategory(action.category)
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
        // Insert immediately after the LAST existing entry of the same category, wherever that
        // category's run currently sits in the layout — NOT by comparing WidgetCategory.ordinal
        // globally. The persisted/default layout is NOT sorted by ordinal (e.g. defaultLayoutSignedIn
        // deliberately places SOCIAL, ordinal 4, before DISCOVER, ordinal 3, to match the funnel
        // order), so an ordinal-ascending `indexOfLast { ordinal <= newOrdinal }` can match the LAST
        // item of an unrelated, lower-ordinal category that sits AFTER this category's own run —
        // splitting the new widget away from its category and breaking the contiguity invariant the
        // gallery's drag&drop relies on. When the category has no existing entries yet, append at
        // the end (a brand-new category run of size 1 is trivially contiguous with itself).
        val lastSameCategoryIndex = current.indexOfLast { it.type.category == type.category }
        val insertIndex =
            if (lastSameCategoryIndex >= 0) lastSameCategoryIndex + 1 else current.size
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
            // Guarantee uniqueness by persistedId before saving to avoid crashing the Lazy keys contract
            // if rapid-fire add/remove actions create a race condition in the mutable list.
            val unique = layout.distinctBy { it.type.persistedId }
            userPrefsDataStore.saveHomeLayout(unique.map { it.toPersisted() })
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
        recentlyAdded: List<RecentlyAddedCard>?,
    ): HomeUiState {
        val collectionStats = core.library.stats
        val deckCount = core.library.decks?.size ?: 0

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
        val friendCount = social.extras.friendCount
        val isProfileComplete = !core.account.avatarUrl.isNullOrBlank()
                && core.playerName != "Wizard"
                && core.playerName.isNotBlank()

        val visibleSteps = buildVisibleSteps(
            isAuthenticated = core.account.isAuthenticated,
            cardCount = cardCount,
            deckCount = deckCountForSteps,
            friendCount = friendCount,
            isProfileComplete = isProfileComplete,
            totalPlaytestCount = core.totalPlaytestCount,
            openForTradeCount = social.trades.openForTrade?.count ?: 0,
            wishlistCount = social.wishlist?.count ?: 0,
            skipped = core.skippedFirstSteps,
        )

        val hero = resolveHero(
            core.activity,
            core.playerName,
            visibleSteps,
            gamification,
            core.firstStepsCompletionSeen
        )
        val nudge = resolveNudge(core.account, collectionStats, deckCount, core.activity.totalGames)

        // TASK 3: persist the completion flag exactly once, the first time the hero actually
        // resolves to the empty-Welcome (completion-card) state while it hasn't been seen before.
        // Guarded by a session-local flag (mirrors sessionContextKeysSet above) so a rapidly
        // re-emitting combine never issues more than one DataStore write per app session.
        if (
            hero is HomeHeroState.Welcome && hero.steps.isEmpty() &&
            !core.firstStepsCompletionSeen && !firstStepsCompletionMarkSeenDispatched
        ) {
            firstStepsCompletionMarkSeenDispatched = true
            crashlytics.log("home_first_steps_completion_seen")
            viewModelScope.launch { userPrefsDataStore.markFirstStepsCompletionSeen() }
        }

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
            authResolved = core.account.authResolved,
            playerName = core.playerName,
            avatarUrl = core.account.avatarUrl,
            // Board
            layout = layout,
            // Phase 2
            lastGameRecap = stats.history.firstOrNull()?.toRecap(),
            playStreak = stats.history.toPlayStreak(),
            winRate = toWinRate(stats),
            bestDeck = toBestDeck(stats, core.library.decks.orEmpty()),
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
            recentlyAdded = recentlyAdded,
            // Phase 3
            tradeSummary = social.trades.summary,
            tradeSuggestionPreviews = social.trades.suggestionPreviews,
            openForTradePreview = social.trades.openForTrade?.let {
                OpenForTradePreview(
                    count = it.count,
                    valueDisplay = it.valueDisplay,
                    cards = it.cards
                )
            },
            activeTournamentSummary = social.activeTournament,
            friendCount = friendCount,
            latestFriendRequestName = social.extras.latestFriendRequestName,
            friends = social.extras.friends,
            recentTrades = social.trades.recentTrades,
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

    /**
     * @param decks The user's decks (already loaded in [LibrarySnapshot]) — joined here to
     *   populate [BestDeckStats.colorIdentity] (fixes F-6). [DeckStats] itself carries no color
     *   data, so the identity is looked up by id from the already-available deck list rather than
     *   adding a new DAO projection.
     */
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
     * Filters [ALL_FIRST_STEPS] down to the steps that:
     *   1. Have not been explicitly dismissed by the user ([skipped] set — tap-to-CTA no longer
     *      dismisses a step; only the carousel's dedicated dismiss affordance does, Home feature
     *      overhaul Phase 2.2), and
     *   2. Meet their show condition based on the current app state.
     *
     * The output list preserves the canonical activation-funnel order defined in
     * [ALL_FIRST_STEPS]. Every condition below is documented as DATA-DRIVEN or DISMISS-ONLY
     * alongside its [FirstStepItem] declaration.
     *
     * @param isAuthenticated Whether the user is signed in (non-anonymous).
     * @param cardCount       Number of unique cards in the local collection.
     * @param deckCount       Number of decks the user has created.
     * @param friendCount     Number of accepted friends — now REAL data (Home feature overhaul
     *   Phase 1.2.d), no longer hardcoded to 0.
     * @param isProfileComplete True when avatar + non-default name are set.
     * @param totalPlaytestCount Total saved playtest sessions across every deck.
     * @param openForTradeCount Count of the local Open-for-Trade list.
     * @param wishlistCount   Count of the local wishlist (0 when signed out — the widget itself
     *   is account-gated).
     * @param skipped         Set of step IDs the user has explicitly dismissed.
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
            // WISHLIST_PROGRESS is ACCOUNT_GATED and wishlistFlow forces wishlistCount to 0 while
            // signed out — without the isAuthenticated gate this step could never auto-hide for a
            // signed-out user (it would sit forever, uncompletable, CTA-ing into a widget they can't
            // use pre-auth). Matches the STEP_FIRST_ADD_FRIEND/STEP_FIRST_REVIEW_FRIEND style above.
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
     * Resolves the hero state following the priority order:
     *   QuestsReady > ActiveDraft > Welcome(steps non-empty)
     *   > Welcome(empty = completion card, ONLY the first time) > Summary.
     *
     * [visibleSteps] drives the Welcome branch: when non-empty the carousel is shown. When empty
     * (all steps done/dismissed), the "You're all set!" completion card
     * ([HomeHeroState.Welcome] with empty steps) is shown EXACTLY ONCE — gated by
     * [firstStepsCompletionSeen] (Home widget board overhaul, TASK 3: the card must stop
     * occupying the hero slot forever once the user has already seen it once). Once seen, this
     * falls through to [HomeHeroState.Summary] regardless of [ActivitySnapshot.totalGames] so the
     * hero always shows something useful rather than repeating the completion card.
     */
    private fun resolveHero(
        activity: ActivitySnapshot,
        playerName: String,
        visibleSteps: List<FirstStepItem>,
        gamification: GamificationSnapshot,
        firstStepsCompletionSeen: Boolean,
    ): HomeHeroState {
        // Force Welcome state for now (user request: only show Welcome and Loading).
        // visibleSteps.isNotEmpty() shows the carousel; empty shows the "You're all set!" card.
        return HomeHeroState.Welcome(steps = visibleSteps)
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

    /**
     * Default widget layouts (Home feature overhaul Phase 2.3 — replaces the pre-overhaul
     * defaults). Applies ONLY when the user has never persisted a layout ([layoutFlow] passes
     * this as the DataStore default, which is never re-applied once a layout exists).
     *
     * Signed-out: demonstrate value fast and support the first-steps funnel with what works
     * without an account — the local collection (reinforces "add your first card") and evergreen
     * discovery content. Signed-in: lead with the user's own data (stats, decks, collection),
     * then social/trade engagement, then evergreen discovery last. Both keep widget categories
     * contiguous ([WidgetCategory] ordinal order) per the VM's category-contiguity invariant.
     * WISHLIST_PROGRESS, CARD_OF_THE_DAY (signed-in), DISCOVER_CARDS (signed-in), the gamification
     * widgets, TRENDING_COMMANDERS, and COMMUNITY_DECKS (TASK 5c) stay gallery-only for signed-in
     * defaults (board length discipline) — all remain fully functional widgets, just not auto-added.
     */
    private val defaultLayoutSignedOut = listOf(
        // Activity
        WidgetInstance(HomeWidgetType.CONTEXT_HERO, WidgetSize.MEDIUM),
        WidgetInstance(HomeWidgetType.QUICK_ACTIONS, WidgetSize.MEDIUM),
        // Collection — the local collection works fully offline and reinforces "add your first card".
        WidgetInstance(HomeWidgetType.COLLECTION_STATS_HUB, WidgetSize.MEDIUM),
        // Discover
        WidgetInstance(HomeWidgetType.COMMUNITY_DECKS, WidgetSize.MEDIUM),

        WidgetInstance(HomeWidgetType.CARD_OF_THE_DAY, WidgetSize.MEDIUM),
        WidgetInstance(HomeWidgetType.DISCOVER_CARDS, WidgetSize.MEDIUM),
        WidgetInstance(HomeWidgetType.LATEST_SETS, WidgetSize.MEDIUM),
        WidgetInstance(HomeWidgetType.RULES_TIP, WidgetSize.MEDIUM),
        WidgetInstance(HomeWidgetType.MTG_NEWS, WidgetSize.MEDIUM),
    )

    private val defaultLayoutSignedIn = listOf(
        // Activity
        WidgetInstance(HomeWidgetType.CONTEXT_HERO, WidgetSize.MEDIUM),
        WidgetInstance(HomeWidgetType.QUICK_ACTIONS, WidgetSize.MEDIUM),
        // Stats
        WidgetInstance(HomeWidgetType.COMMUNITY_DECKS, WidgetSize.MEDIUM),
        // Collection
        WidgetInstance(HomeWidgetType.YOUR_DECKS_SHELF, WidgetSize.MEDIUM),
        WidgetInstance(HomeWidgetType.COLLECTION_STATS_HUB, WidgetSize.MEDIUM),
        WidgetInstance(HomeWidgetType.RECENTLY_ADDED, WidgetSize.MEDIUM),
        // Social
        // TASK 5c: SOCIAL_HUB replaced by FRIENDS (COMMUNITY_DECKS stays gallery-only, matching
        // TRENDING_COMMANDERS' board-length discipline below).
        // Discover
        WidgetInstance(HomeWidgetType.LATEST_SETS, WidgetSize.MEDIUM),
        WidgetInstance(HomeWidgetType.MTG_NEWS, WidgetSize.MEDIUM),
        WidgetInstance(HomeWidgetType.RULES_TIP, WidgetSize.MEDIUM),
        WidgetInstance(HomeWidgetType.DISCOVER_CARDS, WidgetSize.MEDIUM),
        WidgetInstance(HomeWidgetType.CARD_OF_THE_DAY, WidgetSize.MEDIUM),
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
        /** Total saved playtest sessions across every deck (STEP_FIRST_PLAYTEST_DECK condition). */
        val totalPlaytestCount: Int = 0,
        /** Whether the First Steps "You're all set!" completion card has already been shown once. */
        val firstStepsCompletionSeen: Boolean = false,
    )

    private data class LibrarySnapshot(
        val stats: CollectionStats,
        val decks: List<DeckSummary>?,
        val currency: PreferredCurrency,
    )

    private data class ActivitySnapshot(
        val totalGames: Int,
        val activeDraft: DraftState?,
        val activeTournaments: Int,
    )

    private data class AccountSnapshot(
        val isAuthenticated: Boolean,
        /** True once the first real (non-Loading) session state has landed (TASK 7a). */
        val authResolved: Boolean = false,
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
        val recentlyAdded: List<RecentlyAddedCard>?,
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
        val history: List<SessionHistoryEntry>,
        val deckStats: List<DeckStats>,
        val nemesis: EliminationStats?,
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
        val trades: TradesSnapshot,
        val activeTournament: TournamentSummary?,
        val wishlist: WishlistStats?,
        val extras: SocialExtras,
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

        /**
         * [awaitSyncWindow] only logs `home_sync_window_deferred` when the wait exceeded this many
         * ms — filters out the common already-IDLE case (a `StateFlow.first{}` resolving on an
         * uncontended flow still costs a few ms of coroutine dispatch, which isn't a "real" defer).
         */
        private const val SYNC_WINDOW_LOG_THRESHOLD_MS = 100L

        /** Max rows shown by the RECENTLY_ADDED widget (Home feature overhaul Phase 2.1). */
        private const val RECENTLY_ADDED_LIMIT = 10

        /** Max quests previewed in the Home Quests widget. */
        private const val HOME_QUEST_PREVIEW_LIMIT = 3

        /**
         * Max distinct proposal threads hydrated with real items on Home warm-up (TASK 4a) — bounds
         * the [TradesRepository.refreshProposalThread] fan-out to the handful of threads actually
         * rendered by the Inbox/RecentActivity sections.
         */
        private const val HOME_TRADE_THREAD_HYDRATE_LIMIT = 5

        /** Max matched-card previews shown by the Trades Hub Suggestions section (TASK 4b). */
        private const val HOME_TRADE_SUGGESTION_PREVIEW_LIMIT = 6

        /** Max card thumbnails shown by the Trades Hub Open-for-Trade section (TASK 4b). */
        private const val HOME_OPEN_FOR_TRADE_PREVIEW_LIMIT = 8

        /** Page size requested for the Home COMMUNITY_DECKS widget (TASK 5b). */
        private const val HOME_COMMUNITY_DECKS_LIMIT = 10

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
