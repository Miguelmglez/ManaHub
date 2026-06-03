package com.mmg.manahub.feature.draft.domain.engine

import com.mmg.manahub.feature.draft.domain.model.DraftDeck
import com.mmg.manahub.feature.draft.domain.model.DraftSeat

/**
 * Builds the final deck from a completed [DraftSeat] pool.
 *
 * Algorithm (impl in ScoringDraftDeckBuilder):
 * - Identifies the top-2 colors by [DraftSeat.colorCommitment], converts letters to
 *   [com.mmg.manahub.feature.decks.presentation.engine.ManaColor].
 * - Selects 23 non-land cards using [com.mmg.manahub.feature.decks.presentation.engine.MagicScorer]
 *   and [com.mmg.manahub.feature.decks.presentation.engine.SynergyScorer].
 * - Resolves scryfallIds for 17 basic lands (pool unlimited) via the deck-builder
 *   land auto-calculator.
 */
interface DraftDeckBuilder {
    fun build(seat: DraftSeat): DraftDeck
}
