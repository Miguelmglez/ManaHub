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
}
