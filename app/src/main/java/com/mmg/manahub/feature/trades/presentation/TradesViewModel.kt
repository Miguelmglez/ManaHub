package com.mmg.manahub.feature.trades.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.manahub.core.di.IoDispatcher
import com.mmg.manahub.feature.friends.domain.model.Friend
import com.mmg.manahub.feature.friends.domain.usecase.GetFriendsUseCase
import com.mmg.manahub.feature.trades.domain.model.OpenForTradeEntry
import com.mmg.manahub.feature.trades.domain.model.TradeSuggestion
import com.mmg.manahub.feature.trades.domain.model.WishlistEntry
import com.mmg.manahub.feature.trades.domain.repository.TradeSuggestionsRepository
import com.mmg.manahub.feature.trades.domain.usecase.AddToOpenForTradeUseCase
import com.mmg.manahub.feature.trades.domain.usecase.AddToWishlistUseCase
import com.mmg.manahub.feature.trades.domain.usecase.GetLocalOpenForTradeUseCase
import com.mmg.manahub.feature.trades.domain.usecase.GetLocalWishlistUseCase
import com.mmg.manahub.feature.trades.domain.usecase.GetSuggestedTradesUseCase
import com.mmg.manahub.feature.trades.domain.usecase.RemoveFromOpenForTradeUseCase
import com.mmg.manahub.feature.trades.domain.usecase.RemoveFromWishlistUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

// ─────────────────────────────────────────────────────────────────────────────
//  UI state
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Two top-level tabs inside the Trades feature.
 *
 * - [EXPLORATION]: browse and manage your wishlist / open-for-trade lists,
 *   and optionally view your friends' lists.
 * - [HISTORY]: past completed trades (Phase 3).
 */
enum class TradesMainTab { EXPLORATION, HISTORY }

/**
 * Toggle inside the Exploration tab to switch between the user's own lists
 * and their friends' trade lists.
 */
enum class ExplorationToggle { MY_LISTS, FRIENDS }

/**
 * Immutable UI state for [TradesViewModel].
 */
data class TradesUiState(
    val wishlist:           List<WishlistEntry>        = emptyList(),
    val openForTrade:       List<OpenForTradeEntry>    = emptyList(),
    val suggestions:        List<TradeSuggestion>      = emptyList(),
    val friends:            List<Friend>               = emptyList(),
    val selectedTab:        TradesMainTab              = TradesMainTab.EXPLORATION,
    val explorationToggle:  ExplorationToggle          = ExplorationToggle.MY_LISTS,
    val selectedWishlistIds: Set<String>               = emptySet(),
    val selectedOfferIds:   Set<String>                = emptySet(),
    val isMultiSelectMode:  Boolean                    = false,
    val isLoadingSuggestions: Boolean                  = false,
    val snackbarMessage:    String?                    = null,
)

// ─────────────────────────────────────────────────────────────────────────────
//  ViewModel
// ─────────────────────────────────────────────────────────────────────────────

/**
 * ViewModel for the Trades feature.
 *
 * Collects the local wishlist and open-for-trade flows from Room, exposes
 * friends for the "Friends" toggle, and provides actions for adding, removing,
 * and selecting items.
 *
 * Phase 3 will add proposal creation and full trade history.
 */
@HiltViewModel
class TradesViewModel @Inject constructor(
    private val getLocalWishlist: GetLocalWishlistUseCase,
    private val getLocalOpenForTrade: GetLocalOpenForTradeUseCase,
    private val addToWishlist: AddToWishlistUseCase,
    private val removeFromWishlist: RemoveFromWishlistUseCase,
    private val addToOpenForTrade: AddToOpenForTradeUseCase,
    private val removeFromOpenForTrade: RemoveFromOpenForTradeUseCase,
    private val getSuggestions: GetSuggestedTradesUseCase,
    private val suggestionsRepository: TradeSuggestionsRepository,
    private val getFriends: GetFriendsUseCase,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TradesUiState())
    val uiState: StateFlow<TradesUiState> = _uiState.asStateFlow()

    init {
        observeWishlist()
        observeOpenForTrade()
        observeFriends()
    }

    // ── Observation ───────────────────────────────────────────────────────────

    private fun observeWishlist() {
        viewModelScope.launch {
            getLocalWishlist()
                .catch { e -> _uiState.update { it.copy(snackbarMessage = e.message) } }
                .collect { entries -> _uiState.update { it.copy(wishlist = entries) } }
        }
    }

    private fun observeOpenForTrade() {
        viewModelScope.launch {
            getLocalOpenForTrade()
                .catch { e -> _uiState.update { it.copy(snackbarMessage = e.message) } }
                .collect { entries -> _uiState.update { it.copy(openForTrade = entries) } }
        }
    }

    private fun observeFriends() {
        viewModelScope.launch {
            getFriends()
                .catch { /* friends are optional; silently ignore */ }
                .collect { friends -> _uiState.update { it.copy(friends = friends) } }
        }
    }

    // ── Tab / toggle selection ────────────────────────────────────────────────

    /** Switches between [TradesMainTab.EXPLORATION] and [TradesMainTab.HISTORY]. */
    fun onTabSelected(tab: TradesMainTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    /** Switches the Exploration tab between "My Lists" and "Friends". */
    fun onExplorationToggle(toggle: ExplorationToggle) {
        _uiState.update { it.copy(explorationToggle = toggle) }
    }

    // ── Wishlist actions ──────────────────────────────────────────────────────

    /**
     * Adds a new entry to the local wishlist.
     *
     * @param cardId         Scryfall card ID.
     * @param matchAnyVariant If true, any printing of the card satisfies the wish.
     * @param isFoil         Optional foil preference (only when [matchAnyVariant] is false).
     * @param condition      Optional condition filter string.
     * @param language       Optional language code.
     * @param isAltArt       Optional alt-art preference.
     */
    fun onAddToWishlist(
        cardId: String,
        matchAnyVariant: Boolean,
        isFoil: Boolean?,
        condition: String?,
        language: String?,
        isAltArt: Boolean?,
    ) {
        viewModelScope.launch(ioDispatcher) {
            val entry = WishlistEntry(
                id              = UUID.randomUUID().toString(),
                userId          = "",          // local entry; userId assigned on cloud migration
                cardId          = cardId,
                matchAnyVariant = matchAnyVariant,
                isFoil          = isFoil,
                condition       = condition,
                language        = language,
                isAltArt        = isAltArt,
                createdAt       = System.currentTimeMillis(),
            )
            addToWishlist(entry)
                .onFailure { e -> _uiState.update { it.copy(snackbarMessage = e.message) } }
        }
    }

    /** Removes an entry from the local wishlist by its ID. */
    fun onRemoveFromWishlist(id: String) {
        viewModelScope.launch(ioDispatcher) {
            removeFromWishlist(id)
                .onFailure { e -> _uiState.update { it.copy(snackbarMessage = e.message) } }
        }
    }

    // ── Open-for-trade actions ────────────────────────────────────────────────

    /**
     * Marks a collection copy as available for trade.
     *
     * @param scryfallId        Scryfall card ID.
     * @param localCollectionId The user_cards row ID to link this offer to.
     */
    fun onAddToOpenForTrade(scryfallId: String, localCollectionId: String) {
        viewModelScope.launch(ioDispatcher) {
            addToOpenForTrade(scryfallId, localCollectionId)
                .onFailure { e -> _uiState.update { it.copy(snackbarMessage = e.message) } }
        }
    }

    /** Removes an open-for-trade entry by its ID. */
    fun onRemoveFromOpenForTrade(id: String) {
        viewModelScope.launch(ioDispatcher) {
            removeFromOpenForTrade(id)
                .onFailure { e -> _uiState.update { it.copy(snackbarMessage = e.message) } }
        }
    }

    // ── Multi-select ──────────────────────────────────────────────────────────

    /** Toggles a wishlist entry in/out of the selection set. */
    fun onToggleWishlistSelect(id: String) {
        _uiState.update { state ->
            val updated = state.selectedWishlistIds.toMutableSet().also {
                if (!it.add(id)) it.remove(id)
            }
            state.copy(
                selectedWishlistIds = updated,
                isMultiSelectMode = updated.isNotEmpty() || state.selectedOfferIds.isNotEmpty(),
            )
        }
    }

    /** Toggles an offer entry in/out of the selection set. */
    fun onToggleOfferSelect(id: String) {
        _uiState.update { state ->
            val updated = state.selectedOfferIds.toMutableSet().also {
                if (!it.add(id)) it.remove(id)
            }
            state.copy(
                selectedOfferIds = updated,
                isMultiSelectMode = state.selectedWishlistIds.isNotEmpty() || updated.isNotEmpty(),
            )
        }
    }

    /** Clears all multi-select state. */
    fun onClearSelection() {
        _uiState.update {
            it.copy(
                selectedWishlistIds = emptySet(),
                selectedOfferIds    = emptySet(),
                isMultiSelectMode   = false,
            )
        }
    }

    // ── Refresh ───────────────────────────────────────────────────────────────

    /**
     * Refreshes trade suggestions from the remote materialized view.
     * Emits a snackbar message on error.
     */
    fun refresh() {
        viewModelScope.launch(ioDispatcher) {
            _uiState.update { it.copy(isLoadingSuggestions = true) }
            suggestionsRepository.refreshSuggestions()
                .onFailure { e -> _uiState.update { it.copy(snackbarMessage = e.message) } }
            getSuggestions()
                .onSuccess { list -> _uiState.update { it.copy(suggestions = list) } }
                .onFailure { e -> _uiState.update { it.copy(snackbarMessage = e.message) } }
            _uiState.update { it.copy(isLoadingSuggestions = false) }
        }
    }

    // ── Snackbar ──────────────────────────────────────────────────────────────

    /** Clears the one-shot snackbar message after it has been shown. */
    fun onSnackbarDismissed() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }
}
