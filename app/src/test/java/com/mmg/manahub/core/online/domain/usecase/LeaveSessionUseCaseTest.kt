package com.mmg.manahub.core.online.domain.usecase

import com.mmg.manahub.core.online.domain.repository.OnlineSessionRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * Unit tests for [LeaveSessionUseCase].
 *
 * The RPC must run BEFORE the channel is torn down (a disconnected client cannot leave), be retried
 * once, and the local disconnect must happen even when the RPC ultimately fails.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LeaveSessionUseCaseTest {

    private val repository = mockk<OnlineSessionRepository>(relaxed = true)
    private val useCase = LeaveSessionUseCase(repository)

    private companion object {
        const val SESSION_ID = "session-1"
    }

    @Test
    fun `given the RPC succeeds when leaving then it is called once and realtime is disconnected`() = runTest {
        coEvery { repository.leaveSession(SESSION_ID, null) } returns Result.success(Unit)

        val result = useCase(SESSION_ID)

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { repository.leaveSession(SESSION_ID, null) }
        coVerify(exactly = 1) { repository.disconnectRealtime(SESSION_ID) }
    }

    @Test
    fun `given the first attempt fails when leaving then it is retried once and succeeds`() = runTest {
        coEvery { repository.leaveSession(SESSION_ID, null) } returnsMany listOf(
            Result.failure(IOException("offline")),
            Result.success(Unit),
        )

        val result = useCase(SESSION_ID)

        assertTrue(result.isSuccess)
        coVerify(exactly = 2) { repository.leaveSession(SESSION_ID, null) }
    }

    @Test
    fun `given both attempts fail when leaving then realtime still disconnects and failure is returned`() = runTest {
        coEvery { repository.leaveSession(SESSION_ID, null) } returns Result.failure(IOException("offline"))

        val result = useCase(SESSION_ID)

        assertTrue(result.isFailure)
        coVerify(exactly = 2) { repository.leaveSession(SESSION_ID, null) }
        coVerify(exactly = 1) { repository.disconnectRealtime(SESSION_ID) }
    }

    @Test
    fun `given the RPC throws when leaving then realtime is still disconnected`() = runTest {
        coEvery { repository.leaveSession(SESSION_ID, null) } throws IllegalStateException("boom")

        runCatching { useCase(SESSION_ID) }

        coVerify(exactly = 1) { repository.disconnectRealtime(SESSION_ID) }
    }

    @Test
    fun `given a guest token when leaving then it is forwarded to the RPC`() = runTest {
        coEvery { repository.leaveSession(SESSION_ID, "guest-1") } returns Result.success(Unit)

        useCase(SESSION_ID, "guest-1")

        coVerify(exactly = 1) { repository.leaveSession(SESSION_ID, "guest-1") }
    }
}
