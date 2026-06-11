package com.mmg.manahub.feature.draft.data.engine

import com.mmg.manahub.feature.draft.domain.model.DraftCard

/**
 * Shared rating normalisation for the draft bots.
 *
 * Both [HeuristicBotDrafter] and [ArchetypeAwareBotDrafter] need the **same** notion of a card's raw
 * power in `[0, 1]` so that the suggested-pick (which uses the engine path) and the bot picks stay
 * consistent, and so power comparisons across drafters are apples-to-apples.
 *
 * Prefers [DraftCard.pickOrderRank] (lower rank = stronger); falls back to a coarse mapping of
 * [DraftCard.tierRating] when no rank is available, and to a neutral default when neither exists.
 */
internal object DraftRatingNormalizer {

    /** Safe ceiling for pick-order ranks when normalising to `[0, 1]`. */
    const val MAX_RANK = 200f

    /**
     * Normalised tier rating in `[0, 1]`.
     *
     * @param card The card to rate.
     * @return A value in `[0, 1]`; higher is stronger.
     */
    fun ratingScore(card: DraftCard): Float {
        val rank = card.pickOrderRank
        // Ranks are 1-based (rank 1 = strongest). A rank <= 0 is invalid/sentinel data; guard it so
        // an unranked or zero-rank card does NOT compute 1.005f → 1.0f (which would make it score as
        // the best card in the set). Such cards fall through to the tier-letter / neutral default.
        if (rank != null && rank > 0) {
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
}
