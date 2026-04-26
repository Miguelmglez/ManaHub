package com.mmg.manahub.feature.decks.engine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.manahub.core.domain.model.Deck
import com.mmg.manahub.core.domain.repository.DeckRepository
import com.mmg.manahub.core.domain.repository.UserCardRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DeckBuilderEngine @Inject constructor(
    private val userCardRepo: UserCardRepository,
    private val deckRepo:     DeckRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(DeckBuilderUiState())
    val state: StateFlow<DeckBuilderUiState> = _state.asStateFlow()

    // ── Setup ─────────────────────────────────────────────────────────────────

    fun setDeckName(name: String) = _state.update { it.copy(deckName = name) }

    fun setFormat(format: GameFormat) = _state.update { it.copy(format = format) }

    fun toggleColor(color: ManaColor) = _state.update { s ->
        val colors = if (color in s.selectedColors) s.selectedColors - color
                     else s.selectedColors + color
        s.copy(selectedColors = colors)
    }

    fun setSeedStrategy(strategy: SeedStrategy) =
        _state.update { it.copy(seedStrategy = strategy) }

    // ── Step navigation ───────────────────────────────────────────────────────

    fun goToBuilding() {
        if (!_state.value.canAdvanceSetup) return
        viewModelScope.launch {
            _state.update { it.copy(step = DeckBuilderStep.BUILDING, isLoadingQueue = true) }
            buildSuggestionQueue()
        }
    }

    fun goToReview() = _state.update { it.copy(step = DeckBuilderStep.REVIEW) }

    fun goBack() = _state.update { s ->
        when (s.step) {
            DeckBuilderStep.SETUP    -> s
            DeckBuilderStep.BUILDING -> s.copy(step = DeckBuilderStep.SETUP)
            DeckBuilderStep.REVIEW   -> s.copy(step = DeckBuilderStep.BUILDING)
        }
    }

    // ── Building decisions ────────────────────────────────────────────────────

    fun decide(decision: PathDecision) = _state.update { s ->
        val suggestion = s.currentSuggestion ?: return@update s
        val entry = DeckEntry(
            card        = suggestion.card,
            quantity    = 1,
            isOwned     = suggestion.isOwned,
            isSideboard = decision == PathDecision.SIDEBOARD,
        )
        val mainboard = if (decision == PathDecision.ADD)       s.mainboard  + entry else s.mainboard
        val sideboard = if (decision == PathDecision.SIDEBOARD) s.sideboard  + entry else s.sideboard
        val skipped   = if (decision == PathDecision.SKIP)      s.skippedCount + 1   else s.skippedCount
        val remaining = s.suggestionQueue.drop(1)
        s.copy(
            mainboard         = mainboard,
            sideboard         = sideboard,
            skippedCount      = skipped,
            suggestionQueue   = remaining,
            currentSuggestion = remaining.firstOrNull(),
        )
    }

    fun removeFromMainboard(scryfallId: String) = _state.update { s ->
        s.copy(mainboard = s.mainboard.filter { it.card.scryfallId != scryfallId })
    }

    fun removeFromSideboard(scryfallId: String) = _state.update { s ->
        s.copy(sideboard = s.sideboard.filter { it.card.scryfallId != scryfallId })
    }

    fun moveToSideboard(scryfallId: String) = _state.update { s ->
        val entry = s.mainboard.find { it.card.scryfallId == scryfallId } ?: return@update s
        s.copy(
            mainboard = s.mainboard.filter { it.card.scryfallId != scryfallId },
            sideboard = s.sideboard + entry.copy(isSideboard = true),
        )
    }

    fun clearError() = _state.update { it.copy(error = null) }

    // ── Save ──────────────────────────────────────────────────────────────────

    fun saveDeck() {
        val s = _state.value
        if (s.mainboard.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null) }
            try {
                val deckId = deckRepo.createDeck(
                    name        = s.deckName,
                    description = "${s.seedStrategy?.displayName ?: "Custom"} — built by Æther",
                    format      = s.format.name.lowercase(),
                )
                s.mainboard.forEach { e ->
                    deckRepo.addCardToDeck(deckId, e.card.scryfallId, e.quantity, false)
                }
                s.sideboard.forEach { e ->
                    deckRepo.addCardToDeck(deckId, e.card.scryfallId, e.quantity, true)
                }
                _state.update { it.copy(isSaving = false, savedDeckId = deckId) }
            } catch (e: Exception) {
                _state.update { it.copy(isSaving = false, error = e.message) }
            }
        }
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private suspend fun buildSuggestionQueue() {
        val s        = _state.value
        val strategy = s.seedStrategy ?: return
        val alreadyIn = (s.mainboard + s.sideboard).map { it.card.scryfallId }.toSet()

        val collection = userCardRepo.observeCollection().first()
        val suggestions = collection
            .filter { it.card.scryfallId !in alreadyIn }
            .map { uwc ->
                SynergyScorer.score(
                    card           = uwc.card,
                    seedTags       = strategy.primaryTags,
                    selectedColors = s.selectedColors,
                    mainboard      = s.mainboard,
                    isOwned        = true,
                )
            }
            .filter { it.score > 0f }
            .sortedByDescending { it.score }

        _state.update { it.copy(
            suggestionQueue   = suggestions,
            currentSuggestion = suggestions.firstOrNull(),
            isLoadingQueue    = false,
        ) }
    }
}
