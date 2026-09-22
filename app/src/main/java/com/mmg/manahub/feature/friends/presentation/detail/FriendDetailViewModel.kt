package com.mmg.manahub.feature.friends.presentation.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.data.repository.TradesRepository
import com.mmg.manahub.core.domain.auth.AuthRepository
import com.mmg.manahub.core.domain.auth.SessionState
import com.mmg.manahub.core.domain.repository.FriendRepository
import com.mmg.manahub.core.domain.search.FriendCardSearchMapper
import com.mmg.manahub.core.model.AdvancedSearchQuery
import com.mmg.manahub.core.model.Friend
import com.mmg.manahub.core.model.FriendCard
import com.mmg.manahub.core.model.FriendCardCursor
import com.mmg.manahub.core.model.FriendCardSearchException
import com.mmg.manahub.core.model.FriendCardSearchParams
import com.mmg.manahub.core.model.FriendMatchHistory
import com.mmg.manahub.core.model.FriendStats
import com.mmg.manahub.core.model.SearchCriterion
import com.mmg.manahub.core.model.TradeProposal
import com.mmg.manahub.core.model.withMetadata
import com.mmg.manahub.core.ui.components.MagicToastType
import com.mmg.manahub.feature.friends.domain.usecase.SearchFriendCardsUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
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
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ComparableTimeMark
import kotlin.time.TimeSource

/** Top-level tabs displayed on the friend detail screen. */
enum class FriendTab { FOLDER, STATS, HISTORY }

/** Sub-tabs within the Folder tab; [listValue] is the RPC's `p_list`. */
enum class FolderSubTab(val listValue: String) {
    COLLECTION("collection"),
    WISHLIST("wishlist"),
    TRADE("trade"),
}

/** Why the Folder tab's card list could not be shown. */
enum class FolderCardsError {
    ACCESS_DENIED,
    NETWORK,
    GENERIC,
    /** Terminal until the user signs in again; no retry is offered. */
    SESSION_EXPIRED,
    /** Terminal until the viewer completes their own profile; no retry is offered. */
    PROFILE_INCOMPLETE,
}

/**
 * Friend detail screen: profile header, the friend's searchable card lists, stats and history.
 * Reads the friend's auth UUID from the `"userId"` nav arg.
 */
class FriendDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val friendRepo: FriendRepository,
    private val searchFriendCards: SearchFriendCardsUseCase,
    private val tradesRepo: TradesRepository,
    private val authRepo: AuthRepository,
    private val crashReporter: CrashReporter,
    private val timeSource: TimeSource.WithComparableMarks = TimeSource.Monotonic,
) : ViewModel() {

    /** UI state for the friend detail screen. */
    data class UiState(
        val friend: Friend? = null,
        val isLoadingFriend: Boolean = true,
        val selectedTab: FriendTab = FriendTab.FOLDER,
        val folderSubTab: FolderSubTab = FolderSubTab.COLLECTION,
        /** Raw search-bar text, exactly as typed. */
        val searchText: String = "",
        val nameExact: Boolean = false,
        /** Applied Advanced Search criteria, excluding the name (which lives in [searchText]). */
        val advancedQuery: AdvancedSearchQuery = AdvancedSearchQuery(),
        val cards: List<FriendCard> = emptyList(),
        /** True while the first page of a new request is in flight; [cards] may still hold the previous results. */
        val isLoadingCards: Boolean = false,
        val isLoadingMore: Boolean = false,
        val hasMoreCards: Boolean = false,
        val loadMoreFailed: Boolean = false,
        val cardsError: FolderCardsError? = null,
        /** Whether the shown [cards] came from a filtered request (drives the empty-state copy). */
        val resultsFiltered: Boolean = false,
        /** Bumped on every NEW first page (never on append); the list scrolls to top when it changes. */
        val resultsVersion: Int = 0,
        /** Rows of the list the server could not evaluate against the active filters yet. */
        val unindexedCount: Int = 0,
        val toastMessage: String? = null,
        val toastType: MagicToastType = MagicToastType.ERROR,
        val tradeHistory: List<TradeProposal> = emptyList(),
        val friendStats: FriendStats? = null,
        val isLoadingStats: Boolean = false,
        val statsError: Boolean = false,
        val gameHistory: FriendMatchHistory? = null,
        val isLoadingGameHistory: Boolean = false,
        val gameHistoryError: Boolean = false,
    ) {
        private val hasEffectiveName: Boolean
            get() = FriendCardSearchMapper.normalizeName(searchText).isNotEmpty()

        /** Badge count: applied criteria plus the exact-name flag when it is in effect. */
        val activeCriteriaCount: Int
            get() = advancedQuery.criteria.size + if (nameExact && hasEffectiveName) 1 else 0

        /** The typed text is too short to be sent as a name filter. */
        val showMinLengthHint: Boolean
            get() = searchText.isNotBlank() && !hasEffectiveName

        val hasAnySearch: Boolean
            get() = searchText.isNotBlank() || advancedQuery.criteria.isNotEmpty()

        /** What the Advanced Search sheet is seeded with: the criteria plus the search-bar name. */
        val sheetQuery: AdvancedSearchQuery
            get() {
                val name = searchText.trim()
                if (name.isEmpty()) return advancedQuery
                return advancedQuery.copy(criteria = listOf(SearchCriterion.Name(name, nameExact)) + advancedQuery.criteria)
            }
    }

    /** One-shot events emitted to the UI layer. */
    sealed interface UiEvent {
        object NavigateBack : UiEvent
    }

    private data class SearchInput(
        val list: FolderSubTab = FolderSubTab.COLLECTION,
        val text: String = "",
        val nameExact: Boolean = false,
        val query: AdvancedSearchQuery = AdvancedSearchQuery(),
        val delayMs: Long = 0L,
    )

    private data class SearchRequest(val list: FolderSubTab, val params: FriendCardSearchParams)

    private data class CachedResult(
        val cards: List<FriendCard>,
        val cursor: FriendCardCursor?,
        val hasMore: Boolean,
        val unindexedCount: Int,
        val loadedAt: ComparableTimeMark,
    )

    private val friendUserId: String = savedStateHandle["userId"] ?: ""

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events: Flow<UiEvent> = _events.receiveAsFlow()

    // The list lives in the same input as the text so a list switch during a pending typing debounce fires once.
    private val searchInput = MutableStateFlow(SearchInput())
    private val reloadToken = MutableStateFlow(0)

    // Main-thread confined: every mutation happens on viewModelScope's Main dispatcher.
    private var generation = 0
    private var currentRequest: SearchRequest? = null
    private var currentCursor: FriendCardCursor? = null
    private var loadMoreJob: Job? = null
    private val resultCache = LinkedHashMap<SearchRequest, CachedResult>()
    private val attemptedHydrationIds = mutableSetOf<String>()

    init {
        if (friendUserId.isBlank()) {
            viewModelScope.launch { _events.send(UiEvent.NavigateBack) }
        } else {
            setup()
        }
    }

    private fun setup() {
        friendRepo.observeFriends()
            .onEach { friends ->
                val found = friends.firstOrNull { it.userId == friendUserId }
                // Removed externally by the peer: leave instead of showing a blank screen.
                val hadFriend = !_uiState.value.isLoadingFriend && _uiState.value.friend != null
                if (found == null && hadFriend) {
                    _events.send(UiEvent.NavigateBack)
                }
                _uiState.update { it.copy(friend = found, isLoadingFriend = false) }
            }
            .launchIn(viewModelScope)

        viewModelScope.launch {
            val myUserId = authRepo.sessionState
                .mapNotNull { (it as? SessionState.Authenticated)?.user?.id }
                .first()
            tradesRepo.refreshProposals(myUserId)
        }

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

        observeCardRequests()
    }

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private fun observeCardRequests() {
        val requests = searchInput
            .debounce { it.delayMs }
            .map { SearchRequest(it.list, FriendCardSearchMapper.toParams(it.text, it.nameExact, it.query)) }
        combine(requests, reloadToken) { request, token -> request to token }
            // Whitespace-only edits and 1-char text normalize to the same params and never reach the server.
            .distinctUntilChanged()
            .flatMapLatest { (request, _) -> flow<Unit> { loadFirstPage(request) } }
            .launchIn(viewModelScope)
    }

    private suspend fun loadFirstPage(request: SearchRequest) {
        val gen = ++generation
        loadMoreJob?.cancel()
        loadMoreJob = null
        val listChanged = currentRequest?.list != request.list
        currentRequest = request
        currentCursor = null
        val filtered = !request.params.isEmpty()

        freshCacheEntry(request)?.let { hit ->
            currentCursor = hit.cursor
            _uiState.update {
                it.copy(
                    cards = hit.cards,
                    hasMoreCards = hit.hasMore,
                    isLoadingCards = false,
                    isLoadingMore = false,
                    loadMoreFailed = false,
                    cardsError = null,
                    resultsFiltered = filtered,
                    resultsVersion = it.resultsVersion + 1,
                    unindexedCount = hit.unindexedCount,
                )
            }
            return
        }

        // A new query keeps the old results on screen (dimmed); a new list must not show another list's cards.
        _uiState.update {
            it.copy(
                cards = if (listChanged) emptyList() else it.cards,
                isLoadingCards = true,
                isLoadingMore = false,
                hasMoreCards = false,
                loadMoreFailed = false,
                cardsError = null,
            )
        }
        reportSubmit(request)
        val result = searchFriendCards(friendUserId, request.list.listValue, request.params, null, PAGE_SIZE)
        if (gen != generation) return

        result.fold(
            onSuccess = { page ->
                // The row-level count only exists when rows came back; an empty filtered page asks for it.
                val unindexed = if (filtered && page.cards.isEmpty()) fetchUnindexedCount(request) else page.unindexedCount
                if (gen != generation) return
                currentCursor = page.nextCursor
                putCache(request, CachedResult(page.cards, page.nextCursor, page.hasMore, unindexed, timeSource.markNow()))
                crashReporter.log(if (page.cards.isEmpty()) "friend_cards_search_result_empty" else "friend_cards_search_result")
                _uiState.update {
                    it.copy(
                        cards = page.cards,
                        hasMoreCards = page.hasMore,
                        isLoadingCards = false,
                        cardsError = null,
                        resultsFiltered = filtered,
                        resultsVersion = it.resultsVersion + 1,
                        unindexedCount = unindexed,
                    )
                }
                hydrateInBackground(page.unresolvedIds)
            },
            onFailure = { e ->
                val error = reportFailure(e, "friend_cards_search_error")
                _uiState.update {
                    it.copy(cards = emptyList(), hasMoreCards = false, isLoadingCards = false, cardsError = error)
                }
            },
        )
    }

    /** Fetches the next keyset page; no-op unless a further page exists and nothing is in flight. */
    fun loadMoreCards() {
        val state = _uiState.value
        if (state.isLoadingCards || state.isLoadingMore || !state.hasMoreCards || state.loadMoreFailed) return
        if (loadMoreJob?.isActive == true) return
        val request = currentRequest ?: return
        val cursor = currentCursor ?: return
        val gen = generation

        loadMoreJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            crashReporter.log("friend_cards_search_load_more")
            val result = searchFriendCards(friendUserId, request.list.listValue, request.params, cursor, PAGE_SIZE)
            // A newer request superseded this page; its rows belong to a list/query no longer shown.
            if (gen != generation) return@launch
            result.fold(
                onSuccess = { page ->
                    currentCursor = page.nextCursor
                    val merged = (_uiState.value.cards + page.cards).dedupedByRow()
                    resultCache[request]?.let { entry ->
                        resultCache[request] = entry.copy(
                            cards = (entry.cards + page.cards).dedupedByRow(),
                            cursor = page.nextCursor,
                            hasMore = page.hasMore,
                        )
                    }
                    _uiState.update {
                        it.copy(cards = merged, hasMoreCards = page.hasMore, isLoadingMore = false)
                    }
                    hydrateInBackground(page.unresolvedIds)
                },
                onFailure = { e ->
                    val error = reportFailure(e, "friend_cards_search_load_more_error")
                    _uiState.update {
                        if (error.isTerminal()) {
                            it.copy(cards = emptyList(), hasMoreCards = false, isLoadingMore = false, cardsError = error)
                        } else {
                            it.copy(isLoadingMore = false, loadMoreFailed = true)
                        }
                    }
                },
            )
        }
    }

    /** Retries a failed next-page fetch (never retried automatically, to avoid a request loop). */
    fun retryLoadMore() {
        _uiState.update { it.copy(loadMoreFailed = false) }
        loadMoreCards()
    }

    /** Re-runs the current request, bypassing the result cache. */
    fun retryCards() {
        currentRequest?.let(resultCache::remove)
        reloadToken.value++
    }

    /** Free-text edit; debounced unless the field was cleared. */
    fun onSearchQueryChange(text: String) {
        val delay = if (text.isBlank()) CHANGE_DEBOUNCE_MS else TYPING_DEBOUNCE_MS
        updateSearch(delay) { it.copy(text = text, nameExact = it.nameExact && text.isNotBlank()) }
    }

    /** IME Search action: submits the current text with no debounce at all. */
    fun onSearchSubmit() {
        updateSearch(0L) { it }
    }

    fun clearSearchText() {
        updateSearch(CHANGE_DEBOUNCE_MS) { it.copy(text = "", nameExact = false) }
    }

    /** Applies the sheet's result: its Name moves into the search bar, unsupported criteria are dropped. */
    fun applyAdvancedSearch(query: AdvancedSearchQuery) {
        val supported = FriendCardSearchMapper.supportedOnly(query)
        val name = supported.criteria.filterIsInstance<SearchCriterion.Name>().firstOrNull()
        val rest = supported.copy(criteria = supported.criteria.filterNot { it is SearchCriterion.Name })
        crashReporter.log("friend_cards_advanced_search_apply")
        updateSearch(CHANGE_DEBOUNCE_MS) {
            it.copy(text = name?.value.orEmpty(), nameExact = name?.exact ?: false, query = rest)
        }
    }

    fun removeCriterion(criterion: SearchCriterion) {
        updateSearch(CHANGE_DEBOUNCE_MS) { it.copy(query = it.query.copy(criteria = it.query.criteria - criterion)) }
    }

    fun clearNameExact() {
        updateSearch(CHANGE_DEBOUNCE_MS) { it.copy(nameExact = false) }
    }

    /** Clears the text, the exact flag and every criterion. */
    fun clearSearch() {
        updateSearch(CHANGE_DEBOUNCE_MS) { SearchInput(list = it.list) }
    }

    private fun updateSearch(delayMs: Long, transform: (SearchInput) -> SearchInput) {
        val next = transform(searchInput.value).copy(delayMs = delayMs)
        _uiState.update {
            it.copy(folderSubTab = next.list, searchText = next.text, nameExact = next.nameExact, advancedQuery = next.query)
        }
        searchInput.value = next
    }

    /**
     * Switches the top-level tab. The search is kept across tabs so returning to Folder restores
     * what the user typed; re-entry re-runs the request, which the result cache serves while fresh.
     */
    fun selectTab(tab: FriendTab) {
        val previous = _uiState.value.selectedTab
        _uiState.update { it.copy(selectedTab = tab) }
        if (tab == FriendTab.FOLDER && previous != FriendTab.FOLDER) reloadToken.value++
        if (tab == FriendTab.STATS) {
            val current = _uiState.value
            if (current.friendStats == null && !current.isLoadingStats && !current.statsError) {
                loadStats()
            }
        }
    }

    fun retryStats() {
        loadStats()
    }

    fun retryGameHistory() {
        loadGameHistory()
    }

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

    /** Switches the list; the current search is kept and re-run against the new list. */
    fun selectFolderSubTab(subTab: FolderSubTab) {
        updateSearch(CHANGE_DEBOUNCE_MS) { it.copy(list = subTab) }
    }

    /**
     * Removes the current friend; on success navigates back, on failure shows [errorMsg].
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

    fun clearToast() {
        _uiState.update { it.copy(toastMessage = null, toastType = MagicToastType.ERROR) }
    }

    private suspend fun fetchUnindexedCount(request: SearchRequest): Int =
        searchFriendCards.unindexedCount(friendUserId, request.list.listValue).getOrElse {
            crashReporter.log("friend_cards_unindexed_count_error")
            0
        }

    // At most one warm per page, and an id is never re-warmed in this session (no Scryfall storm).
    private fun hydrateInBackground(ids: Set<String>) {
        val toWarm = ids - attemptedHydrationIds
        if (toWarm.isEmpty()) return
        attemptedHydrationIds += toWarm
        viewModelScope.launch {
            val resolved = searchFriendCards.hydrateMetadata(toWarm.toList())
            if (resolved.isEmpty()) return@launch
            val patch: (FriendCard) -> FriendCard = { card -> resolved[card.scryfallId]?.let(card::withMetadata) ?: card }
            _uiState.update { it.copy(cards = it.cards.map(patch)) }
            resultCache.replaceAll { _, entry -> entry.copy(cards = entry.cards.map(patch)) }
        }
    }

    // Hydration mid-pagination can move a row between sort groups; drop any row already shown.
    private fun List<FriendCard>.dedupedByRow(): List<FriendCard> = distinctBy { it.rowId ?: it }

    private fun FolderCardsError.isTerminal(): Boolean =
        this == FolderCardsError.SESSION_EXPIRED || this == FolderCardsError.PROFILE_INCOMPLETE

    private fun freshCacheEntry(request: SearchRequest): CachedResult? {
        val entry = resultCache[request] ?: return null
        if (entry.loadedAt.elapsedNow() > CACHE_TTL) {
            resultCache.remove(request)
            return null
        }
        return entry
    }

    private fun putCache(request: SearchRequest, result: CachedResult) {
        resultCache.remove(request)
        resultCache[request] = result
        while (resultCache.size > CACHE_MAX_ENTRIES) resultCache.remove(resultCache.keys.first())
    }

    // Query length and counts only: the raw text and the friend id never leave the device.
    private fun reportSubmit(request: SearchRequest) {
        crashReporter.setCustomKey(KEY_LIST, request.list.listValue)
        crashReporter.setCustomKey(KEY_CRITERIA_COUNT, request.params.activeFilterCount().toString())
        crashReporter.setCustomKey(KEY_QUERY_LENGTH, (request.params.name?.length ?: 0).toString())
        crashReporter.log("friend_cards_search_submit")
    }

    private fun reportFailure(e: Throwable, event: String): FolderCardsError {
        val error = when (e) {
            is FriendCardSearchException.AccessDenied -> FolderCardsError.ACCESS_DENIED
            is FriendCardSearchException.Network -> FolderCardsError.NETWORK
            is FriendCardSearchException.SessionExpired -> FolderCardsError.SESSION_EXPIRED
            is FriendCardSearchException.ProfileIncomplete -> FolderCardsError.PROFILE_INCOMPLETE
            else -> FolderCardsError.GENERIC
        }
        crashReporter.log("${event}_${error.name.lowercase()}")
        val expected = e is FriendCardSearchException.AccessDenied ||
            e is FriendCardSearchException.Network ||
            e is FriendCardSearchException.SessionExpired ||
            e is FriendCardSearchException.ProfileIncomplete
        if (!expected) {
            // Typed messages are server tokens; anything else is reduced to its class name.
            val reason = if (e is FriendCardSearchException) e.message else e::class.simpleName
            crashReporter.recordException(RuntimeException("[friend_cards_search] $reason"))
        }
        return error
    }

    private companion object {
        const val PAGE_SIZE = 50
        const val TYPING_DEBOUNCE_MS = 450L
        // Coalesces bursts of chip removals / list switches into one request.
        const val CHANGE_DEBOUNCE_MS = 150L
        val CACHE_TTL = 60.seconds
        const val CACHE_MAX_ENTRIES = 8
        const val KEY_LIST = "friend_search_list"
        const val KEY_CRITERIA_COUNT = "friend_search_criteria_count"
        const val KEY_QUERY_LENGTH = "friend_search_query_length"
    }
}
