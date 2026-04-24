package com.mmg.manahub.feature.decks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.manahub.core.domain.model.DeckSummary
import com.mmg.manahub.core.domain.repository.DeckRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DeckViewModel @Inject constructor(
    private val deckRepo: DeckRepository,
    private val cardRepo: com.mmg.manahub.core.domain.repository.CardRepository,
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

    fun onShowImportSheet() = _uiState.update { it.copy(showImportSheet = true) }
    fun onDismissImportSheet() = _uiState.update { it.copy(showImportSheet = false, importError = null) }

    fun createDeck(name: String, format: String = "casual") {
        if (name.isBlank()) return
        viewModelScope.launch {
            runCatching { deckRepo.createDeck(name = name.trim(), description = "", format = format) }
                .onSuccess { _uiState.update { it.copy(showCreateDialog = false) } }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    fun onCreatedDeckNavigated() {
        _uiState.update { it.copy(createdDeckId = null) }
    }

    fun deleteDeck(deckId: String) {
        viewModelScope.launch {
            runCatching { deckRepo.deleteDeck(deckId) }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    fun importDeck(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true, importError = null) }
            try {
                val parsed = com.mmg.manahub.feature.decks.engine.DeckImportExportHelper.parse(text)
                val deckId = deckRepo.createDeck(name = "Imported Deck", description = "", format = "casual")

                val mainboard = parsed.mainboard
                val sideboard = parsed.sideboard
                val commander = parsed.commander

                for (line in mainboard) {
                    addParsedLineToDeck(deckId, line, isSideboard = false)
                }
                for (line in sideboard) {
                    addParsedLineToDeck(deckId, line, isSideboard = true)
                }
                commander?.let {
                    addParsedLineToDeck(deckId, it, isSideboard = false)
                }

                _uiState.update { it.copy(isImporting = false, showImportSheet = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isImporting = false, importError = e.message) }
            }
        }
    }

    private suspend fun addParsedLineToDeck(
        deckId: String,
        line: com.mmg.manahub.feature.decks.engine.DeckImportExportHelper.ParsedLine,
        isSideboard: Boolean,
    ) {
        val result = cardRepo.searchCardByName(line.name)
        if (result is com.mmg.manahub.core.domain.model.DataResult.Success) {
            deckRepo.addCardToDeck(
                deckId      = deckId,
                scryfallId  = result.data.scryfallId,
                quantity    = line.quantity,
                isSideboard = isSideboard,
            )
        }
    }

    fun onErrorDismissed() = _uiState.update { it.copy(error = null) }
}

data class DeckListUiState(
    val decks:            List<DeckSummary> = emptyList(),
    val isLoading:        Boolean           = true,
    val showCreateDialog: Boolean           = false,
    val showImportSheet:  Boolean           = false,
    val isImporting:      Boolean           = false,
    val importError:      String?           = null,
    val error:            String?           = null,
    val createdDeckId:    String?           = null,
)
