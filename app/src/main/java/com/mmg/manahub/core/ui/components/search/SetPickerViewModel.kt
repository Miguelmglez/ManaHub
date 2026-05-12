package com.mmg.manahub.core.ui.components.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.manahub.core.data.remote.ScryfallRemoteDataSource
import com.mmg.manahub.core.domain.model.MagicSet
import com.mmg.manahub.core.domain.model.SetType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SetPickerViewModel @Inject constructor(
    private val scryfallDataSource: ScryfallRemoteDataSource,
) : ViewModel() {

    data class UiState(
        val allSets: List<MagicSet> = emptyList(),
        val restrictedSets: List<MagicSet>? = null,
        val filteredSets: List<MagicSet> = emptyList(),
        val searchQuery: String = "",
        val selectedTypes: Set<SetType> = emptySet(),
        val isLoading: Boolean = false,
        val error: String? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun init(initialRestrictedSets: List<MagicSet>?) {
        if (_uiState.value.allSets.isNotEmpty() || _uiState.value.restrictedSets != null) return
        
        _uiState.update { it.copy(restrictedSets = initialRestrictedSets) }
        loadSets()
    }

    private fun loadSets() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val sets = scryfallDataSource.getAllSets()
                _uiState.update { it.copy(
                    allSets = sets,
                    isLoading = false,
                )}
                applyFilters()
            } catch (e: Exception) {
                _uiState.update { it.copy(
                    isLoading = false,
                    error = e.message,
                )}
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        applyFilters()
    }

    fun setRestrictedSets(sets: List<MagicSet>?) {
        _uiState.update { it.copy(restrictedSets = sets) }
        applyFilters()
    }

    fun toggleTypeFilter(type: SetType) {
        val current = _uiState.value.selectedTypes.toMutableSet()
        if (current.contains(type)) current.remove(type) else current.add(type)
        _uiState.update { it.copy(selectedTypes = current) }
        applyFilters()
    }

    fun clearFilters() {
        _uiState.update { it.copy(
            searchQuery = "",
            selectedTypes = emptySet(),
            filteredSets = _uiState.value.allSets,
        )}
    }

    private fun applyFilters() {
        val s = _uiState.value
        
        // If we have restricted sets, we ONLY show those (even if they are empty)
        // If restrictedSets is null, we use allSets from Scryfall
        val sourceSets = s.restrictedSets ?: s.allSets

        val filtered = sourceSets.filter { set ->
            val matchesQuery = s.searchQuery.isBlank() ||
                set.name.contains(s.searchQuery, ignoreCase = true) ||
                set.code.contains(s.searchQuery, ignoreCase = true)
            val matchesType = s.selectedTypes.isEmpty() ||
                set.setType in s.selectedTypes
            matchesQuery && matchesType
        }
        _uiState.update { it.copy(filteredSets = filtered) }
    }
}
