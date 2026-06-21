package com.mmg.manahub.feature.communitydecks.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.feature.communitydecks.domain.usecase.SearchCommunityDecksUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for the Community Decks search / browse screen.
 *
 * Drives a paged Archidekt search ([SearchCommunityDecksUseCase]) with format /
 * sort filters. Both the landing ([com.mmg.manahub.app.navigation.Screen.CommunityDecks])
 * and the "decks containing a card" deep-link
 * ([com.mmg.manahub.app.navigation.Screen.CommunityDecksByCard]) routes share this
 * ViewModel; the latter pre-fills [initialCardName] from `SavedStateHandle` and
 * auto-triggers a search.
 *
 * One-shot navigation / error effects are delivered through a buffered [Channel].
 */
class CommunityDecksSearchViewModel(
    savedStateHandle: SavedStateHandle,
    private val searchCommunityDecks: SearchCommunityDecksUseCase,
    private val userPreferences: UserPreferencesDataStore,
) : ViewModel() {

    private val crashlytics = FirebaseCrashlytics.getInstance()

    /** Pre-filled card name from the CommunityDecksByCard route (Uri-decoded by the nav arg). */
    private val initialCardName: String? = savedStateHandle.get<String>("cardName")

    private val _uiState = MutableStateFlow(
        CommunityDecksSearchUiState(query = initialCardName ?: ""),
    )
    val uiState: StateFlow<CommunityDecksSearchUiState> = _uiState.asStateFlow()

    private val _events = Channel<CommunityDecksSearchEvent>(Channel.BUFFERED)
    val events: Flow<CommunityDecksSearchEvent> = _events.receiveAsFlow()

    /** Feature flag — the screen renders a disabled state when this is false. */
    val isFeatureEnabled: StateFlow<Boolean> = userPreferences.communityDecksEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private var currentPage = 1
    private var searchJob: Job? = null

    init {
        crashlytics.log("screen_viewed: community_decks_search")
        crashlytics.setCustomKey("community_search_prefilled", !initialCardName.isNullOrBlank())
        if (!initialCardName.isNullOrBlank()) {
            search()
        }
    }

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }
    }

    /** Re-runs the search if one was already issued (so filters apply live). */
    fun onFormatSelected(format: CommunityDeckFormatFilter) {
        _uiState.update { it.copy(selectedFormat = format) }
        if (_uiState.value.hasSearched) {
            search()
        }
    }

    /** Re-runs the search if one was already issued (so the new sort applies live). */
    fun onSortSelected(sort: CommunityDeckSort) {
        _uiState.update { it.copy(selectedSort = sort) }
        if (_uiState.value.hasSearched) {
            search()
        }
    }

    /** Runs a fresh search (page 1). No-ops on a blank query. */
    fun search() {
        val query = _uiState.value.query.trim()
        if (query.isBlank()) return

        searchJob?.cancel()
        currentPage = 1

        searchJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, hasSearched = true) }

            val state = _uiState.value
            crashlytics.setCustomKey("community_search_format", state.selectedFormat.name)
            crashlytics.setCustomKey("community_search_sort", state.selectedSort.name)
            crashlytics.setCustomKey("community_search_query_len", query.length)

            when (
                val result = searchCommunityDecks(
                    cardName = query,
                    deckFormat = state.selectedFormat.apiId,
                    orderBy = state.selectedSort.apiValue,
                    page = 1,
                )
            ) {
                is DataResult.Success -> {
                    crashlytics.setCustomKey("community_search_result_count", result.data.totalCount)
                    crashlytics.log("community_search_success")
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            results = result.data.decks,
                            totalCount = result.data.totalCount,
                            hasMore = result.data.hasMore,
                            error = null,
                        )
                    }
                }
                is DataResult.Error -> {
                    crashlytics.log("community_search_error: ${result.message.take(80)}")
                    _uiState.update {
                        it.copy(isLoading = false, error = result.message)
                    }
                }
            }
        }
    }

    /** Appends the next page of results. No-ops when already loading more or no more pages. */
    fun loadMore() {
        if (_uiState.value.isLoadingMore || !_uiState.value.hasMore) return

        val nextPage = currentPage + 1

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }

            val state = _uiState.value
            when (
                val result = searchCommunityDecks(
                    cardName = state.query.trim(),
                    deckFormat = state.selectedFormat.apiId,
                    orderBy = state.selectedSort.apiValue,
                    page = nextPage,
                )
            ) {
                is DataResult.Success -> {
                    currentPage = nextPage
                    crashlytics.log("community_search_load_more")
                    _uiState.update {
                        it.copy(
                            isLoadingMore = false,
                            results = it.results + result.data.decks,
                            hasMore = result.data.hasMore,
                        )
                    }
                }
                is DataResult.Error -> {
                    crashlytics.log(
                        "community_search_load_more_error: page=$nextPage, " +
                            "accumulated=${_uiState.value.results.size}",
                    )
                    crashlytics.setCustomKey("community_search_failed_page", nextPage)
                    _events.send(CommunityDecksSearchEvent.ShowError(result.message))
                    _uiState.update { it.copy(isLoadingMore = false) }
                }
            }
        }
    }

    fun onDeckClick(archidektId: Int) {
        viewModelScope.launch {
            _events.send(CommunityDecksSearchEvent.NavigateToDeck(archidektId))
        }
    }
}

/** One-shot effects for the search screen. */
sealed interface CommunityDecksSearchEvent {
    data class NavigateToDeck(val archidektId: Int) : CommunityDecksSearchEvent
    data class ShowError(val message: String) : CommunityDecksSearchEvent
}
