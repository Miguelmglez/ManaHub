package com.mmg.manahub.feature.trades.presentation

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.core.data.local.dao.TradeCollectionSyncDao
import com.mmg.manahub.core.data.local.entity.TradeCollectionSyncEntity
import com.mmg.manahub.core.domain.auth.AuthRepository
import com.mmg.manahub.core.domain.auth.AuthUser
import com.mmg.manahub.core.domain.auth.SessionState
import com.mmg.manahub.core.domain.repository.FriendRepository
import com.mmg.manahub.core.model.Friend
import com.mmg.manahub.core.model.TradeError
import com.mmg.manahub.core.model.TradeItem
import com.mmg.manahub.core.model.TradeProposal
import com.mmg.manahub.core.model.TradeStatus
import com.mmg.manahub.core.model.toUserFacingMessage
import com.mmg.manahub.core.util.AnalyticsHelper
import com.mmg.manahub.feature.trades.domain.usecase.AcceptProposalUseCase
import com.mmg.manahub.feature.trades.domain.usecase.CancelProposalUseCase
import com.mmg.manahub.feature.trades.domain.usecase.DeclineProposalUseCase
import com.mmg.manahub.feature.trades.domain.usecase.GetTradeThreadUseCase
import com.mmg.manahub.feature.trades.domain.usecase.MarkCompletedUseCase
import com.mmg.manahub.feature.trades.domain.usecase.RefreshTradeThreadUseCase
import com.mmg.manahub.feature.trades.domain.usecase.RevokeAcceptanceUseCase
import com.mmg.manahub.feature.trades.domain.usecase.UpdateTradeCollectionUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [TradeNegotiationViewModel].
 *
 * Covers:
 *  - GROUP 1: init / session handling — §2.4 `flatMapLatest` account-switch regression
 *  - GROUP 2: onAccept / doAccept — gift-accept gate, error mapping, §2.7 double-tap guard
 *  - GROUP 3: onDecline / onCancel — error mapping, double-tap guard
 *  - GROUP 4: onRevoke / doRevoke — collection reversal, error mapping
 *  - GROUP 5: onMarkCompleted / doMarkCompleted — collection sync, error mapping
 *  - GROUP 6: onUpdateCollection — sync state, double-tap guard
 *  - GROUP 7: onCounter / onEdit — navigation events
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TradeNegotiationViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    // ── Mocks ─────────────────────────────────────────────────────────────────

    private val authRepository = mockk<AuthRepository>()
    private val friendRepository = mockk<FriendRepository>()
    private val getThread = mockk<GetTradeThreadUseCase>()
    private val refreshTradeThread = mockk<RefreshTradeThreadUseCase>()
    private val acceptProposal = mockk<AcceptProposalUseCase>()
    private val declineProposal = mockk<DeclineProposalUseCase>()
    private val cancelProposal = mockk<CancelProposalUseCase>()
    private val revokeAcceptance = mockk<RevokeAcceptanceUseCase>()
    private val markCompleted = mockk<MarkCompletedUseCase>()
    private val updateTradeCollection = mockk<UpdateTradeCollectionUseCase>()
    private val tradeCollectionSyncDao = mockk<TradeCollectionSyncDao>()
    private val analyticsHelper = mockk<AnalyticsHelper>(relaxed = true)

    // ── Shared flows ──────────────────────────────────────────────────────────

    private val sessionFlow = MutableStateFlow<SessionState>(SessionState.Loading)
    private val friendsFlow = MutableStateFlow<List<Friend>>(emptyList())
    private val threadFlow = MutableStateFlow<List<TradeProposal>>(emptyList())
    private val pendingApplyFlow = MutableStateFlow<List<String>>(emptyList())

    private companion object {
        const val ROOT_PROPOSAL_ID = "root-proposal-001"
        const val USER_A = "user-a"
        const val USER_B = "user-b"
    }

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private fun authenticated(userId: String) = SessionState.Authenticated(
        AuthUser(id = userId, email = "$userId@test.com", nickname = userId, gameTag = "#TAG", avatarUrl = null, provider = "email")
    )

    private fun buildProposal(
        id: String = "p1",
        status: TradeStatus = TradeStatus.PROPOSED,
        proposerId: String = USER_A,
        receiverId: String = USER_B,
        items: List<TradeItem> = emptyList(),
        includesReviewCollectionFromProposer: Boolean = false,
        includesReviewCollectionFromReceiver: Boolean = false,
    ) = TradeProposal(
        id = id,
        status = status,
        proposerId = proposerId,
        receiverId = receiverId,
        parentProposalId = null,
        rootProposalId = ROOT_PROPOSAL_ID,
        proposalVersion = 1,
        includesReviewCollectionFromProposer = includesReviewCollectionFromProposer,
        includesReviewCollectionFromReceiver = includesReviewCollectionFromReceiver,
        proposerMarkedCompletedAt = null,
        receiverMarkedCompletedAt = null,
        cancellationReason = null,
        items = items,
        createdAt = 1_000L,
        updatedAt = 1_000L,
    )

    private fun buildItem(
        id: String = "item-1",
        fromUserId: String = USER_A,
        toUserId: String = USER_B,
        cardId: String = "card-1",
        userCardIdRef: String? = "uc-1",
    ) = TradeItem(
        id = id,
        tradeProposalId = "p1",
        fromUserId = fromUserId,
        toUserId = toUserId,
        userCardIdRef = userCardIdRef,
        quantity = 1,
        isFoil = false,
        condition = "NM",
        language = "en",
        cardId = cardId,
        isReviewCollectionPlaceholder = false,
    )

    // ── Setup / teardown ──────────────────────────────────────────────────────

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic(FirebaseCrashlytics::class)
        val crashlytics = mockk<FirebaseCrashlytics>(relaxed = true)
        every { FirebaseCrashlytics.getInstance() } returns crashlytics

        every { authRepository.sessionState } returns sessionFlow
        every { friendRepository.observeFriends() } returns friendsFlow
        every { getThread(any()) } returns threadFlow
        coEvery { refreshTradeThread(any(), any()) } returns Result.success(Unit)
        every { tradeCollectionSyncDao.observeSyncedProposalIds(any()) } returns MutableStateFlow(emptyList())
        every { tradeCollectionSyncDao.observePendingApplyProposalIds(any()) } returns pendingApplyFlow
        coEvery { tradeCollectionSyncDao.markPendingApply(any()) } answers {
            pendingApplyFlow.value = pendingApplyFlow.value + firstArg<TradeCollectionSyncEntity>().proposalId
        }
        coEvery { tradeCollectionSyncDao.clearPendingApply(any(), any()) } answers {
            pendingApplyFlow.value = pendingApplyFlow.value - firstArg<String>()
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(FirebaseCrashlytics::class)
    }

    /** Makes the next thread refresh report every proposal as COMPLETED (both parties marked). */
    private fun completeOnRefresh() {
        coEvery { refreshTradeThread(any(), any()) } coAnswers {
            threadFlow.value = threadFlow.value.map { it.copy(status = TradeStatus.COMPLETED) }
            Result.success(Unit)
        }
    }

    private fun createViewModel() = TradeNegotiationViewModel(
        savedStateHandle = SavedStateHandle(mapOf("rootProposalId" to ROOT_PROPOSAL_ID)),
        authRepository = authRepository,
        friendRepository = friendRepository,
        getThread = getThread,
        refreshTradeThread = refreshTradeThread,
        acceptProposal = acceptProposal,
        declineProposal = declineProposal,
        cancelProposal = cancelProposal,
        revokeAcceptance = revokeAcceptance,
        markCompleted = markCompleted,
        updateTradeCollection = updateTradeCollection,
        tradeCollectionSyncDao = tradeCollectionSyncDao,
        analyticsHelper = analyticsHelper,
        ioDispatcher = testDispatcher,
    )

    // =========================================================================
    // GROUP 1: init / session handling — §2.4 regression
    // =========================================================================

    @Test
    fun `given first authenticated session then thread is refreshed automatically`() = runTest {
        sessionFlow.value = authenticated(USER_A)

        createViewModel()
        advanceUntilIdle()

        coVerify(exactly = 1) { refreshTradeThread(ROOT_PROPOSAL_ID, USER_A) }
    }

    @Test
    fun `given session switches to a different authenticated user then syncedCollectionProposalIds re-subscribes to the new user's DAO flow`() = runTest {
        val userASyncedFlow = MutableStateFlow(listOf("p-old"))
        val userBSyncedFlow = MutableStateFlow(listOf("p-new"))
        every { tradeCollectionSyncDao.observeSyncedProposalIds(USER_A) } returns userASyncedFlow
        every { tradeCollectionSyncDao.observeSyncedProposalIds(USER_B) } returns userBSyncedFlow
        sessionFlow.value = authenticated(USER_A)

        val vm = createViewModel()
        advanceUntilIdle()
        assertEquals(setOf("p-old"), vm.uiState.value.syncedCollectionProposalIds)

        // Act — switch accounts (the §2.4 bug: a nested `collect` never re-targeted this flow)
        sessionFlow.value = authenticated(USER_B)
        advanceUntilIdle()

        // Assert — state now reflects the NEW user's DAO flow
        assertEquals(setOf("p-new"), vm.uiState.value.syncedCollectionProposalIds)
        verify(exactly = 1) { tradeCollectionSyncDao.observeSyncedProposalIds(USER_A) }
        verify(exactly = 1) { tradeCollectionSyncDao.observeSyncedProposalIds(USER_B) }
    }

    @Test
    fun `given session switched to a new user then further emissions on the OLD user's synced flow are ignored`() = runTest {
        val userASyncedFlow = MutableStateFlow(listOf("p-old"))
        val userBSyncedFlow = MutableStateFlow(listOf("p-new"))
        every { tradeCollectionSyncDao.observeSyncedProposalIds(USER_A) } returns userASyncedFlow
        every { tradeCollectionSyncDao.observeSyncedProposalIds(USER_B) } returns userBSyncedFlow
        sessionFlow.value = authenticated(USER_A)

        val vm = createViewModel()
        advanceUntilIdle()
        sessionFlow.value = authenticated(USER_B)
        advanceUntilIdle()

        // Act — the OLD (now-cancelled) flow emits again
        userASyncedFlow.value = listOf("p-old", "p-stale")
        advanceUntilIdle()

        // Assert — state is unaffected; still reflects only user B's data
        assertEquals(setOf("p-new"), vm.uiState.value.syncedCollectionProposalIds)
    }

    @Test
    fun `given thread flow emits proposals then uiState thread reflects them`() = runTest {
        sessionFlow.value = authenticated(USER_A)
        val proposal = buildProposal(id = "p1")
        threadFlow.value = listOf(proposal)

        val vm = createViewModel()
        advanceUntilIdle()

        assertEquals(listOf(proposal), vm.uiState.value.thread)
        assertFalse(vm.uiState.value.isLoading)
    }

    // =========================================================================
    // GROUP 2: onAccept / doAccept
    // =========================================================================

    @Test
    fun `given non-gift proposal when onAccept succeeds then acceptProposal is invoked and analytics logged`() = runTest {
        threadFlow.value = listOf(buildProposal(id = "p1", status = TradeStatus.PROPOSED))
        sessionFlow.value = authenticated(USER_A)
        coEvery { acceptProposal("p1") } returns Result.success(Unit)

        val vm = createViewModel()
        advanceUntilIdle()

        vm.onAccept("p1")
        advanceUntilIdle()

        coVerify(exactly = 1) { acceptProposal("p1") }
        verify { analyticsHelper.logEvent("trade_accepted", emptyMap()) }
        assertFalse(vm.uiState.value.isProcessing)
    }

    @Test
    fun `given other party has review-collection flag with zero concrete items when onAccept then gift dialog is pending and acceptProposal is not invoked`() = runTest {
        // Current user (proposer) offered a real card; the receiver only ticked
        // "review my collection" and contributed no concrete items — a gift trade.
        val proposal = buildProposal(
            id = "p1",
            proposerId = USER_A,
            receiverId = USER_B,
            includesReviewCollectionFromReceiver = true,
            items = listOf(buildItem(fromUserId = USER_A, toUserId = USER_B, cardId = "card-1")),
        )
        threadFlow.value = listOf(proposal)
        sessionFlow.value = authenticated(USER_A)

        val vm = createViewModel()
        advanceUntilIdle()

        vm.onAccept("p1")
        advanceUntilIdle()

        assertEquals("p1", vm.uiState.value.pendingGiftAcceptProposalId)
        coVerify(exactly = 0) { acceptProposal(any()) }
    }

    @Test
    fun `given pending gift accept when onGiftAcceptConfirmed then acceptProposal proceeds`() = runTest {
        val proposal = buildProposal(
            id = "p1", proposerId = USER_A, receiverId = USER_B,
            includesReviewCollectionFromReceiver = true,
            items = listOf(buildItem(fromUserId = USER_A, toUserId = USER_B)),
        )
        threadFlow.value = listOf(proposal)
        sessionFlow.value = authenticated(USER_A)
        coEvery { acceptProposal("p1") } returns Result.success(Unit)

        val vm = createViewModel()
        advanceUntilIdle()
        vm.onAccept("p1")
        advanceUntilIdle()

        vm.onGiftAcceptConfirmed()
        advanceUntilIdle()

        coVerify(exactly = 1) { acceptProposal("p1") }
        assertNull(vm.uiState.value.pendingGiftAcceptProposalId)
    }

    @Test
    fun `given pending gift accept when onGiftAcceptDismissed then dialog clears without accepting`() = runTest {
        val proposal = buildProposal(
            id = "p1", proposerId = USER_A, receiverId = USER_B,
            includesReviewCollectionFromReceiver = true,
            items = listOf(buildItem(fromUserId = USER_A, toUserId = USER_B)),
        )
        threadFlow.value = listOf(proposal)
        sessionFlow.value = authenticated(USER_A)

        val vm = createViewModel()
        advanceUntilIdle()
        vm.onAccept("p1")
        advanceUntilIdle()

        vm.onGiftAcceptDismissed()

        assertNull(vm.uiState.value.pendingGiftAcceptProposalId)
        coVerify(exactly = 0) { acceptProposal(any()) }
    }

    @Test
    fun `given acceptProposal fails with CardAlreadyLocked when onAccept then errorDialog carries the locked card ids`() = runTest {
        threadFlow.value = listOf(buildProposal(id = "p1", status = TradeStatus.PROPOSED))
        sessionFlow.value = authenticated(USER_A)
        coEvery { acceptProposal("p1") } returns Result.failure(TradeError.CardAlreadyLocked(listOf("card-x", "card-y")))

        val vm = createViewModel()
        advanceUntilIdle()
        vm.onAccept("p1")
        advanceUntilIdle()

        val error = vm.uiState.value.errorDialog
        assertTrue(error is NegotiationError.CardAlreadyLocked)
        assertEquals(listOf("card-x", "card-y"), (error as NegotiationError.CardAlreadyLocked).cardIds)
    }

    @Test
    fun `given acceptProposal fails with a generic exception when onAccept then errorDialog is Generic`() = runTest {
        threadFlow.value = listOf(buildProposal(id = "p1", status = TradeStatus.PROPOSED))
        sessionFlow.value = authenticated(USER_A)
        coEvery { acceptProposal("p1") } returns Result.failure(RuntimeException("network down"))

        val vm = createViewModel()
        advanceUntilIdle()
        vm.onAccept("p1")
        advanceUntilIdle()

        assertTrue(vm.uiState.value.errorDialog is NegotiationError.Generic)
    }

    @Test
    fun `given two rapid onAccept calls for the same proposal then acceptProposal is invoked only once`() = runTest {
        threadFlow.value = listOf(buildProposal(id = "p1", status = TradeStatus.PROPOSED))
        sessionFlow.value = authenticated(USER_A)
        coEvery { acceptProposal("p1") } returns Result.success(Unit)

        val vm = createViewModel()
        advanceUntilIdle()

        // Two synchronous taps before the coroutine dispatcher runs — the atomic
        // isProcessing capture-and-flip (§2.7) must block the second one.
        vm.onAccept("p1")
        vm.onAccept("p1")
        advanceUntilIdle()

        coVerify(exactly = 1) { acceptProposal("p1") }
    }

    // =========================================================================
    // GROUP 3: onDecline / onCancel
    // =========================================================================

    @Test
    fun `given decline succeeds when onDecline then declineProposal is invoked`() = runTest {
        threadFlow.value = listOf(buildProposal(id = "p1"))
        sessionFlow.value = authenticated(USER_A)
        coEvery { declineProposal("p1") } returns Result.success(Unit)

        val vm = createViewModel()
        advanceUntilIdle()
        vm.onDecline("p1")
        advanceUntilIdle()

        coVerify(exactly = 1) { declineProposal("p1") }
        assertFalse(vm.uiState.value.isProcessing)
    }

    @Test
    fun `given decline fails when onDecline then ShowError event carries the friendly message`() = runTest {
        threadFlow.value = listOf(buildProposal(id = "p1"))
        sessionFlow.value = authenticated(USER_A)
        coEvery { declineProposal("p1") } returns Result.failure(TradeError.Unauthorized)

        val vm = createViewModel()
        advanceUntilIdle()

        vm.events.test {
            vm.onDecline("p1")
            advanceUntilIdle()
            val event = awaitItem()
            assertTrue(event is NegotiationEvent.ShowError)
            assertEquals(TradeError.Unauthorized.toUserFacingMessage(), (event as NegotiationEvent.ShowError).message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given two rapid onDecline calls then declineProposal is invoked only once`() = runTest {
        threadFlow.value = listOf(buildProposal(id = "p1"))
        sessionFlow.value = authenticated(USER_A)
        coEvery { declineProposal("p1") } returns Result.success(Unit)

        val vm = createViewModel()
        advanceUntilIdle()
        vm.onDecline("p1")
        vm.onDecline("p1")
        advanceUntilIdle()

        coVerify(exactly = 1) { declineProposal("p1") }
    }

    @Test
    fun `given onCancelRequested then onCancelConfirmed when cancel succeeds then cancelProposal is invoked and pending state clears`() = runTest {
        threadFlow.value = listOf(buildProposal(id = "p1"))
        sessionFlow.value = authenticated(USER_A)
        coEvery { cancelProposal("p1") } returns Result.success(Unit)

        val vm = createViewModel()
        advanceUntilIdle()

        vm.onCancelRequested("p1")
        assertEquals("p1", vm.uiState.value.pendingCancelProposalId)

        vm.onCancelConfirmed()
        advanceUntilIdle()

        coVerify(exactly = 1) { cancelProposal("p1") }
        assertNull(vm.uiState.value.pendingCancelProposalId)
    }

    @Test
    fun `given onCancelDismissed then pendingCancelProposalId clears without cancelling`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onCancelRequested("p1")
        vm.onCancelDismissed()

        assertNull(vm.uiState.value.pendingCancelProposalId)
        coVerify(exactly = 0) { cancelProposal(any()) }
    }

    // =========================================================================
    // GROUP 4: onRevoke / doRevoke
    // =========================================================================

    @Test
    fun `given proposal already synced when onRevoke then pendingRevokeHasSynced reflects that sync state`() = runTest {
        sessionFlow.value = authenticated(USER_A)
        every { tradeCollectionSyncDao.observeSyncedProposalIds(USER_A) } returns MutableStateFlow(listOf("p1"))
        threadFlow.value = listOf(buildProposal(id = "p1", status = TradeStatus.ACCEPTED))

        val vm = createViewModel()
        advanceUntilIdle()
        vm.onRevoke("p1")

        assertEquals("p1", vm.uiState.value.pendingRevokeProposalId)
        assertTrue(vm.uiState.value.pendingRevokeHasSynced)
    }

    @Test
    fun `given revoke with reverseCollection true when both succeed then CollectionSyncResult success event is emitted`() = runTest {
        val sentItem = buildItem(id = "i1", fromUserId = USER_A, toUserId = USER_B, cardId = "card-1")
        val receivedItem = buildItem(id = "i2", fromUserId = USER_B, toUserId = USER_A, cardId = "card-2")
        val proposal = buildProposal(id = "p1", proposerId = USER_A, receiverId = USER_B, status = TradeStatus.ACCEPTED, items = listOf(sentItem, receivedItem))
        threadFlow.value = listOf(proposal)
        sessionFlow.value = authenticated(USER_A)
        coEvery { revokeAcceptance("p1") } returns Result.success(Unit)
        coEvery { updateTradeCollection("p1", USER_A, listOf(sentItem), listOf(receivedItem), reverse = true) } returns Result.success(Unit)

        val vm = createViewModel()
        advanceUntilIdle()

        vm.events.test {
            vm.onRevoke("p1")
            vm.onRevokeConfirmed(reverseCollection = true)
            advanceUntilIdle()
            assertEquals(NegotiationEvent.CollectionSyncResult(success = true), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 1) { updateTradeCollection("p1", USER_A, listOf(sentItem), listOf(receivedItem), reverse = true) }
    }

    @Test
    fun `given revoke with reverseCollection true when updateTradeCollection fails then CollectionSyncResult failure event is emitted`() = runTest {
        threadFlow.value = listOf(buildProposal(id = "p1", proposerId = USER_A, receiverId = USER_B, status = TradeStatus.ACCEPTED))
        sessionFlow.value = authenticated(USER_A)
        coEvery { revokeAcceptance("p1") } returns Result.success(Unit)
        coEvery { updateTradeCollection(any(), any(), any(), any(), reverse = true) } returns Result.failure(RuntimeException("db error"))

        val vm = createViewModel()
        advanceUntilIdle()

        vm.events.test {
            vm.onRevoke("p1")
            vm.onRevokeConfirmed(reverseCollection = true)
            advanceUntilIdle()
            assertEquals(NegotiationEvent.CollectionSyncResult(success = false), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given revoke with reverseCollection false when confirmed then updateTradeCollection is never invoked`() = runTest {
        threadFlow.value = listOf(buildProposal(id = "p1", status = TradeStatus.ACCEPTED))
        sessionFlow.value = authenticated(USER_A)
        coEvery { revokeAcceptance("p1") } returns Result.success(Unit)

        val vm = createViewModel()
        advanceUntilIdle()
        vm.onRevoke("p1")
        vm.onRevokeConfirmed(reverseCollection = false)
        advanceUntilIdle()

        coVerify(exactly = 0) { updateTradeCollection(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `given revokeAcceptance itself fails then ShowError event is emitted and updateTradeCollection is never invoked`() = runTest {
        threadFlow.value = listOf(buildProposal(id = "p1", status = TradeStatus.ACCEPTED))
        sessionFlow.value = authenticated(USER_A)
        coEvery { revokeAcceptance("p1") } returns Result.failure(TradeError.InvalidStateTransition)

        val vm = createViewModel()
        advanceUntilIdle()

        vm.events.test {
            vm.onRevoke("p1")
            vm.onRevokeConfirmed(reverseCollection = true)
            advanceUntilIdle()
            assertTrue(awaitItem() is NegotiationEvent.ShowError)
            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 0) { updateTradeCollection(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `given two rapid confirmations for the same revoke then revokeAcceptance is invoked only once`() = runTest {
        threadFlow.value = listOf(buildProposal(id = "p1", status = TradeStatus.ACCEPTED))
        sessionFlow.value = authenticated(USER_A)
        coEvery { revokeAcceptance("p1") } returns Result.success(Unit)

        val vm = createViewModel()
        advanceUntilIdle()
        vm.onRevoke("p1")
        // The first confirm clears pendingRevokeProposalId synchronously; a second rapid
        // confirm reads null and no-ops before doRevoke's own isProcessing guard is even reached.
        vm.onRevokeConfirmed(reverseCollection = false)
        vm.onRevokeConfirmed(reverseCollection = false)
        advanceUntilIdle()

        coVerify(exactly = 1) { revokeAcceptance("p1") }
    }

    // =========================================================================
    // GROUP 5: onMarkCompleted / doMarkCompleted
    // =========================================================================

    @Test
    fun `given onMarkCompleted then pending sent and received items are filtered by the current user`() = runTest {
        val sentItem = buildItem(id = "i1", fromUserId = USER_A, toUserId = USER_B, cardId = "card-1")
        val receivedItem = buildItem(id = "i2", fromUserId = USER_B, toUserId = USER_A, cardId = "card-2")
        val proposal = buildProposal(id = "p1", proposerId = USER_A, receiverId = USER_B, status = TradeStatus.ACCEPTED, items = listOf(sentItem, receivedItem))
        threadFlow.value = listOf(proposal)
        sessionFlow.value = authenticated(USER_A)

        val vm = createViewModel()
        advanceUntilIdle()
        vm.onMarkCompleted("p1")

        assertEquals("p1", vm.uiState.value.pendingMarkCompletedProposalId)
        assertEquals(listOf(sentItem), vm.uiState.value.pendingMarkCompletedSentItems)
        assertEquals(listOf(receivedItem), vm.uiState.value.pendingMarkCompletedReceivedItems)
    }

    @Test
    fun `given mark completed succeeds with addToCollection true and both succeed then CollectionSyncResult success is emitted and analytics logged`() = runTest {
        val sentItem = buildItem(id = "i1", fromUserId = USER_A, toUserId = USER_B)
        val proposal = buildProposal(id = "p1", proposerId = USER_A, receiverId = USER_B, status = TradeStatus.ACCEPTED, items = listOf(sentItem))
        threadFlow.value = listOf(proposal)
        sessionFlow.value = authenticated(USER_A)
        coEvery { markCompleted("p1") } returns Result.success(Unit)
        coEvery { updateTradeCollection("p1", USER_A, listOf(sentItem), emptyList()) } returns Result.success(Unit)

        val vm = createViewModel()
        advanceUntilIdle()
        completeOnRefresh()
        vm.onMarkCompleted("p1")

        vm.events.test {
            vm.onConfirmMarkCompleted(addToCollection = true)
            advanceUntilIdle()
            assertEquals(NegotiationEvent.CollectionSyncResult(success = true), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        verify {
            analyticsHelper.logEvent(
                "trade_completed",
                mapOf("added_to_collection" to true),
            )
        }
    }

    @Test
    fun `given mark completed succeeds with addToCollection false then updateTradeCollection is never invoked`() = runTest {
        threadFlow.value = listOf(buildProposal(id = "p1", status = TradeStatus.ACCEPTED))
        sessionFlow.value = authenticated(USER_A)
        coEvery { markCompleted("p1") } returns Result.success(Unit)

        val vm = createViewModel()
        advanceUntilIdle()
        vm.onMarkCompleted("p1")
        vm.onConfirmMarkCompleted(addToCollection = false)
        advanceUntilIdle()

        coVerify(exactly = 0) { updateTradeCollection(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `given markCompleted fails with InventoryGone then errorDialog is InventoryGone`() = runTest {
        threadFlow.value = listOf(buildProposal(id = "p1", status = TradeStatus.ACCEPTED))
        sessionFlow.value = authenticated(USER_A)
        coEvery { markCompleted("p1") } returns Result.failure(TradeError.InventoryGone)

        val vm = createViewModel()
        advanceUntilIdle()
        vm.onMarkCompleted("p1")
        vm.onConfirmMarkCompleted(addToCollection = false)
        advanceUntilIdle()

        assertEquals(NegotiationError.InventoryGone, vm.uiState.value.errorDialog)
    }

    @Test
    fun `given markCompleted succeeds but updateTradeCollection fails then CollectionSyncResult failure is emitted`() = runTest {
        threadFlow.value = listOf(buildProposal(id = "p1", status = TradeStatus.ACCEPTED))
        sessionFlow.value = authenticated(USER_A)
        coEvery { markCompleted("p1") } returns Result.success(Unit)
        coEvery { updateTradeCollection(any(), any(), any(), any(), any()) } returns Result.failure(RuntimeException("fail"))

        val vm = createViewModel()
        advanceUntilIdle()
        completeOnRefresh()
        vm.onMarkCompleted("p1")

        vm.events.test {
            vm.onConfirmMarkCompleted(addToCollection = true)
            advanceUntilIdle()
            assertEquals(NegotiationEvent.CollectionSyncResult(success = false), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given onDismissMarkCompletedDialog then all pending mark-completed state clears`() = runTest {
        threadFlow.value = listOf(buildProposal(id = "p1", status = TradeStatus.ACCEPTED))
        sessionFlow.value = authenticated(USER_A)

        val vm = createViewModel()
        advanceUntilIdle()
        vm.onMarkCompleted("p1")
        vm.onDismissMarkCompletedDialog()

        assertNull(vm.uiState.value.pendingMarkCompletedProposalId)
        assertTrue(vm.uiState.value.pendingMarkCompletedSentItems.isEmpty())
        assertTrue(vm.uiState.value.pendingMarkCompletedReceivedItems.isEmpty())
    }

    @Test
    fun `given two rapid mark-completed confirmations then markCompleted is invoked only once`() = runTest {
        threadFlow.value = listOf(buildProposal(id = "p1", status = TradeStatus.ACCEPTED))
        sessionFlow.value = authenticated(USER_A)
        coEvery { markCompleted("p1") } returns Result.success(Unit)

        val vm = createViewModel()
        advanceUntilIdle()
        vm.onMarkCompleted("p1")
        vm.onConfirmMarkCompleted(addToCollection = false)
        // Second confirm reads pendingMarkCompletedProposalId == null (cleared by the first) -> no-op.
        vm.onConfirmMarkCompleted(addToCollection = false)
        advanceUntilIdle()

        coVerify(exactly = 1) { markCompleted("p1") }
    }

    // =========================================================================
    // GROUP 6: onUpdateCollection
    // =========================================================================

    @Test
    fun `given update collection succeeds when onUpdateCollection then syncedCollectionProposalIds gains the id and success event is emitted`() = runTest {
        val sentItem = buildItem(id = "i1", fromUserId = USER_A, toUserId = USER_B)
        val proposal = buildProposal(id = "p1", proposerId = USER_A, receiverId = USER_B, status = TradeStatus.COMPLETED, items = listOf(sentItem))
        threadFlow.value = listOf(proposal)
        sessionFlow.value = authenticated(USER_A)
        coEvery { updateTradeCollection("p1", USER_A, listOf(sentItem), emptyList()) } returns Result.success(Unit)

        val vm = createViewModel()
        advanceUntilIdle()

        vm.events.test {
            vm.onUpdateCollection("p1")
            advanceUntilIdle()
            assertEquals(NegotiationEvent.CollectionSyncResult(success = true), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue("p1" in vm.uiState.value.syncedCollectionProposalIds)
        assertFalse(vm.uiState.value.isSyncingCollection)
    }

    @Test
    fun `given update collection fails when onUpdateCollection then a failure event is emitted with no raw message leaked`() = runTest {
        threadFlow.value = listOf(buildProposal(id = "p1", status = TradeStatus.COMPLETED))
        sessionFlow.value = authenticated(USER_A)
        coEvery { updateTradeCollection(any(), any(), any(), any(), any()) } returns
            Result.failure(RuntimeException("raw_server_sentinel_key"))

        val vm = createViewModel()
        advanceUntilIdle()

        vm.events.test {
            vm.onUpdateCollection("p1")
            advanceUntilIdle()
            assertEquals(NegotiationEvent.CollectionSyncResult(success = false), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue("p1" !in vm.uiState.value.syncedCollectionProposalIds)
    }

    @Test
    fun `given blank currentUserId when onUpdateCollection then updateTradeCollection is never invoked`() = runTest {
        threadFlow.value = listOf(buildProposal(id = "p1"))
        // No session emitted -> currentUserId stays blank.

        val vm = createViewModel()
        advanceUntilIdle()
        vm.onUpdateCollection("p1")
        advanceUntilIdle()

        coVerify(exactly = 0) { updateTradeCollection(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `given two rapid onUpdateCollection calls then updateTradeCollection is invoked only once`() = runTest {
        threadFlow.value = listOf(buildProposal(id = "p1", status = TradeStatus.COMPLETED))
        sessionFlow.value = authenticated(USER_A)
        coEvery { updateTradeCollection(any(), any(), any(), any(), any()) } returns Result.success(Unit)

        val vm = createViewModel()
        advanceUntilIdle()
        vm.onUpdateCollection("p1")
        vm.onUpdateCollection("p1")
        advanceUntilIdle()

        coVerify(exactly = 1) { updateTradeCollection(any(), any(), any(), any(), any()) }
    }

    // =========================================================================
    // GROUP 7: onCounter / onEdit
    // =========================================================================

    @Test
    fun `given onCounter then NavigateToEditor event carries the other party as receiver and isCounter true`() = runTest {
        threadFlow.value = listOf(buildProposal(id = "p1", proposerId = USER_A, receiverId = USER_B))
        sessionFlow.value = authenticated(USER_A)

        val vm = createViewModel()
        advanceUntilIdle()

        vm.events.test {
            vm.onCounter("p1")
            val event = awaitItem() as NegotiationEvent.NavigateToEditor
            assertEquals(USER_B, event.args.receiverId)
            assertTrue(event.args.isCounter)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given onEdit then NavigateToEditor event carries isCounter false`() = runTest {
        threadFlow.value = listOf(buildProposal(id = "p1", proposerId = USER_A, receiverId = USER_B))
        sessionFlow.value = authenticated(USER_A)

        val vm = createViewModel()
        advanceUntilIdle()

        vm.events.test {
            vm.onEdit("p1")
            val event = awaitItem() as NegotiationEvent.NavigateToEditor
            assertFalse(event.args.isCounter)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // =========================================================================
    // GROUP 8: unloaded items never drive collection changes (trades audit H3)
    // =========================================================================

    @Test
    fun `given the proposal's items are not loaded when onUpdateCollection then nothing is applied`() = runTest {
        threadFlow.value = listOf(buildProposal(id = "p1", status = TradeStatus.COMPLETED).copy(itemsLoaded = false))
        sessionFlow.value = authenticated(USER_A)

        val vm = createViewModel()
        advanceUntilIdle()
        vm.onUpdateCollection("p1")
        advanceUntilIdle()

        coVerify(exactly = 0) { updateTradeCollection(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `given the proposal's items are not loaded when onMarkCompleted then no confirmation opens`() = runTest {
        threadFlow.value = listOf(buildProposal(id = "p1", status = TradeStatus.ACCEPTED).copy(itemsLoaded = false))
        sessionFlow.value = authenticated(USER_A)

        val vm = createViewModel()
        advanceUntilIdle()
        vm.onMarkCompleted("p1")

        assertNull(vm.uiState.value.pendingMarkCompletedProposalId)
    }

    @Test
    fun `given the proposal's items are not loaded when a revoke with reversal is confirmed then nothing runs`() = runTest {
        threadFlow.value = listOf(buildProposal(id = "p1", status = TradeStatus.ACCEPTED).copy(itemsLoaded = false))
        sessionFlow.value = authenticated(USER_A)

        val vm = createViewModel()
        advanceUntilIdle()
        vm.onRevoke("p1")
        assertFalse(vm.uiState.value.pendingRevokeCanReverse)
        vm.onRevokeConfirmed(reverseCollection = true)
        advanceUntilIdle()

        coVerify(exactly = 0) { revokeAcceptance(any()) }
        coVerify(exactly = 0) { updateTradeCollection(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `given the proposal's items are not loaded when onCounter then no editor navigation happens`() = runTest {
        threadFlow.value = listOf(buildProposal(id = "p1").copy(itemsLoaded = false))
        sessionFlow.value = authenticated(USER_B)

        val vm = createViewModel()
        advanceUntilIdle()

        vm.events.test {
            vm.onCounter("p1")
            vm.onEdit("p1")
            advanceUntilIdle()
            expectNoEvents()
        }
    }

    // =========================================================================
    // GROUP 9: collection changes only once COMPLETED (trades audit H4)
    // =========================================================================

    @Test
    fun `given the trade stays ACCEPTED after marking completed then nothing is applied and the choice is persisted`() = runTest {
        val sentItem = buildItem(id = "i1", fromUserId = USER_A, toUserId = USER_B)
        threadFlow.value = listOf(buildProposal(id = "p1", status = TradeStatus.ACCEPTED, items = listOf(sentItem)))
        sessionFlow.value = authenticated(USER_A)
        coEvery { markCompleted("p1") } returns Result.success(Unit)

        val vm = createViewModel()
        advanceUntilIdle()
        vm.onMarkCompleted("p1")

        vm.events.test {
            vm.onConfirmMarkCompleted(addToCollection = true)
            advanceUntilIdle()
            assertEquals(NegotiationEvent.CollectionApplyDeferred, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 0) { updateTradeCollection(any(), any(), any(), any(), any()) }
        coVerify(exactly = 1) { tradeCollectionSyncDao.markPendingApply(match { it.proposalId == "p1" && it.pendingApply }) }
        assertTrue("p1" in vm.uiState.value.pendingApplyProposalIds)
    }

    @Test
    fun `given a pending apply when the trade is later seen COMPLETED then the collection is applied once`() = runTest {
        val sentItem = buildItem(id = "i1", fromUserId = USER_A, toUserId = USER_B)
        val accepted = buildProposal(id = "p1", status = TradeStatus.ACCEPTED, items = listOf(sentItem))
        threadFlow.value = listOf(accepted)
        pendingApplyFlow.value = listOf("p1")
        sessionFlow.value = authenticated(USER_A)
        coEvery { updateTradeCollection(any(), any(), any(), any(), any()) } returns Result.success(Unit)

        createViewModel()
        advanceUntilIdle()
        coVerify(exactly = 0) { updateTradeCollection(any(), any(), any(), any(), any()) }

        threadFlow.value = listOf(accepted.copy(status = TradeStatus.COMPLETED))
        advanceUntilIdle()
        threadFlow.value = listOf(accepted.copy(status = TradeStatus.COMPLETED, updatedAt = 2_000L))
        advanceUntilIdle()

        coVerify(exactly = 1) { updateTradeCollection("p1", USER_A, listOf(sentItem), emptyList(), false) }
    }

    @Test
    fun `given a pending apply when the trade is revoked then the pending choice is dropped without touching the collection`() = runTest {
        threadFlow.value = listOf(buildProposal(id = "p1", status = TradeStatus.REVOKED))
        pendingApplyFlow.value = listOf("p1")
        sessionFlow.value = authenticated(USER_A)

        createViewModel()
        advanceUntilIdle()

        coVerify(exactly = 1) { tradeCollectionSyncDao.clearPendingApply("p1", USER_A) }
        coVerify(exactly = 0) { updateTradeCollection(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `given a revoked trade whose changes were applied when undo is requested then the reversal runs`() = runTest {
        val sentItem = buildItem(id = "i1", fromUserId = USER_A, toUserId = USER_B)
        threadFlow.value = listOf(buildProposal(id = "p1", status = TradeStatus.REVOKED, items = listOf(sentItem)))
        every { tradeCollectionSyncDao.observeSyncedProposalIds(USER_A) } returns MutableStateFlow(listOf("p1"))
        sessionFlow.value = authenticated(USER_A)
        coEvery { updateTradeCollection(any(), any(), any(), any(), any()) } returns Result.success(Unit)

        val vm = createViewModel()
        advanceUntilIdle()
        vm.onUndoCollectionChanges("p1")
        advanceUntilIdle()

        coVerify(exactly = 1) { updateTradeCollection("p1", USER_A, listOf(sentItem), emptyList(), true) }
    }

    @Test
    fun `given a revoked trade with no applied changes when undo is requested then nothing runs`() = runTest {
        threadFlow.value = listOf(buildProposal(id = "p1", status = TradeStatus.REVOKED))
        sessionFlow.value = authenticated(USER_A)

        val vm = createViewModel()
        advanceUntilIdle()
        vm.onUndoCollectionChanges("p1")
        advanceUntilIdle()

        coVerify(exactly = 0) { updateTradeCollection(any(), any(), any(), any(), any()) }
    }

    // =========================================================================
    // Refresh: single flight, first-load state, account switch
    // =========================================================================

    @Test
    fun `given the session trigger and the screen entry fire together then the thread is refreshed once`() = runTest {
        sessionFlow.value = authenticated(USER_A)
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        coEvery { refreshTradeThread(any(), any()) } coAnswers { gate.await(); Result.success(Unit) }

        val vm = createViewModel()
        advanceUntilIdle()
        vm.onScreenEntered()
        vm.refresh()
        advanceUntilIdle()
        gate.complete(Unit)
        advanceUntilIdle()

        coVerify(exactly = 1) { refreshTradeThread(ROOT_PROPOSAL_ID, USER_A) }
        assertFalse(vm.uiState.value.isRefreshing)
    }

    @Test
    fun `given a return to the screen then the thread is refreshed again`() = runTest {
        sessionFlow.value = authenticated(USER_A)
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onScreenEntered()
        advanceUntilIdle()
        vm.onScreenEntered()
        advanceUntilIdle()

        coVerify(exactly = 2) { refreshTradeThread(ROOT_PROPOSAL_ID, USER_A) }
    }

    @Test
    fun `given an empty cache then the thread stays loading until the first refresh finishes`() = runTest {
        sessionFlow.value = authenticated(USER_A)
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        coEvery { refreshTradeThread(any(), any()) } coAnswers { gate.await(); Result.success(Unit) }

        val vm = createViewModel()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isLoading)

        gate.complete(Unit)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isLoading)
        assertFalse(vm.uiState.value.refreshFailed)
    }

    @Test
    fun `given the first refresh fails with nothing cached then refreshFailed is set`() = runTest {
        sessionFlow.value = authenticated(USER_A)
        coEvery { refreshTradeThread(any(), any()) } returns Result.failure(RuntimeException("offline"))

        val vm = createViewModel()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isLoading)
        assertTrue(vm.uiState.value.refreshFailed)
        assertTrue(vm.uiState.value.thread.isEmpty())
    }

    @Test
    fun `given the account switches then the new account's thread is refreshed and old dialogs are dropped`() = runTest {
        threadFlow.value = listOf(buildProposal(id = "p1", status = TradeStatus.PROPOSED))
        sessionFlow.value = authenticated(USER_A)
        val vm = createViewModel()
        advanceUntilIdle()
        vm.onCancelRequested("p1")

        sessionFlow.value = authenticated(USER_B)
        advanceUntilIdle()

        coVerify(exactly = 1) { refreshTradeThread(ROOT_PROPOSAL_ID, USER_B) }
        assertEquals(USER_B, vm.uiState.value.currentUserId)
        assertNull(vm.uiState.value.pendingCancelProposalId)
    }

    @Test
    fun `given a user without a nickname then no blank participant name is stored`() = runTest {
        sessionFlow.value = SessionState.Authenticated(
            AuthUser(id = USER_A, email = "a@test.com", nickname = null, gameTag = "#TAG", avatarUrl = null, provider = "email"),
        )

        val vm = createViewModel()
        advanceUntilIdle()

        assertFalse(USER_A in vm.uiState.value.participantNames)
    }
}
