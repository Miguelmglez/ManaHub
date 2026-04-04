package com.mmg.magicfolder.feature.news.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.magicfolder.feature.news.domain.model.ContentSource
import com.mmg.magicfolder.feature.news.domain.model.ContentType
import com.mmg.magicfolder.feature.news.domain.model.NewsItem
import com.mmg.magicfolder.feature.news.domain.usecase.GetNewsFeedUseCase
import com.mmg.magicfolder.feature.news.domain.usecase.ManageSourcesUseCase
import com.mmg.magicfolder.feature.news.domain.usecase.RefreshNewsFeedUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
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
        _isRefreshing,
        _error,
    ) { args ->
        val allItems = args[0] as List<*>
        val query = args[1] as String
        val type = args[2] as ContentType
        val sourceId = args[3] as String?
        val refreshing = args[4] as Boolean
        val error = args[5] as String?

        @Suppress("UNCHECKED_CAST")
        val items = (allItems as List<NewsItem>)
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
            items        = items,
            isLoading    = items.isEmpty() && refreshing,
            isRefreshing = refreshing,
            searchQuery  = _searchQuery.value,
            contentType  = type,
            selectedSourceId = sourceId,
            error        = error,
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
)
