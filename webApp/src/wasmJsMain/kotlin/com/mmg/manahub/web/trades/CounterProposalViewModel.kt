package com.mmg.manahub.web.trades

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.data.remote.FriendshipClient
import com.mmg.manahub.core.domain.repository.UserCardRepository
import com.mmg.manahub.core.model.FriendCard
import com.mmg.manahub.core.model.TradeProposal
import com.mmg.manahub.core.model.UserCardWithCard
import com.mmg.manahub.core.domain.repository.FriendRepository
import com.mmg.manahub.feature.trades.domain.usecase.CounterProposalUseCase
import com.mmg.manahub.feature.trades.domain.usecase.GetTradeThreadUseCase
import com.mmg.manahub.core.model.ReviewFlags
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

sealed class CounterProposalEvent {
    data class NavigateToThread(val proposalId: String, val rootProposalId: String) : CounterProposalEvent()
    object NavigateBack : CounterProposalEvent()
}

data class CounterProposalUiState(
    val currentUserId: String = "",
    val counterpartyId: String = "",
    val counterpartyName: String = "",
    val isLoading: Boolean = true,
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
 * Backs [CounterProposalScreen] -- Counter needs the SAME item-picker machinery as
 * [CreateProposalViewModel] (per `project_trades_hub_negotiation.md`'s deferral note: "no separate
 * smaller version exists"), pre-filled from the latest proposal version in the thread instead of
 * starting blank. [parentProposalId] is the id passed to [CounterProposalUseCase] (the LATEST
 * proposal in the chain -- [TradeThreadScreen] only shows the Counter action on that version);
 * [rootProposalId] drives [GetTradeThreadUseCase] the same way [TradeThreadViewModel] does.
 */
class CounterProposalViewModel(
    private val parentProposalId: String,
    private val rootProposalId: String,
    private val supabaseClient: SupabaseClient,
    private val getThread: GetTradeThreadUseCase,
    private val userCardRepository: UserCardRepository,
    private val friendRepository: FriendRepository,
    private val counterProposal: CounterProposalUseCase,
    private val friendshipClient: FriendshipClient,
    private val crashReporter: CrashReporter,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CounterProposalUiState())
    val uiState: StateFlow<CounterProposalUiState> = _uiState.asStateFlow()

    private val _events = Channel<CounterProposalEvent>(Channel.BUFFERED)
    val events: Flow<CounterProposalEvent> = _events.receiveAsFlow()

    private val receiveQueryFlow = MutableStateFlow("")
    private var hasPrefilled = false

    /**
     * MUST be declared BEFORE `init {}` below (real bug, caught live during Trades completion
     * verification -- a genuine Kotlin/Wasm `RuntimeError: dereferencing a null pointer` trap, not
     * a hypothetical). `viewModelScope`'s dispatcher is `Main.immediate`: when `launch {}` is
     * called from a thread already on Main (true here, since the constructor itself runs on Main),
     * the coroutine body runs EAGERLY/SYNCHRONOUSLY up to its first real suspension point --
     * `getThread(rootProposalId).collect {}` hits no suspension before its first (often synchronous,
     * StateFlow-backed) emission, so `tryPrefill()` can run WHILE the constructor is still
     * executing the `init` block, i.e. BEFORE any property declared textually AFTER `init` has run
     * its own initializer. Kotlin property/init-block execution is strictly source-order, so a
     * property referenced from an eagerly-run `init`-block coroutine must be declared ABOVE that
     * `init` block, never below it -- "the class compiled, so ordering doesn't matter" is false for
     * exactly this eager-coroutine-during-construction shape.
     */
    private var latestThread: List<TradeProposal> = emptyList()

    init {
        viewModelScope.launch {
            supabaseClient.auth.sessionStatus.collect { status ->
                if (status is SessionStatus.Authenticated) {
                    val userId = status.session.user?.id ?: return@collect
                    _uiState.update { it.copy(currentUserId = userId) }
                    tryPrefill()
                }
            }
        }
        viewModelScope.launch {
            userCardRepository.observeCollection()
                .distinctUntilChanged()
                .collect { collection -> _uiState.update { it.copy(myCollection = collection.sortedBy { c -> c.card.name }) } }
        }
        viewModelScope.launch {
            getThread(rootProposalId).collect { thread ->
                latestThread = thread
                tryPrefill()
            }
        }
        receiveQueryFlow
            .debounce(300L)
            .distinctUntilChanged()
            .onEach { query -> loadFriendCollection(query) }
            .launchIn(viewModelScope)
    }

    private fun tryPrefill() {
        if (hasPrefilled) return
        val currentUserId = _uiState.value.currentUserId
        if (currentUserId.isBlank()) return
        val proposal = latestThread.find { it.id == parentProposalId } ?: latestThread.maxByOrNull { it.proposalVersion }
        if (proposal == null) return
        hasPrefilled = true

        val counterpartyId = if (proposal.proposerId == currentUserId) proposal.receiverId else proposal.proposerId
        val give = proposal.items
            .filter { it.fromUserId == currentUserId && !it.isReviewCollectionPlaceholder }
            .map { item ->
                TradeItemDraft(
                    cardId = item.cardId,
                    cardName = item.cardName.ifBlank { item.cardId },
                    imageUrl = item.imageUrl,
                    typeLine = item.typeLine,
                    setCode = item.setCode,
                    setName = item.setName,
                    rarity = item.rarity,
                    priceUsd = item.priceUsd,
                    priceEur = item.priceEur,
                    quantity = item.quantity ?: 1,
                    isFoil = item.isFoil ?: false,
                    condition = item.condition ?: "NM",
                    language = item.language ?: "en",
                    userCardIdRef = item.userCardIdRef,
                    maxQuantity = (item.quantity ?: 1).coerceAtLeast(1),
                )
            }
        val receive = proposal.items
            .filter { it.fromUserId == counterpartyId && !it.isReviewCollectionPlaceholder }
            .map { item ->
                TradeItemDraft(
                    cardId = item.cardId,
                    cardName = item.cardName.ifBlank { item.cardId },
                    imageUrl = item.imageUrl,
                    typeLine = item.typeLine,
                    setCode = item.setCode,
                    setName = item.setName,
                    rarity = item.rarity,
                    priceUsd = item.priceUsd,
                    priceEur = item.priceEur,
                    quantity = item.quantity ?: 1,
                    isFoil = item.isFoil ?: false,
                    condition = item.condition ?: "NM",
                    language = item.language ?: "en",
                    userCardIdRef = item.userCardIdRef,
                    maxQuantity = (item.quantity ?: 1).coerceAtLeast(1),
                )
            }
        _uiState.update { it.copy(counterpartyId = counterpartyId, giveItems = give, receiveItems = receive, isLoading = false) }
        resolveCounterpartyName(counterpartyId)
        loadFriendCollectionNow("")
    }

    private fun resolveCounterpartyName(counterpartyId: String) {
        if (counterpartyId.isBlank()) return
        viewModelScope.launch {
            try {
                val profile = friendshipClient.getProfilesByIds(idFilter = "eq.$counterpartyId").firstOrNull()
                val name = profile?.nickname?.takeIf { it.isNotBlank() } ?: profile?.gameTag ?: counterpartyId.take(8)
                _uiState.update { it.copy(counterpartyName = name) }
            } catch (e: Throwable) {
                crashReporter.recordException(e)
                _uiState.update { it.copy(counterpartyName = counterpartyId.take(8)) }
            }
        }
    }

    fun onGiveQueryChanged(query: String) = _uiState.update { it.copy(giveQuery = query) }

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
        val counterpartyId = _uiState.value.counterpartyId
        if (counterpartyId.isBlank()) return
        val result = friendRepository.getFriendCollection(
            friendUserId = counterpartyId,
            list = "collection",
            query = query,
            limit = 60,
        )
        result.fold(
            onSuccess = { cards -> _uiState.update { it.copy(friendCollection = cards, isLoadingFriendCollection = false) } },
            onFailure = { _uiState.update { it.copy(friendCollection = emptyList(), isLoadingFriendCollection = false) } },
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

    fun onSubmit() {
        val state = _uiState.value
        if (state.giveItems.isEmpty() || state.receiveItems.isEmpty()) {
            _uiState.update { it.copy(error = "Add at least one card on each side of the trade.") }
            return
        }
        var acquired = false
        _uiState.update { s -> if (s.isSubmitting) s else { acquired = true; s.copy(isSubmitting = true, error = null) } }
        if (!acquired) return

        viewModelScope.launch {
            val myId = state.currentUserId
            val counterpartyId = state.counterpartyId
            val items = state.giveItems.map { it.toRequestDto(fromUserId = myId, toUserId = counterpartyId) } +
                state.receiveItems.map { it.toRequestDto(fromUserId = counterpartyId, toUserId = myId) }
            counterProposal(parentProposalId = parentProposalId, items = items, reviewFlags = ReviewFlags(false, false))
                .onSuccess { newId ->
                    _uiState.update { it.copy(isSubmitting = false) }
                    if (newId.isNotBlank()) {
                        _events.trySend(CounterProposalEvent.NavigateToThread(newId, rootProposalId))
                    } else {
                        _events.trySend(CounterProposalEvent.NavigateBack)
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(isSubmitting = false, error = e.toUserFacingMessage("send this counter-offer", crashReporter))
                    }
                }
        }
    }
}
