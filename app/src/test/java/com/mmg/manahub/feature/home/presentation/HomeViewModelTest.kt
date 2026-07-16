package com.mmg.manahub.feature.home.presentation

import app.cash.turbine.test
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.model.CollectionStats
import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.model.DeckSummary
import com.mmg.manahub.core.model.PersistedWidget
import com.mmg.manahub.core.model.PreferredCurrency
import com.mmg.manahub.core.model.QuickStartAction
import com.mmg.manahub.core.model.WidgetSize
import com.mmg.manahub.core.model.news.NewsFilterPrefs
import com.mmg.manahub.core.model.news.NewsItem
import com.mmg.manahub.core.model.news.RefreshResult
import com.mmg.manahub.core.domain.repository.CommunityStatsRepository
import com.mmg.manahub.core.domain.repository.DeckRepository
import com.mmg.manahub.feature.game.domain.repository.GameSessionRepository
import com.mmg.manahub.core.domain.repository.StatsRepository
import com.mmg.manahub.feature.tournament.domain.repository.TournamentRepository
import com.mmg.manahub.core.gamification.domain.QuestPeriod
import com.mmg.manahub.core.gamification.domain.QuestWeightClass
import com.mmg.manahub.core.gamification.domain.model.PlayerProgression
import com.mmg.manahub.core.gamification.domain.model.QuestBoard
import com.mmg.manahub.core.gamification.domain.model.QuestUiModel
import com.mmg.manahub.core.gamification.domain.model.StreakUiModel
import com.mmg.manahub.core.gamification.domain.repository.GamificationRepository
import com.mmg.manahub.core.domain.auth.AuthUser
import com.mmg.manahub.core.domain.auth.SessionState
import com.mmg.manahub.core.domain.auth.AuthRepository
import com.mmg.manahub.core.model.DraftConfig
import com.mmg.manahub.core.model.DraftSeat
import com.mmg.manahub.core.model.DraftState
import com.mmg.manahub.core.model.DraftStatus
import com.mmg.manahub.core.model.NudgeTrigger
import com.mmg.manahub.core.model.PassDirection
import com.mmg.manahub.core.domain.repository.DraftRepository
import com.mmg.manahub.core.domain.repository.DraftSimRepository
import com.mmg.manahub.core.domain.usecase.home.GetAccountNudgeUseCase
import com.mmg.manahub.feature.news.domain.usecase.GetNewsFeedUseCase
import com.mmg.manahub.feature.news.domain.usecase.ManageSourcesUseCase
import com.mmg.manahub.feature.news.domain.usecase.RefreshNewsFeedUseCase
import com.mmg.manahub.core.model.WishlistEntry
import com.mmg.manahub.core.domain.repository.WishlistRepository
import com.mmg.manahub.core.model.TradeProposal
import com.mmg.manahub.core.model.TradeItem
import com.mmg.manahub.core.model.TradeStatus
import com.mmg.manahub.core.model.CommunityStats
import com.mmg.manahub.core.model.CommunityEntry
import com.mmg.manahub.core.model.CommunityMilestone
import com.mmg.manahub.core.model.ArchidektTrendingDeck
import com.mmg.manahub.core.model.OpenForTradeEntry
import com.mmg.manahub.core.model.FriendRequest
import com.mmg.manahub.core.model.Tournament
import com.mmg.manahub.core.model.UserCard
import com.mmg.manahub.core.model.UserCardWithCard
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [HomeViewModel].
 *
 * [HomeViewModel.state] is a `stateIn(WhileSubscribed)` StateFlow — the upstream
 * combine only runs while there are active collectors. Each test that reads
 * [HomeViewModel.state].value must first subscribe by calling
 * `backgroundScope.launch { vm.state.collect {} }` before [advanceUntilIdle].
 * Without this, `.value` returns the `initialValue` (isLoading=true).
 *
 * [StandardTestDispatcher] is used so coroutines are scheduled explicitly via
 * [advanceUntilIdle], giving predictable ordering regardless of suspend points.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val sessionStateFlow = MutableStateFlow<SessionState>(SessionState.Unauthenticated)
    private val isCoolingDownFlow = MutableStateFlow(false)
    private val totalGamesFlow = MutableStateFlow(0)
    private val activeDraftFlow = MutableStateFlow<DraftState?>(null)
    private val tournamentsFlow =
        MutableStateFlow<List<com.mmg.manahub.core.model.Tournament>>(emptyList())
    private val deckSummariesFlow = MutableStateFlow<List<DeckSummary>>(emptyList())

    // Gamification (Phase 2) flows.
    private val gamificationEnabledFlow = MutableStateFlow(true)
    private val progressionFlow = MutableStateFlow(
        PlayerProgression(
            totalXp = 0L, level = 1, xpIntoLevel = 0L, xpForNextLevel = 100L,
            updatedAt = kotlinx.datetime.Instant.fromEpochMilliseconds(0L),
        ),
    )
    private val questBoardFlow = MutableStateFlow(QuestBoard.empty)
    private val streakFlow = MutableStateFlow(StreakUiModel(current = 0, longest = 0, freezeTokens = 0))

    /** Backing store for the persisted layout — saveHomeLayout writes here so reads round-trip. */
    private val savedLayoutTokens = MutableStateFlow<String?>(null)

    private val userPrefsDataStore: UserPreferencesDataStore = mockk(relaxed = true)
    private val statsRepository: StatsRepository = mockk(relaxed = true)
    private val deckRepository: DeckRepository = mockk(relaxed = true)
    private val gameSessionRepository: GameSessionRepository = mockk(relaxed = true)
    private val draftSimRepository: DraftSimRepository = mockk(relaxed = true)
    private val tournamentRepository: TournamentRepository = mockk(relaxed = true)
    private val authRepository: AuthRepository = mockk(relaxed = true)
    private val getNewsFeedUseCase: GetNewsFeedUseCase = mockk(relaxed = true)
    private val refreshNewsFeedUseCase: RefreshNewsFeedUseCase = mockk(relaxed = true)
    private val manageSourcesUseCase: ManageSourcesUseCase = mockk(relaxed = true)
    private val communityStatsRepository: CommunityStatsRepository = mockk(relaxed = true)
    private val draftRepository: DraftRepository = mockk(relaxed = true)
    private val cardRepository: com.mmg.manahub.core.domain.repository.CardRepository = mockk(relaxed = true)
    private val scryfallRemoteDataSource: com.mmg.manahub.core.data.remote.ScryfallRemoteDataSource = mockk(relaxed = true)
    private val wishlistRepository: WishlistRepository = mockk(relaxed = true)
    private val gamificationRepository: GamificationRepository = mockk(relaxed = true)

    // Home feature overhaul (2026-07-13) — new real data sources.
    private val userCardRepository: com.mmg.manahub.core.domain.repository.UserCardRepository = mockk(relaxed = true)
    private val tradesRepository: com.mmg.manahub.core.data.repository.TradesRepository = mockk(relaxed = true)
    private val openForTradeRepository: com.mmg.manahub.core.domain.repository.OpenForTradeRepository = mockk(relaxed = true)
    private val tradeSuggestionsRepository: com.mmg.manahub.core.domain.repository.TradeSuggestionsRepository = mockk(relaxed = true)
    private val friendRepository: com.mmg.manahub.core.domain.repository.FriendRepository = mockk(relaxed = true)
    private val archidektTrendingRepository: com.mmg.manahub.core.domain.repository.ArchidektTrendingRepository = mockk(relaxed = true)
    private val playtestRepository: com.mmg.manahub.core.domain.repository.PlaytestRepository = mockk(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        // HomeViewModel resolves FirebaseCrashlytics.getInstance() at construction (additive
        // telemetry, no PII) — mock the static so the un-initialized FirebaseApp doesn't throw.
        mockkStatic(FirebaseCrashlytics::class)
        every { FirebaseCrashlytics.getInstance() } returns mockk(relaxed = true)
        stubDefaultDependencies()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(FirebaseCrashlytics::class)
    }

    private fun stubDefaultDependencies() {
        every { authRepository.sessionState } returns sessionStateFlow
        every { userPrefsDataStore.preferredCurrencyFlow } returns flowOf(PreferredCurrency.USD)
        every { userPrefsDataStore.playerNameFlow } returns flowOf("Wizard")
        every { userPrefsDataStore.observeQuickStartActions() } returns flowOf(QuickStartAction.defaults)
        every { userPrefsDataStore.observeSkippedFirstSteps() } returns flowOf(emptySet())
        every { userPrefsDataStore.isNudgeCoolingDown() } returns isCoolingDownFlow
        every { userPrefsDataStore.avatarUrlFlow } returns flowOf(null)
        every { statsRepository.observeCollectionStats(any()) } returns
            flowOf(emptyCollectionStats())
        every { deckRepository.observeAllDeckSummaries() } returns deckSummariesFlow
        every { gameSessionRepository.observeTotalGames() } returns totalGamesFlow
        every { draftSimRepository.observeActiveSession() } returns activeDraftFlow
        every { tournamentRepository.observeTournaments() } returns tournamentsFlow
        every { getNewsFeedUseCase() } returns flowOf(emptyList())
        coEvery { refreshNewsFeedUseCase() } returns
            Result.success(RefreshResult(fetched = 0, failed = 0, notModified = 0))
        // News filter source of truth: default (English-only, all enabled sources).
        every { userPrefsDataStore.observeNewsFilters() } returns flowOf(NewsFilterPrefs.DEFAULT)
        // Default to no sources; news tests override this with sources that enable their feed.
        every { manageSourcesUseCase.observeSources() } returns flowOf(emptyList())

        // Phase 2 stats flows.
        every { gameSessionRepository.observeLocalWins() } returns flowOf(0)
        every { gameSessionRepository.observeLocalSessionHistory(any()) } returns flowOf(emptyList())
        every { gameSessionRepository.observeDeckStats() } returns flowOf(emptyList())
        every { gameSessionRepository.observeMostFrequentElimination() } returns flowOf(null)
        every { gameSessionRepository.observeAvgWinTurn(any()) } returns flowOf(null)
        every { gameSessionRepository.observeAvgLifeOnWin() } returns flowOf(null)
        every { gameSessionRepository.observeAvgLifeOnLoss() } returns flowOf(null)

        // Phase 3 stubs.
        every { communityStatsRepository.observeCommunityStats() } returns flowOf(null)
        coEvery { draftRepository.getDraftableSets(any()) } returns DataResult.Success(emptyList())
        // Discover widget random-card fetch: default to an empty result.
        coEvery { cardRepository.searchCards(any(), any(), any()) } returns DataResult.Success(emptyList())
        // Default-random-set seeding: no sets available (falls back to the global random query).
        coEvery { scryfallRemoteDataSource.getAllSets() } returns emptyList()

        // Wishlist default: empty.
        every { wishlistRepository.observeLocal() } returns flowOf(emptyList())

        // Home feature overhaul (2026-07-13) — new real data sources, defaulted to empty/zero.
        every { tradesRepository.observeActiveProposals() } returns flowOf(emptyList())
        // Home dashboard audit (HIGH) — VM init warms the trades cache in the background; stub the
        // success path so tests that don't care about it don't crash on MockK's `Result<Unit>`
        // handling (a relaxed mock can't synthesize an inline value class return).
        coEvery { tradesRepository.refreshProposals(any()) } returns Result.success(Unit)
        every { openForTradeRepository.observeLocal() } returns flowOf(emptyList())
        coEvery { tradeSuggestionsRepository.getSuggestions() } returns Result.success(emptyList())
        every { friendRepository.observeFriendCount() } returns flowOf(0)
        every { friendRepository.observePendingRequests() } returns flowOf(emptyList())
        every { archidektTrendingRepository.observeTrendingDecks() } returns flowOf(emptyList())
        every { userCardRepository.observeRecentlyAdded(any()) } returns flowOf(emptyList())
        every { playtestRepository.observeTotalTestCount() } returns flowOf(0)

        // Gamification (Phase 2): enabled with empty board / zeroed streak / level 1.
        every { userPrefsDataStore.gamificationEnabledFlow } returns gamificationEnabledFlow
        every { gamificationRepository.observeProgression() } returns progressionFlow
        every { gamificationRepository.observeActiveQuests() } returns questBoardFlow
        every { gamificationRepository.observeDailyActivityStreak() } returns streakFlow

        // Layout round-trip: homeLayoutFlow decodes the saved tokens (or the supplied
        // default when none are saved); saveHomeLayout writes the tokens.
        every { userPrefsDataStore.homeLayoutFlow(any()) } answers {
            val default = firstArg<List<PersistedWidget>>()
            savedLayoutTokens.map { tokens -> decodeLayout(tokens, default) }
        }
        val layoutSlot = slot<List<PersistedWidget>>()
        coEvery { userPrefsDataStore.saveHomeLayout(capture(layoutSlot)) } answers {
            savedLayoutTokens.value = layoutSlot.captured.joinToString(",") { "${it.persistedId}:${it.size.name}" }
        }
    }

    /** Mirrors UserPreferencesDataStore decode: unknown ids/sizes are skipped; empty → default. */
    private fun decodeLayout(tokens: String?, default: List<PersistedWidget>): List<PersistedWidget> {
        val parsed = tokens
            ?.split(",")
            ?.mapNotNull { token ->
                val parts = token.trim().split(":")
                if (parts.size != 2) return@mapNotNull null
                val type = HomeWidgetType.fromPersistedId(parts[0].trim()) ?: return@mapNotNull null
                val size = WidgetSize.entries.firstOrNull { it.name == parts[1].trim() } ?: return@mapNotNull null
                PersistedWidget(persistedId = type.persistedId, size = size)
            }
            ?: emptyList()
        return parsed.ifEmpty { default }
    }

    private fun buildViewModel(): HomeViewModel = HomeViewModel(
        userPrefsDataStore = userPrefsDataStore,
        statsRepository = statsRepository,
        deckRepository = deckRepository,
        gameSessionRepository = gameSessionRepository,
        draftSimRepository = draftSimRepository,
        tournamentRepository = tournamentRepository,
        authRepository = authRepository,
        getNewsFeedUseCase = getNewsFeedUseCase,
        refreshNewsFeedUseCase = refreshNewsFeedUseCase,
        manageSourcesUseCase = manageSourcesUseCase,
        cardRepository = cardRepository,
        scryfallRemoteDataSource = scryfallRemoteDataSource,
        communityStatsRepository = communityStatsRepository,
        draftRepository = draftRepository,
        wishlistRepository = wishlistRepository,
        getAccountNudgeUseCase = GetAccountNudgeUseCase(),
        gamificationRepository = gamificationRepository,
        userCardRepository = userCardRepository,
        tradesRepository = tradesRepository,
        openForTradeRepository = openForTradeRepository,
        tradeSuggestionsRepository = tradeSuggestionsRepository,
        friendRepository = friendRepository,
        archidektTrendingRepository = archidektTrendingRepository,
        playtestRepository = playtestRepository,
    )

    /** Builds a quest UI model for tests (title/description are irrelevant for VM logic). */
    private fun quest(
        instanceId: String,
        period: QuestPeriod = QuestPeriod.DAILY,
        progress: Int = 0,
        target: Int = 1,
        status: String = "ACTIVE",
        xpReward: Int = 50,
    ) = QuestUiModel(
        instanceId = instanceId,
        templateId = "tmpl_$instanceId",
        title = "",
        description = "",
        emoji = "⚔",
        period = period,
        weightClass = QuestWeightClass.ACCESSIBLE,
        progress = progress,
        target = target,
        status = status,
        xpReward = xpReward,
    )

    private fun emptyCollectionStats() = CollectionStats(
        totalCards = 0, uniqueCards = 0, totalDecks = 0,
        totalValueUsd = 0.0, totalValueEur = 0.0,
        mostValuableCards = emptyList(), byColor = emptyMap(), byRarity = emptyMap(),
        byType = emptyMap(), cmcDistribution = emptyMap(), bySet = emptyMap(),
    )

    private fun collectionStatsWithCards(uniqueCards: Int) =
        emptyCollectionStats().copy(uniqueCards = uniqueCards)

    private fun deckSummary(id: String = "deck-1", name: String = "My Deck", cardCount: Int = 60) =
        DeckSummary(
            id = id, name = name, description = null, format = "STANDARD",
            coverCardId = null, createdAt = 0L, updatedAt = 0L,
            cardCount = cardCount, colorIdentity = emptySet(), coverImageUrl = null,
        )

    private fun tournamentEntity(id: Long = 1L, status: String = "ACTIVE") =
        com.mmg.manahub.core.data.local.entity.TournamentEntity(
            id = id,
            name = "Friday Night Magic",
            format = "STANDARD",
            structure = "SWISS",
            status = status,
            matchesPerPairing = 1,
            isRandomPairings = false,
        )

    private fun draftingState(setCode: String = "MKM") = DraftState(
        config = DraftConfig(setCode = setCode),
        round = 1,
        pickNumber = 3,
        seats = listOf(DraftSeat(index = 0, isHuman = true)),
        packsInFlight = emptyMap(),
        passDirection = PassDirection.LEFT,
        status = DraftStatus.DRAFTING,
    )

    private fun authUser() = AuthUser(
        id = "uid-1",
        email = "test@example.com",
        nickname = "TestUser",
        gameTag = "#ABC123",
        avatarUrl = null,
        provider = "email",
    )

    private fun newsArticle(id: String) = NewsItem.Article(
        id = id, title = "Title $id", description = "Desc",
        imageUrl = null, publishedAt = 0L,
        sourceName = "MTG News", sourceId = "src", url = "https://example.com/$id",
        author = null,
    )

    /**
     * Stubs [manageSourcesUseCase] so every source id present in [feed] resolves to an
     * enabled English source — the Home news widget now filters by enabled-source ∩
     * language ∩ type, so without matching sources the feed would be filtered out.
     */
    private fun stubEnabledEnglishSourcesFor(feed: List<NewsItem>) {
        val sources = feed.map { it.sourceId }.distinct().map { sid ->
            com.mmg.manahub.core.model.news.ContentSource(
                id = sid,
                name = sid,
                feedUrl = "https://example.com/$sid/feed",
                type = com.mmg.manahub.core.model.news.SourceType.ARTICLE,
                isEnabled = true,
                isDefault = true,
                iconUrl = null,
                language = "en",
            )
        }
        every { manageSourcesUseCase.observeSources() } returns flowOf(sources)
    }

    // ── Initial state ──────────────────────────────────────────────────────────

    @Test
    fun `initial hero is Welcome when totalGames is 0 and no active draft`() = runTest(testDispatcher) {
        totalGamesFlow.value = 0
        activeDraftFlow.value = null

        val vm = buildViewModel()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        assertTrue(vm.state.value.hero is HomeHeroState.Welcome)
    }

    // ── Account nudge — milestone triggers ────────────────────────────────────

    @Test
    fun `given unauthenticated and uniqueCards 10 or more and no cooldown then nudge is COLLECTION_MILESTONE`() =
        runTest(testDispatcher) {
            sessionStateFlow.value = SessionState.Unauthenticated
            isCoolingDownFlow.value = false
            every { statsRepository.observeCollectionStats(any()) } returns
                flowOf(collectionStatsWithCards(10))

            val vm = buildViewModel()
            backgroundScope.launch { vm.state.collect {} }
            advanceUntilIdle()

            val nudge = vm.state.value.accountNudge
            assertNotNull(nudge)
            assertEquals(NudgeTrigger.COLLECTION_MILESTONE, nudge!!.trigger)
        }

    @Test
    fun `given unauthenticated and uniqueCards below threshold then nudge is not COLLECTION_MILESTONE`() =
        runTest(testDispatcher) {
            sessionStateFlow.value = SessionState.Unauthenticated
            isCoolingDownFlow.value = false
            every { statsRepository.observeCollectionStats(any()) } returns
                flowOf(collectionStatsWithCards(9))

            val vm = buildViewModel()
            backgroundScope.launch { vm.state.collect {} }
            advanceUntilIdle()

            val nudge = vm.state.value.accountNudge
            assertTrue(
                nudge == null || nudge.trigger != NudgeTrigger.COLLECTION_MILESTONE,
            )
        }

    @Test
    fun `given unauthenticated and deckCount 2 or more and no cooldown then nudge is DECK_MILESTONE`() =
        runTest(testDispatcher) {
            sessionStateFlow.value = SessionState.Unauthenticated
            isCoolingDownFlow.value = false
            every { statsRepository.observeCollectionStats(any()) } returns
                flowOf(collectionStatsWithCards(0))
            deckSummariesFlow.value = listOf(deckSummary("d1"), deckSummary("d2"))

            val vm = buildViewModel()
            backgroundScope.launch { vm.state.collect {} }
            advanceUntilIdle()

            val nudge = vm.state.value.accountNudge
            assertNotNull(nudge)
            assertEquals(NudgeTrigger.DECK_MILESTONE, nudge!!.trigger)
        }

    @Test
    fun `given unauthenticated and totalGames 3 or more and no cooldown then nudge is GAME_MILESTONE`() =
        runTest(testDispatcher) {
            sessionStateFlow.value = SessionState.Unauthenticated
            isCoolingDownFlow.value = false
            every { statsRepository.observeCollectionStats(any()) } returns
                flowOf(collectionStatsWithCards(0))
            deckSummariesFlow.value = listOf(deckSummary())
            totalGamesFlow.value = 3

            val vm = buildViewModel()
            backgroundScope.launch { vm.state.collect {} }
            advanceUntilIdle()

            val nudge = vm.state.value.accountNudge
            assertNotNull(nudge)
            assertEquals(NudgeTrigger.GAME_MILESTONE, nudge!!.trigger)
        }

    // ── Account nudge — authenticated user ────────────────────────────────────

    @Test
    fun `given authenticated then accountNudge is null regardless of collection size`() =
        runTest(testDispatcher) {
            sessionStateFlow.value = SessionState.Authenticated(authUser())
            every { statsRepository.observeCollectionStats(any()) } returns
                flowOf(collectionStatsWithCards(100))
            deckSummariesFlow.value = listOf(deckSummary("d1"), deckSummary("d2"), deckSummary("d3"))
            totalGamesFlow.value = 99

            val vm = buildViewModel()
            backgroundScope.launch { vm.state.collect {} }
            advanceUntilIdle()

            assertNull(vm.state.value.accountNudge)
        }

    @Test
    fun `given authenticated then isAuthenticated is true in state`() = runTest(testDispatcher) {
        sessionStateFlow.value = SessionState.Authenticated(authUser())

        val vm = buildViewModel()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        assertTrue(vm.state.value.isAuthenticated)
    }

    @Test
    fun `given unauthenticated then isAuthenticated is false in state`() = runTest(testDispatcher) {
        sessionStateFlow.value = SessionState.Unauthenticated

        val vm = buildViewModel()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        assertFalse(vm.state.value.isAuthenticated)
    }

    // ── Account nudge — cooldown suppression ──────────────────────────────────

    @Test
    fun `given cooldown active then milestone nudge is suppressed`() = runTest(testDispatcher) {
        sessionStateFlow.value = SessionState.Unauthenticated
        isCoolingDownFlow.value = true
        every { statsRepository.observeCollectionStats(any()) } returns
            flowOf(collectionStatsWithCards(10))
        deckSummariesFlow.value = listOf(deckSummary("d1"), deckSummary("d2"))
        totalGamesFlow.value = 3

        val vm = buildViewModel()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        assertNull(vm.state.value.accountNudge)
    }

    // ── ACTION_REQUIRED nudge ─────────────────────────────────────────────────

    @Test
    fun `triggerActionRequiredNudge sets nudge to ACTION_REQUIRED with the given message`() =
        runTest(testDispatcher) {
            sessionStateFlow.value = SessionState.Unauthenticated
            isCoolingDownFlow.value = true

            val vm = buildViewModel()
            backgroundScope.launch { vm.state.collect {} }
            advanceUntilIdle()

            vm.triggerActionRequiredNudge("Create account to trade")
            advanceUntilIdle()

            val nudge = vm.state.value.accountNudge
            assertNotNull(nudge)
            assertEquals(NudgeTrigger.ACTION_REQUIRED, nudge!!.trigger)
            assertEquals("Create account to trade", nudge.message)
        }

    @Test
    fun `triggerActionRequiredNudge overrides cooldown suppression`() = runTest(testDispatcher) {
        sessionStateFlow.value = SessionState.Unauthenticated
        isCoolingDownFlow.value = true

        val vm = buildViewModel()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        assertNull(vm.state.value.accountNudge)

        vm.triggerActionRequiredNudge("Sign in to access Friends")
        advanceUntilIdle()

        assertNotNull(vm.state.value.accountNudge)
        assertEquals(NudgeTrigger.ACTION_REQUIRED, vm.state.value.accountNudge!!.trigger)
    }

    @Test
    fun `ACTION_REQUIRED nudge is cleared when SessionState becomes Authenticated`() =
        runTest(testDispatcher) {
            sessionStateFlow.value = SessionState.Unauthenticated

            val vm = buildViewModel()
            backgroundScope.launch { vm.state.collect {} }
            advanceUntilIdle()

            vm.triggerActionRequiredNudge("Create account to trade")
            advanceUntilIdle()
            assertNotNull(vm.state.value.accountNudge)

            sessionStateFlow.value = SessionState.Authenticated(authUser())
            advanceUntilIdle()

            assertNull(vm.state.value.accountNudge)
        }

    // ── dismissAccountNudge ───────────────────────────────────────────────────

    @Test
    fun `dismissAccountNudge calls userPrefsDataStore dismissAccountNudge`() =
        runTest(testDispatcher) {
            val vm = buildViewModel()
            backgroundScope.launch { vm.state.collect {} }
            advanceUntilIdle()

            vm.dismissAccountNudge()
            advanceUntilIdle()

            coVerify(exactly = 1) { userPrefsDataStore.dismissAccountNudge() }
        }

    @Test
    fun `dismissAccountNudge clears actionRequiredMessage immediately`() = runTest(testDispatcher) {
        sessionStateFlow.value = SessionState.Unauthenticated

        val vm = buildViewModel()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        vm.triggerActionRequiredNudge("You need an account")
        advanceUntilIdle()
        assertNotNull(vm.state.value.accountNudge)

        vm.dismissAccountNudge()
        advanceUntilIdle()

        val nudge = vm.state.value.accountNudge
        assertTrue(nudge == null || nudge.trigger != NudgeTrigger.ACTION_REQUIRED)
    }

    // ── saveQuickStartActions ─────────────────────────────────────────────────

    @Test
    fun `saveQuickStartActions delegates to userPrefsDataStore`() = runTest(testDispatcher) {
        val vm = buildViewModel()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        val actions = listOf(
            QuickStartAction.SCAN_CARD,
            QuickStartAction.CREATE_DECK,
            QuickStartAction.DRAFT_GUIDE,
            QuickStartAction.STATS,
        )
        vm.saveQuickStartActions(actions)
        advanceUntilIdle()

        coVerify(exactly = 1) { userPrefsDataStore.saveQuickStartActions(actions) }
    }

    // ── Hero resolution ───────────────────────────────────────────────────────

    @Test
    fun `hero is ActiveDraft when draft status is DRAFTING`() = runTest(testDispatcher) {
        activeDraftFlow.value = draftingState(setCode = "MKM")

        val vm = buildViewModel()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        val hero = vm.state.value.hero
        assertTrue("Expected ActiveDraft but was $hero", hero is HomeHeroState.ActiveDraft)
        assertEquals("MKM", (hero as HomeHeroState.ActiveDraft).setName)
    }

    @Test
    fun `hero ActiveDraft setName is uppercased`() = runTest(testDispatcher) {
        activeDraftFlow.value = draftingState(setCode = "mkm")

        val vm = buildViewModel()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        assertEquals("MKM", (vm.state.value.hero as HomeHeroState.ActiveDraft).setName)
    }

    @Test
    fun `hero is Summary when totalGames greater than 0 and no active draft`() =
        runTest(testDispatcher) {
            activeDraftFlow.value = null
            totalGamesFlow.value = 5
            every { userPrefsDataStore.playerNameFlow } returns flowOf("Miguel")
            // Skip all first steps so the hero resolves to Summary instead of Welcome
            every { userPrefsDataStore.observeSkippedFirstSteps() } returns flowOf(
                ALL_FIRST_STEPS.map { it.id }.toSet()
            )

            val vm = buildViewModel()
            backgroundScope.launch { vm.state.collect {} }
            advanceUntilIdle()

            val hero = vm.state.value.hero
            assertTrue("Expected Summary but was $hero", hero is HomeHeroState.Summary)
            hero as HomeHeroState.Summary
            assertEquals("Miguel", hero.playerName)
            assertEquals(5, hero.totalGames)
        }

    @Test
    fun `hero is Welcome when totalGames is 0 and no active draft`() = runTest(testDispatcher) {
        activeDraftFlow.value = null
        totalGamesFlow.value = 0

        val vm = buildViewModel()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        assertTrue(vm.state.value.hero is HomeHeroState.Welcome)
    }

    @Test
    fun `active draft with status BUILDING does not produce ActiveDraft hero`() =
        runTest(testDispatcher) {
            activeDraftFlow.value = draftingState().copy(status = DraftStatus.BUILDING)
            totalGamesFlow.value = 0

            val vm = buildViewModel()
            backgroundScope.launch { vm.state.collect {} }
            advanceUntilIdle()

            assertTrue(vm.state.value.hero is HomeHeroState.Welcome)
        }

    // ── recentNews ────────────────────────────────────────────────────────────

    @Test
    fun `recentNews is capped at MAX_NEWS items when feed has more`() = runTest(testDispatcher) {
        // Feed has more items than MAX_NEWS (10); result must be exactly MAX_NEWS.
        val feed = (1..HomeViewModel.MAX_NEWS + 5).map { newsArticle(it.toString()) }
        every { getNewsFeedUseCase() } returns flowOf(feed)
        stubEnabledEnglishSourcesFor(feed)

        val vm = buildViewModel()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        assertEquals(HomeViewModel.MAX_NEWS, vm.state.value.recentNews!!.size)
    }

    @Test
    fun `recentNews contains fewer than MAX_NEWS items when feed has fewer`() = runTest(testDispatcher) {
        val feed = listOf(newsArticle("1"), newsArticle("2"))
        every { getNewsFeedUseCase() } returns flowOf(feed)
        stubEnabledEnglishSourcesFor(feed)

        val vm = buildViewModel()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        assertEquals(2, vm.state.value.recentNews!!.size)
    }

    @Test
    fun `recentNews is empty when news feed is empty`() = runTest(testDispatcher) {
        every { getNewsFeedUseCase() } returns flowOf(emptyList())

        val vm = buildViewModel()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        assertTrue(vm.state.value.recentNews?.isEmpty() != false)
    }

    @Test
    fun `recentNews items carry correct id title and imageUrl from domain model`() =
        runTest(testDispatcher) {
            val article = NewsItem.Article(
                id = "news-42", title = "New Set Spoilers", description = "Details",
                imageUrl = "https://example.com/img.jpg", publishedAt = 0L,
                sourceName = "MTGGoldfish", sourceId = "mtggoldfish",
                url = "https://mtggoldfish.com/news/42", author = "Reporter",
            )
            every { getNewsFeedUseCase() } returns flowOf(listOf(article))
            stubEnabledEnglishSourcesFor(listOf(article))

            val vm = buildViewModel()
            backgroundScope.launch { vm.state.collect {} }
            advanceUntilIdle()

            val item = vm.state.value.recentNews!!.first()
            assertEquals("news-42", item.id)
            assertEquals("New Set Spoilers", item.title)
            assertEquals("https://example.com/img.jpg", item.imageUrl)
        }

    // ── quickStartActions ─────────────────────────────────────────────────────

    @Test
    fun `state reflects quickStartActions from userPrefsDataStore`() = runTest(testDispatcher) {
        val customActions = listOf(
            QuickStartAction.SETTINGS,
            QuickStartAction.STATS,
            QuickStartAction.FRIENDS,
            QuickStartAction.TRADES,
        )
        every { userPrefsDataStore.observeQuickStartActions() } returns flowOf(customActions)

        val vm = buildViewModel()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        assertEquals(customActions, vm.state.value.quickStartActions)
    }

    // ── libraryStats ──────────────────────────────────────────────────────────

    @Test
    fun `libraryStats uniqueCards reflects stats from statsRepository`() = runTest(testDispatcher) {
        every { statsRepository.observeCollectionStats(any()) } returns
            flowOf(collectionStatsWithCards(42))

        val vm = buildViewModel()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        assertEquals(42, vm.state.value.libraryStats?.uniqueCards)
    }

    @Test
    fun `libraryStats deckCount reflects number of decks from deckRepository`() =
        runTest(testDispatcher) {
            deckSummariesFlow.value = listOf(deckSummary("d1"), deckSummary("d2"), deckSummary("d3"))

            val vm = buildViewModel()
            backgroundScope.launch { vm.state.collect {} }
            advanceUntilIdle()

            assertEquals(3, vm.state.value.libraryStats?.deckCount)
        }

    // ── State flow reactive updates ───────────────────────────────────────────

    @Test
    fun `state updates reactively when totalGames flow changes`() = runTest(testDispatcher) {
        totalGamesFlow.value = 0
        activeDraftFlow.value = null
        // Skip all first steps so the hero resolves to Welcome(empty) then Summary
        // once totalGames > 0 — without this the Welcome carousel would persist.
        every { userPrefsDataStore.observeSkippedFirstSteps() } returns flowOf(
            ALL_FIRST_STEPS.map { it.id }.toSet()
        )

        val vm = buildViewModel()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()
        assertTrue(vm.state.value.hero is HomeHeroState.Welcome)

        totalGamesFlow.value = 1
        advanceUntilIdle()

        assertTrue(vm.state.value.hero is HomeHeroState.Summary)
    }

    @Test
    fun `state updates reactively when auth session changes to Authenticated`() =
        runTest(testDispatcher) {
            sessionStateFlow.value = SessionState.Unauthenticated
            every { statsRepository.observeCollectionStats(any()) } returns
                flowOf(collectionStatsWithCards(10))

            val vm = buildViewModel()
            backgroundScope.launch { vm.state.collect {} }
            advanceUntilIdle()
            assertNotNull(vm.state.value.accountNudge)

            sessionStateFlow.value = SessionState.Authenticated(authUser())
            advanceUntilIdle()

            assertNull(vm.state.value.accountNudge)
            assertTrue(vm.state.value.isAuthenticated)
        }

    // ── Turbine state emission tests ──────────────────────────────────────────

    @Test
    fun `state flow emits non-loading state after flows settle`() = runTest(testDispatcher) {
        val vm = buildViewModel()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        vm.state.test {
            val emission = awaitItem()
            assertFalse(emission.isLoading)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Widget board: default layouts ─────────────────────────────────────────

    @Test
    fun `default layout signed-out is used when unauthenticated and nothing saved`() =
        runTest(testDispatcher) {
            sessionStateFlow.value = SessionState.Unauthenticated
            savedLayoutTokens.value = null

            val vm = buildViewModel()
            backgroundScope.launch { vm.state.collect {} }
            advanceUntilIdle()

            val layout = vm.state.value.layout
            assertEquals(HomeWidgetType.CONTEXT_HERO, layout.first().type)
            // The signed-out default surfaces discovery widgets, not signed-in hubs.
            assertTrue(layout.any { it.type == HomeWidgetType.LATEST_SETS })
            assertTrue(layout.any { it.type == HomeWidgetType.CARD_OF_THE_DAY })
            assertFalse(layout.any { it.type == HomeWidgetType.GAME_STATS_HUB })
        }

    @Test
    fun `default layout signed-in is used when authenticated and nothing saved`() =
        runTest(testDispatcher) {
            sessionStateFlow.value = SessionState.Authenticated(authUser())
            savedLayoutTokens.value = null

            val vm = buildViewModel()
            backgroundScope.launch { vm.state.collect {} }
            advanceUntilIdle()

            val layout = vm.state.value.layout
            assertEquals(HomeWidgetType.CONTEXT_HERO, layout.first().type)
            assertTrue(layout.any { it.type == HomeWidgetType.GAME_STATS_HUB })
            assertTrue(layout.any { it.type == HomeWidgetType.COLLECTION_STATS_HUB })
        }

    // ── Widget board: mutations ────────────────────────────────────────────────

    /**
     * Asserts every category in [layout] forms one unbroken run — a category may not appear,
     * be interrupted by a different category, then reappear later in the list. This is the real
     * contiguity invariant [HomeViewModel.addWidget] must uphold; it does NOT require categories
     * to be in [WidgetCategory] ordinal-ascending order (defaultLayoutSignedIn deliberately is not:
     * SOCIAL, ordinal 4, sits before DISCOVER, ordinal 3, to match the funnel order).
     */
    private fun assertContiguousCategoryRuns(layout: List<WidgetInstance>) {
        val seenCategories = mutableSetOf<WidgetCategory>()
        var previousCategory: WidgetCategory? = null
        for (instance in layout) {
            val category = instance.type.category
            if (category != previousCategory) {
                assertTrue(
                    "Category $category is not contiguous in $layout — it reappears after another category",
                    seenCategories.add(category),
                )
                previousCategory = category
            }
        }
    }

    @Test
    fun `AddWidget appends to the end when its category has no existing entries in the layout`() =
        runTest(testDispatcher) {
            sessionStateFlow.value = SessionState.Unauthenticated

            val vm = buildViewModel()
            backgroundScope.launch { vm.state.collect {} }
            advanceUntilIdle()
            assertFalse(vm.state.value.layout.any { it.type == HomeWidgetType.GAME_STATS_HUB })

            // defaultLayoutSignedOut has no STATS-category widget, so GAME_STATS_HUB (STATS) has no
            // existing run to join — it lands at the very end (a brand-new category run of size 1
            // is trivially contiguous with itself).
            vm.onAction(HomeAction.AddWidget(HomeWidgetType.GAME_STATS_HUB))
            advanceUntilIdle()

            val layout = vm.state.value.layout
            assertEquals(HomeWidgetType.GAME_STATS_HUB, layout.last().type)
            assertContiguousCategoryRuns(layout)
            coVerify(atLeast = 1) { userPrefsDataStore.saveHomeLayout(any()) }
        }

    @Test
    fun `AddWidget joins the existing category run wherever it sits, regardless of category ordinal`() =
        runTest(testDispatcher) {
            // Regression test for the addWidget category-contiguity bug (Home dashboard audit,
            // CRITICAL): defaultLayoutSignedIn places SOCIAL (ordinal 4) BEFORE DISCOVER (ordinal 3),
            // so an ordinal-ascending `indexOfLast { ordinal <= newOrdinal }` insertion mis-matched
            // the last DISCOVER item and split TRADES_HUB away from SOCIAL_HUB, off to the very end.
            sessionStateFlow.value = SessionState.Authenticated(authUser())

            val vm = buildViewModel()
            backgroundScope.launch { vm.state.collect {} }
            advanceUntilIdle()
            assertTrue(vm.state.value.layout.any { it.type == HomeWidgetType.SOCIAL_HUB })

            vm.onAction(HomeAction.RemoveWidget(HomeWidgetType.TRADES_HUB))
            advanceUntilIdle()

            vm.onAction(HomeAction.AddWidget(HomeWidgetType.TRADES_HUB))
            advanceUntilIdle()

            val layout = vm.state.value.layout
            val socialIndex = layout.indexOfFirst { it.type == HomeWidgetType.SOCIAL_HUB }
            val tradesIndex = layout.indexOfFirst { it.type == HomeWidgetType.TRADES_HUB }
            // TRADES_HUB (also SOCIAL) must land immediately after the remaining SOCIAL_HUB entry —
            // NOT split off to the end of the board behind the DISCOVER run.
            assertEquals(socialIndex + 1, tradesIndex)
            assertContiguousCategoryRuns(layout)
            coVerify(atLeast = 1) { userPrefsDataStore.saveHomeLayout(any()) }
        }

    @Test
    fun `AddWidget is a no-op when the widget already exists`() = runTest(testDispatcher) {
        sessionStateFlow.value = SessionState.Authenticated(authUser())

        val vm = buildViewModel()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()
        val before = vm.state.value.layout.count { it.type == HomeWidgetType.GAME_STATS_HUB }

        vm.onAction(HomeAction.AddWidget(HomeWidgetType.GAME_STATS_HUB))
        advanceUntilIdle()

        assertEquals(before, vm.state.value.layout.count { it.type == HomeWidgetType.GAME_STATS_HUB })
        coVerify(exactly = 0) { userPrefsDataStore.saveHomeLayout(any()) }
    }

    @Test
    fun `RemoveWidget removes a removable widget`() = runTest(testDispatcher) {
        sessionStateFlow.value = SessionState.Authenticated(authUser())

        val vm = buildViewModel()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()
        assertTrue(vm.state.value.layout.any { it.type == HomeWidgetType.GAME_STATS_HUB })

        vm.onAction(HomeAction.RemoveWidget(HomeWidgetType.GAME_STATS_HUB))
        advanceUntilIdle()

        assertFalse(vm.state.value.layout.any { it.type == HomeWidgetType.GAME_STATS_HUB })
        coVerify(atLeast = 1) { userPrefsDataStore.saveHomeLayout(any()) }
    }

    @Test
    fun `RemoveWidget cannot remove the always-present CONTEXT_HERO`() = runTest(testDispatcher) {
        sessionStateFlow.value = SessionState.Unauthenticated

        val vm = buildViewModel()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        vm.onAction(HomeAction.RemoveWidget(HomeWidgetType.CONTEXT_HERO))
        advanceUntilIdle()

        assertTrue(vm.state.value.layout.any { it.type == HomeWidgetType.CONTEXT_HERO })
        coVerify(exactly = 0) { userPrefsDataStore.saveHomeLayout(any()) }
    }

    @Test
    fun `MoveWidget reorders correctly`() = runTest(testDispatcher) {
        sessionStateFlow.value = SessionState.Unauthenticated

        val vm = buildViewModel()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()
        val firstNonHero = vm.state.value.layout[1].type

        // Move index 1 to index 3.
        vm.onAction(HomeAction.MoveWidget(1, 3))
        advanceUntilIdle()

        assertEquals(firstNonHero, vm.state.value.layout[3].type)
        coVerify(atLeast = 1) { userPrefsDataStore.saveHomeLayout(any()) }
    }

    @Test
    fun `MoveWidget is a no-op when from equals to`() = runTest(testDispatcher) {
        sessionStateFlow.value = SessionState.Unauthenticated

        val vm = buildViewModel()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()
        val before = vm.state.value.layout

        vm.onAction(HomeAction.MoveWidget(2, 2))
        advanceUntilIdle()

        assertEquals(before, vm.state.value.layout)
        coVerify(exactly = 0) { userPrefsDataStore.saveHomeLayout(any()) }
    }

    // ── Persistence round-trip ────────────────────────────────────────────────

    @Test
    fun `saved layout round-trips through decode`() = runTest(testDispatcher) {
        sessionStateFlow.value = SessionState.Unauthenticated
        savedLayoutTokens.value = "context_hero:MEDIUM,game_stats_hub:MEDIUM"

        val vm = buildViewModel()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        assertEquals(
            listOf(
                WidgetInstance(HomeWidgetType.CONTEXT_HERO, WidgetSize.MEDIUM),
                WidgetInstance(HomeWidgetType.GAME_STATS_HUB, WidgetSize.MEDIUM),
            ),
            vm.state.value.layout,
        )
    }

    @Test
    fun `unknown persistedId is skipped on decode`() = runTest(testDispatcher) {
        sessionStateFlow.value = SessionState.Unauthenticated
        savedLayoutTokens.value = "context_hero:MEDIUM,bogus_widget:SMALL,game_stats_hub:MEDIUM"

        val vm = buildViewModel()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        val ids = vm.state.value.layout.map { it.type }
        assertEquals(listOf(HomeWidgetType.CONTEXT_HERO, HomeWidgetType.GAME_STATS_HUB), ids)
    }

    @Test
    fun `unknown size name is skipped on decode`() = runTest(testDispatcher) {
        sessionStateFlow.value = SessionState.Unauthenticated
        savedLayoutTokens.value = "context_hero:HUGE,game_stats_hub:MEDIUM"

        val vm = buildViewModel()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        // The HUGE token is dropped; only game_stats_hub survives.
        assertEquals(
            listOf(WidgetInstance(HomeWidgetType.GAME_STATS_HUB, WidgetSize.MEDIUM)),
            vm.state.value.layout,
        )
    }

    // ── Account-gating ─────────────────────────────────────────────────────────

    @Test
    fun `community stats flow is subscribed and surfaced (null while stubbed)`() =
        runTest(testDispatcher) {
            sessionStateFlow.value = SessionState.Authenticated(authUser())

            val vm = buildViewModel()
            backgroundScope.launch { vm.state.collect {} }
            advanceUntilIdle()

            assertNull(vm.state.value.communityStats)
        }

    @Test
    fun `news flow crashing does not crash the board`() = runTest(testDispatcher) {
        every { getNewsFeedUseCase() } returns kotlinx.coroutines.flow.flow { throw RuntimeException("boom") }
        sessionStateFlow.value = SessionState.Unauthenticated

        val vm = buildViewModel()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        // The board still resolves: layout present, news empty (catch emits emptyList).
        assertTrue(vm.state.value.recentNews?.isEmpty() != false)
        assertTrue(vm.state.value.layout.isNotEmpty())
    }

    // ── MoveWidget out-of-bounds ──────────────────────────────────────────────

    @Test
    fun `MoveWidget with negative from index is a no-op`() = runTest(testDispatcher) {
        sessionStateFlow.value = SessionState.Unauthenticated
        val vm = buildViewModel()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()
        val before = vm.state.value.layout

        vm.onAction(HomeAction.MoveWidget(-1, 0))
        advanceUntilIdle()

        assertEquals(before, vm.state.value.layout)
        coVerify(exactly = 0) { userPrefsDataStore.saveHomeLayout(any()) }
    }

    @Test
    fun `MoveWidget with to index beyond last is a no-op`() = runTest(testDispatcher) {
        sessionStateFlow.value = SessionState.Unauthenticated
        val vm = buildViewModel()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()
        val before = vm.state.value.layout
        val outOfBounds = before.size

        vm.onAction(HomeAction.MoveWidget(0, outOfBounds))
        advanceUntilIdle()

        assertEquals(before, vm.state.value.layout)
        coVerify(exactly = 0) { userPrefsDataStore.saveHomeLayout(any()) }
    }

    // ── AddWidget default size ────────────────────────────────────────────────

    @Test
    fun `AddWidget uses MEDIUM as default size when the widget supports it`() = runTest(testDispatcher) {
        sessionStateFlow.value = SessionState.Unauthenticated
        val vm = buildViewModel()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        vm.onAction(HomeAction.AddWidget(HomeWidgetType.GAME_STATS_HUB))
        advanceUntilIdle()

        val added = vm.state.value.layout.first { it.type == HomeWidgetType.GAME_STATS_HUB }
        assertEquals(WidgetSize.MEDIUM, added.size)
    }

    // ── Decode fallback ───────────────────────────────────────────────────────

    @Test
    fun `all-invalid decode tokens fall back to auth-appropriate default`() = runTest(testDispatcher) {
        sessionStateFlow.value = SessionState.Unauthenticated
        savedLayoutTokens.value = "bogus:HUGE,another:INVALID"

        val vm = buildViewModel()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        val layout = vm.state.value.layout
        assertTrue(layout.isNotEmpty())
        assertEquals(HomeWidgetType.CONTEXT_HERO, layout.first().type)
    }

    // ── ResetLayout produces correct auth-appropriate default ─────────────────

    @Test
    fun `ResetLayout persists the signed-out default when unauthenticated`() = runTest(testDispatcher) {
        sessionStateFlow.value = SessionState.Unauthenticated
        val vm = buildViewModel()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        vm.onAction(HomeAction.RemoveWidget(HomeWidgetType.QUICK_ACTIONS))
        advanceUntilIdle()

        vm.onAction(HomeAction.ResetLayout)
        advanceUntilIdle()

        val layout = vm.state.value.layout
        assertEquals(HomeWidgetType.CONTEXT_HERO, layout.first().type)
        assertFalse(layout.any { it.type == HomeWidgetType.GAME_STATS_HUB })
        assertTrue(layout.any { it.type == HomeWidgetType.LATEST_SETS })
    }

    @Test
    fun `ResetLayout persists the signed-in default when authenticated`() = runTest(testDispatcher) {
        sessionStateFlow.value = SessionState.Authenticated(authUser())
        val vm = buildViewModel()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        vm.onAction(HomeAction.RemoveWidget(HomeWidgetType.GAME_STATS_HUB))
        advanceUntilIdle()

        vm.onAction(HomeAction.ResetLayout)
        advanceUntilIdle()

        val layout = vm.state.value.layout
        assertTrue(layout.any { it.type == HomeWidgetType.GAME_STATS_HUB })
        assertTrue(layout.any { it.type == HomeWidgetType.COLLECTION_STATS_HUB })
    }

    // ── Wishlist stats ────────────────────────────────────────────────────────

    @Test
    fun `wishlistStats cards reflects cards from wishlistRepository when authenticated`() = runTest(testDispatcher) {
        sessionStateFlow.value = SessionState.Authenticated(authUser())
        val entry = WishlistEntry(
            id = "w1", userId = "u1", cardId = "c1", quantity = 1,
            matchAnyVariant = false, isFoil = false, condition = "NM", language = "en",
            createdAt = 123L,
            card = com.mmg.manahub.core.model.Card(
                scryfallId = "c1", name = "Black Lotus", printedName = null,
                manaCost = "{0}", cmc = 0.0, colors = emptyList(), colorIdentity = emptyList(),
                typeLine = "Artifact", printedTypeLine = null, oracleText = null,
                printedText = null, keywords = emptyList(), power = null,
                toughness = null, loyalty = null, setCode = "LEA",
                setName = "Limited Edition Alpha", collectorNumber = "1",
                rarity = "mythic", releasedAt = "1993-08-05",
                frameEffects = emptyList(), promoTypes = emptyList(), lang = "en",
                imageNormal = "https://example.com/lotus.jpg",
                imageArtCrop = "https://example.com/lotus_art.jpg",
                imageBackNormal = null, priceUsd = 100000.0, priceUsdFoil = null,
                priceEur = 90000.0, priceEurFoil = null,
                legalityStandard = "not_legal", legalityPioneer = "not_legal",
                legalityModern = "not_legal", legalityCommander = "banned",
                flavorText = null, artist = "Christopher Rush", scryfallUri = "",
                tags = emptyList(), userTags = emptyList(), suggestedTags = emptyList(),
                relatedUris = emptyMap(), purchaseUris = emptyMap()
            )
        )
        every { wishlistRepository.observeLocal() } returns flowOf(listOf(entry))

        val vm = buildViewModel()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        val stats = vm.state.value.wishlistStats
        assertNotNull(stats)
        assertEquals(1, stats!!.count)
        assertEquals(1, stats.cards.size)
        assertEquals("c1", stats.cards.first().id)
        assertEquals("Black Lotus", stats.cards.first().name)
        // Wishlist previews use the full card image (imageNormal), not the art crop.
        assertEquals("https://example.com/lotus.jpg", stats.cards.first().imageUrl)
    }

    @Test
    fun `wishlistStats is null when unauthenticated`() = runTest(testDispatcher) {
        sessionStateFlow.value = SessionState.Unauthenticated
        val entry = WishlistEntry(
            id = "w1", userId = "u1", cardId = "c1", quantity = 1,
            matchAnyVariant = false, isFoil = false, condition = "NM", language = "en",
            createdAt = 123L, card = null
        )
        every { wishlistRepository.observeLocal() } returns flowOf(listOf(entry))

        val vm = buildViewModel()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        assertNull(vm.state.value.wishlistStats)
    }

    // ── Gamification widgets + CONTEXT_HERO claim suggestion (Phase 2) ─────────

    @Test
    fun `given gamification enabled then gamification snapshot is populated`() = runTest(testDispatcher) {
        progressionFlow.value = PlayerProgression(
            totalXp = 250L, level = 4, xpIntoLevel = 40L, xpForNextLevel = 120L,
            updatedAt = kotlinx.datetime.Instant.fromEpochMilliseconds(0L),
        )

        val vm = buildViewModel()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        val g = vm.state.value.gamification
        assertNotNull(g)
        assertEquals(4, g!!.level)
        assertTrue(vm.state.value.gamificationEnabled)
    }

    @Test
    fun `given gamification disabled then snapshot is null and toggle is false`() = runTest(testDispatcher) {
        gamificationEnabledFlow.value = false

        val vm = buildViewModel()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        assertFalse(vm.state.value.gamificationEnabled)
        assertNull(vm.state.value.gamification)
    }

    @Test
    fun `daily quest counts map completed and claimable quests as done`() = runTest(testDispatcher) {
        questBoardFlow.value = QuestBoard(
            daily = listOf(
                quest("d1", status = "CLAIMED"),       // done (claimed)
                quest("d2", status = "COMPLETED"),     // done (claimable)
                quest("d3", status = "ACTIVE"),        // not done
            ),
            weekly = emptyList(),
        )

        val vm = buildViewModel()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        val g = vm.state.value.gamification!!
        assertEquals(3, g.dailyTotal)
        assertEquals(2, g.dailyDone)
    }

    @Test
    fun `top quests preview puts claimable first and caps at three`() = runTest(testDispatcher) {
        questBoardFlow.value = QuestBoard(
            daily = listOf(
                quest("d1", status = "ACTIVE"),
                quest("d2", status = "COMPLETED"),     // claimable
                quest("d3", status = "ACTIVE"),
                quest("d4", status = "ACTIVE"),
            ),
            weekly = listOf(quest("w1", period = QuestPeriod.WEEKLY, status = "ACTIVE")),
        )

        val vm = buildViewModel()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        val preview = vm.state.value.gamification!!.topQuests
        assertEquals(3, preview.size)
        assertEquals("d2", preview.first().instanceId) // claimable first
        assertTrue(preview.first().isClaimable)
    }

    @Test
    fun `default layouts exclude the progression and quests widgets (gamification hidden for release)`() =
        runTest(testDispatcher) {
            // The gamification UI is hidden for release (docs/gamification-hidden-for-release.md),
            // so the default dashboard layout no longer seeds PROGRESSION_HUB / QUESTS_HUB. The
            // base layout (e.g. CONTEXT_HERO) is still present so we know the default was applied.
            sessionStateFlow.value = SessionState.Unauthenticated

            val vm = buildViewModel()
            backgroundScope.launch { vm.state.collect {} }
            advanceUntilIdle()

            val types = vm.state.value.layout.map { it.type }
            assertTrue(HomeWidgetType.CONTEXT_HERO in types)
            assertFalse(HomeWidgetType.PROGRESSION_HUB in types)
            assertFalse(HomeWidgetType.QUESTS_HUB in types)
        }

    @Test
    fun `hero is QuestsReady with the claimable count when quests are completed and gamification enabled`() =
        runTest(testDispatcher) {
            // Two completed (claimable) quests; an active draft is also running but QuestsReady wins.
            activeDraftFlow.value = draftingState(setCode = "MKM")
            questBoardFlow.value = QuestBoard(
                daily = listOf(quest("d1", status = "COMPLETED"), quest("d2", status = "COMPLETED")),
                weekly = emptyList(),
            )

            val vm = buildViewModel()
            backgroundScope.launch { vm.state.collect {} }
            advanceUntilIdle()

            val hero = vm.state.value.hero
            assertTrue("Expected QuestsReady but was $hero", hero is HomeHeroState.QuestsReady)
            assertEquals(2, (hero as HomeHeroState.QuestsReady).count)
        }

    @Test
    fun `hero is NOT QuestsReady when gamification is disabled even with claimable quests`() =
        runTest(testDispatcher) {
            gamificationEnabledFlow.value = false
            activeDraftFlow.value = draftingState(setCode = "MKM")
            questBoardFlow.value = QuestBoard(
                daily = listOf(quest("d1", status = "COMPLETED")),
                weekly = emptyList(),
            )

            val vm = buildViewModel()
            backgroundScope.launch { vm.state.collect {} }
            advanceUntilIdle()

            assertTrue(vm.state.value.hero is HomeHeroState.ActiveDraft)
        }

    @Test
    fun `hero is not QuestsReady when there are no claimable quests`() = runTest(testDispatcher) {
        questBoardFlow.value = QuestBoard(
            daily = listOf(quest("d1", status = "ACTIVE")),
            weekly = emptyList(),
        )
        totalGamesFlow.value = 0

        val vm = buildViewModel()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        assertFalse(vm.state.value.hero is HomeHeroState.QuestsReady)
    }

    // ── Discover load state + retry ───────────────────────────────────────────

    @Test
    fun `discover load state is LOADED when the random card fetch succeeds`() = runTest(testDispatcher) {
        coEvery { cardRepository.searchCards(any(), any(), any()) } returns
            DataResult.Success(listOf(discoverCard("c1")))

        val vm = buildViewModel()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        assertEquals(DiscoverLoadState.LOADED, vm.state.value.discoverLoadState)
        assertTrue(vm.state.value.discoverCards.isNotEmpty())
    }

    @Test
    fun `discover load state is FAILED when the random card fetch returns empty`() = runTest(testDispatcher) {
        coEvery { cardRepository.searchCards(any(), any(), any()) } returns DataResult.Success(emptyList())

        val vm = buildViewModel()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        // A failed/empty fetch must not leave the slice stuck on LOADING.
        assertEquals(DiscoverLoadState.FAILED, vm.state.value.discoverLoadState)
    }

    @Test
    fun `RetryDiscover re-fetches and reaches LOADED on success`() = runTest(testDispatcher) {
        coEvery { cardRepository.searchCards(any(), any(), any()) } returns DataResult.Success(emptyList())

        val vm = buildViewModel()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()
        assertEquals(DiscoverLoadState.FAILED, vm.state.value.discoverLoadState)

        // Next attempt succeeds.
        coEvery { cardRepository.searchCards(any(), any(), any()) } returns
            DataResult.Success(listOf(discoverCard("c1")))
        vm.onAction(HomeAction.RetryDiscover)
        advanceUntilIdle()

        assertEquals(DiscoverLoadState.LOADED, vm.state.value.discoverLoadState)
        assertTrue(vm.state.value.discoverCards.isNotEmpty())
    }

    @Test
    fun `RefreshRandomCard reaches LOADED and sets the random card`() = runTest(testDispatcher) {
        coEvery { cardRepository.searchCards(any(), any(), any()) } returns
            DataResult.Success(listOf(discoverCard("r1")))

        val vm = buildViewModel()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        vm.onAction(HomeAction.RefreshRandomCard)
        advanceUntilIdle()

        assertEquals(DiscoverLoadState.LOADED, vm.state.value.randomCardLoadState)
        assertEquals("r1", vm.state.value.cardOfTheDay?.scryfallId)
    }

    // ── News filters (persisted, shared with NewsScreen) ──────────────────────

    @Test
    fun `newsFiltersActive is false when filters match the English-only default`() = runTest(testDispatcher) {
        every { userPrefsDataStore.observeNewsFilters() } returns flowOf(NewsFilterPrefs.DEFAULT)

        val vm = buildViewModel()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        assertFalse(vm.state.value.newsFiltersActive)
    }

    @Test
    fun `newsFiltersActive is true when persisted filters differ from default`() = runTest(testDispatcher) {
        every { userPrefsDataStore.observeNewsFilters() } returns
            flowOf(NewsFilterPrefs.DEFAULT.copy(languages = setOf("en", "es")))

        val vm = buildViewModel()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        assertTrue(vm.state.value.newsFiltersActive)
    }

    @Test
    fun `recentNews filters out items whose source language is not selected`() = runTest(testDispatcher) {
        val enArticle = newsArticle("en-1").copy(sourceId = "en-src")
        val esArticle = newsArticle("es-1").copy(sourceId = "es-src")
        every { getNewsFeedUseCase() } returns flowOf(listOf(enArticle, esArticle))
        every { manageSourcesUseCase.observeSources() } returns flowOf(
            listOf(
                contentSource("en-src", "en"),
                contentSource("es-src", "es"),
            ),
        )
        // Default filters are English-only.
        every { userPrefsDataStore.observeNewsFilters() } returns flowOf(NewsFilterPrefs.DEFAULT)

        val vm = buildViewModel()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        val ids = vm.state.value.recentNews!!.map { it.id }
        assertEquals(listOf("en-1"), ids)
    }

    @Test
    fun `ResetNewsFilters delegates to userPrefsDataStore`() = runTest(testDispatcher) {
        val vm = buildViewModel()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        vm.onAction(HomeAction.ResetNewsFilters)
        advanceUntilIdle()

        coVerify(exactly = 1) { userPrefsDataStore.resetNewsFilters() }
    }

    private fun discoverCard(id: String) = com.mmg.manahub.core.model.Card(
        scryfallId = id, name = "Card $id", printedName = null,
        manaCost = "{1}", cmc = 1.0, colors = emptyList(), colorIdentity = emptyList(),
        typeLine = "Creature", printedTypeLine = null, oracleText = null,
        printedText = null, keywords = emptyList(), power = null,
        toughness = null, loyalty = null, setCode = "TST",
        setName = "Test", collectorNumber = "1",
        rarity = "common", releasedAt = "2020-01-01",
        frameEffects = emptyList(), promoTypes = emptyList(), lang = "en",
        imageNormal = "https://example.com/$id.jpg",
        imageArtCrop = "https://example.com/${id}_art.jpg",
        imageBackNormal = null, priceUsd = null, priceUsdFoil = null,
        priceEur = null, priceEurFoil = null,
        legalityStandard = "legal", legalityPioneer = "legal",
        legalityModern = "legal", legalityCommander = "legal",
        flavorText = null, artist = "Artist", scryfallUri = "",
        tags = emptyList(), userTags = emptyList(), suggestedTags = emptyList(),
        relatedUris = emptyMap(), purchaseUris = emptyMap(),
    )

    private fun contentSource(id: String, language: String) =
        com.mmg.manahub.core.model.news.ContentSource(
            id = id, name = id, feedUrl = "https://example.com/$id/feed",
            type = com.mmg.manahub.core.model.news.SourceType.ARTICLE,
            isEnabled = true, isDefault = true, iconUrl = null, language = language,
        )

    // ── Fixture builders (Home feature overhaul 2026-07-13 test additions) ────

    private fun tradeItem(id: String = "item-1", cardId: String = "c1") = TradeItem(
        id = id, tradeProposalId = "proposal-1", fromUserId = "uid-2", toUserId = "uid-1",
        userCardIdRef = "uc-1", quantity = 1, isFoil = false, condition = "NM", language = "en",
        cardId = cardId, isReviewCollectionPlaceholder = false,
    )

    private fun tradeProposal(
        id: String = "proposal-1",
        status: TradeStatus = TradeStatus.PROPOSED,
        proposerId: String = "uid-2",
        receiverId: String = "uid-1",
        createdAt: Long = 100L,
        items: List<TradeItem> = listOf(tradeItem()),
    ) = TradeProposal(
        id = id, status = status, proposerId = proposerId, receiverId = receiverId,
        parentProposalId = null, rootProposalId = id, proposalVersion = 1,
        includesReviewCollectionFromProposer = false, includesReviewCollectionFromReceiver = false,
        proposerMarkedCompletedAt = null, receiverMarkedCompletedAt = null, cancellationReason = null,
        items = items, createdAt = createdAt, updatedAt = createdAt,
    )

    private fun openForTradeEntry(
        id: String = "oft-1",
        scryfallId: String = "c1",
        quantity: Int = 1,
        priceUsd: Double? = 10.0,
        priceEur: Double? = 9.0,
    ) = OpenForTradeEntry(
        id = id, userId = "uid-1", userCardId = "uc-1", scryfallId = scryfallId,
        quantity = quantity, isFoil = false, condition = "NM", language = "en", createdAt = 0L,
        card = discoverCard(scryfallId).let { c ->
            com.mmg.manahub.core.model.Card(
                scryfallId = c.scryfallId, name = c.name, printedName = null,
                manaCost = "{1}", cmc = 1.0, colors = emptyList(), colorIdentity = emptyList(),
                typeLine = "Creature", printedTypeLine = null, oracleText = null,
                printedText = null, keywords = emptyList(), power = null,
                toughness = null, loyalty = null, setCode = "TST",
                setName = "Test", collectorNumber = "1",
                rarity = "common", releasedAt = "2020-01-01",
                frameEffects = emptyList(), promoTypes = emptyList(), lang = "en",
                imageNormal = c.imageNormal, imageArtCrop = c.imageArtCrop,
                imageBackNormal = null, priceUsd = priceUsd, priceUsdFoil = null,
                priceEur = priceEur, priceEurFoil = null,
                legalityStandard = "legal", legalityPioneer = "legal",
                legalityModern = "legal", legalityCommander = "legal",
                flavorText = null, artist = "Artist", scryfallUri = "",
                tags = emptyList(), userTags = emptyList(), suggestedTags = emptyList(),
                relatedUris = emptyMap(), purchaseUris = emptyMap(),
            )
        },
    )

    private fun friendRequest(id: String = "fr-1", fromNickname: String = "Alice") = FriendRequest(
        id = id, fromUserId = "uid-friend", fromNickname = fromNickname,
        fromGameTag = "#FRIEND", fromAvatarUrl = null,
    )

    private fun userCardWithCard(rowId: String, scryfallId: String, name: String, quantity: Int = 1) =
        UserCardWithCard(
            userCard = UserCard(id = rowId, scryfallId = scryfallId, quantity = quantity),
            card = discoverCard(scryfallId).let { c ->
                com.mmg.manahub.core.model.Card(
                    scryfallId = c.scryfallId, name = name, printedName = null,
                    manaCost = "{1}", cmc = 1.0, colors = emptyList(), colorIdentity = emptyList(),
                    typeLine = "Creature", printedTypeLine = null, oracleText = null,
                    printedText = null, keywords = emptyList(), power = null,
                    toughness = null, loyalty = null, setCode = "TST",
                    setName = "Test", collectorNumber = "1",
                    rarity = "common", releasedAt = "2020-01-01",
                    frameEffects = emptyList(), promoTypes = emptyList(), lang = "en",
                    imageNormal = c.imageNormal, imageArtCrop = c.imageArtCrop,
                    imageBackNormal = null, priceUsd = null, priceUsdFoil = null,
                    priceEur = null, priceEurFoil = null,
                    legalityStandard = "legal", legalityPioneer = "legal",
                    legalityModern = "legal", legalityCommander = "legal",
                    flavorText = null, artist = "Artist", scryfallUri = "",
                    tags = emptyList(), userTags = emptyList(), suggestedTags = emptyList(),
                    relatedUris = emptyMap(), purchaseUris = emptyMap(),
                )
            },
        )

    private fun tradeSuggestion(cardId: String = "c1") = com.mmg.manahub.core.model.TradeSuggestion(
        wishingUserId = "uid-1", offeringUserId = "uid-2", cardId = cardId,
        matchAnyVariant = false, userCardId = "uc-1", offerFoil = false,
        offerCondition = "NM", offerLanguage = "en", suggestionType = "WISHLIST_MATCH",
    )

    private fun tournament(id: Long = 1L, status: String = "ACTIVE") = Tournament(
        id = id, name = "Friday Night Magic", format = "COMMANDER", structure = "SWISS",
        status = status, matchesPerPairing = 1, isRandomPairings = false, createdAt = 0L,
    )

    // ── TRADES_HUB: Inbox slide (Home feature overhaul Phase 1.1) ─────────────

    @Test
    fun `tradeSummary reflects pending proposal count and latest item count`() = runTest(testDispatcher) {
        sessionStateFlow.value = SessionState.Authenticated(authUser())
        val newest = tradeProposal(id = "p-newest", createdAt = 500L, items = listOf(tradeItem(), tradeItem(id = "item-2")))
        val older = tradeProposal(id = "p-older", createdAt = 100L, items = listOf(tradeItem()))
        every { tradesRepository.observeActiveProposals() } returns flowOf(listOf(older, newest))

        val vm = buildViewModel()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        val summary = vm.state.value.tradeSummary
        assertNotNull(summary)
        assertEquals(2, summary!!.pendingCount)
        assertEquals(2, summary.latestItemCount) // newest (createdAt=500) has 2 items
    }

    @Test
    fun `tradeSummary is zero-count with null preview when there are no pending proposals`() =
        runTest(testDispatcher) {
            sessionStateFlow.value = SessionState.Authenticated(authUser())
            every { tradesRepository.observeActiveProposals() } returns flowOf(emptyList())

            val vm = buildViewModel()
            backgroundScope.launch { vm.state.collect {} }
            advanceUntilIdle()

            val summary = vm.state.value.tradeSummary
            assertNotNull(summary)
            assertEquals(0, summary!!.pendingCount)
            assertNull(summary.latestItemCount)
        }

    @Test
    fun `tradeSummary excludes proposals not addressed to the current user or not PROPOSED`() =
        runTest(testDispatcher) {
            sessionStateFlow.value = SessionState.Authenticated(authUser())
            val notMine = tradeProposal(id = "p-not-mine", receiverId = "someone-else")
            val notProposed = tradeProposal(id = "p-accepted", status = TradeStatus.ACCEPTED)
            every { tradesRepository.observeActiveProposals() } returns flowOf(listOf(notMine, notProposed))

            val vm = buildViewModel()
            backgroundScope.launch { vm.state.collect {} }
            advanceUntilIdle()

            assertEquals(0, vm.state.value.tradeSummary!!.pendingCount)
        }

    @Test
    fun `tradeSummary is null when observeActiveProposals errors, without collapsing the rest of the board`() =
        runTest(testDispatcher) {
            sessionStateFlow.value = SessionState.Authenticated(authUser())
            every { tradesRepository.observeActiveProposals() } returns
                kotlinx.coroutines.flow.flow { throw RuntimeException("boom") }
            every { statsRepository.observeCollectionStats(any()) } returns flowOf(collectionStatsWithCards(5))

            val vm = buildViewModel()
            backgroundScope.launch { vm.state.collect {} }
            advanceUntilIdle()

            assertNull(vm.state.value.tradeSummary)
            // The rest of the board must still resolve.
            assertTrue(vm.state.value.layout.isNotEmpty())
            assertEquals(5, vm.state.value.libraryStats?.uniqueCards)
        }

    @Test
    fun `init warms the trades cache via refreshProposals when authenticated`() = runTest(testDispatcher) {
        // Home dashboard audit (HIGH): observeActiveProposals() is in-memory-only and starts empty
        // on every process start — without an explicit warm-up call TRADES_HUB's Inbox always shows
        // "0 pending trades" on a fresh Home open, even with real pending proposals waiting.
        sessionStateFlow.value = SessionState.Authenticated(authUser())

        val vm = buildViewModel()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        coVerify(atLeast = 1) { tradesRepository.refreshProposals("uid-1") }
    }

    @Test
    fun `init never calls refreshProposals when signed out`() = runTest(testDispatcher) {
        sessionStateFlow.value = SessionState.Unauthenticated

        val vm = buildViewModel()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        coVerify(exactly = 0) { tradesRepository.refreshProposals(any()) }
    }

    // ── TRADES_HUB: Suggestions slide ──────────────────────────────────────────

    @Test
    fun `tradeSuggestionsCount reflects the suggestion list size when authenticated`() =
        runTest(testDispatcher) {
            sessionStateFlow.value = SessionState.Authenticated(authUser())
            coEvery { tradeSuggestionsRepository.getSuggestions() } returns
                Result.success(listOf(tradeSuggestion("c1"), tradeSuggestion("c2"), tradeSuggestion("c3")))

            val vm = buildViewModel()
            backgroundScope.launch { vm.state.collect {} }
            advanceUntilIdle()

            assertEquals(3, vm.state.value.tradeSuggestionsCount)
        }

    @Test
    fun `tradeSuggestionsCount is zero when unauthenticated and getSuggestions is never called`() =
        runTest(testDispatcher) {
            sessionStateFlow.value = SessionState.Unauthenticated

            val vm = buildViewModel()
            backgroundScope.launch { vm.state.collect {} }
            advanceUntilIdle()

            assertEquals(0, vm.state.value.tradeSuggestionsCount)
            coVerify(exactly = 0) { tradeSuggestionsRepository.getSuggestions() }
        }

    @Test
    fun `tradeSuggestionsCount defaults to zero when getSuggestions fails`() = runTest(testDispatcher) {
        sessionStateFlow.value = SessionState.Authenticated(authUser())
        coEvery { tradeSuggestionsRepository.getSuggestions() } returns Result.failure(RuntimeException("network"))

        val vm = buildViewModel()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        assertEquals(0, vm.state.value.tradeSuggestionsCount)
    }

    @Test
    fun `getSuggestions is invoked exactly once per auth subscription, not per unrelated state change`() =
        runTest(testDispatcher) {
            sessionStateFlow.value = SessionState.Authenticated(authUser())
            coEvery { tradeSuggestionsRepository.getSuggestions() } returns Result.success(listOf(tradeSuggestion()))

            val vm = buildViewModel()
            backgroundScope.launch { vm.state.collect {} }
            advanceUntilIdle()
            assertEquals(1, vm.state.value.tradeSuggestionsCount)

            // Unrelated state changes (not an auth flip) must not re-trigger the lazy suspend fetch.
            totalGamesFlow.value = 7
            advanceUntilIdle()
            deckSummariesFlow.value = listOf(deckSummary("d1"))
            advanceUntilIdle()

            coVerify(exactly = 1) { tradeSuggestionsRepository.getSuggestions() }
        }

    // ── TRADES_HUB: Open for Trade slide ───────────────────────────────────────

    @Test
    fun `openForTradeCount and value reflect entries from OpenForTradeRepository`() = runTest(testDispatcher) {
        sessionStateFlow.value = SessionState.Authenticated(authUser())
        every { openForTradeRepository.observeLocal() } returns flowOf(
            listOf(openForTradeEntry(id = "oft-1", quantity = 2, priceUsd = 10.0, priceEur = 9.0)),
        )

        val vm = buildViewModel()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        assertEquals(2, vm.state.value.openForTradeCount)
        assertNotNull(vm.state.value.openForTradeValueDisplay)
    }

    @Test
    fun `openForTradeValueDisplay is null when total value is zero`() = runTest(testDispatcher) {
        sessionStateFlow.value = SessionState.Authenticated(authUser())
        every { openForTradeRepository.observeLocal() } returns flowOf(
            listOf(openForTradeEntry(id = "oft-1", priceUsd = null, priceEur = null)),
        )

        val vm = buildViewModel()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        assertEquals(1, vm.state.value.openForTradeCount)
        assertNull(vm.state.value.openForTradeValueDisplay)
    }

    @Test
    fun `openForTradeCount is zero when unauthenticated`() = runTest(testDispatcher) {
        sessionStateFlow.value = SessionState.Unauthenticated
        every { openForTradeRepository.observeLocal() } returns flowOf(listOf(openForTradeEntry()))

        val vm = buildViewModel()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        assertEquals(0, vm.state.value.openForTradeCount)
        assertNull(vm.state.value.openForTradeValueDisplay)
    }

    // ── SOCIAL_HUB: Friends slide (Phase 1.2.d) ────────────────────────────────

    @Test
    fun `friendCount and latestFriendRequestName reflect FriendRepository when authenticated`() =
        runTest(testDispatcher) {
            sessionStateFlow.value = SessionState.Authenticated(authUser())
            every { friendRepository.observeFriendCount() } returns flowOf(3)
            every { friendRepository.observePendingRequests() } returns
                flowOf(listOf(friendRequest(fromNickname = "Alice"), friendRequest(id = "fr-2", fromNickname = "Bob")))

            val vm = buildViewModel()
            backgroundScope.launch { vm.state.collect {} }
            advanceUntilIdle()

            assertEquals(3, vm.state.value.friendCount)
            assertEquals("Alice", vm.state.value.latestFriendRequestName)
        }

    @Test
    fun `friendCount is zero and latestFriendRequestName is null when unauthenticated`() =
        runTest(testDispatcher) {
            sessionStateFlow.value = SessionState.Unauthenticated
            every { friendRepository.observeFriendCount() } returns flowOf(5)

            val vm = buildViewModel()
            backgroundScope.launch { vm.state.collect {} }
            advanceUntilIdle()

            assertEquals(0, vm.state.value.friendCount)
            assertNull(vm.state.value.latestFriendRequestName)
        }

    @Test
    fun `latestFriendRequestName is null when there are no pending requests`() = runTest(testDispatcher) {
        sessionStateFlow.value = SessionState.Authenticated(authUser())
        every { friendRepository.observeFriendCount() } returns flowOf(1)
        every { friendRepository.observePendingRequests() } returns flowOf(emptyList())

        val vm = buildViewModel()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        assertNull(vm.state.value.latestFriendRequestName)
    }

    // ── SOCIAL_HUB: Most Wishlisted + Milestones (community stats RPC) ────────

    @Test
    fun `communityStats mostWishlisted resolves card names locally and keeps unresolved entries with empty name`() =
        runTest(testDispatcher) {
            sessionStateFlow.value = SessionState.Authenticated(authUser())
            val payload = CommunityStats(
                topCommanders = emptyList(), metaArchetypes = emptyList(),
                mostWishlisted = listOf(
                    CommunityEntry(id = "c1", name = "", count = 5, percentage = 0.0),
                    CommunityEntry(id = "unresolvable", name = "", count = 2, percentage = 0.0),
                ),
                milestones = emptyList(),
            )
            every { communityStatsRepository.observeCommunityStats() } returns flowOf(payload)
            coEvery { cardRepository.getCardsByIds(listOf("c1", "unresolvable")) } returns
                listOf(discoverCard("c1")).map {
                    com.mmg.manahub.core.model.Card(
                        scryfallId = it.scryfallId, name = "Sol Ring", printedName = null,
                        manaCost = "{1}", cmc = 1.0, colors = emptyList(), colorIdentity = emptyList(),
                        typeLine = "Artifact", printedTypeLine = null, oracleText = null,
                        printedText = null, keywords = emptyList(), power = null,
                        toughness = null, loyalty = null, setCode = "TST",
                        setName = "Test", collectorNumber = "1",
                        rarity = "common", releasedAt = "2020-01-01",
                        frameEffects = emptyList(), promoTypes = emptyList(), lang = "en",
                        imageNormal = null, imageArtCrop = null,
                        imageBackNormal = null, priceUsd = null, priceUsdFoil = null,
                        priceEur = null, priceEurFoil = null,
                        legalityStandard = "legal", legalityPioneer = "legal",
                        legalityModern = "legal", legalityCommander = "legal",
                        flavorText = null, artist = "Artist", scryfallUri = "",
                        tags = emptyList(), userTags = emptyList(), suggestedTags = emptyList(),
                        relatedUris = emptyMap(), purchaseUris = emptyMap(),
                    )
                }

            val vm = buildViewModel()
            backgroundScope.launch { vm.state.collect {} }
            advanceUntilIdle()

            val resolved = vm.state.value.communityStats!!.mostWishlisted
            // Both entries are kept — "unresolvable" (not in the local cache) is NOT dropped, it
            // renders degraded with an empty name (Home dashboard audit, HIGH: dropping unresolved
            // entries emptied the slide out for most users in practice).
            assertEquals(2, resolved.size)
            assertEquals("c1", resolved[0].id)
            assertEquals("Sol Ring", resolved[0].name)
            assertEquals("unresolvable", resolved[1].id)
            assertEquals("", resolved[1].name)
        }

    @Test
    fun `communityStats milestones pass through with real ids and formatted string values`() =
        runTest(testDispatcher) {
            sessionStateFlow.value = SessionState.Authenticated(authUser())
            val payload = CommunityStats(
                topCommanders = emptyList(), metaArchetypes = emptyList(), mostWishlisted = emptyList(),
                milestones = listOf(
                    CommunityMilestone(id = "active_collectors", label = "Active collectors", value = "4"),
                    CommunityMilestone(id = "decks_built", label = "Decks built", value = "29"),
                ),
            )
            every { communityStatsRepository.observeCommunityStats() } returns flowOf(payload)

            val vm = buildViewModel()
            backgroundScope.launch { vm.state.collect {} }
            advanceUntilIdle()

            val milestones = vm.state.value.communityStats!!.milestones
            assertEquals(listOf("active_collectors", "decks_built"), milestones.map { it.id })
            assertEquals(listOf("4", "29"), milestones.map { it.value })
        }

    @Test
    fun `communityStats is null (not a fake zeroed object) when the repository has no payload`() =
        runTest(testDispatcher) {
            sessionStateFlow.value = SessionState.Authenticated(authUser())
            every { communityStatsRepository.observeCommunityStats() } returns flowOf(null)

            val vm = buildViewModel()
            backgroundScope.launch { vm.state.collect {} }
            advanceUntilIdle()

            assertNull(vm.state.value.communityStats)
        }

    // ── SOCIAL_HUB: TopCommanders is confirmed removed (no-stub rule) ─────────

    @Test
    fun `HomeWidgetType has no TopCommanders entry`() {
        assertTrue(HomeWidgetType.entries.none { it.name.contains("TOP_COMMANDERS") })
        assertTrue(HomeWidgetType.entries.none { it.persistedId == "top_commanders" })
    }

    @Test
    fun `default layouts never include a TopCommanders-style widget`() = runTest(testDispatcher) {
        sessionStateFlow.value = SessionState.Authenticated(authUser())
        val vm = buildViewModel()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        assertTrue(vm.state.value.layout.none { it.type.persistedId == "top_commanders" })
    }

    // ── SOCIAL_HUB: Archidekt trending decks (Phase 1.2.c) ─────────────────────

    @Test
    fun `archidektTrending reflects decks from ArchidektTrendingRepository`() = runTest(testDispatcher) {
        sessionStateFlow.value = SessionState.Authenticated(authUser())
        val decks = listOf(
            ArchidektTrendingDeck(id = 111, name = "Atraxa Superfriends", viewCount = 5000, deckUrl = "https://archidekt.com/decks/111"),
        )
        every { archidektTrendingRepository.observeTrendingDecks() } returns flowOf(decks)

        val vm = buildViewModel()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        assertEquals(decks, vm.state.value.archidektTrending)
        assertTrue(vm.state.value.archidektTrending.first().deckUrl.startsWith("https://archidekt.com/decks/"))
    }

    @Test
    fun `archidektTrending is empty when unauthenticated`() = runTest(testDispatcher) {
        sessionStateFlow.value = SessionState.Unauthenticated
        every { archidektTrendingRepository.observeTrendingDecks() } returns flowOf(
            listOf(ArchidektTrendingDeck(id = 1, name = "Deck", viewCount = 1, deckUrl = "https://archidekt.com/decks/1")),
        )

        val vm = buildViewModel()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        assertTrue(vm.state.value.archidektTrending.isEmpty())
    }

    // ── SOCIAL_HUB: Active tournament round (Phase 1.2.e, fixes F-3) ──────────

    @Test
    fun `activeTournamentSummary round is derived from observeCurrentRound, never hardcoded 0`() =
        runTest(testDispatcher) {
            tournamentsFlow.value = listOf(tournament(id = 1L, status = "ACTIVE"))
            every { tournamentRepository.observeCurrentRound(1L) } returns flowOf(2)

            val vm = buildViewModel()
            backgroundScope.launch { vm.state.collect {} }
            advanceUntilIdle()

            val summary = vm.state.value.activeTournamentSummary
            assertNotNull(summary)
            assertEquals(2, summary!!.round)
            assertNotEquals(0, summary.round)
        }

    @Test
    fun `activeTournamentSummary standing is always null (never a fake value)`() = runTest(testDispatcher) {
        tournamentsFlow.value = listOf(tournament(id = 1L, status = "ACTIVE"))
        every { tournamentRepository.observeCurrentRound(1L) } returns flowOf(3)

        val vm = buildViewModel()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        assertNull(vm.state.value.activeTournamentSummary?.standing)
    }

    @Test
    fun `activeTournamentSummary is null when there is no ACTIVE or SETUP tournament`() =
        runTest(testDispatcher) {
            tournamentsFlow.value = listOf(tournament(id = 1L, status = "FINISHED"))

            val vm = buildViewModel()
            backgroundScope.launch { vm.state.collect {} }
            advanceUntilIdle()

            assertNull(vm.state.value.activeTournamentSummary)
        }

    @Test
    fun `activeTournamentSummary picks a SETUP tournament when no ACTIVE tournament exists`() =
        runTest(testDispatcher) {
            tournamentsFlow.value = listOf(tournament(id = 9L, status = "SETUP"))
            every { tournamentRepository.observeCurrentRound(9L) } returns flowOf(1)

            val vm = buildViewModel()
            backgroundScope.launch { vm.state.collect {} }
            advanceUntilIdle()

            assertEquals(9L, vm.state.value.activeTournamentSummary?.tournamentId)
        }

    // ── RECENTLY_ADDED widget (Phase 2.1) ──────────────────────────────────────

    @Test
    fun `recentlyAdded reflects rows from observeRecentlyAdded preserving newest-first order`() =
        runTest(testDispatcher) {
            val rows = listOf(
                userCardWithCard(rowId = "row-2", scryfallId = "c2", name = "Newest Card", quantity = 3),
                userCardWithCard(rowId = "row-1", scryfallId = "c1", name = "Older Card", quantity = 1),
            )
            every { userCardRepository.observeRecentlyAdded(any()) } returns flowOf(rows)

            val vm = buildViewModel()
            backgroundScope.launch { vm.state.collect {} }
            advanceUntilIdle()

            val recentlyAdded = vm.state.value.recentlyAdded
            assertEquals(listOf("row-2", "row-1"), recentlyAdded.map { it.rowId })
            assertEquals("Newest Card", recentlyAdded.first().card.name)
            assertEquals(3, recentlyAdded.first().quantity)
        }

    @Test
    fun `recentlyAdded is empty when observeRecentlyAdded errors, without collapsing the board`() =
        runTest(testDispatcher) {
            every { userCardRepository.observeRecentlyAdded(any()) } returns
                kotlinx.coroutines.flow.flow { throw RuntimeException("db error") }
            every { statsRepository.observeCollectionStats(any()) } returns flowOf(collectionStatsWithCards(1))

            val vm = buildViewModel()
            backgroundScope.launch { vm.state.collect {} }
            advanceUntilIdle()

            assertTrue(vm.state.value.recentlyAdded.isEmpty())
            assertTrue(vm.state.value.layout.isNotEmpty())
            assertEquals(1, vm.state.value.libraryStats?.uniqueCards)
        }

    // ── BestDeck colorIdentity (fixes F-6) ─────────────────────────────────────

    @Test
    fun `bestDeck colorIdentity is joined from the matching deck summary`() = runTest(testDispatcher) {
        val deckWithColors = deckSummary(id = "deck-red-green").copy(colorIdentity = setOf("R", "G"))
        deckSummariesFlow.value = listOf(deckWithColors)
        every { gameSessionRepository.observeDeckStats() } returns flowOf(
            listOf(
                com.mmg.manahub.feature.game.domain.model.DeckStats(
                    deckId = "deck-red-green", deckName = "Gruul Stompy", totalGames = 4, wins = 3,
                ),
            ),
        )

        val vm = buildViewModel()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        val bestDeck = vm.state.value.bestDeck
        assertNotNull(bestDeck)
        assertEquals(setOf("R", "G"), bestDeck!!.colorIdentity)
    }

    @Test
    fun `bestDeck colorIdentity is empty when no matching deck summary exists`() = runTest(testDispatcher) {
        deckSummariesFlow.value = emptyList()
        every { gameSessionRepository.observeDeckStats() } returns flowOf(
            listOf(
                com.mmg.manahub.feature.game.domain.model.DeckStats(
                    deckId = "missing-deck", deckName = "Ghost Deck", totalGames = 2, wins = 1,
                ),
            ),
        )

        val vm = buildViewModel()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        assertEquals(emptySet<String>(), vm.state.value.bestDeck?.colorIdentity)
    }

    // ── WishlistProgress estimatedValueDisplay (fixes F-7) ─────────────────────

    @Test
    fun `wishlistStats estimatedValueDisplay is present and non-blank when cards are priced`() =
        runTest(testDispatcher) {
            sessionStateFlow.value = SessionState.Authenticated(authUser())
            val entry = WishlistEntry(
                id = "w1", userId = "u1", cardId = "c1", quantity = 2,
                matchAnyVariant = false, isFoil = false, condition = "NM", language = "en",
                createdAt = 123L,
                card = discoverCard("c1").let { c ->
                    com.mmg.manahub.core.model.Card(
                        scryfallId = c.scryfallId, name = c.name, printedName = null,
                        manaCost = "{1}", cmc = 1.0, colors = emptyList(), colorIdentity = emptyList(),
                        typeLine = "Creature", printedTypeLine = null, oracleText = null,
                        printedText = null, keywords = emptyList(), power = null,
                        toughness = null, loyalty = null, setCode = "TST",
                        setName = "Test", collectorNumber = "1",
                        rarity = "common", releasedAt = "2020-01-01",
                        frameEffects = emptyList(), promoTypes = emptyList(), lang = "en",
                        imageNormal = c.imageNormal, imageArtCrop = c.imageArtCrop,
                        imageBackNormal = null, priceUsd = 5.0, priceUsdFoil = null,
                        priceEur = 4.5, priceEurFoil = null,
                        legalityStandard = "legal", legalityPioneer = "legal",
                        legalityModern = "legal", legalityCommander = "legal",
                        flavorText = null, artist = "Artist", scryfallUri = "",
                        tags = emptyList(), userTags = emptyList(), suggestedTags = emptyList(),
                        relatedUris = emptyMap(), purchaseUris = emptyMap(),
                    )
                },
            )
            every { wishlistRepository.observeLocal() } returns flowOf(listOf(entry))

            val vm = buildViewModel()
            backgroundScope.launch { vm.state.collect {} }
            advanceUntilIdle()

            val stats = vm.state.value.wishlistStats
            assertNotNull(stats)
            assertTrue(stats!!.estimatedValueDisplay.isNotBlank())
        }

    // ── LastGameRecap.opponentCount (fixes F-5) ────────────────────────────────

    @Test
    fun `lastGameRecap opponentCount reflects the real value from session history`() = runTest(testDispatcher) {
        val entry = com.mmg.manahub.feature.game.domain.model.SessionHistoryEntry(
            sessionId = 1L, mode = "FFA", totalTurns = 8, durationMs = 60_000L, playedAt = 1000L,
            winnerName = "Wizard", surveyStatus = "NONE", localIsWinner = true,
            localDeckId = "d1", localDeckName = "My Deck", opponentCount = 3,
        )
        every { gameSessionRepository.observeLocalSessionHistory(any()) } returns flowOf(listOf(entry))

        val vm = buildViewModel()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        assertEquals(3, vm.state.value.lastGameRecap?.opponentCount)
    }

    // ── First Steps: buildVisibleSteps condition table (Phase 2.2) ─────────────

    @Test
    fun `STEP_FIRST_ADD_CARD is hidden once the collection has at least one unique card`() =
        runTest(testDispatcher) {
            every { statsRepository.observeCollectionStats(any()) } returns flowOf(collectionStatsWithCards(1))

            val vm = buildViewModel()
            backgroundScope.launch { vm.state.collect {} }
            advanceUntilIdle()

            val steps = (vm.state.value.hero as? HomeHeroState.Welcome)?.steps.orEmpty()
            assertFalse(steps.any { it.id == STEP_FIRST_ADD_CARD })
        }

    @Test
    fun `STEP_FIRST_CREATE_DECK is hidden once the user has at least one deck`() = runTest(testDispatcher) {
        deckSummariesFlow.value = listOf(deckSummary())

        val vm = buildViewModel()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        val steps = (vm.state.value.hero as? HomeHeroState.Welcome)?.steps.orEmpty()
        assertFalse(steps.any { it.id == STEP_FIRST_CREATE_DECK })
    }

    @Test
    fun `STEP_FIRST_PLAYTEST_DECK stays hidden while Playtest is flag-disabled, even with a deck and zero playtest sessions`() =
        runTest(testDispatcher) {
            // Deck Playtest is hidden for release (2026-07-14, DeckFeatureFlags.PLAYTEST_ENABLED =
            // false) — this step's CTA is HomeAction.PlaytestRecentDeck, so it must not surface a
            // shortcut into a hidden feature even when its data condition (deckCount > 0 &&
            // totalPlaytestCount == 0) is otherwise satisfied. Re-enable this assertion's INVERSE
            // (assertTrue) once PLAYTEST_ENABLED flips back to true.
            deckSummariesFlow.value = listOf(deckSummary())
            every { playtestRepository.observeTotalTestCount() } returns flowOf(0)

            val vm = buildViewModel()
            backgroundScope.launch { vm.state.collect {} }
            advanceUntilIdle()

            val steps = (vm.state.value.hero as? HomeHeroState.Welcome)?.steps.orEmpty()
            assertFalse(steps.any { it.id == STEP_FIRST_PLAYTEST_DECK })
        }

    @Test
    fun `STEP_FIRST_PLAYTEST_DECK is hidden with no decks even if playtest count is zero`() =
        runTest(testDispatcher) {
            deckSummariesFlow.value = emptyList()
            every { playtestRepository.observeTotalTestCount() } returns flowOf(0)

            val vm = buildViewModel()
            backgroundScope.launch { vm.state.collect {} }
            advanceUntilIdle()

            val steps = (vm.state.value.hero as? HomeHeroState.Welcome)?.steps.orEmpty()
            assertFalse(steps.any { it.id == STEP_FIRST_PLAYTEST_DECK })
        }

    @Test
    fun `STEP_FIRST_PLAYTEST_DECK auto-hides once at least one playtest session has been saved`() =
        runTest(testDispatcher) {
            deckSummariesFlow.value = listOf(deckSummary())
            every { playtestRepository.observeTotalTestCount() } returns flowOf(1)

            val vm = buildViewModel()
            backgroundScope.launch { vm.state.collect {} }
            advanceUntilIdle()

            val steps = (vm.state.value.hero as? HomeHeroState.Welcome)?.steps.orEmpty()
            assertFalse(steps.any { it.id == STEP_FIRST_PLAYTEST_DECK })
        }

    @Test
    fun `STEP_FIRST_CREATE_ACCOUNT is hidden once authenticated`() = runTest(testDispatcher) {
        sessionStateFlow.value = SessionState.Authenticated(authUser())

        val vm = buildViewModel()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        val steps = (vm.state.value.hero as? HomeHeroState.Welcome)?.steps.orEmpty()
        assertFalse(steps.any { it.id == STEP_FIRST_CREATE_ACCOUNT })
    }

    @Test
    fun `friend-gated steps are reachable once friendCount is real and greater than zero`() =
        runTest(testDispatcher) {
            sessionStateFlow.value = SessionState.Authenticated(authUser())
            every { friendRepository.observeFriendCount() } returns flowOf(2)
            every { statsRepository.observeCollectionStats(any()) } returns flowOf(collectionStatsWithCards(3))

            val vm = buildViewModel()
            backgroundScope.launch { vm.state.collect {} }
            advanceUntilIdle()

            val steps = (vm.state.value.hero as? HomeHeroState.Welcome)?.steps.orEmpty()
            assertTrue(steps.any { it.id == STEP_FIRST_REVIEW_FRIEND })
            assertTrue(steps.any { it.id == STEP_FIRST_CREATE_TRADE })
            // ADD_FRIEND is only shown while friendCount == 0.
            assertFalse(steps.any { it.id == STEP_FIRST_ADD_FRIEND })
        }

    @Test
    fun `STEP_FIRST_ADD_FRIEND is visible while friendCount is zero and authenticated`() =
        runTest(testDispatcher) {
            sessionStateFlow.value = SessionState.Authenticated(authUser())
            every { friendRepository.observeFriendCount() } returns flowOf(0)

            val vm = buildViewModel()
            backgroundScope.launch { vm.state.collect {} }
            advanceUntilIdle()

            val steps = (vm.state.value.hero as? HomeHeroState.Welcome)?.steps.orEmpty()
            assertTrue(steps.any { it.id == STEP_FIRST_ADD_FRIEND })
            assertFalse(steps.any { it.id == STEP_FIRST_REVIEW_FRIEND })
            assertFalse(steps.any { it.id == STEP_FIRST_CREATE_TRADE })
        }

    @Test
    fun `STEP_FIRST_OPEN_FOR_TRADE is hidden once the open-for-trade list has an entry`() =
        runTest(testDispatcher) {
            every { statsRepository.observeCollectionStats(any()) } returns flowOf(collectionStatsWithCards(1))
            sessionStateFlow.value = SessionState.Authenticated(authUser())
            every { openForTradeRepository.observeLocal() } returns flowOf(listOf(openForTradeEntry()))

            val vm = buildViewModel()
            backgroundScope.launch { vm.state.collect {} }
            advanceUntilIdle()

            val steps = (vm.state.value.hero as? HomeHeroState.Welcome)?.steps.orEmpty()
            assertFalse(steps.any { it.id == STEP_FIRST_OPEN_FOR_TRADE })
        }

    @Test
    fun `STEP_FIRST_ADD_WISHLIST is hidden once the wishlist has an entry`() = runTest(testDispatcher) {
        sessionStateFlow.value = SessionState.Authenticated(authUser())
        val entry = WishlistEntry(
            id = "w1", userId = "u1", cardId = "c1", quantity = 1,
            matchAnyVariant = false, isFoil = false, condition = "NM", language = "en",
            createdAt = 123L, card = null,
        )
        every { wishlistRepository.observeLocal() } returns flowOf(listOf(entry))

        val vm = buildViewModel()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        val steps = (vm.state.value.hero as? HomeHeroState.Welcome)?.steps.orEmpty()
        assertFalse(steps.any { it.id == STEP_FIRST_ADD_WISHLIST })
    }

    @Test
    fun `STEP_FIRST_ADD_WISHLIST is hidden when signed out even with an empty wishlist`() =
        runTest(testDispatcher) {
            // WISHLIST_PROGRESS is ACCOUNT_GATED and wishlistFlow forces wishlistCount to 0 while
            // signed out — without an explicit isAuthenticated gate this step would sit forever,
            // uncompletable, CTA-ing a signed-out user into a widget they can't use pre-auth (Home
            // dashboard audit, MEDIUM).
            sessionStateFlow.value = SessionState.Unauthenticated
            every { wishlistRepository.observeLocal() } returns flowOf(emptyList())

            val vm = buildViewModel()
            backgroundScope.launch { vm.state.collect {} }
            advanceUntilIdle()

            val steps = (vm.state.value.hero as? HomeHeroState.Welcome)?.steps.orEmpty()
            assertFalse(steps.any { it.id == STEP_FIRST_ADD_WISHLIST })
        }

    @Test
    fun `visible first steps preserve the canonical activation-funnel order`() = runTest(testDispatcher) {
        // Everything hidden by data is left visible: fresh unauthenticated user, empty collection.
        val vm = buildViewModel()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        val steps = (vm.state.value.hero as? HomeHeroState.Welcome)?.steps.orEmpty()
        val catalogOrder = ALL_FIRST_STEPS.map { it.id }
        val visibleIds = steps.map { it.id }
        // The visible subset must appear in the SAME relative order as the full catalog.
        assertEquals(visibleIds, catalogOrder.filter { it in visibleIds })
    }

    // ── First Steps: tap = CTA only, dismiss is a separate explicit action ────

    @Test
    fun `dispatching a step's CTA action does not persist a skip`() = runTest(testDispatcher) {
        val vm = buildViewModel()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        // OpenLibrary is STEP_FIRST_ADD_CARD's CTA — the VM does not intercept navigation
        // actions, so tapping the CTA must never call skipFirstStep.
        vm.onAction(HomeAction.OpenLibrary)
        advanceUntilIdle()

        coVerify(exactly = 0) { userPrefsDataStore.skipFirstStep(any()) }
    }

    @Test
    fun `explicit skipFirstStep persists the dismissal via userPrefsDataStore`() = runTest(testDispatcher) {
        val vm = buildViewModel()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        vm.skipFirstStep(STEP_FIRST_SCAN_CARD)
        advanceUntilIdle()

        coVerify(exactly = 1) { userPrefsDataStore.skipFirstStep(STEP_FIRST_SCAN_CARD) }
    }

    @Test
    fun `a step present in the skipped set is excluded from visible steps regardless of its condition`() =
        runTest(testDispatcher) {
            every { userPrefsDataStore.observeSkippedFirstSteps() } returns flowOf(setOf(STEP_FIRST_ADD_CARD))
            // Condition would otherwise show this step (collection empty).
            every { statsRepository.observeCollectionStats(any()) } returns flowOf(collectionStatsWithCards(0))

            val vm = buildViewModel()
            backgroundScope.launch { vm.state.collect {} }
            advanceUntilIdle()

            val steps = (vm.state.value.hero as? HomeHeroState.Welcome)?.steps.orEmpty()
            assertFalse(steps.any { it.id == STEP_FIRST_ADD_CARD })
        }

    // ── Default widget layouts: exact ordering + category contiguity ──────────

    @Test
    fun `signed-out default layout matches the exact overhauled widget order`() = runTest(testDispatcher) {
        sessionStateFlow.value = SessionState.Unauthenticated
        savedLayoutTokens.value = null

        val vm = buildViewModel()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        val expected = listOf(
            HomeWidgetType.CONTEXT_HERO, HomeWidgetType.QUICK_ACTIONS,
            HomeWidgetType.COLLECTION_STATS_HUB,
            HomeWidgetType.CARD_OF_THE_DAY, HomeWidgetType.DISCOVER_CARDS,
            HomeWidgetType.LATEST_SETS, HomeWidgetType.RULES_TIP, HomeWidgetType.MTG_NEWS,
        )
        assertEquals(expected, vm.state.value.layout.map { it.type })
    }

    @Test
    fun `signed-in default layout matches the exact overhauled widget order`() = runTest(testDispatcher) {
        sessionStateFlow.value = SessionState.Authenticated(authUser())
        savedLayoutTokens.value = null

        val vm = buildViewModel()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        val expected = listOf(
            HomeWidgetType.CONTEXT_HERO, HomeWidgetType.QUICK_ACTIONS,
            HomeWidgetType.GAME_STATS_HUB,
            HomeWidgetType.YOUR_DECKS_SHELF, HomeWidgetType.COLLECTION_STATS_HUB, HomeWidgetType.RECENTLY_ADDED,
            HomeWidgetType.TRADES_HUB, HomeWidgetType.SOCIAL_HUB,
            HomeWidgetType.LATEST_SETS, HomeWidgetType.MTG_NEWS, HomeWidgetType.RULES_TIP,
        )
        assertEquals(expected, vm.state.value.layout.map { it.type })
    }

    @Test
    fun `both default layouts keep widget categories contiguous`() = runTest(testDispatcher) {
        // "Contiguous" means every category's widgets form ONE unbroken run — NOT that categories
        // appear in WidgetCategory-enum-ordinal ascending order. The signed-in default deliberately
        // places SOCIAL (ordinal 4: TRADES_HUB/SOCIAL_HUB) before DISCOVER (ordinal 3: LATEST_SETS/
        // MTG_NEWS/RULES_TIP) — social/trade engagement is meant to outrank evergreen discovery in
        // the signed-in ordering (see HomeViewModel.defaultLayoutSignedIn's KDoc) — so
        // `ordinals.sorted() == ordinals` is NOT the right assertion here (it previously failed on
        // the real signed-in default: [0,0,1,2,2,2,4,4,3,3,3] is a valid "contiguous" layout with a
        // non-monotonic category order). See feedback_home_default_layout_category_order memory for
        // the discrepancy this uncovered against the pre-existing "AddWidget..." test's comment.
        for (authed in listOf(false, true)) {
            sessionStateFlow.value = if (authed) SessionState.Authenticated(authUser()) else SessionState.Unauthenticated
            savedLayoutTokens.value = null

            val vm = buildViewModel()
            backgroundScope.launch { vm.state.collect {} }
            advanceUntilIdle()

            val categories = vm.state.value.layout.map { it.type.category }
            val runsPerCategory = categories.distinct().associateWith { category ->
                // Count how many separate contiguous runs of `category` appear in the list.
                categories.fold(0 to null as WidgetCategory?) { (runs, prev), current ->
                    if (current == category && prev != category) runs + 1 to current else runs to current
                }.first
            }
            runsPerCategory.forEach { (category, runCount) ->
                assertEquals("authed=$authed category=$category should appear as a single run", 1, runCount)
            }
        }
    }

    @Test
    fun `a persisted layout is never overwritten by the new auth-appropriate defaults`() =
        runTest(testDispatcher) {
            sessionStateFlow.value = SessionState.Unauthenticated
            // A minimal, deliberately non-default persisted layout.
            savedLayoutTokens.value = "context_hero:MEDIUM,rules_tip:MEDIUM"

            val vm = buildViewModel()
            backgroundScope.launch { vm.state.collect {} }
            advanceUntilIdle()

            assertEquals(
                listOf(HomeWidgetType.CONTEXT_HERO, HomeWidgetType.RULES_TIP),
                vm.state.value.layout.map { it.type },
            )
        }
}
