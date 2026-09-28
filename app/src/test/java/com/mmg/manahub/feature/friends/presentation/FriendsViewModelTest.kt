package com.mmg.manahub.feature.friends.presentation

import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.domain.auth.AuthRepository
import com.mmg.manahub.core.domain.auth.AuthUser
import com.mmg.manahub.core.domain.auth.SessionState
import com.mmg.manahub.core.domain.repository.FriendRepository
import com.mmg.manahub.core.model.Friend
import com.mmg.manahub.core.model.FriendRequest
import com.mmg.manahub.core.model.FriendRequestException
import com.mmg.manahub.core.model.FriendshipGoneException
import com.mmg.manahub.core.model.OutgoingFriendRequest
import com.mmg.manahub.core.util.AnalyticsHelper
import com.mmg.manahub.feature.friends.domain.usecase.SearchUserByGameTagUseCase
import com.mmg.manahub.feature.friends.domain.usecase.SendFriendRequestUseCase
import com.mmg.manahub.feature.friends.domain.usecase.ShareInviteUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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

@OptIn(ExperimentalCoroutinesApi::class)
class FriendsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val friendRepo = mockk<FriendRepository>(relaxed = true)
    private val authRepo = mockk<AuthRepository>()
    private val searchUseCase = mockk<SearchUserByGameTagUseCase>()
    private val sendRequestUseCase = mockk<SendFriendRequestUseCase>()
    private val analyticsHelper = mockk<AnalyticsHelper>(relaxed = true)
    private val shareInviteUseCase = mockk<ShareInviteUseCase>()
    private val crashReporter = mockk<CrashReporter>(relaxed = true)

    private val sessionState = MutableStateFlow<SessionState>(SessionState.Unauthenticated)
    private val friends = MutableStateFlow<List<Friend>>(emptyList())
    private val incoming = MutableStateFlow<List<FriendRequest>>(emptyList())
    private val outgoing = MutableStateFlow<List<OutgoingFriendRequest>>(emptyList())

    private val me = AuthUser(
        id = "user-me", email = "me@example.com", nickname = "Me", gameTag = "#A1B2C3",
        avatarUrl = null, provider = "email",
    )
    private val stranger = Friend(id = "", userId = "user-stranger", nickname = "Gandalf", gameTag = "#XYZ123", avatarUrl = null)

    private lateinit var viewModel: FriendsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { authRepo.sessionState } returns sessionState
        every { friendRepo.observeFriends() } returns friends
        every { friendRepo.observePendingRequests() } returns incoming
        every { friendRepo.observeOutgoingRequests() } returns outgoing
        coEvery { friendRepo.refreshAll(any()) } returns Result.success(Unit)
        coEvery { friendRepo.refreshOutgoingRequests(any()) } returns Result.success(Unit)
        coEvery { friendRepo.refreshRequests(any()) } returns Result.success(Unit)
        viewModel = FriendsViewModel(
            friendRepo = friendRepo,
            authRepo = authRepo,
            searchUseCase = searchUseCase,
            sendRequestUseCase = sendRequestUseCase,
            analyticsHelper = analyticsHelper,
            shareInviteUseCase = shareInviteUseCase,
            crashReporter = crashReporter,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun signIn() {
        sessionState.value = SessionState.Authenticated(me)
    }

    // ── Refresh ─────────────────────────────────────────────────────────────

    @Test
    fun `signing in refreshes the three lists once through refreshAll`() = runTest {
        signIn()

        coVerify(exactly = 1) { friendRepo.refreshAll("user-me") }
        coVerify(exactly = 0) { friendRepo.refreshFriends(any()) }
        assertTrue(viewModel.uiState.value.hasRefreshed)
        assertFalse(viewModel.uiState.value.isRefreshing)
    }

    @Test
    fun `a same-user session re-emission does not refresh again`() = runTest {
        signIn()
        sessionState.value = SessionState.Authenticated(me.copy(nickname = "Renamed"))

        coVerify(exactly = 1) { friendRepo.refreshAll("user-me") }
    }

    @Test
    fun `switching accounts cancels the first account refresh`() = runTest {
        val firstStarted = CompletableDeferred<Unit>()
        val firstCancelled = CompletableDeferred<Unit>()
        coEvery { friendRepo.refreshAll("user-me") } coAnswers {
            firstStarted.complete(Unit)
            try {
                CompletableDeferred<Result<Unit>>().await()
            } finally {
                firstCancelled.complete(Unit)
            }
        }
        coEvery { friendRepo.refreshAll("user-other") } returns Result.success(Unit)

        signIn()
        firstStarted.await()
        sessionState.value = SessionState.Authenticated(me.copy(id = "user-other"))
        firstCancelled.await()

        coVerify(exactly = 1) { friendRepo.refreshAll("user-other") }
        assertEquals("user-other", viewModel.uiState.value.currentUserId)
        assertFalse(viewModel.uiState.value.refreshFailed)
    }

    @Test
    fun `signing out clears cached friendship rows before repository flows update`() = runTest {
        signIn()
        friends.value = listOf(Friend("fs-a", "user-a", "A", "#AAAAAA", null))
        incoming.value = listOf(FriendRequest("fs-b", "user-b", "B", "#BBBBBB", null))
        outgoing.value = listOf(OutgoingFriendRequest("fs-c", "user-c", "C", "#CCCCCC", null))
        assertEquals(1, viewModel.uiState.value.friends.size)
        assertEquals(1, viewModel.uiState.value.pendingRequests.size)
        assertEquals(1, viewModel.uiState.value.outgoingRequests.size)

        sessionState.value = SessionState.Unauthenticated

        assertFalse(viewModel.uiState.value.isLoggedIn)
        assertTrue(viewModel.uiState.value.friends.isEmpty())
        assertTrue(viewModel.uiState.value.pendingRequests.isEmpty())
        assertTrue(viewModel.uiState.value.outgoingRequests.isEmpty())
    }

    @Test
    fun `switching accounts clears prior friendship rows before new refresh completes`() = runTest {
        signIn()
        friends.value = listOf(Friend("fs-a", "user-a", "A", "#AAAAAA", null))
        assertEquals(1, viewModel.uiState.value.friends.size)
        coEvery { friendRepo.refreshAll("user-other") } coAnswers {
            CompletableDeferred<Result<Unit>>().await()
        }

        sessionState.value = SessionState.Authenticated(me.copy(id = "user-other"))

        assertEquals("user-other", viewModel.uiState.value.currentUserId)
        assertTrue(viewModel.uiState.value.friends.isEmpty())
    }

    @Test
    fun `a failed refresh keeps the cache and offers a retry`() = runTest {
        coEvery { friendRepo.refreshAll(any()) } returns Result.failure(IllegalStateException("offline"))
        signIn()

        assertTrue(viewModel.uiState.value.refreshFailed)
        assertTrue(viewModel.uiState.value.hasRefreshed)

        coEvery { friendRepo.refreshAll(any()) } returns Result.success(Unit)
        viewModel.retryRefresh()

        coVerify(exactly = 2) { friendRepo.refreshAll("user-me") }
        assertFalse(viewModel.uiState.value.refreshFailed)
    }

    // ── F-02: section-scoped keys ─────────────────────────────────────────────

    @Test
    fun `the same friendship id in three lists yields unique row keys`() = runTest {
        signIn()
        friends.value = listOf(Friend("fs-Y", "user-a", "A", "#AAAAAA", null))
        incoming.value = listOf(FriendRequest("fs-Y", "user-a", "A", "#AAAAAA", null))
        outgoing.value = listOf(OutgoingFriendRequest("fs-Y", "user-a", "A", "#AAAAAA", null))

        val keys = FriendsListKeys.rowKeys(viewModel.uiState.value)

        assertEquals(3, keys.size)
        assertEquals(keys.size, keys.toSet().size)
    }

    // ── F-12: game-tag input ─────────────────────────────────────────────────

    @Test
    fun `a pasted lowercase tag with a hash is normalized before searching`() = runTest {
        signIn()
        coEvery { searchUseCase("#A1B2C3") } returns Result.success(stranger)
        viewModel.onSearchQueryChange("  #a1b2c3 ")

        viewModel.triggerSearch()

        coVerify(exactly = 1) { searchUseCase("#A1B2C3") }
        assertEquals(GameTagSearchStatus.FOUND, viewModel.uiState.value.searchStatus)
    }

    @Test
    fun `a malformed tag never reaches the backend`() = runTest {
        signIn()
        viewModel.onSearchQueryChange("AB-12")

        viewModel.triggerSearch()

        coVerify(exactly = 0) { searchUseCase(any()) }
        assertEquals(GameTagSearchStatus.INVALID_INPUT, viewModel.uiState.value.searchStatus)
    }

    @Test
    fun `a search failure is not reported as no player found`() = runTest {
        signIn()
        coEvery { searchUseCase(any()) } returns Result.failure(IllegalStateException("offline"))
        viewModel.onSearchQueryChange("A1B2C3")

        viewModel.triggerSearch()

        assertEquals(GameTagSearchStatus.FAILED, viewModel.uiState.value.searchStatus)
        assertNull(viewModel.uiState.value.searchResult)
    }

    @Test
    fun `an unknown tag reads as not found`() = runTest {
        signIn()
        coEvery { searchUseCase(any()) } returns Result.success(null)
        viewModel.onSearchQueryChange("A1B2C3")

        viewModel.triggerSearch()

        assertEquals(GameTagSearchStatus.NOT_FOUND, viewModel.uiState.value.searchStatus)
    }

    @Test
    fun `a late result for query A never replaces query B or becomes sendable`() = runTest {
        signIn()
        val firstResult = CompletableDeferred<Result<Friend?>>()
        coEvery { searchUseCase("#A1B2C3") } coAnswers { withContext(NonCancellable) { firstResult.await() } }
        val secondFriend = stranger.copy(userId = "user-second")
        coEvery { searchUseCase("#D4E5F6") } returns Result.success(secondFriend)
        coEvery { sendRequestUseCase("user-me", "user-second") } returns Result.success(Unit)

        viewModel.onSearchQueryChange("A1B2C3")
        viewModel.triggerSearch()
        viewModel.onSearchQueryChange("D4E5F6")
        viewModel.triggerSearch()
        firstResult.complete(Result.success(stranger))

        assertEquals("D4E5F6", viewModel.uiState.value.searchQuery)
        assertEquals(secondFriend, viewModel.uiState.value.searchResult)
        viewModel.sendFriendRequest()
        coVerify(exactly = 0) { sendRequestUseCase("user-me", stranger.userId) }
    }

    // ── F-07: search result relation ─────────────────────────────────────────

    private fun searchFor(result: Friend) {
        coEvery { searchUseCase(any()) } returns Result.success(result)
        viewModel.onSearchQueryChange("A1B2C3")
        viewModel.triggerSearch()
    }

    @Test
    fun `the search result relation follows the cached lists`() = runTest {
        signIn()
        searchFor(stranger)
        assertEquals(SearchResultRelation.NONE, viewModel.uiState.value.searchRelation)

        outgoing.value = listOf(OutgoingFriendRequest("fs-1", stranger.userId, "G", "#XYZ123", null))
        assertEquals(SearchResultRelation.OUTGOING_PENDING, viewModel.uiState.value.searchRelation)

        outgoing.value = emptyList()
        incoming.value = listOf(FriendRequest("fs-2", stranger.userId, "G", "#XYZ123", null))
        assertEquals(SearchResultRelation.INCOMING_PENDING, viewModel.uiState.value.searchRelation)
        assertEquals("fs-2", viewModel.uiState.value.searchResultIncomingRequest?.id)

        friends.value = listOf(Friend("fs-3", stranger.userId, "G", "#XYZ123", null))
        assertEquals(SearchResultRelation.FRIEND, viewModel.uiState.value.searchRelation)
    }

    @Test
    fun `searching your own tag is recognized as self and cannot be sent`() = runTest {
        signIn()
        searchFor(stranger.copy(userId = "user-me"))

        assertEquals(SearchResultRelation.SELF, viewModel.uiState.value.searchRelation)
        viewModel.sendFriendRequest()
        coVerify(exactly = 0) { sendRequestUseCase(any(), any()) }
    }

    @Test
    fun `a request to an existing friend is ignored`() = runTest {
        signIn()
        friends.value = listOf(Friend("fs-3", stranger.userId, "G", "#XYZ123", null))
        searchFor(stranger)

        viewModel.sendFriendRequest()

        coVerify(exactly = 0) { sendRequestUseCase(any(), any()) }
    }

    @Test
    fun `a sent request clears the search and refreshes outgoing requests`() = runTest {
        signIn()
        searchFor(stranger)
        coEvery { sendRequestUseCase("user-me", stranger.userId) } returns Result.success(Unit)

        viewModel.sendFriendRequest()

        assertEquals(FriendsMessage.REQUEST_SENT, viewModel.uiState.value.message)
        assertNull(viewModel.uiState.value.searchResult)
        coVerify(exactly = 1) { friendRepo.refreshOutgoingRequests("user-me") }
    }

    @Test
    fun `a duplicate pair (409) maps to the already-linked message and re-syncs the lists`() = runTest {
        signIn()
        searchFor(stranger)
        coEvery { sendRequestUseCase(any(), any()) } returns Result.failure(FriendRequestException.AlreadyLinked())

        viewModel.sendFriendRequest()

        assertEquals(FriendsMessage.SEND_ALREADY_LINKED, viewModel.uiState.value.message)
        assertFalse(viewModel.uiState.value.isSendingRequest)
        coVerify(exactly = 2) { friendRepo.refreshAll("user-me") }
    }

    @Test
    fun `a self-check violation maps to its own message`() = runTest {
        signIn()
        searchFor(stranger)
        coEvery { sendRequestUseCase(any(), any()) } returns Result.failure(FriendRequestException.SelfRequest())

        viewModel.sendFriendRequest()

        assertEquals(FriendsMessage.SEND_SELF, viewModel.uiState.value.message)
    }

    @Test
    fun `an unknown send failure keeps the generic message`() = runTest {
        signIn()
        searchFor(stranger)
        coEvery { sendRequestUseCase(any(), any()) } returns Result.failure(RuntimeException("boom"))

        viewModel.sendFriendRequest()

        assertEquals(FriendsMessage.SEND_FAILED, viewModel.uiState.value.message)
    }

    // ── F-09: per-row in-flight guard ────────────────────────────────────────

    @Test
    fun `a second tap on a row in flight is ignored and the row is released afterwards`() = runTest {
        signIn()
        val gate = CompletableDeferred<Result<Unit>>()
        coEvery { friendRepo.acceptRequest("fs-1", "user-me") } coAnswers { gate.await() }

        viewModel.acceptRequest("fs-1")
        viewModel.acceptRequest("fs-1")
        viewModel.rejectRequest("fs-1")

        assertEquals(setOf("fs-1"), viewModel.uiState.value.inFlightRequestIds)
        coVerify(exactly = 1) { friendRepo.acceptRequest("fs-1", "user-me") }
        coVerify(exactly = 0) { friendRepo.rejectRequest(any()) }

        gate.complete(Result.success(Unit))

        assertTrue(viewModel.uiState.value.inFlightRequestIds.isEmpty())
        assertEquals(FriendsMessage.REQUEST_ACCEPTED, viewModel.uiState.value.message)
    }

    @Test
    fun `a request that is gone server-side reports it and refreshes the incoming list`() = runTest {
        signIn()
        coEvery { friendRepo.rejectRequest("fs-1") } returns Result.failure(FriendshipGoneException())

        viewModel.rejectRequest("fs-1")

        assertEquals(FriendsMessage.REQUEST_GONE, viewModel.uiState.value.message)
        coVerify(exactly = 1) { friendRepo.refreshRequests("user-me") }
    }

    @Test
    fun `a failed accept releases the row and shows an error`() = runTest {
        signIn()
        coEvery { friendRepo.acceptRequest(any(), any()) } returns Result.failure(RuntimeException("boom"))

        viewModel.acceptRequest("fs-1")

        assertTrue(viewModel.uiState.value.inFlightRequestIds.isEmpty())
        assertEquals(FriendsMessage.ACCEPT_FAILED, viewModel.uiState.value.message)
        assertTrue(viewModel.uiState.value.message!!.isError)
    }

    @Test
    fun `clearMessage drops the shown message`() = runTest {
        signIn()
        coEvery { friendRepo.cancelOutgoingRequest("fs-1") } returns Result.success(Unit)
        viewModel.cancelOutgoingRequest("fs-1")
        assertEquals(FriendsMessage.REQUEST_CANCELLED, viewModel.uiState.value.message)

        viewModel.clearMessage()

        assertNull(viewModel.uiState.value.message)
    }

    @Test
    fun `row actions do nothing while signed out`() = runTest {
        viewModel.acceptRequest("fs-1")

        coVerify(exactly = 0) { friendRepo.acceptRequest(any(), any()) }
        assertFalse(viewModel.uiState.value.isLoggedIn)
    }

    @Test
    fun `normalizeGameTag accepts only six letters or digits`() {
        assertEquals("A1B2C3", FriendsViewModel.normalizeGameTag("#a1b2c3"))
        assertNull(FriendsViewModel.normalizeGameTag("A1B2C"))
        assertNull(FriendsViewModel.normalizeGameTag("A1B2C3D"))
        assertNull(FriendsViewModel.normalizeGameTag("A1_2C3"))
    }
}
