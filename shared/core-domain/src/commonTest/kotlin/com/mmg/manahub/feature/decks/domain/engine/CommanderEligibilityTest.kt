package com.mmg.manahub.feature.decks.domain.engine

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Deck Wizard & Engine Rework plan, Workstream 2.1 (D-G) -- [CommanderEligibility] is the ONE
 * centralized rule; these tests pin its exact eligibility contract (legendary creature, "can be
 * your commander" oracle text, and the negative cases) so both production call sites
 * ([com.mmg.manahub.feature.decks.domain.template.CollectionProfileUseCase] and the wizard harness)
 * stay in sync by construction.
 */
class CommanderEligibilityTest {

    @Test
    fun `a legendary creature is eligible`() {
        val card = card(typeLine = "Legendary Creature — Elf Warrior")
        assertTrue(CommanderEligibility.isCommanderEligible(card))
    }

    @Test
    fun `case-insensitive legendary creature match`() {
        val card = card(typeLine = "legendary creature — Elf Warrior")
        assertTrue(CommanderEligibility.isCommanderEligible(card))
    }

    @Test
    fun `a card whose oracle text grants can be your commander is eligible (D-G planeswalker exception)`() {
        val card = card(
            typeLine = "Legendary Planeswalker — Freyalise",
            oracleText = "Freyalise, Skyshroud Partisan can be your commander.",
        )
        assertTrue(CommanderEligibility.isCommanderEligible(card))
    }

    @Test
    fun `the can-be-your-commander phrase match is case-insensitive`() {
        val card = card(
            typeLine = "Legendary Planeswalker — Test",
            oracleText = "This planeswalker CAN BE YOUR COMMANDER.",
        )
        assertTrue(CommanderEligibility.isCommanderEligible(card))
    }

    @Test
    fun `a non-legendary creature is not eligible`() {
        val card = card(typeLine = "Creature — Elf Warrior")
        assertFalse(CommanderEligibility.isCommanderEligible(card))
    }

    @Test
    fun `a legendary non-creature with no commander-granting oracle text is not eligible`() {
        val card = card(typeLine = "Legendary Artifact", oracleText = "Tap: draw a card.")
        assertFalse(CommanderEligibility.isCommanderEligible(card))
    }

    @Test
    fun `a legendary planeswalker with unrelated oracle text is not eligible`() {
        // The planeswalker exception requires the LITERAL "can be your commander" wording -- a
        // legendary planeswalker with no such text stays ineligible (never guessed from type line
        // alone, per D-G's textual rule).
        val card = card(
            typeLine = "Legendary Planeswalker — Test",
            oracleText = "+1: Draw a card. -3: Destroy target creature.",
        )
        assertFalse(CommanderEligibility.isCommanderEligible(card))
    }

    @Test
    fun `blank oracle text never crashes and resolves ineligible for a non-legendary-creature`() {
        val card = card(typeLine = "Sorcery", oracleText = null)
        assertFalse(CommanderEligibility.isCommanderEligible(card))
    }
}
