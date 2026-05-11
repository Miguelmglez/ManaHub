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
import com.mmg.manahub.feature.friends.domain.model.Friend
import com.mmg.manahub.feature.friends.domain.repository.FriendRepository
import com.mmg.manahub.feature.trades.domain.model.TradeError
import com.mmg.manahub.feature.trades.domain.model.toUserFacingMessage
import com.mmg.manahub.feature.trades.domain.repository.ReviewFlags
import com.mmg.manahub.feature.trades.domain.repository.WishlistRepository
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
    val imageUrl: String? = null,
    val priceUsd: Double? = null,
    val priceUsdFoil: Double? = null,
    val priceEur: Double? = null,
    val priceEurFoil: Double? = null,
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
    val addCardsResults: List<AddCardRow> = emptyList(), // This is Collection/Offer
    val wishlistResults: List<AddCardRow> = emptyList(),
    val isSearchingCards: Boolean = false,
    val isSearchingWishlist: Boolean = false,
    val scryfallResults: List<AddCardRow> = emptyList(),
    val isSearchingScryfall: Boolean = false,
    val collectionIds: Set<String> = emptySet(),

    val friends: List<Friend> = emptyList(),
    val selectedFriend: Friend? = null,
    val sessionState: SessionState = SessionState.Loading,
) {
    val totalProposerValueUsd: Double get() = proposerItems.sumOf { 
        val price = if (it.isFoil) (it.priceUsdFoil ?: it.priceUsd ?: 0.0) else (it.priceUsd ?: 0.0)
        price * it.quantity 
    }
    val totalReceiverValueUsd: Double get() = receiverItems.sumOf { 
        val price = if (it.isFoil) (it.priceUsdFoil ?: it.priceUsd ?: 0.0) else (it.priceUsd ?: 0.0)
        price * it.quantity 
    }
    val totalProposerValueEur: Double get() = proposerItems.sumOf { 
        val price = if (it.isFoil) (it.priceEurFoil ?: it.priceEur ?: 0.0) else (it.priceEur ?: 0.0)
        price * it.quantity 
    }
    val totalReceiverValueEur: Double get() = receiverItems.sumOf { 
        val price = if (it.isFoil) (it.priceEurFoil ?: it.priceEur ?: 0.0) else (it.priceEur ?: 0.0)
        price * it.quantity 
    }
}

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
    private val wishlistRepository: WishlistRepository,
    private val friendRepository: FriendRepository,
    val searchCards: SearchCardsUseCase,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProposalEditorUiState())
    val uiState: StateFlow<ProposalEditorUiState> = _uiState.asStateFlow()

    private var currentUserId: String = ""
    private var collectionCards: List<Card> = emptyList()
    private var wishlistCards: List<Card> = emptyList()
    
    private var friendCollectionCards: List<Card> = emptyList()
    private var friendWishlistCards: List<Card> = emptyList()

    init {
        viewModelScope.launch {
            authRepository.sessionState.collect { state ->
                if (state is SessionState.Authenticated) {
                    currentUserId = state.user.id
                }
                _uiState.update { it.copy(sessionState = state) }
            }
        }
        observeCollection()
        observeWishlist()
        observeFriends()
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
                updateSearchLists(_uiState.value.addCardsQuery)
            }
        }
    }

    private fun observeWishlist() {
        viewModelScope.launch {
            wishlistRepository.observeLocal().collect { wishlist ->
                wishlistCards = wishlist.mapNotNull { it.card }.distinctBy { it.scryfallId }.sortedBy { it.name }
                updateSearchLists(_uiState.value.addCardsQuery)
            }
        }
    }

    private fun observeFriends() {
        viewModelScope.launch {
            friendRepository.observeFriends().collect { friends ->
                _uiState.update { it.copy(friends = friends) }
                // Auto-select receiver if they are a friend
                val receiverId = _uiState.value.receiverId
                val receiverFriend = friends.find { it.userId == receiverId }
                if (receiverFriend != null && _uiState.value.selectedFriend == null) {
                    onFriendSelected(receiverFriend)
                }
            }
        }
    }

    fun onFriendSelected(friend: Friend?) {
        _uiState.update { it.copy(selectedFriend = friend) }
        if (friend != null) {
            fetchFriendData(friend.userId)
        } else {
            friendCollectionCards = emptyList()
            friendWishlistCards = emptyList()
            updateSearchLists(_uiState.value.addCardsQuery)
        }
    }

    /**
     * Fetches the selected friend's remote wishlist so it can be shown in the
     * receiver-side card picker. The friend's collection is not yet accessible
     * via a dedicated remote endpoint; that list stays empty until a
     * "get_friend_collection" RPC is added to Supabase.
     */
    private fun fetchFriendData(userId: String) {
        viewModelScope.launch(ioDispatcher) {
            wishlistRepository.getRemote(userId)
                .onSuccess { entries ->
                    friendWishlistCards = entries.mapNotNull { it.card }.distinctBy { it.scryfallId }.sortedBy { it.name }
                    // Friend collection cards require a dedicated remote endpoint; left empty for now.
                    friendCollectionCards = emptyList()
                    updateSearchLists(_uiState.value.addCardsQuery)
                }
                .onFailure {
                    // Non-fatal: the picker still works without the friend's wishlist.
                    friendWishlistCards = emptyList()
                    friendCollectionCards = emptyList()
                    updateSearchLists(_uiState.value.addCardsQuery)
                }
        }
    }

    fun onAddCardsQueryChange(query: String) {
        _uiState.update { it.copy(addCardsQuery = query) }
        updateSearchLists(query)
    }

    private fun updateSearchLists(query: String) {
        val state = _uiState.value
        val isFriendSelected = state.selectedFriend != null

        val collectionSource = if (isFriendSelected) friendCollectionCards else collectionCards
        val wishlistSource = if (isFriendSelected) friendWishlistCards else wishlistCards

        val filteredCollection = if (query.isBlank()) {
            collectionSource
        } else {
            collectionSource.filter { it.name.contains(query, ignoreCase = true) }
        }

        val filteredWishlist = if (query.isBlank()) {
            wishlistSource
        } else {
            wishlistSource.filter { it.name.contains(query, ignoreCase = true) }
        }

        val ownedIds = state.collectionIds

        _uiState.update { s ->
            s.copy(
                addCardsResults = filteredCollection.map { card ->
                    AddCardRow(card = card, quantityInDeck = 0, isOwned = card.scryfallId in ownedIds)
                },
                wishlistResults = filteredWishlist.map { card ->
                    AddCardRow(card = card, quantityInDeck = 0, isOwned = card.scryfallId in ownedIds)
                }
            )
        }
    }

    fun showCollectionCards() {
        updateSearchLists("")
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
        return (uiState.value.addCardsResults + uiState.value.scryfallResults + uiState.value.wishlistResults)
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
        // A missing receiver ID would send an empty string to the RPC, which
        // either fails with a cryptic server error or creates a malformed proposal.
        if (!state.isCounterMode && state.receiverId.isBlank()) {
            _uiState.update { it.copy(errorMessage = "NO_RECEIVER") }
            return
        }
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
                        s.copy(isSaving = false, errorMessage = e.toUserFacingMessage() ?: e.message)
                    }
                )
            }
        }
    }

    fun onSendProposal() {
        val state = _uiState.value
        // Guard: a new proposal (not a counter-offer, not an edit) requires a
        // receiver. Without this, an empty-string receiverId reaches the RPC.
        if (!state.isCounterMode && state.editingProposalId == null && state.receiverId.isBlank()) {
            _uiState.update { it.copy(errorMessage = "NO_RECEIVER") }
            return
        }
        if (!validateInitialProposal()) {
            _uiState.update { it.copy(errorMessage = "INITIAL_ASYMMETRY") }
            return
        }
        viewModelScope.launch(ioDispatcher) {
            _uiState.update { it.copy(isSaving = true) }

            val editingId = state.editingProposalId
            val parentId = state.parentProposalId

            // Each branch is handled separately so every Result stays properly typed
            // and no unchecked casts or vacuous .map { it } calls are needed.
            val handleError: (Throwable) -> Unit = { e ->
                val msg = when (e) {
                    is TradeError.ProposalVersionMismatch    -> "PROPOSAL_VERSION_MISMATCH"
                    is TradeError.InitialAsymmetryNotAllowed -> "INITIAL_ASYMMETRY"
                    else                                     -> e.toUserFacingMessage() ?: e.message ?: "Unknown error"
                }
                _uiState.update { it.copy(isSaving = false, errorMessage = msg) }
            }

            when {
                editingId != null -> editProposal(
                    proposalId = editingId,
                    expectedVersion = state.currentVersion,
                    newItems = buildItemRequestDtos(state),
                    newReviewFlags = ReviewFlags(
                        state.includesReviewFromProposer,
                        state.includesReviewFromReceiver,
                    ),
                ).fold(
                    onSuccess = { _uiState.update { it.copy(isSaving = false, navigateBack = true) } },
                    onFailure = handleError,
                )

                parentId != null -> counterProposal(
                    parentProposalId = parentId,
                    items = buildItemRequestDtos(state),
                ).fold(
                    onSuccess = { newId ->
                        _uiState.update { s ->
                            s.copy(
                                isSaving = false,
                                navigateToThread = if (newId.isNotBlank()) Pair(newId, s.rootProposalId) else null,
                                navigateBack = newId.isBlank(),
                            )
                        }
                    },
                    onFailure = handleError,
                )

                else -> createProposal(
                    receiverId = state.receiverId,
                    items = buildItemRequestDtos(state),
                    includesReviewFromProposer = state.includesReviewFromProposer,
                    includesReviewFromReceiver = state.includesReviewFromReceiver,
                    autoSend = true,
                ).fold(
                    onSuccess = { newId ->
                        _uiState.update { s ->
                            s.copy(
                                isSaving = false,
                                navigateToThread = if (newId.isNotBlank()) Pair(newId, newId) else null,
                                navigateBack = newId.isBlank(),
                            )
                        }
                    },
                    onFailure = handleError,
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
