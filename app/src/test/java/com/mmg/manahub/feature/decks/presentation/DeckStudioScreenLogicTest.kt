package com.mmg.manahub.feature.decks.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Deck Wizard v4, Run 7 (R14 correction + data-loss bugfix): pure decision functions extracted
 * from [DeckStudioScreen]'s empty-state and wizard-entry-point logic, so both are testable without
 * a Compose harness.
 */
class DeckStudioScreenLogicTest {

    // -- resolveEmptyDeckStateOptions --------------------------------------------------------

    @Test
    fun `commander format, seed and inspirations enabled -- seed is the only primary`() {
        val options = resolveEmptyDeckStateOptions(
            seedEnabled = true,
            isCommanderFormat = true,
            inspirationsEnabled = true,
        )
        assertTrue(options.showSeed)
        assertTrue(options.seedIsPrimary)
        assertTrue(options.showInspirations)
        assertTrue(!options.inspirationsIsPrimary)
        assertTrue(!options.importIsPrimary)
    }

    @Test
    fun `non-commander format -- seed is hidden even though the flag is on`() {
        val options = resolveEmptyDeckStateOptions(
            seedEnabled = true,
            isCommanderFormat = false,
            inspirationsEnabled = true,
        )
        assertTrue(!options.showSeed)
        assertTrue(options.showInspirations)
        assertTrue(options.inspirationsIsPrimary)
        assertTrue(!options.importIsPrimary)
    }

    @Test
    fun `non-commander format, inspirations disabled -- import becomes primary`() {
        val options = resolveEmptyDeckStateOptions(
            seedEnabled = true,
            isCommanderFormat = false,
            inspirationsEnabled = false,
        )
        assertTrue(!options.showSeed)
        assertTrue(!options.showInspirations)
        assertTrue(options.importIsPrimary)
    }

    @Test
    fun `commander format but seed flag off -- behaves like non-commander`() {
        val options = resolveEmptyDeckStateOptions(
            seedEnabled = false,
            isCommanderFormat = true,
            inspirationsEnabled = true,
        )
        assertTrue(!options.showSeed)
        assertTrue(options.inspirationsIsPrimary)
    }

    @Test
    fun `exactly one primary option across the full seed x inspirations matrix, per format`() {
        for (isCommanderFormat in listOf(true, false)) {
            for (seedEnabled in listOf(true, false)) {
                for (inspirationsEnabled in listOf(true, false)) {
                    val options = resolveEmptyDeckStateOptions(seedEnabled, isCommanderFormat, inspirationsEnabled)
                    val primaryCount = listOfNotNull(
                        options.seedIsPrimary.takeIf { options.showSeed },
                        options.inspirationsIsPrimary.takeIf { options.showInspirations },
                        options.importIsPrimary.takeIf { true },
                    ).count { it }
                    assertEquals(
                        "commander=$isCommanderFormat seed=$seedEnabled inspirations=$inspirationsEnabled",
                        1,
                        primaryCount,
                    )
                }
            }
        }
    }

    // -- resolveWizardNavDecision -------------------------------------------------------------

    @Test
    fun `commander format, empty deck -- navigates immediately`() {
        assertEquals(
            WizardNavDecision.NAVIGATE_NOW,
            resolveWizardNavDecision(isCommanderFormat = true, isEmptyDeck = true, hasTriggeredWizardNav = false),
        )
    }

    @Test
    fun `commander format, non-empty deck -- requires confirmation`() {
        assertEquals(
            WizardNavDecision.REQUIRE_CONFIRM,
            resolveWizardNavDecision(isCommanderFormat = true, isEmptyDeck = false, hasTriggeredWizardNav = false),
        )
    }

    @Test
    fun `non-commander format -- always a no-op regardless of deck contents`() {
        assertEquals(
            WizardNavDecision.NO_OP,
            resolveWizardNavDecision(isCommanderFormat = false, isEmptyDeck = true, hasTriggeredWizardNav = false),
        )
        assertEquals(
            WizardNavDecision.NO_OP,
            resolveWizardNavDecision(isCommanderFormat = false, isEmptyDeck = false, hasTriggeredWizardNav = false),
        )
    }

    @Test
    fun `already-triggered nav -- no-op even for a commander empty deck`() {
        assertEquals(
            WizardNavDecision.NO_OP,
            resolveWizardNavDecision(isCommanderFormat = true, isEmptyDeck = true, hasTriggeredWizardNav = true),
        )
    }
}
