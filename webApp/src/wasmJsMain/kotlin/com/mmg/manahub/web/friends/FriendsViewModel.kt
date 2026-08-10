package com.mmg.manahub.web.friends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.domain.repository.FriendRepository
import com.mmg.manahub.core.model.Friend
import com.mmg.manahub.core.model.FriendRequest
import com.mmg.manahub.core.model.OutgoingFriendRequest
import com.mmg.manahub.web.common.toUserFacingMessage
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Resolved state for [FriendsScreen] (web scope expansion, Friends slice, approved 2026-08-04 --
 * second wave after Settings/Profile/Add Card). Mirrors [com.mmg.manahub.web.auth.AuthUiState]'s
 * security discipline: never exposes the raw `SessionStatus`/`UserSession` to the UI layer.
 *
 * Unlike [com.mmg.manahub.web.profile.ProfileUiState], there is NO separate `Guest` state here.
 * A guest (anonymous-signed-in) session already has a real `auth.uid()`, and the `friendships`
 * table is keyed purely on that id -- no `user_profiles` row is required for the CURRENT user to
 * have friends, only for OTHER users to be findable/displayable via [FriendRepository.searchByGameTag]
 * (which a guest, having no row, would simply never appear in). So a guest gets the full [Content]
 * state like any other signed-in session.
 */
sealed interface FriendsUiState {
    data object Loading : FriendsUiState
    data object SignedOut : FriendsUiState
    data class Content(
        val currentUserId: String,
        val friends: List<Friend> = emptyList(),
        val pendingRequests: List<FriendRequest> = emptyList(),
        val outgoingRequests: List<OutgoingFriendRequest> = emptyList(),
        /** Friendship ids currently mid-mutation (accept/reject/cancel/remove) -- disables that row's button(s). */
        val actionInFlightIds: Set<String> = emptySet(),
        val error: String? = null,
    ) : FriendsUiState
}

/**
 * Backs [FriendsScreen] -- the eighth REAL `:webApp` MVP screen (web scope expansion, Friends
 * slice). Scope, per the task brief: friends list + pending/outgoing requests with accept/reject/
 * cancel/remove, and a search-by-game-tag add-friend flow. Deliberately EXCLUDES the friend-detail
 * view ([FriendRepository.getFriendCollection]/[FriendRepository.getFriendStats]/
 * [FriendRepository.getFriendMatchHistory] have no UI consumer yet -- see [FriendsScreen]'s KDoc)
 * and the referral-invite flow ([FriendRepository.acceptInvite]/[FriendRepository.getMyShareUrl])
 * -- both documented follow-ups, not half-built here. [FriendRepository.upsertMyStats] also has no
 * web caller yet (Android's own stats-sync worker has no web equivalent).
 *
 * All three list flows ([FriendRepository.observeFriends]/`observePendingRequests`/
 * `observeOutgoingRequests`) are reactive: an accept/reject/cancel/remove mutates
 * [WebFriendRepository][com.mmg.manahub.core.data.repository.WebFriendRepository]'s in-memory cache
 * on success, so the corresponding list updates automatically -- this ViewModel never manually
 * splices an id out of a locally-held list.
 */
class FriendsViewModel(
    private val friendRepository: FriendRepository,
    supabaseClient: SupabaseClient,
    private val crashReporter: CrashReporter,
) : ViewModel() {

    private val auth = supabaseClient.auth

    private val _uiState = MutableStateFlow<FriendsUiState>(FriendsUiState.Loading)
    val uiState: StateFlow<FriendsUiState> = _uiState.asStateFlow()

    private val _gameTagInput = MutableStateFlow("")
    val gameTagInput: StateFlow<String> = _gameTagInput.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _searchResult = MutableStateFlow<Friend?>(null)
    val searchResult: StateFlow<Friend?> = _searchResult.asStateFlow()

    /** Either an error ("not found", network failure) or an info message ("request sent"). */
    private val _searchMessage = MutableStateFlow<String?>(null)
    val searchMessage: StateFlow<String?> = _searchMessage.asStateFlow()

    private val _isSendingRequest = MutableStateFlow(false)
    val isSendingRequest: StateFlow<Boolean> = _isSendingRequest.asStateFlow()

    // ── Referral-invite redemption (Friends completion slice, 2026-08-05) ────────────────────────
    private val _inviteCodeInput = MutableStateFlow("")
    val inviteCodeInput: StateFlow<String> = _inviteCodeInput.asStateFlow()

    private val _isAcceptingInvite = MutableStateFlow(false)
    val isAcceptingInvite: StateFlow<Boolean> = _isAcceptingInvite.asStateFlow()

    private val _inviteMessage = MutableStateFlow<String?>(null)
    val inviteMessage: StateFlow<String?> = _inviteMessage.asStateFlow()

    private val _inviteMessageIsError = MutableStateFlow(false)
    val inviteMessageIsError: StateFlow<Boolean> = _inviteMessageIsError.asStateFlow()

    private var streamsStarted = false

    init {
        viewModelScope.launch {
            auth.sessionStatus.collect { status -> handleSessionStatus(status) }
        }
    }

    private fun handleSessionStatus(status: SessionStatus) {
        when (status) {
            is SessionStatus.Authenticated -> {
                val userId = status.session.user?.id
                if (userId.isNullOrBlank()) {
                    _uiState.value = FriendsUiState.Content(currentUserId = "", error = "No user id in session.")
                    return
                }
                if (_uiState.value !is FriendsUiState.Content) {
                    _uiState.value = FriendsUiState.Content(currentUserId = userId)
                }
                if (!streamsStarted) {
                    streamsStarted = true
                    observeStreams()
                }
                refreshAll(userId)
            }
            is SessionStatus.NotAuthenticated -> _uiState.value = FriendsUiState.SignedOut
            is SessionStatus.Initializing -> if (_uiState.value !is FriendsUiState.Content) {
                _uiState.value = FriendsUiState.Loading
            }
            is SessionStatus.RefreshFailure -> _uiState.value = FriendsUiState.SignedOut
        }
    }

    private fun observeStreams() {
        viewModelScope.launch {
            friendRepository.observeFriends().collect { list -> updateContent { it.copy(friends = list) } }
        }
        viewModelScope.launch {
            friendRepository.observePendingRequests().collect { list ->
                updateContent { it.copy(pendingRequests = list) }
            }
        }
        viewModelScope.launch {
            friendRepository.observeOutgoingRequests().collect { list ->
                updateContent { it.copy(outgoingRequests = list) }
            }
        }
    }

    private fun updateContent(transform: (FriendsUiState.Content) -> FriendsUiState.Content) {
        _uiState.update { state -> if (state is FriendsUiState.Content) transform(state) else state }
    }

    /** Full pull of friends + both request directions. Failures surface in [FriendsUiState.Content.error]. */
    private fun refreshAll(userId: String) {
        viewModelScope.launch {
            val results = listOf(
                friendRepository.refreshFriends(userId),
                friendRepository.refreshRequests(userId),
                friendRepository.refreshOutgoingRequests(userId),
            )
            results.firstOrNull { it.isFailure }?.let { failure ->
                val message = failure.exceptionOrNull()
                    ?.toUserFacingMessage("load your friends", crashReporter)
                    ?: "Couldn't load your friends. Please try again."
                updateContent { it.copy(error = message) }
            }
        }
    }

    fun onGameTagInputChanged(value: String) {
        _gameTagInput.value = value
        _searchResult.value = null
        _searchMessage.value = null
    }

    /** Looks up a user by exact game tag. No-ops on blank input or while a search is already in flight. */
    fun searchByGameTag() {
        val tag = _gameTagInput.value.trim()
        if (tag.isEmpty() || _isSearching.value) return
        viewModelScope.launch {
            _isSearching.value = true
            _searchMessage.value = null
            _searchResult.value = null
            try {
                val result = friendRepository.searchByGameTag(tag).getOrThrow()
                if (result == null) {
                    _searchMessage.value = "No user found with that game tag."
                } else {
                    _searchResult.value = result
                }
            } catch (e: Throwable) {
                _searchMessage.value = e.toUserFacingMessage("search for that game tag", crashReporter)
            } finally {
                _isSearching.value = false
            }
        }
    }

    /** Sends a friend request to the current [searchResult]. No-ops if there's nothing to send to. */
    fun sendFriendRequest() {
        val target = _searchResult.value ?: return
        val state = _uiState.value
        if (state !is FriendsUiState.Content || _isSendingRequest.value) return
        viewModelScope.launch {
            _isSendingRequest.value = true
            try {
                friendRepository.sendFriendRequest(state.currentUserId, target.userId).getOrThrow()
                friendRepository.refreshOutgoingRequests(state.currentUserId)
                _searchMessage.value = "Friend request sent to ${target.nickname}."
                _searchResult.value = null
                _gameTagInput.value = ""
            } catch (e: Throwable) {
                _searchMessage.value = e.toUserFacingMessage("send the friend request", crashReporter)
            } finally {
                _isSendingRequest.value = false
            }
        }
    }

    fun acceptRequest(friendshipId: String) {
        val userId = (_uiState.value as? FriendsUiState.Content)?.currentUserId ?: return
        runRowAction(friendshipId) { friendRepository.acceptRequest(friendshipId, userId).getOrThrow() }
    }

    fun rejectRequest(friendshipId: String) =
        runRowAction(friendshipId) { friendRepository.rejectRequest(friendshipId).getOrThrow() }

    fun cancelOutgoingRequest(friendshipId: String) =
        runRowAction(friendshipId) { friendRepository.cancelOutgoingRequest(friendshipId).getOrThrow() }

    fun removeFriend(friendshipId: String) =
        runRowAction(friendshipId) { friendRepository.removeFriend(friendshipId).getOrThrow() }

    /** Runs a single per-row mutation, guarding double-taps via [FriendsUiState.Content.actionInFlightIds]. */
    private fun runRowAction(friendshipId: String, action: suspend () -> Unit) {
        val state = _uiState.value
        if (state !is FriendsUiState.Content || friendshipId in state.actionInFlightIds) return
        viewModelScope.launch {
            updateContent { it.copy(actionInFlightIds = it.actionInFlightIds + friendshipId, error = null) }
            try {
                action()
            } catch (e: Throwable) {
                updateContent { it.copy(error = e.toUserFacingMessage("update that friend request", crashReporter)) }
            } finally {
                updateContent { it.copy(actionInFlightIds = it.actionInFlightIds - friendshipId) }
            }
        }
    }

    fun onInviteCodeInputChanged(value: String) {
        _inviteCodeInput.value = value
        _inviteMessage.value = null
    }

    /**
     * Redeems a referral code via [FriendRepository.acceptInvite]. Validates the Crockford base32
     * shape client-side first -- matches Android's own
     * `InviteDispatcherViewModel.isValidReferralCode` -- so an obviously malformed paste never
     * reaches the network.
     *
     * `accept_invite` (confirmed live via the RPC's own SQL body, `pg_get_functiondef`) writes an
     * ACCEPTED friendship directly for both directions -- there is no separate pending-request
     * step to "accept" afterward, only a [FriendRepository.refreshFriends] cache pull so the new
     * friend appears in [FriendsUiState.Content.friends] immediately rather than waiting for the
     * next full reload.
     */
    fun acceptInviteWithCode() {
        val state = _uiState.value
        if (state !is FriendsUiState.Content || _isAcceptingInvite.value) return
        val code = _inviteCodeInput.value.trim().uppercase()
        if (!isValidReferralCode(code)) {
            _inviteMessage.value = "That doesn't look like a valid invite code."
            _inviteMessageIsError.value = true
            return
        }
        viewModelScope.launch {
            _isAcceptingInvite.value = true
            _inviteMessage.value = null
            try {
                val result = friendRepository.acceptInvite(code).getOrThrow()
                friendRepository.refreshFriends(state.currentUserId)
                _inviteMessage.value = if (result.inviterNickname != null) {
                    "Now you are friends with ${result.inviterNickname}."
                } else {
                    "New friendship accepted!"
                }
                _inviteMessageIsError.value = false
                _inviteCodeInput.value = ""
            } catch (e: Throwable) {
                // Known semantic tokens surfaced by WebFriendRepository.acceptInvite's
                // recoverCatching mapping -- same wording as Android's AppNavGraph invite toasts
                // (friends_invite_self/friends_invite_invalid strings.xml entries).
                val token = e.message ?: ""
                _inviteMessage.value = when {
                    token.contains("SELF_INVITE", ignoreCase = true) -> "That's your own invite link."
                    token.contains("INVALID_CODE", ignoreCase = true) -> "Invitation is no longer valid."
                    else -> e.toUserFacingMessage("accept the invitation", crashReporter)
                }
                _inviteMessageIsError.value = true
            } finally {
                _isAcceptingInvite.value = false
            }
        }
    }

    private fun isValidReferralCode(code: String): Boolean =
        code.length == 8 && code.all { it in "23456789ABCDEFGHJKMNPQRSTVWXYZ" }
}
