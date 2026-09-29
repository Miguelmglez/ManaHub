package com.mmg.manahub.feature.friends.presentation.invite

import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.data.local.PendingInviteStore
import com.mmg.manahub.core.domain.auth.AuthRepository
import com.mmg.manahub.core.domain.auth.AuthUser
import com.mmg.manahub.core.domain.auth.SessionState
import com.mmg.manahub.core.model.AcceptInviteResult
import com.mmg.manahub.feature.friends.domain.usecase.AcceptInviteUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InviteDispatcherViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    private val acceptInvite = mockk<AcceptInviteUseCase>()
    private val store = mockk<PendingInviteStore>()
    private val authRepo = mockk<AuthRepository>()
    private val crashReporter = mockk<CrashReporter>(relaxed = true)

    private val session = MutableStateFlow<SessionState>(SessionState.Loading)
    private val stored = MutableStateFlow<String?>(null)
    private val user = AuthUser("me", null, null, null, null, "email")

    private lateinit var viewModel: InviteDispatcherViewModel
    private lateinit var events: MutableList<InviteDispatcherViewModel.UiEvent>

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { authRepo.sessionState } returns session
        every { store.flow } returns stored
        coEvery { store.save(any()) } answers { stored.value = firstArg() }
        coEvery { store.clear() } answers { stored.value = null }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.create() {
        viewModel = InviteDispatcherViewModel(acceptInvite, store, authRepo, crashReporter)
        events = mutableListOf()
        backgroundScope.launch { viewModel.events.collect { events += it } }
    }

    @Test
    fun `a lowercase code is accepted as its uppercase form`() = runTest(dispatcher) {
        session.value = SessionState.Authenticated(user)
        coEvery { acceptInvite("ABCD2345") } returns Result.success(AcceptInviteResult("inviter", "Gandalf"))
        create()

        viewModel.handleInviteCode("abcd2345")

        coVerify(exactly = 1) { acceptInvite("ABCD2345") }
        assertEquals(
            listOf(InviteDispatcherViewModel.UiEvent.InviteAccepted("Gandalf"), InviteDispatcherViewModel.UiEvent.NavigateAway),
            events,
        )
    }

    @Test
    fun `a malformed code never calls the server`() = runTest(dispatcher) {
        session.value = SessionState.Authenticated(user)
        create()

        viewModel.handleInviteCode("bad!")

        coVerify(exactly = 0) { acceptInvite(any()) }
        assertEquals(
            InviteDispatcherViewModel.UiEvent.InviteError(InviteErrorReason.INVALID_CODE),
            events.first(),
        )
    }

    @Test
    fun `a re-run while the request is in flight sends no second request`() = runTest(dispatcher) {
        session.value = SessionState.Authenticated(user)
        val gate = CompletableDeferred<Result<AcceptInviteResult>>()
        coEvery { acceptInvite(any()) } coAnswers { gate.await() }
        create()

        viewModel.handleInviteCode("ABCD2345")
        viewModel.handleInviteCode("abcd2345")
        gate.complete(Result.success(AcceptInviteResult("inviter", null)))

        coVerify(exactly = 1) { acceptInvite(any()) }
        assertEquals(1, events.count { it is InviteDispatcherViewModel.UiEvent.InviteAccepted })
    }

    @Test
    fun `an already accepted code is not sent again after re-entry`() = runTest(dispatcher) {
        session.value = SessionState.Authenticated(user)
        coEvery { acceptInvite(any()) } returns Result.success(AcceptInviteResult("inviter", null))
        create()

        viewModel.handleInviteCode("ABCD2345")
        viewModel.handleInviteCode("ABCD2345")

        coVerify(exactly = 1) { acceptInvite(any()) }
    }

    @Test
    fun `a still-loading session waits instead of deferring the code`() = runTest(dispatcher) {
        coEvery { acceptInvite(any()) } returns Result.success(AcceptInviteResult("inviter", null))
        create()

        viewModel.handleInviteCode("ABCD2345")
        coVerify(exactly = 0) { acceptInvite(any()) }
        coVerify(exactly = 0) { store.save(any()) }

        session.value = SessionState.Authenticated(user)

        coVerify(exactly = 1) { acceptInvite("ABCD2345") }
    }

    @Test
    fun `a signed-out user keeps the code until sign-in, then it is accepted without navigating`() = runTest(dispatcher) {
        session.value = SessionState.Unauthenticated
        coEvery { acceptInvite(any()) } returns Result.success(AcceptInviteResult("inviter", "Gandalf"))
        create()

        viewModel.handleInviteCode("ABCD2345")
        assertEquals("ABCD2345", stored.value)
        assertEquals(listOf(InviteDispatcherViewModel.UiEvent.NavigateAway), events)

        session.value = SessionState.Authenticated(user)

        coVerify(exactly = 1) { acceptInvite("ABCD2345") }
        assertNull(stored.value)
        assertEquals(InviteDispatcherViewModel.UiEvent.InviteAccepted("Gandalf"), events.last())
    }

    @Test
    fun `a transient failure keeps the code and does not loop`() = runTest(dispatcher) {
        session.value = SessionState.Authenticated(user)
        coEvery { acceptInvite(any()) } returns Result.failure(Exception("UNKNOWN_ERROR"))
        create()

        viewModel.handleInviteCode("ABCD2345")

        assertEquals("ABCD2345", stored.value)
        coVerify(exactly = 1) { acceptInvite(any()) }
        assertTrue(events.contains(InviteDispatcherViewModel.UiEvent.InviteError(InviteErrorReason.GENERIC)))
    }

    @Test
    fun `a self invite is permanent and drops the stored code`() = runTest(dispatcher) {
        stored.value = "ABCD2345"
        session.value = SessionState.Unauthenticated
        coEvery { acceptInvite(any()) } returns Result.failure(Exception("SELF_INVITE"))
        create()

        session.value = SessionState.Authenticated(user)

        assertNull(stored.value)
        assertEquals(
            listOf(InviteDispatcherViewModel.UiEvent.InviteError(InviteErrorReason.SELF_INVITE)),
            events,
        )
    }

    @Test
    fun `normalize and validation accept any case of the referral alphabet`() {
        assertEquals("ABCD2345", InviteDispatcherViewModel.normalize(" abcd2345 "))
        assertTrue(InviteDispatcherViewModel.isValidReferralCode("ABCD2345"))
        assertTrue(!InviteDispatcherViewModel.isValidReferralCode("ABCD0345"))
    }
}
