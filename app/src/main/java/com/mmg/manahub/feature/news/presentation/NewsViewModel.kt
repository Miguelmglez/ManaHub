package com.mmg.manahub.feature.news.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.manahub.core.domain.repository.UserPreferencesRepository
import com.mmg.manahub.feature.news.domain.model.ContentSource
import com.mmg.manahub.feature.news.domain.model.ContentType
import com.mmg.manahub.feature.news.domain.model.NewsItem
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(FlowPreview::class)
@HiltViewModel
class NewsViewModel @Inject constructor(
    getNewsFeed: GetNewsFeedUseCase,
    private val refreshNewsFeed: RefreshNewsFeedUseCase,
    manageSources: ManageSourcesUseCase,
    userPreferences: UserPreferencesRepository,
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _contentType = MutableStateFlow(ContentType.ALL)
    private val _selectedSourceId = MutableStateFlow<String?>(null)
    private val _isRefreshing = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)

    private val debouncedQuery = _searchQuery.debounce(300)

    val sources: StateFlow<List<ContentSource>> = manageSources.observeSources()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val uiState: StateFlow<NewsUiState> = combine(
        getNewsFeed(),
        debouncedQuery,
        _contentType,
        _selectedSourceId,
        combine(_isRefreshing, _error) { r, e -> r to e },
        combine(manageSources.observeSources(), userPreferences.preferencesFlow) { srcs, prefs ->
            val activeLanguages = prefs.newsLanguages.map { it.code }.toSet()
            val languageMap = srcs.associate { it.id to it.language }
            val enabledIds = srcs
                .filter { it.isEnabled && it.language in activeLanguages }
                .map { it.id }
                .toSet()
            Triple(activeLanguages, languageMap, enabledIds)
        },
    ) { args ->
        @Suppress("UNCHECKED_CAST")
        val allItems = args[0] as List<NewsItem>
        val query = args[1] as String
        val type = args[2] as ContentType
        val sourceId = args[3] as String?
        val (refreshing, error) = args[4] as Pair<Boolean, String?>
        val (activeLanguages, languageMap, enabledSourceIds) = args[5] as Triple<Set<String>, Map<String, String>, Set<String>>

        val items = allItems
            .filter { item -> item.sourceId in enabledSourceIds }
            .filter { item ->
                when (type) {
                    ContentType.ALL     -> true
                    ContentType.ARTICLE -> item is NewsItem.Article
                    ContentType.VIDEO   -> item is NewsItem.Video
                }
            }
            .filter { item ->
                sourceId == null || item.sourceId == sourceId
            }
            .filter { item ->
                query.isBlank() || item.title.contains(query, ignoreCase = true)
                    || item.description.contains(query, ignoreCase = true)
            }

        NewsUiState(
            items            = items,
            isLoading        = items.isEmpty() && refreshing,
            isRefreshing     = refreshing,
            searchQuery      = _searchQuery.value,
            contentType      = type,
            selectedSourceId = sourceId,
            error            = error,
            sourceLanguageMap    = languageMap,
            showLanguageBadge    = activeLanguages.size > 1,
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
    fun onContentTypeChanged(type: ContentType) { _contentType.value = type }
    fun onSourceFilterChanged(sourceId: String?) { _selectedSourceId.value = sourceId }
    fun onErrorDismissed() { _error.value = null }
}

data class NewsUiState(
    val items: List<NewsItem> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val searchQuery: String = "",
    val contentType: ContentType = ContentType.ALL,
    val selectedSourceId: String? = null,
    val error: String? = null,
    val sourceLanguageMap: Map<String, String> = emptyMap(),
    val showLanguageBadge: Boolean = false,
)
