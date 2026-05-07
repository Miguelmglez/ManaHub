package com.mmg.manahub.feature.trades.data.repository

import app.cash.turbine.test
import com.mmg.manahub.feature.trades.data.remote.TradesRemoteDataSource
import com.mmg.manahub.feature.trades.data.remote.dto.TradeItemDto
import com.mmg.manahub.feature.trades.data.remote.dto.TradeProposalDto
import com.mmg.manahub.feature.trades.domain.model.TradeError
import com.mmg.manahub.feature.trades.domain.model.TradeStatus
import com.mmg.manahub.feature.trades.domain.repository.ReviewFlags
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [TradesRepositoryImpl].
 *
 * All network calls are mocked via [TradesRemoteDataSource].
 * The repository exposes an in-process [MutableStateFlow] cache — no Room involved.
 *
 * Covers:
 *  - GROUP 1: observeActiveProposals — filters to isActive only
 *  - GROUP 2: observeProposalHistory — emits all cached proposals
 *  - GROUP 3: observeProposalThread — filters by rootProposalId
 *  - GROUP 4: refreshProposals — success and failure paths
 *  - GROUP 5: State-machine delegation (sendProposal, cancelProposal, acceptProposal,
 *             revokeAcceptance, markCompleted)
 *  - GROUP 6: Error propagation — CardAlreadyLocked and ProposalVersionMismatch
 */
class TradesRepositoryImplTest {

    // ── Mocks ─────────────────────────────────────────────────────────────────

    private val remote = mockk<TradesRemoteDataSource>(relaxed = true)

    private lateinit var repository: TradesRepositoryImpl

    // ── Constants ─────────────────────────────────────────────────────────────

    private val USER_ID = "user-uuid-001"

    // ── Fixture helpers ───────────────────────────────────────────────────────

    /**
     * Builds a minimal [TradeProposalDto] that [TradesRepositoryImpl.refreshProposals]
     * can map without crashing. All optional fields default to null.
     */
    private fun buildProposalDto(
        id: String = "proposal-id-001",
        status: String = "PROPOSED",
        proposerId: String = USER_ID,
        receiverId: String = "receiver-uuid-002",
        rootProposalId: String = "proposal-id-001",
        parentProposalId: String? = null,
        proposalVersion: Int = 1,
    ) = TradeProposalDto(
        id = id,
        status = status,
        proposerId = proposerId,
        receiverId = receiverId,
        parentProposalId = parentProposalId,
        rootProposalId = rootProposalId,
        proposalVersion = proposalVersion,
        includesReviewCollectionFromProposer = false,
        includesReviewCollectionFromReceiver = false,
        proposerMarkedCompletedAt = null,
        receiverMarkedCompletedAt = null,
        cancellationReason = null,
        createdAt = "2024-01-01T00:00:00Z",
        updatedAt = "2024-01-01T00:00:00Z",
    )

    // ── Setup ─────────────────────────────────────────────────────────────────

    @Before
    fun setUp() {
        repository = TradesRepositoryImpl(remote)

        // Default: fetchProposalItems returns empty list for any proposal id
        coEvery { remote.fetchProposalItems(any()) } returns Result.success(emptyList<TradeItemDto>())
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 1 — observeActiveProposals
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given cache with mixed statuses when observeActiveProposals then only active proposals are emitted`() = runTest {
        // Arrange: populate cache with one active (PROPOSED) and one terminal (CANCELLED)
        val activeDto = buildProposalDto(id = "active-001", status = "PROPOSED")
        val terminalDto = buildProposalDto(id = "terminal-001", status = "CANCELLED")
        coEvery { remote.fetchProposals(USER_ID) } returns Result.success(listOf(activeDto, terminalDto))

        repository.refreshProposals(USER_ID)

        // Act + Assert
        repository.observeActiveProposals().test {
            val items = awaitItem()
            assertEquals(1, items.size)
            assertEquals("active-001", items.first().id)
            assertTrue(items.first().status.isActive)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given cache with all terminal proposals when observeActiveProposals then emits empty list`() = runTest {
        // Arrange
        val statuses = listOf("CANCELLED", "DECLINED", "COUNTERED", "COMPLETED", "REVOKED")
        val dtos = statuses.mapIndexed { i, s -> buildProposalDto(id = "t-$i", status = s) }
        coEvery { remote.fetchProposals(USER_ID) } returns Result.success(dtos)
        repository.refreshProposals(USER_ID)

        // Act + Assert
        repository.observeActiveProposals().test {
            val items = awaitItem()
            assertTrue("Expected empty list, got: $items", items.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given empty cache when observeActiveProposals then emits empty list immediately`() = runTest {
        // Cache starts empty — no refresh called
        repository.observeActiveProposals().test {
            val items = awaitItem()
            assertTrue(items.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given cache refreshed twice when observeActiveProposals then latest value is reflected`() = runTest {
        // First refresh: PROPOSED proposal
        val proposedDto = buildProposalDto(id = "p-001", status = "PROPOSED")
        coEvery { remote.fetchProposals(USER_ID) } returns Result.success(listOf(proposedDto))
        repository.refreshProposals(USER_ID)

        // Second refresh: same proposal now ACCEPTED
        val acceptedDto = buildProposalDto(id = "p-001", status = "ACCEPTED")
        coEvery { remote.fetchProposals(USER_ID) } returns Result.success(listOf(acceptedDto))
        repository.refreshProposals(USER_ID)

        repository.observeActiveProposals().test {
            val items = awaitItem()
            assertEquals(TradeStatus.ACCEPTED, items.first().status)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 2 — observeProposalHistory
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given cache with active and terminal proposals when observeProposalHistory then all are emitted`() = runTest {
        // Arrange
        val active = buildProposalDto(id = "a-001", status = "PROPOSED")
        val terminal = buildProposalDto(id = "t-001", status = "COMPLETED")
        coEvery { remote.fetchProposals(USER_ID) } returns Result.success(listOf(active, terminal))
        repository.refreshProposals(USER_ID)

        // Act + Assert
        repository.observeProposalHistory().test {
            val items = awaitItem()
            assertEquals(2, items.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given empty cache when observeProposalHistory then emits empty list immediately`() = runTest {
        repository.observeProposalHistory().test {
            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 3 — observeProposalThread
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given cache with proposals belonging to different threads when observeProposalThread then filters correctly`() = runTest {
        // Arrange: two proposals share root "root-A", one belongs to "root-B"
        val threadA1 = buildProposalDto(id = "a-1", rootProposalId = "root-A")
        val threadA2 = buildProposalDto(id = "a-2", rootProposalId = "root-A", parentProposalId = "a-1")
        val threadB1 = buildProposalDto(id = "b-1", rootProposalId = "root-B")
        coEvery { remote.fetchProposals(USER_ID) } returns Result.success(listOf(threadA1, threadA2, threadB1))
        repository.refreshProposals(USER_ID)

        // Act + Assert
        repository.observeProposalThread("root-A").test {
            val items = awaitItem()
            assertEquals(2, items.size)
            assertTrue(items.all { it.rootProposalId == "root-A" })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given cache when observeProposalThread with unknown rootId then emits empty list`() = runTest {
        val dto = buildProposalDto(id = "p-001", rootProposalId = "root-A")
        coEvery { remote.fetchProposals(USER_ID) } returns Result.success(listOf(dto))
        repository.refreshProposals(USER_ID)

        repository.observeProposalThread("root-UNKNOWN").test {
            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given single proposal where rootProposalId equals its own id when observeProposalThread then that proposal is included`() = runTest {
        // Root proposal: its rootProposalId == its own id
        val root = buildProposalDto(id = "root-001", rootProposalId = "root-001")
        coEvery { remote.fetchProposals(USER_ID) } returns Result.success(listOf(root))
        repository.refreshProposals(USER_ID)

        repository.observeProposalThread("root-001").test {
            val items = awaitItem()
            assertEquals(1, items.size)
            assertEquals("root-001", items.first().id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 4 — refreshProposals: success and failure paths
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given remote returns proposals when refreshProposals succeeds then cache is populated`() = runTest {
        // Arrange
        val dto1 = buildProposalDto(id = "p-001")
        val dto2 = buildProposalDto(id = "p-002")
        coEvery { remote.fetchProposals(USER_ID) } returns Result.success(listOf(dto1, dto2))

        // Act
        val result = repository.refreshProposals(USER_ID)

        // Assert
        assertTrue(result.isSuccess)
        repository.observeProposalHistory().test {
            assertEquals(2, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given remote fetchProposals fails when refreshProposals then returns Result failure without crashing`() = runTest {
        // Arrange
        coEvery { remote.fetchProposals(USER_ID) } returns Result.failure(RuntimeException("503"))

        // Act
        val result = repository.refreshProposals(USER_ID)

        // Assert
        assertTrue(result.isFailure)
    }

    @Test
    fun `given remote fetchProposals fails when refreshProposals then cache retains previous value`() = runTest {
        // Arrange: first populate the cache
        val dto = buildProposalDto(id = "p-001")
        coEvery { remote.fetchProposals(USER_ID) } returns Result.success(listOf(dto))
        repository.refreshProposals(USER_ID)

        // Now the remote fails
        coEvery { remote.fetchProposals(USER_ID) } returns Result.failure(RuntimeException("network error"))
        repository.refreshProposals(USER_ID)

        // Cache must still hold the previous proposal
        repository.observeProposalHistory().test {
            assertEquals(1, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given fetchProposals returns dto with invalid status string when refreshProposals then status defaults to DRAFT`() = runTest {
        // toDomain() uses runCatching { TradeStatus.valueOf(status) }.getOrDefault(TradeStatus.DRAFT)
        val dto = buildProposalDto(id = "p-001", status = "TOTALLY_UNKNOWN_STATUS")
        coEvery { remote.fetchProposals(USER_ID) } returns Result.success(listOf(dto))

        repository.refreshProposals(USER_ID)

        repository.observeProposalHistory().test {
            val items = awaitItem()
            assertEquals(TradeStatus.DRAFT, items.first().status)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 5 — State-machine delegation
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given remote sendProposal succeeds when sendProposal then returns Result success`() = runTest {
        coEvery { remote.sendProposal("p-001") } returns Result.success(Unit)

        val result = repository.sendProposal("p-001")

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { remote.sendProposal("p-001") }
    }

    @Test
    fun `given remote cancelProposal succeeds when cancelProposal then returns Result success`() = runTest {
        coEvery { remote.cancelProposal("p-001") } returns Result.success(Unit)

        val result = repository.cancelProposal("p-001")

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { remote.cancelProposal("p-001") }
    }

    @Test
    fun `given remote acceptProposal succeeds when acceptProposal then returns Result success`() = runTest {
        coEvery { remote.acceptProposal("p-001") } returns Result.success(Unit)

        val result = repository.acceptProposal("p-001")

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { remote.acceptProposal("p-001") }
    }

    @Test
    fun `given remote revokeAcceptance succeeds when revokeAcceptance then returns Result success`() = runTest {
        coEvery { remote.revokeAcceptance("p-001") } returns Result.success(Unit)

        val result = repository.revokeAcceptance("p-001")

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { remote.revokeAcceptance("p-001") }
    }

    @Test
    fun `given remote markCompleted succeeds when markCompleted then returns Result success`() = runTest {
        coEvery { remote.markCompleted("p-001") } returns Result.success(Unit)

        val result = repository.markCompleted("p-001")

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { remote.markCompleted("p-001") }
    }

    @Test
    fun `given remote sendProposal fails when sendProposal then Result failure is returned`() = runTest {
        coEvery { remote.sendProposal(any()) } returns Result.failure(RuntimeException("network error"))

        val result = repository.sendProposal("p-001")

        assertTrue(result.isFailure)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 6 — Error propagation
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given remote acceptProposal returns CardAlreadyLocked when acceptProposal then error propagates to caller`() = runTest {
        // Arrange
        val lockedError = TradeError.CardAlreadyLocked(listOf("card-uuid-1", "card-uuid-2"))
        coEvery { remote.acceptProposal("p-001") } returns Result.failure(lockedError)

        // Act
        val result = repository.acceptProposal("p-001")

        // Assert
        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue("Expected CardAlreadyLocked", exception is TradeError.CardAlreadyLocked)
        assertEquals(listOf("card-uuid-1", "card-uuid-2"), (exception as TradeError.CardAlreadyLocked).cardIds)
    }

    @Test
    fun `given remote editProposal returns ProposalVersionMismatch when editProposal then error propagates to caller`() = runTest {
        // Arrange
        coEvery {
            remote.editProposal(
                proposalId = "p-001",
                expectedVersion = 2,
                newItems = emptyList(),
                reviewFlags = any(),
            )
        } returns Result.failure(TradeError.ProposalVersionMismatch)

        // Act
        val result = repository.editProposal(
            proposalId = "p-001",
            expectedVersion = 2,
            newItems = emptyList(),
            newReviewFlags = ReviewFlags(fromProposer = false, fromReceiver = false),
        )

        // Assert
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is TradeError.ProposalVersionMismatch)
    }

    @Test
    fun `given remote cancelProposal returns Unauthorized when cancelProposal then error propagates`() = runTest {
        coEvery { remote.cancelProposal("p-001") } returns Result.failure(TradeError.Unauthorized)

        val result = repository.cancelProposal("p-001")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is TradeError.Unauthorized)
    }
}
