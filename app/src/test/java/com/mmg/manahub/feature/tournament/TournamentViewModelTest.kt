package com.mmg.manahub.feature.tournament

import androidx.lifecycle.SavedStateHandle
import com.mmg.manahub.core.data.local.entity.TournamentEntity
import com.mmg.manahub.core.data.local.entity.TournamentMatchEntity
import com.mmg.manahub.core.data.local.entity.TournamentPlayerEntity
import com.mmg.manahub.core.data.local.entity.projection.TournamentStanding
import com.mmg.manahub.core.domain.repository.TournamentRepository
import com.mmg.manahub.core.ui.theme.PlayerTheme
import com.mmg.manahub.feature.game.model.GameMode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [TournamentViewModel].
 *
 * Tests cover:
 * - buildPlayerConfigsForMatch: correct player names, order, and appUser flag
 * - getGameMode: COMMANDER format → GameMode.COMMANDER, anything else → GameMode.STANDARD
 * - recordMatchResultManual: delegates to repository.finishMatch with sessionId=null
 * - recordMatchResult: calls calculateStandings and, if finished, finishTournament
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TournamentViewModelTest {

    // ── Dispatcher ────────────────────────────────────────────────────────────

    private val testDispatcher = StandardTestDispatcher()

    // ── Mocks ─────────────────────────────────────────────────────────────────

    private val repository = mockk<TournamentRepository>(relaxed = true)

    // ── Fixture helpers ───────────────────────────────────────────────────────

    private val defaultTheme = PlayerTheme.ALL[0]

    private fun buildTournamentEntity(
        id:     Long   = 1L,
        name:   String = "Friday Night Magic",
        format: String = "STANDARD",
        structure: String = "ROUND_ROBIN",
        status: String = "ACTIVE",
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
        id:           Long   = 1L,
        tournamentId: Long   = 1L,
        playerIds:    String = "[1,2]",
        status:       String = "PENDING",
        round:        Int    = 1,
        scheduledOrder: Int  = 0,
    ) = TournamentMatchEntity(
        id             = id,
        tournamentId   = tournamentId,
        round          = round,
        playerIds      = playerIds,
        status         = status,
        scheduledOrder = scheduledOrder,
    )

    private fun buildStanding(
        player: TournamentPlayerEntity,
        wins:   Int = 0,
        losses: Int = 0,
    ) = TournamentStanding(
        player        = player,
        wins          = wins,
        losses        = losses,
        draws         = 0,
        points        = wins * 3,
        lifeTotal     = 0,
        position      = 1,
        matchesPlayed = wins + losses,
    )

    /**
     * Creates a ViewModel with controlled state. The ViewModel's init block calls
     * loadTournament(), which relies on combine() of three Flows from the repository.
     * We wire up those flows before creating the ViewModel.
     */
    private fun buildViewModel(
        tournamentId: Long              = 1L,
        tournament:   TournamentEntity? = buildTournamentEntity(),
        matches:      List<TournamentMatchEntity> = emptyList(),
        players:      List<TournamentPlayerEntity> = emptyList(),
        standings:    List<TournamentStanding> = emptyList(),
    ): TournamentViewModel {
        val handle = SavedStateHandle(mapOf("tournamentId" to tournamentId))

        coEvery { repository.observeTournament(tournamentId) } returns flowOf(tournament)
        coEvery { repository.observeMatches(tournamentId) }    returns flowOf(matches)
        coEvery { repository.observePlayers(tournamentId) }    returns flowOf(players)
        coEvery { repository.calculateStandings(tournamentId) } returns standings

        return TournamentViewModel(repository, handle)
    }

    // ── Setup / Teardown ──────────────────────────────────────────────────────

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { repository.finishMatch(any(), any(), any(), any()) } returns Unit
        coEvery { repository.isFinished(any()) } returns false
        coEvery { repository.finishTournament(any()) } returns Unit
        coEvery { repository.startMatch(any()) } returns Unit
        coEvery { repository.calculateStandings(any()) } returns emptyList()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 1 — buildPlayerConfigsForMatch
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given match with 2 players when buildPlayerConfigsForMatch then returns 2 configs`() = runTest {
        // Arrange
        val p1 = buildPlayer(id = 10L, name = "Alice", seed = 0)
        val p2 = buildPlayer(id = 20L, name = "Bob",   seed = 1)
        val match = buildMatch(id = 100L, playerIds = "[10,20]", status = "ACTIVE")

        val vm = buildViewModel(matches = listOf(match), players = listOf(p1, p2))
        advanceUntilIdle()

        // Act
        val (ids, configs) = vm.buildPlayerConfigsForMatch(100L)

        // Assert
        assertEquals(2, configs.size)
        assertEquals(2, ids.size)
    }

    @Test
    fun `given match with 2 players when buildPlayerConfigsForMatch then first config is app user`() = runTest {
        // Arrange
        val p1 = buildPlayer(id = 10L, name = "Alice")
        val p2 = buildPlayer(id = 20L, name = "Bob")
        val match = buildMatch(id = 100L, playerIds = "[10,20]")

        val vm = buildViewModel(matches = listOf(match), players = listOf(p1, p2))
        advanceUntilIdle()

        // Act
        val (_, configs) = vm.buildPlayerConfigsForMatch(100L)

        // Assert
        assertTrue("First config must be isAppUser=true",   configs[0].isAppUser)
        assertFalse("Second config must be isAppUser=false", configs[1].isAppUser)
    }

    @Test
    fun `given match with known players when buildPlayerConfigsForMatch then player names are correct`() = runTest {
        // Arrange
        val p1 = buildPlayer(id = 10L, name = "Alice")
        val p2 = buildPlayer(id = 20L, name = "Bob")
        val match = buildMatch(id = 100L, playerIds = "[10,20]")

        val vm = buildViewModel(matches = listOf(match), players = listOf(p1, p2))
        advanceUntilIdle()

        // Act
        val (_, configs) = vm.buildPlayerConfigsForMatch(100L)

        // Assert: order matches playerIds string — Alice first, Bob second
        assertEquals("Alice", configs[0].name)
        assertEquals("Bob",   configs[1].name)
    }

    @Test
    fun `given match with playerIds in reverse order when buildPlayerConfigsForMatch then configs follow playerIds order`() = runTest {
        // Arrange — [20,10] means Bob appears before Alice in the config list
        val p1 = buildPlayer(id = 10L, name = "Alice")
        val p2 = buildPlayer(id = 20L, name = "Bob")
        val match = buildMatch(id = 100L, playerIds = "[20,10]")

        val vm = buildViewModel(matches = listOf(match), players = listOf(p1, p2))
        advanceUntilIdle()

        // Act
        val (_, configs) = vm.buildPlayerConfigsForMatch(100L)

        // Assert: Bob is first because 20 appears first in playerIds
        assertEquals("Bob",   configs[0].name)
        assertEquals("Alice", configs[1].name)
    }

    @Test
    fun `given matchId not found when buildPlayerConfigsForMatch then returns empty pair`() = runTest {
        // Arrange
        val vm = buildViewModel(matches = emptyList(), players = emptyList())
        advanceUntilIdle()

        // Act
        val (ids, configs) = vm.buildPlayerConfigsForMatch(999L)

        // Assert
        assertTrue(ids.isEmpty())
        assertTrue(configs.isEmpty())
    }

    @Test
    fun `given player not found in state when buildPlayerConfigsForMatch then uses fallback name`() = runTest {
        // Arrange — match references player 99L but that player is not in state
        val match = buildMatch(id = 100L, playerIds = "[99]")

        val vm = buildViewModel(matches = listOf(match), players = emptyList())
        advanceUntilIdle()

        // Act
        val (_, configs) = vm.buildPlayerConfigsForMatch(100L)

        // Assert: fallback to "Player 1"
        assertEquals("Player 1", configs[0].name)
    }

    @Test
    fun `given match when buildPlayerConfigsForMatch then tournamentPlayerIds matches playerIds`() = runTest {
        // Arrange
        val p1 = buildPlayer(id = 10L, name = "Alice")
        val p2 = buildPlayer(id = 20L, name = "Bob")
        val match = buildMatch(id = 100L, playerIds = "[10,20]")

        val vm = buildViewModel(matches = listOf(match), players = listOf(p1, p2))
        advanceUntilIdle()

        // Act
        val (tournamentPlayerIds, _) = vm.buildPlayerConfigsForMatch(100L)

        // Assert
        assertEquals(listOf(10L, 20L), tournamentPlayerIds)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 2 — getGameMode
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given COMMANDER format when getGameMode then returns GameMode COMMANDER`() = runTest {
        // Arrange
        val tournament = buildTournamentEntity(format = "COMMANDER")
        val vm = buildViewModel(tournament = tournament)
        advanceUntilIdle()

        // Act
        val mode = vm.getGameMode()

        // Assert
        assertEquals(GameMode.COMMANDER, mode)
    }

    @Test
    fun `given STANDARD format when getGameMode then returns GameMode STANDARD`() = runTest {
        // Arrange
        val tournament = buildTournamentEntity(format = "STANDARD")
        val vm = buildViewModel(tournament = tournament)
        advanceUntilIdle()

        // Act
        val mode = vm.getGameMode()

        // Assert
        assertEquals(GameMode.STANDARD, mode)
    }

    @Test
    fun `given lowercase commander format when getGameMode then returns COMMANDER`() = runTest {
        // Arrange — format stored as lowercase in some cases
        val tournament = buildTournamentEntity(format = "commander")
        val vm = buildViewModel(tournament = tournament)
        advanceUntilIdle()

        // Act
        val mode = vm.getGameMode()

        // Assert: uppercase() in getGameMode() handles this
        assertEquals(GameMode.COMMANDER, mode)
    }

    @Test
    fun `given null tournament when getGameMode then returns STANDARD as default`() = runTest {
        // Arrange — no tournament loaded
        val vm = buildViewModel(tournament = null)
        advanceUntilIdle()

        // Act
        val mode = vm.getGameMode()

        // Assert: else branch → STANDARD
        assertEquals(GameMode.STANDARD, mode)
    }

    @Test
    fun `given unknown format when getGameMode then returns STANDARD as default`() = runTest {
        // Arrange
        val tournament = buildTournamentEntity(format = "DRAFT")
        val vm = buildViewModel(tournament = tournament)
        advanceUntilIdle()

        // Act
        val mode = vm.getGameMode()

        // Assert: else branch → STANDARD
        assertEquals(GameMode.STANDARD, mode)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 3 — recordMatchResultManual
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given matchId and winnerId when recordMatchResultManual then finishMatch is called with sessionId=null`() = runTest {
        // Arrange
        val vm = buildViewModel()
        advanceUntilIdle()

        // Act
        vm.recordMatchResultManual(matchId = 100L, winnerId = 10L)
        advanceUntilIdle()

        // Assert: sessionId must be null for manual recording
        coVerify(exactly = 1) { repository.finishMatch(100L, 10L, null, emptyMap()) }
    }

    @Test
    fun `given matchId when recordMatchResultManual then finishMatch is called with empty lifeTotals`() = runTest {
        // Arrange
        val vm = buildViewModel()
        advanceUntilIdle()

        // Act
        vm.recordMatchResultManual(50L, 99L)
        advanceUntilIdle()

        // Assert: manual recording has no life totals
        coVerify { repository.finishMatch(50L, 99L, null, emptyMap()) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 4 — recordMatchResult
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given match result when recordMatchResult then calculateStandings is called`() = runTest {
        // Arrange
        val vm = buildViewModel()
        advanceUntilIdle()

        // Act
        vm.recordMatchResult(100L, 10L, 42L, mapOf(10L to 15, 20L to 0))
        advanceUntilIdle()

        // Assert: standings refresh is always triggered after finishing a match
        // calculateStandings is called once during init (by loadTournament) + once after recordMatchResult
        coVerify(atLeast = 1) { repository.calculateStandings(1L) }
    }

    @Test
    fun `given all matches finished when recordMatchResult then finishTournament is called`() = runTest {
        // Arrange — isFinished returns true after this match
        coEvery { repository.isFinished(1L) } returns true
        val vm = buildViewModel()
        advanceUntilIdle()

        // Act
        vm.recordMatchResult(100L, 10L, null, emptyMap())
        advanceUntilIdle()

        // Assert
        coVerify(exactly = 1) { repository.finishTournament(1L) }
    }

    @Test
    fun `given pending matches remain when recordMatchResult then finishTournament is NOT called`() = runTest {
        // Arrange — still matches to play
        coEvery { repository.isFinished(1L) } returns false
        val vm = buildViewModel()
        advanceUntilIdle()

        // Act
        vm.recordMatchResult(100L, 10L, null, emptyMap())
        advanceUntilIdle()

        // Assert
        coVerify(exactly = 0) { repository.finishTournament(any()) }
    }

    @Test
    fun `given all matches finished when recordMatchResult then isFinished state is true`() = runTest {
        // Arrange
        coEvery { repository.isFinished(1L) } returns true
        val vm = buildViewModel()
        advanceUntilIdle()

        // Act
        vm.recordMatchResult(100L, 10L, null, emptyMap())
        advanceUntilIdle()

        // Assert
        assertTrue(vm.uiState.value.isFinished)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 5 — loadTournament UiState
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given tournament loaded when ViewModel initialises then isLoading becomes false`() = runTest {
        // Arrange
        val vm = buildViewModel(tournament = buildTournamentEntity())
        advanceUntilIdle()

        // Assert
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun `given pending matches when loadTournament then nextMatch is first PENDING match`() = runTest {
        // Arrange
        val pending = buildMatch(id = 1L, status = "PENDING",  scheduledOrder = 0)
        val finished = buildMatch(id = 2L, status = "FINISHED", scheduledOrder = 1)
        val vm = buildViewModel(matches = listOf(pending, finished))
        advanceUntilIdle()

        // Assert
        assertEquals(1L, vm.uiState.value.nextMatch?.id)
    }

    @Test
    fun `given all matches finished when loadTournament then isFinished state is true`() = runTest {
        // Arrange
        val finished = buildMatch(id = 1L, status = "FINISHED")
        val vm = buildViewModel(matches = listOf(finished))
        advanceUntilIdle()

        // Assert
        assertTrue(vm.uiState.value.isFinished)
    }

    @Test
    fun `given no matches when loadTournament then isFinished state is false`() = runTest {
        // Arrange — isFinished = matches.isNotEmpty() && all finished
        val vm = buildViewModel(matches = emptyList())
        advanceUntilIdle()

        // Assert: no matches → not finished
        assertFalse(vm.uiState.value.isFinished)
    }

    @Test
    fun `given players loaded when loadTournament then players are in UiState`() = runTest {
        // Arrange
        val players = listOf(buildPlayer(1L, name = "Alice"), buildPlayer(2L, name = "Bob"))
        val vm = buildViewModel(players = players)
        advanceUntilIdle()

        // Assert
        assertEquals(2, vm.uiState.value.players.size)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 6 — startNextMatch / startMatch
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given pending match when startNextMatch then repository startMatch is called`() = runTest {
        // Arrange
        val pending = buildMatch(id = 55L, status = "PENDING")
        val vm = buildViewModel(matches = listOf(pending))
        advanceUntilIdle()

        var navigatedMatchId: Long? = null

        // Act
        vm.startNextMatch { navigatedMatchId = it }
        advanceUntilIdle()

        // Assert
        coVerify(exactly = 1) { repository.startMatch(55L) }
        assertEquals(55L, navigatedMatchId)
    }

    @Test
    fun `given no pending match when startNextMatch then repository startMatch is NOT called`() = runTest {
        // Arrange — no nextMatch in state
        val vm = buildViewModel(matches = emptyList())
        advanceUntilIdle()

        // Act
        vm.startNextMatch { }
        advanceUntilIdle()

        // Assert
        coVerify(exactly = 0) { repository.startMatch(any()) }
    }

    @Test
    fun `given matchId when startMatch then repository startMatch is called with correct id`() = runTest {
        // Arrange
        val vm = buildViewModel()
        advanceUntilIdle()

        var navigated: Long? = null

        // Act
        vm.startMatch(77L) { navigated = it }
        advanceUntilIdle()

        // Assert
        coVerify(exactly = 1) { repository.startMatch(77L) }
        assertEquals(77L, navigated)
    }
}
