package com.mmg.manahub.feature.communitydecks.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.domain.model.DataResult
import com.mmg.manahub.core.util.recordNonFatal
import com.mmg.manahub.feature.communitydecks.domain.usecase.GetCommunityDeckUseCase
import com.mmg.manahub.feature.communitydecks.domain.usecase.ImportCommunityDeckUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Community Deck detail screen.
 *
 * Loads a single Archidekt deck (cache-first via the repository) and drives a
 * resilient import into a new local ManaHub deck. One-shot navigation / toast
 * effects are delivered through a buffered [Channel].
 */
@HiltViewModel
class CommunityDeckDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getCommunityDeck: GetCommunityDeckUseCase,
    private val importCommunityDeck: ImportCommunityDeckUseCase,
    userPreferences: UserPreferencesDataStore,
) : ViewModel() {

    private val crashlytics = FirebaseCrashlytics.getInstance()

    private val archidektId: Int = savedStateHandle["archidektId"] ?: 0

    private val _uiState = MutableStateFlow<CommunityDeckDetailUiState>(CommunityDeckDetailUiState.Loading)
    val uiState: StateFlow<CommunityDeckDetailUiState> = _uiState.asStateFlow()

    private val _events = Channel<CommunityDeckDetailEvent>(Channel.BUFFERED)
    val events: Flow<CommunityDeckDetailEvent> = _events.receiveAsFlow()

    /** Feature flag — the screen renders a disabled state when this is false. */
    val isFeatureEnabled: StateFlow<Boolean> = userPreferences.communityDecksEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    init {
        crashlytics.setCustomKey("community_deck_archidekt_id", archidektId)
        if (archidektId <= 0) {
            recordNonFatal("community_deck_invalid_id", IllegalArgumentException("archidektId=$archidektId"))
        }
        loadDeck()
    }

    /** (Re)loads the deck. Safe to call from a retry button. */
    fun loadDeck() {
        viewModelScope.launch {
            _uiState.value = CommunityDeckDetailUiState.Loading
            when (val result = getCommunityDeck(archidektId)) {
                is DataResult.Success -> {
                    crashlytics.setCustomKey("community_deck_card_count", result.data.cards.size)
                    crashlytics.log("community_deck_load_success")
                    _uiState.value = CommunityDeckDetailUiState.Content(
                        deck = result.data,
                        isStale = result.isStale,
                    )
                }
                is DataResult.Error -> {
                    crashlytics.log("community_deck_load_error")
                    _uiState.value = CommunityDeckDetailUiState.Error(result.message)
                }
            }
        }
    }

    /**
     * Imports the loaded deck into a new local deck. No-ops unless we are in [CommunityDeckDetailUiState.Content]
     * and not already importing (re-entry guard).
     */
    fun importDeck() {
        val state = _uiState.value
        if (state !is CommunityDeckDetailUiState.Content || state.isImporting) return

        _uiState.update { current ->
            if (current is CommunityDeckDetailUiState.Content) {
                current.copy(isImporting = true, importProgress = 0 to state.deck.cards.size)
            } else {
                current
            }
        }

        viewModelScope.launch {
            crashlytics.log("community_deck_import_started")
            val result = importCommunityDeck(
                deck = state.deck,
                onProgress = { processed, total ->
                    _uiState.update { current ->
                        if (current is CommunityDeckDetailUiState.Content) {
                            current.copy(importProgress = processed to total)
                        } else {
                            current
                        }
                    }
                },
            )

            // Clear the importing flag regardless of outcome.
            _uiState.update { current ->
                if (current is CommunityDeckDetailUiState.Content) {
                    current.copy(isImporting = false, importProgress = null)
                } else {
                    current
                }
            }

            when (result) {
                is ImportCommunityDeckUseCase.ImportResult.Success -> {
                    val total = result.resolvedCount + result.failedCount
                    crashlytics.setCustomKey("community_deck_import_resolved", result.resolvedCount)
                    crashlytics.setCustomKey("community_deck_import_failed", result.failedCount)
                    crashlytics.log("community_deck_import_success")
                    _events.send(
                        CommunityDeckDetailEvent.ShowImportResult(
                            isError = false,
                            resolvedCount = result.resolvedCount,
                            totalCount = total,
                        ),
                    )
                    _events.send(CommunityDeckDetailEvent.NavigateToDeck(result.deckId))
                }
                is ImportCommunityDeckUseCase.ImportResult.Error -> {
                    crashlytics.log("community_deck_import_error")
                    _events.send(CommunityDeckDetailEvent.ShowImportResult(isError = true))
                }
            }
        }
    }
}
