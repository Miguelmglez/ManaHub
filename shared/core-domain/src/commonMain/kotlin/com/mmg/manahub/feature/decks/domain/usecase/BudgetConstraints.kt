package com.mmg.manahub.feature.decks.domain.usecase

/**
 * User-facing budget filters applied to the ADD suggestions.
 *
 * Originally declared alongside the dormant budget-suggestions pipeline in `BudgetOptimizer.kt`,
 * which was DELETED in the Deck Wizard & Engine Rework plan, WS7.3 (2026-07-28, D-H: the budget
 * feature is not coming back). This class itself SURVIVED the deletion — unlike `BudgetOptimizer`/
 * `BudgetSelection`/`SuggestAddsWithBudgetUseCase`, it is still a genuinely live type: threaded
 * through [com.mmg.manahub.feature.decks.domain.orchestrator.DeckDoctorOrchestrator]'s public API
 * and [com.mmg.manahub.feature.decks.presentation.DeckStudioViewModel]'s free-text budget state
 * (`rawPerCardText`/`rawTotalText`/`ownedCardsAreFree` — U7), even though `DeckDoctorOrchestrator`
 * itself no longer reads the constraint VALUES for anything (Motor A ignores them entirely, per
 * its own `recomputeAdds`/`recomputeAddsInternal` KDoc) — the parameter is accepted purely for
 * signature compatibility with the pre-Motor-A budget pipeline. Moved to its own file so deleting
 * `BudgetOptimizer.kt` didn't also delete this still-referenced type.
 *
 * @property maxPerCardEur drop any candidate whose effective cost exceeds this. Null = no per-card cap.
 * @property maxTotalEur stop selecting once the accumulated effective cost reaches this. Null = no cap.
 * @property ownedCardsAreFree when true, cards already in the user's collection cost 0 € and never
 *           consume [maxTotalEur] (you already own them, adding them to the deck costs nothing).
 */
data class BudgetConstraints(
    val maxPerCardEur: Double? = null,
    val maxTotalEur: Double? = null,
    val ownedCardsAreFree: Boolean = true,
) {
    init {
        require(maxPerCardEur == null || (maxPerCardEur.isFinite() && maxPerCardEur > 0.0)) {
            "maxPerCardEur must be a positive finite value or null, got: $maxPerCardEur"
        }
        require(maxTotalEur == null || (maxTotalEur.isFinite() && maxTotalEur > 0.0)) {
            "maxTotalEur must be a positive finite value or null, got: $maxTotalEur"
        }
    }

    /** True when neither cap is set — the optimizer becomes a pass-through. */
    val isUnconstrained: Boolean get() = maxPerCardEur == null && maxTotalEur == null
}
