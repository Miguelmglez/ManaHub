package com.mmg.manahub.feature.trades.presentation

import com.mmg.manahub.core.data.repository.TradesRepository
import com.mmg.manahub.core.domain.auth.AuthRepository
import com.mmg.manahub.core.domain.auth.AuthUser
import com.mmg.manahub.core.domain.auth.SessionState
import com.mmg.manahub.core.domain.repository.FriendRepository
import com.mmg.manahub.core.model.Friend
import com.mmg.manahub.core.model.TradeProposal
import com.mmg.manahub.core.model.TradeStatus
import com.mmg.manahub.feature.trades.domain.usecase.GetActiveTradesUseCase
import com.mmg.manahub.feature.trades.domain.usecase.GetTradeHistoryUseCase
import com.mmg.manahub.feature.trades.domain.usecase.RefreshTradesUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [TradesHistoryViewModel].
 *
 * Covers:
 *  - GROUP 1: TTL refresh — [TradesHistoryViewModel.refreshIfStale]
 *  - GROUP 2: [HistoryFilter] — ALL / ACTIVE / COMPLETED / DECLINED
 *  - GROUP 3: Account-switch state bleed (§2.10) — the shared [TradesRepository] cache must
 *    never leak a previous account's proposals into the next signed-in account
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TradesHistoryViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    // ── Mocks ─────────────────────────────────────────────────────────────────

    private val authRepository = mockk<AuthRepository>()
    private val friendRepository = mockk<FriendRepository>()
    private val tradesRepository = mockk<TradesRepository>(relaxed = true)
    private val getActive = mockk<GetActiveTradesUseCase>()
    private val getHistory = mockk<GetTradeHistoryUseCase>()
    private val refreshTrades = mockk<RefreshTradesUseCase>()

    // ── Shared flows ──────────────────────────────────────────────────────────

    private val sessionFlow = MutableStateFlow<SessionState>(SessionState.Loading)
    private val friendsFlow = MutableStateFlow<List<Friend>>(emptyList())
    private val activeFlow = MutableStateFlow<List<TradeProposal>>(emptyList())
    private val historyFlow = MutableStateFlow<List<TradeProposal>>(emptyList())

    private companion object {
        const val USER_A = "user-a"
        const val USER_B = "user-b"
    }

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private fun authenticated(userId: String) = SessionState.Authenticated(
        AuthUser(id = userId, email = "$userId@test.com", nickname = userId, gameTag = "#TAG", avatarUrl = null, provider = "email")
    )

    private fun buildProposal(
        id: String,
        status: TradeStatus,
        updatedAt: Long = 1_000L,
    ) = TradeProposal(
        id = id,
        status = status,
        proposerId = USER_A,
        receiverId = USER_B,
        parentProposalId = null,
        rootProposalId = id,
        proposalVersion = 1,
        includesReviewCollectionFromProposer = false,
        includesReviewCollectionFromReceiver = false,
        proposerMarkedCompletedAt = null,
        receiverMarkedCompletedAt = null,
        cancellationReason = null,
        items = emptyList(),
        createdAt = 1_000L,
        updatedAt = updatedAt,
    )

    // ── Setup / teardown ──────────────────────────────────────────────────────

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { authRepository.sessionState } returns sessionFlow
        every { friendRepository.observeFriends() } returns friendsFlow
        every { getActive() } returns activeFlow
        every { getHistory() } returns historyFlow
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = TradesHistoryViewModel(
        authRepository = authRepository,
        friendRepository = friendRepository,
        tradesRepository = tradesRepository,
        getActive = getActive,
        getHistory = getHistory,
        refreshTrades = refreshTrades,
        ioDispatcher = testDispatcher,
    )

    // =========================================================================
    // GROUP 1: TTL refresh — refreshIfStale
    // =========================================================================

    @Test
    fun `given a refresh just succeeded when refreshIfStale is called then refreshTrades is not invoked again`() = runTest {
        sessionFlow.value = authenticated(USER_A)
        coEvery { refreshTrades(USER_A) } returns Result.success(Unit)

        val vm = createViewModel()
        advanceUntilIdle() // init's firstAuth refresh sets lastRefreshedAt = now (fresh)

        vm.refreshIfStale()
        advanceUntilIdle()

        // Only the init auto-refresh fired; the still-fresh cache blocks the retry.
        coVerify(exactly = 1) { refreshTrades(USER_A) }
    }

    @Test
    fun `given the last refresh never succeeded when refreshIfStale is called then refresh is retried`() = runTest {
        sessionFlow.value = authenticated(USER_A)
        // init's firstAuth refresh FAILS, so lastRefreshedAt stays at its default (0L) — far
        // enough in the past (real epoch millis) that any refreshIfStale() call treats it as stale.
        coEvery { refreshTrades(USER_A) } returns Result.failure(RuntimeException("network down"))

        val vm = createViewModel()
        advanceUntilIdle()
        assertEquals(0L, vm.uiState.value.lastRefreshedAt)

        // Act — now let it succeed and verify the stale check actually retries.
        coEvery { refreshTrades(USER_A) } returns Result.success(Unit)
        vm.refreshIfStale()
        advanceUntilIdle()

        coVerify(exactly = 2) { refreshTrades(USER_A) }
        assertTrue(vm.uiState.value.lastRefreshedAt > 0L)
    }

    @Test
    fun `given a refresh already in progress when refreshIfStale is called then it does not trigger a second concurrent refresh`() = runTest {
        sessionFlow.value = authenticated(USER_A)
        coEvery { refreshTrades(USER_A) } coAnswers {
            delay(10_000)
            Result.success(Unit)
        }

        val vm = createViewModel()
        // Let the init refresh START and suspend on delay(), but not finish.
        advanceTimeBy(1)
        runCurrent()
        assertTrue(vm.uiState.value.isRefreshing)

        vm.refreshIfStale()
        advanceTimeBy(1)
        runCurrent()

        coVerify(exactly = 1) { refreshTrades(USER_A) }

        advanceUntilIdle() // drain the pending delay so the test coroutine can complete cleanly
    }

    @Test
    fun `given a blank currentUserId when refresh is called then refreshTrades is never invoked`() = runTest {
        // No session emitted -> currentUserId stays blank.
        val vm = createViewModel()
        advanceUntilIdle()

        vm.refresh()
        advanceUntilIdle()

        coVerify(exactly = 0) { refreshTrades(any()) }
    }

    // =========================================================================
    // GROUP 2: HistoryFilter
    // =========================================================================

    @Test
    fun `given proposals of every status when filter is ACTIVE then only active-status proposals are returned`() = runTest {
        activeFlow.value = listOf(
            buildProposal("p1", TradeStatus.PROPOSED),
            buildProposal("p2", TradeStatus.ACCEPTED),
        )
        historyFlow.value = listOf(
            buildProposal("p3", TradeStatus.COMPLETED),
            buildProposal("p4", TradeStatus.DECLINED),
        )
        sessionFlow.value = authenticated(USER_A)
        coEvery { refreshTrades(any()) } returns Result.success(Unit)

        val vm = createViewModel()
        advanceUntilIdle()
        vm.onFilterSelected(HistoryFilter.ACTIVE)

        assertEquals(setOf("p1", "p2"), vm.uiState.value.filtered.map { it.id }.toSet())
    }

    @Test
    fun `given proposals of every status when filter is COMPLETED then only COMPLETED proposals are returned`() = runTest {
        activeFlow.value = listOf(buildProposal("p1", TradeStatus.PROPOSED))
        historyFlow.value = listOf(
            buildProposal("p2", TradeStatus.COMPLETED),
            buildProposal("p3", TradeStatus.DECLINED),
        )
        sessionFlow.value = authenticated(USER_A)
        coEvery { refreshTrades(any()) } returns Result.success(Unit)

        val vm = createViewModel()
        advanceUntilIdle()
        vm.onFilterSelected(HistoryFilter.COMPLETED)

        assertEquals(listOf("p2"), vm.uiState.value.filtered.map { it.id })
    }

    @Test
    fun `given proposals of every status when filter is DECLINED then DECLINED CANCELLED REVOKED and COUNTERED are all returned`() = runTest {
        historyFlow.value = listOf(
            buildProposal("p1", TradeStatus.DECLINED),
            buildProposal("p2", TradeStatus.CANCELLED),
            buildProposal("p3", TradeStatus.REVOKED),
            buildProposal("p4", TradeStatus.COUNTERED),
            buildProposal("p5", TradeStatus.COMPLETED),
        )
        sessionFlow.value = authenticated(USER_A)
        coEvery { refreshTrades(any()) } returns Result.success(Unit)

        val vm = createViewModel()
        advanceUntilIdle()
        vm.onFilterSelected(HistoryFilter.DECLINED)

        assertEquals(setOf("p1", "p2", "p3", "p4"), vm.uiState.value.filtered.map { it.id }.toSet())
    }

    @Test
    fun `given proposals across active and history when filter is ALL then every proposal is returned sorted by most recent first`() = runTest {
        activeFlow.value = listOf(buildProposal("p1", TradeStatus.PROPOSED, updatedAt = 2_000L))
        historyFlow.value = listOf(buildProposal("p2", TradeStatus.COMPLETED, updatedAt = 3_000L))
        sessionFlow.value = authenticated(USER_A)
        coEvery { refreshTrades(any()) } returns Result.success(Unit)

        val vm = createViewModel()
        advanceUntilIdle()

        assertEquals(listOf("p2", "p1"), vm.uiState.value.filtered.map { it.id })
    }

    // =========================================================================
    // GROUP 3: Account-switch state bleed (§2.10)
    // =========================================================================

    @Test
    fun `given signed in then signed out then the shared repository cache is cleared`() = runTest {
        sessionFlow.value = authenticated(USER_A)
        coEvery { refreshTrades(any()) } returns Result.success(Unit)
        val vm = createViewModel()
        advanceUntilIdle()

        sessionFlow.value = SessionState.Unauthenticated
        advanceUntilIdle()

        verify(exactly = 1) { tradesRepository.clearCache() }
        assertEquals("", vm.uiState.value.currentUserId)
        assertFalse(vm.uiState.value.isLoggedIn)
    }

    @Test
    fun `given signed in as user A then a different account signs in then the cache is cleared and the new account is refreshed`() = runTest {
        sessionFlow.value = authenticated(USER_A)
        coEvery { refreshTrades(any()) } returns Result.success(Unit)
        val vm = createViewModel()
        advanceUntilIdle()
        assertEquals(USER_A, vm.uiState.value.currentUserId)

        // Act — switch to a DIFFERENT account with no intermediate sign-out.
        sessionFlow.value = authenticated(USER_B)
        advanceUntilIdle()

        // Assert — the previous account's cached proposals must never leak into user B's view.
        verify(exactly = 1) { tradesRepository.clearCache() }
        assertEquals(USER_B, vm.uiState.value.currentUserId)
        coVerify(exactly = 1) { refreshTrades(USER_A) }
        coVerify(exactly = 1) { refreshTrades(USER_B) }
    }

    @Test
    fun `given the same user re-authenticates (token refresh) then the cache is NOT cleared and no duplicate refresh fires`() = runTest {
        sessionFlow.value = authenticated(USER_A)
        coEvery { refreshTrades(any()) } returns Result.success(Unit)
        val vm = createViewModel()
        advanceUntilIdle()

        // Act — the SAME user id re-emitted (e.g. a Supabase token refresh), not an account switch.
        sessionFlow.value = authenticated(USER_A)
        advanceUntilIdle()

        verify(exactly = 0) { tradesRepository.clearCache() }
        coVerify(exactly = 1) { refreshTrades(USER_A) }
        assertEquals(USER_A, vm.uiState.value.currentUserId)
    }
}
