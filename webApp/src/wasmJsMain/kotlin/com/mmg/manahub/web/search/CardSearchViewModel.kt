package com.mmg.manahub.web.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.domain.repository.UserCardRepository
import com.mmg.manahub.core.domain.usecase.card.GetSpotlightFeedUseCase
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.web.common.toUserFacingMessage
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
    val totalCards: Int = 0,
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
    /**
     * Web scope expansion (Add Card, 2026-08-04). The idle-state discovery grid — a shuffled page
     * of real, addable cards from one Scryfall set at a time, mirroring Android's `AddCardScreen`
     * spotlight feed. Only rendered by `CardSearchScreen` while [query] is blank; accumulates
     * across [CardSearchViewModel.loadSpotlightFeed] calls (each call appends the next set's
     * cards, never replaces).
     */
    val spotlightCards: List<Card> = emptyList(),
    /** True while a [CardSearchViewModel.loadSpotlightFeed] fetch is in flight. */
    val isSpotlightLoading: Boolean = false,
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
 *
 * Web scope expansion (Add Card, 2026-08-04): this screen already covers Android's `AddCardScreen`
 * search half — what's added here is the idle-state discovery grid, via [GetSpotlightFeedUseCase]
 * (already `commonMain`, zero new domain work). This ViewModel has no filter state distinct from
 * [CardSearchUiState.query] (unlike Android's `AddCardViewModel`, which also gates idle-ness on an
 * advanced-search filter), so "idle" here is exactly `query.isBlank()`. [loadSpotlightFeed] is
 * called once from [init] and again from [CardSearchScreen]'s grid-footer trigger (same
 * `LaunchedEffect`-on-compose pattern Android's `SpotlightGrid` footer uses) to page through sets
 * as the user scrolls.
 */
class CardSearchViewModel(
    private val cardRepository: CardRepository,
    private val userCardRepository: UserCardRepository,
    private val getSpotlightFeed: GetSpotlightFeedUseCase,
    private val crashReporter: CrashReporter,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CardSearchUiState())
    val uiState: StateFlow<CardSearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null
    private var currentSpotlightSetIndex = 0
    private var hasMoreSpotlightSets = true

    init {
        loadSpotlightFeed()
    }

    /**
     * Updates the draft query as the user types — does NOT trigger a search (see [search]).
     * Clearing the query back to blank also resets any stale search results/state right away
     * (same reset [search] performs on a blank query) so [CardSearchScreen] falls straight back
     * to the spotlight grid without requiring the user to re-submit an empty search.
     */
    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query, addToCollectionMessage = null) }
        if (query.isBlank()) {
            _uiState.update {
                it.copy(cards = emptyList(), error = null, hasMore = false, page = 1, hasSearched = false, isLoading = false)
            }
        }
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
            } catch (e: Throwable) {
                _uiState.update {
                    it.copy(addToCollectionMessage = e.toUserFacingMessage("add ${card.name}", crashReporter))
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

    /**
     * Loads the next page of the idle-state discovery/spotlight feed — a shuffled page of cards
     * from ONE Scryfall set, then advances to the next set on the following call (see
     * [GetSpotlightFeedUseCase]'s own KDoc for the pagination shape: `setIndex` in, `nextSetIndex`
     * out). No-op while already loading or once every playable set has been exhausted. Mirrors
     * Android's `AddCardViewModel.loadSpotlightFeed` exactly, minus the filter-state guard (this
     * ViewModel has none — see the class KDoc).
     */
    fun loadSpotlightFeed() {
        if (_uiState.value.isSpotlightLoading || !hasMoreSpotlightSets) return
        _uiState.update { it.copy(isSpotlightLoading = true) }
        viewModelScope.launch {
            when (val result = getSpotlightFeed(currentSpotlightSetIndex)) {
                is DataResult.Success -> {
                    _uiState.update {
                        it.copy(
                            spotlightCards = it.spotlightCards + result.data.cards,
                            isSpotlightLoading = false,
                        )
                    }
                    currentSpotlightSetIndex = result.data.nextSetIndex
                }
                is DataResult.Error -> {
                    _uiState.update { it.copy(isSpotlightLoading = false) }
                    if (result.message == "No more sets available") {
                        hasMoreSpotlightSets = false
                    }
                }
            }
        }
    }
}
