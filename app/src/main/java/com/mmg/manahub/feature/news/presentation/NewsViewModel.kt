package com.mmg.manahub.feature.news.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.feature.news.domain.model.ContentSource
import com.mmg.manahub.core.domain.model.news.NewsFilterPrefs
import com.mmg.manahub.core.domain.model.news.NewsItem
import com.mmg.manahub.core.domain.model.news.SourceType
import com.mmg.manahub.feature.news.domain.usecase.GetNewsFeedUseCase
import com.mmg.manahub.feature.news.domain.usecase.ManageSourcesUseCase
import com.mmg.manahub.feature.news.domain.usecase.RefreshNewsFeedUseCase
import com.google.firebase.crashlytics.FirebaseCrashlytics
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(FlowPreview::class)
@HiltViewModel
class NewsViewModel @Inject constructor(
    getNewsFeed: GetNewsFeedUseCase,
    private val refreshNewsFeed: RefreshNewsFeedUseCase,
    manageSources: ManageSourcesUseCase,
    private val userPrefsDataStore: UserPreferencesDataStore,
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    private val _isRefreshing = MutableStateFlow(false)
    private val _isInitialLoad = MutableStateFlow(true)
    private val _error = MutableStateFlow<String?>(null)

    // Advanced search filter state. Seeded from the persisted NewsFilterPrefs (default
    // English-only) in init; user edits are written back through to DataStore so the
    // selection is shared with the Home news widget and survives process death.
    private val _filterTypes = MutableStateFlow(NewsFilterPrefs.DEFAULT.types)
    private val _filterLanguages = MutableStateFlow(NewsFilterPrefs.DEFAULT.languages)
    private val _filterSourceIds = MutableStateFlow(NewsFilterPrefs.DEFAULT.sourceIds) // null = all enabled

    private val debouncedQuery = _searchQuery.debounce(300)

    val sources: StateFlow<List<ContentSource>> = manageSources.observeSources()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val uiState: StateFlow<NewsUiState> = combine(
        getNewsFeed(),
        debouncedQuery,
        combine(_filterTypes, _filterLanguages, _filterSourceIds) { types, langs, srcIds ->
            Triple(types, langs, srcIds)
        },
        combine(_isRefreshing, _error) { r, e -> r to e },
        manageSources.observeSources().map { srcs ->
            val languageMap = srcs.associate { it.id to it.language }
            val enabledIds = srcs.filter { it.isEnabled }.map { it.id }.toSet()
            languageMap to enabledIds
        },
    ) { allItems, query, filters, status, sourceData ->
        val (filterTypes, filterLanguages, filterSourceIds) = filters
        val (refreshing, error) = status
        val (languageMap, enabledSourceIds) = sourceData

        val items = allItems
            .filter { item -> item.sourceId in enabledSourceIds }
            .filter { item ->
                val sourceLanguage = languageMap[item.sourceId] ?: "en"
                sourceLanguage in filterLanguages
            }
            .filter { item ->
                val matchesType = when (item) {
                    is NewsItem.Article -> SourceType.ARTICLE in filterTypes
                    is NewsItem.Video   -> SourceType.VIDEO in filterTypes
                }
                matchesType
            }
            .filter { item ->
                filterSourceIds == null || item.sourceId in filterSourceIds
            }
            .filter { item ->
                query.isBlank() || item.title.contains(query, ignoreCase = true)
                    || item.description.contains(query, ignoreCase = true)
            }

        NewsUiState(
            items             = items,
            isLoading         = _isInitialLoad.value && items.isEmpty(),
            isRefreshing      = refreshing && !_isInitialLoad.value,
            searchQuery       = _searchQuery.value,
            filterTypes       = filterTypes,
            filterLanguages   = filterLanguages,
            filterSourceIds   = filterSourceIds,
            error             = error,
            sourceLanguageMap = languageMap,
            showLanguageBadge = filterLanguages.size > 1,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NewsUiState())

    init {
        // Seed the in-memory filter state from the persisted selection (default English-only).
        viewModelScope.launch {
            val persisted = userPrefsDataStore.observeNewsFilters().first()
            _filterTypes.value = persisted.types
            _filterLanguages.value = persisted.languages
            _filterSourceIds.value = persisted.sourceIds
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            _error.value = null
            val result = refreshNewsFeed()
            result.onFailure { e ->
                FirebaseCrashlytics.getInstance().apply {
                    log("news_refresh_failed: ${e::class.simpleName}")
                    recordException(RuntimeException("[NewsViewModel] Feed refresh failed", e))
                }
                _error.value = e.message
            }
            _isRefreshing.value = false
            _isInitialLoad.value = false
        }
    }

    fun onSearchQueryChanged(query: String) { _searchQuery.value = query }

    fun onFiltersApplied(
        types: Set<SourceType>,
        languages: Set<String>,
        sourceIds: Set<String>?,
    ) {
        val effectiveTypes = types.ifEmpty { NewsFilterPrefs.DEFAULT.types }
        val effectiveLanguages = languages.ifEmpty { NewsFilterPrefs.DEFAULT.languages }
        _filterTypes.value = effectiveTypes
        _filterLanguages.value = effectiveLanguages
        _filterSourceIds.value = sourceIds
        // Write through to DataStore so NewsScreen and the Home widget share one source of truth.
        viewModelScope.launch {
            userPrefsDataStore.setNewsFilters(
                languages = effectiveLanguages,
                types = effectiveTypes,
                sourceIds = sourceIds,
            )
        }
    }

    fun onErrorDismissed() { _error.value = null }
}

data class NewsUiState(
    val items: List<NewsItem> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val searchQuery: String = "",
    val filterTypes: Set<SourceType> = NewsFilterPrefs.DEFAULT.types,
    val filterLanguages: Set<String> = NewsFilterPrefs.DEFAULT.languages,
    val filterSourceIds: Set<String>? = NewsFilterPrefs.DEFAULT.sourceIds,
    val error: String? = null,
    val sourceLanguageMap: Map<String, String> = emptyMap(),
    val showLanguageBadge: Boolean = false,
)
