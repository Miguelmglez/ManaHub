package com.mmg.manahub.feature.decks.domain.inspirations

import com.mmg.manahub.core.domain.usecase.decks.BasicLandCalculator
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.feature.decks.domain.engine.CopyPolicy
import com.mmg.manahub.feature.decks.domain.engine.isLegalForFormat

/** One card the user picked in Browse inspirations, handed to the wizard as a seed. */
data class InspirationPick(val card: Card, val quantity: Int)

/** Why an [InspirationSelection] mutation did (or did not) change the list. */
sealed interface SelectionOutcome {
    data object Changed : SelectionOutcome
    data object Unchanged : SelectionOutcome
    data object Illegal : SelectionOutcome
    data class CopyCapReached(val maxCopies: Int) : SelectionOutcome
    data class TotalCapReached(val cap: Int) : SelectionOutcome
}

data class SelectionResult(val picks: List<InspirationPick>, val outcome: SelectionOutcome)

/** Mirrors the wizard's `onAddSeed` rules (legality, name-keyed copy cap, deck-size total cap) so a hand-off is never rejected there. */
object InspirationSelection {

    fun totalCap(format: DeckFormat): Int = format.targetDeckSize

    fun copies(picks: List<InspirationPick>): Int = picks.sumOf { it.quantity }

    fun quantityOf(picks: List<InspirationPick>, card: Card): Int = picks.firstOrNull { it.matches(card) }?.quantity ?: 0

    fun contains(picks: List<InspirationPick>, card: Card): Boolean = picks.any { it.matches(card) }

    /** Adds one copy of [card]; an existing entry for the same name keeps its own printing. */
    fun add(picks: List<InspirationPick>, card: Card, format: DeckFormat): SelectionResult {
        if (!isLegalForFormat(card, format)) return SelectionResult(picks, SelectionOutcome.Illegal)
        val existing = picks.firstOrNull { it.matches(card) }
        val maxCopies = CopyPolicy.maxSeedCopies(card, format)
        if ((existing?.quantity ?: 0) >= maxCopies) return SelectionResult(picks, SelectionOutcome.CopyCapReached(maxCopies))
        val cap = totalCap(format)
        if (copies(picks) >= cap) return SelectionResult(picks, SelectionOutcome.TotalCapReached(cap))
        val updated = if (existing != null) {
            picks.map { if (it === existing) it.copy(quantity = it.quantity + 1) else it }
        } else {
            picks + InspirationPick(card, 1)
        }
        return SelectionResult(updated, SelectionOutcome.Changed)
    }

    /** Removes one copy; the entry disappears at zero. */
    fun decrement(picks: List<InspirationPick>, card: Card): List<InspirationPick> {
        val existing = picks.firstOrNull { it.matches(card) } ?: return picks
        return if (existing.quantity <= 1) picks - existing else picks.map { if (it === existing) it.copy(quantity = it.quantity - 1) else it }
    }

    fun remove(picks: List<InspirationPick>, card: Card): List<InspirationPick> = picks.filterNot { it.matches(card) }

    /** Adds one copy of each card not selected yet; already-selected cards keep their quantity (combos sharing a piece never bump it). */
    fun addMissing(picks: List<InspirationPick>, cards: List<Card>, format: DeckFormat): SelectionResult {
        var current = picks
        var rejection: SelectionOutcome? = null
        var changed = false
        cards.forEach { card ->
            if (contains(current, card)) return@forEach
            val result = add(current, card, format)
            if (result.outcome == SelectionOutcome.Changed) {
                current = result.picks
                changed = true
            } else if (rejection == null) {
                rejection = result.outcome
            }
        }
        val outcome = rejection ?: if (changed) SelectionOutcome.Changed else SelectionOutcome.Unchanged
        return SelectionResult(current, outcome)
    }

    private fun InspirationPick.matches(other: Card): Boolean =
        if (BasicLandCalculator.isBasicLand(other)) card.scryfallId == other.scryfallId
        else card.name.equals(other.name, ignoreCase = false)
}
