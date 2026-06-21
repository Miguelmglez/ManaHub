package com.mmg.manahub.feature.friends.presentation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.manahub.R
import com.mmg.manahub.core.ui.components.MagicToastType
import com.mmg.manahub.core.util.AnalyticsHelper
import com.mmg.manahub.core.util.CardConstants
import com.mmg.manahub.core.domain.auth.SessionState
import com.mmg.manahub.core.domain.auth.AuthRepository
import com.mmg.manahub.feature.friends.domain.model.Friend
import com.mmg.manahub.feature.friends.domain.model.FriendRequest
import com.mmg.manahub.feature.friends.domain.model.OutgoingFriendRequest
import com.mmg.manahub.feature.friends.domain.repository.FriendRepository
import com.mmg.manahub.feature.friends.domain.usecase.SearchUserByGameTagUseCase
import com.mmg.manahub.feature.friends.domain.usecase.SendFriendRequestUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for the friends list / search screen.
 *
 * KMP migration — Phase 1 Hilt→Koin cutover: this VM is resolved by Koin (`koinViewModel()`) via
 * [com.mmg.manahub.feature.friends.di.friendsKoinModule], so it is a plain class with a normal
 * constructor (no `@HiltViewModel`/`@Inject`).
 */
class FriendsViewModel(
    private val friendRepo: FriendRepository,
    private val authRepo: AuthRepository,
    private val searchUseCase: SearchUserByGameTagUseCase,
    private val sendRequestUseCase: SendFriendRequestUseCase,
    private val analyticsHelper: AnalyticsHelper
) : ViewModel() {

    data class UiState(
        val friends: List<Friend> = emptyList(),
        val pendingRequests: List<FriendRequest> = emptyList(),
        val outgoingRequests: List<OutgoingFriendRequest> = emptyList(),
        val searchQuery: String = "",
        val searchResult: Friend? = null,
        val searchPerformed: Boolean = false,
        val isSearching: Boolean = false,
        val isLoading: Boolean = false,
        val toastMessage: String? = null,
        val toastType: MagicToastType = MagicToastType.ERROR,
        val isLoggedIn: Boolean = false,
        /** Current user's game tag (e.g. "#A3KX9Z"), or null if not yet loaded. */
        val gameTag: String? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _currentUserId = MutableStateFlow<String?>(null)

    init {
        // Cheap per-emission UI sync (login flag + game tag). This must run on every
        // emission because the game tag can change without the userId changing.
        authRepo.sessionState
            .onEach { state ->
                val loggedIn = state is SessionState.Authenticated
                val authUser = (state as? SessionState.Authenticated)?.user
                _currentUserId.value = authUser?.id
                _uiState.update { it.copy(isLoggedIn = loggedIn, gameTag = authUser?.gameTag) }
            }
            .catch { }
            .launchIn(viewModelScope)

        // Expensive network refresh — gated on a CHANGE of userId only. The session flow
        // re-emits Authenticated on every token refresh / app foreground; without this gate
        // each duplicate emission for the same user would fire 3 sequential network refreshes
        // and an isLoading flicker. distinctUntilChangedBy collapses same-user re-emissions.
        authRepo.sessionState
            .map { (it as? SessionState.Authenticated)?.user?.id }
            .distinctUntilChangedBy { it }
            .onEach { userId -> if (userId != null) loadData(userId) }
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

        friendRepo.observeOutgoingRequests()
            .onEach { requests -> _uiState.update { it.copy(outgoingRequests = requests) } }
            .catch { }
            .launchIn(viewModelScope)
    }

    private fun loadData(userId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            friendRepo.refreshFriends(userId)
            friendRepo.refreshRequests(userId)
            friendRepo.refreshOutgoingRequests(userId)
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    /**
     * Copies the current user's game tag (without the '#') to the clipboard.
     * Does nothing if the game tag is not available.
     */
    fun onCopyGameTagClicked(context: Context) {
        val tag = _uiState.value.gameTag?.removePrefix("#") ?: return
        
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("Game Tag", tag)
        clipboard.setPrimaryClip(clip)
        
        _uiState.update { 
            it.copy(
                toastMessage = context.getString(R.string.friends_gametag_copied),
                toastType = MagicToastType.SUCCESS
            )
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query, searchResult = null, searchPerformed = false) }
    }

    fun triggerSearch() {
        val query = _uiState.value.searchQuery.trim()
        // ASSUMPTION: game tags are FIXED length. GAME_TAG_LENGTH includes the '#' prefix, so
        // the raw input without '#' is exactly one character shorter — any other length is
        // rejected as malformed. If tags ever become variable-length, this exact-length check
        // must be replaced with a min/max range (or a regex) or valid searches will be dropped.
        if (query.length != CardConstants.GAME_TAG_LENGTH - 1) {
            analyticsHelper.logEvent("wrong_length_friend_search")
            _uiState.update { it.copy(searchPerformed = true, searchResult = null) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true, searchPerformed = false) }
            val result = searchUseCase("#$query")
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

    fun sendFriendRequest(toUserId: String, errorMsg: String, sentMsg: String) {
        val fromUserId = _currentUserId.value ?: return
        viewModelScope.launch {
            val result = sendRequestUseCase(fromUserId, toUserId)
            if (result.isSuccess) {
                analyticsHelper.logEvent("send_friend_request")
                _uiState.update {
                    it.copy(
                        searchQuery = "",
                        searchResult = null,
                        searchPerformed = false,
                        toastMessage = sentMsg,
                        toastType = MagicToastType.SUCCESS,
                    )
                }
                refreshOutgoingIfLoggedIn()
            } else {
                analyticsHelper.logEvent("error_send_friend_request", mapOf("error" to errorMsg))
                _uiState.update { it.copy(toastMessage = errorMsg, toastType = MagicToastType.ERROR) }
            }
        }
    }

    fun acceptRequest(requestId: String, errorMsg: String, successMsg: String) {
        val userId = _currentUserId.value ?: return
        viewModelScope.launch {
            analyticsHelper.logEvent("accept_friend_request")
            val result = friendRepo.acceptRequest(requestId, userId)
            _uiState.update {
                if (result.isSuccess)
                    it.copy(toastMessage = successMsg, toastType = MagicToastType.SUCCESS)
                else {
                    analyticsHelper.logEvent("error_accept_friend_request", mapOf("error" to errorMsg))
                    it.copy(toastMessage = errorMsg, toastType = MagicToastType.ERROR)
                }
            }
        }
    }

    fun rejectRequest(requestId: String, errorMsg: String, successMsg: String) {
        viewModelScope.launch {
            val result = friendRepo.rejectRequest(requestId)
            analyticsHelper.logEvent("reject_friend_request")
            _uiState.update {
                if (result.isSuccess)
                    it.copy(toastMessage = successMsg, toastType = MagicToastType.SUCCESS)
                else {
                    analyticsHelper.logEvent("error_reject_friend_request", mapOf("error" to errorMsg))
                    it.copy(toastMessage = errorMsg, toastType = MagicToastType.ERROR)
                }
            }
        }
    }

    fun cancelOutgoingRequest(friendshipId: String, errorMsg: String, successMsg: String) {
        viewModelScope.launch {
            val result = friendRepo.cancelOutgoingRequest(friendshipId)
            analyticsHelper.logEvent("cancel_outgoing_request")
            _uiState.update {
                if (result.isSuccess)
                    it.copy(toastMessage = successMsg, toastType = MagicToastType.SUCCESS)
                else
                    it.copy(toastMessage = errorMsg, toastType = MagicToastType.ERROR)
            }
        }
    }

    fun clearToast() = _uiState.update { it.copy(toastMessage = null, toastType = MagicToastType.ERROR) }

    private fun refreshOutgoingIfLoggedIn() {
        val userId = _currentUserId.value ?: return
        viewModelScope.launch { friendRepo.refreshOutgoingRequests(userId) }
    }
}
