package com.mmg.manahub.feature.tournament

import androidx.lifecycle.SavedStateHandle
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.core.data.local.entity.TournamentEntity
import com.mmg.manahub.core.data.local.entity.TournamentMatchEntity
import com.mmg.manahub.core.data.local.entity.TournamentPlayerEntity
import com.mmg.manahub.core.data.local.entity.projection.TournamentStanding
import com.mmg.manahub.feature.tournament.domain.repository.MatchResultOutcome
import com.mmg.manahub.feature.tournament.domain.repository.TournamentRepository
import com.mmg.manahub.feature.game.domain.model.GameMode
import com.mmg.manahub.feature.tournament.domain.usecase.CalculateStandingsUseCase
import com.mmg.manahub.feature.tournament.domain.usecase.RecordMatchResultUseCase
import com.mmg.manahub.feature.tournament.presentation.TournamentViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
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
 * Unit tests for [TournamentViewModel].
 *
 * NOTE (single-write-path refactor, audit C1/C2): the VM no longer owns round advancement or tournament
 * finishing. Both the game-played flow and the manual dialog route through [RecordMatchResultUseCase] →
 * [TournamentRepository.finishMatch], which advances rounds and finishes the tournament ATOMICALLY in
 * the repository and emits completion XP there. The VM only (a) recomputes standings and (b) reflects
 * the finished flag from the returned [MatchResultOutcome]. So these tests verify the VM calls the use
 * case and reflects the outcome — NOT that the VM calls finishTournament (it never does).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TournamentViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val repository                = mockk<TournamentRepository>(relaxed = true)
    private val calculateStandingsUseCase = mockk<CalculateStandingsUseCase>(relaxed = true)
    private val recordMatchResultUseCase  = mockk<RecordMatchResultUseCase>(relaxed = true)

    private fun buildTournamentEntity(
        id:        Long   = 1L,
        name:      String = "Friday Night Magic",
        format:    String = "STANDARD",
        structure: String = "ROUND_ROBIN",
        status:    String = "ACTIVE",
    ) = TournamentEntity(
        id                = id,
        name              = name,
        format            = format,
        structure         = structure,
        status            = status,
        matchesPerPairing = 1,
        isRandomPairings  = false,
    )

    private fun buildPlayer(
        id:           Long   = 1L,
        tournamentId: Long   = 1L,
        name:         String = "Player",
        seed:         Int    = 0,
    ) = TournamentPlayerEntity(
        id           = id,
        tournamentId = tournamentId,
        playerName   = name,
        playerColor  = "#FF0000",
        seed         = seed,
    )

    private fun buildMatch(
        id:             Long   = 1L,
        tournamentId:   Long   = 1L,
        playerIds:      String = "[1,2]",
        status:         String = "PENDING",
        round:          Int    = 1,
        scheduledOrder: Int    = 0,
    ) = TournamentMatchEntity(
        id             = id,
        tournamentId   = tournamentId,
        round          = round,
        playerIds      = playerIds,
        status         = status,
        scheduledOrder = scheduledOrder,
    )

    private fun buildViewModel(
        tournamentId: Long                        = 1L,
        tournament:   TournamentEntity?           = buildTournamentEntity(),
        matches:      List<TournamentMatchEntity>  = emptyList(),
        players:      List<TournamentPlayerEntity> = emptyList(),
        standings:    List<TournamentStanding>    = emptyList(),
    ): TournamentViewModel {
        val handle = SavedStateHandle(mapOf("tournamentId" to tournamentId))

        coEvery { repository.observeTournament(tournamentId) } returns flowOf(tournament)
        coEvery { repository.observeMatches(tournamentId) }    returns flowOf(matches)
        coEvery { repository.observePlayers(tournamentId) }    returns flowOf(players)
        coEvery { calculateStandingsUseCase(tournamentId) }    returns standings

        return TournamentViewModel(
            repository               = repository,
            calculateStandings       = calculateStandingsUseCase,
            recordMatchResultUseCase = recordMatchResultUseCase,
            savedStateHandle         = handle,
        )
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        // Prevent FirebaseCrashlytics.getInstance() from crashing in JVM unit tests
        mockkStatic(FirebaseCrashlytics::class)
        val crashlytics = mockk<FirebaseCrashlytics>(relaxed = true)
        every { FirebaseCrashlytics.getInstance() } returns crashlytics

        coEvery { repository.finishMatch(any(), any(), any(), any()) } returns MatchResultOutcome.RoundNotComplete
        coEvery { repository.isFinished(any()) } returns false
        coEvery { repository.finishTournament(any()) } returns Unit
        coEvery { repository.startMatch(any()) } returns Unit
        coEvery { repository.startTournament(any()) } returns Unit
        coEvery { repository.pauseTournament(any()) } returns Unit
        coEvery { repository.resetMatch(any()) } returns Unit
        coEvery { calculateStandingsUseCase(any()) } returns emptyList()
        coEvery { recordMatchResultUseCase.recordWin(any(), any(), any(), any()) } returns MatchResultOutcome.RoundNotComplete
        coEvery { recordMatchResultUseCase.recordDraw(any(), any(), any()) } returns MatchResultOutcome.RoundNotComplete
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(FirebaseCrashlytics::class)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 1 — buildPlayerConfigsForMatch
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given match with 2 players when buildPlayerConfigsForMatch then returns 2 configs`() = runTest {
        val p1 = buildPlayer(id = 10L, name = "Alice", seed = 0)
        val p2 = buildPlayer(id = 20L, name = "Bob",   seed = 1)
        val match = buildMatch(id = 100L, playerIds = "[10,20]", status = "ACTIVE")

        val vm = buildViewModel(matches = listOf(match), players = listOf(p1, p2))
        advanceUntilIdle()

        val (ids, configs) = vm.buildPlayerConfigsForMatch(100L)
        assertEquals(2, configs.size)
        assertEquals(2, ids.size)
    }

    @Test
    fun `given match with 2 players when buildPlayerConfigsForMatch then first config is app user`() = runTest {
        val p1 = buildPlayer(id = 10L, name = "Alice")
        val p2 = buildPlayer(id = 20L, name = "Bob")
        val match = buildMatch(id = 100L, playerIds = "[10,20]")

        val vm = buildViewModel(matches = listOf(match), players = listOf(p1, p2))
        advanceUntilIdle()

        val (_, configs) = vm.buildPlayerConfigsForMatch(100L)
        assertTrue("First config must be isAppUser=true",   configs[0].isAppUser)
        assertFalse("Second config must be isAppUser=false", configs[1].isAppUser)
    }

    @Test
    fun `given match with known players when buildPlayerConfigsForMatch then player names are correct`() = runTest {
        val p1 = buildPlayer(id = 10L, name = "Alice")
        val p2 = buildPlayer(id = 20L, name = "Bob")
        val match = buildMatch(id = 100L, playerIds = "[10,20]")

        val vm = buildViewModel(matches = listOf(match), players = listOf(p1, p2))
        advanceUntilIdle()

        val (_, configs) = vm.buildPlayerConfigsForMatch(100L)
        assertEquals("Alice", configs[0].name)
        assertEquals("Bob",   configs[1].name)
    }

    @Test
    fun `given match with playerIds in reverse order when buildPlayerConfigsForMatch then configs follow playerIds order`() = runTest {
        val p1 = buildPlayer(id = 10L, name = "Alice")
        val p2 = buildPlayer(id = 20L, name = "Bob")
        val match = buildMatch(id = 100L, playerIds = "[20,10]")

        val vm = buildViewModel(matches = listOf(match), players = listOf(p1, p2))
        advanceUntilIdle()

        val (_, configs) = vm.buildPlayerConfigsForMatch(100L)
        assertEquals("Bob",   configs[0].name)
        assertEquals("Alice", configs[1].name)
    }

    @Test
    fun `given matchId not found when buildPlayerConfigsForMatch then returns empty pair`() = runTest {
        val vm = buildViewModel(matches = emptyList(), players = emptyList())
        advanceUntilIdle()

        val (ids, configs) = vm.buildPlayerConfigsForMatch(999L)
        assertTrue(ids.isEmpty())
        assertTrue(configs.isEmpty())
    }

    @Test
    fun `given player not found in state when buildPlayerConfigsForMatch then uses fallback name`() = runTest {
        val match = buildMatch(id = 100L, playerIds = "[99]")
        val vm = buildViewModel(matches = listOf(match), players = emptyList())
        advanceUntilIdle()

        val (_, configs) = vm.buildPlayerConfigsForMatch(100L)
        assertEquals("Wizard 1", configs[0].name)
    }

    @Test
    fun `given match when buildPlayerConfigsForMatch then tournamentPlayerIds matches playerIds`() = runTest {
        val p1 = buildPlayer(id = 10L, name = "Alice")
        val p2 = buildPlayer(id = 20L, name = "Bob")
        val match = buildMatch(id = 100L, playerIds = "[10,20]")

        val vm = buildViewModel(matches = listOf(match), players = listOf(p1, p2))
        advanceUntilIdle()

        val (tournamentPlayerIds, _) = vm.buildPlayerConfigsForMatch(100L)
        assertEquals(listOf(10L, 20L), tournamentPlayerIds)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 2 — getGameMode
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given COMMANDER format when getGameMode then returns GameMode COMMANDER`() = runTest {
        val vm = buildViewModel(tournament = buildTournamentEntity(format = "COMMANDER"))
        advanceUntilIdle()
        assertEquals(GameMode.COMMANDER, vm.getGameMode())
    }

    @Test
    fun `given STANDARD format when getGameMode then returns GameMode STANDARD`() = runTest {
        val vm = buildViewModel(tournament = buildTournamentEntity(format = "STANDARD"))
        advanceUntilIdle()
        assertEquals(GameMode.STANDARD, vm.getGameMode())
    }

    @Test
    fun `given lowercase commander format when getGameMode then returns COMMANDER`() = runTest {
        val vm = buildViewModel(tournament = buildTournamentEntity(format = "commander"))
        advanceUntilIdle()
        assertEquals(GameMode.COMMANDER, vm.getGameMode())
    }

    @Test
    fun `given null tournament when getGameMode then returns STANDARD as default`() = runTest {
        val vm = buildViewModel(tournament = null)
        advanceUntilIdle()
        assertEquals(GameMode.STANDARD, vm.getGameMode())
    }

    @Test
    fun `given unknown format when getGameMode then returns STANDARD as default`() = runTest {
        val vm = buildViewModel(tournament = buildTournamentEntity(format = "DRAFT"))
        advanceUntilIdle()
        assertEquals(GameMode.STANDARD, vm.getGameMode())
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 3 — recordMatchResultManual / recordDrawManual (route through use case)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given matchId and winnerId when recordMatchResultManual then recordWin is called with sessionId=null`() = runTest {
        val vm = buildViewModel()
        advanceUntilIdle()

        vm.recordMatchResultManual(matchId = 100L, winnerId = 10L)
        advanceUntilIdle()

        coVerify(exactly = 1) { recordMatchResultUseCase.recordWin(100L, 10L, null, emptyMap()) }
    }

    @Test
    fun `given matchId when recordDrawManual then recordDraw is called with sessionId=null`() = runTest {
        val vm = buildViewModel()
        advanceUntilIdle()

        vm.recordDrawManual(matchId = 50L)
        advanceUntilIdle()

        coVerify(exactly = 1) { recordMatchResultUseCase.recordDraw(50L, null, emptyMap()) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 4 — recordMatchResult outcome handling (single write path)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given match result when recordMatchResult then calculateStandings is called`() = runTest {
        val vm = buildViewModel()
        advanceUntilIdle()

        vm.recordMatchResult(100L, 10L, 42L, mapOf(10L to 15, 20L to 0))
        advanceUntilIdle()

        coVerify(atLeast = 1) { calculateStandingsUseCase(1L) }
    }

    @Test
    fun `given TournamentFinished outcome when recordMatchResult then isFinished state is true`() = runTest {
        coEvery { recordMatchResultUseCase.recordWin(any(), any(), any(), any()) } returns MatchResultOutcome.TournamentFinished
        val vm = buildViewModel()
        advanceUntilIdle()

        vm.recordMatchResult(100L, 10L, null, emptyMap())
        advanceUntilIdle()

        assertTrue(vm.uiState.value.isFinished)
    }

    @Test
    fun `given RoundGenerated outcome when recordMatchResult then VM never calls finishTournament`() = runTest {
        // The repository owns finishing; the VM must NOT finish the tournament itself (audit C1/C2).
        coEvery { recordMatchResultUseCase.recordWin(any(), any(), any(), any()) } returns MatchResultOutcome.RoundGenerated
        val vm = buildViewModel()
        advanceUntilIdle()

        vm.recordMatchResult(100L, 10L, null, emptyMap())
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.finishTournament(any()) }
    }

    @Test
    fun `given NoOp outcome when recordMatchResult then VM never calls finishTournament`() = runTest {
        coEvery { recordMatchResultUseCase.recordWin(any(), any(), any(), any()) } returns MatchResultOutcome.NoOp
        val vm = buildViewModel()
        advanceUntilIdle()

        vm.recordMatchResult(100L, 10L, null, emptyMap())
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.finishTournament(any()) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 5 — loadTournament UiState + H4 auto-resume semantics
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given tournament loaded when ViewModel initialises then isLoading becomes false`() = runTest {
        val vm = buildViewModel(tournament = buildTournamentEntity())
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun `given pending matches when loadTournament then nextMatch is first PENDING match`() = runTest {
        val pending  = buildMatch(id = 1L, status = "PENDING",  scheduledOrder = 0)
        val finished = buildMatch(id = 2L, status = "FINISHED", scheduledOrder = 1)
        val vm = buildViewModel(matches = listOf(pending, finished))
        advanceUntilIdle()
        assertEquals(1L, vm.uiState.value.nextMatch?.id)
    }

    @Test
    fun `given active match when loadTournament then activeMatch is set`() = runTest {
        val active = buildMatch(id = 5L, status = "ACTIVE")
        val vm = buildViewModel(matches = listOf(active))
        advanceUntilIdle()
        assertNotNull(vm.uiState.value.activeMatch)
        assertEquals(5L, vm.uiState.value.activeMatch?.id)
    }

    @Test
    fun `given no active match when loadTournament then activeMatch is null`() = runTest {
        val pending = buildMatch(id = 1L, status = "PENDING")
        val vm = buildViewModel(matches = listOf(pending))
        advanceUntilIdle()
        assertNull(vm.uiState.value.activeMatch)
    }

    @Test
    fun `given all matches finished when loadTournament then isFinished state is true`() = runTest {
        val finished = buildMatch(id = 1L, status = "FINISHED")
        val vm = buildViewModel(matches = listOf(finished))
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isFinished)
    }

    @Test
    fun `given no matches when loadTournament then isFinished state is false`() = runTest {
        val vm = buildViewModel(matches = emptyList())
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isFinished)
    }

    @Test
    fun `given players loaded when loadTournament then players are in UiState`() = runTest {
        val players = listOf(buildPlayer(1L, name = "Alice"), buildPlayer(2L, name = "Bob"))
        val vm = buildViewModel(players = players)
        advanceUntilIdle()
        assertEquals(2, vm.uiState.value.players.size)
    }

    // ── H4: pause survives a screen reopen; SETUP auto-resumes ────────────────────

    @Test
    fun `given SETUP tournament when ViewModel initialises then it IS auto-resumed`() = runTest {
        val vm = buildViewModel(tournament = buildTournamentEntity(status = "SETUP"))
        advanceUntilIdle()
        coVerify(exactly = 1) { repository.startTournament(1L) }
    }

    @Test
    fun `given PAUSED tournament when ViewModel initialises then it is NOT auto-resumed`() = runTest {
        val vm = buildViewModel(tournament = buildTournamentEntity(status = "PAUSED"))
        advanceUntilIdle()
        coVerify(exactly = 0) { repository.startTournament(any()) }
        assertTrue(vm.uiState.value.isPaused)
    }

    @Test
    fun `given ACTIVE tournament when ViewModel initialises then it is NOT re-started`() = runTest {
        val vm = buildViewModel(tournament = buildTournamentEntity(status = "ACTIVE"))
        advanceUntilIdle()
        coVerify(exactly = 0) { repository.startTournament(any()) }
    }

    @Test
    fun `given PAUSED tournament when resumeTournament then it is explicitly started`() = runTest {
        val vm = buildViewModel(tournament = buildTournamentEntity(status = "PAUSED"))
        advanceUntilIdle()

        vm.resumeTournament()
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.startTournament(1L) }
        assertFalse(vm.uiState.value.isPaused)
    }

    @Test
    fun `given active tournament when pause then repository pauseTournament is called and state is paused`() = runTest {
        val vm = buildViewModel(tournament = buildTournamentEntity(status = "ACTIVE"))
        advanceUntilIdle()

        vm.pause()
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.pauseTournament(1L) }
        assertTrue(vm.uiState.value.isPaused)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 6 — startNextMatch / startMatch / resetMatch + M6 nav guard
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given pending match when startNextMatch then repository startMatch is called`() = runTest {
        val pending = buildMatch(id = 55L, status = "PENDING")
        val vm = buildViewModel(matches = listOf(pending))
        advanceUntilIdle()

        var navigatedMatchId: Long? = null
        vm.startNextMatch { navigatedMatchId = it }
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.startMatch(55L) }
        assertEquals(55L, navigatedMatchId)
    }

    @Test
    fun `given no pending match when startNextMatch then repository startMatch is NOT called`() = runTest {
        val vm = buildViewModel(matches = emptyList())
        advanceUntilIdle()

        vm.startNextMatch { }
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.startMatch(any()) }
    }

    @Test
    fun `given matchId when startMatch then repository startMatch is called with correct id`() = runTest {
        val vm = buildViewModel()
        advanceUntilIdle()

        var navigated: Long? = null
        vm.startMatch(77L) { navigated = it }
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.startMatch(77L) }
        assertEquals(77L, navigated)
    }

    @Test
    fun `given navigation already in flight when startMatch then second start is blocked by the nav guard`() = runTest {
        // M6: a single isNavigatingToGame guard covers startNext/start/resume. Once navigation is in
        // flight (guard set, not yet consumed), a second start must be a no-op.
        val vm = buildViewModel()
        advanceUntilIdle()

        vm.startMatch(77L) { }
        advanceUntilIdle()
        vm.startMatch(88L) { }   // blocked — guard still set (no onGameNavigationConsumed yet)
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.startMatch(77L) }
        coVerify(exactly = 0) { repository.startMatch(88L) }
    }

    @Test
    fun `given guard consumed when startMatch again then second start proceeds`() = runTest {
        val vm = buildViewModel()
        advanceUntilIdle()

        vm.startMatch(77L) { }
        advanceUntilIdle()
        vm.onGameNavigationConsumed()    // screen returned from the game
        vm.startMatch(88L) { }
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.startMatch(77L) }
        coVerify(exactly = 1) { repository.startMatch(88L) }
    }

    @Test
    fun `given active matchId when resetMatch then repository resetMatch is called`() = runTest {
        val vm = buildViewModel()
        advanceUntilIdle()

        vm.resetMatch(42L)
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.resetMatch(42L) }
    }
}
