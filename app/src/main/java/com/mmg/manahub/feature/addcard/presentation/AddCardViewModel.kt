package com.mmg.manahub.feature.addcard.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.manahub.core.domain.model.AdvancedSearchQuery
import com.mmg.manahub.core.domain.model.Card
import com.mmg.manahub.core.domain.model.DataResult
import com.mmg.manahub.core.domain.repository.UserPreferencesRepository
import com.mmg.manahub.core.domain.usecase.card.SearchCardsUseCase
import com.mmg.manahub.core.domain.usecase.collection.AddCardToCollectionUseCase
import com.mmg.manahub.core.domain.usecase.search.BuildScryfallQueryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class AddCardViewModel @Inject constructor(
    private val searchCards:        SearchCardsUseCase,
    private val addToCollection:    AddCardToCollectionUseCase,
    private val userPreferences:    UserPreferencesRepository,
    private val buildScryfallQuery: BuildScryfallQueryUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddCardUiState())
    val uiState: StateFlow<AddCardUiState> = _uiState.asStateFlow()

    /** Debounce source — only the latest query that survives 400 ms is searched. */
    private val textQueryFlow = MutableStateFlow("")
    private val activeQueryFlow = MutableStateFlow<AdvancedSearchQuery?>(null)

    init {
        viewModelScope.launch {
            combine(
                textQueryFlow.debounce(400L),
                activeQueryFlow,
                userPreferences.preferencesFlow
            ) { text, active, prefs -> Triple(text, active, prefs) }
                .distinctUntilChanged()
                .collectLatest { (text, active, prefs) ->
                    _uiState.update { it.copy(preferredCurrency = prefs.preferredCurrency) }

                    val advancedString = active?.let { buildScryfallQuery(it) } ?: ""
                    val combinedQuery = when {
                        text.isNotBlank() && advancedString.isNotBlank() -> "$text $advancedString"
                        text.isNotBlank() -> text
                        advancedString.isNotBlank() -> advancedString
                        else -> ""
                    }

                    if (combinedQuery.isBlank() || (text.length < 2 && advancedString.isBlank())) {
                        _uiState.update { it.copy(results = emptyList(), isSearching = false) }
                        return@collectLatest
                    }

                    _uiState.update { it.copy(isSearching = true) }
                    val lang = prefs.cardLanguage.code
                    // If combinedQuery already contains a language filter, don't append default
                    val effectiveQuery = if (lang != "en" && !combinedQuery.contains("lang:")) {
                        "$combinedQuery lang:$lang"
                    } else {
                        combinedQuery
                    }

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
        textQueryFlow.value = query
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
            when (val result = addToCollection(scryfallId = scryfallId, isFoil = isFoil, condition = condition, language = language)) {
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
    fun onAdvancedQuerySearch(query: AdvancedSearchQuery, rawQuery: String) {
        _uiState.update { it.copy(activeQuery = query) }
        activeQueryFlow.value = query
    }

    fun onClearFilters() {
        _uiState.update { it.copy(activeQuery = null) }
        activeQueryFlow.value = null
    }

    fun onErrorDismissed()   = _uiState.update { it.copy(error = null) }
    fun onSuccessDismissed() = _uiState.update { it.copy(addedSuccessfully = false) }
}
