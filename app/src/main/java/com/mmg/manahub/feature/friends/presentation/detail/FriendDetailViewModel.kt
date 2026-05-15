package com.mmg.manahub.feature.friends.presentation.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.manahub.core.ui.components.MagicToastType
import com.mmg.manahub.feature.auth.domain.repository.AuthRepository
import com.mmg.manahub.feature.friends.domain.model.Friend
import com.mmg.manahub.feature.friends.domain.model.FriendCard
import com.mmg.manahub.feature.friends.domain.model.FriendStats
import com.mmg.manahub.feature.friends.domain.repository.FriendRepository
import com.mmg.manahub.feature.friends.domain.usecase.GetFriendCollectionUseCase
import com.mmg.manahub.feature.trades.domain.model.TradeProposal
import com.mmg.manahub.feature.trades.domain.repository.TradesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import com.mmg.manahub.feature.auth.domain.model.SessionState
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Top-level tabs displayed on the friend detail screen. */
enum class FriendTab { FOLDER, STATS, HISTORY }

/**
 * Sub-tabs within the Folder tab.
 *
 * [listValue] corresponds to the `p_list` parameter accepted by the `get_friend_collection` RPC.
 */
enum class FolderSubTab(val listValue: String) {
    COLLECTION("collection"),
    WISHLIST("wishlist"),
    TRADE("trade"),
}

/**
 * ViewModel for the friend detail screen.
 *
 * Reads the friend's auth UUID from [SavedStateHandle] under key `"userId"`, looks up
 * the matching [Friend] domain model from the local friends cache, and reactively
 * loads card lists whenever the selected sub-tab or search query changes.
 */
@HiltViewModel
class FriendDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val friendRepo: FriendRepository,
    private val getFriendCollectionUseCase: GetFriendCollectionUseCase,
    private val tradesRepo: TradesRepository,
    private val authRepo: AuthRepository,
) : ViewModel() {

    /** UI state for the friend detail screen. */
    data class UiState(
        val friend: Friend? = null,
        val isLoadingFriend: Boolean = true,
        val selectedTab: FriendTab = FriendTab.FOLDER,
        val folderSubTab: FolderSubTab = FolderSubTab.COLLECTION,
        val searchQuery: String = "",
        val cards: List<FriendCard> = emptyList(),
        val isLoadingCards: Boolean = false,
        /** True when the last card fetch resulted in an error (access denied or network). */
        val cardError: Boolean = false,
        val toastMessage: String? = null,
        val toastType: MagicToastType = MagicToastType.ERROR,
        /** Completed and terminal trade proposals shared between me and this friend. */
        val tradeHistory: List<TradeProposal> = emptyList(),
        /** Friend's collection statistics snapshot; null until loaded or if unavailable. */
        val friendStats: FriendStats? = null,
        /** True while the stats request is in flight. */
        val isLoadingStats: Boolean = false,
        /** True when the last stats fetch resulted in a network or server error. */
        val statsError: Boolean = false,
    )

    /** One-shot events emitted to the UI layer. */
    sealed interface UiEvent {
        /** Signals that the screen should close and return to the friends list. */
        object NavigateBack : UiEvent
    }

    private val friendUserId: String = savedStateHandle["userId"] ?: ""

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events: Flow<UiEvent> = _events.receiveAsFlow()

    init {
        if (friendUserId.isBlank()) {
            // Guard: a blank userId means navigation passed a malformed argument.
            viewModelScope.launch { _events.send(UiEvent.NavigateBack) }
        } else {
            setup()
        }
    }

    private fun setup() {
        // Observe the local friends cache and find the matching Friend by userId.
        friendRepo.observeFriends()
            .onEach { friends ->
                val found = friends.firstOrNull { it.userId == friendUserId }
                // If we had a loaded friend and they no longer appear in the list (removed
                // externally by the remote peer), navigate back rather than leaving the user
                // on a blank detail screen.
                val hadFriend = !_uiState.value.isLoadingFriend && _uiState.value.friend != null
                if (found == null && hadFriend) {
                    _events.send(UiEvent.NavigateBack)
                }
                _uiState.update { it.copy(friend = found, isLoadingFriend = false) }
            }
            .launchIn(viewModelScope)

        // Observe trade history shared between the current user and this friend.
        // Uses sessionState in the combine so no suspend call blocks the init path,
        // and the filter remains live even if the user logs out and back in.
        @Suppress("OPT_IN_USAGE")
        combine(
            tradesRepo.observeProposalHistory(),
            _uiState.map { it.friend?.userId }.distinctUntilChanged(),
            authRepo.sessionState,
        ) { history, friendUid, session ->
            val myUserId = (session as? SessionState.Authenticated)?.user?.id
            if (friendUid == null || myUserId == null) emptyList()
            else history.filter { proposal ->
                (proposal.proposerId == myUserId && proposal.receiverId == friendUid) ||
                    (proposal.proposerId == friendUid && proposal.receiverId == myUserId)
            }
        }
            .distinctUntilChanged()
            .onEach { filtered -> _uiState.update { it.copy(tradeHistory = filtered) } }
            .launchIn(viewModelScope)

        // Reactively reload cards whenever the sub-tab or search query changes.
        // debounce prevents rapid re-fetches while the user is typing.
        @Suppress("OPT_IN_USAGE") // flatMapLatest is stable in kotlinx.coroutines
        combine(
            _uiState.map { it.folderSubTab }.distinctUntilChanged(),
            _uiState.map { it.searchQuery }.distinctUntilChanged(),
        ) { subTab, query -> subTab to query }
            .debounce(300L)
            .flatMapLatest { (subTab, query) ->
                flow {
                    emit(null) // signal loading start
                    emit(getFriendCollectionUseCase(friendUserId, subTab.listValue, query))
                }
            }
            .onEach { result ->
                if (result == null) {
                    _uiState.update { it.copy(isLoadingCards = true, cardError = false) }
                } else {
                    _uiState.update {
                        it.copy(
                            cards = result.getOrDefault(emptyList()),
                            isLoadingCards = false,
                            cardError = result.isFailure,
                        )
                    }
                }
            }
            .launchIn(viewModelScope)
    }

    /**
     * Switches the active top-level tab.
     *
     * - Switching back to [FriendTab.FOLDER] resets search and cards so the list is fresh.
     * - Switching to [FriendTab.STATS] triggers a lazy load if stats have not been fetched yet.
     */
    fun selectTab(tab: FriendTab) {
        _uiState.update { state ->
            when {
                tab == FriendTab.FOLDER && state.selectedTab != FriendTab.FOLDER ->
                    state.copy(selectedTab = tab, searchQuery = "", cards = emptyList())
                else -> state.copy(selectedTab = tab)
            }
        }
        if (tab == FriendTab.STATS) {
            val current = _uiState.value
            // Only load if we haven't fetched yet and are not already loading.
            if (current.friendStats == null && !current.isLoadingStats && !current.statsError) {
                loadStats()
            }
        }
    }

    /**
     * Retries loading the friend's stats after a previous failure.
     * Exposed to the UI as an onClick handler for the retry button.
     */
    fun retryStats() {
        loadStats()
    }

    /**
     * Fetches the friend's collection stats from the repository and updates [UiState].
     *
     * Sets [UiState.isLoadingStats] while the request is in flight and surfaces any failure
     * via [UiState.statsError]. On success populates [UiState.friendStats].
     */
    private fun loadStats() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingStats = true, statsError = false) }
            val result = friendRepo.getFriendStats(friendUserId)
            _uiState.update {
                it.copy(
                    friendStats = result.getOrNull(),
                    isLoadingStats = false,
                    statsError = result.isFailure,
                )
            }
        }
    }

    /** Switches the active sub-tab inside the Folder tab. */
    fun selectFolderSubTab(subTab: FolderSubTab) {
        _uiState.update { it.copy(folderSubTab = subTab) }
    }

    /** Updates the search query; card reload is debounced in the reactive pipeline. */
    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    /**
     * Removes the current friend using their friendship ID.
     *
     * On success emits [UiEvent.NavigateBack] so the screen pops itself.
     * On failure shows a toast with [errorMsg].
     *
     * @param errorMsg Localised error string resolved by the composable layer.
     */
    fun removeFriend(errorMsg: String) {
        val friendshipId = _uiState.value.friend?.id ?: return
        viewModelScope.launch {
            val result = friendRepo.removeFriend(friendshipId)
            if (result.isSuccess) {
                _events.send(UiEvent.NavigateBack)
            } else {
                _uiState.update {
                    it.copy(toastMessage = errorMsg, toastType = MagicToastType.ERROR)
                }
            }
        }
    }

    /** Clears the current toast so it does not re-appear after recomposition. */
    fun clearToast() {
        _uiState.update { it.copy(toastMessage = null, toastType = MagicToastType.ERROR) }
    }
}
