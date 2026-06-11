package com.mmg.manahub.feature.draft.data.engine

import com.mmg.manahub.feature.draft.domain.engine.BotDrafter
import com.mmg.manahub.feature.draft.domain.model.BoosterPack
import com.mmg.manahub.feature.draft.domain.model.DraftCard
import com.mmg.manahub.feature.draft.domain.model.DraftSeat
import com.mmg.manahub.feature.draft.domain.model.EngineConfig

/**
 * Heuristic two-phase bot drafter.
 *
 * Each pick is scored as:
 * ```
 * score = ratingScore + colorBonus × phaseFactor + synergyBonus + curveBonus + offColorPenalty
 * ```
 *
 * The drafter operates in two phases derived from the seat's drafted pool:
 * - **Speculation phase** — early in the draft or when no two colors dominate yet. Small color bonus.
 * - **Commitment phase** — the top-2 colors dominate by a clear margin (and we have enough picks).
 *   On-color cards get a strong bonus; off-color cards receive a penalty.
 *
 * This drafter is pure and stateless. It derives all its color commitments dynamically from
 * [DraftSeat.pool], making it safe to use for suggestions on the human seat without side effects.
 */
class HeuristicBotDrafter : BotDrafter {

    override fun pick(
        seat: DraftSeat,
        pack: BoosterPack,
        round: Int,
        pickNumber: Int,
        engine: EngineConfig?,
    ): DraftCard {
        // A pick can never be made from an empty pack; fail fast rather than crash on the
        // `?: pack.cards.first()` fallback below (which would throw NoSuchElementException).
        require(pack.cards.isNotEmpty()) { "HeuristicBotDrafter.pick called with an empty pack" }
        // The heuristic drafter intentionally ignores [engine]; it is the colour-commitment fallback
        // used when a set has no engine.json.
        val commitment = buildColorCommitment(seat.pool)
        return pack.cards.maxByOrNull { card -> score(card, commitment, seat.pool) }
            ?: pack.cards.first()
    }

    // ── Scoring ──────────────────────────────────────────────────────────────

    private fun score(card: DraftCard, commitment: Map<String, Float>, pool: List<DraftCard>): Float {
        val rating = ratingScore(card)
        val totalCommitment = commitment.values.sum()
        val speculation = isSpeculationPhase(commitment, pool.size)
        val phaseFactor = if (speculation) PHASE_FACTOR_SPECULATION else PHASE_FACTOR_COMMITMENT

        // Normalised commitment to each of the card's colors.
        val colorBonus = if (totalCommitment <= 0f) {
            0f
        } else {
            card.card.colors.sumOf { color ->
                (commitment[color] ?: 0f).toDouble() / totalCommitment
            }.toFloat()
        }

        // Off-color penalty — once committed, a card that has at least one color outside the
        // committed pair is penalised so the pool stays focused. Colorless cards (no colors) are
        // never penalised. Applied in the commitment phase only.
        val offColorPenalty = if (!speculation && isOffColor(card, commitment)) {
            OFF_COLOR_PENALTY
        } else {
            0f
        }

        val synergyBonus = synergyBonus(card, pool)
        val curveBonus = curveBonus(card, pool)

        return rating + colorBonus * phaseFactor + synergyBonus + curveBonus + offColorPenalty
    }

    private fun buildColorCommitment(pool: List<DraftCard>): Map<String, Float> {
        val commitment = mutableMapOf<String, Float>()
        for (card in pool) {
            val weight = ratingScore(card)
            for (color in card.card.colors) {
                commitment[color] = (commitment[color] ?: 0f) + weight
            }
        }
        return commitment
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

    /** Normalised tier rating in `[0, 1]`; shared with [ArchetypeAwareBotDrafter]. */
    private fun ratingScore(card: DraftCard): Float = DraftRatingNormalizer.ratingScore(card)

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
     * Returns true while the seat is still speculating about its colors — i.e. early in the draft
     * or the top-2 colors do not dominate the rest by at least [COMMITMENT_MARGIN].
     */
    private fun isSpeculationPhase(commitment: Map<String, Float>, poolSize: Int): Boolean {
        val total = commitment.values.sum()
        // Prevent hard commitment before the bot has picked enough cards to establish a real signal.
        if (total < 4.0f || poolSize < 12) return true

        val sorted = commitment.values.sortedDescending()
        val topTwo = sorted.take(2).sum()
        val rest = total - topTwo
        // Margin = how much the top-2 outweigh the rest, normalised by total.
        val margin = (topTwo - rest) / total
        return margin < COMMITMENT_MARGIN
    }

    private companion object {
        const val PHASE_FACTOR_SPECULATION = 0.2f
        const val PHASE_FACTOR_COMMITMENT = 0.6f

        /** Top-2 colors must outweigh the rest by this fraction of total commitment to commit. */
        const val COMMITMENT_MARGIN = 0.3f

        /** Subtractive penalty for off-color cards once the seat has committed to its colors. */
        const val OFF_COLOR_PENALTY = -0.35f

        /** Below this many creatures in the pool, creatures get a small synergy nudge. */
        const val CREATURE_TARGET = 10
    }
}
