package com.mmg.manahub.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.data.remote.ScryfallRemoteDataSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AvatarPickerViewModel @Inject constructor(
    private val scryfallRemoteDataSource: ScryfallRemoteDataSource,
    private val userPreferencesDataStore: UserPreferencesDataStore,
) : ViewModel() {

    data class PlaneswalkerArt(
        val name: String,
        val artCropUrl: String,
        val colors: List<String>,
    )

    data class UiState(
        val artworks: List<PlaneswalkerArt> = emptyList(),
        val selectedColors: Set<String> = emptySet(),
        val isLoading: Boolean = false,
        val error: String? = null,
        val currentAvatarUrl: String? = null,
        val pendingSelection: String? = null,
        val hasMore: Boolean = false,
        val currentPage: Int = 1,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val savedUrl = userPreferencesDataStore.avatarUrlFlow.first()
            _uiState.update { it.copy(currentAvatarUrl = savedUrl) }
        }
        loadPlaneswalkers()
    }

    fun toggleColorFilter(color: String) {
        val current = _uiState.value.selectedColors.toMutableSet()
        if (current.contains(color)) current.remove(color) else current.add(color)
        _uiState.update { it.copy(selectedColors = current, artworks = emptyList(), currentPage = 1) }
        loadPlaneswalkers()
    }

    fun clearColorFilters() {
        _uiState.update { it.copy(selectedColors = emptySet(), artworks = emptyList(), currentPage = 1) }
        loadPlaneswalkers()
    }

    fun loadNextPage() {
        if (!_uiState.value.hasMore || _uiState.value.isLoading) return
        _uiState.update { it.copy(currentPage = it.currentPage + 1) }
        loadPlaneswalkers(append = true)
    }

    private fun loadPlaneswalkers(append: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val colorFilter = _uiState.value.selectedColors
                    .joinToString("") { it.lowercase() }
                val query = buildString {
                    append("t:planeswalker unique:art")
                    if (colorFilter.isNotEmpty()) append(" c:$colorFilter")
                }
                val page = _uiState.value.currentPage
                val response = scryfallRemoteDataSource.searchPlaneswalkerArts(query, page)

                val arts = response.data.mapNotNull { dto ->
                    val artUrl = dto.imageUris?.artCrop
                        ?: dto.cardFaces?.firstOrNull()?.imageUris?.artCrop
                    if (artUrl != null)
                        PlaneswalkerArt(
                            name = dto.name,
                            artCropUrl = artUrl,
                            colors = dto.colors ?: emptyList(),
                        )
                    else null
                }

                _uiState.update { state ->
                    state.copy(
                        artworks = if (append) state.artworks + arts else arts,
                        isLoading = false,
                        hasMore = response.hasMore,
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun selectArt(artUrl: String) {
        _uiState.update { it.copy(pendingSelection = artUrl) }
    }

    fun confirmSelection() {
        val url = _uiState.value.pendingSelection ?: return
        viewModelScope.launch {
            userPreferencesDataStore.saveAvatarUrl(url)
            _uiState.update { it.copy(currentAvatarUrl = url, pendingSelection = null) }
        }
    }

    fun cancelSelection() {
        _uiState.update { it.copy(pendingSelection = null) }
    }

    fun removeAvatar() {
        viewModelScope.launch {
            userPreferencesDataStore.saveAvatarUrl(null)
            _uiState.update { it.copy(currentAvatarUrl = null) }
        }
    }

}
