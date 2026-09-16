package com.mmg.manahub.feature.decks.domain.engine

import com.mmg.manahub.core.model.DeckFormat
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Deck Wizard v4, W0.1 (G13/E9/R7): ONE legality predicate, shared by the builder and the analysis. */
class DeckLegalityTest {

    @Test
    fun `a banned Commander card is excluded for COMMANDER`() {
        val banned = card(legalityCommander = "banned")
        assertFalse(isLegalForFormat(banned, DeckFormat.COMMANDER))
    }

    @Test
    fun `a banned Commander card is INCLUDED for COMMANDER_CASUAL -- R7 ignores legality for Casual`() {
        val banned = card(legalityCommander = "banned")
        assertTrue(isLegalForFormat(banned, DeckFormat.COMMANDER_CASUAL))
    }

    @Test
    fun `restricted counts as legal for COMMANDER`() {
        val restricted = card(legalityCommander = "restricted")
        assertTrue(isLegalForFormat(restricted, DeckFormat.COMMANDER))
    }

    @Test
    fun `CASUAL and DRAFT are always permissive regardless of any legality field`() {
        val notLegalAnywhere = card(
            legalityCommander = "banned",
            legalityStandard = "banned",
        )
        assertTrue(isLegalForFormat(notLegalAnywhere, DeckFormat.CASUAL))
        assertTrue(isLegalForFormat(notLegalAnywhere, DeckFormat.DRAFT))
    }

    // ── isRestricted (v6, plan §5 Phase 1.5, S2) ────────────────────────────────────────

    @Test
    fun `isRestricted is true for a Vintage-restricted card in VINTAGE`() {
        val restricted = card(legalityVintage = "restricted")
        assertTrue(isRestricted(restricted, DeckFormat.VINTAGE))
    }

    @Test
    fun `isRestricted is false for a legal card in VINTAGE`() {
        val legal = card(legalityVintage = "legal")
        assertFalse(isRestricted(legal, DeckFormat.VINTAGE))
    }

    @Test
    fun `isRestricted is false for the same restricted-in-Vintage card in a different format`() {
        val restricted = card(legalityVintage = "restricted")
        assertFalse(isRestricted(restricted, DeckFormat.MODERN))
    }

    @Test
    fun `isLegalForFormat still treats a Vintage-restricted card as legal`() {
        val restricted = card(legalityVintage = "restricted")
        assertTrue(isLegalForFormat(restricted, DeckFormat.VINTAGE))
    }
}
