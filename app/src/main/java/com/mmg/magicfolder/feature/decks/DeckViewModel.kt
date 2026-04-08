package com.mmg.magicfolder.feature.decks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.magicfolder.core.domain.model.Deck
import com.mmg.magicfolder.core.domain.model.DeckSummary
import com.mmg.magicfolder.core.domain.repository.DeckRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DeckViewModel @Inject constructor(
    private val deckRepo: DeckRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeckListUiState())
    val uiState: StateFlow<DeckListUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            deckRepo.observeAllDeckSummaries()
                .catch { e -> _uiState.update { it.copy(error = e.message, isLoading = false) } }
                .collect { decks -> _uiState.update { it.copy(decks = decks, isLoading = false) } }
        }
    }

    fun onShowCreateDialog() = _uiState.update { it.copy(showCreateDialog = true) }
    fun onDismissCreateDialog() = _uiState.update { it.copy(showCreateDialog = false) }

    fun createDeck(name: String, format: String = "casual") {
        if (name.isBlank()) return
        viewModelScope.launch {
            runCatching { deckRepo.createDeck(Deck(name = name.trim(), format = format)) }
                .onSuccess  { _uiState.update { it.copy(showCreateDialog = false) } }
                .onFailure  { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    fun deleteDeck(deckId: Long) {
        viewModelScope.launch {
            runCatching { deckRepo.deleteDeck(deckId) }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    fun onErrorDismissed() = _uiState.update { it.copy(error = null) }
}

data class DeckListUiState(
    val decks:            List<DeckSummary> = emptyList(),
    val isLoading:        Boolean           = true,
    val showCreateDialog: Boolean           = false,
    val error:            String?           = null,
)
