package com.mmg.manahub.feature.decks.domain.engine

// COMMENTS_REVIEWED: 2026-09-10

import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.DeckFormat

/**
 * The ONE legality predicate for deck construction and analysis (Deck Wizard v4, G13/E9).
 * Reference semantics come from the pre-existing `AnalysisEngine.isLegal` ("restricted" counts
 * as legal). Product rule R7: legality is ENFORCED for [DeckFormat.COMMANDER] and IGNORED
 * ENTIRELY for every Casual-family / non-competitively-tracked format, so a build and its own
 * analysis can never disagree on which cards are eligible.
 */
fun isLegalForFormat(card: Card, format: DeckFormat): Boolean {
    fun ok(s: String) = s.equals("legal", true) || s.equals("restricted", true)
    return when (format) {
        DeckFormat.STANDARD -> ok(card.legalityStandard)
        DeckFormat.PIONEER -> ok(card.legalityPioneer)
        DeckFormat.MODERN -> ok(card.legalityModern)
        DeckFormat.LEGACY -> ok(card.legalityLegacy)
        DeckFormat.VINTAGE -> ok(card.legalityVintage)
        DeckFormat.PAUPER -> ok(card.legalityPauper)
        DeckFormat.COMMANDER -> ok(card.legalityCommander)
        DeckFormat.COMMANDER_CASUAL -> true
        DeckFormat.CASUAL -> true
        DeckFormat.DRAFT -> true
    }
}
