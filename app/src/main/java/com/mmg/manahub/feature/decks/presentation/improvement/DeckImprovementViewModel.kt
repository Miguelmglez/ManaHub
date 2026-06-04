package com.mmg.manahub.feature.decks.presentation.improvement

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.manahub.core.domain.model.Card
import com.mmg.manahub.core.domain.model.DataResult
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.domain.repository.DeckRepository
import com.mmg.manahub.core.domain.repository.UserCardRepository
import com.mmg.manahub.feature.decks.domain.usecase.BudgetConstraints
import com.mmg.manahub.feature.decks.domain.usecase.EvaluateDeckUseCase
import com.mmg.manahub.feature.decks.domain.usecase.SuggestAddsWithBudgetUseCase
import com.mmg.manahub.feature.decks.domain.usecase.SuggestCutsUseCase
import com.mmg.manahub.feature.decks.presentation.engine.DeckEntry
import com.mmg.manahub.feature.decks.presentation.engine.DeckProfile
import com.mmg.manahub.feature.decks.presentation.engine.DeckEvaluation
import com.mmg.manahub.feature.trades.domain.repository.WishlistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * One-shot UI events emitted by [DeckImprovementViewModel]. A buffered [Channel] is used (not a
 * nullable StateFlow) so repeated identical toasts are never equality-collapsed or dropped while
 * the lifecycle is paused. The event carries the structured data (card name); the UI layer turns
 * it into a localized [com.mmg.manahub.core.ui.components.MagicToast] message.
 */
sealed interface DeckImprovementEvent {
    /** A card was removed from the deck. */
    data class CardCut(val cardName: String) : DeckImprovementEvent

    /** A card was added to the deck. */
    data class CardAdded(val cardName: String) : DeckImprovementEvent

    /**
     * The external (Scryfall) candidate fetch failed; suggestions fell back to collection + wishlist.
     * The UI surfaces this as a non-fatal warning toast.
     */
    data object ExternalPoolFailed : DeckImprovementEvent
}

@HiltViewModel
class DeckImprovementViewModel @Inject constructor(
    private val deckRepository: DeckRepository,
    private val cardRepository: CardRepository,
    private val userCardRepository: UserCardRepository,
    private val evaluateDeckUseCase: EvaluateDeckUseCase,
    private val suggestCutsUseCase: SuggestCutsUseCase,
    private val suggestAddsWithBudgetUseCase: SuggestAddsWithBudgetUseCase,
    private val wishlistRepository: WishlistRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val deckId: String = checkNotNull(savedStateHandle["deckId"])
    private val _uiState = MutableStateFlow(DeckImprovementUiState())
    val uiState: StateFlow<DeckImprovementUiState> = _uiState.asStateFlow()

    private val _events = Channel<DeckImprovementEvent>(Channel.BUFFERED)
    val events: Flow<DeckImprovementEvent> = _events.receiveAsFlow()

    /**
     * Cached inputs for the ADD pipeline so a budget change can recompute suggestions WITHOUT
     * re-running the whole deck analysis. Populated by [loadAnalysis]; null until the first run.
     */
    private var addContext: AddContext? = null

    /** Immutable snapshot of everything the ADD pipeline needs to recompute on a budget change. */
    private data class AddContext(
        val collection: List<Card>,
        val wishlistIds: Set<String>,
        val mainboardIds: Set<String>,
        val profile: DeckProfile,
        val evaluation: DeckEvaluation,
    )

    init {
        loadAnalysis()
    }

    private fun loadAnalysis() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            val deckWithCards = deckRepository.observeDeckWithCards(deckId).first()
            if (deckWithCards == null) {
                _uiState.update { it.copy(isLoading = false, error = "Deck not found") }
                return@launch
            }

            val collection = userCardRepository.observeCollection().first()
            val deckFormat = com.mmg.manahub.core.domain.model.DeckFormat.valueOf(deckWithCards.deck.format.uppercase())
            // DeckScorer-based Health evaluation. Only mainboard slots with a resolved card
            // participate; the engine ignores ownership, so isOwned is left at false here.
            val mainboardEntries = deckWithCards.mainboard.mapNotNull { slot ->
                resolveCard(slot.scryfallId)?.let { card ->
                    DeckEntry(card = card, quantity = slot.quantity, isOwned = false, isSideboard = false)
                }
            }
            val commanderId = deckWithCards.deck.commanderCardId
            val commanderIdentity = commanderId
                ?.let { resolveCard(it)?.colorIdentity?.toSet() }
                .orEmpty()
            val health = evaluateDeckUseCase(
                mainboard = mainboardEntries,
                format = deckFormat,
                commanderIdentity = commanderIdentity,
            )

            // Cut + Add suggestions reuse the SAME DeckProfile the Health view was built from.
            val protectedIds = setOfNotNull(commanderId)
            val cuts = suggestCutsUseCase(
                mainboard = mainboardEntries,
                profile = health.profile,
                protectedIds = protectedIds,
            )

            // Cache the inputs the ADD pipeline needs so a budget change can recompute cheaply.
            val mainboardIds = deckWithCards.mainboard.map { it.scryfallId }.toSet()
            val collectionCards = collection.map { it.card }
            val wishlistIds = wishlistRepository.observeLocal().first()
                .map { it.cardId }
                .toSet()
            addContext = AddContext(
                collection = collectionCards,
                wishlistIds = wishlistIds,
                mainboardIds = mainboardIds,
                profile = health.profile,
                evaluation = health.evaluation,
            )

            // Render Health + Cut immediately; the ADD pipeline (network) fills in asynchronously.
            _uiState.update {
                it.copy(
                    deckName = deckWithCards.deck.name,
                    health = health,
                    cuts = cuts,
                    isLoading = false,
                )
            }
            recomputeAdds()
        }
    }

    /**
     * Recomputes the ADD suggestions from [addContext] using the current [DeckImprovementUiState.budget].
     * Runs the collection + wishlist + external pipeline; on a Scryfall failure the pipeline already
     * falls back to collection + wishlist, so this never crashes — it only emits a warning toast when
     * the external pool comes back empty due to an error. Safe to call repeatedly (budget changes).
     */
    private fun recomputeAdds() {
        val context = addContext ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isAddsLoading = true) }
            val constraints = _uiState.value.budget
            val selection = runCatching {
                suggestAddsWithBudgetUseCase(
                    collection = context.collection,
                    wishlistIds = context.wishlistIds,
                    mainboardIds = context.mainboardIds,
                    profile = context.profile,
                    evaluation = context.evaluation,
                    constraints = constraints,
                )
            }.getOrNull()

            if (selection == null) {
                // The whole pipeline failed (should be rare — external errors are absorbed inside it).
                _uiState.update { it.copy(isAddsLoading = false) }
                _events.send(DeckImprovementEvent.ExternalPoolFailed)
                return@launch
            }

            _uiState.update {
                it.copy(
                    adds = selection.selected,
                    addsTotalCostEur = selection.totalCostEur,
                    addsCardsToBuy = selection.cardsToBuy,
                    isAddsLoading = false,
                )
            }
        }
    }

    /** Updates the budget filters and recomputes the ADD suggestions (no full re-analysis). */
    fun onBudgetChanged(budget: BudgetConstraints) {
        _uiState.update { it.copy(budget = budget) }
        recomputeAdds()
    }

    /** Resolves a Scryfall id to a full [Card], or null when it is not available. */
    private suspend fun resolveCard(scryfallId: String): Card? =
        when (val res = cardRepository.getCardById(scryfallId)) {
            is DataResult.Success -> res.data
            else -> null
        }

    /** Switches the active Deck Doctor tab (Health / Cut / Add). */
    fun onTabSelected(tab: DeckDoctorTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    /**
     * Removes a cut-candidate card from the deck's mainboard, then re-runs the analysis so the
     * Health/Cut/Add views reflect the change.
     */
    fun onCut(scryfallId: String, cardName: String) {
        viewModelScope.launch {
            deckRepository.removeCardFromDeck(deckId, scryfallId, isSideboard = false)
            _events.send(DeckImprovementEvent.CardCut(cardName))
            loadAnalysis()
        }
    }

    /**
     * Adds a suggested card (one copy) to the deck's mainboard, then re-runs the analysis.
     */
    fun onAdd(scryfallId: String, cardName: String) {
        viewModelScope.launch {
            deckRepository.addCardToDeck(deckId, scryfallId, 1, false)
            _events.send(DeckImprovementEvent.CardAdded(cardName))
            loadAnalysis()
        }
    }
}














