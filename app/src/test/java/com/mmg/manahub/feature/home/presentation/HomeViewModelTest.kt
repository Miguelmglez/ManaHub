package com.mmg.manahub.feature.home.presentation

import app.cash.turbine.test
import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.domain.model.CollectionStats
import com.mmg.manahub.core.domain.model.DeckSummary
import com.mmg.manahub.core.domain.model.PreferredCurrency
import com.mmg.manahub.core.domain.model.DataResult
import com.mmg.manahub.core.domain.repository.CommunityStatsRepository
import com.mmg.manahub.core.domain.repository.DeckRepository
import com.mmg.manahub.core.domain.repository.GameSessionRepository
import com.mmg.manahub.core.domain.repository.StatsRepository
import com.mmg.manahub.core.domain.repository.TournamentRepository
import com.mmg.manahub.feature.draft.domain.repository.DraftRepository
import com.mmg.manahub.feature.auth.domain.model.AuthUser
import com.mmg.manahub.feature.auth.domain.model.SessionState
import com.mmg.manahub.feature.auth.domain.repository.AuthRepository
import com.mmg.manahub.feature.draft.domain.model.DraftConfig
import com.mmg.manahub.feature.draft.domain.model.DraftSeat
import com.mmg.manahub.feature.draft.domain.model.DraftState
import com.mmg.manahub.feature.draft.domain.model.DraftStatus
import com.mmg.manahub.feature.draft.domain.model.PassDirection
import com.mmg.manahub.feature.draft.domain.repository.DraftSimRepository
import com.mmg.manahub.core.domain.model.news.NewsItem
import com.mmg.manahub.feature.home.domain.usecase.GetAccountNudgeUseCase
import com.mmg.manahub.feature.news.domain.usecase.GetNewsFeedUseCase
import com.mmg.manahub.feature.news.domain.usecase.RefreshNewsFeedUseCase
import com.mmg.manahub.feature.trades.domain.model.WishlistEntry
import com.mmg.manahub.feature.trades.domain.repository.WishlistRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
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
        MutableStateFlow<List<com.mmg.manahub.core.data.local.entity.TournamentEntity>>(emptyList())
    private val deckSummariesFlow = MutableStateFlow<List<DeckSummary>>(emptyList())

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
    private val communityStatsRepository: CommunityStatsRepository = mockk(relaxed = true)
    private val draftRepository: DraftRepository = mockk(relaxed = true)
    private val cardRepository: com.mmg.manahub.core.domain.repository.CardRepository = mockk(relaxed = true)
    private val wishlistRepository: WishlistRepository = mockk(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        stubDefaultDependencies()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
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
        coEvery { refreshNewsFeedUseCase() } returns Result.success(Unit)

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
        coEvery { cardRepository.searchCards(any(), any()) } returns DataResult.Success(emptyList())

        // Wishlist default: empty.
        every { wishlistRepository.observeLocal() } returns flowOf(emptyList())

        // Layout round-trip: homeLayoutFlow decodes the saved tokens (or the supplied
        // default when none are saved); saveHomeLayout writes the tokens.
        every { userPrefsDataStore.homeLayoutFlow(any()) } answers {
            val default = firstArg<List<WidgetInstance>>()
            savedLayoutTokens.map { tokens -> decodeLayout(tokens, default) }
        }
        val layoutSlot = slot<List<WidgetInstance>>()
        coEvery { userPrefsDataStore.saveHomeLayout(capture(layoutSlot)) } answers {
            savedLayoutTokens.value = layoutSlot.captured.joinToString(",") { "${it.type.persistedId}:${it.size.name}" }
        }
    }

    /** Mirrors UserPreferencesDataStore decode: unknown ids/sizes are skipped; empty → default. */
    private fun decodeLayout(tokens: String?, default: List<WidgetInstance>): List<WidgetInstance> {
        val parsed = tokens
            ?.split(",")
            ?.mapNotNull { token ->
                val parts = token.trim().split(":")
                if (parts.size != 2) return@mapNotNull null
                val type = HomeWidgetType.fromPersistedId(parts[0].trim()) ?: return@mapNotNull null
                val size = WidgetSize.entries.firstOrNull { it.name == parts[1].trim() } ?: return@mapNotNull null
                WidgetInstance(type, size)
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
        cardRepository = cardRepository,
        communityStatsRepository = communityStatsRepository,
        draftRepository = draftRepository,
        wishlistRepository = wishlistRepository,
        getAccountNudgeUseCase = GetAccountNudgeUseCase(),
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
            QuickStartAction.START_GAME,
            QuickStartAction.SCAN_CARD,
            QuickStartAction.CREATE_DECK,
            QuickStartAction.DRAFT_GUIDE,
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

        val vm = buildViewModel()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        assertEquals(HomeViewModel.MAX_NEWS, vm.state.value.recentNews!!.size)
    }

    @Test
    fun `recentNews contains fewer than MAX_NEWS items when feed has fewer`() = runTest(testDispatcher) {
        val feed = listOf(newsArticle("1"), newsArticle("2"))
        every { getNewsFeedUseCase() } returns flowOf(feed)

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
            QuickStartAction.TOURNAMENTS,
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

    @Test
    fun `AddWidget appends the widget and persists`() = runTest(testDispatcher) {
        sessionStateFlow.value = SessionState.Unauthenticated

        val vm = buildViewModel()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()
        assertFalse(vm.state.value.layout.any { it.type == HomeWidgetType.GAME_STATS_HUB })

        vm.onAction(HomeAction.AddWidget(HomeWidgetType.GAME_STATS_HUB))
        advanceUntilIdle()

        assertEquals(HomeWidgetType.GAME_STATS_HUB, vm.state.value.layout.last().type)
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
            card = com.mmg.manahub.core.domain.model.Card(
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
        assertEquals("https://example.com/lotus_art.jpg", stats.cards.first().imageUrl)
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
}
