package com.mmg.manahub.feature.decks.domain.engine
// COMMENTS_REVIEWED: 2026-09-16

import com.mmg.manahub.core.domain.usecase.decks.BasicLandCalculator
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.DeckFormat

/**
 * Deck Wizard 60-card wave (v6), plan §5 Phase 1.1, S2: the ONE "how many copies of this card may
 * the engine place" predicate — Commander only ever placed 1 of anything, so this concept did not
 * exist before this wave.
 */
object CopyPolicy {

    /**
     * Copies the ENGINE may place: a basic land ([BasicLandCalculator.isBasicLand]) is always
     * unlimited (R12 — ownership-exempt), a Commander-shaped [format] or a [DeckLegality.isRestricted]
     * card caps at 1 regardless of [owned], otherwise [DeckFormat.maxCopies] (4) clamped down to
     * [owned] when non-null. [owned] `null` means ownership-exempt (the caller has no owned-quantity
     * signal to clamp against — e.g. [maxSeedCopies]).
     */
    fun maxPlaceable(card: Card, format: DeckFormat, owned: Int?): Int {
        if (BasicLandCalculator.isBasicLand(card)) return Int.MAX_VALUE
        if (format.isCommanderFormat) return 1
        if (isRestricted(card, format)) return 1
        val cap = format.maxCopies
        return owned?.coerceIn(0, cap) ?: cap
    }

    /** Copies a USER may add as a seed (S3): legality/format-shape only, never owned-clamped — a
     * seed may be a card the user does not own yet (D7). */
    fun maxSeedCopies(card: Card, format: DeckFormat): Int = maxPlaceable(card, format, owned = null)
}
