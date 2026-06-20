package com.mmg.manahub.feature.friends.presentation.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.manahub.core.ui.components.MagicToastType
import com.mmg.manahub.core.domain.auth.SessionState
import com.mmg.manahub.core.domain.auth.AuthRepository
import com.mmg.manahub.feature.friends.domain.model.Friend
import com.mmg.manahub.feature.friends.domain.model.FriendCard
import com.mmg.manahub.feature.friends.domain.model.FriendMatchHistory
import com.mmg.manahub.feature.friends.domain.model.FriendStats
import com.mmg.manahub.feature.friends.domain.repository.FriendRepository
import com.mmg.manahub.feature.friends.domain.usecase.GetFriendCollectionUseCase
import com.mmg.manahub.feature.trades.domain.model.TradeProposal
import com.mmg.manahub.feature.trades.domain.repository.TradesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
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

enum class Condition(val label: String) {
    NM("NM"), LP("LP"), MP("MP"), HP("HP"), DMG("DMG")
}

data class FolderFilters(
    val sets: Set<String> = emptySet(),
    val rarities: Set<com.mmg.manahub.core.model.Rarity> = emptySet(),
    val colors: Set<com.mmg.manahub.core.model.MtgColor> = emptySet(),
    val foilOnly: Boolean = false,
    val conditions: Set<Condition> = emptySet(),
    val languages: Set<String> = emptySet()
) {
    val hasFilters: Boolean
        get() = sets.isNotEmpty() || rarities.isNotEmpty() || colors.isNotEmpty() || foilOnly || conditions.isNotEmpty() || languages.isNotEmpty()
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
        val filters: FolderFilters = FolderFilters(),
        val cards: List<FriendCard> = emptyList(),
        val isLoadingCards: Boolean = false,
        /** True while the next page of cards is being fetched (pagination). */
        val isLoadingMore: Boolean = false,
        /** True when there are more server pages to fetch for the current list. */
        val hasMoreCards: Boolean = false,
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
        /** Aggregate match history vs this friend; null until loaded. */
        val gameHistory: FriendMatchHistory? = null,
        /** True while the match history request is in flight. */
        val isLoadingGameHistory: Boolean = false,
        /** True when the last match history fetch resulted in a network or server error. */
        val gameHistoryError: Boolean = false,
    )

    private companion object {
        const val PAGE_SIZE = 50
        // When a search query is active, client-side name filtering makes server-side
        // pagination unreliable. Load a generous batch so results feel immediate.
        const val SEARCH_LIMIT = 500
    }

    /** One-shot events emitted to the UI layer. */
    sealed interface UiEvent {
        /** Signals that the screen should close and return to the friends list. */
        object NavigateBack : UiEvent
    }

    private val friendUserId: String = savedStateHandle["userId"] ?: ""

    // Tracks the server-side row offset for "load more". Reset to 0 on every
    // new query/sub-tab so pagination always starts from page 1.
    private var serverOffset = 0

    // Incremented each time the FOLDER tab is (re-)entered to force the reactive
    // pipeline to re-fetch even when subTab/query/filters have not changed.
    // This token is never exposed in UiState because it carries no display meaning.
    private val _folderRefreshToken = MutableStateFlow(0)

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

        // Fetch trades from the backend so the cache is populated when this screen opens.
        // The cache may be empty if the user has not visited the trades section yet.
        viewModelScope.launch {
            val myUserId = authRepo.sessionState
                .mapNotNull { (it as? SessionState.Authenticated)?.user?.id }
                .first()
            tradesRepo.refreshProposals(myUserId)
        }

        // Observe trade history shared between the current user and this friend.
        // Uses sessionState in the combine so no suspend call blocks the init path,
        // and the filter remains live even if the user logs out and back in.
        @Suppress("OPT_IN_USAGE")
        combine(
            tradesRepo.observeAllProposals(),
            _uiState.map { it.friend?.userId }.distinctUntilChanged(),
            authRepo.sessionState,
        ) { allProposals, friendUid, session ->
            val myUserId = (session as? SessionState.Authenticated)?.user?.id
            if (friendUid == null || myUserId == null) emptyList()
            else allProposals
                .filter { proposal ->
                    (proposal.proposerId == myUserId && proposal.receiverId == friendUid) ||
                        (proposal.proposerId == friendUid && proposal.receiverId == myUserId)
                }
                .sortedWith(
                    compareByDescending<TradeProposal> { it.status.isActive }
                        .thenByDescending { it.updatedAt }
                )
        }
            .distinctUntilChanged()
            .onEach { filtered -> _uiState.update { it.copy(tradeHistory = filtered) } }
            .launchIn(viewModelScope)

        // Reactively reload cards whenever the sub-tab, search query, filters, or the
        // folder refresh token changes. The refresh token is bumped each time the FOLDER
        // tab is (re-)entered, ensuring a re-fetch even when the other three values are
        // unchanged (which would otherwise be swallowed by distinctUntilChanged).
        //
        // isLoadingCards is set immediately in onEach (before the debounce) so the loading
        // indicator appears without the 300 ms jitter of the old emit(null) pattern.
        // Pagination resets on every new combination.
        @Suppress("OPT_IN_USAGE") // flatMapLatest is stable in kotlinx.coroutines
        combine(
            _uiState.map { it.folderSubTab }.distinctUntilChanged(),
            _uiState.map { it.searchQuery }.distinctUntilChanged(),
            _uiState.map { it.filters }.distinctUntilChanged(),
            _folderRefreshToken,
        ) { subTab, query, filters, _ -> Triple(subTab, query, filters) }
            .onEach {
                _uiState.update {
                    it.copy(
                        isLoadingCards = true,
                        cardError = false,
                        cards = emptyList(),
                        hasMoreCards = false,
                        isLoadingMore = false,
                    )
                }
            }
            .debounce(300L)
            .flatMapLatest { (subTab, query, filters) ->
                flow {
                    serverOffset = 0
                    val limit = if (query.isBlank()) PAGE_SIZE else SEARCH_LIMIT
                    emit(getFriendCollectionUseCase(friendUserId, subTab.listValue, query, filters, limit, 0) to query.isBlank())
                }
            }
            .onEach { (result, isPaginated) ->
                val cards = result.getOrDefault(emptyList())
                if (result.isSuccess) serverOffset = if (isPaginated) PAGE_SIZE else SEARCH_LIMIT
                _uiState.update {
                    it.copy(
                        cards = cards,
                        isLoadingCards = false,
                        isLoadingMore = false,
                        cardError = result.isFailure,
                        hasMoreCards = isPaginated && cards.size >= PAGE_SIZE,
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    /**
     * Fetches the next page of cards and appends them to [UiState.cards].
     *
     * No-op when already loading, no more pages exist, or a search query is active
     * (search loads a single large batch — pagination doesn't compose with client-side
     * name filtering).
     */
    fun loadMoreCards() {
        val state = _uiState.value
        if (state.isLoadingMore || state.isLoadingCards || !state.hasMoreCards) return
        if (state.searchQuery.isNotBlank()) return
        val subTab = state.folderSubTab
        val offset = serverOffset
        val filters = state.filters
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            val result = getFriendCollectionUseCase(friendUserId, subTab.listValue, "", filters, PAGE_SIZE, offset)
            result.onSuccess { newCards ->
                serverOffset += PAGE_SIZE
                _uiState.update {
                    it.copy(
                        cards = it.cards + newCards,
                        isLoadingMore = false,
                        hasMoreCards = newCards.size >= PAGE_SIZE,
                    )
                }
            }
            result.onFailure {
                _uiState.update { it.copy(isLoadingMore = false) }
            }
        }
    }

    /**
     * Switches the active top-level tab.
     *
     * - Switching back to [FriendTab.FOLDER] resets the search query and bumps the
     *   [_folderRefreshToken] so the reactive pipeline triggers a fresh fetch even when
     *   subTab/query/filters are already at their default values. Cards are NOT cleared
     *   here; the pipeline's onEach clears them once it wakes up, keeping [UiState.isLoadingCards]
     *   and the card list in sync and avoiding a transient empty-state flash.
     * - Switching to [FriendTab.STATS] triggers a lazy load if stats have not been fetched yet.
     */
    fun selectTab(tab: FriendTab) {
        _uiState.update { state ->
            when {
                tab == FriendTab.FOLDER && state.selectedTab != FriendTab.FOLDER ->
                    state.copy(selectedTab = tab, searchQuery = "")
                else -> state.copy(selectedTab = tab)
            }
        }
        if (tab == FriendTab.FOLDER) {
            // Bump the token to guarantee the pipeline fires even if query/subTab/filters
            // did not change (distinctUntilChanged would suppress the emission otherwise).
            // This covers both the re-entry case (coming from STATS/HISTORY) and the case
            // where the user taps FOLDER while already on FOLDER (acts as a manual refresh).
            _folderRefreshToken.value++
        }
        if (tab == FriendTab.STATS) {
            val current = _uiState.value
            if (current.friendStats == null && !current.isLoadingStats && !current.statsError) {
                loadStats()
            }
        }
        if (tab == FriendTab.HISTORY) {
            val current = _uiState.value
            if (current.gameHistory == null && !current.isLoadingGameHistory && !current.gameHistoryError) {
                loadGameHistory()
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

    /** Retries loading the match history after a previous failure. */
    fun retryGameHistory() {
        loadGameHistory()
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

    private fun loadGameHistory() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingGameHistory = true, gameHistoryError = false) }
            val result = friendRepo.getFriendMatchHistory(friendUserId)
            _uiState.update {
                it.copy(
                    gameHistory = result.getOrNull(),
                    isLoadingGameHistory = false,
                    gameHistoryError = result.isFailure,
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

    fun toggleFilter(
        sets: Set<String>? = null,
        rarities: Set<com.mmg.manahub.core.model.Rarity>? = null,
        colors: Set<com.mmg.manahub.core.model.MtgColor>? = null,
        foilOnly: Boolean? = null,
        conditions: Set<Condition>? = null,
        languages: Set<String>? = null,
    ) {
        _uiState.update { state ->
            val current = state.filters
            state.copy(
                filters = current.copy(
                    sets = sets ?: current.sets,
                    rarities = rarities ?: current.rarities,
                    colors = colors ?: current.colors,
                    foilOnly = foilOnly ?: current.foilOnly,
                    conditions = conditions ?: current.conditions,
                    languages = languages ?: current.languages,
                )
            )
        }
    }

    fun clearFilters() {
        _uiState.update { it.copy(filters = FolderFilters()) }
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
