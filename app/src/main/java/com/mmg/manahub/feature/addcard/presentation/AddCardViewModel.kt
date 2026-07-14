package com.mmg.manahub.feature.addcard.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.manahub.core.model.AdvancedSearchQuery
import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.domain.repository.UserPreferencesRepository
import com.mmg.manahub.core.domain.usecase.card.GetSpotlightFeedUseCase
import com.mmg.manahub.core.domain.usecase.card.SearchCardsUseCase
import com.mmg.manahub.core.domain.usecase.search.BuildScryfallQueryUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class AddCardViewModel(
    private val searchCards:        SearchCardsUseCase,
    private val userPreferences:    UserPreferencesRepository,
    private val buildScryfallQuery: BuildScryfallQueryUseCase,
    private val getSpotlightFeed:   GetSpotlightFeedUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddCardUiState())
    val uiState: StateFlow<AddCardUiState> = _uiState.asStateFlow()

    private val textQueryFlow  = MutableStateFlow("")
    private val activeQueryFlow = MutableStateFlow<AdvancedSearchQuery?>(null)

    private val forceSearchTrigger = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private var lastEffectiveQuery: String? = null
    private var currentSpotlightSetIndex: Int = 0
    private var hasMoreSpotlightSets: Boolean = true

    init {
        viewModelScope.launch {
            val dataFlow = combine(
                textQueryFlow.debounce(400L),
                activeQueryFlow,
                userPreferences.preferencesFlow,
            ) { text, active, prefs ->
                Triple(text, active, prefs)
            }.distinctUntilChanged()

            combine(
                dataFlow,
                forceSearchTrigger.onStart { emit(Unit) }
            ) { data, _ -> data }
                .collectLatest { (text, active, prefs) ->
                    _uiState.update {
                        it.copy(
                            preferredCurrency = prefs.preferredCurrency,
                            searchLanguage = prefs.cardLanguage.toScryfallCode(),
                        )
                    }

                    val advancedString = active?.let { buildScryfallQuery(it) } ?: ""
                    val combinedQuery = when {
                        text.isNotBlank() && advancedString.isNotBlank() -> "$text $advancedString"
                        text.isNotBlank()                                -> text
                        advancedString.isNotBlank()                      -> advancedString
                        else                                             -> ""
                    }

                    if (combinedQuery.isBlank() || (text.length < 2 && advancedString.isBlank())) {
                        _uiState.update { 
                            it.copy(
                                results = emptyList(), 
                                isSearching = false,
                                isLoadingMore = false,
                                hasMore = false,
                                currentPage = 1,
                                error = null
                            ) 
                        }
                        lastEffectiveQuery = null
                        return@collectLatest
                    }

                    _uiState.update { 
                        it.copy(
                            isSearching = true, 
                            error = null,
                            isLoadingMore = false,
                            hasMore = false,
                            currentPage = 1
                            // We don't clear results here to keep stale results visible (F-08)
                        ) 
                    }
                    val lang = _uiState.value.searchLanguage
                    val effectiveQuery = if (lang != "en" && !hasLangToken(combinedQuery)) {
                        "$combinedQuery lang:$lang"
                    } else {
                        combinedQuery
                    }
                    lastEffectiveQuery = effectiveQuery

                    when (val result = searchCards(effectiveQuery, page = 1)) {
                        is DataResult.Success -> {
                            _uiState.update {
                                it.copy(
                                    results = result.data.cards,
                                    isSearching = false,
                                    error = null,
                                    hasMore = result.data.hasMore,
                                    currentPage = 1
                                )
                            }
                        }
                        is DataResult.Error -> _uiState.update {
                            it.copy(error = result.message, isSearching = false)
                        }
                    }
                }
                
            // Launch spotlight feed fetch
            loadSpotlightFeed()
        }
    }

    private fun hasLangToken(query: String): Boolean {
        return query.split("\\s+".toRegex()).any { it.startsWith("lang:") }
    }

    fun forceSearch() {
        forceSearchTrigger.tryEmit(Unit)
    }

    fun loadNextPage() {
        val query = lastEffectiveQuery ?: return
        val currentState = _uiState.value
        
        if (!currentState.hasMore || currentState.isLoadingMore || currentState.isSearching) return
        
        val nextPage = currentState.currentPage + 1
        _uiState.update { it.copy(isLoadingMore = true) }
        
        viewModelScope.launch {
            when (val result = searchCards(query, nextPage)) {
                is DataResult.Success -> _uiState.update {
                    it.copy(
                        results = it.results + result.data.cards,
                        isLoadingMore = false,
                        hasMore = result.data.hasMore,
                        currentPage = nextPage
                    )
                }
                is DataResult.Error -> _uiState.update {
                    it.copy(isLoadingMore = false, error = result.message)
                }
            }
        }
    }

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }
        textQueryFlow.value = query
    }

    fun onAdvancedQuerySearch(query: AdvancedSearchQuery) {
        _uiState.update { it.copy(activeQuery = query) }
        activeQueryFlow.value = query
    }

    fun onClearFilters() {
        _uiState.update { it.copy(activeQuery = null) }
        activeQueryFlow.value = null
    }

    fun onClearAll() {
        _uiState.update {
            it.copy(
                query = "",
                activeQuery = null,
                results = emptyList(),
                isSearching = false,
                isLoadingMore = false,
                hasMore = false,
                currentPage = 1,
                error = null
            )
        }
        textQueryFlow.value = ""
        activeQueryFlow.value = null
        lastEffectiveQuery = null
    }

    fun onLanguageChange(code: String) {
        viewModelScope.launch {
            val lang = when (code) {
                "es"  -> com.mmg.manahub.core.model.CardLanguage.SPANISH
                "de"  -> com.mmg.manahub.core.model.CardLanguage.GERMAN
                "fr"  -> com.mmg.manahub.core.model.CardLanguage.FRENCH
                "it"  -> com.mmg.manahub.core.model.CardLanguage.ITALIAN
                "pt"  -> com.mmg.manahub.core.model.CardLanguage.PORTUGUESE
                "ja"  -> com.mmg.manahub.core.model.CardLanguage.JAPANESE
                "ko"  -> com.mmg.manahub.core.model.CardLanguage.KOREAN
                "ru"  -> com.mmg.manahub.core.model.CardLanguage.RUSSIAN
                "zhs" -> com.mmg.manahub.core.model.CardLanguage.CHINESE_SIMPLIFIED
                "zht" -> com.mmg.manahub.core.model.CardLanguage.CHINESE_TRADITIONAL
                else  -> com.mmg.manahub.core.model.CardLanguage.ENGLISH
            }
            userPreferences.setCardLanguage(lang)
        }
    }

    fun onErrorDismissed() = _uiState.update { it.copy(error = null) }

    fun loadSpotlightFeed() {
        val currentState = _uiState.value
        if (currentState.isSpotlightLoading || !hasMoreSpotlightSets) return
        if (currentState.query.length >= 2 || currentState.activeQuery != null) return // Only load if idle
        
        _uiState.update { it.copy(isSpotlightLoading = true) }
        viewModelScope.launch {
            when (val result = getSpotlightFeed(currentSpotlightSetIndex)) {
                is DataResult.Success -> {
                    _uiState.update { 
                        it.copy(
                            spotlightCards = it.spotlightCards + result.data.cards,
                            spotlightSet = result.data.sourceSet,
                            isSpotlightLoading = false
                        )
                    }
                    currentSpotlightSetIndex = result.data.nextSetIndex
                }
                is DataResult.Error -> {
                    _uiState.update { it.copy(isSpotlightLoading = false) }
                    if (result.message == "No more sets available") {
                        hasMoreSpotlightSets = false
                    }
                }
            }
        }
    }
}
