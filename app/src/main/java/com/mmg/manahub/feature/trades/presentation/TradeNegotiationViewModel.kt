package com.mmg.manahub.feature.trades.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.core.di.IoDispatcher
import com.mmg.manahub.core.util.AnalyticsHelper
import com.mmg.manahub.feature.auth.domain.model.SessionState
import com.mmg.manahub.feature.auth.domain.repository.AuthRepository
import com.mmg.manahub.feature.friends.domain.repository.FriendRepository
import com.mmg.manahub.feature.trades.data.local.dao.TradeCollectionSyncDao
import com.mmg.manahub.feature.trades.domain.model.TradeError
import com.mmg.manahub.feature.trades.domain.model.TradeItem
import com.mmg.manahub.feature.trades.domain.model.TradeProposal
import com.mmg.manahub.feature.trades.domain.model.toUserFacingMessage
import com.mmg.manahub.feature.trades.domain.usecase.AcceptProposalUseCase
import com.mmg.manahub.feature.trades.domain.usecase.CancelProposalUseCase
import com.mmg.manahub.feature.trades.domain.usecase.DeclineProposalUseCase
import com.mmg.manahub.feature.trades.domain.usecase.GetTradeThreadUseCase
import com.mmg.manahub.feature.trades.domain.usecase.MarkCompletedUseCase
import com.mmg.manahub.feature.trades.domain.usecase.RefreshTradeThreadUseCase
import com.mmg.manahub.feature.trades.domain.usecase.RevokeAcceptanceUseCase
import com.mmg.manahub.feature.trades.domain.usecase.UpdateTradeCollectionUseCase
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
    /** userId → display name; populated from auth session (current user) and friends list. */
    val participantNames: Map<String, String> = emptyMap(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isProcessing: Boolean = false,
    val errorDialog: NegotiationError? = null,
    val snackbarMessage: String? = null,
    val navigateToEditor: EditorNavArgs? = null,
    val pendingMarkCompletedProposalId: String? = null,
    val pendingMarkCompletedSentItems: List<TradeItem> = emptyList(),
    val pendingMarkCompletedReceivedItems: List<TradeItem> = emptyList(),
    /** Proposal ID waiting for revoke confirmation. */
    val pendingRevokeProposalId: String? = null,
    /** True if the user already synced the collection for the pending-revoke proposal. */
    val pendingRevokeHasSynced: Boolean = false,
    val pendingCancelProposalId: String? = null,
    /**
     * Set when the user taps Accept on a proposal where the other party has only included
     * "Review my collection" (no concrete card items). Stores the proposal ID until the user
     * confirms or dismisses the gift-warning dialog.
     */
    val pendingGiftAcceptProposalId: String? = null,
    /**
     * Set of proposal IDs for which the current user has already run
     * "Update Collection". Populated reactively from Room via
     * [TradeCollectionSyncDao.observeSyncedProposalIds].
     */
    val syncedCollectionProposalIds: Set<String> = emptySet(),
    /** True while [UpdateTradeCollectionUseCase] is executing, to disable the button. */
    val isSyncingCollection: Boolean = false,
)

@HiltViewModel
class TradeNegotiationViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val authRepository: AuthRepository,
    private val friendRepository: FriendRepository,
    private val getThread: GetTradeThreadUseCase,
    private val refreshTradeThread: RefreshTradeThreadUseCase,
    private val acceptProposal: AcceptProposalUseCase,
    private val declineProposal: DeclineProposalUseCase,
    private val cancelProposal: CancelProposalUseCase,
    private val revokeAcceptance: RevokeAcceptanceUseCase,
    private val markCompleted: MarkCompletedUseCase,
    private val updateTradeCollection: UpdateTradeCollectionUseCase,
    private val tradeCollectionSyncDao: TradeCollectionSyncDao,
    private val analyticsHelper: AnalyticsHelper,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val rootProposalId: String = savedStateHandle["rootProposalId"] ?: ""

    private val _uiState = MutableStateFlow(NegotiationUiState(isLoading = true))
    val uiState: StateFlow<NegotiationUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.sessionState.collect { state ->
                if (state is SessionState.Authenticated) {
                    val userId = state.user.id
                    val firstAuth = _uiState.value.currentUserId.isBlank()
                    val nickname = state.user.nickname ?: ""
                    _uiState.update { s ->
                        s.copy(
                            currentUserId = userId,
                            participantNames = s.participantNames + (userId to nickname),
                        )
                    }
                    if (firstAuth) refresh()
                }
            }
        }
        viewModelScope.launch {
            friendRepository.observeFriends()
                .catch { /* friends are supplementary for display only */ }
                .collect { friends ->
                    val names = friends.associate { it.userId to it.nickname }
                    _uiState.update { s -> s.copy(participantNames = s.participantNames + names) }
                }
        }
        viewModelScope.launch {
            getThread(rootProposalId)
                .catch { _uiState.update { s -> s.copy(isLoading = false) } }
                .collect { thread ->
                    _uiState.update { s -> s.copy(thread = thread, isLoading = false) }
                }
        }
        // Observe which proposals have already been collection-synced by this user.
        // We wait until the userId is known before starting the Room Flow so the
        // query targets the correct user from the first emission.
        viewModelScope.launch {
            authRepository.sessionState.collect { state ->
                if (state is SessionState.Authenticated) {
                    tradeCollectionSyncDao.observeSyncedProposalIds(state.user.id)
                        .catch { /* non-critical — sync state is best-effort */ }
                        .collect { ids ->
                            _uiState.update { s ->
                                s.copy(syncedCollectionProposalIds = ids.toSet())
                            }
                        }
                }
            }
        }
    }

    fun onAccept(proposalId: String) {
        if (_uiState.value.isProcessing) return
        val proposal = _uiState.value.thread.find { it.id == proposalId } ?: return
        val currentUserId = _uiState.value.currentUserId
        val isProposer = proposal.proposerId == currentUserId
        // Determine whether the other party's side is a gift:
        // they have the review-collection flag set but zero concrete card items.
        val otherPartyId = if (isProposer) proposal.receiverId else proposal.proposerId
        val otherPartyReviewFlag = if (isProposer) {
            proposal.includesReviewCollectionFromReceiver
        } else {
            proposal.includesReviewCollectionFromProposer
        }
        val otherPartyActualItems = proposal.items.filter {
            it.fromUserId == otherPartyId && !it.isReviewCollectionPlaceholder
        }
        val isGiftTrade = otherPartyReviewFlag && otherPartyActualItems.isEmpty()
        if (isGiftTrade) {
            _uiState.update { it.copy(pendingGiftAcceptProposalId = proposalId) }
            return
        }
        doAccept(proposalId)
    }

    /** Called when the user confirms accepting a gift trade via the warning dialog. */
    fun onGiftAcceptConfirmed() {
        val proposalId = _uiState.value.pendingGiftAcceptProposalId ?: return
        _uiState.update { it.copy(pendingGiftAcceptProposalId = null) }
        doAccept(proposalId)
    }

    /** Called when the user dismisses the gift-accept warning dialog. */
    fun onGiftAcceptDismissed() {
        _uiState.update { it.copy(pendingGiftAcceptProposalId = null) }
    }

    private fun doAccept(proposalId: String) {
        if (_uiState.value.isProcessing) return
        viewModelScope.launch(ioDispatcher) {
            _uiState.update { it.copy(isProcessing = true) }
            FirebaseCrashlytics.getInstance().log("trade_accept_started: proposal=$proposalId")
            acceptProposal(proposalId)
                .onSuccess {
                    analyticsHelper.logEvent("trade_accepted", mapOf("root_proposal_id" to rootProposalId))
                    refresh()
                }
                .onFailure { e ->
                    val error = when (e) {
                        is TradeError.CardAlreadyLocked -> NegotiationError.CardAlreadyLocked(e.cardIds)
                        is TradeError.CannotAcceptReviewCollection -> NegotiationError.Generic(e.toUserFacingMessage())
                        else -> {
                            FirebaseCrashlytics.getInstance().apply {
                                log("trade_accept_failed: proposal=$proposalId")
                                setCustomKey("trade_root_proposal_id", rootProposalId)
                                recordException(e)
                            }
                            NegotiationError.Generic(e.toUserFacingMessage())
                        }
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
            declineProposal(proposalId)
                .onSuccess { refresh() }
                .onFailure { e ->
                    FirebaseCrashlytics.getInstance().apply {
                        log("trade_decline_failed: proposal=$proposalId")
                        setCustomKey("trade_root_proposal_id", rootProposalId)
                        recordException(e)
                    }
                    _uiState.update { it.copy(snackbarMessage = e.toUserFacingMessage()) }
                }
            _uiState.update { it.copy(isProcessing = false) }
        }
    }

    fun onCancelRequested(proposalId: String) {
        _uiState.update { it.copy(pendingCancelProposalId = proposalId) }
    }

    fun onCancelConfirmed() {
        val proposalId = _uiState.value.pendingCancelProposalId ?: return
        _uiState.update { it.copy(pendingCancelProposalId = null) }
        onCancel(proposalId)
    }

    fun onCancelDismissed() {
        _uiState.update { it.copy(pendingCancelProposalId = null) }
    }

    private fun onCancel(proposalId: String) {
        if (_uiState.value.isProcessing) return
        viewModelScope.launch(ioDispatcher) {
            _uiState.update { it.copy(isProcessing = true) }
            cancelProposal(proposalId)
                .onSuccess { refresh() }
                .onFailure { e ->
                    FirebaseCrashlytics.getInstance().apply {
                        log("trade_cancel_failed: proposal=$proposalId")
                        setCustomKey("trade_root_proposal_id", rootProposalId)
                        recordException(e)
                    }
                    _uiState.update { it.copy(snackbarMessage = e.toUserFacingMessage()) }
                }
            _uiState.update { it.copy(isProcessing = false) }
        }
    }

    fun onRevoke(proposalId: String) {
        if (_uiState.value.isProcessing) return
        val hasSynced = proposalId in _uiState.value.syncedCollectionProposalIds
        _uiState.update { it.copy(
            pendingRevokeProposalId = proposalId,
            pendingRevokeHasSynced = hasSynced,
        )}
    }

    fun onRevokeDismissed() {
        _uiState.update { it.copy(pendingRevokeProposalId = null, pendingRevokeHasSynced = false) }
    }

    fun onRevokeConfirmed(reverseCollection: Boolean) {
        val proposalId = _uiState.value.pendingRevokeProposalId ?: return
        _uiState.update { it.copy(pendingRevokeProposalId = null, pendingRevokeHasSynced = false) }
        doRevoke(proposalId, reverseCollection)
    }

    private fun doRevoke(proposalId: String, reverseCollection: Boolean) {
        val userId = _uiState.value.currentUserId
        val proposal = _uiState.value.thread.find { it.id == proposalId } ?: return
        viewModelScope.launch(ioDispatcher) {
            _uiState.update { it.copy(isProcessing = true) }
            revokeAcceptance(proposalId)
                .onSuccess {
                    if (reverseCollection) {
                        val sentItems = proposal.items.filter {
                            it.fromUserId == userId && !it.isReviewCollectionPlaceholder
                        }
                        val receivedItems = proposal.items.filter {
                            it.toUserId == userId && !it.isReviewCollectionPlaceholder
                        }
                        updateTradeCollection(proposalId, userId, sentItems, receivedItems, reverse = true)
                    }
                    FirebaseCrashlytics.getInstance().log("trade_revoked: proposal=$proposalId")
                    refresh()
                }
                .onFailure { e ->
                    FirebaseCrashlytics.getInstance().apply {
                        log("trade_revoke_failed: proposal=$proposalId")
                        setCustomKey("trade_root_proposal_id", rootProposalId)
                        recordException(e)
                    }
                    _uiState.update { it.copy(snackbarMessage = e.toUserFacingMessage()) }
                }
            _uiState.update { it.copy(isProcessing = false) }
        }
    }

    fun onMarkCompleted(proposalId: String) {
        if (_uiState.value.isProcessing) return
        val proposal = _uiState.value.thread.find { it.id == proposalId } ?: return
        val currentUserId = _uiState.value.currentUserId
        val sentItems = proposal.items.filter { it.fromUserId == currentUserId && !it.isReviewCollectionPlaceholder }
        val receivedItems = proposal.items.filter { it.toUserId == currentUserId && !it.isReviewCollectionPlaceholder }
        _uiState.update { it.copy(
            pendingMarkCompletedProposalId = proposalId,
            pendingMarkCompletedSentItems = sentItems,
            pendingMarkCompletedReceivedItems = receivedItems,
        )}
    }

    fun onConfirmMarkCompleted(addToCollection: Boolean) {
        val proposalId = _uiState.value.pendingMarkCompletedProposalId ?: return
        val sentItems = _uiState.value.pendingMarkCompletedSentItems
        val receivedItems = _uiState.value.pendingMarkCompletedReceivedItems
        _uiState.update { it.copy(
            pendingMarkCompletedProposalId = null,
            pendingMarkCompletedSentItems = emptyList(),
            pendingMarkCompletedReceivedItems = emptyList(),
        )}
        doMarkCompleted(proposalId, addToCollection, sentItems, receivedItems)
    }

    fun onDismissMarkCompletedDialog() {
        _uiState.update { it.copy(
            pendingMarkCompletedProposalId = null,
            pendingMarkCompletedSentItems = emptyList(),
            pendingMarkCompletedReceivedItems = emptyList(),
        )}
    }

    private fun doMarkCompleted(
        proposalId: String,
        addToCollection: Boolean,
        sentItems: List<TradeItem>,
        receivedItems: List<TradeItem>,
    ) {
        val userId = _uiState.value.currentUserId
        viewModelScope.launch(ioDispatcher) {
            _uiState.update { it.copy(isProcessing = true) }
            FirebaseCrashlytics.getInstance().log("trade_mark_completed_started: proposal=$proposalId")
            markCompleted(proposalId)
                .onSuccess {
                    if (addToCollection) {
                        updateTradeCollection(proposalId, userId, sentItems, receivedItems)
                    }
                    analyticsHelper.logEvent("trade_completed", mapOf(
                        "root_proposal_id" to rootProposalId,
                        "added_to_collection" to addToCollection,
                    ))
                    FirebaseCrashlytics.getInstance().log("trade_mark_completed_success: proposal=$proposalId")
                    refresh()
                }
                .onFailure { e ->
                    val error = when (e) {
                        is TradeError.InventoryGone -> NegotiationError.InventoryGone
                        else -> {
                            FirebaseCrashlytics.getInstance().apply {
                                log("trade_mark_completed_failed: proposal=$proposalId")
                                setCustomKey("trade_root_proposal_id", rootProposalId)
                                recordException(e)
                            }
                            NegotiationError.Generic(e.toUserFacingMessage())
                        }
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

    /**
     * Triggered when the user taps "Update Collection" on a completed proposal.
     *
     * Deducts sent cards from the local collection and adds received cards, then
     * writes a [TradeCollectionSyncEntity] so the button is replaced with a
     * static "Collection updated" label on subsequent renders.
     */
    fun onUpdateCollection(proposalId: String) {
        val proposal = _uiState.value.thread.find { it.id == proposalId } ?: return
        val userId = _uiState.value.currentUserId
        if (userId.isBlank()) return
        if (_uiState.value.isSyncingCollection) return
        viewModelScope.launch(ioDispatcher) {
            _uiState.update { it.copy(isSyncingCollection = true) }
            val sentItems = proposal.items.filter {
                it.fromUserId == userId && !it.isReviewCollectionPlaceholder
            }
            val receivedItems = proposal.items.filter {
                it.toUserId == userId && !it.isReviewCollectionPlaceholder
            }
            updateTradeCollection(proposalId, userId, sentItems, receivedItems)
                .onSuccess {
                    analyticsHelper.logEvent(
                        "trade_collection_updated",
                        mapOf("proposal_id" to proposalId),
                    )
                    _uiState.update { s ->
                        s.copy(
                            syncedCollectionProposalIds = s.syncedCollectionProposalIds + proposalId,
                            snackbarMessage = "collection_updated",
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update { s ->
                        s.copy(snackbarMessage = e.message ?: "collection_update_failed")
                    }
                }
            _uiState.update { it.copy(isSyncingCollection = false) }
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
            refreshTradeThread(rootProposalId, userId)
                .onFailure { e -> _uiState.update { it.copy(snackbarMessage = e.toUserFacingMessage()) } }
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }
}
