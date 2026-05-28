package com.mmg.manahub.feature.online.presentation.lobby

import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.online.domain.model.OnlineParticipant
import com.mmg.manahub.core.online.domain.model.OnlineSession
import com.mmg.manahub.core.online.domain.model.OnlineSessionStatus
import com.mmg.manahub.core.online.domain.model.ParticipantStatus
import com.mmg.manahub.core.online.domain.model.SessionEvent
import com.mmg.manahub.core.online.domain.model.SessionPlayerState
import com.mmg.manahub.core.online.domain.model.SessionSnapshot
import com.mmg.manahub.core.online.domain.model.SessionState
import com.mmg.manahub.core.online.domain.repository.OnlineSessionRepository
import com.mmg.manahub.core.online.domain.usecase.JoinSessionUseCase
import com.mmg.manahub.core.online.domain.usecase.LeaveSessionUseCase
import com.mmg.manahub.core.online.domain.usecase.ObserveSessionUseCase
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
 * Unit tests for [LobbyJoinViewModel].
 *
 * GROUP 1:  onCodeChanged — uppercase + truncate to 6 chars
 * GROUP 2:  prefillCode — sets code if not yet joined; no-op if already joined
 * GROUP 3:  joinSession — success path: sessionId, slotIndex, snapshot fetch
 * GROUP 4:  joinSession — no-op if already joined
 * GROUP 5:  joinSession — failure paths with error mapping
 * GROUP 6:  setReady — optimistic update, reverts on failure
 * GROUP 7:  leaveSession — calls use case and navigates back
 * GROUP 8:  handleEvent ParticipantUpdated — correct merge and filter
 * GROUP 9:  handleEvent SessionStatusChanged → ACTIVE triggers onGameStart
 * GROUP 10: clearError — clears the error field
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LobbyJoinViewModelTest {

    // ── Dispatcher ────────────────────────────────────────────────────────────

    private val testDispatcher = StandardTestDispatcher()

    // ── Mocks ─────────────────────────────────────────────────────────────────

    private val joinSessionUseCase       = mockk<JoinSessionUseCase>()
    private val observeSessionUseCase    = mockk<ObserveSessionUseCase>(relaxed = true)
    private val leaveSessionUseCase      = mockk<LeaveSessionUseCase>(relaxed = true)
    private val repository               = mockk<OnlineSessionRepository>(relaxed = true)
    private val userPreferencesDataStore = mockk<UserPreferencesDataStore>(relaxed = true)

    // Shared event flow reused across tests that need to emit realtime events
    private val eventFlow = MutableSharedFlow<SessionEvent>(extraBufferCapacity = 8)

    // ── Constants ─────────────────────────────────────────────────────────────

    companion object {
        const val SESSION_ID   = "session-xyz-999"
        const val SESSION_CODE = "123456"
        const val SLOT_INDEX   = 2
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildParticipant(
        id: String = "p1",
        slotIndex: Int = 0,
        status: ParticipantStatus = ParticipantStatus.JOINED,
    ) = OnlineParticipant(
        id          = id,
        sessionId   = SESSION_ID,
        slotIndex   = slotIndex,
        userId      = "user-$id",
        displayName = "Player $id",
        themeKey    = "Crimson",
        isHost      = false,
        isReady     = false,
        status      = status,
        lastSeenAt  = "2026-01-01T00:00:00Z",
    )

    private fun buildSnapshot(
        mode: String = "COMMANDER",
        playerCount: Int = 4,
    ) = SessionSnapshot(
        session = OnlineSession(
            id             = SESSION_ID,
            code           = SESSION_CODE,
            hostUserId     = "host-user",
            gameMode       = mode,
            playerCount    = playerCount,
            layoutKey      = null,
            status         = OnlineSessionStatus.LOBBY,
            tournamentId   = null,
            tournamentMatchId = null,
            createdAt      = "2026-01-01T00:00:00Z",
            startedAt      = null,
            finishedAt     = null,
            lastActivityAt = "2026-01-01T00:00:00Z",
        ),
        sessionState = SessionState(
            sessionId        = SESSION_ID,
            currentPhase     = "UNTAP",
            activePlayerSlot = 0,
            turnNumber       = 1,
            phaseStops       = emptyMap(),
            lastDiceResult   = null,
            lastCoinResult   = null,
            updatedAt        = "2026-01-01T00:00:00Z",
        ),
        playerStates = emptyList(),
        participants = emptyList(),
    )

    /**
     * Creates the ViewModel.
     * [observeSessionUseCase.invoke] returns [eventFlow] by default.
     * [observeSessionUseCase.getSnapshot] returns a successful snapshot.
     */
    private fun createViewModel(
        snapshotMode: String = "COMMANDER",
        snapshotPlayerCount: Int = 4,
    ): LobbyJoinViewModel {
        coEvery { observeSessionUseCase.invoke(any()) } returns eventFlow
        coEvery { observeSessionUseCase.getSnapshot(any()) } returns
            Result.success(buildSnapshot(snapshotMode, snapshotPlayerCount))
        // init block calls userPreferencesDataStore.playerNameFlow.first()
        coEvery { userPreferencesDataStore.playerNameFlow } returns flowOf("")
        return LobbyJoinViewModel(
            joinSessionUseCase    = joinSessionUseCase,
            observeSessionUseCase = observeSessionUseCase,
            leaveSessionUseCase   = leaveSessionUseCase,
            repository            = repository,
            userPreferencesDataStore = userPreferencesDataStore,
        )
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
    //  GROUP 1 — onCodeChanged
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given mixed input when onCodeChanged then only digits are kept`() = runTest {
        // Arrange
        val vm = createViewModel()

        // Act — letters are stripped, only digits remain
        vm.onCodeChanged("abc123")

        // Assert
        assertEquals("123", vm.uiState.value.codeInput)
    }

    @Test
    fun `given code longer than 6 chars when onCodeChanged then truncated to 6`() = runTest {
        // Arrange
        val vm = createViewModel()

        // Act
        vm.onCodeChanged("123456789")

        // Assert
        assertEquals("123456", vm.uiState.value.codeInput)
    }

    @Test
    fun `given exactly 6 digit code when onCodeChanged then stored as-is`() = runTest {
        // Arrange
        val vm = createViewModel()

        // Act
        vm.onCodeChanged("123456")

        // Assert
        assertEquals("123456", vm.uiState.value.codeInput)
    }

    @Test
    fun `given empty code when onCodeChanged then codeInput is empty`() = runTest {
        // Arrange
        val vm = createViewModel()

        // Act
        vm.onCodeChanged("")

        // Assert
        assertEquals("", vm.uiState.value.codeInput)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 2 — prefillCode
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given not yet joined when prefillCode called then code is set`() = runTest {
        // Arrange — sessionId is null (not yet joined)
        val vm = createViewModel()

        // Act
        vm.prefillCode("abc456")

        // Assert
        assertEquals("ABC456", vm.uiState.value.codeInput)
    }

    @Test
    fun `given prefillCode with code longer than 6 then truncated and uppercased`() = runTest {
        // Arrange
        val vm = createViewModel()

        // Act
        vm.prefillCode("abcdefxyz")

        // Assert
        assertEquals("ABCDEF", vm.uiState.value.codeInput)
    }

    @Test
    fun `given already joined when prefillCode called then code is NOT changed`() = runTest {
        // Arrange — simulate joined state by performing a successful join
        coEvery { joinSessionUseCase(any(), any(), any()) } returns
            Result.success(Pair(SESSION_ID, SLOT_INDEX))
        val vm = createViewModel()
        vm.onCodeChanged(SESSION_CODE)
        vm.joinSession { _, _, _, _ -> }
        advanceUntilIdle()
        val codeBeforePrefill = vm.uiState.value.codeInput

        // Act
        vm.prefillCode("NEWCOD")

        // Assert — code unchanged because session already joined
        assertEquals(codeBeforePrefill, vm.uiState.value.codeInput)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 3 — joinSession success path
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given valid code when joinSession succeeds then sessionId and slotIndex are set`() = runTest {
        // Arrange
        coEvery { joinSessionUseCase(SESSION_CODE, any(), any()) } returns
            Result.success(Pair(SESSION_ID, SLOT_INDEX))
        val vm = createViewModel()
        vm.onCodeChanged(SESSION_CODE)

        // Act
        vm.joinSession { _, _, _, _ -> }
        advanceUntilIdle()

        // Assert
        assertEquals(SESSION_ID, vm.uiState.value.sessionId)
        assertEquals(SLOT_INDEX, vm.uiState.value.slotIndex)
        assertFalse("isLoading must be false after completion", vm.uiState.value.isLoading)
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun `given joinSession succeeds then snapshot is fetched and mode and playerCount are stored`() = runTest {
        // Arrange
        coEvery { joinSessionUseCase(any(), any(), any()) } returns
            Result.success(Pair(SESSION_ID, SLOT_INDEX))
        val vm = createViewModel(snapshotMode = "BRAWL", snapshotPlayerCount = 3)
        vm.onCodeChanged(SESSION_CODE)

        // Act
        vm.joinSession { _, _, _, _ -> }
        advanceUntilIdle()

        // Assert — snapshot values stored in state
        assertEquals("BRAWL", vm.uiState.value.sessionMode)
        assertEquals(3, vm.uiState.value.sessionPlayerCount)
    }

    @Test
    fun `given joinSession succeeds then connects and observes the session`() = runTest {
        // Arrange
        coEvery { joinSessionUseCase(any(), any(), any()) } returns
            Result.success(Pair(SESSION_ID, SLOT_INDEX))
        val vm = createViewModel()
        vm.onCodeChanged(SESSION_CODE)

        // Act
        vm.joinSession { _, _, _, _ -> }
        advanceUntilIdle()

        // Assert
        coVerify { observeSessionUseCase.connect(SESSION_ID) }
        coVerify { observeSessionUseCase.invoke(SESSION_ID) }
    }

    @Test
    fun `given blank displayName when joinSession then uses fallback name Player`() = runTest {
        // Arrange — capture the displayName passed to the use case
        var capturedDisplayName: String? = null
        coEvery { joinSessionUseCase(any(), capture(mutableListOf<String>().also {
            capturedDisplayName = it.firstOrNull()
        }), any()) } coAnswers {
            capturedDisplayName = secondArg()
            Result.success(Pair(SESSION_ID, SLOT_INDEX))
        }
        val vm = createViewModel()
        vm.onCodeChanged(SESSION_CODE)
        // Deliberately leave displayName blank

        // Act
        vm.joinSession { _, _, _, _ -> }
        advanceUntilIdle()

        // Assert — fallback is "Player"
        assertEquals("Player", capturedDisplayName)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 4 — joinSession no-op if already joined
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given already joined when joinSession called again then use case is NOT called a second time`() = runTest {
        // Arrange
        coEvery { joinSessionUseCase(any(), any(), any()) } returns
            Result.success(Pair(SESSION_ID, SLOT_INDEX))
        val vm = createViewModel()
        vm.onCodeChanged(SESSION_CODE)
        vm.joinSession { _, _, _, _ -> }
        advanceUntilIdle()

        // Act — second join attempt
        vm.joinSession { _, _, _, _ -> }
        advanceUntilIdle()

        // Assert — use case invoked exactly once
        coVerify(exactly = 1) { joinSessionUseCase(any(), any(), any()) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 5 — joinSession failure — error mapping
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given joinSession fails with Session is full then error maps to Spanish message`() = runTest {
        // Arrange
        coEvery { joinSessionUseCase(any(), any(), any()) } returns
            Result.failure(RuntimeException("Session is full"))
        val vm = createViewModel()
        vm.onCodeChanged(SESSION_CODE)

        // Act
        vm.joinSession { _, _, _, _ -> }
        advanceUntilIdle()

        // Assert
        assertEquals("La sala ya está llena.", vm.uiState.value.error)
        assertNull("sessionId must be null after failed join", vm.uiState.value.sessionId)
    }

    @Test
    fun `given joinSession fails with Session not found message then error is mapped`() = runTest {
        // Arrange
        coEvery { joinSessionUseCase(any(), any(), any()) } returns
            Result.failure(RuntimeException("Session not found or not in LOBBY status"))
        val vm = createViewModel()
        vm.onCodeChanged(SESSION_CODE)

        // Act
        vm.joinSession { _, _, _, _ -> }
        advanceUntilIdle()

        // Assert
        assertEquals("Sala no encontrada o ya iniciada.", vm.uiState.value.error)
    }

    @Test
    fun `given joinSession fails with Invalid session code format then error is mapped`() = runTest {
        // Arrange — code "AB" is shorter than 6 chars; client-side validation
        // fires before the use case is called and sets the same Spanish error message
        val vm = createViewModel()
        vm.onCodeChanged("ab")

        // Act
        vm.joinSession { _, _, _, _ -> }
        advanceUntilIdle()

        // Assert — error set (either by client validation or mapped from backend)
        assertEquals("Código inválido. Debe ser 6 dígitos.", vm.uiState.value.error)
    }

    @Test
    fun `given joinSession fails with Too many failed join attempts then error is mapped`() = runTest {
        // Arrange
        coEvery { joinSessionUseCase(any(), any(), any()) } returns
            Result.failure(RuntimeException("Too many failed join attempts for this user"))
        val vm = createViewModel()
        vm.onCodeChanged(SESSION_CODE)

        // Act
        vm.joinSession { _, _, _, _ -> }
        advanceUntilIdle()

        // Assert
        assertEquals("Demasiados intentos fallidos. Espera 5 minutos.", vm.uiState.value.error)
    }

    @Test
    fun `given joinSession fails with null message then generic fallback is used`() = runTest {
        // Arrange
        coEvery { joinSessionUseCase(any(), any(), any()) } returns
            Result.failure(RuntimeException())
        val vm = createViewModel()
        vm.onCodeChanged(SESSION_CODE)

        // Act
        vm.joinSession { _, _, _, _ -> }
        advanceUntilIdle()

        // Assert
        assertEquals("Ocurrió un error inesperado.", vm.uiState.value.error)
    }

    @Test
    fun `given joinSession fails then isLoading is false`() = runTest {
        // Arrange
        coEvery { joinSessionUseCase(any(), any(), any()) } returns
            Result.failure(RuntimeException("error"))
        val vm = createViewModel()
        vm.onCodeChanged(SESSION_CODE)

        // Act
        vm.joinSession { _, _, _, _ -> }
        advanceUntilIdle()

        // Assert
        assertFalse(vm.uiState.value.isLoading)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 6 — setReady optimistic update + revert on failure
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given joined session when setReady true called then isReady is optimistically set to true`() = runTest {
        // Arrange — join first
        coEvery { joinSessionUseCase(any(), any(), any()) } returns
            Result.success(Pair(SESSION_ID, SLOT_INDEX))
        coEvery { repository.setReady(SESSION_ID, true) } returns Result.success(Unit)
        val vm = createViewModel()
        vm.onCodeChanged(SESSION_CODE)
        vm.joinSession { _, _, _, _ -> }
        advanceUntilIdle()

        // Act
        vm.setReady(true)
        advanceUntilIdle()

        // Assert — optimistic update applied and backend succeeded
        assertTrue(vm.uiState.value.isReady)
    }

    @Test
    fun `given setReady true when backend fails then isReady is reverted to false`() = runTest {
        // Arrange
        coEvery { joinSessionUseCase(any(), any(), any()) } returns
            Result.success(Pair(SESSION_ID, SLOT_INDEX))
        coEvery { repository.setReady(SESSION_ID, true) } returns
            Result.failure(RuntimeException("Network error"))
        val vm = createViewModel()
        vm.onCodeChanged(SESSION_CODE)
        vm.joinSession { _, _, _, _ -> }
        advanceUntilIdle()

        // Act
        vm.setReady(true)
        advanceUntilIdle()

        // Assert — reverted to false because backend failed
        assertFalse("isReady must be reverted to false after backend failure", vm.uiState.value.isReady)
    }

    @Test
    fun `given setReady true then false when both succeed then isReady is false`() = runTest {
        // Arrange
        coEvery { joinSessionUseCase(any(), any(), any()) } returns
            Result.success(Pair(SESSION_ID, SLOT_INDEX))
        coEvery { repository.setReady(SESSION_ID, any()) } returns Result.success(Unit)
        val vm = createViewModel()
        vm.onCodeChanged(SESSION_CODE)
        vm.joinSession { _, _, _, _ -> }
        advanceUntilIdle()

        // Act
        vm.setReady(true)
        advanceUntilIdle()
        vm.setReady(false)
        advanceUntilIdle()

        // Assert
        assertFalse(vm.uiState.value.isReady)
    }

    @Test
    fun `given setReady false when backend fails then isReady is reverted to true`() = runTest {
        // Arrange — start ready=true, then fail toggling to false
        coEvery { joinSessionUseCase(any(), any(), any()) } returns
            Result.success(Pair(SESSION_ID, SLOT_INDEX))
        coEvery { repository.setReady(SESSION_ID, true) } returns Result.success(Unit)
        coEvery { repository.setReady(SESSION_ID, false) } returns
            Result.failure(RuntimeException("network error"))
        val vm = createViewModel()
        vm.onCodeChanged(SESSION_CODE)
        vm.joinSession { _, _, _, _ -> }
        advanceUntilIdle()

        // Set to true first (succeeds)
        vm.setReady(true)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isReady)

        // Act — toggle to false (fails, must revert)
        vm.setReady(false)
        advanceUntilIdle()

        // Assert — reverted back to true
        assertTrue("isReady must be reverted to true after failed setReady(false)", vm.uiState.value.isReady)
    }

    @Test
    fun `given no session when setReady called then repository is NOT called`() = runTest {
        // Arrange — sessionId is null
        val vm = createViewModel()

        // Act
        vm.setReady(true)
        advanceUntilIdle()

        // Assert
        coVerify(exactly = 0) { repository.setReady(any(), any()) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 7 — leaveSession
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given joined session when leaveSession then LeaveSessionUseCase is called`() = runTest {
        // Arrange
        coEvery { joinSessionUseCase(any(), any(), any()) } returns
            Result.success(Pair(SESSION_ID, SLOT_INDEX))
        val vm = createViewModel()
        vm.onCodeChanged(SESSION_CODE)
        vm.joinSession { _, _, _, _ -> }
        advanceUntilIdle()

        // Act
        vm.leaveSession {}
        advanceUntilIdle()

        // Assert
        coVerify { leaveSessionUseCase(SESSION_ID) }
    }

    @Test
    fun `given joined session when leaveSession then onNavigateBack is invoked`() = runTest {
        // Arrange
        coEvery { joinSessionUseCase(any(), any(), any()) } returns
            Result.success(Pair(SESSION_ID, SLOT_INDEX))
        val vm = createViewModel()
        vm.onCodeChanged(SESSION_CODE)
        vm.joinSession { _, _, _, _ -> }
        advanceUntilIdle()
        var navigated = false

        // Act
        vm.leaveSession { navigated = true }
        advanceUntilIdle()

        // Assert
        assertTrue("onNavigateBack must be invoked after leaveSession", navigated)
    }

    @Test
    fun `given no session when leaveSession then onNavigateBack is invoked without calling use case`() = runTest {
        // Arrange
        val vm = createViewModel()
        var navigated = false

        // Act
        vm.leaveSession { navigated = true }
        advanceUntilIdle()

        // Assert
        assertTrue(navigated)
        coVerify(exactly = 0) { leaveSessionUseCase(any()) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 8 — handleEvent: ParticipantUpdated
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given ParticipantUpdated event then participant is added to list`() = runTest {
        // Arrange
        coEvery { joinSessionUseCase(any(), any(), any()) } returns
            Result.success(Pair(SESSION_ID, SLOT_INDEX))
        val vm = createViewModel()
        vm.onCodeChanged(SESSION_CODE)
        vm.joinSession { _, _, _, _ -> }
        advanceUntilIdle()

        // Act
        val participant = buildParticipant("p1", slotIndex = 0)
        eventFlow.emit(SessionEvent.ParticipantUpdated(participant))
        advanceUntilIdle()

        // Assert
        assertEquals(1, vm.uiState.value.participants.size)
        assertEquals("p1", vm.uiState.value.participants.first().id)
    }

    @Test
    fun `given existing participant receives update then replaced not duplicated`() = runTest {
        // Arrange
        coEvery { joinSessionUseCase(any(), any(), any()) } returns
            Result.success(Pair(SESSION_ID, SLOT_INDEX))
        val vm = createViewModel()
        vm.onCodeChanged(SESSION_CODE)
        vm.joinSession { _, _, _, _ -> }
        advanceUntilIdle()

        val p1 = buildParticipant("p1", slotIndex = 0)
        eventFlow.emit(SessionEvent.ParticipantUpdated(p1))
        advanceUntilIdle()

        // Act — emit update
        eventFlow.emit(SessionEvent.ParticipantUpdated(p1.copy(displayName = "Updated Name")))
        advanceUntilIdle()

        // Assert — only one participant, with updated name
        assertEquals(1, vm.uiState.value.participants.size)
        assertEquals("Updated Name", vm.uiState.value.participants.first().displayName)
    }

    @Test
    fun `given ParticipantUpdated with LEFT status then participant is removed`() = runTest {
        // Arrange
        coEvery { joinSessionUseCase(any(), any(), any()) } returns
            Result.success(Pair(SESSION_ID, SLOT_INDEX))
        val vm = createViewModel()
        vm.onCodeChanged(SESSION_CODE)
        vm.joinSession { _, _, _, _ -> }
        advanceUntilIdle()

        val p1 = buildParticipant("p1", slotIndex = 0, status = ParticipantStatus.JOINED)
        eventFlow.emit(SessionEvent.ParticipantUpdated(p1))
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.participants.size)

        // Act
        eventFlow.emit(SessionEvent.ParticipantUpdated(p1.copy(status = ParticipantStatus.LEFT)))
        advanceUntilIdle()

        // Assert
        assertTrue(
            "LEFT participant must be removed from the join lobby list",
            vm.uiState.value.participants.isEmpty(),
        )
    }

    @Test
    fun `given multiple participants events then sorted by slotIndex`() = runTest {
        // Arrange
        coEvery { joinSessionUseCase(any(), any(), any()) } returns
            Result.success(Pair(SESSION_ID, SLOT_INDEX))
        val vm = createViewModel()
        vm.onCodeChanged(SESSION_CODE)
        vm.joinSession { _, _, _, _ -> }
        advanceUntilIdle()

        // Act — emit out of order
        eventFlow.emit(SessionEvent.ParticipantUpdated(buildParticipant("p3", slotIndex = 2)))
        eventFlow.emit(SessionEvent.ParticipantUpdated(buildParticipant("p1", slotIndex = 0)))
        eventFlow.emit(SessionEvent.ParticipantUpdated(buildParticipant("p2", slotIndex = 1)))
        advanceUntilIdle()

        // Assert
        val slots = vm.uiState.value.participants.map { it.slotIndex }
        assertEquals(listOf(0, 1, 2), slots)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 9 — handleEvent: SessionStatusChanged → ACTIVE triggers onGameStart
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given joined session when SessionStatusChanged to ACTIVE then onGameStart is called with correct params`() = runTest {
        // Arrange
        coEvery { joinSessionUseCase(any(), any(), any()) } returns
            Result.success(Pair(SESSION_ID, SLOT_INDEX))
        val vm = createViewModel(snapshotMode = "COMMANDER", snapshotPlayerCount = 4)
        vm.onCodeChanged(SESSION_CODE)

        var capturedSessionId: String? = null
        var capturedSlot: Int? = null
        var capturedMode: String? = null
        var capturedPlayerCount: Int? = null

        vm.joinSession { sid, slot, mode, count ->
            capturedSessionId   = sid
            capturedSlot        = slot
            capturedMode        = mode
            capturedPlayerCount = count
        }
        advanceUntilIdle()

        // Act — emit ACTIVE status
        eventFlow.emit(SessionEvent.SessionStatusChanged(OnlineSessionStatus.ACTIVE))
        advanceUntilIdle()

        // Assert
        assertEquals(SESSION_ID, capturedSessionId)
        assertEquals(SLOT_INDEX, capturedSlot)
        assertEquals("COMMANDER", capturedMode)
        assertEquals(4, capturedPlayerCount)
    }

    @Test
    fun `given SessionStatusChanged to LOBBY then sessionStatus is updated but onGameStart is NOT called`() = runTest {
        // Arrange
        coEvery { joinSessionUseCase(any(), any(), any()) } returns
            Result.success(Pair(SESSION_ID, SLOT_INDEX))
        val vm = createViewModel()
        vm.onCodeChanged(SESSION_CODE)
        var gameStartCount = 0
        vm.joinSession { _, _, _, _ -> gameStartCount++ }
        advanceUntilIdle()

        // Act — non-ACTIVE status change
        eventFlow.emit(SessionEvent.SessionStatusChanged(OnlineSessionStatus.LOBBY))
        advanceUntilIdle()

        // Assert — status updated but onGameStart not invoked
        assertEquals(OnlineSessionStatus.LOBBY, vm.uiState.value.sessionStatus)
        assertEquals(0, gameStartCount)
    }

    @Test
    fun `given SessionEvent Error after join then error is mapped and set in state`() = runTest {
        // Arrange
        coEvery { joinSessionUseCase(any(), any(), any()) } returns
            Result.success(Pair(SESSION_ID, SLOT_INDEX))
        val vm = createViewModel()
        vm.onCodeChanged(SESSION_CODE)
        vm.joinSession { _, _, _, _ -> }
        advanceUntilIdle()

        // Act
        eventFlow.emit(SessionEvent.Error("Too many failed join attempts: user blocked"))
        advanceUntilIdle()

        // Assert
        assertEquals("Demasiados intentos fallidos. Espera 5 minutos.", vm.uiState.value.error)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 10 — clearError
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given error in state when clearError called then error is null`() = runTest {
        // Arrange — trigger an error via failed join
        coEvery { joinSessionUseCase(any(), any(), any()) } returns
            Result.failure(RuntimeException("Session is full"))
        val vm = createViewModel()
        vm.onCodeChanged(SESSION_CODE)
        vm.joinSession { _, _, _, _ -> }
        advanceUntilIdle()
        assertNotNull("Pre-condition: error must be set", vm.uiState.value.error)

        // Act
        vm.clearError()

        // Assert
        assertNull(vm.uiState.value.error)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 11 — Security: input validation and information leakage
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given code with special character when joinSession then rejected client-side without network call`() = runTest {
        // Arrange — prefillCode with a non-alphanumeric character at position 3
        // (e.g. injected via deep link before client-side validation was added)
        val vm = createViewModel()
        vm.prefillCode("AB!C1D") // '!' is non-alphanumeric

        // Act
        vm.joinSession { _, _, _, _ -> }
        advanceUntilIdle()

        // Assert — validation must reject it before hitting the use case
        coVerify(exactly = 0) { joinSessionUseCase(any(), any(), any()) }
        assertEquals(
            "Client-side validation must fire for non-alphanumeric code",
            "Código inválido. Debe ser 6 dígitos.",
            vm.uiState.value.error,
        )
    }

    @Test
    fun `given empty code when joinSession then rejected client-side without network call`() = runTest {
        // Arrange — codeInput is empty (length 0, fails the length == 6 check)
        val vm = createViewModel()
        // Do not call onCodeChanged — codeInput stays ""

        // Act
        vm.joinSession { _, _, _, _ -> }
        advanceUntilIdle()

        // Assert
        coVerify(exactly = 0) { joinSessionUseCase(any(), any(), any()) }
        assertEquals(
            "Código inválido. Debe ser 6 dígitos.",
            vm.uiState.value.error,
        )
    }

    @Test
    fun `given code shorter than 6 chars when joinSession then rejected client-side`() = runTest {
        // Arrange
        val vm = createViewModel()
        vm.onCodeChanged("AB12") // 4 chars

        // Act
        vm.joinSession { _, _, _, _ -> }
        advanceUntilIdle()

        // Assert
        coVerify(exactly = 0) { joinSessionUseCase(any(), any(), any()) }
        assertEquals(
            "Código inválido. Debe ser 6 dígitos.",
            vm.uiState.value.error,
        )
    }

    @Test
    fun `given code with only digits when joinSession then accepted and use case is called`() = runTest {
        // Arrange — all-digit codes are valid alphanumeric codes
        coEvery { joinSessionUseCase("123456", any(), any()) } returns
            Result.success(Pair(SESSION_ID, SLOT_INDEX))
        val vm = createViewModel()
        vm.onCodeChanged("123456")

        // Act
        vm.joinSession { _, _, _, _ -> }
        advanceUntilIdle()

        // Assert — valid code must reach the use case
        coVerify { joinSessionUseCase("123456", any(), any()) }
    }

    @Test
    fun `given prefillCode with URL-encoded injection when joinSession then rejected`() = runTest {
        // Arrange — simulate a deep link that injects a percent-encoded payload.
        // After uppercase + take(6), "%2FABX" is 6 chars but contains '%' which is non-alphanumeric.
        val vm = createViewModel()
        vm.prefillCode("%2FABX")

        // Act
        vm.joinSession { _, _, _, _ -> }
        advanceUntilIdle()

        // Assert
        coVerify(exactly = 0) { joinSessionUseCase(any(), any(), any()) }
        assertEquals(
            "Código inválido. Debe ser 6 dígitos.",
            vm.uiState.value.error,
        )
    }

    @Test
    fun `given joinSession fails with unrecognised server message then user sees generic error not raw details`() = runTest {
        // Arrange — an unrecognised backend message must not reach the user verbatim
        val internalMessage = "JWT malformed: expected 3 segments, got 1"
        coEvery { joinSessionUseCase(any(), any(), any()) } returns
            Result.failure(RuntimeException(internalMessage))
        val vm = createViewModel()
        vm.onCodeChanged(SESSION_CODE)

        // Act
        vm.joinSession { _, _, _, _ -> }
        advanceUntilIdle()

        // Assert — raw server details must not be visible to the user
        val error = vm.uiState.value.error
        assertEquals("Ocurrió un error inesperado.", error)
        assertFalse(
            "Raw server message must not appear in the user-visible error",
            error?.contains("JWT") == true,
        )
    }

    @Test
    fun `given displayName at max 32 chars when joinSession then accepted and forwarded unchanged`() = runTest {
        // Arrange — 32-char name is the boundary value; must be stored intact and forwarded
        val maxName = "A".repeat(32)
        coEvery { joinSessionUseCase(SESSION_CODE, maxName, any()) } returns
            Result.success(Pair(SESSION_ID, SLOT_INDEX))
        val vm = createViewModel()
        vm.onCodeChanged(SESSION_CODE)
        vm.onDisplayNameChanged(maxName)

        // Act
        vm.joinSession { _, _, _, _ -> }
        advanceUntilIdle()

        // Assert — 32-char name passes through to the use case unchanged
        coVerify { joinSessionUseCase(SESSION_CODE, maxName, any()) }
    }

    @Test
    fun `given displayName beyond 32 chars when onDisplayNameChanged then truncated to 32`() = runTest {
        // Arrange — onDisplayNameChanged caps at 32; verify the stored value is never longer
        val longName = "B".repeat(50)
        val vm = createViewModel()

        // Act
        vm.onDisplayNameChanged(longName)

        // Assert
        assertEquals(
            "displayName must be capped at 32 characters",
            32,
            vm.uiState.value.displayName.length,
        )
    }
}
