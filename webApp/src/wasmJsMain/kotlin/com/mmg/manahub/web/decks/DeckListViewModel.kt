package com.mmg.manahub.web.decks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.domain.repository.DeckRepository
import com.mmg.manahub.core.model.DeckSummary
import com.mmg.manahub.web.common.toUserFacingMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * UI state for [DeckListScreen] (web roadmap W3c) -- covers all four states CLAUDE.md requires
 * every screen to handle: loading (initial collection before the first emission), error (a failed
 * [createDeck] attempt), empty ([decks] empty after loading), and content ([decks] populated).
 */
data class DeckListUiState(
    val decks: List<DeckSummary> = emptyList(),
    val isLoading: Boolean = true,
    val isCreating: Boolean = false,
    val error: String? = null,
)

/**
 * Backs [DeckListScreen] -- the third REAL web MVP screen (web roadmap W3c), proving
 * [DeckRepository] (`WebDeckRepository`) works end-to-end against real Supabase data: a created
 * deck must appear in the list AND survive a full page reload (the `WebDeckRepository` cache is
 * session-scoped, so a reload re-hydrates from Supabase, not from anything cached locally).
 *
 * Deliberately NOT Deck Studio -- no card editing, no format picker, no deck detail navigation.
 * Those are a separate, much bigger future slice (master plan §5/§6 W4+).
 */
class DeckListViewModel(
    private val deckRepository: DeckRepository,
    private val crashReporter: CrashReporter,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeckListUiState())
    val uiState: StateFlow<DeckListUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            deckRepository.observeAllDeckSummaries().collect { decks ->
                _uiState.update { it.copy(decks = decks, isLoading = false) }
            }
        }
    }

    /** Creates a placeholder deck and refreshes the list. No-ops on a double-tap while in flight. */
    fun createDeck() {
        if (_uiState.value.isCreating) return
        viewModelScope.launch {
            _uiState.update { it.copy(isCreating = true, error = null) }
            try {
                deckRepository.createDeck(
                    name = "New Deck",
                    description = "",
                    format = "casual",
                )
                // No manual re-fetch needed here -- observeAllDeckSummaries() above already
                // reflects WebDeckRepository's in-memory cache, which createDeck() updates
                // synchronously before returning.
            } catch (e: Throwable) {
                _uiState.update { it.copy(error = e.toUserFacingMessage("create the deck", crashReporter)) }
            } finally {
                _uiState.update { it.copy(isCreating = false) }
            }
        }
    }
}
