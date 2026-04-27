package com.mmg.manahub.feature.trades.domain.usecase

import com.mmg.manahub.feature.trades.domain.model.TradeError
import com.mmg.manahub.feature.trades.domain.repository.TradesRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [AcceptProposalUseCase].
 *
 * [TradesRepository] is fully mocked — the use case is a thin delegation wrapper.
 *
 * Covers:
 *  - GROUP 1: Successful acceptance
 *  - GROUP 2: CardAlreadyLocked error propagation
 *  - GROUP 3: Other typed trade errors propagate unchanged
 *  - GROUP 4: Generic exception propagation
 */
class AcceptProposalUseCaseTest {

    // ── Mocks ─────────────────────────────────────────────────────────────────

    private val repository = mockk<TradesRepository>()

    private lateinit var useCase: AcceptProposalUseCase

    // ── Setup ─────────────────────────────────────────────────────────────────

    @Before
    fun setUp() {
        useCase = AcceptProposalUseCase(repo = repository)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 1 — Successful acceptance
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given repository acceptProposal succeeds when invoke then returns Result success`() = runTest {
        // Arrange
        coEvery { repository.acceptProposal("proposal-id-001") } returns Result.success(Unit)

        // Act
        val result = useCase("proposal-id-001")

        // Assert
        assertTrue(result.isSuccess)
    }

    @Test
    fun `given repository acceptProposal succeeds when invoke then acceptProposal is called with correct id`() = runTest {
        coEvery { repository.acceptProposal(any()) } returns Result.success(Unit)

        useCase("proposal-id-42")

        coVerify(exactly = 1) { repository.acceptProposal("proposal-id-42") }
    }

    @Test
    fun `given invoke called twice with same id then acceptProposal is called exactly twice`() = runTest {
        coEvery { repository.acceptProposal(any()) } returns Result.success(Unit)

        useCase("proposal-id-001")
        useCase("proposal-id-001")

        coVerify(exactly = 2) { repository.acceptProposal("proposal-id-001") }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 2 — CardAlreadyLocked error propagation
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given repository returns CardAlreadyLocked when invoke then Result failure contains CardAlreadyLocked`() = runTest {
        // Arrange
        val lockedError = TradeError.CardAlreadyLocked(listOf("card-uuid-A", "card-uuid-B"))
        coEvery { repository.acceptProposal("proposal-id-001") } returns Result.failure(lockedError)

        // Act
        val result = useCase("proposal-id-001")

        // Assert
        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue("Expected CardAlreadyLocked, got ${exception?.javaClass?.simpleName}", exception is TradeError.CardAlreadyLocked)
        assertEquals(listOf("card-uuid-A", "card-uuid-B"), (exception as TradeError.CardAlreadyLocked).cardIds)
    }

    @Test
    fun `given repository returns CardAlreadyLocked with empty list when invoke then cardIds is empty`() = runTest {
        val lockedError = TradeError.CardAlreadyLocked(emptyList())
        coEvery { repository.acceptProposal(any()) } returns Result.failure(lockedError)

        val result = useCase("proposal-id-001")

        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(exception is TradeError.CardAlreadyLocked)
        assertTrue((exception as TradeError.CardAlreadyLocked).cardIds.isEmpty())
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 3 — Other typed trade errors propagate unchanged
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given repository returns InvalidStateTransition when invoke then error propagates`() = runTest {
        coEvery { repository.acceptProposal(any()) } returns Result.failure(TradeError.InvalidStateTransition)

        val result = useCase("proposal-id-001")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is TradeError.InvalidStateTransition)
    }

    @Test
    fun `given repository returns Unauthorized when invoke then error propagates`() = runTest {
        coEvery { repository.acceptProposal(any()) } returns Result.failure(TradeError.Unauthorized)

        val result = useCase("proposal-id-001")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is TradeError.Unauthorized)
    }

    @Test
    fun `given repository returns CannotAcceptReviewCollection when invoke then error propagates`() = runTest {
        coEvery { repository.acceptProposal(any()) } returns Result.failure(TradeError.CannotAcceptReviewCollection)

        val result = useCase("proposal-id-001")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is TradeError.CannotAcceptReviewCollection)
    }

    @Test
    fun `given repository returns InventoryGone when invoke then error propagates`() = runTest {
        coEvery { repository.acceptProposal(any()) } returns Result.failure(TradeError.InventoryGone)

        val result = useCase("proposal-id-001")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is TradeError.InventoryGone)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 4 — Generic exception propagation
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given repository acceptProposal returns network exception when invoke then Result failure contains that exception`() = runTest {
        // Arrange
        val networkError = RuntimeException("connection refused")
        coEvery { repository.acceptProposal(any()) } returns Result.failure(networkError)

        // Act
        val result = useCase("proposal-id-001")

        // Assert
        assertTrue(result.isFailure)
        assertEquals("connection refused", result.exceptionOrNull()?.message)
    }
}
