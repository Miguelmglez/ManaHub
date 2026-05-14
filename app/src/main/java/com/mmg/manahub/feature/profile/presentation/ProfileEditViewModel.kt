package com.mmg.manahub.feature.profile.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.data.remote.ScryfallRemoteDataSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileEditViewModel @Inject constructor(
    private val scryfallRemoteDataSource: ScryfallRemoteDataSource,
    private val userPreferencesDataStore: UserPreferencesDataStore,
    private val authRepository: com.mmg.manahub.feature.auth.domain.repository.AuthRepository,
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
        // Profile Info
        val currentName: String = "",
        val pendingName: String = "",
        val gameTag: String? = null,
    ) {
        val isNameValid: Boolean get() = pendingName.isNotBlank() && pendingName.length <= 30
        val hasChanges: Boolean get() = (pendingName != currentName && isNameValid) || pendingSelection != null
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // Observe DataStore for Name
            userPreferencesDataStore.playerNameFlow.collect { name ->
                _uiState.update { it.copy(
                    currentName = name,
                    // If currentName changes (from server sync), we should likely update pendingName 
                    // unless the user is currently editing it and it differs from the PREVIOUS currentName.
                    // To keep it simple and fix the "stale nickname on login" issue:
                    pendingName = name
                ) }
            }
        }
        viewModelScope.launch {
            // Observe session for gameTag
            authRepository.sessionState.collect { session ->
                if (session is com.mmg.manahub.feature.auth.domain.model.SessionState.Authenticated) {
                    _uiState.update { it.copy(gameTag = session.user.gameTag) }
                } else {
                    _uiState.update { it.copy(gameTag = null) }
                }
            }
        }
        viewModelScope.launch {
            userPreferencesDataStore.avatarUrlFlow.collect { url ->
                _uiState.update { it.copy(currentAvatarUrl = url) }
            }
        }
        loadPlaneswalkers()
    }

    fun onNameChange(newName: String) {
        if (newName.length <= 30) {
            _uiState.update { it.copy(pendingName = newName) }
        }
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
        var shouldLoad = false
        _uiState.update { s ->
            if (!s.hasMore || s.isLoading) {
                s
            } else {
                shouldLoad = true
                s.copy(currentPage = s.currentPage + 1, isLoading = true)
            }
        }
        if (!shouldLoad) return
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

    fun confirmChanges(onNicknameUpdate: ((String) -> Unit)? = null) {
        val state = _uiState.value
        if (!state.isNameValid) return

        viewModelScope.launch {
            // Save Name
            if (state.pendingName != state.currentName) {
                userPreferencesDataStore.savePlayerName(state.pendingName)
                onNicknameUpdate?.invoke(state.pendingName)
            }
            
            // Save Avatar locally and push to Supabase
            state.pendingSelection?.let { url ->
                userPreferencesDataStore.saveAvatarUrl(url)
                authRepository.updateAvatarUrl(url)
            }
            
            _uiState.update { it.copy(
                currentName = it.pendingName,
                currentAvatarUrl = it.pendingSelection ?: it.currentAvatarUrl,
                pendingSelection = null
            ) }
        }
    }

    fun cancelSelection() {
        _uiState.update { it.copy(pendingSelection = null, pendingName = it.currentName) }
    }

    fun removeAvatar() {
        viewModelScope.launch {
            userPreferencesDataStore.saveAvatarUrl(null)
            authRepository.updateAvatarUrl(null)
            _uiState.update { it.copy(currentAvatarUrl = null, pendingSelection = null) }
        }
    }

}
