package com.mmg.manahub.feature.trades.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.manahub.core.di.IoDispatcher
import com.mmg.manahub.feature.auth.domain.model.SessionState
import com.mmg.manahub.feature.auth.domain.repository.AuthRepository
import com.mmg.manahub.feature.trades.domain.model.TradeError
import com.mmg.manahub.feature.trades.domain.model.TradeProposal
import com.mmg.manahub.feature.trades.domain.usecase.AcceptProposalUseCase
import com.mmg.manahub.feature.trades.domain.usecase.CancelProposalUseCase
import com.mmg.manahub.feature.trades.domain.usecase.DeclineProposalUseCase
import com.mmg.manahub.feature.trades.domain.usecase.GetTradeThreadUseCase
import com.mmg.manahub.feature.trades.domain.usecase.MarkCompletedUseCase
import com.mmg.manahub.feature.trades.domain.usecase.RefreshTradesUseCase
import com.mmg.manahub.feature.trades.domain.usecase.RevokeAcceptanceUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class NegotiationError {
    data class CardAlreadyLocked(val cardIds: List<String>) : NegotiationError()
    object ProposalVersionMismatch : NegotiationError()
    object InventoryGone : NegotiationError()
    data class Generic(val message: String?) : NegotiationError()
}

data class EditorNavArgs(
    val receiverId: String,
    val proposalId: String,
    val rootProposalId: String,
    val isCounter: Boolean,
)

data class NegotiationUiState(
    val thread: List<TradeProposal> = emptyList(),
    val currentUserId: String = "",
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isProcessing: Boolean = false,
    val errorDialog: NegotiationError? = null,
    val snackbarMessage: String? = null,
    val navigateToEditor: EditorNavArgs? = null,
)

@HiltViewModel
class TradeNegotiationViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val authRepository: AuthRepository,
    private val getThread: GetTradeThreadUseCase,
    private val refreshTrades: RefreshTradesUseCase,
    private val acceptProposal: AcceptProposalUseCase,
    private val declineProposal: DeclineProposalUseCase,
    private val cancelProposal: CancelProposalUseCase,
    private val revokeAcceptance: RevokeAcceptanceUseCase,
    private val markCompleted: MarkCompletedUseCase,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val rootProposalId: String = savedStateHandle["rootProposalId"] ?: ""

    private val _uiState = MutableStateFlow(NegotiationUiState(isLoading = true))
    val uiState: StateFlow<NegotiationUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.sessionState.collect { state ->
                if (state is SessionState.Authenticated) {
                    _uiState.update { it.copy(currentUserId = state.user.id) }
                }
            }
        }
        viewModelScope.launch {
            getThread(rootProposalId)
                .catch { _uiState.update { s -> s.copy(isLoading = false) } }
                .collect { thread ->
                    _uiState.update { s -> s.copy(thread = thread, isLoading = false) }
                }
        }
    }

    fun onAccept(proposalId: String) {
        if (_uiState.value.isProcessing) return
        viewModelScope.launch(ioDispatcher) {
            _uiState.update { it.copy(isProcessing = true) }
            acceptProposal(proposalId).onFailure { e ->
                val error = when (e) {
                    is TradeError.CardAlreadyLocked -> NegotiationError.CardAlreadyLocked(e.cardIds)
                    is TradeError.CannotAcceptReviewCollection -> NegotiationError.Generic("CANNOT_ACCEPT_REVIEW_COLLECTION")
                    else -> NegotiationError.Generic(e.message)
                }
                _uiState.update { it.copy(errorDialog = error) }
            }
            _uiState.update { it.copy(isProcessing = false) }
        }
    }

    fun onDecline(proposalId: String) {
        if (_uiState.value.isProcessing) return
        viewModelScope.launch(ioDispatcher) {
            _uiState.update { it.copy(isProcessing = true) }
            declineProposal(proposalId).onFailure { e ->
                _uiState.update { it.copy(snackbarMessage = e.message) }
            }
            _uiState.update { it.copy(isProcessing = false) }
        }
    }

    fun onCancel(proposalId: String) {
        if (_uiState.value.isProcessing) return
        viewModelScope.launch(ioDispatcher) {
            _uiState.update { it.copy(isProcessing = true) }
            cancelProposal(proposalId).onFailure { e ->
                _uiState.update { it.copy(snackbarMessage = e.message) }
            }
            _uiState.update { it.copy(isProcessing = false) }
        }
    }

    fun onRevoke(proposalId: String) {
        if (_uiState.value.isProcessing) return
        viewModelScope.launch(ioDispatcher) {
            _uiState.update { it.copy(isProcessing = true) }
            revokeAcceptance(proposalId).onFailure { e ->
                _uiState.update { it.copy(snackbarMessage = e.message) }
            }
            _uiState.update { it.copy(isProcessing = false) }
        }
    }

    fun onMarkCompleted(proposalId: String) {
        if (_uiState.value.isProcessing) return
        viewModelScope.launch(ioDispatcher) {
            _uiState.update { it.copy(isProcessing = true) }
            markCompleted(proposalId).onFailure { e ->
                val error = when (e) {
                    is TradeError.InventoryGone -> NegotiationError.InventoryGone
                    else -> NegotiationError.Generic(e.message)
                }
                _uiState.update { it.copy(errorDialog = error) }
            }
            _uiState.update { it.copy(isProcessing = false) }
        }
    }

    fun onCounter(proposalId: String) {
        val proposal = _uiState.value.thread.find { it.id == proposalId } ?: return
        val currentUserId = _uiState.value.currentUserId
        val otherUserId = if (proposal.proposerId == currentUserId) proposal.receiverId else proposal.proposerId
        _uiState.update {
            it.copy(navigateToEditor = EditorNavArgs(
                receiverId = otherUserId,
                proposalId = proposalId,
                rootProposalId = rootProposalId,
                isCounter = true,
            ))
        }
    }

    fun onEdit(proposalId: String) {
        val proposal = _uiState.value.thread.find { it.id == proposalId } ?: return
        val currentUserId = _uiState.value.currentUserId
        val otherUserId = if (proposal.proposerId == currentUserId) proposal.receiverId else proposal.proposerId
        _uiState.update {
            it.copy(navigateToEditor = EditorNavArgs(
                receiverId = otherUserId,
                proposalId = proposalId,
                rootProposalId = rootProposalId,
                isCounter = false,
            ))
        }
    }

    fun onErrorDismissed() = _uiState.update { it.copy(errorDialog = null) }
    fun onSnackbarDismissed() = _uiState.update { it.copy(snackbarMessage = null) }
    fun onNavigationConsumed() = _uiState.update { it.copy(navigateToEditor = null) }

    fun refresh() {
        val userId = _uiState.value.currentUserId
        if (userId.isBlank()) return
        viewModelScope.launch(ioDispatcher) {
            _uiState.update { it.copy(isRefreshing = true) }
            refreshTrades(userId)
                .onFailure { e -> _uiState.update { it.copy(snackbarMessage = e.message) } }
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }
}
