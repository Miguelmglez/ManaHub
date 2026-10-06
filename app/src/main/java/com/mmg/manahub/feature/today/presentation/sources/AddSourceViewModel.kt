package com.mmg.manahub.feature.today.presentation.sources

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.model.news.ContentSource
import com.mmg.manahub.core.model.news.ResolvedSource
import com.mmg.manahub.core.model.news.SourceResolveError
import com.mmg.manahub.core.model.news.SourceResolveException
import com.mmg.manahub.feature.news.domain.usecase.FollowSourceUseCase
import com.mmg.manahub.feature.news.domain.usecase.ResolveSourceUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface AddSourceEvent {
    data class Followed(val sourceName: String) : AddSourceEvent
}

data class AddSourceUiState(
    val input: String = "",
    val isResolving: Boolean = false,
    val resolved: ResolvedSource? = null,
    val name: String = "",
    val language: String = "en",
    val isFollowing: Boolean = false,
    val error: SourceResolveError? = null,
) {
    val canFind: Boolean get() = input.isNotBlank() && !isResolving
}

/** Add Source sheet: paste anything → resolve → preview (editable name/language) → follow. */
class AddSourceViewModel(
    private val resolveSource: ResolveSourceUseCase,
    private val followSource: FollowSourceUseCase,
    private val crashReporter: CrashReporter,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddSourceUiState())
    val uiState: StateFlow<AddSourceUiState> = _uiState.asStateFlow()

    private val _events = Channel<AddSourceEvent>(Channel.BUFFERED)
    val events: Flow<AddSourceEvent> = _events.receiveAsFlow()

    private var job: Job? = null

    fun onInputChanged(input: String) {
        _uiState.update { it.copy(input = input, error = null) }
    }

    fun find() {
        val input = _uiState.value.input
        if (input.isBlank() || _uiState.value.isResolving) return
        job?.cancel()
        _uiState.update { it.copy(isResolving = true, error = null, resolved = null) }
        job = viewModelScope.launch {
            resolveSource(input)
                .onSuccess { resolved ->
                    crashReporter.log("news_source_resolve_success")
                    _uiState.update {
                        it.copy(
                            isResolving = false,
                            resolved = resolved,
                            name = resolved.name,
                            language = resolved.language?.takeIf { code -> code in ContentSource.SUPPORTED_LANGUAGES } ?: "en",
                        )
                    }
                }
                .onFailure { e -> fail(e, stage = "resolve") }
        }
    }

    fun onNameChanged(name: String) {
        _uiState.update { it.copy(name = name) }
    }

    fun onLanguageSelected(language: String) {
        _uiState.update { it.copy(language = language) }
    }

    /** Back from the preview to the link field, keeping what the user pasted. */
    fun editLink() {
        job?.cancel()
        _uiState.update { it.copy(resolved = null, isResolving = false, isFollowing = false, error = null) }
    }

    fun follow() {
        val state = _uiState.value
        val resolved = state.resolved ?: return
        if (state.isFollowing) return
        _uiState.update { it.copy(isFollowing = true, error = null) }
        job = viewModelScope.launch {
            followSource(resolved, state.name, state.language)
                .onSuccess { followed ->
                    crashReporter.log("news_source_followed")
                    _events.send(AddSourceEvent.Followed(followed.name))
                    _uiState.value = AddSourceUiState()
                }
                .onFailure { e -> fail(e, stage = "follow") }
        }
    }

    /** Clears everything, e.g. when the sheet is dismissed mid-resolution. */
    fun reset() {
        job?.cancel()
        _uiState.value = AddSourceUiState()
    }

    // Messages are dropped on purpose: they can carry the pasted URL.
    private fun fail(e: Throwable, stage: String) {
        val error = (e as? SourceResolveException)?.error ?: SourceResolveError.UNREACHABLE
        if (e !is SourceResolveException) {
            crashReporter.recordException(RuntimeException("[AddSourceViewModel] $stage: ${e::class.simpleName}"))
        }
        crashReporter.log("news_source_${stage}_failed:${error.name}")
        _uiState.update { it.copy(isResolving = false, isFollowing = false, error = error) }
    }
}
