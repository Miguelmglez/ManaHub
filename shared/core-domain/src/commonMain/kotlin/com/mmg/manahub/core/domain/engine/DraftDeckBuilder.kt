package com.mmg.manahub.core.domain.engine

import com.mmg.manahub.core.model.BasicLandSlot
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
 *
 * [build] is now only ever a candidate INITIAL suggestion for the Deck tab's curation UI (Draft
 * Simulator Phase D) — the actual save path assembles its own [DraftDeck] from the user's
 * mainboard/sideboard/basic-land curation, never [build] blindly. [DraftSimViewModel] still calls
 * [build] once, when a draft first finishes, to seed a sensible starting mainboard/sideboard split.
 */
interface DraftDeckBuilder {
    fun build(seat: DraftSeat): DraftDeck

    /**
     * Proportional per-color basic-land allocation for [totalLandCount], driven by [colorWeights]
     * (typically [DraftSeat.colorCommitment]). Extracted as its own method (Phase D) so the Deck
     * tab's "Magic Land Suggestions" autofill can recompute lands against a user-adjustable target
     * instead of the fixed 17-land [build] default, without duplicating the distribution math.
     */
    fun buildBasicLands(colorWeights: Map<String, Float>, totalLandCount: Int): List<BasicLandSlot>
}
