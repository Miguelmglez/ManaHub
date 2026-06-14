package com.mmg.manahub.feature.decks.presentation.engine

import com.mmg.manahub.feature.decks.domain.engine.CardSuggestion
import com.mmg.manahub.feature.decks.domain.engine.DeckEntry
import com.mmg.manahub.feature.decks.domain.engine.GameFormat
import com.mmg.manahub.feature.decks.domain.engine.ManaColor
import com.mmg.manahub.feature.decks.domain.engine.SeedStrategy

// ═══════════════════════════════════════════════════════════════════════════════
//  Step
// ═══════════════════════════════════════════════════════════════════════════════

enum class DeckBuilderStep { SETUP, BUILDING, REVIEW }

// ═══════════════════════════════════════════════════════════════════════════════
//  Filters + top-level UiState
// ═══════════════════════════════════════════════════════════════════════════════

data class DeckBuilderFilters(
    val maxCmc: Int = 7,
)

data class DeckBuilderUiState(
    val step: DeckBuilderStep = DeckBuilderStep.SETUP,

    // ── SETUP fields ─────────────────────────────────────────────────────────
    val deckName:       String              = "",
    val format:         GameFormat          = GameFormat.CASUAL,
    val selectedColors: Set<ManaColor>      = emptySet(),
    val seedStrategy:   SeedStrategy?       = null,
    val filters:        DeckBuilderFilters  = DeckBuilderFilters(),

    // ── BUILDING fields ───────────────────────────────────────────────────────
    val currentSuggestion: CardSuggestion?   = null,
    val suggestionQueue:   List<CardSuggestion> = emptyList(),
    val mainboard:         List<DeckEntry>   = emptyList(),
    val sideboard:         List<DeckEntry>   = emptyList(),
    val skippedCount:      Int               = 0,
    val isLoadingQueue:    Boolean           = false,

    // ── REVIEW fields ─────────────────────────────────────────────────────────
    val isSaving:    Boolean = false,
    val savedDeckId: String? = null,
    val error:       String? = null,
) {
    val mainboardCount: Int   get() = mainboard.sumOf { it.quantity }
    val sideboardCount: Int   get() = sideboard.sumOf { it.quantity }
    val targetSize:     Int   get() = format.deckSize
    val progressFraction: Float get() = (mainboardCount.toFloat() / targetSize).coerceIn(0f, 1f)
    val isComplete:     Boolean get() = mainboardCount >= targetSize
    val canAdvanceSetup: Boolean get() = deckName.isNotBlank() && seedStrategy != null
    val queueIsExhausted: Boolean get() = currentSuggestion == null && !isLoadingQueue
}
