package com.mmg.manahub.feature.decks.presentation.improvement

import com.mmg.manahub.feature.decks.presentation.engine.DeckImprovementReport

data class DeckImprovementUiState(
    val deckName: String = "",
    val report: DeckImprovementReport? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val appliedSuggestions: Set<String> = emptySet(),
)



















