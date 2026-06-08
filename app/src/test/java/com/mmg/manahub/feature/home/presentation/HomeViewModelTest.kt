package com.mmg.manahub.feature.home.presentation

import app.cash.turbine.test
import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.domain.model.CollectionStats
import com.mmg.manahub.core.domain.model.DeckSummary
import com.mmg.manahub.core.domain.model.PreferredCurrency
import com.mmg.manahub.core.domain.repository.DeckRepository
import com.mmg.manahub.core.domain.repository.GameSessionRepository
import com.mmg.manahub.core.domain.repository.StatsRepository
import com.mmg.manahub.core.domain.repository.TournamentRepository
import com.mmg.manahub.feature.auth.domain.model.AuthUser
import com.mmg.manahub.feature.auth.domain.model.SessionState
import com.mmg.manahub.feature.auth.domain.repository.AuthRepository
import com.mmg.manahub.feature.draft.domain.model.DraftConfig
import com.mmg.manahub.feature.draft.domain.model.DraftSeat
import com.mmg.manahub.feature.draft.domain.model.DraftState
import com.mmg.manahub.feature.draft.domain.model.DraftStatus
import com.mmg.manahub.feature.draft.domain.model.PassDirection
import com.mmg.manahub.feature.draft.domain.repository.DraftSimRepository
import com.mmg.manahub.feature.news.domain.model.NewsItem
import com.mmg.manahub.feature.news.domain.usecase.GetNewsFeedUseCase
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
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

    private val userPrefsDataStore: UserPreferencesDataStore = mockk(relaxed = true)
    private val statsRepository: StatsRepository = mockk(relaxed = true)
    private val deckRepository: DeckRepository = mockk(relaxed = true)
    private val gameSessionRepository: GameSessionRepository = mockk(relaxed = true)
    private val draftSimRepository: DraftSimRepository = mockk(relaxed = true)
    private val tournamentRepository: TournamentRepository = mockk(relaxed = true)
    private val authRepository: AuthRepository = mockk(relaxed = true)
    private val getNewsFeedUseCase: GetNewsFeedUseCase = mockk(relaxed = true)

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
        every { userPrefsDataStore.isNudgeCoolingDown() } returns isCoolingDownFlow
        every { userPrefsDataStore.avatarUrlFlow } returns flowOf(null)
        every { statsRepository.observeCollectionStats(any()) } returns
            flowOf(emptyCollectionStats())
        every { deckRepository.observeAllDeckSummaries() } returns deckSummariesFlow
        every { gameSessionRepository.observeTotalGames() } returns totalGamesFlow
        every { draftSimRepository.observeActiveSession() } returns activeDraftFlow
        every { tournamentRepository.observeTournaments() } returns tournamentsFlow
        every { getNewsFeedUseCase() } returns flowOf(emptyList())
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

        assertEquals(HomeHeroState.Welcome, vm.state.value.hero)
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

        assertEquals(HomeHeroState.Welcome, vm.state.value.hero)
    }

    @Test
    fun `active draft with status BUILDING does not produce ActiveDraft hero`() =
        runTest(testDispatcher) {
            activeDraftFlow.value = draftingState().copy(status = DraftStatus.BUILDING)
            totalGamesFlow.value = 0

            val vm = buildViewModel()
            backgroundScope.launch { vm.state.collect {} }
            advanceUntilIdle()

            assertEquals(HomeHeroState.Welcome, vm.state.value.hero)
        }

    // ── continueItems ─────────────────────────────────────────────────────────

    @Test
    fun `continueItems contains DRAFT entry when draft is active and DRAFTING`() =
        runTest(testDispatcher) {
            activeDraftFlow.value = draftingState(setCode = "MKM")

            val vm = buildViewModel()
            backgroundScope.launch { vm.state.collect {} }
            advanceUntilIdle()

            assertTrue(vm.state.value.continueItems.any { it.type == ContinueType.DRAFT })
        }

    @Test
    fun `continueItems DRAFT entry subtitle contains pack and pick numbers`() =
        runTest(testDispatcher) {
            activeDraftFlow.value = draftingState().copy(round = 2, pickNumber = 5)

            val vm = buildViewModel()
            backgroundScope.launch { vm.state.collect {} }
            advanceUntilIdle()

            val draftItem = vm.state.value.continueItems.first { it.type == ContinueType.DRAFT }
            assertTrue(
                "Subtitle should mention pack/pick: '${draftItem.subtitle}'",
                draftItem.subtitle.contains("2") && draftItem.subtitle.contains("5"),
            )
        }

    @Test
    fun `continueItems contains TOURNAMENT entry when activeTournaments greater than 0`() =
        runTest(testDispatcher) {
            tournamentsFlow.value = listOf(tournamentEntity(status = "ACTIVE"))

            val vm = buildViewModel()
            backgroundScope.launch { vm.state.collect {} }
            advanceUntilIdle()

            assertTrue(vm.state.value.continueItems.any { it.type == ContinueType.TOURNAMENT })
        }

    @Test
    fun `continueItems does not contain TOURNAMENT entry when all tournaments are FINISHED`() =
        runTest(testDispatcher) {
            tournamentsFlow.value = listOf(tournamentEntity(status = "FINISHED"))

            val vm = buildViewModel()
            backgroundScope.launch { vm.state.collect {} }
            advanceUntilIdle()

            assertFalse(vm.state.value.continueItems.any { it.type == ContinueType.TOURNAMENT })
        }

    @Test
    fun `continueItems contains DECK entry from first deck summary`() = runTest(testDispatcher) {
        deckSummariesFlow.value = listOf(
            deckSummary(id = "deck-1", name = "Control Blue", cardCount = 60),
            deckSummary(id = "deck-2", name = "Aggro Red", cardCount = 40),
        )

        val vm = buildViewModel()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        val deckItem = vm.state.value.continueItems.firstOrNull { it.type == ContinueType.DECK }
        assertNotNull(deckItem)
        assertEquals("Control Blue", deckItem!!.label)
        assertEquals("deck-1", deckItem.id)
    }

    @Test
    fun `continueItems does not contain DECK entry when deck list is empty`() =
        runTest(testDispatcher) {
            deckSummariesFlow.value = emptyList()

            val vm = buildViewModel()
            backgroundScope.launch { vm.state.collect {} }
            advanceUntilIdle()

            assertFalse(vm.state.value.continueItems.any { it.type == ContinueType.DECK })
        }

    @Test
    fun `continueItems DECK subtitle shows card count`() = runTest(testDispatcher) {
        deckSummariesFlow.value = listOf(deckSummary(cardCount = 75))

        val vm = buildViewModel()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        val deckItem = vm.state.value.continueItems.first { it.type == ContinueType.DECK }
        assertTrue(
            "Subtitle should contain card count: '${deckItem.subtitle}'",
            deckItem.subtitle.contains("75"),
        )
    }

    @Test
    fun `TOURNAMENT subtitle says 1 in progress when exactly 1 active tournament`() =
        runTest(testDispatcher) {
            tournamentsFlow.value = listOf(tournamentEntity(status = "ACTIVE"))

            val vm = buildViewModel()
            backgroundScope.launch { vm.state.collect {} }
            advanceUntilIdle()

            val t = vm.state.value.continueItems.first { it.type == ContinueType.TOURNAMENT }
            assertEquals("1 in progress", t.subtitle)
        }

    @Test
    fun `TOURNAMENT subtitle shows count when multiple active tournaments`() =
        runTest(testDispatcher) {
            tournamentsFlow.value = listOf(
                tournamentEntity(id = 1L, status = "ACTIVE"),
                tournamentEntity(id = 2L, status = "SETUP"),
            )

            val vm = buildViewModel()
            backgroundScope.launch { vm.state.collect {} }
            advanceUntilIdle()

            val t = vm.state.value.continueItems.first { it.type == ContinueType.TOURNAMENT }
            assertEquals("2 in progress", t.subtitle)
        }

    // ── recentNews ────────────────────────────────────────────────────────────

    @Test
    fun `recentNews is capped at 3 items when feed has more`() = runTest(testDispatcher) {
        val feed = (1..10).map { newsArticle(it.toString()) }
        every { getNewsFeedUseCase() } returns flowOf(feed)

        val vm = buildViewModel()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        assertEquals(3, vm.state.value.recentNews.size)
    }

    @Test
    fun `recentNews contains fewer than 3 items when feed has fewer`() = runTest(testDispatcher) {
        val feed = listOf(newsArticle("1"), newsArticle("2"))
        every { getNewsFeedUseCase() } returns flowOf(feed)

        val vm = buildViewModel()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        assertEquals(2, vm.state.value.recentNews.size)
    }

    @Test
    fun `recentNews is empty when news feed is empty`() = runTest(testDispatcher) {
        every { getNewsFeedUseCase() } returns flowOf(emptyList())

        val vm = buildViewModel()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        assertTrue(vm.state.value.recentNews.isEmpty())
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

            val item = vm.state.value.recentNews.first()
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

        val vm = buildViewModel()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()
        assertEquals(HomeHeroState.Welcome, vm.state.value.hero)

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
}
