package com.mmg.manahub.feature.trades.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.manahub.core.domain.auth.SessionState
import com.mmg.manahub.core.domain.auth.AuthRepository
import com.mmg.manahub.core.model.Friend
import com.mmg.manahub.feature.friends.domain.usecase.GetFriendsUseCase
import com.mmg.manahub.core.model.OpenForTradeEntry
import com.mmg.manahub.core.model.WishlistEntry
import com.mmg.manahub.core.model.toUserFacingMessage
import com.mmg.manahub.feature.trades.domain.usecase.GetLocalOpenForTradeUseCase
import com.mmg.manahub.feature.trades.domain.usecase.GetLocalWishlistUseCase
import com.mmg.manahub.feature.trades.domain.usecase.SyncTradeListsFromRemoteUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope

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
    /** True only when the user has an active authenticated session. */
    val isLoggedIn:          Boolean                 = false,
)

/**
 * One-shot events for [TradesViewModel] (snackbar messages). Delivered via a buffered
 * [Channel] rather than a nullable [MutableStateFlow] field, per CLAUDE.md's Playtest-section
 * standard: a StateFlow equality-collapses two consecutive identical events (the second toast
 * would be silently dropped) and can lose events across a lifecycle pause. Collected via
 * `LaunchedEffect(Unit) { viewModel.events.collect { } }` in [TradesScreen].
 */
sealed class TradesEvent {
    /** A pre-resolved, user-facing message (e.g. from [Throwable.toUserFacingMessage]). A null
     *  message is intentionally dropped by the screen (mirrors the previous nullable-field
     *  behaviour where a null message never surfaced a toast). */
    data class ShowMessage(val message: String?) : TradesEvent()

    /** The background wishlist/open-for-trade remote sync failed. The screen resolves the
     *  localized string — the ViewModel never carries a sentinel key or raw English text
     *  (trades audit §5.1, 2026-07-10). */
    data object SyncFailed : TradesEvent()
}

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

    private val _events = Channel<TradesEvent>(Channel.BUFFERED)
    val events: Flow<TradesEvent> = _events.receiveAsFlow()

    init {
        observeSession()
    }

    // ── Session observation ───────────────────────────────────────────────────

    private fun observeSession() {
        viewModelScope.launch {
            authRepo.sessionState
                .map { (it as? SessionState.Authenticated)?.user?.id }
                .distinctUntilChanged()
                .collectLatest { ownerId ->
                    _uiState.update { it.copy(
                        wishlist = emptyList(), openForTrade = emptyList(), friends = emptyList(),
                        isLoggedIn = ownerId != null,
                    ) }
                    coroutineScope {
                        launch {
                            getLocalWishlist().distinctUntilChanged()
                                .catch { e -> _events.trySend(TradesEvent.ShowMessage(e.toUserFacingMessage())) }
                                .collect { entries ->
                                    if ((authRepo.sessionState.value as? SessionState.Authenticated)?.user?.id == ownerId) {
                                        _uiState.update { it.copy(wishlist = entries) }
                                    }
                                }
                        }
                        launch {
                            getLocalOpenForTrade().distinctUntilChanged()
                                .catch { e -> _events.trySend(TradesEvent.ShowMessage(e.toUserFacingMessage())) }
                                .collect { entries ->
                                    if ((authRepo.sessionState.value as? SessionState.Authenticated)?.user?.id == ownerId) {
                                        _uiState.update { it.copy(openForTrade = entries) }
                                    }
                                }
                        }
                        if (ownerId != null) {
                            launch {
                                getFriends().distinctUntilChanged()
                                    .catch { /* friends are optional; silently ignore */ }
                                    .collect { friends ->
                                        if ((authRepo.sessionState.value as? SessionState.Authenticated)?.user?.id == ownerId) {
                                            _uiState.update { it.copy(friends = friends) }
                                        }
                                    }
                            }
                            launch {
                                syncTradeListsFromRemote(ownerId)
                                    .onFailure { _events.trySend(TradesEvent.SyncFailed) }
                            }
                        }
                    }
                }
        }
    }

    // ── Tab selection ─────────────────────────────────────────────────────────

    /** Switches between [TradesMainTab] states (MY_LIST, FRIENDS, HISTORY). */
    fun onTabSelected(tab: TradesMainTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }
}
