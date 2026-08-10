package com.mmg.manahub.feature.stats.presentation

import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.core.data.remote.ScryfallRemoteDataSource
import com.mmg.manahub.core.data.repository.TradesRepository
import com.mmg.manahub.core.data.usecase.stats.GetTradeStatsUseCase
import com.mmg.manahub.core.domain.auth.AuthRepository
import com.mmg.manahub.core.domain.auth.AuthUser
import com.mmg.manahub.core.domain.auth.SessionState
import com.mmg.manahub.core.domain.repository.DeckRepository
import com.mmg.manahub.core.domain.repository.UserPreferencesRepository
import com.mmg.manahub.core.domain.usecase.stats.GetCollectionSetCodesUseCase
import com.mmg.manahub.core.domain.usecase.stats.GetCollectionStatsUseCase
import com.mmg.manahub.core.domain.usecase.stats.GetSetCompletionCountsUseCase
import com.mmg.manahub.core.model.CollectionStats
import com.mmg.manahub.core.model.Deck
import com.mmg.manahub.core.model.MagicSet
import com.mmg.manahub.core.model.MtgColor
import com.mmg.manahub.core.model.PreferredCurrency
import com.mmg.manahub.core.model.SetType
import com.mmg.manahub.core.model.TradePartnerSummary
import com.mmg.manahub.core.model.TradeProposal
import com.mmg.manahub.core.model.TradeStats
import com.mmg.manahub.core.model.TradeStatus
import com.mmg.manahub.feature.game.domain.model.ArchetypeMatchupData
import com.mmg.manahub.feature.game.domain.model.DeckStats
import com.mmg.manahub.feature.game.domain.model.EliminationStats
import com.mmg.manahub.feature.game.domain.model.GameModeCount
import com.mmg.manahub.feature.game.domain.model.ModeWinrate
import com.mmg.manahub.feature.game.domain.model.PlayerCountWinrate
import com.mmg.manahub.feature.game.domain.model.SessionHistoryEntry
import com.mmg.manahub.feature.game.domain.repository.GameSessionRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [StatsViewModel] (Stats feature expansion, Phase 6 — 2026-07 test-writing pass).
 *
 * Covers: the collection-stats `flatMapLatest` re-subscription on color/set/currency changes, the
 * `observeAvailableSets()` failure degrade-not-crash fix, the new Phase 3 streak / mode-winrate /
 * player-count computations, "recent form", the fixed (Room-succeeds-first) delete-session toast,
 * and the TRADES tab's two-tier lazy-fetch state machine (Phase 4).
 *
 * Every game-stats fixture sets [SessionHistoryEntry.localIsWinner] explicitly (never inferred from
 * a name match) per ADR-001 / memory `feedback_survey_winloss_isLocal`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    // ── Mocked collaborators ────────────────────────────────────────────────────
    private val getStats = mockk<GetCollectionStatsUseCase>()
    private val getSetCodes = mockk<GetCollectionSetCodesUseCase>()
    private val getSetCompletionCounts = mockk<GetSetCompletionCountsUseCase>()
    private val scryfallDataSource = mockk<ScryfallRemoteDataSource>()
    private val userPreferencesDataStore = mockk<UserPreferencesRepository>(relaxed = true)
    private val gameSessionRepository = mockk<GameSessionRepository>()
    private val deckRepository = mockk<DeckRepository>()
    private val authRepository = mockk<AuthRepository>()
    private val tradesRepository = mockk<TradesRepository>()
    private val getTradeStats = mockk<GetTradeStatsUseCase>()

    // ── Controllable flows ──────────────────────────────────────────────────────
    private val currencyFlow = MutableStateFlow(PreferredCurrency.USD)
    private val totalGamesFlow = MutableStateFlow(0)
    private val localWinsFlow = MutableStateFlow(0)
    private val avgDurationFlow = MutableStateFlow<Double?>(null)
    private val favoriteModeFlow = MutableStateFlow<GameModeCount?>(null)
    private val mostFrequentEliminationFlow = MutableStateFlow<EliminationStats?>(null)
    private val pendingSurveyFlow = MutableStateFlow(0)
    private val localSessionHistoryFlow = MutableStateFlow<List<SessionHistoryEntry>>(emptyList())
    private val localDeckGameStatsFlow = MutableStateFlow<List<DeckStats>>(emptyList())
    private val archetypeMatchupsFlow = MutableStateFlow<List<ArchetypeMatchupData>>(emptyList())
    private val winrateByModeFlow = MutableStateFlow<List<ModeWinrate>>(emptyList())
    private val winrateByPlayerCountFlow = MutableStateFlow<List<PlayerCountWinrate>>(emptyList())
    private val allProposalsFlow = MutableStateFlow<List<TradeProposal>>(emptyList())
    private val sessionStateFlow = MutableStateFlow<SessionState>(SessionState.Unauthenticated)

    private val getStatsCalls = mutableListOf<Triple<PreferredCurrency, MtgColor?, String?>>()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic(FirebaseCrashlytics::class)
        every { FirebaseCrashlytics.getInstance() } returns mockk(relaxed = true)

        getStatsCalls.clear()
        every { getStats(any(), any(), any()) } answers {
            val currency = firstArg<PreferredCurrency>()
            val color = secondArg<MtgColor?>()
            val setCode = thirdArg<String?>()
            getStatsCalls.add(Triple(currency, color, setCode))
            flowOf(sampleCollectionStats())
        }
        every { getSetCodes() } returns flowOf(emptyList())
        every { getSetCompletionCounts() } returns flowOf(emptyMap())
        coEvery { scryfallDataSource.getAllSets() } returns emptyList()

        every { userPreferencesDataStore.preferredCurrencyFlow } returns currencyFlow
        every { userPreferencesDataStore.lastPriceRefreshFlow } returns flowOf(null)
        coEvery { userPreferencesDataStore.setPreferredCurrency(any()) } answers {
            currencyFlow.value = firstArg()
        }

        every { gameSessionRepository.observeTotalGames() } returns totalGamesFlow
        every { gameSessionRepository.observeLocalWins() } returns localWinsFlow
        every { gameSessionRepository.observeAvgDurationMs() } returns avgDurationFlow
        every { gameSessionRepository.observeFavoriteMode() } returns favoriteModeFlow
        every { gameSessionRepository.observeMostFrequentElimination() } returns mostFrequentEliminationFlow
        every { gameSessionRepository.observePendingSurveyCount() } returns pendingSurveyFlow
        every { gameSessionRepository.observeLocalSessionHistory() } returns localSessionHistoryFlow
        every { gameSessionRepository.observeLocalDeckGameStats() } returns localDeckGameStatsFlow
        every { gameSessionRepository.observeArchetypeMatchups() } returns archetypeMatchupsFlow
        every { gameSessionRepository.observeWinrateByMode() } returns winrateByModeFlow
        every { gameSessionRepository.observeWinrateByPlayerCount() } returns winrateByPlayerCountFlow

        every { deckRepository.observeAllDecks() } returns flowOf(emptyList())

        every { authRepository.sessionState } returns sessionStateFlow

        coEvery { tradesRepository.refreshProposals(any()) } returns Result.success(Unit)
        every { tradesRepository.observeAllProposals() } returns allProposalsFlow
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(FirebaseCrashlytics::class)
    }

    private fun buildViewModel() = StatsViewModel(
        getStats = getStats,
        getSetCodes = getSetCodes,
        getSetCompletionCounts = getSetCompletionCounts,
        scryfallDataSource = scryfallDataSource,
        userPreferencesDataStore = userPreferencesDataStore,
        gameSessionRepository = gameSessionRepository,
        deckRepository = deckRepository,
        authRepository = authRepository,
        tradesRepository = tradesRepository,
        getTradeStats = getTradeStats,
    )

    // ── Fixtures ─────────────────────────────────────────────────────────────────

    private fun sampleCollectionStats() = CollectionStats(
        totalCards = 10,
        uniqueCards = 5,
        totalDecks = 1,
        totalValueUsd = 100.0,
        totalValueEur = 90.0,
        mostValuableCards = emptyList(),
        byColor = emptyMap(),
        byRarity = emptyMap(),
        byType = emptyMap(),
        cmcDistribution = emptyMap(),
        bySet = emptyMap(),
    )

    private fun magicSet(code: String, cardCount: Int = 100) = MagicSet(
        code = code,
        name = code.uppercase(),
        setType = SetType.EXPANSION,
        releasedAt = "2020-01-01",
        cardCount = cardCount,
        iconSvgUri = "",
    )

    private fun historyEntry(
        sessionId: Long,
        localIsWinner: Boolean,
        mode: String = "COMMANDER",
        localDeckId: String? = null,
    ) = SessionHistoryEntry(
        sessionId = sessionId,
        mode = mode,
        totalTurns = 5,
        durationMs = 1000L,
        playedAt = sessionId * 1000,
        winnerName = "Player",
        surveyStatus = "COMPLETED",
        localIsWinner = localIsWinner,
        localDeckId = localDeckId,
        localDeckName = null,
        opponentCount = 1,
    )

    private fun authUser(id: String, isAnonymous: Boolean = false) = AuthUser(
        id = id,
        email = "tester@example.com",
        nickname = "tester",
        gameTag = "#TEST01",
        avatarUrl = null,
        provider = "email",
        profileCompleted = true,
        isAnonymous = isAnonymous,
    )

    private fun tradeProposal(
        id: String = "p1",
        status: TradeStatus,
        proposerId: String = "user-1",
        receiverId: String = "user-2",
    ) = TradeProposal(
        id = id,
        status = status,
        proposerId = proposerId,
        receiverId = receiverId,
        parentProposalId = null,
        rootProposalId = id,
        proposalVersion = 1,
        includesReviewCollectionFromProposer = false,
        includesReviewCollectionFromReceiver = false,
        proposerMarkedCompletedAt = null,
        receiverMarkedCompletedAt = null,
        cancellationReason = null,
        items = emptyList(),
        createdAt = 0L,
        updatedAt = 0L,
    )

    private fun sampleTradeStats() = TradeStats(
        completedTradesCount = 3,
        cardsSent = 5,
        cardsReceived = 4,
        netValueDelta = 12.5,
        currency = PreferredCurrency.USD,
        topPartner = TradePartnerSummary(userId = "user-2", displayName = "Friendo", completedTradesCount = 3),
    )

    // ── Game-stats combine happy path ───────────────────────────────────────────

    @Test
    fun `given game sessions when observing game stats then aggregates totals winrate and history`() = runTest {
        totalGamesFlow.value = 4
        localWinsFlow.value = 3
        avgDurationFlow.value = 1200.0
        favoriteModeFlow.value = GameModeCount("COMMANDER", 3)
        mostFrequentEliminationFlow.value = EliminationStats("LIFE", 2)
        pendingSurveyFlow.value = 1
        every { deckRepository.observeAllDecks() } returns flowOf(listOf(Deck(id = "deck-1", name = "My Deck")))
        localSessionHistoryFlow.value = listOf(
            historyEntry(sessionId = 4, localIsWinner = true, localDeckId = "deck-1"),
            historyEntry(sessionId = 3, localIsWinner = false),
            historyEntry(sessionId = 2, localIsWinner = true),
            historyEntry(sessionId = 1, localIsWinner = true),
        )

        val vm = buildViewModel()
        advanceUntilIdle()
        // WS5a: the Games tab's 12-flow combine is gated to StatsTab.GAMES via flatMapLatest —
        // it never populates state.gameStats while the default COLLECTION tab is active.
        vm.onTabSelected(StatsTab.GAMES)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.hasGameStats)
        assertEquals(4, state.gameStats?.totalGames)
        assertEquals(3, state.gameStats?.wins)
        assertEquals(0.75f, state.gameStats?.winrate)
        assertEquals(1200L, state.gameStats?.avgDurationMs)
        assertEquals("COMMANDER", state.gameStats?.favoriteMode)
        assertEquals("LIFE", state.gameStats?.mostFrequentLoss)
        assertEquals(1, state.gameStats?.pendingSurveys)
        assertEquals(4, state.sessionHistory.size)
        assertEquals("My Deck", state.sessionHistory.first { it.sessionId == 4L }.deckName)
    }

    // ── Tab-scoped subscription (Backend & Performance Optimization plan, WS5, 2026-07-28) ──

    @Test
    fun `given game session data available when the default COLLECTION tab is active then gameStats stays null (the GAMES combine is not subscribed)`() =
        runTest {
            totalGamesFlow.value = 4
            localWinsFlow.value = 3
            avgDurationFlow.value = 1200.0
            favoriteModeFlow.value = GameModeCount("COMMANDER", 3)
            mostFrequentEliminationFlow.value = EliminationStats("LIFE", 2)
            pendingSurveyFlow.value = 1
            localSessionHistoryFlow.value = listOf(historyEntry(sessionId = 1, localIsWinner = true))

            val vm = buildViewModel()
            advanceUntilIdle()

            // The default tab is COLLECTION -- the 12-flow GAMES combine is flatMapLatest-gated off
            // StatsTab and must never populate state.gameStats until GAMES is explicitly selected,
            // even though every underlying game-session flow already has real data available.
            assertEquals(StatsTab.COLLECTION, vm.uiState.value.selectedTab)
            assertEquals(null, vm.uiState.value.gameStats)
        }

    @Test
    fun `given the GAMES tab is left and re-entered when the underlying session data changed meanwhile then gameStats reflects the fresh data, not a stale value`() =
        runTest {
            totalGamesFlow.value = 4
            localWinsFlow.value = 3
            localSessionHistoryFlow.value = listOf(historyEntry(sessionId = 1, localIsWinner = true))

            val vm = buildViewModel()
            advanceUntilIdle()
            vm.onTabSelected(StatsTab.GAMES)
            advanceUntilIdle()
            assertEquals(4, vm.uiState.value.gameStats?.totalGames)

            // Navigate away -- flatMapLatest cancels/unsubscribes the 12-flow combine while off-tab
            // (the WS5 fix this test protects). The underlying data changes WHILE unsubscribed.
            vm.onTabSelected(StatsTab.COLLECTION)
            advanceUntilIdle()
            totalGamesFlow.value = 10
            localWinsFlow.value = 7
            advanceUntilIdle()

            // Re-entering GAMES must re-subscribe and recompute from the CURRENT data, proving the
            // gate is a real subscribe/unsubscribe toggle rather than a one-time snapshot.
            vm.onTabSelected(StatsTab.GAMES)
            advanceUntilIdle()

            assertEquals(10, vm.uiState.value.gameStats?.totalGames)
            assertEquals(7, vm.uiState.value.gameStats?.wins)
        }

    @Test
    fun `given no sessions when observing game stats then hasGameStats stays false and winrate is zero`() = runTest {
        val vm = buildViewModel()
        advanceUntilIdle()
        // WS5a: gameStats is only populated once the GAMES tab is active.
        vm.onTabSelected(StatsTab.GAMES)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.hasGameStats)
        assertEquals(0f, state.gameStats?.winrate)
    }

    // ── Filter re-subscription ──────────────────────────────────────────────────

    @Test
    fun `given color set and currency changes when observing collection stats then the pipeline resubscribes with new args`() = runTest {
        val vm = buildViewModel()
        advanceUntilIdle()
        assertEquals(1, getStatsCalls.size)
        assertEquals(Triple(PreferredCurrency.USD, null, null), getStatsCalls.last())

        vm.onColorSelected(MtgColor.R)
        advanceUntilIdle()
        assertEquals(2, getStatsCalls.size)
        assertEquals(MtgColor.R, getStatsCalls.last().second)

        vm.onSetSelected(magicSet("war"))
        advanceUntilIdle()
        assertEquals(3, getStatsCalls.size)
        assertEquals("war", getStatsCalls.last().third)

        currencyFlow.value = PreferredCurrency.EUR
        advanceUntilIdle()
        assertEquals(4, getStatsCalls.size)
        assertEquals(PreferredCurrency.EUR, getStatsCalls.last().first)
    }

    @Test
    fun `given the same color selected twice when toggling then the filter clears back to null`() = runTest {
        val vm = buildViewModel()
        advanceUntilIdle()

        vm.onColorSelected(MtgColor.U)
        advanceUntilIdle()
        assertEquals(MtgColor.U, vm.uiState.value.selectedColor)

        vm.onColorSelected(MtgColor.U)
        advanceUntilIdle()
        assertNull(vm.uiState.value.selectedColor)
        assertNull(getStatsCalls.last().second)
    }

    // ── observeAvailableSets() failure path ─────────────────────────────────────

    @Test
    fun `given scryfall getAllSets throws when observing available sets then it degrades to an empty list without crashing`() = runTest {
        coEvery { scryfallDataSource.getAllSets() } throws RuntimeException("network down")
        every { getSetCodes() } returns flowOf(listOf("war"))
        every { getSetCompletionCounts() } returns flowOf(mapOf("war" to 10))

        val vm = buildViewModel()
        advanceUntilIdle()

        // No crash reached this point; the failure degrades to empty collections instead.
        assertTrue(vm.uiState.value.availableSets.isEmpty())
        assertTrue(vm.uiState.value.setCompletions.isEmpty())
    }

    // ── Streaks ──────────────────────────────────────────────────────────────────

    @Test
    fun `given no session history when computing streaks then both streaks are zero`() = runTest {
        localSessionHistoryFlow.value = emptyList()

        val vm = buildViewModel()
        advanceUntilIdle()
        vm.onTabSelected(StatsTab.GAMES)
        advanceUntilIdle()

        assertEquals(0, vm.uiState.value.gameStats?.currentStreak)
        assertEquals(0, vm.uiState.value.gameStats?.bestStreak)
    }

    @Test
    fun `given an unbroken win streak when computing streaks then current equals best`() = runTest {
        // history is DESC (most-recent first): three consecutive wins.
        localSessionHistoryFlow.value = listOf(
            historyEntry(sessionId = 5, localIsWinner = true),
            historyEntry(sessionId = 4, localIsWinner = true),
            historyEntry(sessionId = 3, localIsWinner = true),
        )

        val vm = buildViewModel()
        advanceUntilIdle()
        vm.onTabSelected(StatsTab.GAMES)
        advanceUntilIdle()

        assertEquals(3, vm.uiState.value.gameStats?.currentStreak)
        assertEquals(3, vm.uiState.value.gameStats?.bestStreak)
    }

    @Test
    fun `given the most recent game is a loss when computing streaks then current resets but best keeps the earlier run`() = runTest {
        // DESC order: win(5), LOSS(4), win(3), win(2) -- most recent is a loss.
        localSessionHistoryFlow.value = listOf(
            historyEntry(sessionId = 5, localIsWinner = true),
            historyEntry(sessionId = 4, localIsWinner = false),
            historyEntry(sessionId = 3, localIsWinner = true),
            historyEntry(sessionId = 2, localIsWinner = true),
        )

        val vm = buildViewModel()
        advanceUntilIdle()
        vm.onTabSelected(StatsTab.GAMES)
        advanceUntilIdle()

        assertEquals(1, vm.uiState.value.gameStats?.currentStreak) // only the front-most win before the loss
        assertEquals(2, vm.uiState.value.gameStats?.bestStreak)    // the earlier 3/2 run is longer
    }

    // ── Win-rate by mode (ties) ──────────────────────────────────────────────────

    @Test
    fun `given tied games played across modes when computing mode winrate then the original DAO order is preserved`() = runTest {
        // Both modes have totalGames = 5 -- sortedByDescending is stable, so the tie preserves
        // the order the DAO already emitted (ORDER BY totalGames DESC in the query itself).
        winrateByModeFlow.value = listOf(
            ModeWinrate("STANDARD", totalGames = 5, wins = 2),
            ModeWinrate("COMMANDER", totalGames = 5, wins = 4),
        )

        val vm = buildViewModel()
        advanceUntilIdle()
        vm.onTabSelected(StatsTab.GAMES)
        advanceUntilIdle()

        val items = vm.uiState.value.modeWinrates
        assertEquals(listOf("STANDARD", "COMMANDER"), items.map { it.mode })
        assertEquals(0.4f, items.first { it.mode == "STANDARD" }.winrate)
        assertEquals(0.8f, items.first { it.mode == "COMMANDER" }.winrate)
    }

    // ── Player-count bucketing ───────────────────────────────────────────────────

    @Test
    fun `given exact player counts when bucketing 2 3 4plus then only populated buckets appear and 4 or more merge`() = runTest {
        winrateByPlayerCountFlow.value = listOf(
            PlayerCountWinrate(playerCount = 2, totalGames = 10, wins = 6),
            PlayerCountWinrate(playerCount = 4, totalGames = 3, wins = 1),
            PlayerCountWinrate(playerCount = 5, totalGames = 2, wins = 2),
        )

        val vm = buildViewModel()
        advanceUntilIdle()
        vm.onTabSelected(StatsTab.GAMES)
        advanceUntilIdle()

        val items = vm.uiState.value.playerCountWinrates
        // No bucket 3 -- it never had any recorded games, so it stays hidden (never a synthetic zero row).
        assertEquals(listOf(2, 4), items.map { it.playerCount })
        val bucket4 = items.first { it.playerCount == 4 }
        assertEquals(5, bucket4.totalGames) // 3 (playerCount=4) + 2 (playerCount=5) merged
        assertEquals(3, bucket4.wins)       // 1 + 2 merged
        assertTrue(bucket4.isFourPlus)
    }

    // ── Recent form ──────────────────────────────────────────────────────────────

    @Test
    fun `given more than 10 games when computing recent form then only the last 10 are shown chronologically`() = runTest {
        // history DESC (most-recent first): session ids 15 down to 1.
        localSessionHistoryFlow.value = (15 downTo 1).map { id ->
            historyEntry(sessionId = id.toLong(), localIsWinner = id % 2 == 0)
        }

        val vm = buildViewModel()
        advanceUntilIdle()
        vm.onTabSelected(StatsTab.GAMES)
        advanceUntilIdle()

        val recent = vm.uiState.value.recentForm
        assertEquals(10, recent.size)
        // take(10) of the DESC list keeps ids 15..6, then reversed() -> chronological 6..15.
        assertEquals((6L..15L).toList(), recent.map { it.sessionId })
        assertEquals(15L, recent.last().sessionId) // most-recent game rendered LAST
    }

    // ── mostFrequentLoss KPI passthrough ─────────────────────────────────────────

    @Test
    fun `given no elimination data when computing game stats then mostFrequentLoss is null`() = runTest {
        mostFrequentEliminationFlow.value = null

        val vm = buildViewModel()
        advanceUntilIdle()
        vm.onTabSelected(StatsTab.GAMES)
        advanceUntilIdle()

        assertNull(vm.uiState.value.gameStats?.mostFrequentLoss)
    }

    @Test
    fun `given a dominant elimination reason when computing game stats then mostFrequentLoss passes through unchanged`() = runTest {
        mostFrequentEliminationFlow.value = EliminationStats("COMMANDER_DAMAGE", 7)

        val vm = buildViewModel()
        advanceUntilIdle()
        vm.onTabSelected(StatsTab.GAMES)
        advanceUntilIdle()

        assertEquals("COMMANDER_DAMAGE", vm.uiState.value.gameStats?.mostFrequentLoss)
    }

    // ── Delete session ───────────────────────────────────────────────────────────

    @Test
    fun `given the Room delete succeeds when deleting a session then the success toast is surfaced only afterward`() = runTest {
        coEvery { gameSessionRepository.deleteSession(42L) } returns true
        val vm = buildViewModel()
        advanceUntilIdle()
        assertNull(vm.uiState.value.deleteSessionSuccess)

        vm.deleteSession(42L)
        advanceUntilIdle()

        assertEquals(true, vm.uiState.value.deleteSessionSuccess)
        coVerify(exactly = 1) { gameSessionRepository.deleteSession(42L) }

        vm.clearDeleteSessionMessage()
        assertNull(vm.uiState.value.deleteSessionSuccess)
    }

    @Test
    fun `given the Room delete fails when deleting a session then an error toast is surfaced`() = runTest {
        coEvery { gameSessionRepository.deleteSession(99L) } returns false
        val vm = buildViewModel()
        advanceUntilIdle()

        vm.deleteSession(99L)
        advanceUntilIdle()

        assertEquals(false, vm.uiState.value.deleteSessionSuccess)
    }

    // ── TRADES tab: two-tier lazy fetch ──────────────────────────────────────────

    @Test
    fun `given a completed proposal when observing trade tab visibility then hasTradeStats is true without fetching the expensive stats`() = runTest {
        sessionStateFlow.value = SessionState.Authenticated(authUser("user-1"))
        allProposalsFlow.value = listOf(tradeProposal(status = TradeStatus.COMPLETED))

        val vm = buildViewModel()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.hasTradeStats)
        assertEquals(TradeStatsUiState.Idle, vm.uiState.value.tradeStats)
        coVerify(exactly = 0) { getTradeStats(any(), any()) }
        coVerify(exactly = 1) { tradesRepository.refreshProposals("user-1") }
    }

    @Test
    fun `given no completed proposals when observing trade tab visibility then hasTradeStats is false`() = runTest {
        sessionStateFlow.value = SessionState.Authenticated(authUser("user-1"))
        allProposalsFlow.value = listOf(tradeProposal(status = TradeStatus.PROPOSED))

        val vm = buildViewModel()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.hasTradeStats)
    }

    @Test
    fun `given an anonymous session when observing trade tab visibility then it never refreshes proposals`() = runTest {
        sessionStateFlow.value = SessionState.Authenticated(authUser("guest-1", isAnonymous = true))

        val vm = buildViewModel()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.hasTradeStats)
        coVerify(exactly = 0) { tradesRepository.refreshProposals(any()) }
    }

    @Test
    fun `given the trades tab activated for the first time when selecting it then loadTradeStats fires exactly once`() = runTest {
        sessionStateFlow.value = SessionState.Authenticated(authUser("user-1"))
        coEvery { getTradeStats("user-1", PreferredCurrency.USD) } returns Result.success(sampleTradeStats())

        val vm = buildViewModel()
        advanceUntilIdle()
        coVerify(exactly = 0) { getTradeStats(any(), any()) }

        vm.onTabSelected(StatsTab.TRADES)
        advanceUntilIdle()

        coVerify(exactly = 1) { getTradeStats("user-1", PreferredCurrency.USD) }
        assertTrue(vm.uiState.value.tradeStats is TradeStatsUiState.Content)
    }

    @Test
    fun `given a trade stats fetch in flight when not yet resolved then the state is Loading`() = runTest {
        sessionStateFlow.value = SessionState.Authenticated(authUser("user-1"))
        val gate = CompletableDeferred<Result<TradeStats>>()
        coEvery { getTradeStats(any(), any()) } coAnswers { gate.await() }

        val vm = buildViewModel()
        advanceUntilIdle()

        vm.onTabSelected(StatsTab.TRADES)
        runCurrent()

        assertEquals(TradeStatsUiState.Loading, vm.uiState.value.tradeStats)

        gate.complete(Result.success(sampleTradeStats()))
        advanceUntilIdle()
        assertTrue(vm.uiState.value.tradeStats is TradeStatsUiState.Content)
    }

    @Test
    fun `given the trade stats fetch fails when selecting the trades tab then the state is Error and retry re-fetches`() = runTest {
        sessionStateFlow.value = SessionState.Authenticated(authUser("user-1"))
        coEvery { getTradeStats(any(), any()) } returnsMany listOf(
            Result.failure(RuntimeException("boom")),
            Result.success(sampleTradeStats()),
        )

        val vm = buildViewModel()
        advanceUntilIdle()

        vm.onTabSelected(StatsTab.TRADES)
        advanceUntilIdle()
        assertEquals(TradeStatsUiState.Error, vm.uiState.value.tradeStats)

        vm.retryTradeStats()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.tradeStats is TradeStatsUiState.Content)
    }

    @Test
    fun `given an unauthenticated session when selecting the trades tab then the state is Error synchronously`() = runTest {
        sessionStateFlow.value = SessionState.Unauthenticated

        val vm = buildViewModel()
        advanceUntilIdle()

        vm.onTabSelected(StatsTab.TRADES)

        assertEquals(TradeStatsUiState.Error, vm.uiState.value.tradeStats)
        coVerify(exactly = 0) { getTradeStats(any(), any()) }
    }

    @Test
    fun `given zero completed trades when trade stats resolve then the state is Content with zero counts`() = runTest {
        sessionStateFlow.value = SessionState.Authenticated(authUser("user-1"))
        val empty = TradeStats(0, 0, 0, 0.0, PreferredCurrency.USD, null)
        coEvery { getTradeStats(any(), any()) } returns Result.success(empty)

        val vm = buildViewModel()
        advanceUntilIdle()

        vm.onTabSelected(StatsTab.TRADES)
        advanceUntilIdle()

        val state = vm.uiState.value.tradeStats
        assertTrue(state is TradeStatsUiState.Content)
        assertEquals(0, (state as TradeStatsUiState.Content).stats.completedTradesCount)
    }

    @Test
    fun `given trade stats already loaded when the preferred currency changes then it re-fetches for the active tab`() = runTest {
        sessionStateFlow.value = SessionState.Authenticated(authUser("user-1"))
        coEvery { getTradeStats(any(), any()) } returns Result.success(sampleTradeStats())

        val vm = buildViewModel()
        advanceUntilIdle()
        vm.onTabSelected(StatsTab.TRADES)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.tradeStats is TradeStatsUiState.Content)

        currencyFlow.value = PreferredCurrency.EUR
        advanceUntilIdle()

        coVerify(exactly = 1) { getTradeStats("user-1", PreferredCurrency.EUR) }
    }
}
