package com.mmg.manahub.core.online.data.repository

import com.mmg.manahub.core.online.data.remote.OnlineSessionRemoteDataSource
import com.mmg.manahub.core.online.data.remote.SupabaseRealtimeClient
import com.mmg.manahub.core.online.data.remote.dto.CreateSessionResponseDto
import com.mmg.manahub.core.online.data.remote.dto.JoinSessionResponseDto
import com.mmg.manahub.core.online.data.remote.dto.OnlineSessionDto
import com.mmg.manahub.core.online.data.remote.dto.SessionSnapshotDto
import com.mmg.manahub.core.online.data.remote.dto.SessionStateDto
import com.mmg.manahub.core.online.domain.model.SessionEvent
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [OnlineSessionRepositoryImpl].
 *
 * GROUP 1: createSession — delegates to remote, maps DTO to domain Pair
 * GROUP 2: joinSession — delegates to remote, maps DTO to domain Pair
 * GROUP 3: leaveSession — delegates to remote, returns Unit
 * GROUP 4: setReady — delegates to remote
 * GROUP 5: startSession — delegates to remote
 * GROUP 6: getSnapshot — delegates to remote, calls toDomain()
 * GROUP 7: observeSession — delegates to realtime client
 * GROUP 8: Edge cases — network failures propagate as Result.failure
 */
class OnlineSessionRepositoryImplTest {

    // ── Mocks ─────────────────────────────────────────────────────────────────

    private val remoteDataSource  = mockk<OnlineSessionRemoteDataSource>()
    private val realtimeClient    = mockk<SupabaseRealtimeClient>(relaxed = true)

    // ── SUT ───────────────────────────────────────────────────────────────────

    private lateinit var repository: OnlineSessionRepositoryImpl

    // ── Constants ─────────────────────────────────────────────────────────────

    companion object {
        const val SESSION_ID   = "session-test-abc"
        const val SESSION_CODE = "TEST01"
        const val SLOT_INDEX   = 1
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildSnapshotDto() = SessionSnapshotDto(
        session = OnlineSessionDto(
            id             = SESSION_ID,
            code           = SESSION_CODE,
            hostUserId     = "host-user",
            gameMode       = "COMMANDER",
            playerCount    = 4,
            layoutKey      = null,
            status         = "LOBBY",
            tournamentId   = null,
            tournamentMatchId = null,
            createdAt      = "2026-01-01T00:00:00Z",
            startedAt      = null,
            finishedAt     = null,
            lastActivityAt = "2026-01-01T00:00:00Z",
        ),
        sessionState = SessionStateDto(
            sessionId        = SESSION_ID,
            currentPhase     = "UNTAP",
            activePlayerSlot = 0,
            turnNumber       = 1,
            lastDiceResult   = null,
            lastCoinResult   = null,
            updatedAt        = "2026-01-01T00:00:00Z",
        ),
        playerStates = emptyList(),
        participants = emptyList(),
    )

    // ── Setup ─────────────────────────────────────────────────────────────────

    @Before
    fun setUp() {
        repository = OnlineSessionRepositoryImpl(
            remoteDataSource = remoteDataSource,
            realtimeClient   = realtimeClient,
        )
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 1 — createSession
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given remote returns CreateSessionResponseDto when createSession then returns Pair(sessionId, code)`() = runTest {
        // Arrange
        val dto = CreateSessionResponseDto(sessionId = SESSION_ID, code = SESSION_CODE)
        coEvery { remoteDataSource.createSession("COMMANDER", 4, null) } returns Result.success(dto)

        // Act
        val result = repository.createSession("COMMANDER", 4, null)

        // Assert
        assertTrue(result.isSuccess)
        assertEquals(Pair(SESSION_ID, SESSION_CODE), result.getOrThrow())
    }

    @Test
    fun `given remote fails when createSession then Result is failure`() = runTest {
        // Arrange
        coEvery { remoteDataSource.createSession(any(), any(), any()) } returns
            Result.failure(RuntimeException("Session limit reached"))

        // Act
        val result = repository.createSession("STANDARD", 2, null)

        // Assert
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Session limit reached") == true)
    }

    @Test
    fun `given layoutKey provided when createSession then it is forwarded to remote`() = runTest {
        // Arrange
        val dto = CreateSessionResponseDto(sessionId = SESSION_ID, code = SESSION_CODE)
        coEvery { remoteDataSource.createSession("BRAWL", 2, "custom-layout") } returns Result.success(dto)

        // Act
        val result = repository.createSession("BRAWL", 2, "custom-layout")

        // Assert
        assertTrue(result.isSuccess)
        coVerify { remoteDataSource.createSession("BRAWL", 2, "custom-layout") }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 2 — joinSession
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given remote returns JoinSessionResponseDto when joinSession then returns Pair(sessionId, slotIndex)`() = runTest {
        // Arrange
        val dto = JoinSessionResponseDto(sessionId = SESSION_ID, slotIndex = SLOT_INDEX)
        coEvery { remoteDataSource.joinSession(SESSION_CODE, "TestPlayer", "Crimson") } returns
            Result.success(dto)

        // Act
        val result = repository.joinSession(SESSION_CODE, "TestPlayer", "Crimson")

        // Assert
        assertTrue(result.isSuccess)
        assertEquals(Pair(SESSION_ID, SLOT_INDEX), result.getOrThrow())
    }

    @Test
    fun `given remote fails when joinSession then Result is failure`() = runTest {
        // Arrange
        coEvery { remoteDataSource.joinSession(any(), any(), any()) } returns
            Result.failure(RuntimeException("Session is full"))

        // Act
        val result = repository.joinSession("BADCOD", "Player", "Crimson")

        // Assert
        assertTrue(result.isFailure)
    }

    @Test
    fun `given joinSession succeeds then correct parameters are forwarded to remote`() = runTest {
        // Arrange
        val dto = JoinSessionResponseDto(sessionId = SESSION_ID, slotIndex = 0)
        coEvery { remoteDataSource.joinSession("ABCDEF", "Wizard", "NeonVoid") } returns
            Result.success(dto)

        // Act
        repository.joinSession("ABCDEF", "Wizard", "NeonVoid")

        // Assert
        coVerify { remoteDataSource.joinSession("ABCDEF", "Wizard", "NeonVoid") }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 3 — leaveSession
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given remote succeeds when leaveSession then returns Result success of Unit`() = runTest {
        // Arrange
        coEvery { remoteDataSource.leaveSession(SESSION_ID) } returns Result.success(Unit)

        // Act
        val result = repository.leaveSession(SESSION_ID)

        // Assert
        assertTrue(result.isSuccess)
    }

    @Test
    fun `given remote fails when leaveSession then Result is failure`() = runTest {
        // Arrange
        coEvery { remoteDataSource.leaveSession(SESSION_ID) } returns
            Result.failure(RuntimeException("Network timeout"))

        // Act
        val result = repository.leaveSession(SESSION_ID)

        // Assert
        assertTrue(result.isFailure)
    }

    @Test
    fun `given leaveSession called then sessionId is forwarded to remote`() = runTest {
        // Arrange
        coEvery { remoteDataSource.leaveSession(SESSION_ID) } returns Result.success(Unit)

        // Act
        repository.leaveSession(SESSION_ID)

        // Assert
        coVerify { remoteDataSource.leaveSession(SESSION_ID) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 4 — setReady
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given setReady true when remote succeeds then returns Result success`() = runTest {
        // Arrange
        coEvery { remoteDataSource.setReady(SESSION_ID, true) } returns Result.success(Unit)

        // Act
        val result = repository.setReady(SESSION_ID, true)

        // Assert
        assertTrue(result.isSuccess)
    }

    @Test
    fun `given setReady false when remote succeeds then returns Result success`() = runTest {
        // Arrange
        coEvery { remoteDataSource.setReady(SESSION_ID, false) } returns Result.success(Unit)

        // Act
        val result = repository.setReady(SESSION_ID, false)

        // Assert
        assertTrue(result.isSuccess)
    }

    @Test
    fun `given setReady when remote fails then Result is failure`() = runTest {
        // Arrange
        coEvery { remoteDataSource.setReady(any(), any()) } returns
            Result.failure(RuntimeException("Forbidden"))

        // Act
        val result = repository.setReady(SESSION_ID, true)

        // Assert
        assertTrue(result.isFailure)
    }

    @Test
    fun `given setReady called then correct params are forwarded to remote`() = runTest {
        // Arrange
        coEvery { remoteDataSource.setReady(SESSION_ID, true) } returns Result.success(Unit)

        // Act
        repository.setReady(SESSION_ID, true)

        // Assert
        coVerify { remoteDataSource.setReady(SESSION_ID, true) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 5 — startSession
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given remote succeeds when startSession then returns Result success of Unit`() = runTest {
        // Arrange
        coEvery { remoteDataSource.startSession(SESSION_ID) } returns Result.success(Unit)

        // Act
        val result = repository.startSession(SESSION_ID)

        // Assert
        assertTrue(result.isSuccess)
    }

    @Test
    fun `given remote fails when startSession then Result is failure`() = runTest {
        // Arrange
        coEvery { remoteDataSource.startSession(SESSION_ID) } returns
            Result.failure(RuntimeException("Session not found or not in LOBBY status"))

        // Act
        val result = repository.startSession(SESSION_ID)

        // Assert
        assertTrue(result.isFailure)
    }

    @Test
    fun `given startSession called then sessionId is forwarded to remote`() = runTest {
        // Arrange
        coEvery { remoteDataSource.startSession(SESSION_ID) } returns Result.success(Unit)

        // Act
        repository.startSession(SESSION_ID)

        // Assert
        coVerify { remoteDataSource.startSession(SESSION_ID) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 6 — getSnapshot
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given remote returns snapshot DTO when getSnapshot then toDomain is called and domain model returned`() = runTest {
        // Arrange
        val dto = buildSnapshotDto()
        coEvery { remoteDataSource.getSnapshot(SESSION_ID) } returns Result.success(dto)

        // Act
        val result = repository.getSnapshot(SESSION_ID)

        // Assert
        assertTrue(result.isSuccess)
        val snapshot = result.getOrThrow()
        assertEquals(SESSION_ID, snapshot.session.id)
        assertEquals("COMMANDER", snapshot.session.gameMode)
        assertEquals(4, snapshot.session.playerCount)
        assertEquals("UNTAP", snapshot.sessionState.currentPhase)
    }

    @Test
    fun `given remote fails when getSnapshot then Result is failure`() = runTest {
        // Arrange
        coEvery { remoteDataSource.getSnapshot(SESSION_ID) } returns
            Result.failure(RuntimeException("404 Not Found"))

        // Act
        val result = repository.getSnapshot(SESSION_ID)

        // Assert
        assertTrue(result.isFailure)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 7 — observeSession
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given realtimeClient emits events when observeSession then events are forwarded to caller`() = runTest {
        // Arrange
        val event1 = SessionEvent.SessionStatusChanged(
            com.mmg.manahub.core.online.domain.model.OnlineSessionStatus.ACTIVE
        )
        val event2 = SessionEvent.Error("some error")
        every { realtimeClient.observeSession(SESSION_ID) } returns flow {
            emit(event1)
            emit(event2)
        }

        // Act
        val events = repository.observeSession(SESSION_ID).toList()

        // Assert
        assertEquals(2, events.size)
        assertEquals(event1, events[0])
        assertEquals(event2, events[1])
    }

    @Test
    fun `given observeSession called then realtimeClient is used not remoteDataSource`() = runTest {
        // Arrange
        every { realtimeClient.observeSession(SESSION_ID) } returns flow {}

        // Act
        repository.observeSession(SESSION_ID)

        // Assert — realtime client invoked, not the HTTP remote source
        coVerify(exactly = 0) { remoteDataSource.getSnapshot(any()) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 8 — Edge cases: network failures propagate
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given updateLife fails when called then Result is failure`() = runTest {
        // Arrange
        coEvery { remoteDataSource.updateLife(SESSION_ID, 0, 35) } returns
            Result.failure(RuntimeException("403 Forbidden"))

        // Act
        val result = repository.updateLife(SESSION_ID, 0, 35)

        // Assert
        assertTrue(result.isFailure)
    }

    @Test
    fun `given updateCommanderDamage fails then Result is failure`() = runTest {
        // Arrange
        coEvery { remoteDataSource.updateCommanderDamage(any(), any(), any(), any()) } returns
            Result.failure(RuntimeException("500 Internal Server Error"))

        // Act
        val result = repository.updateCommanderDamage(SESSION_ID, 1, 0, 3)

        // Assert
        assertTrue(result.isFailure)
    }

    @Test
    fun `given advancePhase fails then Result is failure`() = runTest {
        // Arrange
        coEvery { remoteDataSource.advancePhase(SESSION_ID) } returns
            Result.failure(RuntimeException("Timeout"))

        // Act
        val result = repository.advancePhase(SESSION_ID)

        // Assert
        assertTrue(result.isFailure)
    }

    @Test
    fun `given nextTurn fails then Result is failure`() = runTest {
        // Arrange
        coEvery { remoteDataSource.nextTurn(SESSION_ID) } returns
            Result.failure(RuntimeException("Connection refused"))

        // Act
        val result = repository.nextTurn(SESSION_ID)

        // Assert
        assertTrue(result.isFailure)
    }

    @Test
    fun `given confirmDefeat succeeds then Result is success`() = runTest {
        // Arrange
        coEvery { remoteDataSource.confirmDefeat(SESSION_ID, 2) } returns Result.success(Unit)

        // Act
        val result = repository.confirmDefeat(SESSION_ID, 2)

        // Assert
        assertTrue(result.isSuccess)
        coVerify { remoteDataSource.confirmDefeat(SESSION_ID, 2) }
    }

    @Test
    fun `given revokeDefeat succeeds then Result is success`() = runTest {
        // Arrange
        coEvery { remoteDataSource.revokeDefeat(SESSION_ID, 1) } returns Result.success(Unit)

        // Act
        val result = repository.revokeDefeat(SESSION_ID, 1)

        // Assert
        assertTrue(result.isSuccess)
        coVerify { remoteDataSource.revokeDefeat(SESSION_ID, 1) }
    }

    @Test
    fun `given connectRealtime called then realtimeClient connect is called`() = runTest {
        // Act
        repository.connectRealtime(SESSION_ID)

        // Assert
        coVerify { realtimeClient.connect(SESSION_ID) }
    }

    @Test
    fun `given disconnectRealtime called then realtimeClient disconnect is called`() = runTest {
        // Act
        repository.disconnectRealtime(SESSION_ID)

        // Assert
        coVerify { realtimeClient.disconnect(SESSION_ID) }
    }
}
