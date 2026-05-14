package com.mmg.manahub.feature.decks.presentation.improvement

import com.mmg.manahub.feature.decks.presentation.engine.DeckImprovementReport

data class DeckImprovementUiState(
    val deckName: String = "",
    val report: DeckImprovementReport? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val appliedSuggestions: Set<String> = emptySet(),
    
    val cards: List<com.mmg.manahub.core.domain.model.DeckCard> = emptyList(),
    val totalCards: Int = 0,
    val targetCount: Int = 0,
    val manaCurve: Map<Int, Int> = emptyMap(),
    val maxInCurve: Int = 0
)


















