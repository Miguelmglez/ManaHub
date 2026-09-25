package com.mmg.manahub.feature.friends.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.domain.auth.AuthRepository
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One-shot feedback of the Friends screen; the screen maps each value to its copy. */
enum class FriendsMessage(val isError: Boolean) {
    REQUEST_SENT(false),
    REQUEST_ACCEPTED(false),
    REQUEST_DECLINED(false),
    REQUEST_CANCELLED(false),
    SEND_FAILED(true),
    SEND_ALREADY_LINKED(true),
    SEND_SELF(true),
    SEND_NOT_PERMITTED(true),
    ACCEPT_FAILED(true),
    REQUEST_GONE(true),
    DECLINE_FAILED(true),
    CANCEL_FAILED(true),
}

/** How the game-tag search result relates to the signed-in user; drives the result card's CTA. */
enum class SearchResultRelation { SELF, FRIEND, INCOMING_PENDING, OUTGOING_PENDING, NONE }

/** State of the game-tag search box. */
enum class GameTagSearchStatus { IDLE, SEARCHING, FOUND, NOT_FOUND, INVALID_INPUT, FAILED }

/**
 * Friends list / game-tag search screen. Cached lists render immediately; one atomic refresh runs per
 * signed-in account, and row actions are guarded per request id so a double tap never sends twice.
 */
class FriendsViewModel(
    private val friendRepo: FriendRepository,
    private val authRepo: AuthRepository,
    private val searchUseCase: SearchUserByGameTagUseCase,
    private val sendRequestUseCase: SendFriendRequestUseCase,
    private val analyticsHelper: AnalyticsHelper,
    private val shareInviteUseCase: ShareInviteUseCase,
    private val crashReporter: CrashReporter,
) : ViewModel() {

    data class UiState(
        val friends: List<Friend> = emptyList(),
        val pendingRequests: List<FriendRequest> = emptyList(),
        val outgoingRequests: List<OutgoingFriendRequest> = emptyList(),
        val searchQuery: String = "",
        val searchResult: Friend? = null,
        val searchStatus: GameTagSearchStatus = GameTagSearchStatus.IDLE,
        val isRefreshing: Boolean = false,
        /** True once the first refresh for the current account finished, successfully or not. */
        val hasRefreshed: Boolean = false,
        val refreshFailed: Boolean = false,
        /** Request ids with an accept / decline / cancel in flight; their buttons are disabled. */
        val inFlightRequestIds: Set<String> = emptySet(),
        val isSendingRequest: Boolean = false,
        val message: FriendsMessage? = null,
        val isLoggedIn: Boolean = false,
        val currentUserId: String? = null,
        /** Current user's game tag (e.g. "#A3KX9Z"), or null if not yet loaded. */
        val gameTag: String? = null,
    ) {
        val isSearching: Boolean get() = searchStatus == GameTagSearchStatus.SEARCHING

        val searchRelation: SearchResultRelation
            get() {
                val target = searchResult?.userId ?: return SearchResultRelation.NONE
                return when {
                    target == currentUserId -> SearchResultRelation.SELF
                    friends.any { it.userId == target } -> SearchResultRelation.FRIEND
                    pendingRequests.any { it.fromUserId == target } -> SearchResultRelation.INCOMING_PENDING
                    outgoingRequests.any { it.toUserId == target } -> SearchResultRelation.OUTGOING_PENDING
                    else -> SearchResultRelation.NONE
                }
            }

        /** The incoming request from the search result, when there is one to accept instead of sending. */
        val searchResultIncomingRequest: FriendRequest?
            get() = searchResult?.let { result -> pendingRequests.firstOrNull { it.fromUserId == result.userId } }
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var refreshJob: Job? = null
    private var searchJob: Job? = null
    private var searchGeneration = 0

    init {
        // The game tag can change without the user id changing, so this runs on every emission.
        authRepo.sessionState
            .onEach { state ->
                val authUser = (state as? SessionState.Authenticated)?.user
                _uiState.update {
                    it.copy(isLoggedIn = authUser != null, currentUserId = authUser?.id, gameTag = authUser?.gameTag)
                }
            }
            .reportErrors("session")
            .launchIn(viewModelScope)

        // Token refreshes re-emit the same user; only a new account refreshes, and it cancels the old one's load.
        viewModelScope.launch {
            authRepo.sessionState
                .map { (it as? SessionState.Authenticated)?.user?.id }
                .distinctUntilChanged()
                .reportErrors("session_user")
                .collectLatest { userId ->
                    searchGeneration++
                    searchJob?.cancel()
                    _uiState.update {
                        it.copy(
                            hasRefreshed = false,
                            refreshFailed = false,
                            inFlightRequestIds = emptySet(),
                            searchQuery = "",
                            searchResult = null,
                            searchStatus = GameTagSearchStatus.IDLE,
                            isSendingRequest = false,
                            message = null,
                        )
                    }
                    if (userId != null) refresh(userId)
                }
        }

        friendRepo.observeFriends()
            .onEach { friends -> _uiState.update { it.copy(friends = friends) } }
            .reportErrors("friends")
            .launchIn(viewModelScope)

        friendRepo.observePendingRequests()
            .onEach { requests -> _uiState.update { it.copy(pendingRequests = requests) } }
            .reportErrors("incoming")
            .launchIn(viewModelScope)

        friendRepo.observeOutgoingRequests()
            .onEach { requests -> _uiState.update { it.copy(outgoingRequests = requests) } }
            .reportErrors("outgoing")
            .launchIn(viewModelScope)
    }

    /** Re-runs the refresh for the signed-in account (retry after a failure). */
    fun retryRefresh() {
        val userId = _uiState.value.currentUserId ?: return
        if (refreshJob?.isActive == true || _uiState.value.isRefreshing) return
        refreshJob = viewModelScope.launch { refresh(userId) }
    }

    private suspend fun refresh(userId: String) {
        _uiState.update { it.copy(isRefreshing = true, refreshFailed = false) }
        val result = try {
            friendRepo.refreshAll(userId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
        result.onFailure { e ->
            crashReporter.log("friends_refresh_failed")
            crashReporter.recordException(RuntimeException("[friends_refresh_failed] ${e::class.simpleName}"))
        }
        _uiState.update {
            if (it.currentUserId == userId) {
                it.copy(isRefreshing = false, hasRefreshed = true, refreshFailed = result.isFailure)
            } else it
        }
    }

    /**
     * Resolves the current user's invite share link for
     * [com.mmg.manahub.core.ui.components.ShareProfileSheet].
     */
    suspend fun fetchShareLink(): Result<String> {
        val userId = _uiState.value.currentUserId
            ?: return Result.failure(IllegalStateException("Not authenticated"))
        return shareInviteUseCase(userId)
    }

    fun onSearchQueryChange(query: String) {
        searchGeneration++
        searchJob?.cancel()
        _uiState.update { it.copy(searchQuery = query, searchResult = null, searchStatus = GameTagSearchStatus.IDLE) }
    }

    /** Searches the typed tag; accepts a pasted `#tag` and any letter case. */
    fun triggerSearch() {
        if (_uiState.value.isSearching) return
        val userId = _uiState.value.currentUserId ?: return
        val tag = normalizeGameTag(_uiState.value.searchQuery)
        if (tag == null) {
            analyticsHelper.logEvent("wrong_length_friend_search")
            _uiState.update { it.copy(searchResult = null, searchStatus = GameTagSearchStatus.INVALID_INPUT) }
            return
        }
        val generation = ++searchGeneration
        _uiState.update { it.copy(searchStatus = GameTagSearchStatus.SEARCHING, searchResult = null) }
        searchJob = viewModelScope.launch {
            val result = searchUseCase("#$tag")
            if (generation != searchGeneration || _uiState.value.currentUserId != userId ||
                normalizeGameTag(_uiState.value.searchQuery) != tag) return@launch
            analyticsHelper.logEvent("friend_search")
            result.onFailure { e -> crashReporter.log("friends_search_failed_${e::class.simpleName}") }
            _uiState.update {
                val found = result.getOrNull()
                it.copy(
                    searchResult = found,
                    searchStatus = when {
                        result.isFailure -> GameTagSearchStatus.FAILED
                        found == null -> GameTagSearchStatus.NOT_FOUND
                        else -> GameTagSearchStatus.FOUND
                    },
                )
            }
        }
    }

    /** Sends a request to the current search result; ignored unless it is a stranger and nothing is in flight. */
    fun sendFriendRequest() {
        val state = _uiState.value
        val fromUserId = state.currentUserId ?: return
        val target = state.searchResult ?: return
        if (state.isSendingRequest || state.searchRelation != SearchResultRelation.NONE) return
        _uiState.update { it.copy(isSendingRequest = true) }
        viewModelScope.launch {
            val result = sendRequestUseCase(fromUserId, target.userId)
            if (_uiState.value.currentUserId != fromUserId) return@launch
            if (result.isSuccess) {
                analyticsHelper.logEvent("send_friend_request")
                _uiState.update {
                    it.copy(
                        isSendingRequest = false,
                        searchQuery = "",
                        searchResult = null,
                        searchStatus = GameTagSearchStatus.IDLE,
                        message = FriendsMessage.REQUEST_SENT,
                    )
                }
                friendRepo.refreshOutgoingRequests(fromUserId)
            } else {
                val error = result.exceptionOrNull()
                val message = when (error) {
                    is FriendRequestException.AlreadyLinked -> FriendsMessage.SEND_ALREADY_LINKED
                    is FriendRequestException.SelfRequest -> FriendsMessage.SEND_SELF
                    is FriendRequestException.NotPermitted -> FriendsMessage.SEND_NOT_PERMITTED
                    else -> FriendsMessage.SEND_FAILED
                }
                analyticsHelper.logEvent("error_send_friend_request", mapOf("reason" to message.name.lowercase()))
                _uiState.update { it.copy(isSendingRequest = false, message = message) }
                // A duplicate means the server knows a link the cache is missing.
                if (error is FriendRequestException.AlreadyLinked) friendRepo.refreshAll(fromUserId)
            }
        }
    }

    fun acceptRequest(requestId: String) {
        val userId = _uiState.value.currentUserId ?: return
        runRowAction(requestId, userId) {
            analyticsHelper.logEvent("accept_friend_request")
            val result = friendRepo.acceptRequest(requestId, userId)
            result.exceptionOrNull()?.let { e ->
                analyticsHelper.logEvent("error_accept_friend_request", mapOf("reason" to e.reason()))
            }
            when {
                result.isSuccess -> FriendsMessage.REQUEST_ACCEPTED
                result.exceptionOrNull() is FriendshipGoneException -> FriendsMessage.REQUEST_GONE
                else -> FriendsMessage.ACCEPT_FAILED
            }
        }
    }

    fun rejectRequest(requestId: String) {
        val userId = _uiState.value.currentUserId ?: return
        runRowAction(requestId, userId) {
            analyticsHelper.logEvent("reject_friend_request")
            val result = friendRepo.rejectRequest(requestId)
            result.exceptionOrNull()?.let { e ->
                analyticsHelper.logEvent("error_reject_friend_request", mapOf("reason" to e.reason()))
            }
            when {
                result.isSuccess -> FriendsMessage.REQUEST_DECLINED
                result.exceptionOrNull() is FriendshipGoneException -> {
                    friendRepo.refreshRequests(userId)
                    FriendsMessage.REQUEST_GONE
                }
                else -> FriendsMessage.DECLINE_FAILED
            }
        }
    }

    fun cancelOutgoingRequest(friendshipId: String) {
        val userId = _uiState.value.currentUserId ?: return
        runRowAction(friendshipId, userId) {
            analyticsHelper.logEvent("cancel_outgoing_request")
            val result = friendRepo.cancelOutgoingRequest(friendshipId)
            when {
                result.isSuccess -> FriendsMessage.REQUEST_CANCELLED
                result.exceptionOrNull() is FriendshipGoneException -> {
                    friendRepo.refreshAll(userId)
                    FriendsMessage.REQUEST_GONE
                }
                else -> FriendsMessage.CANCEL_FAILED
            }
        }
    }

    fun clearMessage() = _uiState.update { it.copy(message = null) }

    // Check-and-mark runs synchronously on the main thread, so a second tap on the same row is dropped.
    private fun runRowAction(requestId: String, userId: String, action: suspend () -> FriendsMessage) {
        if (requestId in _uiState.value.inFlightRequestIds) return
        _uiState.update { it.copy(inFlightRequestIds = it.inFlightRequestIds + requestId) }
        viewModelScope.launch {
            val message = try {
                action()
            } finally {
                _uiState.update {
                    if (it.currentUserId == userId) it.copy(inFlightRequestIds = it.inFlightRequestIds - requestId) else it
                }
            }
            _uiState.update { if (it.currentUserId == userId) it.copy(message = message) else it }
        }
    }

    private fun Throwable.reason(): String = if (this is FriendshipGoneException) "gone" else "failed"

    private fun <T> Flow<T>.reportErrors(source: String): Flow<T> = catch { e ->
        crashReporter.setCustomKey("friends_flow_error_source", source)
        crashReporter.recordException(RuntimeException("[friends_flow_failed] ${e::class.simpleName}"))
    }

    companion object {
        private val GAME_TAG_PATTERN = Regex("^[A-Z0-9]{6}$")

        /** Normalizes user input to the bare 6-character tag, or null when it cannot be a game tag. */
        fun normalizeGameTag(input: String): String? =
            input.trim().removePrefix("#").trim().uppercase().takeIf { GAME_TAG_PATTERN.matches(it) }
    }
}
