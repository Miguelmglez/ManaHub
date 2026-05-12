package com.mmg.manahub.feature.decks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.manahub.core.domain.model.CardTag
import com.mmg.manahub.core.domain.repository.UserCardRepository
import com.mmg.manahub.feature.decks.engine.DeckMagicEngine
import com.mmg.manahub.feature.decks.engine.GameFormat
import com.mmg.manahub.feature.decks.engine.MagicCard
import com.mmg.manahub.feature.decks.engine.MagicDiscovery
import com.mmg.manahub.feature.decks.engine.MagicSuggestion
import com.mmg.manahub.feature.decks.engine.ManaColor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class DeckMagicStep { DASHBOARD, SETUP, BUILDING, REVIEW }

data class DeckMagicUiState(
    val step: DeckMagicStep = DeckMagicStep.DASHBOARD,
    val isLoading: Boolean = true,
    
    // Discovery
    val discoveries: List<MagicDiscovery> = emptyList(),
    
    // Setup
    val deckName: String = "",
    val format: GameFormat = GameFormat.CASUAL,
    val selectedColors: Set<ManaColor> = emptySet(),
    val targetTags: List<CardTag> = emptyList(),
    
    // Building
    val currentSuggestions: List<MagicSuggestion> = emptyList(),
    val mainboard: List<MagicCard> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class DeckMagicViewModel @Inject constructor(
    private val userCardRepo: UserCardRepository,
    private val engine: DeckMagicEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeckMagicUiState())
    val uiState: StateFlow<DeckMagicUiState> = _uiState.asStateFlow()

    init {
        loadDiscoveries()
    }

    private fun loadDiscoveries() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val collection = userCardRepo.observeCollection().first()
            val discoveries = engine.discoverSynergies(collection)
            _uiState.update { it.copy(discoveries = discoveries, isLoading = false) }
        }
    }

    fun startFromDiscovery(discovery: MagicDiscovery) {
        _uiState.update { 
            it.copy(
                step = DeckMagicStep.BUILDING,
                targetTags = listOf(discovery.primaryTag),
                mainboard = discovery.cards.take(1) // Seed with the first card
            )
        }
        updateSuggestions()
    }

    fun setStep(step: DeckMagicStep) {
        _uiState.update { it.copy(step = step) }
    }

    fun toggleColor(color: ManaColor) {
        _uiState.update { s ->
            val colors = if (color in s.selectedColors) s.selectedColors - color
                         else s.selectedColors + color
            s.copy(selectedColors = colors)
        }
        updateSuggestions()
    }

    fun addCard(card: MagicCard) {
        _uiState.update { it.copy(mainboard = it.mainboard + card) }
        updateSuggestions()
    }

    private fun updateSuggestions() {
        viewModelScope.launch {
            val s = _uiState.value
            val collection = userCardRepo.observeCollection().first()
            val suggestions = engine.getSuggestions(
                collection = collection,
                targetTags = s.targetTags,
                selectedColors = s.selectedColors,
                mainboard = s.mainboard,
                format = s.format
            )
            _uiState.update { it.copy(currentSuggestions = suggestions) }
        }
    }
}
















