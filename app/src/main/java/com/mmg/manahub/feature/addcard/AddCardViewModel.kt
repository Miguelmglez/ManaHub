package com.mmg.manahub.feature.addcard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.manahub.core.domain.model.Card
import com.mmg.manahub.core.domain.model.DataResult
import com.mmg.manahub.core.domain.usecase.card.SearchCardsUseCase
import com.mmg.manahub.core.domain.usecase.collection.AddCardToCollectionUseCase
import com.mmg.manahub.core.domain.repository.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class AddCardViewModel @Inject constructor(
    private val searchCards:        SearchCardsUseCase,
    private val addToCollection:    AddCardToCollectionUseCase,
    private val userPreferences:    UserPreferencesRepository,
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
                .combine(userPreferences.preferencesFlow) { query, prefs -> query to prefs }
                .collectLatest { (query, prefs) ->
                    _uiState.update { it.copy(preferredCurrency = prefs.preferredCurrency) }
                    if (query.length < 2) {
                        _uiState.update { it.copy(results = emptyList(), isSearching = false) }
                        return@collectLatest
                    }
                    _uiState.update { it.copy(isSearching = true) }
                    val lang = prefs.cardLanguage.code
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
            when (val result = addToCollection(scryfallId = scryfallId, isFoil = isFoil, condition = condition, language = language, quantity = quantity)) {
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

    /** Direct search with a raw Scryfall query (bypasses debounce and lang appending). */
    fun onAdvancedQuerySearch(rawQuery: String) {
        if (rawQuery.isBlank()) return
        _uiState.update { it.copy(query = rawQuery, isSearching = true) }
        viewModelScope.launch {
            when (val result = searchCards(rawQuery)) {
                is DataResult.Success -> _uiState.update {
                    it.copy(results = result.data, isSearching = false, error = null)
                }
                is DataResult.Error -> _uiState.update {
                    it.copy(error = result.message, isSearching = false)
                }
            }
        }
    }

    fun onErrorDismissed()   = _uiState.update { it.copy(error = null) }
    fun onSuccessDismissed() = _uiState.update { it.copy(addedSuccessfully = false) }
}
