package com.mmg.manahub.feature.online.presentation.lobby

import androidx.lifecycle.SavedStateHandle
import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.online.domain.model.ActiveSession
import com.mmg.manahub.core.online.domain.model.OnlineParticipant
import com.mmg.manahub.core.online.domain.model.OnlineSessionStatus
import com.mmg.manahub.core.online.domain.model.ParticipantStatus
import com.mmg.manahub.core.online.domain.model.SessionEvent
import com.mmg.manahub.core.online.domain.repository.OnlineSessionRepository
import com.mmg.manahub.core.online.domain.usecase.AbandonMyActiveSessionUseCase
import com.mmg.manahub.core.online.domain.usecase.CreateSessionUseCase
import com.mmg.manahub.core.online.domain.usecase.GetMyActiveSessionsUseCase
import com.mmg.manahub.core.online.domain.usecase.LeaveSessionUseCase
import com.mmg.manahub.core.online.domain.usecase.ObserveSessionUseCase
import com.mmg.manahub.core.online.domain.usecase.StartSessionUseCase
import com.mmg.manahub.feature.game.domain.model.GameMode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
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
 * Unit tests for [LobbyHostViewModel].
 *
 * GROUP 1: createSession — success path
 * GROUP 2: createSession — no-op if session already exists
 * GROUP 3: createSession — failure path with error mapping
 * GROUP 4: startSession — success path invokes onGameStart callback
 * GROUP 5: startSession — no-op when sessionId is null
 * GROUP 6: startSession — failure path sets error
 * GROUP 7: leaveSession — calls use case and navigates back
 * GROUP 8: handleEvent ParticipantUpdated — merge, sort, remove LEFT
 * GROUP 9: handleEvent SessionStatusChanged — clears participants on ABANDONED/FINISHED
 * GROUP 10: clearError — clears the error field
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LobbyHostViewModelTest {

    // ── Dispatcher ────────────────────────────────────────────────────────────

    private val testDispatcher = StandardTestDispatcher()

    // ── Mocks ─────────────────────────────────────────────────────────────────

    private val createSessionUseCase        = mockk<CreateSessionUseCase>()
    private val startSessionUseCase         = mockk<StartSessionUseCase>()
    private val observeSessionUseCase       = mockk<ObserveSessionUseCase>(relaxed = true)
    private val leaveSessionUseCase         = mockk<LeaveSessionUseCase>(relaxed = true)
    private val getMyActiveSessionsUseCase  = mockk<GetMyActiveSessionsUseCase>(relaxed = true)
    private val abandonMyActiveSessionUseCase = mockk<AbandonMyActiveSessionUseCase>(relaxed = true)
    private val repository                  = mockk<OnlineSessionRepository>(relaxed = true)
    private val userPreferencesDataStore    = mockk<UserPreferencesDataStore>(relaxed = true)

    // Shared flow reused across tests that need to emit events after session creation
    private val eventFlow = MutableSharedFlow<SessionEvent>(extraBufferCapacity = 8)

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildParticipant(
        id: String = "p1",
        slotIndex: Int = 0,
        isReady: Boolean = false,
        status: ParticipantStatus = ParticipantStatus.JOINED,
    ) = OnlineParticipant(
        id           = id,
        sessionId    = SESSION_ID,
        slotIndex    = slotIndex,
        userId       = "user-$id",
        displayName  = "Player $id",
        themeKey     = "Crimson",
        isHost       = slotIndex == 0,
        isReady      = isReady,
        status       = status,
        lastSeenAt   = "2026-01-01T00:00:00Z",
    )

    /**
     * Creates the ViewModel wired to the shared [eventFlow].
     * By default [observeSessionUseCase.connect] is a no-op (relaxed mock),
     * and [observeSessionUseCase.invoke] returns [eventFlow].
     */
    private fun createViewModel(): LobbyHostViewModel {
        coEvery { observeSessionUseCase.invoke(any()) } returns eventFlow
        // init block calls checkForExistingSession() → getMyActiveSessionsUseCase()
        coEvery { getMyActiveSessionsUseCase() } returns Result.success(emptyList())
        // init block calls userPreferencesDataStore.playerNameFlow.first()
        coEvery { userPreferencesDataStore.playerNameFlow } returns flowOf("")
        return LobbyHostViewModel(
            createSessionUseCase          = createSessionUseCase,
            startSessionUseCase           = startSessionUseCase,
            observeSessionUseCase         = observeSessionUseCase,
            leaveSessionUseCase           = leaveSessionUseCase,
            getMyActiveSessionsUseCase    = getMyActiveSessionsUseCase,
            abandonMyActiveSessionUseCase = abandonMyActiveSessionUseCase,
            repository                    = repository,
            userPreferencesDataStore      = userPreferencesDataStore,
            savedStateHandle              = SavedStateHandle(),
        )
    }

    companion object {
        const val SESSION_ID   = "session-abc-123"
        const val SESSION_CODE = "123456"
    }

    // ── Setup / Teardown ──────────────────────────────────────────────────────

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 1 — createSession success path
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given no session when createSession succeeds then sessionId and code are set in state`() = runTest {
        // Arrange
        coEvery { createSessionUseCase(any(), any(), any(), any(), any()) } returns
            Result.success(Pair(SESSION_ID, SESSION_CODE))
        val vm = createViewModel()

        // Act
        vm.createSession()
        advanceUntilIdle()

        // Assert
        val state = vm.uiState.value
        assertEquals(SESSION_ID, state.sessionId)
        assertEquals(SESSION_CODE, state.sessionCode)
        assertNull("error must be null after successful creation", state.error)
        assertFalse("isLoading must be false after completion", state.isLoading)
    }

    @Test
    fun `given no session when createSession succeeds then loading is true during request then false after`() = runTest {
        // Arrange — suspend the use case so we can observe the loading state
        val latch = kotlinx.coroutines.CompletableDeferred<Result<Pair<String, String>>>()
        coEvery { createSessionUseCase(any(), any(), any(), any(), any()) } coAnswers { latch.await() }
        val vm = createViewModel()

        // Act — start the request but do not yet resolve it
        vm.createSession()
        testDispatcher.scheduler.runCurrent()
        assertTrue("isLoading must be true while request is in flight", vm.uiState.value.isLoading)

        // Resolve the use case
        latch.complete(Result.success(Pair(SESSION_ID, SESSION_CODE)))
        advanceUntilIdle()

        // Assert
        assertFalse("isLoading must be false after completion", vm.uiState.value.isLoading)
    }

    @Test
    fun `given createSession succeeds then connects and observes session`() = runTest {
        // Arrange
        coEvery { createSessionUseCase(any(), any(), any(), any(), any()) } returns
            Result.success(Pair(SESSION_ID, SESSION_CODE))
        val vm = createViewModel()

        // Act
        vm.createSession()
        advanceUntilIdle()

        // Assert — connect was called for the session
        coVerify { observeSessionUseCase.connect(SESSION_ID) }
        coVerify { observeSessionUseCase.invoke(SESSION_ID) }
    }

    @Test
    fun `given createSession succeeds then selected gameMode is passed to use case`() = runTest {
        // Arrange
        coEvery { createSessionUseCase("COMMANDER", 4, null, any(), any()) } returns
            Result.success(Pair(SESSION_ID, SESSION_CODE))
        val vm = createViewModel()
        vm.setGameMode(GameMode.COMMANDER)
        vm.setPlayerCount(4)

        // Act
        vm.createSession()
        advanceUntilIdle()

        // Assert
        coVerify { createSessionUseCase("COMMANDER", 4, null, any(), any()) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 2 — createSession no-op if session already set
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given sessionId already set when createSession called then use case is NOT invoked again`() = runTest {
        // Arrange — first call succeeds
        coEvery { createSessionUseCase(any(), any(), any(), any(), any()) } returns
            Result.success(Pair(SESSION_ID, SESSION_CODE))
        val vm = createViewModel()
        vm.createSession()
        advanceUntilIdle()

        // Act — call again
        vm.createSession()
        advanceUntilIdle()

        // Assert — use case was called exactly once
        coVerify(exactly = 1) { createSessionUseCase(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `given sessionId already set when createSession called then state is unchanged`() = runTest {
        // Arrange
        coEvery { createSessionUseCase(any(), any(), any(), any(), any()) } returns
            Result.success(Pair(SESSION_ID, SESSION_CODE))
        val vm = createViewModel()
        vm.createSession()
        advanceUntilIdle()
        val stateAfterFirst = vm.uiState.value

        // Act
        vm.createSession()
        advanceUntilIdle()

        // Assert — state identical after second call
        assertEquals(stateAfterFirst, vm.uiState.value)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 3 — createSession failure — error mapping
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given createSession fails with Session limit reached then error is mapped to Spanish message`() = runTest {
        // Arrange
        coEvery { createSessionUseCase(any(), any(), any(), any(), any()) } returns
            Result.failure(RuntimeException("Session limit reached for this user"))
        val vm = createViewModel()

        // Act
        vm.createSession()
        advanceUntilIdle()

        // Assert
        assertEquals(
            "Ya tienes una sala activa. Ciérrala antes de crear otra.",
            vm.uiState.value.error,
        )
        assertNull("sessionId must remain null after failure", vm.uiState.value.sessionId)
    }

    @Test
    fun `given createSession fails with null message then error is generic fallback`() = runTest {
        // Arrange — throwable without message
        coEvery { createSessionUseCase(any(), any(), any(), any(), any()) } returns
            Result.failure(RuntimeException())
        val vm = createViewModel()

        // Act
        vm.createSession()
        advanceUntilIdle()

        // Assert
        assertEquals("Ocurrió un error inesperado.", vm.uiState.value.error)
    }

    @Test
    fun `given createSession fails with unknown message then error is the generic fallback not raw server message`() = runTest {
        // Arrange — an unrecognised backend message must NOT reach the user verbatim;
        // it would leak internal server details. The expected value is the generic fallback.
        val rawMessage = "Quota exceeded for region us-east-1"
        coEvery { createSessionUseCase(any(), any(), any(), any(), any()) } returns
            Result.failure(RuntimeException(rawMessage))
        val vm = createViewModel()

        // Act
        vm.createSession()
        advanceUntilIdle()

        // Assert — user sees generic message, NOT the raw server string
        assertEquals("Ocurrió un error inesperado.", vm.uiState.value.error)
    }

    @Test
    fun `given createSession fails with Session is full message then mapped correctly`() = runTest {
        // Arrange
        coEvery { createSessionUseCase(any(), any(), any(), any(), any()) } returns
            Result.failure(RuntimeException("Session is full"))
        val vm = createViewModel()

        // Act
        vm.createSession()
        advanceUntilIdle()

        // Assert
        assertEquals("La sala ya está llena.", vm.uiState.value.error)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 4 — startSession success path
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given session created when startSession succeeds then onGameStart is invoked with correct params`() = runTest {
        // Arrange — create session first, then start it
        coEvery { createSessionUseCase(any(), any(), any(), any(), any()) } returns
            Result.success(Pair(SESSION_ID, SESSION_CODE))
        coEvery { startSessionUseCase(SESSION_ID) } returns Result.success(Unit)
        val vm = createViewModel()
        vm.setGameMode(GameMode.COMMANDER)
        vm.setPlayerCount(3)
        vm.createSession()
        advanceUntilIdle()

        var capturedSessionId: String? = null
        var capturedMode: GameMode? = null
        var capturedPlayerCount: Int? = null

        // Act
        vm.startSession { sid, mode, count ->
            capturedSessionId   = sid
            capturedMode        = mode
            capturedPlayerCount = count
        }
        advanceUntilIdle()

        // Assert
        assertEquals(SESSION_ID, capturedSessionId)
        assertEquals(GameMode.COMMANDER, capturedMode)
        assertEquals(3, capturedPlayerCount)
        assertFalse("isLoading must be false after startSession completes", vm.uiState.value.isLoading)
    }

    @Test
    fun `given startSession succeeds then state has no error`() = runTest {
        // Arrange
        coEvery { createSessionUseCase(any(), any(), any(), any(), any()) } returns
            Result.success(Pair(SESSION_ID, SESSION_CODE))
        coEvery { startSessionUseCase(SESSION_ID) } returns Result.success(Unit)
        val vm = createViewModel()
        vm.createSession()
        advanceUntilIdle()

        // Act
        vm.startSession { _, _, _ -> }
        advanceUntilIdle()

        // Assert
        assertNull(vm.uiState.value.error)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 5 — startSession no-op when sessionId is null
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given no session when startSession called then onGameStart is NOT invoked and use case is NOT called`() = runTest {
        // Arrange — sessionId is null (no createSession called)
        val vm = createViewModel()
        var callbackInvoked = false

        // Act
        vm.startSession { _, _, _ -> callbackInvoked = true }
        advanceUntilIdle()

        // Assert
        assertFalse("onGameStart must not be invoked when sessionId is null", callbackInvoked)
        coVerify(exactly = 0) { startSessionUseCase(any()) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 6 — startSession failure path
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given startSession fails then error is set in state and onGameStart is NOT called`() = runTest {
        // Arrange
        coEvery { createSessionUseCase(any(), any(), any(), any(), any()) } returns
            Result.success(Pair(SESSION_ID, SESSION_CODE))
        coEvery { startSessionUseCase(SESSION_ID) } returns
            Result.failure(RuntimeException("Session not found or not in LOBBY status"))
        val vm = createViewModel()
        vm.createSession()
        advanceUntilIdle()
        var callbackInvoked = false

        // Act
        vm.startSession { _, _, _ -> callbackInvoked = true }
        advanceUntilIdle()

        // Assert
        assertFalse("onGameStart must not be called when startSession fails", callbackInvoked)
        assertEquals("Sala no encontrada o ya iniciada.", vm.uiState.value.error)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 7 — leaveSession
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given session exists when leaveSession then LeaveSessionUseCase is called`() = runTest {
        // Arrange
        coEvery { createSessionUseCase(any(), any(), any(), any(), any()) } returns
            Result.success(Pair(SESSION_ID, SESSION_CODE))
        val vm = createViewModel()
        vm.createSession()
        advanceUntilIdle()

        // Act
        vm.leaveSession {}
        advanceUntilIdle()

        // Assert
        coVerify { leaveSessionUseCase(SESSION_ID) }
    }

    @Test
    fun `given session exists when leaveSession then onNavigateBack is invoked`() = runTest {
        // Arrange
        coEvery { createSessionUseCase(any(), any(), any(), any(), any()) } returns
            Result.success(Pair(SESSION_ID, SESSION_CODE))
        val vm = createViewModel()
        vm.createSession()
        advanceUntilIdle()
        var navigated = false

        // Act
        vm.leaveSession { navigated = true }
        advanceUntilIdle()

        // Assert
        assertTrue("onNavigateBack must be invoked after leaveSession", navigated)
    }

    @Test
    fun `given no session when leaveSession then onNavigateBack is still invoked without calling use case`() = runTest {
        // Arrange — sessionId is null
        val vm = createViewModel()
        var navigated = false

        // Act
        vm.leaveSession { navigated = true }
        advanceUntilIdle()

        // Assert
        assertTrue("onNavigateBack must still be invoked even without a session", navigated)
        coVerify(exactly = 0) { leaveSessionUseCase(any()) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 8 — handleEvent: ParticipantUpdated
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given ParticipantUpdated event then participant is added to the list`() = runTest {
        // Arrange — create session so observation starts
        coEvery { createSessionUseCase(any(), any(), any(), any(), any()) } returns
            Result.success(Pair(SESSION_ID, SESSION_CODE))
        val vm = createViewModel()
        vm.createSession()
        advanceUntilIdle()

        // Act
        val participant = buildParticipant(id = "p1", slotIndex = 1)
        eventFlow.emit(SessionEvent.ParticipantUpdated(participant))
        advanceUntilIdle()

        // Assert
        val participants = vm.uiState.value.participants
        assertEquals(1, participants.size)
        assertEquals("p1", participants.first().id)
    }

    @Test
    fun `given existing participant receives updated event then participant is replaced not duplicated`() = runTest {
        // Arrange
        coEvery { createSessionUseCase(any(), any(), any(), any(), any()) } returns
            Result.success(Pair(SESSION_ID, SESSION_CODE))
        val vm = createViewModel()
        vm.createSession()
        advanceUntilIdle()

        // Emit initial participant (not ready)
        val p1 = buildParticipant(id = "p1", slotIndex = 0, isReady = false)
        eventFlow.emit(SessionEvent.ParticipantUpdated(p1))
        advanceUntilIdle()

        // Act — update same participant to ready
        val p1Updated = p1.copy(isReady = true)
        eventFlow.emit(SessionEvent.ParticipantUpdated(p1Updated))
        advanceUntilIdle()

        // Assert — still only one participant, now ready
        assertEquals(1, vm.uiState.value.participants.size)
        assertTrue(vm.uiState.value.participants.first().isReady)
    }

    @Test
    fun `given ParticipantUpdated with LEFT status then participant is removed from list`() = runTest {
        // Arrange — add a participant first
        coEvery { createSessionUseCase(any(), any(), any(), any(), any()) } returns
            Result.success(Pair(SESSION_ID, SESSION_CODE))
        val vm = createViewModel()
        vm.createSession()
        advanceUntilIdle()

        val p1 = buildParticipant(id = "p1", slotIndex = 0, status = ParticipantStatus.JOINED)
        eventFlow.emit(SessionEvent.ParticipantUpdated(p1))
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.participants.size)

        // Act — emit the same participant with LEFT status
        eventFlow.emit(SessionEvent.ParticipantUpdated(p1.copy(status = ParticipantStatus.LEFT)))
        advanceUntilIdle()

        // Assert
        assertTrue(
            "LEFT participant must be removed from the list",
            vm.uiState.value.participants.isEmpty(),
        )
    }

    @Test
    fun `given multiple participants when ParticipantUpdated events received then sorted by slotIndex`() = runTest {
        // Arrange
        coEvery { createSessionUseCase(any(), any(), any(), any(), any()) } returns
            Result.success(Pair(SESSION_ID, SESSION_CODE))
        val vm = createViewModel()
        vm.createSession()
        advanceUntilIdle()

        // Act — emit in reverse slotIndex order
        eventFlow.emit(SessionEvent.ParticipantUpdated(buildParticipant("p3", slotIndex = 2)))
        eventFlow.emit(SessionEvent.ParticipantUpdated(buildParticipant("p1", slotIndex = 0)))
        eventFlow.emit(SessionEvent.ParticipantUpdated(buildParticipant("p2", slotIndex = 1)))
        advanceUntilIdle()

        // Assert — must be sorted ascending by slotIndex
        val slots = vm.uiState.value.participants.map { it.slotIndex }
        assertEquals(listOf(0, 1, 2), slots)
    }

    @Test
    fun `given all participants are ready then allReady and canStart are true`() = runTest {
        // Arrange
        coEvery { createSessionUseCase(any(), any(), any(), any(), any()) } returns
            Result.success(Pair(SESSION_ID, SESSION_CODE))
        val vm = createViewModel()
        vm.createSession()
        advanceUntilIdle()

        // Act — add two ready participants
        eventFlow.emit(SessionEvent.ParticipantUpdated(buildParticipant("p1", slotIndex = 0, isReady = true)))
        eventFlow.emit(SessionEvent.ParticipantUpdated(buildParticipant("p2", slotIndex = 1, isReady = true)))
        advanceUntilIdle()

        // Assert
        assertTrue(vm.uiState.value.allReady)
        assertTrue(vm.uiState.value.canStart)
    }

    @Test
    fun `given only one participant is ready then allReady is false and canStart is false`() = runTest {
        // Arrange
        coEvery { createSessionUseCase(any(), any(), any(), any(), any()) } returns
            Result.success(Pair(SESSION_ID, SESSION_CODE))
        val vm = createViewModel()
        vm.createSession()
        advanceUntilIdle()

        // Act — one ready, one not ready
        eventFlow.emit(SessionEvent.ParticipantUpdated(buildParticipant("p1", slotIndex = 0, isReady = true)))
        eventFlow.emit(SessionEvent.ParticipantUpdated(buildParticipant("p2", slotIndex = 1, isReady = false)))
        advanceUntilIdle()

        // Assert
        assertFalse(vm.uiState.value.allReady)
        assertFalse(vm.uiState.value.canStart)
    }

    @Test
    fun `given only one participant even if ready then canStart is false (minimum 2 players required)`() = runTest {
        // Arrange
        coEvery { createSessionUseCase(any(), any(), any(), any(), any()) } returns
            Result.success(Pair(SESSION_ID, SESSION_CODE))
        val vm = createViewModel()
        vm.createSession()
        advanceUntilIdle()

        // Act — single ready participant
        eventFlow.emit(SessionEvent.ParticipantUpdated(buildParticipant("p1", slotIndex = 0, isReady = true)))
        advanceUntilIdle()

        // Assert
        assertTrue(vm.uiState.value.allReady)
        assertFalse(
            "canStart must be false with fewer than 2 players",
            vm.uiState.value.canStart,
        )
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 9 — handleEvent: SessionStatusChanged
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given SessionStatusChanged to ABANDONED then participants list is cleared`() = runTest {
        // Arrange — add participants first
        coEvery { createSessionUseCase(any(), any(), any(), any(), any()) } returns
            Result.success(Pair(SESSION_ID, SESSION_CODE))
        val vm = createViewModel()
        vm.createSession()
        advanceUntilIdle()

        eventFlow.emit(SessionEvent.ParticipantUpdated(buildParticipant("p1", slotIndex = 0)))
        eventFlow.emit(SessionEvent.ParticipantUpdated(buildParticipant("p2", slotIndex = 1)))
        advanceUntilIdle()
        assertEquals(2, vm.uiState.value.participants.size)

        // Act
        eventFlow.emit(SessionEvent.SessionStatusChanged(OnlineSessionStatus.ABANDONED))
        advanceUntilIdle()

        // Assert
        assertTrue(
            "participants must be cleared on ABANDONED status",
            vm.uiState.value.participants.isEmpty(),
        )
    }

    @Test
    fun `given SessionStatusChanged to FINISHED then participants list is cleared`() = runTest {
        // Arrange
        coEvery { createSessionUseCase(any(), any(), any(), any(), any()) } returns
            Result.success(Pair(SESSION_ID, SESSION_CODE))
        val vm = createViewModel()
        vm.createSession()
        advanceUntilIdle()

        eventFlow.emit(SessionEvent.ParticipantUpdated(buildParticipant("p1", slotIndex = 0)))
        advanceUntilIdle()

        // Act
        eventFlow.emit(SessionEvent.SessionStatusChanged(OnlineSessionStatus.FINISHED))
        advanceUntilIdle()

        // Assert
        assertTrue(vm.uiState.value.participants.isEmpty())
    }

    @Test
    fun `given SessionStatusChanged to ACTIVE then participants list is NOT cleared`() = runTest {
        // Arrange
        coEvery { createSessionUseCase(any(), any(), any(), any(), any()) } returns
            Result.success(Pair(SESSION_ID, SESSION_CODE))
        val vm = createViewModel()
        vm.createSession()
        advanceUntilIdle()

        eventFlow.emit(SessionEvent.ParticipantUpdated(buildParticipant("p1", slotIndex = 0)))
        advanceUntilIdle()

        // Act — ACTIVE does not clear participants (only ABANDONED/FINISHED do)
        eventFlow.emit(SessionEvent.SessionStatusChanged(OnlineSessionStatus.ACTIVE))
        advanceUntilIdle()

        // Assert
        assertEquals(
            "participants must NOT be cleared on ACTIVE status",
            1,
            vm.uiState.value.participants.size,
        )
    }

    @Test
    fun `given SessionEvent Error then error is set in state`() = runTest {
        // Arrange
        coEvery { createSessionUseCase(any(), any(), any(), any(), any()) } returns
            Result.success(Pair(SESSION_ID, SESSION_CODE))
        val vm = createViewModel()
        vm.createSession()
        advanceUntilIdle()

        // Act
        eventFlow.emit(SessionEvent.Error("Session is full"))
        advanceUntilIdle()

        // Assert
        assertEquals("La sala ya está llena.", vm.uiState.value.error)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 10 — clearError
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given error in state when clearError called then error is null`() = runTest {
        // Arrange — trigger an error via failed createSession
        coEvery { createSessionUseCase(any(), any(), any(), any(), any()) } returns
            Result.failure(RuntimeException("Session limit reached for this user"))
        val vm = createViewModel()
        vm.createSession()
        advanceUntilIdle()
        assertNotNull("Pre-condition: error must be set", vm.uiState.value.error)

        // Act
        vm.clearError()

        // Assert
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun `given no error in state when clearError called then state remains clean`() = runTest {
        // Arrange — no operations yet
        val vm = createViewModel()

        // Act
        vm.clearError()

        // Assert — no crash, error remains null
        assertNull(vm.uiState.value.error)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 11 — setGameMode / setPlayerCount pre-session config
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given setPlayerCount with value above 6 then it is clamped to 6`() = runTest {
        // Arrange
        val vm = createViewModel()

        // Act
        vm.setPlayerCount(99)

        // Assert
        assertEquals(6, vm.uiState.value.playerCount)
    }

    @Test
    fun `given setPlayerCount with value below 2 then it is clamped to 2`() = runTest {
        // Arrange
        val vm = createViewModel()

        // Act
        vm.setPlayerCount(0)

        // Assert
        assertEquals(2, vm.uiState.value.playerCount)
    }

    @Test
    fun `given setGameMode called then gameMode is updated in state`() = runTest {
        // Arrange — default is COMMANDER; switch to STANDARD
        val vm = createViewModel()

        // Act
        vm.setGameMode(GameMode.STANDARD)

        // Assert
        assertEquals(GameMode.STANDARD, vm.uiState.value.gameMode)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 12 — Security: error message information leakage
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given createSession fails with internal server error message then user sees generic error not raw details`() = runTest {
        // Arrange — simulate a backend message containing internal infrastructure details
        val internalMessage = "PostgreSQL error 42P01: relation \"sessions\" does not exist"
        coEvery { createSessionUseCase(any(), any(), any(), any(), any()) } returns
            Result.failure(RuntimeException(internalMessage))
        val vm = createViewModel()

        // Act
        vm.createSession()
        advanceUntilIdle()

        // Assert — internal details must not reach the user
        val error = vm.uiState.value.error
        assertEquals(
            "Error fallback must be generic, not the raw server message",
            "Ocurrió un error inesperado.",
            error,
        )
        assertFalse(
            "Raw server message must not be visible to the user",
            error?.contains("PostgreSQL") == true || error?.contains("42P01") == true,
        )
    }

    @Test
    fun `given createSession fails with SQL-like error message then user sees generic error`() = runTest {
        // Arrange — simulate a message that could expose schema information
        val sqlMessage = "ERROR: duplicate key value violates unique constraint on table sessions"
        coEvery { createSessionUseCase(any(), any(), any(), any(), any()) } returns
            Result.failure(RuntimeException(sqlMessage))
        val vm = createViewModel()

        // Act
        vm.createSession()
        advanceUntilIdle()

        // Assert
        assertEquals("Ocurrió un error inesperado.", vm.uiState.value.error)
    }

    @Test
    fun `given resumeSession fails with unrecognised message then user sees generic error not raw message`() = runTest {
        // Arrange
        val internalMessage = "JWT expired: exp claim is in the past"
        val fakeSession = ActiveSession(
            sessionId   = SESSION_ID,
            code        = SESSION_CODE,
            status      = "LOBBY",
            gameMode    = "COMMANDER",
            playerCount = 4,
        )
        coEvery { observeSessionUseCase.getSnapshot(SESSION_ID) } returns
            Result.failure(RuntimeException(internalMessage))
        val vm = createViewModel()

        // Act
        vm.resumeSession(fakeSession)
        advanceUntilIdle()

        // Assert
        assertEquals("Ocurrió un error inesperado.", vm.uiState.value.error)
    }
}
