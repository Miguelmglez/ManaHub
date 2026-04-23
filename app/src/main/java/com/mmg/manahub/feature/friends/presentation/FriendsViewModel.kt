package com.mmg.manahub.feature.friends.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.manahub.core.util.AnalyticsHelper
import com.mmg.manahub.core.util.CardConstants
import com.mmg.manahub.feature.auth.domain.model.SessionState
import com.mmg.manahub.feature.auth.domain.repository.AuthRepository
import com.mmg.manahub.feature.friends.domain.model.Friend
import com.mmg.manahub.feature.friends.domain.model.FriendRequest
import com.mmg.manahub.feature.friends.domain.repository.FriendRepository
import com.mmg.manahub.feature.friends.domain.usecase.SearchUserByGameTagUseCase
import com.mmg.manahub.feature.friends.domain.usecase.SendFriendRequestUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FriendsViewModel @Inject constructor(
    private val friendRepo: FriendRepository,
    private val authRepo: AuthRepository,
    private val searchUseCase: SearchUserByGameTagUseCase,
    private val sendRequestUseCase: SendFriendRequestUseCase,
    private val analyticsHelper: AnalyticsHelper
) : ViewModel() {

    data class UiState(
        val friends: List<Friend> = emptyList(),
        val pendingRequests: List<FriendRequest> = emptyList(),
        val searchQuery: String = "",
        val searchResult: Friend? = null,
        val searchPerformed: Boolean = false,
        val isSearching: Boolean = false,
        val isLoading: Boolean = false,
        val toastMessage: String? = null,
        val isLoggedIn: Boolean = false,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _currentUserId = MutableStateFlow<String?>(null)

    init {
        authRepo.sessionState
            .onEach { state ->
                val loggedIn = state is SessionState.Authenticated
                val userId = (state as? SessionState.Authenticated)?.user?.id
                _currentUserId.value = userId
                _uiState.update { it.copy(isLoggedIn = loggedIn) }
                if (loggedIn && userId != null) loadData(userId)
            }
            .catch { }
            .launchIn(viewModelScope)

        friendRepo.observeFriends()
            .onEach { friends -> _uiState.update { it.copy(friends = friends) } }
            .catch { }
            .launchIn(viewModelScope)

        friendRepo.observePendingRequests()
            .onEach { requests -> _uiState.update { it.copy(pendingRequests = requests) } }
            .catch { }
            .launchIn(viewModelScope)
    }

    private fun loadData(userId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            friendRepo.refreshFriends(userId)
            friendRepo.refreshRequests(userId)
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query, searchResult = null, searchPerformed = false) }
    }

    fun triggerSearch() {
        val query = _uiState.value.searchQuery.trim()
        if (query.length != CardConstants.GAME_TAG_LENGTH) {
            analyticsHelper.logEvent("wrong_length_friend_search")
            _uiState.update { it.copy(searchPerformed = true, searchResult = null) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true, searchPerformed = false) }
            val result = searchUseCase(query)
            _uiState.update {
                analyticsHelper.logEvent("friend_search")
                it.copy(
                    isSearching = false,
                    searchResult = result.getOrNull(),
                    searchPerformed = true,
                )
            }
        }
    }

    fun sendFriendRequest(toUserId: String, errorMsg: String) {
        val fromUserId = _currentUserId.value ?: return
        viewModelScope.launch {
            val result = sendRequestUseCase(fromUserId, toUserId)
            if (result.isSuccess) {
                analyticsHelper.logEvent("send_friend_request")
                _uiState.update { it.copy(searchQuery = "", searchResult = null, searchPerformed = false) }
            } else {
                analyticsHelper.logEvent("error_send_friend_request", mapOf("error" to errorMsg))
                _uiState.update { it.copy(toastMessage = errorMsg) }
            }
        }
    }

    fun acceptRequest(requestId: String, errorMsg: String) {
        val userId = _currentUserId.value ?: return
        viewModelScope.launch {
            analyticsHelper.logEvent("accept_friend_request")
            val result = friendRepo.acceptRequest(requestId, userId)
            if (result.isFailure) _uiState.update {
                analyticsHelper.logEvent("error_accept_friend_request", mapOf("error" to errorMsg))
                it.copy(toastMessage = errorMsg) }
        }
    }

    fun rejectRequest(requestId: String, errorMsg: String) {
        viewModelScope.launch {
            val result = friendRepo.rejectRequest(requestId)
            analyticsHelper.logEvent("reject_friend_request")
            if (result.isFailure) _uiState.update {
                analyticsHelper.logEvent("error_reject_friend_request", mapOf("error" to errorMsg))
                it.copy(toastMessage = errorMsg) }
        }
    }

    fun removeFriend(friendshipId: String, errorMsg: String) {
        viewModelScope.launch {
            val result = friendRepo.removeFriend(friendshipId)
            analyticsHelper.logEvent("remove_friend")
            if (result.isFailure) _uiState.update {
                analyticsHelper.logEvent("error_remove_friend", mapOf("error" to errorMsg))
                it.copy(toastMessage = errorMsg) }
        }
    }

    fun clearToast() = _uiState.update { it.copy(toastMessage = null) }
}
