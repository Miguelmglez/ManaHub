package com.mmg.manahub.feature.trades.data.repository

import app.cash.turbine.test
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.core.data.local.dao.CardDao
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.gamification.domain.ProgressionEventBus
import com.mmg.manahub.core.gamification.domain.event.ProgressionEvent
import com.mmg.manahub.core.data.remote.trades.TradesRemoteDataSource
import com.mmg.manahub.core.data.remote.dto.TradeItemDto
import com.mmg.manahub.core.data.remote.dto.TradeProposalDto
import com.mmg.manahub.core.model.TradeError
import com.mmg.manahub.core.model.TradeStatus
import com.mmg.manahub.core.model.ReviewFlags
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.After
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
    private val cardDao = mockk<CardDao>(relaxed = true)
    private val cardRepository = mockk<CardRepository>(relaxed = true)
    private val progressionEventBus = mockk<ProgressionEventBus>(relaxed = true)

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
        // toDomain()'s unknown-status fallback path (§2.6) calls recordSafeNonFatal(), which
        // hits FirebaseCrashlytics.getInstance() outside a runCatching block.
        mockkStatic(FirebaseCrashlytics::class)
        val crashlytics = mockk<FirebaseCrashlytics>(relaxed = true)
        every { FirebaseCrashlytics.getInstance() } returns crashlytics

        repository = TradesRepositoryImpl(remote, cardDao, cardRepository, progressionEventBus)

        // Default: fetchProposalItems returns empty list for any proposal id
        coEvery { remote.fetchProposalItems(any()) } returns Result.success(emptyList<TradeItemDto>())
    }

    @After
    fun tearDown() {
        unmockkStatic(FirebaseCrashlytics::class)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 1 — observeActiveProposals
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given cache with mixed statuses when observeActiveProposals then only active proposals are emitted`() = runTest {
        // Arrange: populate cache with one active (PROPOSED) and one terminal (CANCELLED)
        val activeDto = buildProposalDto(id = "active-001", status = "PROPOSED")
        val terminalDto = buildProposalDto(id = "terminal-001", status = "CANCELLED")
        coEvery { remote.fetchProposals(USER_ID, any()) } returns Result.success(listOf(activeDto, terminalDto))

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
        coEvery { remote.fetchProposals(USER_ID, any()) } returns Result.success(dtos)
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
        coEvery { remote.fetchProposals(USER_ID, any()) } returns Result.success(listOf(proposedDto))
        repository.refreshProposals(USER_ID)

        // Second refresh: same proposal now ACCEPTED
        val acceptedDto = buildProposalDto(id = "p-001", status = "ACCEPTED")
        coEvery { remote.fetchProposals(USER_ID, any()) } returns Result.success(listOf(acceptedDto))
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
    fun `given cache with active and terminal proposals when observeProposalHistory then only the terminal one is emitted`() = runTest {
        // observeProposalHistory() filters to isTerminal proposals only — the active (PROPOSED)
        // one belongs on observeActiveProposals()/observeAllProposals(), not here.
        val active = buildProposalDto(id = "a-001", status = "PROPOSED")
        val terminal = buildProposalDto(id = "t-001", status = "COMPLETED")
        coEvery { remote.fetchProposals(USER_ID, any()) } returns Result.success(listOf(active, terminal))
        repository.refreshProposals(USER_ID)

        // Act + Assert
        repository.observeProposalHistory().test {
            val items = awaitItem()
            assertEquals(1, items.size)
            assertEquals("t-001", items.first().id)
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
        coEvery { remote.fetchProposals(USER_ID, any()) } returns Result.success(listOf(threadA1, threadA2, threadB1))
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
        coEvery { remote.fetchProposals(USER_ID, any()) } returns Result.success(listOf(dto))
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
        coEvery { remote.fetchProposals(USER_ID, any()) } returns Result.success(listOf(root))
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
        // Arrange — both dtos default to status "PROPOSED" (active), so read the unfiltered
        // cache via observeAllProposals() rather than the terminal-only observeProposalHistory().
        val dto1 = buildProposalDto(id = "p-001")
        val dto2 = buildProposalDto(id = "p-002")
        coEvery { remote.fetchProposals(USER_ID, any()) } returns Result.success(listOf(dto1, dto2))

        // Act
        val result = repository.refreshProposals(USER_ID)

        // Assert
        assertTrue(result.isSuccess)
        repository.observeAllProposals().test {
            assertEquals(2, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given remote fetchProposals fails when refreshProposals then returns Result failure without crashing`() = runTest {
        // Arrange
        coEvery { remote.fetchProposals(USER_ID, any()) } returns Result.failure(RuntimeException("503"))

        // Act
        val result = repository.refreshProposals(USER_ID)

        // Assert
        assertTrue(result.isFailure)
    }

    @Test
    fun `given remote fetchProposals fails when refreshProposals then cache retains previous value`() = runTest {
        // Arrange: first populate the cache. Default status "PROPOSED" is active, so read the
        // unfiltered cache via observeAllProposals() rather than the terminal-only
        // observeProposalHistory().
        val dto = buildProposalDto(id = "p-001")
        coEvery { remote.fetchProposals(USER_ID, any()) } returns Result.success(listOf(dto))
        repository.refreshProposals(USER_ID)

        // Now the remote fails
        coEvery { remote.fetchProposals(USER_ID, any()) } returns Result.failure(RuntimeException("network error"))
        repository.refreshProposals(USER_ID)

        // Cache must still hold the previous proposal
        repository.observeAllProposals().test {
            assertEquals(1, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given fetchProposals returns dto with invalid status string when refreshProposals then status defaults to CANCELLED not DRAFT`() = runTest {
        // §2.6 fix: an unrecognised status must NOT fall back to DRAFT — DRAFT is the most
        // permissive state (shows proposer Edit/Cancel). It falls back to the terminal/inert
        // CANCELLED instead, via toTradeStatusOrFallback().
        val dto = buildProposalDto(id = "p-001", status = "TOTALLY_UNKNOWN_STATUS")
        coEvery { remote.fetchProposals(USER_ID, any()) } returns Result.success(listOf(dto))

        repository.refreshProposals(USER_ID)

        repository.observeProposalHistory().test {
            val items = awaitItem()
            assertEquals(TradeStatus.CANCELLED, items.first().status)
            assertTrue(items.first().status.isTerminal)
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

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 7 — refreshItemsForThread (Backend & Performance Optimization plan,
    //            WS4a finding 2, 2026-07-28): item-only fan-out, no metadata re-fetch
    // ══════════════════════════════════════════════════════════════════════════

    private fun tradeItemDto(
        id: String,
        proposalId: String,
        cardId: String = "card-uuid-1",
        fromUserId: String = USER_ID,
        toUserId: String = "receiver-uuid-002",
    ) = TradeItemDto(
        id = id,
        tradeProposalId = proposalId,
        fromUserId = fromUserId,
        toUserId = toUserId,
        cardId = cardId,
    )

    @Test
    fun `given a cached proposal in the thread when refreshItemsForThread then only fetchProposalItems is called never fetchProposals`() =
        runTest {
            // Arrange: populate the cache via refreshProposals first (metadata already fresh).
            val dto = buildProposalDto(id = "p-001", rootProposalId = "p-001")
            coEvery { remote.fetchProposals(USER_ID, any()) } returns Result.success(listOf(dto))
            repository.refreshProposals(USER_ID)

            coEvery { remote.fetchProposalItems("p-001") } returns
                Result.success(listOf(tradeItemDto(id = "item-1", proposalId = "p-001")))

            // Act
            val result = repository.refreshItemsForThread("p-001")

            // Assert
            assertTrue(result.isSuccess)
            coVerify(exactly = 1) { remote.fetchProposalItems("p-001") }
            // The whole point of refreshItemsForThread: it must NOT re-fetch proposal metadata --
            // fetchProposals is only ever called once, by the setup refreshProposals() above.
            coVerify(exactly = 1) { remote.fetchProposals(USER_ID, any()) }
        }

    @Test
    fun `given a proposal id not present in the cache when refreshItemsForThread then it is a no-op success`() = runTest {
        // No refreshProposals() call -- the cache starts empty, so no proposal belongs to this thread.
        val result = repository.refreshItemsForThread("unknown-root")

        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { remote.fetchProposalItems(any()) }
    }

    @Test
    fun `given fetched items when refreshItemsForThread then items merge into the existing cache entry by proposal id`() =
        runTest {
            val dto1 = buildProposalDto(id = "p-001", rootProposalId = "p-001")
            val dto2 = buildProposalDto(id = "p-002", rootProposalId = "p-002")
            coEvery { remote.fetchProposals(USER_ID, any()) } returns Result.success(listOf(dto1, dto2))
            repository.refreshProposals(USER_ID)

            coEvery { remote.fetchProposalItems("p-001") } returns
                Result.success(listOf(tradeItemDto(id = "item-1", proposalId = "p-001")))

            repository.refreshItemsForThread("p-001")

            repository.observeAllProposals().test {
                val proposals = awaitItem()
                val p1 = proposals.single { it.id == "p-001" }
                val p2 = proposals.single { it.id == "p-002" }
                assertEquals(1, p1.items.size)
                // p-002 was never part of the refreshed thread -- its items (empty from the
                // refreshProposals() default) must be untouched, not cleared.
                assertTrue(p2.items.isEmpty())
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 8 — item load state (trades audit 2026-09-23, H3)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given items loaded once when a later thread refresh fails to fetch them then prior items are kept and failure is returned`() =
        runTest {
            val dto = buildProposalDto(id = "p-001", rootProposalId = "p-001")
            coEvery { remote.fetchProposals(USER_ID, any()) } returns Result.success(listOf(dto))
            coEvery { remote.fetchProposalItems("p-001") } returns
                Result.success(listOf(tradeItemDto(id = "item-1", proposalId = "p-001")))
            assertTrue(repository.refreshProposalThread("p-001", USER_ID).isSuccess)

            coEvery { remote.fetchProposalItems("p-001") } returns Result.failure(RuntimeException("timeout"))
            val result = repository.refreshProposalThread("p-001", USER_ID)

            assertTrue(result.isFailure)
            repository.observeAllProposals().test {
                val proposal = awaitItem().single()
                assertEquals(1, proposal.items.size)
                assertTrue(proposal.itemsLoaded)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `given items were never loaded when their fetch fails then the proposal reports itemsLoaded false`() = runTest {
        val dto = buildProposalDto(id = "p-001", rootProposalId = "p-001")
        coEvery { remote.fetchProposals(USER_ID, any()) } returns Result.success(listOf(dto))
        coEvery { remote.fetchProposalItems("p-001") } returns Result.failure(RuntimeException("timeout"))

        val result = repository.refreshProposalThread("p-001", USER_ID)

        assertTrue(result.isFailure)
        repository.observeAllProposals().test {
            val proposal = awaitItem().single()
            assertTrue(proposal.items.isEmpty())
            assertEquals(false, proposal.itemsLoaded)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given a metadata-only refresh then a proposal whose items were never fetched is not reported as loaded`() = runTest {
        coEvery { remote.fetchProposals(USER_ID, any()) } returns Result.success(listOf(buildProposalDto(id = "p-001")))

        repository.refreshProposals(USER_ID)

        repository.observeAllProposals().test {
            assertEquals(false, awaitItem().single().itemsLoaded)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given an item fetch failure when refreshItemsForThread then failure is returned and the cache entry is untouched`() =
        runTest {
            coEvery { remote.fetchProposals(USER_ID, any()) } returns
                Result.success(listOf(buildProposalDto(id = "p-001", rootProposalId = "p-001")))
            repository.refreshProposals(USER_ID)
            coEvery { remote.fetchProposalItems("p-001") } returns Result.failure(RuntimeException("timeout"))

            val result = repository.refreshItemsForThread("p-001")

            assertTrue(result.isFailure)
            repository.observeAllProposals().test {
                assertEquals(false, awaitItem().single().itemsLoaded)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `given a thread refresh then only that thread is fetched and other threads stay cached`() = runTest {
        coEvery { remote.fetchProposals(USER_ID, null) } returns Result.success(
            listOf(buildProposalDto(id = "a-1", rootProposalId = "a-1"), buildProposalDto(id = "b-1", rootProposalId = "b-1")),
        )
        repository.refreshProposals(USER_ID)
        coEvery { remote.fetchProposals(USER_ID, "a-1") } returns Result.success(
            listOf(
                buildProposalDto(id = "a-1", rootProposalId = "a-1", status = "COUNTERED"),
                buildProposalDto(id = "a-2", rootProposalId = "a-1", parentProposalId = "a-1"),
            ),
        )

        val result = repository.refreshProposalThread("a-1", USER_ID)

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { remote.fetchProposals(USER_ID, "a-1") }
        coVerify(exactly = 0) { remote.fetchProposalItems("b-1") }
        repository.observeAllProposals().test {
            val all = awaitItem()
            assertEquals(setOf("a-1", "a-2", "b-1"), all.map { it.id }.toSet())
            assertEquals(TradeStatus.COUNTERED, all.single { it.id == "a-1" }.status)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given a thread fetch failure when refreshProposalThread then the cache is untouched`() = runTest {
        coEvery { remote.fetchProposals(USER_ID, null) } returns Result.success(listOf(buildProposalDto(id = "a-1", rootProposalId = "a-1")))
        repository.refreshProposals(USER_ID)
        coEvery { remote.fetchProposals(USER_ID, "a-1") } returns Result.failure(RuntimeException("keyset_drain_page_cap"))

        val result = repository.refreshProposalThread("a-1", USER_ID)

        assertTrue(result.isFailure)
        repository.observeAllProposals().test {
            assertEquals(listOf("a-1"), awaitItem().map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 7 — TradeCompleted emission (restore plan D7)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given a successful accept when acceptProposal then no TradeCompleted is emitted`() = runTest {
        coEvery { remote.acceptProposal("p-001") } returns Result.success(Unit)

        repository.acceptProposal("p-001")

        coVerify(exactly = 0) { progressionEventBus.emit(any()) }
    }

    @Test
    fun `given a completed countered trade observed twice when refreshing then TradeCompleted is emitted once keyed by the root`() = runTest {
        coEvery { remote.fetchProposals(USER_ID, any()) } returns Result.success(
            listOf(
                buildProposalDto(id = "root-1", rootProposalId = "root-1", status = "COUNTERED"),
                buildProposalDto(id = "counter-2", rootProposalId = "root-1", parentProposalId = "root-1", status = "COMPLETED"),
            ),
        )

        repository.refreshProposals(USER_ID)
        repository.refreshProposalThread("root-1", USER_ID)

        coVerify(exactly = 1) {
            progressionEventBus.emit(match { it is ProgressionEvent.TradeCompleted && it.tradeId == "root-1" })
        }
    }

    @Test
    fun `given both parties observe the same completed trade then each emits the same global key`() = runTest {
        val completed = buildProposalDto(id = "t-1", rootProposalId = "t-1", status = "COMPLETED")
        coEvery { remote.fetchProposals(any(), any()) } returns Result.success(listOf(completed))
        val receiverBus = mockk<ProgressionEventBus>(relaxed = true)
        val receiverRepository = TradesRepositoryImpl(remote, cardDao, cardRepository, receiverBus)

        repository.refreshProposals(USER_ID)
        receiverRepository.refreshProposals("receiver-uuid-002")

        val proposerEvents = mutableListOf<ProgressionEvent>()
        val receiverEvents = mutableListOf<ProgressionEvent>()
        coVerify(exactly = 1) { progressionEventBus.emit(capture(proposerEvents)) }
        coVerify(exactly = 1) { receiverBus.emit(capture(receiverEvents)) }
        assertEquals("trade:t-1", proposerEvents.single().idempotencyKey)
        assertEquals(proposerEvents.single().idempotencyKey, receiverEvents.single().idempotencyKey)
        assertEquals(false, proposerEvents.single().isDeviceScoped)
    }

    @Test
    fun `given accepted then revoked trades when refreshing then no TradeCompleted is emitted`() = runTest {
        coEvery { remote.fetchProposals(USER_ID, any()) } returns Result.success(
            listOf(
                buildProposalDto(id = "a-1", rootProposalId = "a-1", status = "ACCEPTED"),
                buildProposalDto(id = "r-1", rootProposalId = "r-1", status = "REVOKED"),
            ),
        )

        repository.refreshProposals(USER_ID)

        coVerify(exactly = 0) { progressionEventBus.emit(any()) }
    }

    @Test
    fun `given the cache is cleared when the completed trade is observed again then it is re-emitted for the ledger to dedupe`() = runTest {
        coEvery { remote.fetchProposals(USER_ID, any()) } returns Result.success(
            listOf(buildProposalDto(id = "t-1", rootProposalId = "t-1", status = "COMPLETED")),
        )

        repository.refreshProposals(USER_ID)
        repository.clearCache()
        repository.refreshProposals(USER_ID)

        coVerify(exactly = 2) { progressionEventBus.emit(match { it is ProgressionEvent.TradeCompleted }) }
    }
}
