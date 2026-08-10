package com.mmg.manahub.web.friends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.domain.repository.FriendRepository
import com.mmg.manahub.core.model.Friend
import com.mmg.manahub.core.model.FriendCard
import com.mmg.manahub.core.model.FriendMatchHistory
import com.mmg.manahub.core.model.FriendStats
import com.mmg.manahub.web.common.toUserFacingMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Top-level tabs on [FriendDetailScreen]. */
enum class FriendDetailTab { COLLECTION, STATS, HISTORY }

/**
 * Resolved state for [FriendDetailScreen] (web scope expansion, Friends completion slice,
 * approved 2026-08-05 -- the two pieces explicitly deferred from the original Friends slice, see
 * `project_w_friends_slice.md`).
 *
 * [friend] is resolved by matching [FriendRepository.observeFriends] against the `friendUserId`
 * nav arg -- there is no dedicated "get single friend" RPC, and the friends list is already the
 * local source of truth every other web screen reads (same pattern Android's own
 * `FriendDetailViewModel` uses). `friend == null` after [isLoadingFriend] settles means the id
 * either never was a friend or was removed since -- rendered as a clean "not found" state, never a
 * forced back-navigation (a deliberate, documented simplification vs. Android's auto-pop-on-removal
 * behaviour: this is a web MVP surface, and silently popping the screen out from under the user is
 * more surprising than a static empty state on the web's own back-button-friendly navigation model).
 */
data class FriendDetailUiState(
    val friend: Friend? = null,
    val isLoadingFriend: Boolean = true,
    val selectedTab: FriendDetailTab = FriendDetailTab.COLLECTION,
    val searchQuery: String = "",
    val cards: List<FriendCard> = emptyList(),
    val isLoadingCards: Boolean = false,
    val cardsError: String? = null,
    val hasMoreCards: Boolean = false,
    val isLoadingMoreCards: Boolean = false,
    val stats: FriendStats? = null,
    val isLoadingStats: Boolean = false,
    val statsError: String? = null,
    val statsLoaded: Boolean = false,
    val matchHistory: FriendMatchHistory? = null,
    val isLoadingHistory: Boolean = false,
    val historyError: String? = null,
    val historyLoaded: Boolean = false,
)

/**
 * Backs [FriendDetailScreen] -- the friend-detail view deferred from the original Friends slice.
 * Takes `friendUserId` as a Koin runtime parameter (`params.get()`), the SAME shape as
 * [com.mmg.manahub.web.carddetail.CardDetailViewModel]/[com.mmg.manahub.web.deckeditor.DeckEditorViewModel]/
 * [com.mmg.manahub.web.trades.TradeThreadViewModel] -- `:webApp` has no `koin-androidx-compose`
 * `SavedStateHandle` integration.
 *
 * Deliberately scoped DOWN from Android's `FriendDetailViewModel` (which the task brief explicitly
 * asked to check for precedent): a SINGLE collection list (hard-coded `list = "collection"`, no
 * Wishlist/Trade sub-tabs or rarity/color/condition filters -- the same kind of MVP cut
 * `DeckEditorScreen`'s own KDoc documents relative to Deck Studio), and a manual "Load more" button
 * instead of scroll-position pagination (no `LazyListState`/`derivedStateOf` hookup). Stats and
 * History are lazy-loaded on first tab selection, matching Android's `selectTab` behaviour.
 *
 * [FriendRepository.getFriendStats]/[FriendRepository.getFriendMatchHistory] both document
 * `Result.success(null)` as a VALID "no data yet" outcome, not an error -- [statsLoaded]/
 * [historyLoaded] distinguish "loaded, nothing there" (show a clean empty state) from "never
 * fetched" (show nothing / let the loading branch render), and [statsError]/[historyError] are
 * reserved for an actual [Result.failure] (network/server error, or -- for the collection list --
 * a server-side RLS access rejection; the [FriendRepository] contract does not distinguish "access
 * denied" from "network failure" in its `Result.failure`, so both render the same generic retry
 * state here).
 */
class FriendDetailViewModel(
    private val friendUserId: String,
    private val friendRepository: FriendRepository,
    private val crashReporter: CrashReporter,
) : ViewModel() {

    private companion object {
        const val PAGE_SIZE = 60
    }

    private val _uiState = MutableStateFlow(FriendDetailUiState())
    val uiState: StateFlow<FriendDetailUiState> = _uiState.asStateFlow()

    // Tracks the server-side row offset for "load more" -- reset to 0 on every new query.
    private var serverOffset = 0

    init {
        viewModelScope.launch {
            friendRepository.observeFriends().collect { friends ->
                val found = friends.firstOrNull { it.userId == friendUserId }
                _uiState.update { it.copy(friend = found, isLoadingFriend = false) }
            }
        }

        // Reactive collection fetch, debounced on search-query changes, only while the
        // COLLECTION tab is selected -- isLoadingCards is flipped BEFORE the debounce (same
        // ordering Android's own pipeline uses) so the spinner appears immediately, not 300ms
        // late. Re-fires on tab re-entry too, since `selectedTab`'s own distinctUntilChanged
        // still emits a fresh combine tuple when it flips back to COLLECTION.
        combine(
            _uiState.map { it.selectedTab }.distinctUntilChanged(),
            _uiState.map { it.searchQuery }.distinctUntilChanged(),
        ) { tab, query -> tab to query }
            .filter { (tab, _) -> tab == FriendDetailTab.COLLECTION }
            .onEach { _uiState.update { it.copy(isLoadingCards = true, cardsError = null) } }
            .debounce(300L)
            .onEach { (_, query) -> loadCollection(query) }
            .launchIn(viewModelScope)
    }

    private suspend fun loadCollection(query: String) {
        serverOffset = 0
        val result = friendRepository.getFriendCollection(
            friendUserId = friendUserId,
            list = "collection",
            query = query,
            limit = PAGE_SIZE,
            offset = 0,
        )
        result.fold(
            onSuccess = { cards ->
                serverOffset = PAGE_SIZE
                _uiState.update {
                    it.copy(
                        cards = cards,
                        isLoadingCards = false,
                        cardsError = null,
                        hasMoreCards = cards.size >= PAGE_SIZE,
                    )
                }
            },
            onFailure = { e ->
                _uiState.update {
                    it.copy(
                        cards = emptyList(),
                        isLoadingCards = false,
                        hasMoreCards = false,
                        cardsError = e.toUserFacingMessage("load this friend's collection", crashReporter),
                    )
                }
            },
        )
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    /** Bypasses the reactive pipeline directly -- a plain re-set of the same query would be
     * swallowed by `distinctUntilChanged()`. */
    fun retryCollection() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingCards = true, cardsError = null) }
            loadCollection(_uiState.value.searchQuery)
        }
    }

    /** Fetches the next page and appends. No-op while already loading or when no more pages exist. */
    fun loadMoreCards() {
        val state = _uiState.value
        if (state.isLoadingMoreCards || state.isLoadingCards || !state.hasMoreCards) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMoreCards = true) }
            val result = friendRepository.getFriendCollection(
                friendUserId = friendUserId,
                list = "collection",
                query = state.searchQuery,
                limit = PAGE_SIZE,
                offset = serverOffset,
            )
            result.fold(
                onSuccess = { newCards ->
                    serverOffset += PAGE_SIZE
                    _uiState.update {
                        it.copy(
                            cards = it.cards + newCards,
                            isLoadingMoreCards = false,
                            hasMoreCards = newCards.size >= PAGE_SIZE,
                        )
                    }
                },
                onFailure = {
                    _uiState.update { it.copy(isLoadingMoreCards = false, hasMoreCards = false) }
                },
            )
        }
    }

    fun selectTab(tab: FriendDetailTab) {
        _uiState.update { it.copy(selectedTab = tab) }
        val state = _uiState.value
        when (tab) {
            FriendDetailTab.STATS -> if (!state.statsLoaded && !state.isLoadingStats) loadStats()
            FriendDetailTab.HISTORY -> if (!state.historyLoaded && !state.isLoadingHistory) loadHistory()
            FriendDetailTab.COLLECTION -> Unit
        }
    }

    fun retryStats() = loadStats()

    fun retryHistory() = loadHistory()

    private fun loadStats() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingStats = true, statsError = null) }
            val result = friendRepository.getFriendStats(friendUserId)
            result.fold(
                onSuccess = { stats ->
                    _uiState.update {
                        it.copy(stats = stats, isLoadingStats = false, statsLoaded = true, statsError = null)
                    }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(
                            isLoadingStats = false,
                            statsError = e.toUserFacingMessage("load this friend's stats", crashReporter),
                        )
                    }
                },
            )
        }
    }

    private fun loadHistory() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingHistory = true, historyError = null) }
            val result = friendRepository.getFriendMatchHistory(friendUserId)
            result.fold(
                onSuccess = { history ->
                    _uiState.update {
                        it.copy(
                            matchHistory = history,
                            isLoadingHistory = false,
                            historyLoaded = true,
                            historyError = null,
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(
                            isLoadingHistory = false,
                            historyError = e.toUserFacingMessage("load match history", crashReporter),
                        )
                    }
                },
            )
        }
    }
}
