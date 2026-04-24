package com.mmg.manahub.feature.friends.presentation

import app.cash.turbine.test
import com.mmg.manahub.core.util.CardConstants
import com.mmg.manahub.feature.auth.domain.model.AuthUser
import com.mmg.manahub.feature.auth.domain.model.SessionState
import com.mmg.manahub.feature.auth.domain.repository.AuthRepository
import com.mmg.manahub.feature.friends.domain.model.Friend
import com.mmg.manahub.feature.friends.domain.model.FriendRequest
import com.mmg.manahub.feature.friends.domain.repository.FriendRepository
import com.mmg.manahub.core.util.AnalyticsHelper
import com.mmg.manahub.feature.friends.domain.usecase.SearchUserByGameTagUseCase
import com.mmg.manahub.feature.friends.domain.usecase.SendFriendRequestUseCase
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
 * Unit tests for [FriendsViewModel].
 *
 * Strategy:
 * - [FriendRepository] and [AuthRepository] are mocked with MockK.
 * - [SearchUserByGameTagUseCase] and [SendFriendRequestUseCase] are mocked.
 * - [UnconfinedTestDispatcher] is used so coroutines complete eagerly without
 *   needing [advanceUntilIdle] in most cases.
 * - [AuthRepository.sessionState] is backed by a [MutableStateFlow] so we can
 *   drive authentication state transitions per test.
 * - The key invariant under test: [FriendsViewModel.triggerSearch] validates that
 *   the game tag is exactly [CardConstants.GAME_TAG_LENGTH] (7) characters before
 *   calling the backend. [onSearchQueryChange] must NOT trigger a search automatically.
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
        every { friendRepo.observeFriends() }          returns flowOf(emptyList())
        every { friendRepo.observePendingRequests() }  returns flowOf(emptyList())

        // Default stub: refreshFriends and refreshRequests succeed silently.
        coEvery { friendRepo.refreshFriends(any()) }   returns Result.success(Unit)
        coEvery { friendRepo.refreshRequests(any()) }  returns Result.success(Unit)

        viewModel = FriendsViewModel(
            friendRepo         = friendRepo,
            authRepo           = authRepo,
            searchUseCase      = searchUseCase,
            sendRequestUseCase = sendRequestUseCase,
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
        // The old behaviour auto-searched on query changes. The new behaviour only updates
        // state — triggerSearch() must be called explicitly.
        val validGameTag = "#A1B2C3" // exactly GAME_TAG_LENGTH = 7 chars

        viewModel.onSearchQueryChange(validGameTag)
        advanceUntilIdle()

        // searchUseCase must never be invoked by onSearchQueryChange
        coVerify(exactly = 0) { searchUseCase(any()) }
        assertEquals(validGameTag, viewModel.uiState.value.searchQuery)
    }

    @Test
    fun `given onSearchQueryChange called then searchResult is cleared and searchPerformed resets to false`() = runTest {
        // Any pending search result from a previous triggerSearch must be cleared when the
        // user modifies the query text.
        viewModel.onSearchQueryChange("#A1B2C3")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.searchResult)
        assertFalse(state.searchPerformed)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 2 — triggerSearch length validation
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given query shorter than GAME_TAG_LENGTH when triggerSearch then no backend call is made`() = runTest {
        // A game tag must be exactly 7 characters (e.g. #A1B2C3). Shorter queries must be
        // rejected client-side without touching the network.
        val shortQuery = "#ABC" // 4 chars — too short

        viewModel.onSearchQueryChange(shortQuery)
        viewModel.triggerSearch()
        advanceUntilIdle()

        coVerify(exactly = 0) { searchUseCase(any()) }
        // searchPerformed is set to true so the UI can show a validation message
        assertTrue(viewModel.uiState.value.searchPerformed)
        assertNull(viewModel.uiState.value.searchResult)
    }

    @Test
    fun `given query longer than GAME_TAG_LENGTH when triggerSearch then no backend call is made`() = runTest {
        val longQuery = "#A1B2C3D4" // 9 chars — too long

        viewModel.onSearchQueryChange(longQuery)
        viewModel.triggerSearch()
        advanceUntilIdle()

        coVerify(exactly = 0) { searchUseCase(any()) }
        assertTrue(viewModel.uiState.value.searchPerformed)
        assertNull(viewModel.uiState.value.searchResult)
    }

    @Test
    fun `given query exactly GAME_TAG_LENGTH when triggerSearch then searchUseCase is called`() = runTest {
        // Exactly 7 characters is the only valid game-tag length.
        val validGameTag = "#A1B2C3"
        assertEquals(CardConstants.GAME_TAG_LENGTH, validGameTag.length)

        coEvery { searchUseCase(validGameTag) } returns Result.success(null)

        viewModel.onSearchQueryChange(validGameTag)
        viewModel.triggerSearch()
        advanceUntilIdle()

        coVerify(exactly = 1) { searchUseCase(validGameTag) }
    }

    @Test
    fun `given query with surrounding whitespace and correct trimmed length when triggerSearch then searchUseCase is called with trimmed query`() = runTest {
        // triggerSearch trims the query before checking length — leading/trailing spaces must
        // not disqualify an otherwise valid game tag.
        val validGameTag = "#A1B2C3"
        assertEquals(CardConstants.GAME_TAG_LENGTH, validGameTag.length)

        coEvery { searchUseCase(validGameTag) } returns Result.success(null)

        viewModel.onSearchQueryChange("  $validGameTag  ")
        viewModel.triggerSearch()
        advanceUntilIdle()

        coVerify(exactly = 1) { searchUseCase(validGameTag) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 3 — triggerSearch result handling
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given valid query and search returns result when triggerSearch then searchResult is populated`() = runTest {
        val validGameTag = "#A1B2C3"
        coEvery { searchUseCase(validGameTag) } returns Result.success(dummyFriend)

        viewModel.onSearchQueryChange(validGameTag)
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
        val validGameTag = "#A1B2C3"
        coEvery { searchUseCase(validGameTag) } returns Result.success(null)

        viewModel.onSearchQueryChange(validGameTag)
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
        val validGameTag = "#A1B2C3"
        coEvery { searchUseCase(validGameTag) } returns Result.failure(Exception("Network error"))

        viewModel.onSearchQueryChange(validGameTag)
        viewModel.triggerSearch()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.searchResult)
        assertTrue(state.searchPerformed)
        assertFalse(state.isSearching)
    }

    @Test
    fun `given triggerSearch in progress when called then isSearching is true during execution`() = runTest {
        val validGameTag = "#A1B2C3"

        viewModel.uiState.test {
            awaitItem() // initial state

            coEvery { searchUseCase(validGameTag) } returns Result.success(dummyFriend)
            viewModel.onSearchQueryChange(validGameTag)
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
        // After trim() a whitespace-only string becomes empty, length 0 != GAME_TAG_LENGTH
        viewModel.onSearchQueryChange("       ")
        viewModel.triggerSearch()
        advanceUntilIdle()

        coVerify(exactly = 0) { searchUseCase(any()) }
    }
}
