package com.mmg.manahub.web.trades

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.domain.repository.FriendRepository
import com.mmg.manahub.core.domain.repository.UserCardRepository
import com.mmg.manahub.core.model.Friend
import com.mmg.manahub.core.model.FriendCard
import com.mmg.manahub.core.model.UserCardWithCard
import com.mmg.manahub.feature.trades.domain.usecase.CreateTradeProposalUseCase
import com.mmg.manahub.web.common.toUserFacingMessage
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The two steps of the [CreateProposalScreen] flow. */
enum class ProposalStep { SELECT_FRIEND, BUILD_ITEMS }

/** One-shot navigation events (buffered [Channel] per CLAUDE.md's Playtest precedent -- a plain
 *  nullable `StateFlow` field would equality-collapse a second identical event and can be missed
 *  across a paused lifecycle). */
sealed class CreateProposalEvent {
    data class NavigateToThread(val proposalId: String) : CreateProposalEvent()
}

data class CreateProposalUiState(
    val step: ProposalStep = ProposalStep.SELECT_FRIEND,
    val currentUserId: String = "",
    val isLoadingFriends: Boolean = true,
    val friends: List<Friend> = emptyList(),
    val selectedFriend: Friend? = null,
    val myCollection: List<UserCardWithCard> = emptyList(),
    val giveItems: List<TradeItemDraft> = emptyList(),
    val receiveItems: List<TradeItemDraft> = emptyList(),
    val isGiveSheetOpen: Boolean = false,
    val isReceiveSheetOpen: Boolean = false,
    val giveQuery: String = "",
    val receiveQuery: String = "",
    val friendCollection: List<FriendCard> = emptyList(),
    val isLoadingFriendCollection: Boolean = false,
    val isSubmitting: Boolean = false,
    val error: String? = null,
)

/**
 * Backs [CreateProposalScreen] -- the single heaviest piece deferred from the original Trades
 * slice (`project_trades_hub_negotiation.md`): creating a brand-new proposal from scratch. A
 * deliberately scoped-down web port of Android's `TradeProposalViewModel`'s create-mode path (see
 * that class's KDoc): no draft/save-for-later distinction (every submit is `autoSend = true`), no
 * review-collection ("gift trade") toggle, no Scryfall-direct search or wishlist/offer match
 * suggestions -- just the two real item sources the task brief called for: the caller's own
 * collection ([UserCardRepository.observeCollection]) and the selected friend's collection
 * ([FriendRepository.getFriendCollection], `list = "collection"`).
 */
class CreateProposalViewModel(
    private val supabaseClient: SupabaseClient,
    private val friendRepository: FriendRepository,
    private val userCardRepository: UserCardRepository,
    private val createTradeProposal: CreateTradeProposalUseCase,
    private val crashReporter: CrashReporter,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateProposalUiState())
    val uiState: StateFlow<CreateProposalUiState> = _uiState.asStateFlow()

    private val _events = Channel<CreateProposalEvent>(Channel.BUFFERED)
    val events: Flow<CreateProposalEvent> = _events.receiveAsFlow()

    private val receiveQueryFlow = MutableStateFlow("")

    init {
        viewModelScope.launch {
            supabaseClient.auth.sessionStatus.collect { status ->
                if (status is SessionStatus.Authenticated) {
                    val userId = status.session.user?.id ?: return@collect
                    _uiState.update { it.copy(currentUserId = userId) }
                }
            }
        }
        viewModelScope.launch {
            friendRepository.observeFriends().collect { friends ->
                _uiState.update { it.copy(friends = friends, isLoadingFriends = false) }
            }
        }
        viewModelScope.launch {
            userCardRepository.observeCollection()
                .distinctUntilChanged()
                .collect { collection ->
                    _uiState.update { it.copy(myCollection = collection.sortedBy { c -> c.card.name }) }
                }
        }
        receiveQueryFlow
            .debounce(300L)
            .distinctUntilChanged()
            .onEach { query -> loadFriendCollection(query) }
            .launchIn(viewModelScope)
    }

    fun onFriendSelected(friend: Friend) {
        _uiState.update { it.copy(selectedFriend = friend, step = ProposalStep.BUILD_ITEMS) }
        receiveQueryFlow.value = ""
        loadFriendCollectionNow("")
    }

    fun onBackToFriendSelect() {
        _uiState.update {
            it.copy(
                step = ProposalStep.SELECT_FRIEND,
                selectedFriend = null,
                giveItems = emptyList(),
                receiveItems = emptyList(),
                friendCollection = emptyList(),
                giveQuery = "",
                receiveQuery = "",
                error = null,
            )
        }
    }

    fun onGiveQueryChanged(query: String) {
        _uiState.update { it.copy(giveQuery = query) }
    }

    fun onReceiveQueryChanged(query: String) {
        _uiState.update { it.copy(receiveQuery = query, isLoadingFriendCollection = true) }
        receiveQueryFlow.value = query
    }

    fun openGiveSheet() = _uiState.update { it.copy(isGiveSheetOpen = true) }
    fun closeGiveSheet() = _uiState.update { it.copy(isGiveSheetOpen = false) }
    fun openReceiveSheet() = _uiState.update { it.copy(isReceiveSheetOpen = true) }
    fun closeReceiveSheet() = _uiState.update { it.copy(isReceiveSheetOpen = false) }

    private fun loadFriendCollectionNow(query: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingFriendCollection = true) }
            loadFriendCollection(query)
        }
    }

    private suspend fun loadFriendCollection(query: String) {
        val friendId = _uiState.value.selectedFriend?.userId ?: return
        val result = friendRepository.getFriendCollection(
            friendUserId = friendId,
            list = "collection",
            query = query,
            limit = 60,
        )
        result.fold(
            onSuccess = { cards ->
                _uiState.update { it.copy(friendCollection = cards, isLoadingFriendCollection = false) }
            },
            onFailure = {
                _uiState.update { it.copy(friendCollection = emptyList(), isLoadingFriendCollection = false) }
            },
        )
    }

    fun addGiveItem(userCard: UserCardWithCard) {
        val draft = TradeItemDraft(
            cardId = userCard.card.scryfallId,
            cardName = userCard.card.name,
            imageUrl = userCard.card.imageArtCrop ?: userCard.card.imageNormal,
            typeLine = userCard.card.typeLine,
            setCode = userCard.card.setCode,
            setName = userCard.card.setName,
            rarity = userCard.card.rarity,
            priceUsd = if (userCard.userCard.isFoil) userCard.card.priceUsdFoil else userCard.card.priceUsd,
            priceEur = if (userCard.userCard.isFoil) userCard.card.priceEurFoil else userCard.card.priceEur,
            quantity = 1,
            isFoil = userCard.userCard.isFoil,
            condition = userCard.userCard.condition,
            language = userCard.userCard.language,
            userCardIdRef = userCard.userCard.id,
            maxQuantity = userCard.userCard.quantity,
        )
        _uiState.update { it.copy(giveItems = mergeTradeItemDraft(it.giveItems, draft)) }
    }

    fun removeGiveItem(draft: TradeItemDraft) {
        _uiState.update { it.copy(giveItems = it.giveItems.filterNot { d -> d == draft }) }
    }

    fun addReceiveItem(friendCard: FriendCard) {
        val draft = TradeItemDraft(
            cardId = friendCard.scryfallId,
            cardName = friendCard.name,
            imageUrl = friendCard.imageArtCrop ?: friendCard.imageNormal,
            typeLine = friendCard.typeLine,
            setCode = friendCard.setCode,
            setName = friendCard.setName,
            rarity = friendCard.rarity,
            priceUsd = if (friendCard.isFoil) friendCard.priceUsdFoil ?: friendCard.priceUsd else friendCard.priceUsd,
            priceEur = if (friendCard.isFoil) friendCard.priceEurFoil ?: friendCard.priceEur else friendCard.priceEur,
            quantity = 1,
            isFoil = friendCard.isFoil,
            condition = friendCard.condition ?: "NM",
            language = friendCard.language ?: "en",
            userCardIdRef = null,
            maxQuantity = friendCard.quantity,
        )
        _uiState.update { it.copy(receiveItems = mergeTradeItemDraft(it.receiveItems, draft)) }
    }

    fun removeReceiveItem(draft: TradeItemDraft) {
        _uiState.update { it.copy(receiveItems = it.receiveItems.filterNot { d -> d == draft }) }
    }

    fun onErrorDismissed() = _uiState.update { it.copy(error = null) }

    /** Atomic capture-and-flip double-tap guard -- same pattern as [TradeThreadViewModel.runGuarded]. */
    fun onSubmit() {
        val state = _uiState.value
        val friend = state.selectedFriend
        if (friend == null) {
            _uiState.update { it.copy(error = "Select a friend first.") }
            return
        }
        if (state.giveItems.isEmpty() || state.receiveItems.isEmpty()) {
            _uiState.update { it.copy(error = "Add at least one card on each side of the trade.") }
            return
        }
        var acquired = false
        _uiState.update { s -> if (s.isSubmitting) s else { acquired = true; s.copy(isSubmitting = true, error = null) } }
        if (!acquired) return

        viewModelScope.launch {
            val myId = state.currentUserId
            val items = state.giveItems.map { it.toRequestDto(fromUserId = myId, toUserId = friend.userId) } +
                state.receiveItems.map { it.toRequestDto(fromUserId = friend.userId, toUserId = myId) }
            createTradeProposal(receiverId = friend.userId, items = items, autoSend = true)
                .onSuccess { proposalId ->
                    _uiState.update { it.copy(isSubmitting = false) }
                    _events.trySend(CreateProposalEvent.NavigateToThread(proposalId))
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(isSubmitting = false, error = e.toUserFacingMessage("send this proposal", crashReporter))
                    }
                }
        }
    }
}
