package com.mmg.manahub.feature.decks.presentation.improvement

import com.mmg.manahub.feature.decks.domain.usecase.AddSuggestion
import com.mmg.manahub.feature.decks.domain.usecase.BudgetConstraints
import com.mmg.manahub.feature.decks.domain.usecase.DeckHealth
import com.mmg.manahub.feature.decks.presentation.engine.CardFit

/** Top-level tabs of the Deck Doctor screen. */
enum class DeckDoctorTab { HEALTH, CUT, ADD }

data class DeckImprovementUiState(
    val deckName: String = "",
    /** Read-only Health evaluation from the scoring engine. Null until computed. */
    val health: DeckHealth? = null,
    /** Cut candidates (worst fit first), excluding lands / commander / combo cores. */
    val cuts: List<CardFit> = emptyList(),
    /** Add suggestions (collection + wishlist + external), budget-filtered, best fit first. */
    val adds: List<AddSuggestion> = emptyList(),
    /** Active budget filters for the ADD tab. */
    val budget: BudgetConstraints = BudgetConstraints(),
    /** Total € the currently shown adds would cost to buy (owned/free cards excluded). */
    val addsTotalCostEur: Double = 0.0,
    /** How many of the shown adds have a non-zero price (i.e. need buying). */
    val addsCardsToBuy: Int = 0,
    /** True while the external (Scryfall) candidate pool is being fetched/recomputed. */
    val isAddsLoading: Boolean = false,
    val selectedTab: DeckDoctorTab = DeckDoctorTab.HEALTH,
    val isLoading: Boolean = false,
    val error: String? = null,
    val appliedSuggestions: Set<String> = emptySet(),
)
