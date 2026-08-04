package com.mmg.manahub.web.collection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.manahub.core.domain.repository.UserCardRepository
import com.mmg.manahub.core.domain.repository.UserPreferencesRepository
import com.mmg.manahub.core.model.CollectionViewMode
import com.mmg.manahub.core.model.UserCardWithCard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * UI state for [CollectionScreen] (web roadmap W3d, [viewMode] added by the Settings expansion
 * slice) — covers all four states CLAUDE.md requires every screen to handle: loading (initial
 * collection before the first emission), error (reserved for a future mutation surface on this
 * screen — none exist yet, read-only for this slice), empty ([cards] empty after loading), and
 * content ([cards] populated).
 */
data class CollectionUiState(
    val cards: List<UserCardWithCard> = emptyList(),
    val isLoading: Boolean = true,
    val viewMode: CollectionViewMode = CollectionViewMode.GRID,
)

/**
 * Backs [CollectionScreen] — the fourth REAL web MVP screen (web roadmap W3d), proving
 * [UserCardRepository] (`WebUserCardRepository`) works end-to-end against real Supabase data: a
 * card added via [com.mmg.manahub.web.search.CardSearchViewModel.addToCollection] must appear here
 * AND survive a full page reload (the `WebUserCardRepository` cache is session-scoped, so a reload
 * re-hydrates from Supabase, not from anything cached locally).
 *
 * Read-only over collection CONTENT for this slice (no quantity edit / remove UI yet) —
 * deliberately minimal, mirroring [com.mmg.manahub.web.decks.DeckListScreen]'s scope discipline.
 * [setViewMode] is the one write path this ViewModel owns, added by the Settings expansion slice:
 * it and [com.mmg.manahub.web.settings.SettingsViewModel.setCollectionViewMode] call the exact
 * same [UserPreferencesRepository.saveCollectionViewMode] method on the same Koin singleton, so
 * this screen's own inline toggle and the Settings screen's picker always agree.
 */
class CollectionViewModel(
    private val userCardRepository: UserCardRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CollectionUiState())
    val uiState: StateFlow<CollectionUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            userCardRepository.observeCollection().collect { cards ->
                _uiState.update { it.copy(cards = cards, isLoading = false) }
            }
        }
        viewModelScope.launch {
            userPreferencesRepository.collectionViewModeFlow.collect { mode ->
                _uiState.update { it.copy(viewMode = mode) }
            }
        }
    }

    fun setViewMode(mode: CollectionViewMode) {
        viewModelScope.launch { userPreferencesRepository.saveCollectionViewMode(mode) }
    }
}
