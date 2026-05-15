package com.mmg.manahub.feature.friends.presentation

import app.cash.turbine.test
import com.mmg.manahub.core.ui.components.MagicToastType
import com.mmg.manahub.core.util.AnalyticsHelper
import com.mmg.manahub.core.util.CardConstants
import com.mmg.manahub.feature.auth.domain.model.AuthUser
import com.mmg.manahub.feature.auth.domain.model.SessionState
import com.mmg.manahub.feature.auth.domain.repository.AuthRepository
import com.mmg.manahub.feature.friends.domain.model.Friend
import com.mmg.manahub.feature.friends.domain.model.OutgoingFriendRequest
import com.mmg.manahub.feature.friends.domain.repository.FriendRepository
import com.mmg.manahub.feature.friends.domain.usecase.SearchUserByGameTagUseCase
import com.mmg.manahub.feature.friends.domain.usecase.SendFriendRequestUseCase
import com.mmg.manahub.feature.friends.domain.usecase.ShareInviteUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
 * Unit tests for [FriendsViewModel] — Phase 1 (friends-v2).
 *
 * Strategy:
 * - [FriendRepository] and [AuthRepository] are mocked with MockK.
 * - [SearchUserByGameTagUseCase] and [SendFriendRequestUseCase] are mocked.
 * - [UnconfinedTestDispatcher] is used so coroutines complete eagerly without
 *   needing [advanceUntilIdle] in most cases.
 * - [AuthRepository.sessionState] is backed by a [MutableStateFlow] so we can
 *   drive authentication state transitions per test.
 *
 * Key invariants:
 * - The user types the game tag WITHOUT the "#" prefix (e.g. "A1B2C3", 6 chars).
 *   The ViewModel internally prepends "#" before calling [SearchUserByGameTagUseCase].
 * - [GAME_TAG_LENGTH] = 7 (with "#"); the user input must be exactly 6 chars.
 * - [triggerSearch] validates query.length != (GAME_TAG_LENGTH - 1), i.e. != 6.
 * - [onSearchQueryChange] must NOT trigger a search automatically.
 * - [UiState.toastType] defaults to [MagicToastType.ERROR]; success paths emit [MagicToastType.SUCCESS].
 * - [clearToast] resets both [UiState.toastMessage] to null and [UiState.toastType] to ERROR.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FriendsViewModelTest {

    // ── Test dispatcher ───────────────────────────────────────────────────────

    private val testDispatcher = UnconfinedTestDispatcher()

    // ── Mocks ─────────────────────────────────────────────────────────────────

    private val friendRepo         = mockk<FriendRepository>(relaxed = true)
    private val authRepo           = mockk<AuthRepository>()
    private val searchUseCase      = mockk<SearchUserByGameTagUseCase>()
    private val sendRequestUseCase = mockk<SendFriendRequestUseCase>()
    private val shareInviteUseCase = mockk<ShareInviteUseCase>(relaxed = true)
    private val analyticsHelper    = mockk<AnalyticsHelper>(relaxed = true)

    // Controls sessionState emissions
    private val sessionStateFlow = MutableStateFlow<SessionState>(SessionState.Unauthenticated)

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private val dummyAuthUser = AuthUser(
        id        = "user-uuid-001",
        email     = "test@example.com",
        nickname  = "TestUser",
        gameTag   = "#A1B2C3",
        avatarUrl = null,
        provider  = "email",
    )

    private val dummyFriend = Friend(
        id        = "friendship-001",
        userId    = "user-uuid-002",
        nickname  = "Gandalf",
        gameTag   = "#XYZ1234",
        avatarUrl = null,
    )

    // ── SUT ───────────────────────────────────────────────────────────────────

    private lateinit var viewModel: FriendsViewModel

    // ── Setup / Teardown ─────────────────────────────────────────────────────

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        every { authRepo.sessionState } returns sessionStateFlow

        // Default stubs for FriendRepository flows — return empty lists so the ViewModel
        // does not crash on init.
        every { friendRepo.observeFriends() }           returns flowOf(emptyList())
        every { friendRepo.observePendingRequests() }   returns flowOf(emptyList())
        every { friendRepo.observeOutgoingRequests() }  returns flowOf(emptyList())

        // Default stub: all refresh operations succeed silently.
        coEvery { friendRepo.refreshFriends(any()) }          returns Result.success(Unit)
        coEvery { friendRepo.refreshRequests(any()) }         returns Result.success(Unit)
        coEvery { friendRepo.refreshOutgoingRequests(any()) } returns Result.success(Unit)

        viewModel = FriendsViewModel(
            friendRepo         = friendRepo,
            authRepo           = authRepo,
            searchUseCase      = searchUseCase,
            sendRequestUseCase = sendRequestUseCase,
            shareInviteUseCase = shareInviteUseCase,
            analyticsHelper    = analyticsHelper,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 1 — onSearchQueryChange (must NOT auto-trigger search)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given onSearchQueryChange called when query is valid length then search does NOT trigger automatically`() = runTest {
        // The user types the tag WITHOUT the "#" prefix. Exactly 6 chars is valid (GAME_TAG_LENGTH - 1).
        // onSearchQueryChange must only update state — triggerSearch() is required to call the backend.
        val userInput = "A1B2C3" // 6 chars, no "#" prefix — the new valid format

        viewModel.onSearchQueryChange(userInput)
        advanceUntilIdle()

        // searchUseCase must never be invoked by onSearchQueryChange
        coVerify(exactly = 0) { searchUseCase(any()) }
        assertEquals(userInput, viewModel.uiState.value.searchQuery)
    }

    @Test
    fun `given onSearchQueryChange called then searchResult is cleared and searchPerformed resets to false`() = runTest {
        // Any pending search result from a previous triggerSearch must be cleared when the
        // user modifies the query text.
        viewModel.onSearchQueryChange("A1B2C3")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.searchResult)
        assertFalse(state.searchPerformed)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 2 — triggerSearch length validation
    //
    //  GAME_TAG_LENGTH = 7 (full tag with "#").
    //  User input = tag without "#" → valid length = GAME_TAG_LENGTH - 1 = 6.
    //  The ViewModel prepends "#" before calling the use case.
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given query shorter than 6 chars when triggerSearch then no backend call is made`() = runTest {
        // After trimming, a 5-char input is != 6, so the ViewModel must reject it client-side.
        val shortInput = "ABC12" // 5 chars — too short

        viewModel.onSearchQueryChange(shortInput)
        viewModel.triggerSearch()
        advanceUntilIdle()

        coVerify(exactly = 0) { searchUseCase(any()) }
        // searchPerformed is set to true so the UI can show a validation message
        assertTrue(viewModel.uiState.value.searchPerformed)
        assertNull(viewModel.uiState.value.searchResult)
    }

    @Test
    fun `given query longer than 6 chars when triggerSearch then no backend call is made`() = runTest {
        // A 7-char input (which equals the old full-tag format with "#") must now be rejected
        // because the user should NOT type the "#" prefix themselves.
        val longInput = "#A1B2C3" // 7 chars — one char too many for user input

        viewModel.onSearchQueryChange(longInput)
        viewModel.triggerSearch()
        advanceUntilIdle()

        coVerify(exactly = 0) { searchUseCase(any()) }
        assertTrue(viewModel.uiState.value.searchPerformed)
        assertNull(viewModel.uiState.value.searchResult)
    }

    @Test
    fun `given query exactly 6 chars when triggerSearch then searchUseCase is called with hash-prefixed tag`() = runTest {
        // Exactly 6 characters is the only valid user-input length (GAME_TAG_LENGTH - 1).
        // The ViewModel must prepend "#" so the use case receives "#A1B2C3".
        val userInput     = "A1B2C3"               // what the user types (6 chars)
        val expectedArg   = "#$userInput"           // what the ViewModel passes to the use case
        assertEquals(CardConstants.GAME_TAG_LENGTH - 1, userInput.length)

        coEvery { searchUseCase(expectedArg) } returns Result.success(null)

        viewModel.onSearchQueryChange(userInput)
        viewModel.triggerSearch()
        advanceUntilIdle()

        coVerify(exactly = 1) { searchUseCase(expectedArg) }
    }

    @Test
    fun `given query with surrounding whitespace and correct trimmed length when triggerSearch then searchUseCase is called with trimmed hash-prefixed tag`() = runTest {
        // triggerSearch trims the query before checking length — leading/trailing spaces must
        // not disqualify an otherwise valid 6-char game tag.
        val userInput   = "A1B2C3"
        val expectedArg = "#$userInput"
        assertEquals(CardConstants.GAME_TAG_LENGTH - 1, userInput.length)

        coEvery { searchUseCase(expectedArg) } returns Result.success(null)

        viewModel.onSearchQueryChange("  $userInput  ")
        viewModel.triggerSearch()
        advanceUntilIdle()

        coVerify(exactly = 1) { searchUseCase(expectedArg) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 3 — triggerSearch result handling
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given valid query and search returns result when triggerSearch then searchResult is populated`() = runTest {
        val userInput   = "A1B2C3"
        val expectedArg = "#$userInput"
        coEvery { searchUseCase(expectedArg) } returns Result.success(dummyFriend)

        viewModel.onSearchQueryChange(userInput)
        viewModel.triggerSearch()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(dummyFriend, state.searchResult)
        assertTrue(state.searchPerformed)
        assertFalse(state.isSearching)
    }

    @Test
    fun `given valid query and search returns null when triggerSearch then searchResult is null and searchPerformed is true`() = runTest {
        // null result means no user was found with that game tag
        val userInput   = "A1B2C3"
        val expectedArg = "#$userInput"
        coEvery { searchUseCase(expectedArg) } returns Result.success(null)

        viewModel.onSearchQueryChange(userInput)
        viewModel.triggerSearch()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.searchResult)
        assertTrue(state.searchPerformed)
        assertFalse(state.isSearching)
    }

    @Test
    fun `given valid query and search fails when triggerSearch then searchResult is null and searchPerformed is true`() = runTest {
        // Network failures must not crash the ViewModel — searchResult stays null and
        // the UI should interpret searchPerformed=true + searchResult=null as "not found".
        val userInput   = "A1B2C3"
        val expectedArg = "#$userInput"
        coEvery { searchUseCase(expectedArg) } returns Result.failure(Exception("Network error"))

        viewModel.onSearchQueryChange(userInput)
        viewModel.triggerSearch()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.searchResult)
        assertTrue(state.searchPerformed)
        assertFalse(state.isSearching)
    }

    @Test
    fun `given triggerSearch in progress when called then isSearching becomes false after completion`() = runTest {
        val userInput   = "A1B2C3"
        val expectedArg = "#$userInput"

        viewModel.uiState.test {
            awaitItem() // initial state

            coEvery { searchUseCase(expectedArg) } returns Result.success(dummyFriend)
            viewModel.onSearchQueryChange(userInput)
            // onSearchQueryChange emits a new state (query updated, result/performed cleared)
            awaitItem()

            viewModel.triggerSearch()
            // UnconfinedTestDispatcher completes the coroutine eagerly, so isSearching=true
            // and the final state are both emitted; we only care the final state is correct.
            cancelAndIgnoreRemainingEvents()
        }

        // After completion, isSearching must be false
        assertFalse(viewModel.uiState.value.isSearching)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 4 — authentication state integration
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given unauthenticated session when init then isLoggedIn is false`() = runTest {
        // sessionStateFlow starts as Unauthenticated in setUp()
        assertFalse(viewModel.uiState.value.isLoggedIn)
    }

    @Test
    fun `given session transitions to Authenticated when observed then isLoggedIn becomes true`() = runTest {
        viewModel.uiState.test {
            awaitItem() // initial Unauthenticated state

            sessionStateFlow.value = SessionState.Authenticated(dummyAuthUser)
            val updatedState = awaitItem()
            assertTrue(updatedState.isLoggedIn)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given authenticated session when init then loadData is called via refreshFriends and refreshRequests`() = runTest {
        // Simulate being already authenticated at ViewModel construction time
        sessionStateFlow.value = SessionState.Authenticated(dummyAuthUser)

        val vm = FriendsViewModel(
            friendRepo         = friendRepo,
            authRepo           = authRepo,
            searchUseCase      = searchUseCase,
            sendRequestUseCase = sendRequestUseCase,
            shareInviteUseCase = shareInviteUseCase,
            analyticsHelper    = analyticsHelper,
        )
        advanceUntilIdle()

        coVerify(atLeast = 1) { friendRepo.refreshFriends(dummyAuthUser.id) }
        coVerify(atLeast = 1) { friendRepo.refreshRequests(dummyAuthUser.id) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 5 — empty query edge cases
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given empty query when triggerSearch then no backend call is made`() = runTest {
        viewModel.onSearchQueryChange("")
        viewModel.triggerSearch()
        advanceUntilIdle()

        coVerify(exactly = 0) { searchUseCase(any()) }
        assertTrue(viewModel.uiState.value.searchPerformed)
    }

    @Test
    fun `given blank whitespace query when triggerSearch then no backend call is made`() = runTest {
        // After trim() a whitespace-only string becomes empty, length 0 != 6
        viewModel.onSearchQueryChange("       ")
        viewModel.triggerSearch()
        advanceUntilIdle()

        coVerify(exactly = 0) { searchUseCase(any()) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 6 — Toast types (toastType field in UiState)
    //
    //  Default: MagicToastType.ERROR
    //  Success paths → MagicToastType.SUCCESS
    //  Failure paths → MagicToastType.ERROR
    //  clearToast()  → toastMessage = null, toastType = ERROR
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given sendFriendRequest succeeds then toastMessage is set and toastType is SUCCESS`() = runTest {
        sessionStateFlow.value = SessionState.Authenticated(dummyAuthUser)
        advanceUntilIdle()

        coEvery { sendRequestUseCase(any(), any()) } returns Result.success(Unit)

        viewModel.sendFriendRequest(
            toUserId  = "user-uuid-002",
            errorMsg  = "Could not send request",
            sentMsg   = "Friend request sent!",
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Friend request sent!", state.toastMessage)
        assertEquals(MagicToastType.SUCCESS, state.toastType)
    }

    @Test
    fun `given sendFriendRequest fails then toastMessage is set and toastType is ERROR`() = runTest {
        sessionStateFlow.value = SessionState.Authenticated(dummyAuthUser)
        advanceUntilIdle()

        coEvery { sendRequestUseCase(any(), any()) } returns Result.failure(Exception("Network error"))

        viewModel.sendFriendRequest(
            toUserId  = "user-uuid-002",
            errorMsg  = "Could not send request",
            sentMsg   = "Friend request sent!",
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Could not send request", state.toastMessage)
        assertEquals(MagicToastType.ERROR, state.toastType)
    }

    @Test
    fun `given acceptRequest succeeds then toastMessage is set and toastType is SUCCESS`() = runTest {
        sessionStateFlow.value = SessionState.Authenticated(dummyAuthUser)
        advanceUntilIdle()

        coEvery { friendRepo.acceptRequest(any(), any()) } returns Result.success(Unit)

        viewModel.acceptRequest(
            requestId  = "req-id-001",
            errorMsg   = "Could not accept",
            successMsg = "Friend request accepted!",
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Friend request accepted!", state.toastMessage)
        assertEquals(MagicToastType.SUCCESS, state.toastType)
    }

    @Test
    fun `given acceptRequest fails then toastMessage is set and toastType is ERROR`() = runTest {
        sessionStateFlow.value = SessionState.Authenticated(dummyAuthUser)
        advanceUntilIdle()

        coEvery { friendRepo.acceptRequest(any(), any()) } returns Result.failure(Exception("Server error"))

        viewModel.acceptRequest(
            requestId  = "req-id-001",
            errorMsg   = "Could not accept",
            successMsg = "Friend request accepted!",
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Could not accept", state.toastMessage)
        assertEquals(MagicToastType.ERROR, state.toastType)
    }

    @Test
    fun `given rejectRequest succeeds then toastMessage is set and toastType is SUCCESS`() = runTest {
        coEvery { friendRepo.rejectRequest(any()) } returns Result.success(Unit)

        viewModel.rejectRequest(
            requestId  = "req-id-002",
            errorMsg   = "Could not reject",
            successMsg = "Request declined.",
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Request declined.", state.toastMessage)
        assertEquals(MagicToastType.SUCCESS, state.toastType)
    }

    @Test
    fun `given rejectRequest fails then toastMessage is set and toastType is ERROR`() = runTest {
        coEvery { friendRepo.rejectRequest(any()) } returns Result.failure(Exception("Server error"))

        viewModel.rejectRequest(
            requestId  = "req-id-002",
            errorMsg   = "Could not reject",
            successMsg = "Request declined.",
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Could not reject", state.toastMessage)
        assertEquals(MagicToastType.ERROR, state.toastType)
    }

    @Test
    fun `given clearToast called then toastMessage is null and toastType resets to ERROR`() = runTest {
        // Manually put a SUCCESS toast into the state, then verify clearToast wipes both fields.
        sessionStateFlow.value = SessionState.Authenticated(dummyAuthUser)
        advanceUntilIdle()

        coEvery { sendRequestUseCase(any(), any()) } returns Result.success(Unit)
        viewModel.sendFriendRequest(
            toUserId  = "user-uuid-002",
            errorMsg  = "Error",
            sentMsg   = "Sent!",
        )
        advanceUntilIdle()

        // Pre-condition: toast is showing
        assertEquals(MagicToastType.SUCCESS, viewModel.uiState.value.toastType)

        viewModel.clearToast()

        val state = viewModel.uiState.value
        assertNull(state.toastMessage)
        assertEquals(MagicToastType.ERROR, state.toastType)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 7 — Outgoing requests (cancelOutgoingRequest + UiState field)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given observeOutgoingRequests emits list when ViewModel initializes then outgoingRequests in UiState updates`() = runTest {
        val outgoing = listOf(
            OutgoingFriendRequest(
                id          = "id1",
                toUserId    = "uid2",
                toNickname  = "Wizard",
                toGameTag   = "#B2C3D4",
                toAvatarUrl = null,
            )
        )
        every { friendRepo.observeOutgoingRequests() } returns flowOf(outgoing)

        // Construct a fresh ViewModel to pick up the overridden flow stub
        val vm = FriendsViewModel(
            friendRepo         = friendRepo,
            authRepo           = authRepo,
            searchUseCase      = searchUseCase,
            sendRequestUseCase = sendRequestUseCase,
            shareInviteUseCase = shareInviteUseCase,
            analyticsHelper    = analyticsHelper,
        )
        advanceUntilIdle()

        assertEquals(outgoing, vm.uiState.value.outgoingRequests)
    }

    @Test
    fun `given observeOutgoingRequests emits empty list then outgoingRequests in UiState is empty`() = runTest {
        // The default setUp already stubs observeOutgoingRequests() with emptyList(), so the
        // default viewModel is sufficient for this assertion.
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.outgoingRequests.isEmpty())
    }

    @Test
    fun `given cancelOutgoingRequest succeeds then toastMessage is successMsg and toastType is SUCCESS`() = runTest {
        coEvery { friendRepo.cancelOutgoingRequest("req-id") } returns Result.success(Unit)

        viewModel.cancelOutgoingRequest(
            friendshipId = "req-id",
            errorMsg     = "Could not cancel",
            successMsg   = "Request cancelled.",
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Request cancelled.", state.toastMessage)
        assertEquals(MagicToastType.SUCCESS, state.toastType)
    }

    @Test
    fun `given cancelOutgoingRequest fails then toastMessage is errorMsg and toastType is ERROR`() = runTest {
        coEvery { friendRepo.cancelOutgoingRequest("req-id") } returns Result.failure(Exception("Network error"))

        viewModel.cancelOutgoingRequest(
            friendshipId = "req-id",
            errorMsg     = "Could not cancel",
            successMsg   = "Request cancelled.",
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Could not cancel", state.toastMessage)
        assertEquals(MagicToastType.ERROR, state.toastType)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 8 — refreshOutgoingRequests integration with loadData
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given authenticated session when ViewModel initializes then refreshOutgoingRequests is called with the current user id`() = runTest {
        // refreshOutgoingRequests must be part of loadData so that the outgoing-request cache
        // is warmed up every time the user lands on the Friends screen while authenticated.
        sessionStateFlow.value = SessionState.Authenticated(dummyAuthUser)

        val vm = FriendsViewModel(
            friendRepo         = friendRepo,
            authRepo           = authRepo,
            searchUseCase      = searchUseCase,
            sendRequestUseCase = sendRequestUseCase,
            shareInviteUseCase = shareInviteUseCase,
            analyticsHelper    = analyticsHelper,
        )
        advanceUntilIdle()

        coVerify(atLeast = 1) { friendRepo.refreshOutgoingRequests(dummyAuthUser.id) }
    }

    @Test
    fun `given session transitions to Authenticated after init then refreshOutgoingRequests is called`() = runTest {
        // The session starts as Unauthenticated; loadData (and therefore refreshOutgoingRequests)
        // must fire when the session later becomes Authenticated.
        advanceUntilIdle() // let the ViewModel observe the initial Unauthenticated state

        sessionStateFlow.value = SessionState.Authenticated(dummyAuthUser)
        advanceUntilIdle()

        coVerify(atLeast = 1) { friendRepo.refreshOutgoingRequests(dummyAuthUser.id) }
    }
}
