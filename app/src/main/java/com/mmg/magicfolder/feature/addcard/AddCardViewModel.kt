package com.mmg.magicfolder.feature.addcard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.magicfolder.core.data.local.LanguagePreference
import com.mmg.magicfolder.core.domain.model.Card
import com.mmg.magicfolder.core.domain.model.DataResult
import com.mmg.magicfolder.core.domain.usecase.card.SearchCardsUseCase
import com.mmg.magicfolder.core.domain.usecase.collection.AddCardToCollectionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(FlowPreview::class)
@HiltViewModel
class AddCardViewModel @Inject constructor(
    private val searchCards:     SearchCardsUseCase,
    private val addToCollection: AddCardToCollectionUseCase,
    private val langPref:        LanguagePreference,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddCardUiState())
    val uiState: StateFlow<AddCardUiState> = _uiState.asStateFlow()

    /** Debounce source — only the latest query that survives 400 ms is searched. */
    private val queryFlow = MutableStateFlow("")

    init {
        viewModelScope.launch {
            queryFlow
                .debounce(400L)
                .distinctUntilChanged()
                .collectLatest { query ->
                    if (query.length < 2) {
                        _uiState.update { it.copy(results = emptyList(), isSearching = false) }
                        return@collectLatest
                    }
                    _uiState.update { it.copy(isSearching = true) }
                    val lang = runCatching { langPref.languageFlow.first() }.getOrDefault("en")
                    val effectiveQuery = if (lang != "en") "$query lang:$lang" else query
                    when (val result = searchCards(effectiveQuery)) {
                        is DataResult.Success -> _uiState.update {
                            it.copy(results = result.data, isSearching = false, error = null)
                        }
                        is DataResult.Error -> _uiState.update {
                            it.copy(error = result.message, isSearching = false)
                        }
                    }
                }
        }
    }

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }
        queryFlow.value = query
    }

    fun onCardSelected(card: Card) {
        _uiState.update { it.copy(selectedCard = card, showConfirmSheet = true) }
    }

    fun onConfirmAdd(
        scryfallId: String,
        isFoil:     Boolean,
        condition:  String,
        language:   String,
        quantity:   Int,
    ) {
        viewModelScope.launch {
            when (val result = addToCollection(scryfallId, isFoil, condition, language, quantity)) {
                is DataResult.Success -> _uiState.update {
                    it.copy(
                        showConfirmSheet  = false,
                        selectedCard      = null,
                        addedSuccessfully = true,
                        error             = null,
                    )
                }
                is DataResult.Error -> _uiState.update {
                    it.copy(error = result.message, showConfirmSheet = false)
                }
            }
        }
    }

    fun onDismissConfirmSheet() {
        _uiState.update { it.copy(showConfirmSheet = false, selectedCard = null) }
    }

    fun onErrorDismissed()   = _uiState.update { it.copy(error = null) }
    fun onSuccessDismissed() = _uiState.update { it.copy(addedSuccessfully = false) }
}
