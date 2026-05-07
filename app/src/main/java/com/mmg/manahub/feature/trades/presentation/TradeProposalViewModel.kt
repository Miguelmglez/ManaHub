package com.mmg.manahub.feature.trades.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.manahub.core.di.IoDispatcher
import com.mmg.manahub.feature.auth.domain.model.SessionState
import com.mmg.manahub.core.domain.model.*
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.domain.repository.UserCardRepository
import com.mmg.manahub.feature.auth.domain.repository.AuthRepository
import com.mmg.manahub.core.domain.usecase.card.SearchCardsUseCase
import com.mmg.manahub.feature.trades.data.remote.dto.TradeItemRequestDto
import com.mmg.manahub.feature.trades.domain.model.TradeError
import com.mmg.manahub.feature.trades.domain.repository.ReviewFlags
import com.mmg.manahub.feature.trades.domain.usecase.CounterProposalUseCase
import com.mmg.manahub.feature.trades.domain.usecase.CreateTradeProposalUseCase
import com.mmg.manahub.feature.trades.domain.usecase.EditProposalUseCase
import com.mmg.manahub.feature.trades.domain.usecase.RefreshTradesUseCase
import com.mmg.manahub.feature.trades.domain.usecase.SendProposalUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class TradeItemDraft(
    val id: String = UUID.randomUUID().toString(),
    val cardId: String,
    val cardName: String = "",
    val quantity: Int = 1,
    val isFoil: Boolean = false,
    val condition: String = "NM",
    val language: String = "en",
    val isAltArt: Boolean = false,
    val userCardIdRef: String? = null,
    val isReviewCollectionPlaceholder: Boolean = false,
)

data class ProposalEditorUiState(
    val receiverId: String = "",
    val proposerItems: List<TradeItemDraft> = emptyList(),
    val receiverItems: List<TradeItemDraft> = emptyList(),
    val includesReviewFromProposer: Boolean = false,
    val includesReviewFromReceiver: Boolean = false,
    val isCounterMode: Boolean = false,
    val parentProposalId: String? = null,
    val editingProposalId: String? = null,
    val rootProposalId: String = "",
    val currentVersion: Int = 1,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val snackbarMessage: String? = null,
    val navigateToThread: Pair<String, String>? = null, // (proposalId, rootProposalId)
    val navigateBack: Boolean = false,

    // Search / Add cards state
    val addCardsQuery: String = "",
    val addCardsResults: List<AddCardRow> = emptyList(),
    val isSearchingCards: Boolean = false,
    val scryfallResults: List<AddCardRow> = emptyList(),
    val isSearchingScryfall: Boolean = false,
    val collectionIds: Set<String> = emptySet(),
)

@HiltViewModel
class TradeProposalViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val authRepository: AuthRepository,
    private val createProposal: CreateTradeProposalUseCase,
    private val editProposal: EditProposalUseCase,
    private val sendProposal: SendProposalUseCase,
    private val counterProposal: CounterProposalUseCase,
    private val refreshTrades: RefreshTradesUseCase,
    private val cardRepository: CardRepository,
    private val userCardRepository: UserCardRepository,
    val searchCards: SearchCardsUseCase,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProposalEditorUiState())
    val uiState: StateFlow<ProposalEditorUiState> = _uiState.asStateFlow()

    private var currentUserId: String = ""
    private var collectionCards: List<Card> = emptyList()

    init {
        viewModelScope.launch {
            authRepository.sessionState.collect { state ->
                if (state is SessionState.Authenticated) {
                    currentUserId = state.user.id
                }
            }
        }
        observeCollection()
        val receiverId = savedStateHandle.get<String>("receiverId") ?: ""
        val parentProposalId = savedStateHandle.get<String>("parentProposalId")
        val editingProposalId = savedStateHandle.get<String>("editingProposalId")
        val rootProposalId = savedStateHandle.get<String>("rootProposalId") ?: receiverId
        _uiState.update {
            it.copy(
                receiverId = receiverId,
                parentProposalId = parentProposalId,
                editingProposalId = editingProposalId,
                rootProposalId = rootProposalId,
                isCounterMode = parentProposalId != null,
            )
        }
    }

    private fun observeCollection() {
        viewModelScope.launch {
            userCardRepository.observeCollection().collect { collection ->
                collectionCards = collection.map { it.card }.distinctBy { it.scryfallId }.sortedBy { it.name }
                val ids = collectionCards.map { it.scryfallId }.toSet()
                _uiState.update { it.copy(collectionIds = ids) }
            }
        }
    }

    fun onAddCardsQueryChange(query: String) {
        _uiState.update { it.copy(addCardsQuery = query) }
        if (query.isBlank()) {
            showCollectionCards()
            return
        }
        val filtered = collectionCards.filter { it.name.contains(query, ignoreCase = true) }
        _uiState.update { s ->
            s.copy(
                addCardsResults = filtered.map { card ->
                    AddCardRow(
                        card = card,
                        quantityInDeck = 0, // Not applicable here
                        isOwned = true,
                    )
                },
            )
        }
    }

    fun showCollectionCards() {
        _uiState.update { s ->
            s.copy(
                addCardsResults = collectionCards.map { card ->
                    AddCardRow(
                        card = card,
                        quantityInDeck = 0,
                        isOwned = true,
                    )
                },
            )
        }
    }

    fun searchScryfallDirect(query: String) {
        _uiState.update { it.copy(addCardsQuery = query) }
        if (query.isBlank()) {
            _uiState.update { it.copy(scryfallResults = emptyList(), isSearchingScryfall = false) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSearchingScryfall = true) }
            try {
                val cards = when (val result = cardRepository.searchCards(query)) {
                    is DataResult.Success -> result.data
                    is DataResult.Error -> emptyList()
                }
                val ownedIds = _uiState.value.collectionIds
                _uiState.update { s ->
                    s.copy(
                        isSearchingScryfall = false,
                        scryfallResults = cards.map { card ->
                            AddCardRow(
                                card = card,
                                quantityInDeck = 0,
                                isOwned = card.scryfallId in ownedIds,
                            )
                        },
                    )
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(isSearchingScryfall = false) }
            }
        }
    }

    fun clearAddCardsState() {
        _uiState.update { it.copy(addCardsQuery = "", addCardsResults = emptyList(), scryfallResults = emptyList()) }
    }

    fun getCardById(scryfallId: String): Card? {
        return (uiState.value.addCardsResults + uiState.value.scryfallResults)
            .find { it.card.scryfallId == scryfallId }?.card
    }

    fun addProposerItem(item: TradeItemDraft) {
        _uiState.update { it.copy(proposerItems = it.proposerItems + item) }
    }

    fun removeProposerItem(id: String) {
        _uiState.update { it.copy(proposerItems = it.proposerItems.filter { i -> i.id != id }) }
    }

    fun addReceiverItem(item: TradeItemDraft) {
        _uiState.update { it.copy(receiverItems = it.receiverItems + item) }
    }

    fun removeReceiverItem(id: String) {
        _uiState.update { it.copy(receiverItems = it.receiverItems.filter { i -> i.id != id }) }
    }

    fun toggleReviewCollectionProposer() {
        _uiState.update { it.copy(includesReviewFromProposer = !it.includesReviewFromProposer) }
    }

    fun toggleReviewCollectionReceiver() {
        _uiState.update { it.copy(includesReviewFromReceiver = !it.includesReviewFromReceiver) }
    }

    fun updateProposerItem(updated: TradeItemDraft) {
        _uiState.update {
            it.copy(proposerItems = it.proposerItems.map { i -> if (i.id == updated.id) updated else i })
        }
    }

    fun updateReceiverItem(updated: TradeItemDraft) {
        _uiState.update {
            it.copy(receiverItems = it.receiverItems.map { i -> if (i.id == updated.id) updated else i })
        }
    }

    fun validateInitialProposal(): Boolean {
        val state = _uiState.value
        if (state.isCounterMode) return true
        val proposerCovered = state.proposerItems.isNotEmpty() || state.includesReviewFromProposer
        val receiverCovered = state.receiverItems.isNotEmpty() || state.includesReviewFromReceiver
        return proposerCovered && receiverCovered
    }

    fun onSaveDraft() {
        val state = _uiState.value
        if (!validateInitialProposal()) {
            _uiState.update { it.copy(errorMessage = "INITIAL_ASYMMETRY") }
            return
        }
        viewModelScope.launch(ioDispatcher) {
            _uiState.update { it.copy(isSaving = true) }
            val result = createProposal(
                receiverId = state.receiverId,
                items = buildItemRequestDtos(state),
                includesReviewFromProposer = state.includesReviewFromProposer,
                includesReviewFromReceiver = state.includesReviewFromReceiver,
                autoSend = false,
            )
            _uiState.update { s ->
                result.fold(
                    onSuccess = { proposalId ->
                        s.copy(isSaving = false, navigateToThread = Pair(proposalId, proposalId))
                    },
                    onFailure = { e ->
                        s.copy(isSaving = false, errorMessage = (e as? TradeError)?.javaClass?.simpleName)
                    }
                )
            }
        }
    }

    fun onSendProposal() {
        val state = _uiState.value
        if (!validateInitialProposal()) {
            _uiState.update { it.copy(errorMessage = "INITIAL_ASYMMETRY") }
            return
        }
        viewModelScope.launch(ioDispatcher) {
            _uiState.update { it.copy(isSaving = true) }

            val editingId = state.editingProposalId
            val parentId = state.parentProposalId

            val result = when {
                editingId != null -> editProposal(
                    proposalId = editingId,
                    expectedVersion = state.currentVersion,
                    newItems = buildItemRequestDtos(state),
                    newReviewFlags = ReviewFlags(state.includesReviewFromProposer, state.includesReviewFromReceiver),
                )
                parentId != null -> counterProposal(
                    parentProposalId = parentId,
                    items = buildItemRequestDtos(state),
                ).map { it }
                else -> createProposal(
                    receiverId = state.receiverId,
                    items = buildItemRequestDtos(state),
                    includesReviewFromProposer = state.includesReviewFromProposer,
                    includesReviewFromReceiver = state.includesReviewFromReceiver,
                    autoSend = true,
                )
            }

            _uiState.update { s ->
                result.fold(
                    onSuccess = { id ->
                        when {
                            editingId != null -> s.copy(isSaving = false, navigateBack = true)
                            parentId != null -> {
                                val newId = id as? String ?: ""
                                s.copy(
                                    isSaving = false,
                                    navigateToThread = if (newId.isNotBlank()) Pair(newId, s.rootProposalId) else null,
                                    navigateBack = newId.isBlank(),
                                )
                            }
                            else -> {
                                val newId = id as? String ?: ""
                                s.copy(
                                    isSaving = false,
                                    navigateToThread = if (newId.isNotBlank()) Pair(newId, newId) else null,
                                    navigateBack = newId.isBlank(),
                                )
                            }
                        }
                    },
                    onFailure = { e ->
                        val msg = when (e) {
                            is TradeError.ProposalVersionMismatch -> "PROPOSAL_VERSION_MISMATCH"
                            is TradeError.InitialAsymmetryNotAllowed -> "INITIAL_ASYMMETRY"
                            else -> (e as? TradeError)?.javaClass?.simpleName
                        }
                        s.copy(isSaving = false, errorMessage = msg)
                    }
                )
            }
        }
    }

    fun onErrorDismissed() = _uiState.update { it.copy(errorMessage = null) }
    fun onSnackbarDismissed() = _uiState.update { it.copy(snackbarMessage = null) }
    fun onNavigationConsumed() = _uiState.update { it.copy(navigateToThread = null, navigateBack = false) }

    private fun buildItemRequestDtos(state: ProposalEditorUiState): List<TradeItemRequestDto> {
        val myId = currentUserId
        val proposerDtos = state.proposerItems.map { it.toRequestDto(fromUserId = myId, toUserId = state.receiverId) }
        val receiverDtos = state.receiverItems.map { it.toRequestDto(fromUserId = state.receiverId, toUserId = myId) }
        return proposerDtos + receiverDtos
    }

    private fun TradeItemDraft.toRequestDto(fromUserId: String, toUserId: String) = TradeItemRequestDto(
        fromUserId = fromUserId,
        toUserId = toUserId,
        userCardIdRef = userCardIdRef,
        quantity = if (isReviewCollectionPlaceholder) null else quantity,
        isFoil = if (isReviewCollectionPlaceholder) null else isFoil,
        condition = if (isReviewCollectionPlaceholder) null else condition,
        language = if (isReviewCollectionPlaceholder) null else language,
        isAltArt = if (isReviewCollectionPlaceholder) null else isAltArt,
        cardId = cardId,
        isReviewCollectionPlaceholder = isReviewCollectionPlaceholder,
    )
}
