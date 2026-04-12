package com.mmg.manahub.feature.news.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.manahub.feature.news.domain.model.ContentSource
import com.mmg.manahub.feature.news.domain.model.SourceType
import com.mmg.manahub.feature.news.domain.usecase.ManageSourcesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NewsSourcesSettingsViewModel @Inject constructor(
    private val manageSources: ManageSourcesUseCase,
) : ViewModel() {

    val sources: StateFlow<List<ContentSource>> = manageSources.observeSources()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _addState = MutableStateFlow(AddSourceState())
    val addState: StateFlow<AddSourceState> = _addState.asStateFlow()

    fun toggleSource(sourceId: String, enabled: Boolean) {
        viewModelScope.launch { manageSources.toggleSource(sourceId, enabled) }
    }

    fun deleteSource(sourceId: String) {
        viewModelScope.launch { manageSources.deleteSource(sourceId) }
    }

    fun onNameChanged(name: String) {
        _addState.update { it.copy(name = name) }
    }

    fun onFeedUrlChanged(url: String) {
        _addState.update { it.copy(feedUrl = url) }
    }

    fun onTypeChanged(type: SourceType) {
        _addState.update { it.copy(type = type) }
    }

    fun validateAndAdd() {
        val state = _addState.value
        if (state.name.isBlank() || state.feedUrl.isBlank()) return

        viewModelScope.launch {
            _addState.update { it.copy(isValidating = true, error = null, previewCount = null) }

            // Try to resolve YouTube channel URL to RSS feed
            val resolvedUrl = resolveYouTubeUrl(state.feedUrl, state.type)

            val result = manageSources.validateFeed(resolvedUrl, state.type)
            result.onSuccess { count ->
                _addState.update { it.copy(isValidating = false, previewCount = count) }
                // Auto-add after successful validation
                manageSources.addCustomSource(state.name, resolvedUrl, state.type)
                _addState.value = AddSourceState() // reset
            }.onFailure { e ->
                _addState.update { it.copy(isValidating = false, error = e.message) }
            }
        }
    }

    private fun resolveYouTubeUrl(url: String, type: SourceType): String {
        if (type != SourceType.VIDEO) return url
        // If user pastes a youtube.com/@channel URL, try to convert to RSS feed
        val channelHandleRegex = Regex("""youtube\.com/@([\w-]+)""")
        val match = channelHandleRegex.find(url)
        if (match != null) {
            // Can't resolve handle to channel ID without API, return as-is
            // User will get an error and be told to paste channel ID
            return url
        }
        // If it's a channel ID directly (24 chars starting with UC)
        val channelIdRegex = Regex("""^UC[\w-]{22}$""")
        if (channelIdRegex.matches(url.trim())) {
            return "https://www.youtube.com/feeds/videos.xml?channel_id=${url.trim()}"
        }
        return url
    }
}

data class AddSourceState(
    val name: String = "",
    val feedUrl: String = "",
    val type: SourceType = SourceType.ARTICLE,
    val isValidating: Boolean = false,
    val previewCount: Int? = null,
    val error: String? = null,
)
