package com.mmg.manahub.feature.trades.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.manahub.core.di.IoDispatcher
import com.mmg.manahub.feature.trades.domain.model.SharedListResult
import com.mmg.manahub.feature.trades.domain.repository.SharedListsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─────────────────────────────────────────────────────────────────────────────
//  UI state
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Models all possible display states for the shared-list deep link landing screen.
 */
sealed class SharedListUiState {
    /** The share ID is being resolved against the remote database. */
    object Loading : SharedListUiState()

    /** Successfully resolved; [result] holds the list data. */
    data class Success(val result: SharedListResult.Ok) : SharedListUiState()

    /** The owner has revoked public sharing. */
    object Private : SharedListUiState()

    /** No list exists for this share ID. */
    object NotFound : SharedListUiState()

    /** An unexpected error occurred during resolution. */
    data class Error(val message: String?) : SharedListUiState()
}

// ─────────────────────────────────────────────────────────────────────────────
//  ViewModel
// ─────────────────────────────────────────────────────────────────────────────

/**
 * ViewModel for [TradesSharedListScreen].
 *
 * Reads the [shareId] from the navigation back-stack entry (saved state) and
 * calls [SharedListsRepository.resolveSharedList] to fetch the referenced list.
 */
@HiltViewModel
class TradesSharedListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sharedListsRepository: SharedListsRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _uiState = MutableStateFlow<SharedListUiState>(SharedListUiState.Loading)
    val uiState: StateFlow<SharedListUiState> = _uiState.asStateFlow()

    init {
        val shareId = savedStateHandle.get<String>("shareId")
        if (shareId.isNullOrBlank()) {
            _uiState.update { SharedListUiState.NotFound }
        } else {
            resolveList(shareId)
        }
    }

    private fun resolveList(shareId: String) {
        viewModelScope.launch(ioDispatcher) {
            _uiState.update { SharedListUiState.Loading }
            sharedListsRepository.resolveSharedList(shareId)
                .onSuccess { result ->
                    _uiState.update {
                        when (result) {
                            is SharedListResult.Ok      -> SharedListUiState.Success(result)
                            is SharedListResult.Private -> SharedListUiState.Private
                            is SharedListResult.NotFound -> SharedListUiState.NotFound
                        }
                    }
                }
                .onFailure { e ->
                    _uiState.update { SharedListUiState.Error(e.message) }
                }
        }
    }
}
