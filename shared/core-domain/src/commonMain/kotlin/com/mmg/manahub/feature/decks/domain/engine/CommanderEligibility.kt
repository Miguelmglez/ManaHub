package com.mmg.manahub.feature.decks.domain.engine

import com.mmg.manahub.core.model.Card

// ═══════════════════════════════════════════════════════════════════════════════
//  CommanderEligibility — Deck Wizard & Engine Rework plan (docs/plans/deck-wizard-rework-plan.md),
//  Workstream 2.1, decision D-G.
//
//  Before this file, commander eligibility was checked in TWO independently-maintained places, both
//  Legendary-Creature-only (missing D-G's "can be your commander" exception entirely):
//   - CollectionProfileUseCase.isCommanderEligible (shared/core-domain, production)
//   - HarnessFixtures.isCommanderEligible (app test harness, a hand duplicate)
//  Both now delegate here — this is the ONLY place the rule is defined.
//
//  D-G: "Commander eligibility is NOT strictly 'legendary creature': cards whose oracle text grants
//  'can be your commander' (planeswalker exceptions, Backgrounds' partners, etc.) are INCLUDED."
//  The outside-collection path (Scryfall `is:commander`) encodes this server-side already — this
//  local-collection filter is the client-side equivalent: (Legendary ∧ Creature in type line) ∨
//  (oracle text contains "can be your commander").
//
//  Scope note: this does NOT special-case Background cards (Enchantment — Background) as
//  commander-eligible on their own. A Background is only ever a SECOND/co-commander, granted by a
//  game rule tied to its partner creature's own "Choose a Background" ability — the Background card
//  itself carries no "can be your commander" oracle text, and this wizard only supports picking ONE
//  commander (no partner/co-commander slot exists in the UI). If a future workstream adds partner/
//  background support, extend this helper deliberately rather than guessing at Background eligibility
//  here.
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * The single, centralized commander-eligibility rule (D-G). Both [CollectionProfileUseCase] and the
 * wizard test harness (`HarnessFixtures`) delegate to this — extend the phrase list here if a future
 * card ships a new "can be your commander" wording variant, never re-duplicate the check.
 */
object CommanderEligibility {

    /**
     * True when [card] can be picked as a Commander per D-G: a Legendary Creature, OR a card whose
     * oracle text explicitly grants commander eligibility (the literal phrasing Wizards prints for
     * planeswalker-commander and similar exceptions).
     */
    fun isCommanderEligible(card: Card): Boolean {
        val typeLine = card.typeLine
        if (typeLine.contains("Legendary", ignoreCase = true) && typeLine.contains("Creature", ignoreCase = true)) {
            return true
        }
        val oracle = card.oracleText.orEmpty()
        return CAN_BE_COMMANDER_PHRASES.any { phrase -> oracle.contains(phrase, ignoreCase = true) }
    }

    /** Known "can be your commander" oracle-text phrasings (D-G). Extend, never replace — a new
     * card wording is additive, not a taxonomy change. */
    private val CAN_BE_COMMANDER_PHRASES = listOf(
        "can be your commander",
    )
}
