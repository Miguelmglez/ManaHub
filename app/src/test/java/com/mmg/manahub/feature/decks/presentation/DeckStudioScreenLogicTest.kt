package com.mmg.manahub.feature.decks.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Deck Wizard v4: pure decision functions extracted from [DeckStudioScreen]'s empty-state and
 * wizard-entry-point logic, so both are testable without a Compose harness.
 * Run 7 (R14 correction + data-loss bugfix) introduced [resolveWizardNavDecision] with a shared
 * `isEmptyDeck` branch for both entry points; Run 8 (R15, 2026-09-14) gave each entry point its
 * own disjoint render condition (Build from seed: empty-deck state card only; Rebuild: overflow
 * menu on a non-empty deck only), so the `isEmptyDeck`-branching tests below were REWRITTEN to
 * assert the new per-entry-point rule instead of deleted -- see each test's own note.
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

    // -- resolveWizardNavDecision (R15: rewritten for the per-entry-point signature) -----------

    // R15 rewrite of "commander format, empty deck -- navigates immediately": Build from seed is
    // now unconditionally NAVIGATE_NOW for a commander format (it is only ever rendered on the
    // empty-deck state card, so there is no isEmptyDeck param left to vary).
    @Test
    fun `commander format, build-from-seed entry point -- navigates immediately, never confirms`() {
        assertEquals(
            WizardNavDecision.NAVIGATE_NOW,
            resolveWizardNavDecision(WizardEntryPoint.BUILD_FROM_SEED, isCommanderFormat = true, hasTriggeredWizardNav = false),
        )
    }

    // R15 rewrite of "commander format, non-empty deck -- requires confirmation": Rebuild is now
    // unconditionally REQUIRE_CONFIRM for a commander format (it is only ever rendered on a
    // non-empty deck, so there is no isEmptyDeck skip branch left either).
    @Test
    fun `commander format, rebuild entry point -- always requires confirmation`() {
        assertEquals(
            WizardNavDecision.REQUIRE_CONFIRM,
            resolveWizardNavDecision(WizardEntryPoint.REBUILD, isCommanderFormat = true, hasTriggeredWizardNav = false),
        )
    }

    @Test
    fun `non-commander format -- always a no-op regardless of entry point`() {
        assertEquals(
            WizardNavDecision.NO_OP,
            resolveWizardNavDecision(WizardEntryPoint.BUILD_FROM_SEED, isCommanderFormat = false, hasTriggeredWizardNav = false),
        )
        assertEquals(
            WizardNavDecision.NO_OP,
            resolveWizardNavDecision(WizardEntryPoint.REBUILD, isCommanderFormat = false, hasTriggeredWizardNav = false),
        )
    }

    @Test
    fun `already-triggered nav -- no-op for both entry points even on a commander format`() {
        assertEquals(
            WizardNavDecision.NO_OP,
            resolveWizardNavDecision(WizardEntryPoint.BUILD_FROM_SEED, isCommanderFormat = true, hasTriggeredWizardNav = true),
        )
        assertEquals(
            WizardNavDecision.NO_OP,
            resolveWizardNavDecision(WizardEntryPoint.REBUILD, isCommanderFormat = true, hasTriggeredWizardNav = true),
        )
    }
}
