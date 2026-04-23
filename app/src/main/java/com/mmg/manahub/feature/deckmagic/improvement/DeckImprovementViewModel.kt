package com.mmg.manahub.feature.deckmagic.improvement

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.domain.repository.DeckRepository
import com.mmg.manahub.core.domain.repository.UserCardRepository
import com.mmg.manahub.feature.deckmagic.engine.DeckMagicEngine
import com.mmg.manahub.feature.deckmagic.engine.ImprovementSuggestion
import com.mmg.manahub.feature.deckmagic.engine.SuggestionActionType
import com.mmg.manahub.feature.decks.DeckSlotEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DeckImprovementViewModel @Inject constructor(
    private val deckRepository: DeckRepository,
    private val cardRepository: CardRepository,
    private val userCardRepository: UserCardRepository,
    private val magicEngine: DeckMagicEngine,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val deckId: Long = checkNotNull(savedStateHandle["deckId"])

    private val _uiState = MutableStateFlow(DeckImprovementUiState())
    val uiState: StateFlow<DeckImprovementUiState> = _uiState.asStateFlow()

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

            val deckEntries = (deckWithCards.mainboard.map { slot ->
                val card = when (val res = cardRepository.getCardById(slot.scryfallId)) {
                    is com.mmg.manahub.core.domain.model.DataResult.Success -> res.data
                    else -> null
                }
                DeckSlotEntry(slot.scryfallId, slot.quantity, false, card)
            } + deckWithCards.sideboard.map { slot ->
                val card = when (val res = cardRepository.getCardById(slot.scryfallId)) {
                    is com.mmg.manahub.core.domain.model.DataResult.Success -> res.data
                    else -> null
                }
                DeckSlotEntry(slot.scryfallId, slot.quantity, true, card)
            })

            val collection = userCardRepository.observeCollection().first()
            val format = com.mmg.manahub.core.domain.model.DeckFormat.valueOf(deckWithCards.deck.format.uppercase())
            
            val report = magicEngine.analyzeDeck(deckEntries, collection, format)
            
            _uiState.update { 
                it.copy(
                    deckName = deckWithCards.deck.name,
                    report = report,
                    isLoading = false
                )
            }
        }
    }

    fun applySuggestion(suggestion: ImprovementSuggestion) {
        viewModelScope.launch {
            val scryfallId = suggestion.magicCard.card.scryfallId
            when (suggestion.actionType) {
                SuggestionActionType.ADD_FROM_COLLECTION -> {
                    deckRepository.addCardToDeck(deckId, scryfallId, 1, false)
                }
                SuggestionActionType.SWAP_FROM_SIDEBOARD -> {
                    // Move from side to main
                    deckRepository.removeCardFromDeck(deckId, scryfallId, true)
                    deckRepository.addCardToDeck(deckId, scryfallId, 1, false)
                    
                    // If swapFor is provided, move it to sideboard
                    suggestion.swapFor?.let { 
                        deckRepository.removeCardFromDeck(deckId, it.card.scryfallId, false)
                        deckRepository.addCardToDeck(deckId, it.card.scryfallId, 1, true)
                    }
                }
            }
            _uiState.update { it.copy(appliedSuggestions = it.appliedSuggestions + scryfallId) }
            // Re-analyze after change
            loadAnalysis()
        }
    }
}
