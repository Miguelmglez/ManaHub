package com.mmg.manahub.feature.news.presentation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.manahub.R
import com.mmg.manahub.core.model.news.SourceType
import com.mmg.manahub.core.model.news.ContentSource
import com.mmg.manahub.feature.news.domain.usecase.ManageSourcesUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Marks a YouTube URL shape (`@handle`, `/c/…`, `/user/…`) that this app cannot resolve to a
 * channel id from an Android OkHttp call alone (that requires scraping the channel page HTML or
 * the YouTube Data API — both out of scope here, see the `TODO(KMP)` at the detection site).
 */
private class UnsupportedYouTubeHandleException : Exception()

/**
 * KMP migration — Phase 1: resolved by Koin (`koinViewModel()`), not Hilt. The plain constructor lets
 * `newsKoinModule` build it from the bridged [ManageSourcesUseCase] + the application [Context]
 * (Koin `androidContext()` — same pattern as `SurveyViewModel`), needed to resolve the one
 * resource-backed error message this VM surfaces (§Phase 4, the YouTube handle warning).
 */
class NewsSourcesSettingsViewModel(
    private val manageSources: ManageSourcesUseCase,
    private val context: Context,
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

    fun onLanguageChanged(language: String) {
        _addState.update { it.copy(language = language) }
    }

    fun validateAndAdd() {
        val state = _addState.value
        if (state.name.isBlank() || state.feedUrl.isBlank()) return

        val normalizedUrl = state.feedUrl.trim()
        // A bare YouTube channel id (e.g. "UC..." x22) is a supported VIDEO input shape per
        // resolveYouTubeUrl's KDoc/BARE_CHANNEL_ID_REGEX, but it never starts with "https://" —
        // exempt exactly that shape from the HTTPS guard so it can reach resolveYouTubeUrl below.
        // Every other input (including actual non-HTTPS URLs) still hits the guard unchanged.
        val isBareYouTubeChannelId = state.type == SourceType.VIDEO && BARE_CHANNEL_ID_REGEX.matches(normalizedUrl)
        if (!isBareYouTubeChannelId && !normalizedUrl.startsWith("https://")) {
            _addState.update { it.copy(error = "Only HTTPS feed URLs are accepted") }
            return
        }

        viewModelScope.launch {
            _addState.update { it.copy(isValidating = true, error = null, previewCount = null) }

            // Try to resolve a YouTube channel URL to its RSS feed; surfaces a specific error for
            // unsupported @handle/c/user shapes instead of falling through to a generic message.
            val resolution = resolveYouTubeUrl(normalizedUrl, state.type)
            val resolvedUrl = resolution.getOrNull()
            if (resolvedUrl == null) {
                val message = if (resolution.exceptionOrNull() is UnsupportedYouTubeHandleException) {
                    context.getString(R.string.news_sources_youtube_handle_unsupported)
                } else {
                    resolution.exceptionOrNull()?.message ?: "Invalid feed URL"
                }
                _addState.update { it.copy(isValidating = false, error = message) }
                return@launch
            }

            val result = manageSources.validateFeed(resolvedUrl, state.type)
            result.onSuccess { count ->
                _addState.update { it.copy(isValidating = false, previewCount = count) }

                // F3 bonus: pre-select the feed's detected channel language for article sources —
                // never overrides a language the user already picked explicitly.
                val effectiveLanguage = if (state.type == SourceType.ARTICLE && state.language == "en") {
                    manageSources.detectFeedLanguage(resolvedUrl) ?: state.language
                } else {
                    state.language
                }

                val addResult = manageSources.addCustomSource(state.name, resolvedUrl, state.type, effectiveLanguage)
                addResult.onSuccess { added ->
                    _addState.value = AddSourceState()
                    // F9: refresh the new source right away so its items show up immediately
                    // instead of waiting for the next scheduled refresh cycle.
                    manageSources.refreshSource(added.id)
                }.onFailure { e ->
                    _addState.update { it.copy(error = e.message) }
                }
            }.onFailure { e ->
                _addState.update { it.copy(isValidating = false, error = e.message) }
            }
        }
    }

    /**
     * Resolves a pasted YouTube URL to its RSS feed URL where possible:
     *  - `youtube.com/feeds/videos.xml?channel_id=UC…` — already a feed URL, passed through as-is.
     *  - `youtube.com/channel/UC…` — the channel id is extracted and the feed URL built directly.
     *  - a bare `UC…` channel id (24 chars) — same as above.
     *  - `youtube.com/@handle`, `/c/…`, `/user/…` — cannot be resolved without scraping the
     *    channel page HTML or calling the YouTube Data API (both out of scope); fails with
     *    [UnsupportedYouTubeHandleException] so the caller can surface a specific, actionable
     *    error instead of the generic "no items found in feed" message.
     *    TODO(KMP): resolve `@handle`s via the YouTube Data API.
     *  - anything else (including non-YouTube URLs, and ARTICLE-type sources) — passed through
     *    unchanged; [ManageSourcesUseCase.validateFeed] handles the generic failure case.
     */
    private fun resolveYouTubeUrl(url: String, type: SourceType): Result<String> {
        if (type != SourceType.VIDEO) return Result.success(url)
        val trimmed = url.trim()

        if (trimmed.contains("youtube.com/feeds/videos.xml", ignoreCase = true) && trimmed.contains("channel_id=")) {
            return Result.success(trimmed)
        }

        CHANNEL_PATH_REGEX.find(trimmed)?.let { match ->
            return Result.success(youTubeFeedUrl(match.groupValues[1]))
        }

        if (BARE_CHANNEL_ID_REGEX.matches(trimmed)) {
            return Result.success(youTubeFeedUrl(trimmed))
        }

        if (UNSUPPORTED_HANDLE_REGEXES.any { it.containsMatchIn(trimmed) }) {
            return Result.failure(UnsupportedYouTubeHandleException())
        }

        return Result.success(trimmed)
    }

    private fun youTubeFeedUrl(channelId: String) =
        "https://www.youtube.com/feeds/videos.xml?channel_id=$channelId"

    companion object {
        // `(?i:...)` scopes case-insensitivity to just the literal host text (a differently-cased
        // host like "YouTube.com/channel/UC..." must still resolve) WITHOUT case-folding the
        // captured channel id itself — real channel ids are case-sensitive and always start "UC".
        // The negative lookahead `(?![\w-])` rejects a malformed 23+-char id instead of silently
        // truncating it to the first 22 chars, matching BARE_CHANNEL_ID_REGEX's `^...$` strictness.
        private val CHANNEL_PATH_REGEX = Regex("""(?i:youtube\.com/channel/)(UC[\w-]{22})(?![\w-])""")
        private val BARE_CHANNEL_ID_REGEX = Regex("""^UC[\w-]{22}$""")
        private val UNSUPPORTED_HANDLE_REGEXES = listOf(
            Regex("""(?i:youtube\.com/@)[\w.-]+"""),
            Regex("""(?i:youtube\.com/c/)[\w-]+"""),
            Regex("""(?i:youtube\.com/user/)[\w-]+"""),
        )
    }
}

data class AddSourceState(
    val name: String = "",
    val feedUrl: String = "",
    val type: SourceType = SourceType.ARTICLE,
    val language: String = "en",
    val isValidating: Boolean = false,
    val previewCount: Int? = null,
    val error: String? = null,
)
