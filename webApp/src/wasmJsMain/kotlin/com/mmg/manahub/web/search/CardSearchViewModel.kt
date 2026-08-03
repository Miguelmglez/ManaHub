package com.mmg.manahub.web.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.domain.repository.UserCardRepository
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.DataResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * UI state for [CardSearchScreen] (web roadmap W3b, extended W3d) — covers all four states
 * CLAUDE.md requires every screen to handle: idle (empty [query], no [cards]), loading
 * ([isLoading]), error ([error]), and content ([cards]).
 */
data class CardSearchUiState(
    val query: String = "",
    val cards: List<Card> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val page: Int = 1,
    val error: String? = null,
    /** True once at least one search has completed (successfully or not) for the current [query] — distinguishes the initial idle state from a genuine "no results" empty state. */
    val hasSearched: Boolean = false,
    /**
     * Web roadmap W3d. Non-null right after a tap-to-add attempt (success or failure) resolves;
     * a transient inline confirmation/error banner, not a persisted state. Cleared on the next
     * [addToCollection] call or [onQueryChange].
     */
    val addToCollectionMessage: String? = null,
)

/**
 * Backs [CardSearchScreen] — the first REAL web MVP screen (web roadmap W3b), not a showcase.
 * Exercises [CardRepository.searchCardsPaginated] (one of the 13 real methods on
 * [com.mmg.manahub.core.data.repository.WebCardRepository]) end-to-end against live Scryfall data.
 *
 * Deliberately does NOT call any of [CardRepository]'s 14 Room-cache-only stub methods
 * (price/tag mutation, backfills, stale-cache eviction) — those throw
 * [UnsupportedOperationException] on web and have no place in a search screen anyway.
 *
 * Web roadmap W3d: also exercises [UserCardRepository.addOrIncrement] via [addToCollection] --
 * a search result is genuinely add-able now, proving `WebUserCardRepository`'s write path end to
 * end (not just [com.mmg.manahub.web.collection.CollectionScreen]'s read path).
 */
class CardSearchViewModel(
    private val cardRepository: CardRepository,
    private val userCardRepository: UserCardRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CardSearchUiState())
    val uiState: StateFlow<CardSearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    /** Updates the draft query as the user types — does NOT trigger a search (see [search]). */
    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query, addToCollectionMessage = null) }
    }

    /**
     * Adds one copy of [card] to the signed-in session's collection (default attributes: not
     * foil, Near Mint, English -- this MVP tile has no attribute picker yet, see
     * [com.mmg.manahub.core.ui.components.AddToCollectionSheet] for the full Android flow this
     * will eventually mirror). Proves [UserCardRepository.addOrIncrement] end-to-end: a failure
     * (e.g. no signed-in session yet) surfaces as [CardSearchUiState.addToCollectionMessage]
     * rather than being swallowed.
     */
    fun addToCollection(card: Card) {
        viewModelScope.launch {
            _uiState.update { it.copy(addToCollectionMessage = null) }
            try {
                userCardRepository.addOrIncrement(
                    scryfallId = card.scryfallId,
                    isFoil = false,
                    condition = "NM",
                    language = "en",
                    isForTrade = false,
                    userId = null,
                    quantity = 1,
                )
                _uiState.update { it.copy(addToCollectionMessage = "Added ${card.name} to your collection.") }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(addToCollectionMessage = "Couldn't add ${card.name}: ${e.message ?: "unknown error"}")
                }
            }
        }
    }

    /** Runs a fresh page-1 search for the current [CardSearchUiState.query]. Cancels any in-flight search. */
    fun search() {
        val query = _uiState.value.query.trim()
        searchJob?.cancel()
        if (query.isBlank()) {
            _uiState.update {
                it.copy(cards = emptyList(), error = null, hasMore = false, page = 1, hasSearched = false, isLoading = false)
            }
            return
        }
        searchJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val result = cardRepository.searchCardsPaginated(query, page = 1)) {
                is DataResult.Success -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        cards = result.data.cards,
                        hasMore = result.data.hasMore,
                        page = 1,
                        error = null,
                        hasSearched = true,
                    )
                }
                is DataResult.Error -> _uiState.update {
                    it.copy(isLoading = false, error = result.message, hasSearched = true)
                }
            }
        }
    }

    /** Appends the next results page for the current query. No-op if already loading or exhausted. */
    fun loadMore() {
        val state = _uiState.value
        if (state.isLoadingMore || state.isLoading || !state.hasMore || state.query.isBlank()) return
        val nextPage = state.page + 1
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            when (val result = cardRepository.searchCardsPaginated(state.query.trim(), page = nextPage)) {
                is DataResult.Success -> _uiState.update {
                    it.copy(
                        isLoadingMore = false,
                        cards = it.cards + result.data.cards,
                        hasMore = result.data.hasMore,
                        page = nextPage,
                    )
                }
                is DataResult.Error -> _uiState.update { it.copy(isLoadingMore = false, error = result.message) }
            }
        }
    }
}
