package com.mmg.manahub.core.domain.engine

import com.mmg.manahub.core.model.DraftDeck
import com.mmg.manahub.core.model.DraftSeat

/**
 * Builds the final deck from a completed [DraftSeat] pool.
 *
 * Algorithm (impl in ScoringDraftDeckBuilder):
 * - Identifies the top-2 colors by [DraftSeat.colorCommitment], converts letters to
 *   [com.mmg.manahub.feature.decks.domain.engine.ManaColor].
 * - Selects 23 non-land cards using [com.mmg.manahub.feature.decks.domain.engine.DeckScorer].
 * - Resolves scryfallIds for 17 basic lands (pool unlimited) via the deck-builder
 *   land auto-calculator.
 */
interface DraftDeckBuilder {
    fun build(seat: DraftSeat): DraftDeck
}
