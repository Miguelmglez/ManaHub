package com.mmg.manahub.feature.game.presentation

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.core.nearby.domain.repository.NearbySessionRepository
import com.mmg.manahub.core.online.domain.usecase.AdvancePhaseUseCase
import com.mmg.manahub.core.online.domain.usecase.ConfirmDefeatUseCase
import com.mmg.manahub.core.online.domain.usecase.LeaveSessionUseCase
import com.mmg.manahub.core.online.domain.usecase.NextTurnUseCase
import com.mmg.manahub.core.online.domain.usecase.ObserveSessionUseCase
import com.mmg.manahub.core.online.domain.usecase.RevokeDefeatUseCase
import com.mmg.manahub.core.online.domain.usecase.ToggleLandPlayedUseCase
import com.mmg.manahub.core.online.domain.usecase.UpdateCommanderDamageUseCase
import com.mmg.manahub.core.online.domain.usecase.UpdateCounterUseCase
import com.mmg.manahub.core.online.domain.usecase.UpdateLifeUseCase
import com.mmg.manahub.core.ui.theme.PlayerTheme
import com.mmg.manahub.core.util.AnalyticsHelper
import com.mmg.manahub.core.voice.domain.VoiceCommandRecognizer
import com.mmg.manahub.feature.game.domain.model.EliminationReason
import com.mmg.manahub.feature.game.domain.model.GameMode
import com.mmg.manahub.feature.game.domain.repository.GameSessionRepository
import com.mmg.manahub.feature.game.domain.usecase.EvaluatePlayerEliminationUseCase
import com.mmg.manahub.feature.tournament.domain.repository.TournamentRepository
import com.mmg.manahub.feature.tournament.domain.usecase.RecordMatchResultUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
 * Unit tests for [GameViewModel] game-lifecycle state.
 *
 * GROUP 1 — a new game never inherits a finished game's result or tournament context
 * GROUP 2 — turn order walks table order past defeated seats; a round completes only on wrap
 * GROUP 3 — eliminationReason derives from the defeated flag (CONCEDE for a manual concession)
 * GROUP 4 — finished / defeated seats reject further mutations
 * GROUP 5 — resetGame clears survivor + session bookkeeping; a networked context is torn down
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModelStateTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val gameSessionRepo              = mockk<GameSessionRepository>(relaxed = true)
    private val tournamentRepo               = mockk<TournamentRepository>(relaxed = true)
    private val analyticsHelper              = mockk<AnalyticsHelper>(relaxed = true)
    private val observeSessionUseCase        = mockk<ObserveSessionUseCase>(relaxed = true)
    private val updateLifeUseCase            = mockk<UpdateLifeUseCase>(relaxed = true)
    private val advancePhaseUseCase          = mockk<AdvancePhaseUseCase>(relaxed = true)
    private val nextTurnUseCase              = mockk<NextTurnUseCase>(relaxed = true)
    private val updateCounterUseCase         = mockk<UpdateCounterUseCase>(relaxed = true)
    private val updateCommanderDamageUseCase = mockk<UpdateCommanderDamageUseCase>(relaxed = true)
    private val confirmDefeatUseCase         = mockk<ConfirmDefeatUseCase>(relaxed = true)
    private val revokeDefeatUseCase          = mockk<RevokeDefeatUseCase>(relaxed = true)
    private val leaveSessionUseCase          = mockk<LeaveSessionUseCase>(relaxed = true)
    private val nearbyRepo                   = mockk<NearbySessionRepository>(relaxed = true)
    private val toggleLandPlayedUseCase      = mockk<ToggleLandPlayedUseCase>(relaxed = true)
    private val recordMatchResultUseCase     = mockk<RecordMatchResultUseCase>(relaxed = true)
    private val voiceCommandRecognizer       = mockk<VoiceCommandRecognizer>(relaxed = true)
    private val appContext                   = mockk<Context>(relaxed = true)

    private companion object {
        const val MATCH_ID = 42L
        const val TOURNAMENT_ID = 7L
        val TOURNAMENT_PLAYER_IDS = listOf(100L, 200L)
    }

    private fun buildViewModel(): GameViewModel {
        val handle = SavedStateHandle(mapOf("mode" to GameMode.STANDARD.name, "playerCount" to 2))
        return GameViewModel(
            savedStateHandle                 = handle,
            gameSessionRepo                  = gameSessionRepo,
            tournamentRepo                   = tournamentRepo,
            recordMatchResultUseCase         = recordMatchResultUseCase,
            analyticsHelper                  = analyticsHelper,
            observeSessionUseCase            = observeSessionUseCase,
            updateLifeUseCase                = updateLifeUseCase,
            advancePhaseUseCase              = advancePhaseUseCase,
            nextTurnUseCase                  = nextTurnUseCase,
            updateCounterUseCase             = updateCounterUseCase,
            updateCommanderDamageUseCase     = updateCommanderDamageUseCase,
            confirmDefeatUseCase             = confirmDefeatUseCase,
            revokeDefeatUseCase              = revokeDefeatUseCase,
            leaveSessionUseCase              = leaveSessionUseCase,
            nearbyRepo                       = nearbyRepo,
            toggleLandPlayedUseCase          = toggleLandPlayedUseCase,
            voiceCommandRecognizer           = voiceCommandRecognizer,
            evaluatePlayerEliminationUseCase = EvaluatePlayerEliminationUseCase(),
            appContext                       = appContext,
        )
    }

    private fun configs(count: Int, appUserIndex: Int? = 0) = (0 until count).map { i ->
        PlayerConfig(i, "Player ${i + 1}", PlayerTheme.ALL[i % PlayerTheme.ALL.size], isAppUser = i == appUserIndex)
    }

    private fun GameViewModel.startTournamentGame() = initFromTournamentMatch(
        matchId             = MATCH_ID,
        tournamentId        = TOURNAMENT_ID,
        tournamentPlayerIds = TOURNAMENT_PLAYER_IDS,
        configs             = configs(2, appUserIndex = null),
        mode                = GameMode.STANDARD,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic(FirebaseCrashlytics::class)
        every { FirebaseCrashlytics.getInstance() } returns mockk(relaxed = true)
        every { voiceCommandRecognizer.commands } returns emptyFlow()
        every { voiceCommandRecognizer.isListening } returns MutableStateFlow(false)
        coEvery { gameSessionRepo.saveGameSession(any()) } returns 1L
        every { nearbyRepo.observeMessages() } returns MutableSharedFlow()
        every { nearbyRepo.observeConnectionEvents() } returns MutableSharedFlow()
        every { observeSessionUseCase.invoke(any()) } returns emptyFlow()
        coEvery { observeSessionUseCase.getSnapshot(any(), any()) } returns Result.failure(IllegalStateException("offline"))
        every { appContext.getString(any()) } returns "Player"
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(FirebaseCrashlytics::class)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 1 — fresh state after a finished tournament game
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given finished tournament game when initFromConfigs then winner and tournament context are cleared`() = runTest {
        // Arrange — play a tournament match to its end
        val vm = buildViewModel()
        vm.startTournamentGame()
        vm.confirmDefeat(1)
        assertNotNull(vm.uiState.value.winner)
        assertEquals(MATCH_ID, vm.uiState.value.gameResult?.tournamentMatchId)
        coVerify(timeout = 3_000, exactly = 1) {
            recordMatchResultUseCase.recordWin(MATCH_ID, TOURNAMENT_PLAYER_IDS[0], any(), any())
        }

        // Act — start a casual game without going through finishGame()
        vm.initFromConfigs(configs(2), GameMode.COMMANDER)

        // Assert — nothing of the previous game survives
        val s = vm.uiState.value
        assertNull(s.winner)
        assertNull(s.gameResult)
        assertNull(s.lastSessionId)
        assertNull(s.activeTournamentId)
        assertNull(s.activeTournamentMatchId)
        assertTrue(s.tournamentPlayerIds.isEmpty())
        assertEquals(1, s.turnNumber)
        assertEquals(GameMode.COMMANDER, s.mode)
        assertTrue(s.isGameRunning)
        assertFalse(s.isOnlineSession)
        assertTrue(s.players.none { it.defeated })
    }

    @Test
    fun `given casual game after a tournament game when it finishes then the tournament match is not recorded again`() = runTest {
        // Arrange
        val vm = buildViewModel()
        vm.startTournamentGame()
        vm.confirmDefeat(1)
        coVerify(timeout = 3_000, exactly = 1) { recordMatchResultUseCase.recordWin(any(), any(), any(), any()) }
        vm.initFromConfigs(configs(2), GameMode.STANDARD)

        // Act — the casual game ends
        vm.confirmDefeat(0)

        // Assert — the snapshot carries no tournament context, so no second write can happen
        val result = vm.uiState.value.gameResult
        assertNotNull(result)
        assertNull(result?.tournamentMatchId)
        assertTrue(result?.tournamentPlayerIds.orEmpty().isEmpty())
        Thread.sleep(300) // the record path hops through Dispatchers.IO; give a stray call time to surface
        coVerify(exactly = 1) { recordMatchResultUseCase.recordWin(any(), any(), any(), any()) }
    }

    @Test
    fun `given finished tournament game when finishGame then tournament context is cleared`() = runTest {
        val vm = buildViewModel()
        vm.startTournamentGame()
        vm.confirmDefeat(1)

        vm.finishGame()

        val s = vm.uiState.value
        assertNull(s.winner)
        assertNull(s.activeTournamentMatchId)
        assertNull(s.activeTournamentId)
        assertTrue(s.tournamentPlayerIds.isEmpty())
        assertFalse(s.isGameRunning)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 2 — turn order
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given defeated active player when nextTurn then next seat in table order is active and no new round starts`() = runTest {
        // Arrange — 3 seats; seat 1 becomes active and then is eliminated
        val vm = buildViewModel()
        vm.initFromConfigs(configs(3), GameMode.STANDARD)
        vm.nextTurn()
        assertEquals(1, vm.uiState.value.activePlayerId)
        vm.confirmDefeat(1)
        assertEquals(1, vm.uiState.value.turnNumber)

        // Act
        vm.nextTurn()

        // Assert — seat 2 follows seat 1, still round 1
        assertEquals(2, vm.uiState.value.activePlayerId)
        assertEquals(1, vm.uiState.value.turnNumber)

        // Wrapping past the end of the table completes the round
        vm.nextTurn()
        assertEquals(0, vm.uiState.value.activePlayerId)
        assertEquals(2, vm.uiState.value.turnNumber)
    }

    @Test
    fun `given last seat defeated when nextTurn from the seat before it then the wrap to seat 0 counts as a new round`() = runTest {
        val vm = buildViewModel()
        vm.initFromConfigs(configs(3), GameMode.STANDARD)
        vm.confirmDefeat(2)
        vm.nextTurn()                       // 0 -> 1
        assertEquals(1, vm.uiState.value.turnNumber)

        vm.nextTurn()                       // 1 -> (2 skipped) -> 0, wrapped

        assertEquals(0, vm.uiState.value.activePlayerId)
        assertEquals(2, vm.uiState.value.turnNumber)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 3 — elimination reason
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given player concedes at full life when game ends then eliminationReason is CONCEDE`() = runTest {
        val vm = buildViewModel()
        vm.initFromConfigs(configs(2), GameMode.STANDARD)

        vm.confirmDefeat(1)

        val results = vm.uiState.value.gameResult!!.playerResults.associateBy { it.player.id }
        assertEquals(EliminationReason.CONCEDE, results.getValue(1).eliminationReason)
        assertNull(results.getValue(0).eliminationReason)
    }

    @Test
    fun `given player defeated at zero life when game ends then eliminationReason is LIFE`() = runTest {
        val vm = buildViewModel()
        vm.initFromConfigs(configs(2), GameMode.STANDARD)
        vm.changeLife(1, -20)
        assertTrue(vm.uiState.value.players[1].pendingDefeat)

        vm.confirmDefeat(1)

        val results = vm.uiState.value.gameResult!!.playerResults.associateBy { it.player.id }
        assertEquals(EliminationReason.LIFE, results.getValue(1).eliminationReason)
    }

    @Test
    fun `given survivor at negative life who wins then their eliminationReason is null`() = runTest {
        // Arrange — seat 0 drops to -5 but disputes the defeat and keeps playing
        val vm = buildViewModel()
        vm.initFromConfigs(configs(2), GameMode.STANDARD)
        vm.changeLife(0, -25)
        vm.revokeDefeat(0)
        assertTrue(vm.uiState.value.players[0].isSurviving)

        // Act — the opponent concedes
        vm.confirmDefeat(1)

        // Assert — a raw life total never marks a non-defeated seat as eliminated
        val results = vm.uiState.value.gameResult!!.playerResults.associateBy { it.player.id }
        assertNull(results.getValue(0).eliminationReason)
        assertEquals(EliminationReason.CONCEDE, results.getValue(1).eliminationReason)
    }

    @Test
    fun `given 21 commander damage in STANDARD mode when defeated then reason is CONCEDE not COMMANDER_DAMAGE`() = runTest {
        val vm = buildViewModel()
        vm.initFromConfigs(configs(2), GameMode.STANDARD)
        vm.changeCommanderDamage(targetId = 1, sourceId = 0, delta = 21)

        vm.confirmDefeat(1)

        val results = vm.uiState.value.gameResult!!.playerResults.associateBy { it.player.id }
        assertEquals(EliminationReason.CONCEDE, results.getValue(1).eliminationReason)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 4 — mutation guards
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given defeated player when life counters or land are changed then nothing changes`() = runTest {
        val vm = buildViewModel()
        vm.initFromConfigs(configs(3), GameMode.COMMANDER)
        vm.confirmDefeat(2)
        val before = vm.uiState.value.players[2]

        vm.changeLife(2, -3)
        vm.changeCounter(2, com.mmg.manahub.feature.game.domain.model.CounterType.POISON, 2)
        vm.changeCommanderDamage(targetId = 2, sourceId = 0, delta = 5)
        vm.toggleLandPlayed(2)

        assertEquals(before, vm.uiState.value.players[2])
        assertFalse(2 in vm.uiState.value.hasPlayedLand)
    }

    @Test
    fun `given finished game when any mutating action is invoked then state is frozen`() = runTest {
        val vm = buildViewModel()
        vm.initFromConfigs(configs(2), GameMode.STANDARD)
        vm.confirmDefeat(1)
        val frozen = vm.uiState.value

        vm.changeCounter(0, com.mmg.manahub.feature.game.domain.model.CounterType.ENERGY, 1)
        vm.changeCommanderDamage(0, 1, 3)
        vm.nextTurn()
        vm.advancePhase()
        vm.toggleLandPlayed(0)

        val after = vm.uiState.value
        assertEquals(frozen.players, after.players)
        assertEquals(frozen.turnNumber, after.turnNumber)
        assertEquals(frozen.currentPhase, after.currentPhase)
        assertEquals(frozen.activePlayerId, after.activePlayerId)
        assertEquals(frozen.hasPlayedLand, after.hasPlayedLand)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 4b — turn-order reorder and custom counters
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given a reorder list missing a player then that player is kept at the end`() {
        // mapNotNull silently DELETED any seat the caller omitted (e.g. a stale drag draft)
        val vm = buildViewModel()
        vm.initFromConfigs(configs(3), GameMode.STANDARD)

        vm.reorderTurnOrder(listOf(2, 0))

        assertEquals(listOf(2, 0, 1), vm.uiState.value.players.map { it.id })
    }

    @Test
    fun `given a full reorder list then the order is applied exactly`() {
        val vm = buildViewModel()
        vm.initFromConfigs(configs(3), GameMode.STANDARD)

        vm.reorderTurnOrder(listOf(1, 2, 0))

        assertEquals(listOf(1, 2, 0), vm.uiState.value.players.map { it.id })
    }

    @Test
    fun `given two rapid custom counters then their ids are distinct`() {
        // The id used to be System.currentTimeMillis(): a double tap produced duplicates that then
        // mutated together.
        val vm = buildViewModel()
        vm.initFromConfigs(configs(2), GameMode.COMMANDER)

        vm.addCustomCounter(0, "Treasure")
        vm.addCustomCounter(0, "Treasure")

        val ids = vm.uiState.value.players.first { it.id == 0 }.customCounters.map { it.id }
        assertEquals(2, ids.size)
        assertEquals(2, ids.toSet().size)
    }

    @Test
    fun `given two counters with the same name then changing one leaves the other untouched`() {
        val vm = buildViewModel()
        vm.initFromConfigs(configs(2), GameMode.COMMANDER)
        vm.addCustomCounter(0, "Treasure")
        vm.addCustomCounter(0, "Treasure")
        val counters = vm.uiState.value.players.first { it.id == 0 }.customCounters

        vm.changeCustomCounter(0, counters.first().id, delta = 3)

        val after = vm.uiState.value.players.first { it.id == 0 }.customCounters
        assertEquals(3, after.first { it.id == counters.first().id }.value)
        assertEquals(0, after.first { it.id == counters.last().id }.value)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 5 — resetGame / networked teardown
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given surviving player and saved session when resetGame then survivor flag and lastSessionId are cleared`() = runTest {
        val vm = buildViewModel()
        vm.initFromConfigs(configs(2), GameMode.STANDARD)
        vm.changeLife(0, -25)
        vm.revokeDefeat(0)
        vm.confirmDefeat(1)
        assertTrue(vm.uiState.value.players[0].isSurviving)

        vm.resetGame()

        val s = vm.uiState.value
        assertTrue(s.players.none { it.isSurviving || it.defeated || it.pendingDefeat })
        assertNull(s.lastSessionId)
        assertNull(s.winner)
        assertTrue(s.isGameRunning)
    }

    @Test
    fun `given online game when initFromConfigs then the session is disconnected and left`() = runTest {
        val vm = buildViewModel()
        vm.initFromOnlineSession("session-1", mySlotIndex = 0, configs = configs(2), mode = GameMode.STANDARD)
        assertTrue(vm.uiState.value.isOnlineSession)

        vm.initFromConfigs(configs(2), GameMode.STANDARD)

        assertFalse(vm.uiState.value.isOnlineSession)
        coVerify(timeout = 3_000, exactly = 1) { leaveSessionUseCase("session-1", null) }
        coVerify(timeout = 3_000, atLeast = 1) { observeSessionUseCase.disconnect("session-1") }
    }

    @Test
    fun `given online game when finishGame then the session is disconnected and left`() = runTest {
        val vm = buildViewModel()
        vm.initFromOnlineSession("session-2", mySlotIndex = 1, configs = configs(2), mode = GameMode.STANDARD)

        vm.finishGame()

        assertFalse(vm.uiState.value.isOnlineSession)
        assertFalse(vm.uiState.value.isGameRunning)
        coVerify(timeout = 3_000, exactly = 1) { leaveSessionUseCase("session-2", null) }
    }
}
