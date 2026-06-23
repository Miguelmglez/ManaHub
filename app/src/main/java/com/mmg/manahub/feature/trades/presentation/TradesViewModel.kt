package com.mmg.manahub.feature.trades.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.manahub.core.domain.auth.SessionState
import com.mmg.manahub.core.domain.auth.AuthRepository
import com.mmg.manahub.feature.friends.domain.model.Friend
import com.mmg.manahub.feature.friends.domain.usecase.GetFriendsUseCase
import com.mmg.manahub.core.model.OpenForTradeEntry
import com.mmg.manahub.core.model.WishlistEntry
import com.mmg.manahub.core.model.toUserFacingMessage
import com.mmg.manahub.feature.trades.domain.usecase.GetLocalOpenForTradeUseCase
import com.mmg.manahub.feature.trades.domain.usecase.GetLocalWishlistUseCase
import com.mmg.manahub.feature.trades.domain.usecase.SyncTradeListsFromRemoteUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────────────────────
//  UI state
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Sub-tabs inside the Trades feature.
 */
enum class TradesMainTab { MY_LIST, FRIENDS, HISTORY }

/**
 * Immutable UI state for [TradesViewModel].
 */
data class TradesUiState(
    val wishlist:            List<WishlistEntry>     = emptyList(),
    val openForTrade:        List<OpenForTradeEntry> = emptyList(),
    val friends:             List<Friend>            = emptyList(),
    val selectedTab:         TradesMainTab           = TradesMainTab.MY_LIST,
    val selectedWishlistIds: Set<String>             = emptySet(),
    val selectedOfferIds:    Set<String>             = emptySet(),
    val snackbarMessage:     String?                 = null,
    /** True only when the user has an active authenticated session. */
    val isLoggedIn:          Boolean                 = false,
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
class TradesViewModel(
    private val authRepo: AuthRepository,
    private val getLocalWishlist: GetLocalWishlistUseCase,
    private val getLocalOpenForTrade: GetLocalOpenForTradeUseCase,
    private val getFriends: GetFriendsUseCase,
    private val syncTradeListsFromRemote: SyncTradeListsFromRemoteUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TradesUiState())
    val uiState: StateFlow<TradesUiState> = _uiState.asStateFlow()

    init {
        observeSession()
        observeWishlist()
        observeOpenForTrade()
        observeFriends()
    }

    // ── Session observation ───────────────────────────────────────────────────

    private fun observeSession() {
        authRepo.sessionState
            .onEach { state ->
                _uiState.update { it.copy(isLoggedIn = state is SessionState.Authenticated) }
                if (state is SessionState.Authenticated) {
                    viewModelScope.launch {
                        syncTradeListsFromRemote(state.user.id)
                            .onFailure {
                                _uiState.update { s -> s.copy(snackbarMessage = "sync_failed") }
                            }
                    }
                }
            }
            .catch { /* session errors are non-fatal for this screen */ }
            .launchIn(viewModelScope)
    }

    // ── Observation ───────────────────────────────────────────────────────────

    private fun observeWishlist() {
        viewModelScope.launch {
            getLocalWishlist()
                .catch { e -> _uiState.update { it.copy(snackbarMessage = e.toUserFacingMessage()) } }
                .collect { entries ->
                    val validIds = entries.map { it.id }.toSet()
                    _uiState.update { s ->
                        s.copy(
                            wishlist = entries,
                            selectedWishlistIds = s.selectedWishlistIds.intersect(validIds),
                        )
                    }
                }
        }
    }

    private fun observeOpenForTrade() {
        viewModelScope.launch {
            getLocalOpenForTrade()
                .catch { e -> _uiState.update { it.copy(snackbarMessage = e.toUserFacingMessage()) } }
                .collect { entries ->
                    val validIds = entries.map { it.id }.toSet()
                    _uiState.update { s ->
                        s.copy(
                            openForTrade = entries,
                            selectedOfferIds = s.selectedOfferIds.intersect(validIds),
                        )
                    }
                }
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

    /** Switches between [TradesMainTab] states (MY_LIST, FRIENDS, HISTORY). */
    fun onTabSelected(tab: TradesMainTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    // ── Multi-select ──────────────────────────────────────────────────────────

    /** Toggles a wishlist entry in/out of the selection set. */
    fun onToggleWishlistSelect(id: String) {
        _uiState.update { state ->
            val updated = state.selectedWishlistIds.toMutableSet().also {
                if (!it.add(id)) it.remove(id)
            }
            state.copy(selectedWishlistIds = updated)
        }
    }

    /** Toggles an offer entry in/out of the selection set. */
    fun onToggleOfferSelect(id: String) {
        _uiState.update { state ->
            val updated = state.selectedOfferIds.toMutableSet().also {
                if (!it.add(id)) it.remove(id)
            }
            state.copy(selectedOfferIds = updated)
        }
    }

    /** Clears the one-shot snackbar message after it has been shown. */
    fun onSnackbarDismissed() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }
}
