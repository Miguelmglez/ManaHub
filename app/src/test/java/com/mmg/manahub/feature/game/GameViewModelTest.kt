package com.mmg.manahub.feature.game

import androidx.lifecycle.SavedStateHandle
import com.mmg.manahub.core.domain.repository.GameSessionRepository
import com.mmg.manahub.core.domain.repository.TournamentRepository
import com.mmg.manahub.core.ui.theme.PlayerTheme
import com.mmg.manahub.feature.game.model.CounterType
import com.mmg.manahub.feature.game.model.GameMode
import com.mmg.manahub.feature.game.model.GamePhase
import com.mmg.manahub.feature.game.model.Player
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
 * Unit tests for [GameViewModel].
 *
 * Tests cover life management, defeat/win detection, turn order, commander damage,
 * tournament result recording, and the companion object pure-logic helpers.
 *
 * Note: GameViewModel uses viewModelScope.launch in init{} (save game session observer).
 * Tests that trigger gameResult must call advanceUntilIdle() so the coroutine runs.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModelTest {

    // ── Dispatcher ────────────────────────────────────────────────────────────

    private val testDispatcher = StandardTestDispatcher()

    // ── Mocks ─────────────────────────────────────────────────────────────────

    private val gameSessionRepo  = mockk<GameSessionRepository>(relaxed = true)
    private val tournamentRepo   = mockk<TournamentRepository>(relaxed = true)

    // ── Helpers ───────────────────────────────────────────────────────────────

    private val defaultTheme = PlayerTheme.ALL[0]

    private fun buildViewModel(
        mode:        String = GameMode.STANDARD.name,
        playerCount: Int    = 2,
    ): GameViewModel {
        val handle = SavedStateHandle(mapOf("mode" to mode, "playerCount" to playerCount))
        return GameViewModel(handle, gameSessionRepo, tournamentRepo)
    }

    private fun buildPlayer(
        id:               Int  = 0,
        life:             Int  = 20,
        defeated:         Boolean = false,
        pendingDefeat:    Boolean = false,
        poison:           Int  = 0,
        commanderDamage:  Map<Int, Int> = emptyMap(),
        isAppUser:        Boolean = false,
    ) = Player(
        id              = id,
        name            = "Player ${id + 1}",
        life            = life,
        defeated        = defeated,
        pendingDefeat   = pendingDefeat,
        poison          = poison,
        commanderDamage = commanderDamage,
        isAppUser       = isAppUser,
        theme           = defaultTheme,
    )

    // ── Setup / Teardown ──────────────────────────────────────────────────────

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { gameSessionRepo.saveGameSession(any()) } returns 1L
        coEvery { tournamentRepo.finishMatch(any(), any(), any(), any()) } returns Unit
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 1 — buildInitialState (companion object pure logic)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given STANDARD mode with 2 players when buildInitialState then starting life is 20`() {
        // Arrange + Act
        val state = GameViewModel.buildInitialState(GameMode.STANDARD, 2)

        // Assert
        assertTrue(state.players.all { it.life == 20 })
    }

    @Test
    fun `given COMMANDER mode when buildInitialState then starting life is 40`() {
        // Arrange + Act
        val state = GameViewModel.buildInitialState(GameMode.COMMANDER, 4)

        // Assert
        assertTrue(state.players.all { it.life == 40 })
    }

    @Test
    fun `given playerCount=2 when buildInitialState then 2 players are created`() {
        // Arrange + Act
        val state = GameViewModel.buildInitialState(GameMode.STANDARD, 2)

        // Assert
        assertEquals(2, state.players.size)
    }

    @Test
    fun `given playerCount=6 when buildInitialState then 6 players are created`() {
        // Arrange + Act
        val state = GameViewModel.buildInitialState(GameMode.STANDARD, 6)

        // Assert
        assertEquals(6, state.players.size)
    }

    @Test
    fun `given playerCount below 2 when buildInitialState then clamps to 2`() {
        // Arrange + Act — below minimum
        val state = GameViewModel.buildInitialState(GameMode.STANDARD, 1)

        // Assert
        assertEquals(2, state.players.size)
    }

    @Test
    fun `given playerCount above 6 when buildInitialState then clamps to 6`() {
        // Arrange + Act — above maximum
        val state = GameViewModel.buildInitialState(GameMode.STANDARD, 10)

        // Assert
        assertEquals(6, state.players.size)
    }

    @Test
    fun `given initial state then first player is marked as app user`() {
        // Arrange + Act
        val state = GameViewModel.buildInitialState(GameMode.STANDARD, 3)

        // Assert
        assertTrue(state.players[0].isAppUser)
        assertFalse(state.players[1].isAppUser)
        assertFalse(state.players[2].isAppUser)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 2 — shouldEliminate (companion object pure logic)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given player with 0 life when shouldEliminate then returns true`() {
        val player = buildPlayer(life = 0)
        assertTrue(GameViewModel.shouldEliminate(player, GameMode.STANDARD))
    }

    @Test
    fun `given player with negative life when shouldEliminate then returns true`() {
        val player = buildPlayer(life = -5)
        assertTrue(GameViewModel.shouldEliminate(player, GameMode.STANDARD))
    }

    @Test
    fun `given player with 1 life when shouldEliminate then returns false`() {
        val player = buildPlayer(life = 1)
        assertFalse(GameViewModel.shouldEliminate(player, GameMode.STANDARD))
    }

    @Test
    fun `given player with 10 poison counters when shouldEliminate then returns true`() {
        val player = buildPlayer(poison = 10)
        assertTrue(GameViewModel.shouldEliminate(player, GameMode.STANDARD))
    }

    @Test
    fun `given player with 9 poison counters when shouldEliminate then returns false`() {
        val player = buildPlayer(poison = 9)
        assertFalse(GameViewModel.shouldEliminate(player, GameMode.STANDARD))
    }

    @Test
    fun `given COMMANDER mode and 21 commander damage when shouldEliminate then returns true`() {
        val player = buildPlayer(commanderDamage = mapOf(1 to 21))
        assertTrue(GameViewModel.shouldEliminate(player, GameMode.COMMANDER))
    }

    @Test
    fun `given COMMANDER mode and 20 commander damage when shouldEliminate then returns false`() {
        val player = buildPlayer(commanderDamage = mapOf(1 to 20))
        assertFalse(GameViewModel.shouldEliminate(player, GameMode.COMMANDER))
    }

    @Test
    fun `given STANDARD mode and 21 commander damage when shouldEliminate then returns false`() {
        // Commander damage rule only applies in COMMANDER mode
        val player = buildPlayer(commanderDamage = mapOf(1 to 21))
        assertFalse(GameViewModel.shouldEliminate(player, GameMode.STANDARD))
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 3 — changeLife
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given player at 20 life when changeLife delta=-5 then player life is 15`() = runTest {
        // Arrange
        val vm = buildViewModel(playerCount = 2)
        val playerId = vm.uiState.value.players[0].id

        // Act
        vm.changeLife(playerId, -5)

        // Assert
        val updatedPlayer = vm.uiState.value.players.find { it.id == playerId }
        assertEquals(15, updatedPlayer?.life)
    }

    @Test
    fun `given player at 20 life when changeLife delta=+3 then player life is 23`() = runTest {
        // Arrange
        val vm = buildViewModel(playerCount = 2)
        val playerId = vm.uiState.value.players[0].id

        // Act
        vm.changeLife(playerId, +3)

        // Assert
        val updatedPlayer = vm.uiState.value.players.find { it.id == playerId }
        assertEquals(23, updatedPlayer?.life)
    }

    @Test
    fun `given player at 1 life when changeLife to 0 then pendingDefeat is set but player is not defeated`() = runTest {
        // Arrange
        val vm = buildViewModel(mode = GameMode.STANDARD.name, playerCount = 2)
        val playerId = vm.uiState.value.players[1].id

        // bring to 1 first
        vm.changeLife(playerId, -19)

        // Act — bring to 0
        vm.changeLife(playerId, -1)

        // Assert
        val player = vm.uiState.value.players.find { it.id == playerId }!!
        assertTrue("pendingDefeat should be set at 0 life",   player.pendingDefeat)
        assertFalse("player should NOT be defeated yet",       player.defeated)
    }

    @Test
    fun `given player at 0 life when changeLife delta is not applied then other player is unaffected`() = runTest {
        // Arrange
        val vm = buildViewModel(playerCount = 2)
        val p0Id = vm.uiState.value.players[0].id
        val p1Id = vm.uiState.value.players[1].id

        // Act
        vm.changeLife(p0Id, -5)

        // Assert: player 1 unaffected
        val p1 = vm.uiState.value.players.find { it.id == p1Id }
        assertEquals(20, p1?.life)
    }

    @Test
    fun `given life change when changeLife then lifeDelta is accumulated`() = runTest {
        // Arrange
        val vm = buildViewModel(playerCount = 2)
        val playerId = vm.uiState.value.players[0].id

        // Act
        vm.changeLife(playerId, -3)
        vm.changeLife(playerId, -2)

        // Assert: total delta = -5
        val delta = vm.uiState.value.lifeDeltas[playerId]
        assertEquals(-5, delta)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 4 — confirmDefeat / revokeDefeat
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given player pendingDefeat when confirmDefeat then player is marked defeated`() = runTest {
        // Arrange
        val vm = buildViewModel(playerCount = 3)
        val playerId = vm.uiState.value.players[1].id
        // Bring to 0 life to set pendingDefeat
        vm.changeLife(playerId, -20)

        // Act
        vm.confirmDefeat(playerId)

        // Assert
        val player = vm.uiState.value.players.find { it.id == playerId }!!
        assertTrue(player.defeated)
        assertFalse(player.pendingDefeat)
    }

    @Test
    fun `given player with pendingDefeat when revokeDefeat then player continues playing`() = runTest {
        // Arrange
        val vm = buildViewModel(playerCount = 2)
        val playerId = vm.uiState.value.players[0].id
        vm.changeLife(playerId, -20)

        // Act
        vm.revokeDefeat(playerId)

        // Assert
        val player = vm.uiState.value.players.find { it.id == playerId }!!
        assertFalse(player.pendingDefeat)
        assertFalse(player.defeated)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 5 — checkWinner / gameResult
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given 2 players and 1 confirms defeat when checkWinner then gameResult is set`() = runTest {
        // Arrange
        val vm = buildViewModel(playerCount = 2)
        val p0Id = vm.uiState.value.players[0].id
        val p1Id = vm.uiState.value.players[1].id

        vm.changeLife(p1Id, -20)

        // Act
        vm.confirmDefeat(p1Id)
        advanceUntilIdle()

        // Assert: with 1 alive player, gameResult is set
        assertNotNull(vm.uiState.value.gameResult)
        assertEquals(p0Id, vm.uiState.value.winner?.id)
    }

    @Test
    fun `given 3 players when only 1 alive after 2 defeats then gameResult is set`() = runTest {
        // Arrange
        val vm = buildViewModel(playerCount = 3)
        val players = vm.uiState.value.players

        vm.changeLife(players[1].id, -20)
        vm.confirmDefeat(players[1].id)

        vm.changeLife(players[2].id, -20)
        vm.confirmDefeat(players[2].id)
        advanceUntilIdle()

        // Assert
        assertNotNull(vm.uiState.value.gameResult)
        assertEquals(players[0].id, vm.uiState.value.winner?.id)
    }

    @Test
    fun `given 3 players when only 2 defeated when confirmDefeat then no winner yet`() = runTest {
        // Arrange — 3 players: only 1 defeated so far
        val vm = buildViewModel(playerCount = 3)
        val players = vm.uiState.value.players

        vm.changeLife(players[1].id, -20)

        // Act
        vm.confirmDefeat(players[1].id)
        advanceUntilIdle()

        // Assert: 2 alive → no winner yet
        assertNull(vm.uiState.value.gameResult)
    }

    @Test
    fun `given gameResult is set when saveGameSession then lastSessionId is populated`() = runTest {
        // Arrange
        coEvery { gameSessionRepo.saveGameSession(any()) } returns 77L
        val vm = buildViewModel(playerCount = 2)
        val p1Id = vm.uiState.value.players[1].id

        vm.changeLife(p1Id, -20)

        // Act
        vm.confirmDefeat(p1Id)
        advanceUntilIdle()

        // Assert
        assertEquals(77L, vm.uiState.value.lastSessionId)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 6 — Commander damage
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given COMMANDER mode and 21 commander damage when changeCommanderDamage then pendingDefeat is true`() = runTest {
        // Arrange
        val vm = buildViewModel(mode = GameMode.COMMANDER.name, playerCount = 2)
        val p0Id = vm.uiState.value.players[0].id
        val p1Id = vm.uiState.value.players[1].id

        // Act — player 0 deals 21 commander damage to player 1
        vm.changeCommanderDamage(targetId = p1Id, sourceId = p0Id, delta = 21)

        // Assert
        val target = vm.uiState.value.players.find { it.id == p1Id }!!
        assertTrue("21 commander damage in COMMANDER mode should set pendingDefeat",
            target.pendingDefeat)
    }

    @Test
    fun `given STANDARD mode and 21 commander damage when changeCommanderDamage then pendingDefeat is false`() = runTest {
        // Arrange
        val vm = buildViewModel(mode = GameMode.STANDARD.name, playerCount = 2)
        val p0Id = vm.uiState.value.players[0].id
        val p1Id = vm.uiState.value.players[1].id

        // Act
        vm.changeCommanderDamage(targetId = p1Id, sourceId = p0Id, delta = 21)

        // Assert: commander damage rule does NOT apply in STANDARD
        val target = vm.uiState.value.players.find { it.id == p1Id }!!
        assertFalse("Commander damage should NOT set pendingDefeat in STANDARD mode",
            target.pendingDefeat)
    }

    @Test
    fun `given COMMANDER mode and 20 commander damage when changeCommanderDamage then pendingDefeat is false`() = runTest {
        // Arrange — boundary: 20 is not enough
        val vm = buildViewModel(mode = GameMode.COMMANDER.name, playerCount = 2)
        val p0Id = vm.uiState.value.players[0].id
        val p1Id = vm.uiState.value.players[1].id

        // Act
        vm.changeCommanderDamage(targetId = p1Id, sourceId = p0Id, delta = 20)

        // Assert
        val target = vm.uiState.value.players.find { it.id == p1Id }!!
        assertFalse(target.pendingDefeat)
    }

    @Test
    fun `given damage delta is negative when changeCommanderDamage then damage does not go below 0`() = runTest {
        // Arrange
        val vm = buildViewModel(mode = GameMode.COMMANDER.name, playerCount = 2)
        val p0Id = vm.uiState.value.players[0].id
        val p1Id = vm.uiState.value.players[1].id
        vm.changeCommanderDamage(targetId = p1Id, sourceId = p0Id, delta = 5)

        // Act — reduce by more than current
        vm.changeCommanderDamage(targetId = p1Id, sourceId = p0Id, delta = -10)

        // Assert: coerceAtLeast(0)
        val target = vm.uiState.value.players.find { it.id == p1Id }!!
        assertEquals(0, target.commanderDamage[p0Id])
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 7 — nextTurn
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given 2 players when nextTurn then activePlayerId changes to next player`() = runTest {
        // Arrange
        val vm = buildViewModel(playerCount = 2)
        val p0Id = vm.uiState.value.players[0].id
        val p1Id = vm.uiState.value.players[1].id
        assertEquals(p0Id, vm.uiState.value.activePlayerId)

        // Act
        vm.nextTurn()

        // Assert
        assertEquals(p1Id, vm.uiState.value.activePlayerId)
    }

    @Test
    fun `given 2 players when nextTurn called twice then turnNumber increments once (full round)`() = runTest {
        // Arrange
        val vm = buildViewModel(playerCount = 2)
        assertEquals(1, vm.uiState.value.turnNumber)

        // Act — first nextTurn: P0 → P1 (not a new round yet)
        vm.nextTurn()
        assertEquals(1, vm.uiState.value.turnNumber)

        // Act — second nextTurn: P1 → P0 (back to first alive player → new round)
        vm.nextTurn()

        // Assert
        assertEquals(2, vm.uiState.value.turnNumber)
    }

    @Test
    fun `given 3 players when nextTurn after full round then turnNumber increments`() = runTest {
        // Arrange
        val vm = buildViewModel(playerCount = 3)
        val firstAliveId = vm.uiState.value.players[0].id

        // Act — complete a full round: P0 → P1 → P2 → P0
        vm.nextTurn()  // P0 → P1
        vm.nextTurn()  // P1 → P2
        vm.nextTurn()  // P2 → P0 (first alive)

        // Assert
        assertEquals(2, vm.uiState.value.turnNumber)
        assertEquals(firstAliveId, vm.uiState.value.activePlayerId)
    }

    @Test
    fun `given nextTurn when called then phase resets to UNTAP`() = runTest {
        // Arrange
        val vm = buildViewModel(playerCount = 2)
        vm.advancePhase()  // move to DRAW or next phase

        // Act
        vm.nextTurn()

        // Assert
        assertEquals(GamePhase.UNTAP, vm.uiState.value.currentPhase)
    }

    @Test
    fun `given nextTurn when called then hasPlayedLand is cleared`() = runTest {
        // Arrange
        val vm = buildViewModel(playerCount = 2)
        val p0Id = vm.uiState.value.players[0].id
        vm.toggleLandPlayed(p0Id)
        assertTrue(vm.uiState.value.hasPlayedLand.contains(p0Id))

        // Act
        vm.nextTurn()

        // Assert
        assertTrue(vm.uiState.value.hasPlayedLand.isEmpty())
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 8 — reorderTurnOrder
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given turnNumber=1 when reorderTurnOrder then activePlayerId is set to first in new order`() = runTest {
        // Arrange
        val vm = buildViewModel(playerCount = 3)
        val players = vm.uiState.value.players
        assertEquals(1, vm.uiState.value.turnNumber)

        // Act — put player 2 first
        val newOrder = listOf(players[2].id, players[0].id, players[1].id)
        vm.reorderTurnOrder(newOrder)

        // Assert: turnNumber==1 → activePlayerId follows new first player
        assertEquals(players[2].id, vm.uiState.value.activePlayerId)
    }

    @Test
    fun `given turnNumber greater than 1 when reorderTurnOrder then activePlayerId does not change`() = runTest {
        // Arrange — advance past turn 1
        val vm = buildViewModel(playerCount = 2)
        vm.nextTurn()
        vm.nextTurn()   // full round → turnNumber = 2

        val activeBeforeReorder = vm.uiState.value.activePlayerId
        val players = vm.uiState.value.players

        // Act
        vm.reorderTurnOrder(listOf(players[1].id, players[0].id))

        // Assert: activePlayerId should NOT change after turn 1
        assertEquals(activeBeforeReorder, vm.uiState.value.activePlayerId)
    }

    @Test
    fun `given reorderTurnOrder when called then players list is reordered`() = runTest {
        // Arrange
        val vm = buildViewModel(playerCount = 3)
        val players = vm.uiState.value.players
        val newOrder = listOf(players[2].id, players[1].id, players[0].id)

        // Act
        vm.reorderTurnOrder(newOrder)

        // Assert
        assertEquals(players[2].id, vm.uiState.value.players[0].id)
        assertEquals(players[1].id, vm.uiState.value.players[1].id)
        assertEquals(players[0].id, vm.uiState.value.players[2].id)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 9 — Tournament result recording
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given no tournament context when game ends then tournamentRepo finishMatch is NOT called`() = runTest {
        // Arrange — standalone game (no activeTournamentMatchId)
        val vm = buildViewModel(playerCount = 2)
        val p1Id = vm.uiState.value.players[1].id

        vm.changeLife(p1Id, -20)

        // Act
        vm.confirmDefeat(p1Id)
        advanceUntilIdle()

        // Assert: no tournament context → finishMatch must never be called
        coVerify(exactly = 0) { tournamentRepo.finishMatch(any(), any(), any(), any()) }
    }

    @Test
    fun `given tournament context when game ends then tournamentRepo finishMatch IS called`() = runTest {
        // Arrange — init from tournament match
        val handle = SavedStateHandle(mapOf(
            "mode" to GameMode.STANDARD.name,
            "playerCount" to 2,
        ))
        val vm = GameViewModel(handle, gameSessionRepo, tournamentRepo)

        val configs = listOf(
            PlayerConfig(id = 0, name = "Alice", theme = defaultTheme, isAppUser = true),
            PlayerConfig(id = 1, name = "Bob",   theme = defaultTheme, isAppUser = false),
        )
        vm.initFromTournamentMatch(
            matchId             = 100L,
            tournamentId        = 5L,
            tournamentPlayerIds = listOf(10L, 20L),
            configs             = configs,
            mode                = GameMode.STANDARD,
        )

        val p1Id = vm.uiState.value.players[1].id
        vm.changeLife(p1Id, -20)

        // Act
        vm.confirmDefeat(p1Id)
        advanceUntilIdle()

        // Assert: tournament context present → finishMatch called once
        coVerify(exactly = 1) { tournamentRepo.finishMatch(any(), any(), any(), any()) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 10 — Counter management
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given poison counter when changeCounter then poison increments`() = runTest {
        // Arrange
        val vm = buildViewModel(playerCount = 2)
        val playerId = vm.uiState.value.players[0].id

        // Act
        vm.changeCounter(playerId, CounterType.POISON, 3)

        // Assert
        val player = vm.uiState.value.players.find { it.id == playerId }!!
        assertEquals(3, player.poison)
    }

    @Test
    fun `given 10 poison counters when changeCounter then pendingDefeat is true`() = runTest {
        // Arrange
        val vm = buildViewModel(playerCount = 2)
        val p1Id = vm.uiState.value.players[1].id

        // Act
        vm.changeCounter(p1Id, CounterType.POISON, 10)

        // Assert
        val player = vm.uiState.value.players.find { it.id == p1Id }!!
        assertTrue(player.pendingDefeat)
    }

    @Test
    fun `given poison counter when decremented below 0 then it stays at 0`() = runTest {
        // Arrange
        val vm = buildViewModel(playerCount = 2)
        val playerId = vm.uiState.value.players[0].id
        vm.changeCounter(playerId, CounterType.POISON, 2)

        // Act
        vm.changeCounter(playerId, CounterType.POISON, -5)

        // Assert: coerceAtLeast(0)
        val player = vm.uiState.value.players.find { it.id == playerId }!!
        assertEquals(0, player.poison)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 11 — renamePlayer
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given valid name when renamePlayer then player name is updated`() = runTest {
        // Arrange
        val vm = buildViewModel(playerCount = 2)
        val playerId = vm.uiState.value.players[0].id

        // Act
        vm.renamePlayer(playerId, "Gandalf")

        // Assert
        val player = vm.uiState.value.players.find { it.id == playerId }!!
        assertEquals("Gandalf", player.name)
    }

    @Test
    fun `given blank name when renamePlayer then player name is NOT changed`() = runTest {
        // Arrange
        val vm = buildViewModel(playerCount = 2)
        val playerId = vm.uiState.value.players[0].id
        val originalName = vm.uiState.value.players[0].name

        // Act
        vm.renamePlayer(playerId, "   ")

        // Assert
        val player = vm.uiState.value.players.find { it.id == playerId }!!
        assertEquals(originalName, player.name)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 12 — resetGame
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given game in progress when resetGame then turnNumber resets to 1`() = runTest {
        // Arrange
        val vm = buildViewModel(playerCount = 2)
        vm.nextTurn()
        vm.nextTurn()  // turnNumber = 2

        // Act
        vm.resetGame()

        // Assert
        assertEquals(1, vm.uiState.value.turnNumber)
    }

    @Test
    fun `given player life changed when resetGame then life resets to starting life`() = runTest {
        // Arrange
        val vm = buildViewModel(mode = GameMode.STANDARD.name, playerCount = 2)
        val playerId = vm.uiState.value.players[0].id
        vm.changeLife(playerId, -10)

        // Act
        vm.resetGame()

        // Assert
        val player = vm.uiState.value.players.find { it.id == playerId }!!
        assertEquals(20, player.life)
    }

    @Test
    fun `given player defeated when resetGame then no player is defeated`() = runTest {
        // Arrange
        val vm = buildViewModel(playerCount = 3)
        val p1Id = vm.uiState.value.players[1].id
        vm.changeLife(p1Id, -20)
        vm.confirmDefeat(p1Id)

        // Act
        vm.resetGame()

        // Assert
        assertTrue(vm.uiState.value.players.none { it.defeated })
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 13 — toggleLandPlayed
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given player has not played land when toggleLandPlayed then player is added to hasPlayedLand`() = runTest {
        // Arrange
        val vm = buildViewModel(playerCount = 2)
        val playerId = vm.uiState.value.players[0].id

        // Act
        vm.toggleLandPlayed(playerId)

        // Assert
        assertTrue(vm.uiState.value.hasPlayedLand.contains(playerId))
    }

    @Test
    fun `given player already in hasPlayedLand when toggleLandPlayed then player is removed`() = runTest {
        // Arrange
        val vm = buildViewModel(playerCount = 2)
        val playerId = vm.uiState.value.players[0].id
        vm.toggleLandPlayed(playerId)

        // Act — toggle off
        vm.toggleLandPlayed(playerId)

        // Assert
        assertFalse(vm.uiState.value.hasPlayedLand.contains(playerId))
    }
}
