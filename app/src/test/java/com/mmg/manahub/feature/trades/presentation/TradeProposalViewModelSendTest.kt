package com.mmg.manahub.feature.trades.presentation

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.R
import com.mmg.manahub.core.domain.auth.AuthRepository
import com.mmg.manahub.core.domain.auth.AuthUser
import com.mmg.manahub.core.domain.auth.SessionState
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.domain.repository.FriendRepository
import com.mmg.manahub.core.domain.repository.OpenForTradeRepository
import com.mmg.manahub.core.domain.repository.UserCardRepository
import com.mmg.manahub.core.domain.repository.WishlistRepository
import com.mmg.manahub.core.model.OpenForTradeEntry
import com.mmg.manahub.core.model.TradeError
import com.mmg.manahub.core.model.UserCardWithCard
import com.mmg.manahub.core.model.WishlistEntry
import com.mmg.manahub.core.model.toUserFacingMessage
import com.mmg.manahub.core.data.repository.TradesRepository
import com.mmg.manahub.core.util.AnalyticsHelper
import com.mmg.manahub.feature.trades.domain.usecase.CounterProposalUseCase
import com.mmg.manahub.feature.trades.domain.usecase.CreateTradeProposalUseCase
import com.mmg.manahub.feature.trades.domain.usecase.EditProposalUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for the send/save/validation paths of [TradeProposalViewModel] (the golden-matches
 * computation is covered separately by [TradeProposalViewModelMatchesTest]).
 *
 * Covers:
 *  - GROUP 1: onSendProposal — local validation sentinels (NOT_LOGGED_IN / NO_RECEIVER /
 *    SELF_TRADE / INITIAL_ASYMMETRY) map to the correct typed [ProposalEvent.ShowValidationError]
 *  - GROUP 2: onSaveDraft — the same local validation applies to the draft path
 *  - GROUP 3: onSendProposal — success branches (new proposal / edit / counter)
 *  - GROUP 4: onSendProposal — remote error mapping (typed [TradeError] vs untyped exception)
 *  - GROUP 5: §2.7 double-tap duplicate-proposal regression
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TradeProposalViewModelSendTest {

    private val testDispatcher = StandardTestDispatcher()

    // ── Mocks ─────────────────────────────────────────────────────────────────

    private val authRepository = mockk<AuthRepository>()
    private val tradesRepository = mockk<TradesRepository>(relaxed = true)
    private val createProposal = mockk<CreateTradeProposalUseCase>()
    private val editProposal = mockk<EditProposalUseCase>()
    private val counterProposal = mockk<CounterProposalUseCase>()
    private val cardRepository = mockk<CardRepository>(relaxed = true)
    private val userCardRepository = mockk<UserCardRepository>(relaxed = true)
    private val wishlistRepository = mockk<WishlistRepository>(relaxed = true)
    private val openForTradeRepository = mockk<OpenForTradeRepository>(relaxed = true)
    private val friendRepository = mockk<FriendRepository>(relaxed = true)
    private val analyticsHelper = mockk<AnalyticsHelper>(relaxed = true)

    // ── Shared flows ──────────────────────────────────────────────────────────

    private val sessionFlow = MutableStateFlow<SessionState>(SessionState.Loading)
    private val collectionFlow = MutableStateFlow<List<UserCardWithCard>>(emptyList())
    private val wishlistFlow = MutableStateFlow<List<WishlistEntry>>(emptyList())
    private val offerFlow = MutableStateFlow<List<OpenForTradeEntry>>(emptyList())

    private companion object {
        const val MY_USER_ID = "user-me-001"
        const val FRIEND_USER_ID = "user-friend-002"
    }

    private fun authenticated(userId: String) = SessionState.Authenticated(
        AuthUser(id = userId, email = "$userId@test.com", nickname = "Me", gameTag = "#ME001", avatarUrl = null, provider = "email")
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic(FirebaseCrashlytics::class)
        val crashlytics = mockk<FirebaseCrashlytics>(relaxed = true)
        every { FirebaseCrashlytics.getInstance() } returns crashlytics

        every { authRepository.sessionState } returns sessionFlow
        every { userCardRepository.observeCollection() } returns collectionFlow
        every { wishlistRepository.observeLocal() } returns wishlistFlow
        every { openForTradeRepository.observeLocal() } returns offerFlow
        every { friendRepository.observeFriends() } returns MutableStateFlow(emptyList())

        // Any counter/edit prefill attempt resolves to "not found" harmlessly — these tests
        // exercise the send/save decision branches, not the prefill machinery itself.
        every { tradesRepository.observeProposalThread(any()) } returns flowOf(emptyList())
        coEvery { tradesRepository.refreshProposalThread(any(), any()) } returns Result.failure(RuntimeException("not found"))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(FirebaseCrashlytics::class)
    }

    private fun createViewModel(
        receiverId: String = "",
        editingProposalId: String? = null,
        parentProposalId: String? = null,
        rootProposalId: String? = null,
    ): TradeProposalViewModel {
        val args = buildMap<String, String> {
            if (receiverId.isNotBlank()) put("receiverId", receiverId)
            editingProposalId?.let { put("editingProposalId", it) }
            parentProposalId?.let { put("parentProposalId", it) }
            rootProposalId?.let { put("rootProposalId", it) }
        }
        return TradeProposalViewModel(
            savedStateHandle = SavedStateHandle(args),
            authRepository = authRepository,
            tradesRepository = tradesRepository,
            createProposal = createProposal,
            editProposal = editProposal,
            counterProposal = counterProposal,
            cardRepository = cardRepository,
            userCardRepository = userCardRepository,
            wishlistRepository = wishlistRepository,
            openForTradeRepository = openForTradeRepository,
            friendRepository = friendRepository,
            analyticsHelper = analyticsHelper,
            ioDispatcher = testDispatcher,
            defaultDispatcher = testDispatcher,
        )
    }

    // =========================================================================
    // GROUP 1: onSendProposal — local validation sentinels
    // =========================================================================

    @Test
    fun `given not logged in when onSendProposal then NOT_LOGGED_IN validation event is emitted`() = runTest {
        val vm = createViewModel(receiverId = FRIEND_USER_ID)
        advanceUntilIdle()

        vm.events.test {
            vm.onSendProposal()
            val event = awaitItem() as ProposalEvent.ShowValidationError
            assertEquals(R.string.trades_error_not_logged_in, event.messageRes)
            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 0) { createProposal(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `given logged in with blank receiverId when onSendProposal then NO_RECEIVER validation event is emitted`() = runTest {
        sessionFlow.value = authenticated(MY_USER_ID)
        val vm = createViewModel(receiverId = "")
        advanceUntilIdle()

        vm.events.test {
            vm.onSendProposal()
            val event = awaitItem() as ProposalEvent.ShowValidationError
            assertEquals(R.string.trades_error_no_receiver, event.messageRes)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given receiverId equals currentUserId when onSendProposal then SELF_TRADE validation event is emitted`() = runTest {
        sessionFlow.value = authenticated(MY_USER_ID)
        val vm = createViewModel(receiverId = MY_USER_ID)
        advanceUntilIdle()

        vm.events.test {
            vm.onSendProposal()
            val event = awaitItem() as ProposalEvent.ShowValidationError
            assertEquals(R.string.trades_error_self_trade, event.messageRes)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given both sides empty when onSendProposal then INITIAL_ASYMMETRY validation event is emitted`() = runTest {
        sessionFlow.value = authenticated(MY_USER_ID)
        val vm = createViewModel(receiverId = FRIEND_USER_ID)
        advanceUntilIdle()

        vm.events.test {
            vm.onSendProposal()
            val event = awaitItem() as ProposalEvent.ShowValidationError
            assertEquals(R.string.trades_error_initial_asymmetry, event.messageRes)
            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 0) { createProposal(any(), any(), any(), any(), any()) }
    }

    // =========================================================================
    // GROUP 2: onSaveDraft — same local validation applies
    // =========================================================================

    @Test
    fun `given blank receiverId when onSaveDraft then NO_RECEIVER validation event is emitted`() = runTest {
        sessionFlow.value = authenticated(MY_USER_ID)
        val vm = createViewModel(receiverId = "")
        advanceUntilIdle()

        vm.events.test {
            vm.onSaveDraft()
            val event = awaitItem() as ProposalEvent.ShowValidationError
            assertEquals(R.string.trades_error_no_receiver, event.messageRes)
            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 0) { createProposal(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `given both sides empty when onSaveDraft then INITIAL_ASYMMETRY validation event is emitted`() = runTest {
        sessionFlow.value = authenticated(MY_USER_ID)
        val vm = createViewModel(receiverId = FRIEND_USER_ID)
        advanceUntilIdle()

        vm.events.test {
            vm.onSaveDraft()
            val event = awaitItem() as ProposalEvent.ShowValidationError
            assertEquals(R.string.trades_error_initial_asymmetry, event.messageRes)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // =========================================================================
    // GROUP 3: onSendProposal — success branches
    // =========================================================================

    @Test
    fun `given a new proposal when onSendProposal succeeds then NavigateToThread carries the new proposal id`() = runTest {
        sessionFlow.value = authenticated(MY_USER_ID)
        val vm = createViewModel(receiverId = FRIEND_USER_ID)
        advanceUntilIdle()
        vm.toggleReviewCollectionProposer()
        vm.toggleReviewCollectionReceiver()
        coEvery { createProposal(FRIEND_USER_ID, any(), true, true, true) } returns Result.success("new-proposal-id")

        vm.events.test {
            vm.onSendProposal()
            advanceUntilIdle()
            assertEquals(ProposalEvent.NavigateToThread("new-proposal-id", "new-proposal-id"), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertFalse(vm.uiState.value.isSaving)
    }

    @Test
    fun `given a draft when onSaveDraft succeeds then NavigateToThread carries the draft id and autoSend is false`() = runTest {
        sessionFlow.value = authenticated(MY_USER_ID)
        val vm = createViewModel(receiverId = FRIEND_USER_ID)
        advanceUntilIdle()
        vm.toggleReviewCollectionProposer()
        vm.toggleReviewCollectionReceiver()
        coEvery { createProposal(FRIEND_USER_ID, any(), true, true, false) } returns Result.success("draft-id")

        vm.events.test {
            vm.onSaveDraft()
            advanceUntilIdle()
            assertEquals(ProposalEvent.NavigateToThread("draft-id", "draft-id"), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given editingProposalId set when onSendProposal succeeds then editProposal is invoked and NavigateBack is emitted`() = runTest {
        sessionFlow.value = authenticated(MY_USER_ID)
        val vm = createViewModel(receiverId = FRIEND_USER_ID, editingProposalId = "editing-id", rootProposalId = "root-id")
        advanceUntilIdle()
        vm.toggleReviewCollectionProposer()
        vm.toggleReviewCollectionReceiver()
        coEvery { editProposal("editing-id", any(), any(), any()) } returns Result.success(Unit)

        vm.events.test {
            vm.onSendProposal()
            advanceUntilIdle()
            assertEquals(ProposalEvent.NavigateBack, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 1) { editProposal("editing-id", any(), any(), any()) }
        coVerify(exactly = 0) { createProposal(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `given parentProposalId set when onSendProposal succeeds with a new counter id then NavigateToThread is emitted`() = runTest {
        sessionFlow.value = authenticated(MY_USER_ID)
        val vm = createViewModel(receiverId = FRIEND_USER_ID, parentProposalId = "parent-id", rootProposalId = "root-id")
        advanceUntilIdle()
        vm.toggleReviewCollectionProposer()
        vm.toggleReviewCollectionReceiver()
        coEvery { counterProposal("parent-id", any(), any()) } returns Result.success("counter-id")

        vm.events.test {
            vm.onSendProposal()
            advanceUntilIdle()
            assertEquals(ProposalEvent.NavigateToThread("counter-id", "root-id"), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given parentProposalId set when onSendProposal succeeds with a blank id then NavigateBack is emitted`() = runTest {
        sessionFlow.value = authenticated(MY_USER_ID)
        val vm = createViewModel(receiverId = FRIEND_USER_ID, parentProposalId = "parent-id", rootProposalId = "root-id")
        advanceUntilIdle()
        vm.toggleReviewCollectionProposer()
        vm.toggleReviewCollectionReceiver()
        coEvery { counterProposal("parent-id", any(), any()) } returns Result.success("")

        vm.events.test {
            vm.onSendProposal()
            advanceUntilIdle()
            assertEquals(ProposalEvent.NavigateBack, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // =========================================================================
    // GROUP 4: onSendProposal — remote error mapping
    // =========================================================================

    @Test
    fun `given send fails with ProposalVersionMismatch then a version-mismatch validation event is emitted`() = runTest {
        sessionFlow.value = authenticated(MY_USER_ID)
        val vm = createViewModel(receiverId = FRIEND_USER_ID)
        advanceUntilIdle()
        vm.toggleReviewCollectionProposer(); vm.toggleReviewCollectionReceiver()
        coEvery { createProposal(any(), any(), any(), any(), any()) } returns Result.failure(TradeError.ProposalVersionMismatch)

        vm.events.test {
            vm.onSendProposal()
            advanceUntilIdle()
            val event = awaitItem() as ProposalEvent.ShowValidationError
            assertEquals(R.string.trades_version_mismatch, event.messageRes)
            cancelAndIgnoreRemainingEvents()
        }
        assertFalse(vm.uiState.value.isSaving)
    }

    @Test
    fun `given send fails with InitialAsymmetryNotAllowed from the server then the initial-asymmetry validation event is emitted`() = runTest {
        sessionFlow.value = authenticated(MY_USER_ID)
        val vm = createViewModel(receiverId = FRIEND_USER_ID)
        advanceUntilIdle()
        vm.toggleReviewCollectionProposer(); vm.toggleReviewCollectionReceiver()
        coEvery { createProposal(any(), any(), any(), any(), any()) } returns Result.failure(TradeError.InitialAsymmetryNotAllowed)

        vm.events.test {
            vm.onSendProposal()
            advanceUntilIdle()
            val event = awaitItem() as ProposalEvent.ShowValidationError
            assertEquals(R.string.trades_error_initial_asymmetry, event.messageRes)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given send fails with another typed TradeError then ShowRemoteError carries its friendly message`() = runTest {
        sessionFlow.value = authenticated(MY_USER_ID)
        val vm = createViewModel(receiverId = FRIEND_USER_ID)
        advanceUntilIdle()
        vm.toggleReviewCollectionProposer(); vm.toggleReviewCollectionReceiver()
        coEvery { createProposal(any(), any(), any(), any(), any()) } returns Result.failure(TradeError.NotFriends)

        vm.events.test {
            vm.onSendProposal()
            advanceUntilIdle()
            val event = awaitItem() as ProposalEvent.ShowRemoteError
            assertEquals(TradeError.NotFriends.toUserFacingMessage(), event.message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given send fails with an untyped exception then ShowRemoteError message is null (raw server text never shown)`() = runTest {
        sessionFlow.value = authenticated(MY_USER_ID)
        val vm = createViewModel(receiverId = FRIEND_USER_ID)
        advanceUntilIdle()
        vm.toggleReviewCollectionProposer(); vm.toggleReviewCollectionReceiver()
        coEvery { createProposal(any(), any(), any(), any(), any()) } returns
            Result.failure(RuntimeException("raw_internal_server_sentinel"))

        vm.events.test {
            vm.onSendProposal()
            advanceUntilIdle()
            val event = awaitItem() as ProposalEvent.ShowRemoteError
            assertNull(event.message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // =========================================================================
    // GROUP 5: §2.7 double-tap duplicate-proposal regression
    // =========================================================================

    @Test
    fun `given two rapid onSendProposal calls then createProposal is invoked only once`() = runTest {
        sessionFlow.value = authenticated(MY_USER_ID)
        val vm = createViewModel(receiverId = FRIEND_USER_ID)
        advanceUntilIdle()
        vm.toggleReviewCollectionProposer(); vm.toggleReviewCollectionReceiver()
        coEvery { createProposal(any(), any(), any(), any(), any()) } returns Result.success("new-id")

        vm.onSendProposal()
        vm.onSendProposal()
        advanceUntilIdle()

        coVerify(exactly = 1) { createProposal(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `given two rapid onSaveDraft calls then createProposal is invoked only once`() = runTest {
        sessionFlow.value = authenticated(MY_USER_ID)
        val vm = createViewModel(receiverId = FRIEND_USER_ID)
        advanceUntilIdle()
        vm.toggleReviewCollectionProposer(); vm.toggleReviewCollectionReceiver()
        coEvery { createProposal(any(), any(), any(), any(), false) } returns Result.success("draft-id")

        vm.onSaveDraft()
        vm.onSaveDraft()
        advanceUntilIdle()

        coVerify(exactly = 1) { createProposal(any(), any(), any(), any(), false) }
    }

    @Test
    fun `given a mixed rapid onSendProposal then onSaveDraft when the first is still in flight then only the first call executes`() = runTest {
        sessionFlow.value = authenticated(MY_USER_ID)
        val vm = createViewModel(receiverId = FRIEND_USER_ID)
        advanceUntilIdle()
        vm.toggleReviewCollectionProposer(); vm.toggleReviewCollectionReceiver()
        coEvery { createProposal(any(), any(), any(), any(), any()) } returns Result.success("new-id")

        // isSaving is one shared flag guarding BOTH entry points.
        vm.onSendProposal()
        vm.onSaveDraft()
        advanceUntilIdle()

        coVerify(exactly = 1) { createProposal(any(), any(), any(), any(), any()) }
    }
}
