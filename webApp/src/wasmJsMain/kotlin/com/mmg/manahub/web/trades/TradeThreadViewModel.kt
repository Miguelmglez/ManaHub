package com.mmg.manahub.web.trades

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.data.remote.FriendshipClient
import com.mmg.manahub.core.model.TradeProposal
import com.mmg.manahub.feature.trades.domain.usecase.AcceptProposalUseCase
import com.mmg.manahub.feature.trades.domain.usecase.CancelProposalUseCase
import com.mmg.manahub.feature.trades.domain.usecase.DeclineProposalUseCase
import com.mmg.manahub.feature.trades.domain.usecase.GetTradeThreadUseCase
import com.mmg.manahub.feature.trades.domain.usecase.RefreshTradeThreadUseCase
import com.mmg.manahub.feature.trades.domain.usecase.RevokeAcceptanceUseCase
import com.mmg.manahub.web.common.toUserFacingMessage
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NegotiationUiState(
    val isLoading: Boolean = true,
    val currentUserId: String = "",
    val participantNames: Map<String, String> = emptyMap(),
    val thread: List<TradeProposal> = emptyList(),
    val isProcessing: Boolean = false,
    val error: String? = null,
    /** Proposal id awaiting a Cancel confirmation (mirrors Android's confirm-before-cancel dialog). */
    val pendingCancelProposalId: String? = null,
)

/** One-shot [TradeThreadViewModel] events -- delivered via a buffered [Channel], never a nullable `StateFlow` field (CLAUDE.md standard). */
sealed interface NegotiationEvent {
    /** Navigate to the counter-proposal editor for [parentProposalId], offering to [counterpartyId]. */
    data class NavigateToCounter(val parentProposalId: String, val counterpartyId: String) : NegotiationEvent
}

/**
 * Backs [TradeThreadScreen] -- the negotiation detail view for one root proposal chain: every
 * version in the thread, and Accept / Decline / Counter / Cancel / Revoke acceptance actions. A
 * deliberately-scoped-down web port of Android's `TradeNegotiationViewModel`: **Mark Completed +
 * automatic collection sync (`UpdateTradeCollectionUseCase`) and the "gift trade" warning dialog
 * are explicitly DEFERRED** (flagged follow-up, not half-built) -- both require a Room-specific
 * `TradeCollectionSyncDao` sync-tracking table that has no web equivalent yet, and are secondary
 * to the core respond-to-a-proposal flow this slice targets. `onCounter` only navigates (via
 * [NegotiationEvent.NavigateToCounter]) to the shared item-picker screen -- it does not build the
 * counter-items list itself.
 */
class TradeThreadViewModel(
    private val rootProposalId: String,
    private val supabaseClient: SupabaseClient,
    private val getThread: GetTradeThreadUseCase,
    private val refreshTradeThread: RefreshTradeThreadUseCase,
    private val acceptProposal: AcceptProposalUseCase,
    private val declineProposal: DeclineProposalUseCase,
    private val cancelProposal: CancelProposalUseCase,
    private val revokeAcceptance: RevokeAcceptanceUseCase,
    private val friendshipClient: FriendshipClient,
    private val crashReporter: CrashReporter,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NegotiationUiState())
    val uiState: StateFlow<NegotiationUiState> = _uiState.asStateFlow()

    private val _events = Channel<NegotiationEvent>(Channel.BUFFERED)
    val events: Flow<NegotiationEvent> = _events.receiveAsFlow()

    private var refreshedForUserId: String? = null

    init {
        viewModelScope.launch {
            supabaseClient.auth.sessionStatus.collect { status ->
                if (status is SessionStatus.Authenticated) {
                    val userId = status.session.user?.id ?: return@collect
                    _uiState.update { it.copy(currentUserId = userId) }
                    if (refreshedForUserId != userId) {
                        refreshedForUserId = userId
                        refresh()
                    }
                }
            }
        }
        viewModelScope.launch {
            getThread(rootProposalId)
                .distinctUntilChanged()
                .catch { _uiState.update { it.copy(isLoading = false) } }
                .collect { thread ->
                    _uiState.update { it.copy(thread = thread, isLoading = false) }
                    resolveParticipantNames(thread)
                }
        }
    }

    private suspend fun resolveParticipantNames(thread: List<TradeProposal>) {
        val known = _uiState.value.participantNames.keys
        val currentUserId = _uiState.value.currentUserId
        val missing = thread.flatMap { listOf(it.proposerId, it.receiverId) }
            .filter { it.isNotBlank() && it != currentUserId }
            .distinct()
            .filterNot { known.contains(it) }
        if (missing.isEmpty()) return
        try {
            val resolved = friendshipClient.getProfilesByIds(idFilter = "in.(${missing.joinToString(",")})")
                .associate { it.id to (it.nickname?.takeIf { n -> n.isNotBlank() } ?: it.gameTag ?: "Unknown") }
            _uiState.update { it.copy(participantNames = it.participantNames + resolved) }
        } catch (e: Throwable) {
            crashReporter.recordException(e)
        }
    }

    fun refresh() {
        val userId = _uiState.value.currentUserId
        if (userId.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            refreshTradeThread(rootProposalId, userId)
                .onFailure { e -> _uiState.update { it.copy(error = e.toUserFacingMessage("refresh this trade", crashReporter)) } }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun onAccept(proposalId: String) = runGuarded {
        acceptProposal(proposalId)
            .onSuccess { refresh() }
            .onFailure { e -> _uiState.update { it.copy(error = e.toUserFacingMessage("accept this proposal", crashReporter)) } }
    }

    fun onDecline(proposalId: String) = runGuarded {
        declineProposal(proposalId)
            .onSuccess { refresh() }
            .onFailure { e -> _uiState.update { it.copy(error = e.toUserFacingMessage("decline this proposal", crashReporter)) } }
    }

    fun onRevoke(proposalId: String) = runGuarded {
        revokeAcceptance(proposalId)
            .onSuccess { refresh() }
            .onFailure { e -> _uiState.update { it.copy(error = e.toUserFacingMessage("revoke your acceptance", crashReporter)) } }
    }

    fun onCancelRequested(proposalId: String) {
        _uiState.update { it.copy(pendingCancelProposalId = proposalId) }
    }

    fun onCancelDismissed() {
        _uiState.update { it.copy(pendingCancelProposalId = null) }
    }

    fun onCancelConfirmed() {
        val proposalId = _uiState.value.pendingCancelProposalId ?: return
        _uiState.update { it.copy(pendingCancelProposalId = null) }
        runGuarded {
            cancelProposal(proposalId)
                .onSuccess { refresh() }
                .onFailure { e -> _uiState.update { it.copy(error = e.toUserFacingMessage("cancel this proposal", crashReporter)) } }
        }
    }

    fun onCounter(proposalId: String) {
        val proposal = _uiState.value.thread.find { it.id == proposalId } ?: return
        val currentUserId = _uiState.value.currentUserId
        val counterpartyId = if (proposal.proposerId == currentUserId) proposal.receiverId else proposal.proposerId
        _events.trySend(NegotiationEvent.NavigateToCounter(parentProposalId = proposalId, counterpartyId = counterpartyId))
    }

    fun onErrorDismissed() {
        _uiState.update { it.copy(error = null) }
    }

    /**
     * Atomic double-tap guard: acquires [NegotiationUiState.isProcessing] synchronously via a
     * single [MutableStateFlow.update] call BEFORE launching the coroutine (mirrors Android's
     * `TradeNegotiationViewModel.doAccept` §2.7 fix -- checking-then-setting on the caller's thread
     * would let two fast taps both read `false` and both proceed).
     */
    private fun runGuarded(block: suspend () -> Unit) {
        var acquired = false
        _uiState.update { state -> if (state.isProcessing) state else { acquired = true; state.copy(isProcessing = true) } }
        if (!acquired) return
        viewModelScope.launch {
            block()
            _uiState.update { it.copy(isProcessing = false) }
        }
    }
}
