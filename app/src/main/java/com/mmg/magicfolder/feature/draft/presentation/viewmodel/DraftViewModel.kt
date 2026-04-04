package com.mmg.magicfolder.feature.draft.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.magicfolder.core.domain.model.DataResult
import com.mmg.magicfolder.feature.draft.domain.model.DraftSet
import com.mmg.magicfolder.feature.draft.domain.usecase.GetDraftableSetsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DraftUiState(
    val sets: List<DraftSet> = emptyList(),
    val filteredSets: List<DraftSet> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val isStale: Boolean = false,
    val searchQuery: String = "",
)

@HiltViewModel
class DraftViewModel @Inject constructor(
    private val getDraftableSetsUseCase: GetDraftableSetsUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DraftUiState())
    val uiState: StateFlow<DraftUiState> = _uiState.asStateFlow()

    init {
        loadSets()
    }

    fun loadSets(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val result = getDraftableSetsUseCase(forceRefresh)) {
                is DataResult.Success -> {
                    val sets = result.data
                    _uiState.update { state ->
                        state.copy(
                            sets = sets,
                            filteredSets = filterSets(sets, state.searchQuery),
                            isLoading = false,
                            isStale = result.isStale,
                            error = null,
                        )
                    }
                }
                is DataResult.Error -> {
                    _uiState.update { it.copy(isLoading = false, error = result.message) }
                }
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { state ->
            state.copy(
                searchQuery = query,
                filteredSets = filterSets(state.sets, query),
            )
        }
    }

    private fun filterSets(sets: List<DraftSet>, query: String): List<DraftSet> {
        if (query.isBlank()) return sets
        val lower = query.lowercase()
        return sets.filter {
            it.name.lowercase().contains(lower) || it.code.lowercase().contains(lower)
        }
    }
}
