package com.mmg.manahub.feature.news.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.manahub.feature.news.domain.model.ContentSource
import com.mmg.manahub.feature.news.domain.model.NewsItem
import com.mmg.manahub.feature.news.domain.model.SourceType
import com.mmg.manahub.feature.news.domain.usecase.GetNewsFeedUseCase
import com.mmg.manahub.feature.news.domain.usecase.ManageSourcesUseCase
import com.mmg.manahub.feature.news.domain.usecase.RefreshNewsFeedUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
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
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _isRefreshing = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)

    // Advanced search filter state
    private val _filterTypes = MutableStateFlow(setOf(SourceType.ARTICLE, SourceType.VIDEO))
    private val _filterLanguages = MutableStateFlow(setOf("en", "es"))
    private val _filterSourceIds = MutableStateFlow<Set<String>?>(null) // null = all enabled

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
            isLoading         = items.isEmpty() && refreshing,
            isRefreshing      = refreshing,
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
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            _error.value = null
            val result = refreshNewsFeed()
            result.onFailure { _error.value = it.message }
            _isRefreshing.value = false
        }
    }

    fun onSearchQueryChanged(query: String) { _searchQuery.value = query }

    fun onFiltersApplied(
        types: Set<SourceType>,
        languages: Set<String>,
        sourceIds: Set<String>?,
    ) {
        _filterTypes.value = types.ifEmpty { setOf(SourceType.ARTICLE, SourceType.VIDEO) }
        _filterLanguages.value = languages.ifEmpty { setOf("en", "es") }
        _filterSourceIds.value = sourceIds
    }

    fun onErrorDismissed() { _error.value = null }
}

data class NewsUiState(
    val items: List<NewsItem> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val searchQuery: String = "",
    val filterTypes: Set<SourceType> = setOf(SourceType.ARTICLE, SourceType.VIDEO),
    val filterLanguages: Set<String> = setOf("en", "es"),
    val filterSourceIds: Set<String>? = null,
    val error: String? = null,
    val sourceLanguageMap: Map<String, String> = emptyMap(),
    val showLanguageBadge: Boolean = false,
)
