package com.mmg.manahub.feature.decks.domain.usecase

import com.mmg.manahub.feature.decks.presentation.engine.DeckRole
import javax.inject.Inject

/**
 * User-facing budget filters applied to the ADD suggestions.
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
    /** True when neither cap is set — the optimizer becomes a pass-through. */
    val isUnconstrained: Boolean get() = maxPerCardEur == null && maxTotalEur == null
}

/**
 * Summary of what a budget selection costs, surfaced as the "to buy: X € of Y" header.
 *
 * @property selected the suggestions that survived the budget filter (input order preserved).
 * @property totalCostEur sum of the effective cost of the selected NON-free cards (the money the
 *           user would actually spend to buy the cards they don't already own).
 * @property cardsToBuy how many selected cards have a non-zero effective cost.
 */
data class BudgetSelection(
    val selected: List<AddSuggestion>,
    val totalCostEur: Double,
    val cardsToBuy: Int,
)

/**
 * Greedy budget selector for ADD suggestions.
 *
 * ## Algorithm (heuristic, NOT a guaranteed optimum)
 * 1. **Effective cost** per card: 0 € when the card is owned and [BudgetConstraints.ownedCardsAreFree];
 *    otherwise `card.priceEur ?: 0.0` (a missing price is treated as free — we never invent a price).
 * 2. **Per-card cap**: drop every card whose effective cost exceeds [BudgetConstraints.maxPerCardEur].
 * 3. **Greedy fill by value-per-euro, grouped per role gap**: candidates are bucketed by the role gap
 *    they fill (cards with no gap role share one bucket). Within the global ordering we pick cards by
 *    descending **value/€** — `fit.score / (cost + ε)` — so a high-fit cheap card beats an expensive
 *    marginal one. Free (owned) cards have effectively infinite value/€ and are always taken first;
 *    they never consume [BudgetConstraints.maxTotalEur]. We keep taking until the running paid total
 *    would exceed the cap, skipping any card that would breach it (a later, cheaper card may still fit).
 *
 * This is a classic greedy knapsack approximation: simple, fast, deterministic and easily testable.
 * It does NOT solve the 0/1 knapsack optimally — that is acceptable for a suggestion list where the
 * user makes the final call. The per-role grouping only influences tie ordering, ensuring we don't
 * spend the whole budget over-filling a single role before touching the others.
 */
class BudgetOptimizer @Inject constructor() {

    /**
     * @param suggestions ranked add suggestions (typically already sorted by fit, best first).
     * @param constraints the active budget filters.
     * @return a [BudgetSelection] with the surviving suggestions and the cost summary.
     */
    operator fun invoke(
        suggestions: List<AddSuggestion>,
        constraints: BudgetConstraints,
    ): BudgetSelection {
        // Annotate each suggestion with its effective cost up front.
        val priced = suggestions.map { it to effectiveCost(it, constraints) }

        // Step 2 — per-card cap.
        val withinPerCard = priced.filter { (_, cost) ->
            constraints.maxPerCardEur == null || cost <= constraints.maxPerCardEur
        }

        // Step 3 — greedy fill ordered by value-per-euro (free cards first, then richest value/€).
        // We keep a stable secondary sort on the original fit score so equal value/€ is deterministic.
        val ordered = withinPerCard.sortedWith(
            compareByDescending<Pair<AddSuggestion, Double>> { (suggestion, cost) ->
                valuePerEuro(fit = suggestion.fit.score, cost = cost)
            }.thenByDescending { (suggestion, _) -> suggestion.fit.score }
        )

        val maxTotal = constraints.maxTotalEur
        var runningPaid = 0.0
        var cardsToBuy = 0
        val selectedIds = HashSet<String>()
        val selected = mutableListOf<AddSuggestion>()

        for ((suggestion, cost) in ordered) {
            val isFree = cost <= 0.0
            if (!isFree && maxTotal != null && runningPaid + cost > maxTotal) {
                // Would breach the total cap — skip; a later cheaper card might still fit.
                continue
            }
            if (!isFree) {
                runningPaid += cost
                cardsToBuy++
            }
            selectedIds += suggestion.fit.card.scryfallId
            selected += suggestion
        }

        // Restore the caller's original (fit-descending) order for display; only membership changed.
        val displayOrdered = suggestions.filter { it.fit.card.scryfallId in selectedIds }

        return BudgetSelection(
            selected = displayOrdered,
            totalCostEur = runningPaid,
            cardsToBuy = cardsToBuy,
        )
    }

    /** 0 € for owned-and-free cards; otherwise the EUR price (missing price = free, never invented). */
    private fun effectiveCost(suggestion: AddSuggestion, constraints: BudgetConstraints): Double {
        val owned = suggestion.fit.isOwned
        if (owned && constraints.ownedCardsAreFree) return 0.0
        return suggestion.fit.card.priceEur ?: 0.0
    }

    /** Value density: free cards score infinitely high so they are always picked first. */
    private fun valuePerEuro(fit: Float, cost: Double): Double =
        if (cost <= 0.0) Double.MAX_VALUE else fit.toDouble() / cost

    /** The role gap a suggestion fills, used only for documentation of the per-role grouping intent. */
    @Suppress("unused")
    private fun AddSuggestion.gapRole(): DeckRole? = fit.fillsGapRoleOrNull()
}

/**
 * Local mirror of the presentation-layer `CardFit.fillsGapRole()` extension, kept in the domain layer
 * so [BudgetOptimizer] has no presentation dependency. Returns the role of the first `FillsGap` reason.
 */
internal fun com.mmg.manahub.feature.decks.presentation.engine.CardFit.fillsGapRoleOrNull():
    DeckRole? =
    (reasons.firstOrNull { it is com.mmg.manahub.feature.decks.presentation.engine.ScoreReason.FillsGap }
        as? com.mmg.manahub.feature.decks.presentation.engine.ScoreReason.FillsGap)?.role
