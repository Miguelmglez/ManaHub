package com.mmg.manahub.feature.game.presentation

import androidx.lifecycle.SavedStateHandle
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.core.domain.repository.GameSessionRepository
import com.mmg.manahub.core.domain.repository.TournamentRepository
import com.mmg.manahub.core.online.domain.model.OnlineSession
import com.mmg.manahub.core.online.domain.model.OnlineSessionStatus
import com.mmg.manahub.core.online.domain.model.SessionEvent
import com.mmg.manahub.core.online.domain.model.SessionPlayerState
import com.mmg.manahub.core.online.domain.model.SessionSnapshot
import com.mmg.manahub.core.online.domain.model.SessionState
import com.mmg.manahub.core.online.domain.usecase.AdvancePhaseUseCase
import com.mmg.manahub.core.online.domain.usecase.ConfirmDefeatUseCase
import com.mmg.manahub.core.online.domain.usecase.LeaveSessionUseCase
import com.mmg.manahub.core.online.domain.usecase.NextTurnUseCase
import com.mmg.manahub.core.online.domain.usecase.ObserveSessionUseCase
import com.mmg.manahub.core.online.domain.usecase.RevokeDefeatUseCase
import com.mmg.manahub.core.online.domain.usecase.UpdateLifeUseCase
import com.mmg.manahub.core.ui.theme.PlayerTheme
import com.mmg.manahub.core.ui.theme.PlayerThemeColors
import com.mmg.manahub.core.util.AnalyticsHelper
import com.mmg.manahub.feature.game.domain.model.GameMode
import com.mmg.manahub.feature.game.domain.model.GamePhase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
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
 * Unit tests for [GameViewModel] — **online session path only**.
 *
 * Offline/game logic is covered by GameViewModelTest.
 *
 * GROUP 1: initFromOnlineSession — player list, mySlotIndex, flags
 * GROUP 2: changeLife for own slot — broadcasts AND schedules persist
 * GROUP 3: changeLife for opponent slot — does NOT broadcast
 * GROUP 4: handleOnlineEvent LifeDeltaReceived — updates correct player
 * GROUP 5: handleOnlineEvent SessionStatusChanged ABANDONED/FINISHED
 * GROUP 6: Snapshot applied on init — player states and session state
 * GROUP 7: onCleared — calls leaveSessionUseCase
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModelOnlineTest {

    // ── Dispatcher ────────────────────────────────────────────────────────────

    private val testDispatcher = StandardTestDispatcher()

    // ── Mocks ─────────────────────────────────────────────────────────────────

    private val gameSessionRepo       = mockk<GameSessionRepository>(relaxed = true)
    private val tournamentRepo        = mockk<TournamentRepository>(relaxed = true)
    private val analyticsHelper       = mockk<AnalyticsHelper>(relaxed = true)
    private val observeSessionUseCase = mockk<ObserveSessionUseCase>(relaxed = true)
    private val updateLifeUseCase     = mockk<UpdateLifeUseCase>(relaxed = true)
    private val advancePhaseUseCase   = mockk<AdvancePhaseUseCase>(relaxed = true)
    private val nextTurnUseCase       = mockk<NextTurnUseCase>(relaxed = true)
    private val confirmDefeatUseCase  = mockk<ConfirmDefeatUseCase>(relaxed = true)
    private val revokeDefeatUseCase   = mockk<RevokeDefeatUseCase>(relaxed = true)
    private val leaveSessionUseCase   = mockk<LeaveSessionUseCase>(relaxed = true)

    // Shared event flow that tests can emit into
    private val eventFlow = MutableSharedFlow<SessionEvent>(extraBufferCapacity = 16)

    // ── Constants ─────────────────────────────────────────────────────────────

    companion object {
        const val SESSION_ID  = "online-session-001"
        const val MY_SLOT     = 0   // app-user occupies slot 0
        const val OPP_SLOT    = 1   // opponent occupies slot 1
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private val defaultTheme: PlayerThemeColors = PlayerTheme.ALL[0]

    private fun buildConfig(
        id: Int,
        name: String = "Wizard ${id + 1}",
        theme: PlayerThemeColors = defaultTheme,
    ) = PlayerConfig(
        id        = id,
        name      = name,
        theme     = theme,
        isAppUser = false, // isAppUser is derived from slotIndex in initFromOnlineSession
    )

    private fun buildSnapshot(
        currentPhase: String = "UNTAP",
        activePlayerSlot: Int = 0,
        turnNumber: Int = 1,
        playerStates: List<SessionPlayerState> = emptyList(),
    ) = SessionSnapshot(
        session = OnlineSession(
            id             = SESSION_ID,
            code           = "ABCDEF",
            hostUserId     = "host-user",
            gameMode       = "COMMANDER",
            playerCount    = 2,
            layoutKey      = null,
            status         = OnlineSessionStatus.ACTIVE,
            tournamentId   = null,
            tournamentMatchId = null,
            createdAt      = "2026-01-01T00:00:00Z",
            startedAt      = "2026-01-01T00:01:00Z",
            finishedAt     = null,
            lastActivityAt = "2026-01-01T00:01:00Z",
        ),
        sessionState = SessionState(
            sessionId        = SESSION_ID,
            currentPhase     = currentPhase,
            activePlayerSlot = activePlayerSlot,
            turnNumber       = turnNumber,
            phaseStops       = emptyMap(),
            lastDiceResult   = null,
            lastCoinResult   = null,
            updatedAt        = "2026-01-01T00:01:00Z",
        ),
        playerStates = playerStates,
        participants = emptyList(),
    )

    private fun buildPlayerState(
        slotIndex: Int,
        life: Int = 40,
        poison: Int = 0,
        experience: Int = 0,
        energy: Int = 0,
        defeated: Boolean = false,
    ) = SessionPlayerState(
        sessionId     = SESSION_ID,
        slotIndex     = slotIndex,
        life          = life,
        poison        = poison,
        energy        = energy,
        experience    = experience,
        commanderDamage = emptyMap(),
        customCounters  = emptyMap(),
        pendingDefeat = false,
        defeated      = defeated,
        hasPlayedLand = false,
        updatedAt     = "2026-01-01T00:01:00Z",
    )

    /**
     * Creates a ViewModel wired to [eventFlow] after each call to
     * [observeSessionUseCase.getSnapshot].
     */
    private fun createViewModel(): GameViewModel {
        coEvery { observeSessionUseCase.invoke(any()) } returns eventFlow
        coEvery { observeSessionUseCase.getSnapshot(any()) } returns
            Result.success(buildSnapshot())
        coEvery { gameSessionRepo.saveGameSession(any()) } returns 1L
        val handle = SavedStateHandle(mapOf("mode" to GameMode.COMMANDER.name, "playerCount" to 2))
        return GameViewModel(
            savedStateHandle      = handle,
            gameSessionRepo       = gameSessionRepo,
            tournamentRepo        = tournamentRepo,
            analyticsHelper       = analyticsHelper,
            observeSessionUseCase = observeSessionUseCase,
            updateLifeUseCase     = updateLifeUseCase,
            advancePhaseUseCase   = advancePhaseUseCase,
            nextTurnUseCase       = nextTurnUseCase,
            confirmDefeatUseCase  = confirmDefeatUseCase,
            revokeDefeatUseCase   = revokeDefeatUseCase,
            leaveSessionUseCase   = leaveSessionUseCase,
        )
    }

    /** Calls initFromOnlineSession with a 2-player setup where MY_SLOT=0. */
    private fun GameViewModel.initTwoPlayerOnlineSession() {
        initFromOnlineSession(
            sessionId   = SESSION_ID,
            mySlotIndex = MY_SLOT,
            configs     = listOf(buildConfig(MY_SLOT, "Me"), buildConfig(OPP_SLOT, "Opponent")),
            mode        = GameMode.COMMANDER,
        )
    }

    // ── Setup / Teardown ──────────────────────────────────────────────────────

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic(FirebaseCrashlytics::class)
        val crashlytics = mockk<FirebaseCrashlytics>(relaxed = true)
        every { FirebaseCrashlytics.getInstance() } returns crashlytics
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(FirebaseCrashlytics::class)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 1 — initFromOnlineSession: player list, mySlotIndex, flags
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given initFromOnlineSession when called then isOnlineSession flag is true`() = runTest {
        // Arrange
        val vm = createViewModel()

        // Act
        vm.initTwoPlayerOnlineSession()
        advanceUntilIdle()

        // Assert
        assertTrue(
            "isOnlineSession must be true after initFromOnlineSession",
            vm.uiState.value.isOnlineSession,
        )
    }

    @Test
    fun `given initFromOnlineSession then player list matches the provided configs`() = runTest {
        // Arrange
        val vm = createViewModel()

        // Act
        vm.initTwoPlayerOnlineSession()
        advanceUntilIdle()

        // Assert
        val players = vm.uiState.value.players
        assertEquals(2, players.size)
        assertEquals("Me", players[MY_SLOT].name)
        assertEquals("Opponent", players[OPP_SLOT].name)
    }

    @Test
    fun `given initFromOnlineSession then only the player at mySlotIndex has isAppUser true`() = runTest {
        // Arrange
        val vm = createViewModel()

        // Act
        vm.initTwoPlayerOnlineSession()
        advanceUntilIdle()

        // Assert
        val players = vm.uiState.value.players
        assertTrue("player at mySlotIndex must be appUser", players[MY_SLOT].isAppUser)
        assertFalse("opponent must NOT be appUser", players[OPP_SLOT].isAppUser)
    }

    @Test
    fun `given initFromOnlineSession then starting life matches the game mode`() = runTest {
        // Arrange — COMMANDER starts at 40
        val vm = createViewModel()

        // Act
        vm.initTwoPlayerOnlineSession()
        advanceUntilIdle()

        // Assert
        assertTrue(vm.uiState.value.players.all { it.life == GameMode.COMMANDER.startingLife })
    }

    @Test
    fun `given initFromOnlineSession then isGameRunning is true`() = runTest {
        // Arrange
        val vm = createViewModel()

        // Act
        vm.initTwoPlayerOnlineSession()
        advanceUntilIdle()

        // Assert
        assertTrue(vm.uiState.value.isGameRunning)
    }

    @Test
    fun `given initFromOnlineSession with empty config name then fallback name used`() = runTest {
        // Arrange — blank name in config
        coEvery { observeSessionUseCase.invoke(any()) } returns eventFlow
        coEvery { observeSessionUseCase.getSnapshot(any()) } returns Result.success(buildSnapshot())
        coEvery { gameSessionRepo.saveGameSession(any()) } returns 1L
        val handle = SavedStateHandle(mapOf("mode" to GameMode.STANDARD.name, "playerCount" to 2))
        val vm = GameViewModel(
            savedStateHandle      = handle,
            gameSessionRepo       = gameSessionRepo,
            tournamentRepo        = tournamentRepo,
            analyticsHelper       = analyticsHelper,
            observeSessionUseCase = observeSessionUseCase,
            updateLifeUseCase     = updateLifeUseCase,
            advancePhaseUseCase   = advancePhaseUseCase,
            nextTurnUseCase       = nextTurnUseCase,
            confirmDefeatUseCase  = confirmDefeatUseCase,
            revokeDefeatUseCase   = revokeDefeatUseCase,
            leaveSessionUseCase   = leaveSessionUseCase,
        )

        // Act
        vm.initFromOnlineSession(
            sessionId   = SESSION_ID,
            mySlotIndex = 0,
            configs     = listOf(buildConfig(0, name = ""), buildConfig(1, name = "")),
            mode        = GameMode.STANDARD,
        )
        advanceUntilIdle()

        // Assert — fallback names applied
        val players = vm.uiState.value.players
        assertEquals("Wizard 1", players[0].name)
        assertEquals("Wizard 2", players[1].name)
    }

    @Test
    fun `given initFromOnlineSession called then observeSession and connect are invoked`() = runTest {
        // Arrange
        val vm = createViewModel()

        // Act
        vm.initTwoPlayerOnlineSession()
        advanceUntilIdle()

        // Assert
        coVerify { observeSessionUseCase.connect(SESSION_ID) }
        coVerify { observeSessionUseCase.invoke(SESSION_ID) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 2 — changeLife for own slot: broadcasts AND schedules persist
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given online session when changeLife called for own slot then life is updated in state`() = runTest {
        // Arrange
        val vm = createViewModel()
        vm.initTwoPlayerOnlineSession()
        advanceUntilIdle()
        val startingLife = vm.uiState.value.players[MY_SLOT].life

        // Act — decrease own life by 5
        vm.changeLife(MY_SLOT, -5)
        advanceUntilIdle()

        // Assert
        assertEquals(startingLife - 5, vm.uiState.value.players[MY_SLOT].life)
    }

    @Test
    fun `given online session when changeLife called for own slot then broadcast is sent immediately`() = runTest {
        // Arrange
        val vm = createViewModel()
        vm.initTwoPlayerOnlineSession()
        advanceUntilIdle()
        val expectedLife = GameMode.COMMANDER.startingLife - 3

        // Act
        vm.changeLife(MY_SLOT, -3)
        advanceUntilIdle()

        // Assert — broadcast called with correct life value
        coVerify { updateLifeUseCase.broadcast(SESSION_ID, MY_SLOT, expectedLife) }
    }

    @Test
    fun `given online session when changeLife called multiple times for own slot then life accumulates correctly`() = runTest {
        // Arrange
        val vm = createViewModel()
        vm.initTwoPlayerOnlineSession()
        advanceUntilIdle()
        val startingLife = GameMode.COMMANDER.startingLife

        // Act — apply two deltas
        vm.changeLife(MY_SLOT, -5)
        vm.changeLife(MY_SLOT, +2)
        advanceUntilIdle()

        // Assert — net delta is -3
        assertEquals(startingLife - 3, vm.uiState.value.players[MY_SLOT].life)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 3 — changeLife for opponent slot: no broadcast
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given online session when changeLife called for opponent slot then state is updated locally`() = runTest {
        // Arrange
        val vm = createViewModel()
        vm.initTwoPlayerOnlineSession()
        advanceUntilIdle()
        val startingLife = vm.uiState.value.players[OPP_SLOT].life

        // Act
        vm.changeLife(OPP_SLOT, -10)
        advanceUntilIdle()

        // Assert — life decremented locally
        assertEquals(startingLife - 10, vm.uiState.value.players[OPP_SLOT].life)
    }

    @Test
    fun `given online session when changeLife called for opponent slot then broadcast is NOT sent`() = runTest {
        // Arrange
        val vm = createViewModel()
        vm.initTwoPlayerOnlineSession()
        advanceUntilIdle()

        // Act — change opponent's life
        vm.changeLife(OPP_SLOT, -10)
        advanceUntilIdle()

        // Assert — broadcast must NOT be called for opponent slot
        coVerify(exactly = 0) { updateLifeUseCase.broadcast(SESSION_ID, OPP_SLOT, any()) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 4 — handleOnlineEvent: LifeDeltaReceived
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given LifeDeltaReceived for opponent slot then opponent life is updated in state`() = runTest {
        // Arrange
        val vm = createViewModel()
        vm.initTwoPlayerOnlineSession()
        advanceUntilIdle()

        // Act — opponent's life drops to 35 via realtime event
        eventFlow.emit(SessionEvent.LifeDeltaReceived(slotIndex = OPP_SLOT, newLife = 35))
        advanceUntilIdle()

        // Assert
        assertEquals(35, vm.uiState.value.players[OPP_SLOT].life)
    }

    @Test
    fun `given LifeDeltaReceived for own slot then own life is NOT updated (self-broadcast ignored)`() = runTest {
        // Arrange
        val vm = createViewModel()
        vm.initTwoPlayerOnlineSession()
        advanceUntilIdle()
        val ownLifeBefore = vm.uiState.value.players[MY_SLOT].life

        // Act — receive our own life update over realtime (should be ignored)
        eventFlow.emit(SessionEvent.LifeDeltaReceived(slotIndex = MY_SLOT, newLife = 99))
        advanceUntilIdle()

        // Assert — own life unchanged
        assertEquals(
            "Own life must not be overwritten by LifeDeltaReceived for own slot",
            ownLifeBefore,
            vm.uiState.value.players[MY_SLOT].life,
        )
    }

    @Test
    fun `given LifeDeltaReceived for opponent with zero life then opponent state reflects that`() = runTest {
        // Arrange
        val vm = createViewModel()
        vm.initTwoPlayerOnlineSession()
        advanceUntilIdle()

        // Act — opponent reaches 0
        eventFlow.emit(SessionEvent.LifeDeltaReceived(slotIndex = OPP_SLOT, newLife = 0))
        advanceUntilIdle()

        // Assert — life is 0 and pendingDefeat should be set
        assertEquals(0, vm.uiState.value.players[OPP_SLOT].life)
        assertTrue(
            "pendingDefeat must be true when opponent life reaches 0",
            vm.uiState.value.players[OPP_SLOT].pendingDefeat,
        )
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 5 — handleOnlineEvent: SessionStatusChanged ABANDONED / FINISHED
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given SessionStatusChanged to ABANDONED then isOnlineSessionAbandoned is true`() = runTest {
        // Arrange
        val vm = createViewModel()
        vm.initTwoPlayerOnlineSession()
        advanceUntilIdle()

        // Act
        eventFlow.emit(SessionEvent.SessionStatusChanged(OnlineSessionStatus.ABANDONED))
        advanceUntilIdle()

        // Assert
        assertTrue(
            "isOnlineSessionAbandoned must be true when ABANDONED event received",
            vm.uiState.value.isOnlineSessionAbandoned,
        )
    }

    @Test
    fun `given SessionStatusChanged to ABANDONED then isGameRunning is false`() = runTest {
        // Arrange
        val vm = createViewModel()
        vm.initTwoPlayerOnlineSession()
        advanceUntilIdle()

        // Act
        eventFlow.emit(SessionEvent.SessionStatusChanged(OnlineSessionStatus.ABANDONED))
        advanceUntilIdle()

        // Assert
        assertFalse(
            "isGameRunning must be false when session is ABANDONED",
            vm.uiState.value.isGameRunning,
        )
    }

    @Test
    fun `given SessionStatusChanged to FINISHED then isOnlineSessionAbandoned is true`() = runTest {
        // Arrange
        val vm = createViewModel()
        vm.initTwoPlayerOnlineSession()
        advanceUntilIdle()

        // Act
        eventFlow.emit(SessionEvent.SessionStatusChanged(OnlineSessionStatus.FINISHED))
        advanceUntilIdle()

        // Assert
        assertTrue(vm.uiState.value.isOnlineSessionAbandoned)
    }

    @Test
    fun `given SessionStatusChanged to ACTIVE then isOnlineSessionAbandoned remains false`() = runTest {
        // Arrange
        val vm = createViewModel()
        vm.initTwoPlayerOnlineSession()
        advanceUntilIdle()

        // Act — ACTIVE does not affect the abandoned flag
        eventFlow.emit(SessionEvent.SessionStatusChanged(OnlineSessionStatus.ACTIVE))
        advanceUntilIdle()

        // Assert
        assertFalse(
            "isOnlineSessionAbandoned must remain false on ACTIVE status",
            vm.uiState.value.isOnlineSessionAbandoned,
        )
        assertTrue("isGameRunning must remain true on ACTIVE status", vm.uiState.value.isGameRunning)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 6 — Snapshot applied on init
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given snapshot has player states when initFromOnlineSession then player life is synced from snapshot`() = runTest {
        // Arrange — snapshot with custom life values (e.g. reconnect scenario)
        val snapshotLife = 27
        val playerState = buildPlayerState(slotIndex = OPP_SLOT, life = snapshotLife)
        coEvery { observeSessionUseCase.getSnapshot(SESSION_ID) } returns
            Result.success(buildSnapshot(playerStates = listOf(playerState)))
        coEvery { observeSessionUseCase.invoke(any()) } returns eventFlow
        val vm = createViewModel()

        // Act
        vm.initTwoPlayerOnlineSession()
        advanceUntilIdle()

        // Assert — opponent life reflects snapshot value
        assertEquals(snapshotLife, vm.uiState.value.players[OPP_SLOT].life)
    }

    @Test
    fun `given snapshot has session state when initFromOnlineSession then phase and turn number are applied`() = runTest {
        // Arrange — reconnect with turn 3 in DRAW phase
        coEvery { observeSessionUseCase.getSnapshot(SESSION_ID) } returns Result.success(
            buildSnapshot(
                currentPhase  = "DRAW",
                activePlayerSlot = OPP_SLOT,
                turnNumber    = 3,
            )
        )
        coEvery { observeSessionUseCase.invoke(any()) } returns eventFlow
        val vm = createViewModel()

        // Act
        vm.initTwoPlayerOnlineSession()
        advanceUntilIdle()

        // Assert
        assertEquals(GamePhase.DRAW, vm.uiState.value.currentPhase)
        assertEquals(3, vm.uiState.value.turnNumber)
        assertEquals(OPP_SLOT, vm.uiState.value.activePlayerId)
    }

    @Test
    fun `given snapshot fetch fails when initFromOnlineSession then game still initialises with default state`() = runTest {
        // Arrange — getSnapshot returns failure (e.g. network offline on reconnect)
        coEvery { observeSessionUseCase.getSnapshot(SESSION_ID) } returns
            Result.failure(RuntimeException("Snapshot unavailable"))
        coEvery { observeSessionUseCase.invoke(any()) } returns eventFlow
        val vm = createViewModel()

        // Act
        vm.initTwoPlayerOnlineSession()
        advanceUntilIdle()

        // Assert — ViewModel did not crash, online session flag is set
        assertTrue(vm.uiState.value.isOnlineSession)
        // Default starting life from mode
        assertTrue(vm.uiState.value.players.all { it.life == GameMode.COMMANDER.startingLife })
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 7 — onCleared: calls leaveSessionUseCase
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given online session active when ViewModel cleared then leaveSessionUseCase is called`() = runTest {
        // Arrange
        val vm = createViewModel()
        vm.initTwoPlayerOnlineSession()
        advanceUntilIdle()

        // Act — simulate onCleared (accessible via reflection or direct call in test scope)
        // We call the public-facing method directly as the private field is cleared internally
        vm.onCleared_testHook()
        advanceUntilIdle()

        // Assert
        coVerify { leaveSessionUseCase(SESSION_ID) }
    }

    @Test
    fun `given no online session when ViewModel cleared then leaveSessionUseCase is NOT called`() = runTest {
        // Arrange — no initFromOnlineSession called
        val vm = createViewModel()
        advanceUntilIdle()

        // Act
        vm.onCleared_testHook()
        advanceUntilIdle()

        // Assert — no session to leave
        coVerify(exactly = 0) { leaveSessionUseCase(any()) }
    }
}

/**
 * Exposes [GameViewModel.onCleared] for testing via reflection.
 *
 * [androidx.lifecycle.ViewModel.onCleared] is protected. Reflection is used here so that
 * production code remains unmodified while still allowing lifecycle cleanup verification.
 */
fun GameViewModel.onCleared_testHook() {
    // Walk the class hierarchy to find the declared onCleared() method.
    var clazz: Class<*>? = this::class.java
    while (clazz != null) {
        try {
            val method = clazz.getDeclaredMethod("onCleared")
            method.isAccessible = true
            method.invoke(this)
            return
        } catch (_: NoSuchMethodException) {
            clazz = clazz.superclass
        }
    }
    throw NoSuchMethodException("onCleared not found in ${this::class.java.name} hierarchy")
}
