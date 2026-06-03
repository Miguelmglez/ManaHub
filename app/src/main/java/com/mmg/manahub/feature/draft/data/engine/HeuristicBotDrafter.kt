package com.mmg.manahub.feature.draft.data.engine

import com.mmg.manahub.feature.draft.domain.engine.BotDrafter
import com.mmg.manahub.feature.draft.domain.model.BoosterPack
import com.mmg.manahub.feature.draft.domain.model.DraftCard
import com.mmg.manahub.feature.draft.domain.model.DraftSeat

/**
 * Heuristic two-phase bot drafter.
 *
 * Each pick is scored as:
 * ```
 * score = ratingScore + colorBonus × phaseFactor + synergyBonus + curveBonus + opennessBonus
 * ```
 *
 * The drafter operates in two phases derived from the accumulated color commitment of the seat:
 * - **Speculation phase** — no two colors dominate yet. Small color bonus, openness bonus active
 *   (rewards picking into colors where strong cards were passed).
 * - **Commitment phase** — the top-2 colors dominate by a clear margin. On-color cards get a strong
 *   bonus; off-color cards are implicitly penalised by receiving no bonus while committed cards do.
 *
 * Per-seat state ([colorCommitment] and [seenSignal]) is maintained internally keyed by
 * [DraftSeat.index]. This is intentionally stateful: a single drafter instance is used for the
 * whole draft session, so it can learn the open colors over successive picks even though the
 * [BotDrafter] contract only returns the picked card. Call [resetState] between independent drafts
 * (e.g. in tests) to clear accumulated state.
 */
class HeuristicBotDrafter : BotDrafter {

    /** Accumulated, per-seat learning state. Keys are single-letter color symbols. */
    private data class SeatState(
        val colorCommitment: Map<String, Float> = emptyMap(),
        val seenSignal: Map<String, Float> = emptyMap(),
    )

    private val seatStates = HashMap<Int, SeatState>()

    /** Clears all accumulated per-seat state. Intended for tests that reuse one instance. */
    fun resetState() {
        seatStates.clear()
    }

    override fun pick(seat: DraftSeat, pack: BoosterPack, round: Int, pickNumber: Int): DraftCard {
        val state = seatStates.getOrDefault(seat.index, SeatState())
        val best = pack.cards.maxByOrNull { card -> score(card, state, seat.pool) }
            ?: pack.cards.first()
        seatStates[seat.index] = updateState(state, best, pack.cards - best)
        return best
    }

    // ── Scoring ──────────────────────────────────────────────────────────────

    private fun score(card: DraftCard, state: SeatState, pool: List<DraftCard>): Float {
        val rating = ratingScore(card)
        val totalCommitment = state.colorCommitment.values.sum()
        val speculation = isSpeculationPhase(state.colorCommitment)
        val phaseFactor = if (speculation) PHASE_FACTOR_SPECULATION else PHASE_FACTOR_COMMITMENT

        // Normalised commitment to each of the card's colors.
        val colorBonus = if (totalCommitment <= 0f) {
            0f
        } else {
            card.card.colors.sumOf { color ->
                (state.colorCommitment[color] ?: 0f).toDouble() / totalCommitment
            }.toFloat()
        }

        // Openness reward — only while still speculating about colors.
        val opennessBonus = if (speculation) {
            card.card.colors.sumOf { color ->
                ((state.seenSignal[color] ?: 0f) * OPENNESS_WEIGHT).toDouble()
            }.toFloat()
        } else {
            0f
        }

        // Off-color penalty — once committed, a card that has at least one color outside the
        // committed pair is penalised so the pool stays focused. Colorless cards (no colors) are
        // never penalised. Applied in the commitment phase only.
        val offColorPenalty = if (!speculation && isOffColor(card, state.colorCommitment)) {
            OFF_COLOR_PENALTY
        } else {
            0f
        }

        val synergyBonus = synergyBonus(card, pool)
        val curveBonus = curveBonus(card, pool)

        return rating + colorBonus * phaseFactor + synergyBonus + curveBonus +
            opennessBonus + offColorPenalty
    }

    /** True if the card has any color outside the seat's current top-2 committed colors. */
    private fun isOffColor(card: DraftCard, commitment: Map<String, Float>): Boolean {
        if (card.card.colors.isEmpty()) return false
        val committed = commitment.entries
            .filter { it.value > 0f }
            .sortedByDescending { it.value }
            .take(2)
            .map { it.key }
            .toSet()
        if (committed.isEmpty()) return false
        return card.card.colors.any { it !in committed }
    }

    /**
     * Normalised tier rating in `[0, 1]`. Prefers [DraftCard.pickOrderRank] (lower rank = stronger);
     * falls back to a coarse mapping of [DraftCard.tierRating] when no rank is available.
     */
    private fun ratingScore(card: DraftCard): Float {
        val rank = card.pickOrderRank
        if (rank != null) {
            return (1.0f - (rank - 1).toFloat() / MAX_RANK).coerceIn(0f, 1f)
        }
        return when (card.tierRating?.uppercase()) {
            "S" -> 0.90f
            "A" -> 0.75f
            "B" -> 0.55f
            "C" -> 0.35f
            "D" -> 0.20f
            "F" -> 0.05f
            else -> 0.30f
        }
    }

    private fun synergyBonus(card: DraftCard, pool: List<DraftCard>): Float {
        var bonus = 0f
        if (card.card.typeLine.contains("Creature", ignoreCase = true)) {
            val creatureCount = pool.count { it.card.typeLine.contains("Creature", ignoreCase = true) }
            if (creatureCount < CREATURE_TARGET) bonus += 0.10f
        }
        if (card.card.cmc <= 2.0 && poolAverageCmc(pool) > 3.0) {
            bonus += 0.05f
        }
        return bonus
    }

    private fun curveBonus(card: DraftCard, pool: List<DraftCard>): Float {
        return when {
            card.card.cmc <= 2.0 -> 0.05f
            card.card.cmc >= 5.0 && pool.count { it.card.cmc >= 5.0 } >= 3 -> -0.05f
            else -> 0.0f
        }
    }

    private fun poolAverageCmc(pool: List<DraftCard>): Double =
        if (pool.isEmpty()) 0.0 else pool.sumOf { it.card.cmc } / pool.size

    // ── Phase detection ────────────────────────────────────────────────────────

    /**
     * Returns true while the seat is still speculating about its colors — i.e. the top-2 colors do
     * not dominate the rest by at least [COMMITMENT_MARGIN] of the total commitment. An empty
     * commitment map is always speculation.
     */
    private fun isSpeculationPhase(commitment: Map<String, Float>): Boolean {
        val total = commitment.values.sum()
        if (total <= 0f) return true
        val sorted = commitment.values.sortedDescending()
        val topTwo = sorted.take(2).sum()
        val rest = total - topTwo
        // Margin = how much the top-2 outweigh the rest, normalised by total.
        val margin = (topTwo - rest) / total
        return margin < COMMITMENT_MARGIN
    }

    // ── State updates ────────────────────────────────────────────────────────

    private fun updateState(
        state: SeatState,
        picked: DraftCard,
        passed: List<DraftCard>,
    ): SeatState {
        val pickedWeight = ratingScore(picked)

        // Commit to the colors of the picked card, weighted by its strength.
        val newCommitment = state.colorCommitment.toMutableMap()
        for (color in picked.card.colors) {
            newCommitment[color] = (newCommitment[color] ?: 0f) + pickedWeight
        }

        // Record open-color signal from strong cards we are passing.
        val newSignal = state.seenSignal.toMutableMap()
        for (card in passed) {
            val strength = ratingScore(card)
            if (strength > STRONG_SIGNAL_THRESHOLD) {
                for (color in card.card.colors) {
                    newSignal[color] = (newSignal[color] ?: 0f) + strength * SIGNAL_DECAY
                }
            }
        }

        return state.copy(colorCommitment = newCommitment, seenSignal = newSignal)
    }

    private companion object {
        /** Safe ceiling for pick-order ranks when normalising to `[0, 1]`. */
        const val MAX_RANK = 200f

        const val PHASE_FACTOR_SPECULATION = 0.2f
        const val PHASE_FACTOR_COMMITMENT = 0.6f

        /** Top-2 colors must outweigh the rest by this fraction of total commitment to commit. */
        const val COMMITMENT_MARGIN = 0.3f

        const val OPENNESS_WEIGHT = 0.1f
        const val STRONG_SIGNAL_THRESHOLD = 0.6f
        const val SIGNAL_DECAY = 0.3f

        /** Subtractive penalty for off-color cards once the seat has committed to its colors. */
        const val OFF_COLOR_PENALTY = -0.35f

        /** Below this many creatures in the pool, creatures get a small synergy nudge. */
        const val CREATURE_TARGET = 10
    }
}
