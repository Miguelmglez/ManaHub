package com.mmg.manahub.feature.decks.presentation

// COMMENTS_REVIEWED: 2026-09-16

import com.mmg.manahub.core.model.DeckFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeckStudioScreenLogicTest {

    // -- resolveEmptyDeckStateOptions --------------------------------------------------------

    @Test
    fun `seed and inspirations enabled -- seed is the only primary`() {
        val options = resolveEmptyDeckStateOptions(
            seedEnabled = true,
            inspirationsEnabled = true,
        )
        assertTrue(options.showSeed)
        assertTrue(options.seedIsPrimary)
        assertTrue(options.showInspirations)
        assertTrue(!options.inspirationsIsPrimary)
        assertTrue(!options.importIsPrimary)
    }

    @Test
    fun `seed enabled -- seed is available even without a format condition`() {
        val options = resolveEmptyDeckStateOptions(
            seedEnabled = true,
            inspirationsEnabled = true,
        )
        assertTrue(options.showSeed)
        assertTrue(options.seedIsPrimary)
        assertTrue(options.showInspirations)
        assertTrue(!options.importIsPrimary)
    }

    @Test
    fun `seed and inspirations disabled -- import becomes primary`() {
        val options = resolveEmptyDeckStateOptions(
            seedEnabled = false,
            inspirationsEnabled = false,
        )
        assertTrue(!options.showSeed)
        assertTrue(!options.showInspirations)
        assertTrue(options.importIsPrimary)
    }

    @Test
    fun `seed flag off -- inspirations become primary when enabled`() {
        val options = resolveEmptyDeckStateOptions(
            seedEnabled = false,
            inspirationsEnabled = true,
        )
        assertTrue(!options.showSeed)
        assertTrue(options.inspirationsIsPrimary)
    }

    @Test
    fun `exactly one primary option across the full seed x inspirations matrix`() {
        for (seedEnabled in listOf(true, false)) {
            for (inspirationsEnabled in listOf(true, false)) {
                val options = resolveEmptyDeckStateOptions(seedEnabled, inspirationsEnabled)
                val primaryCount = listOfNotNull(
                    options.seedIsPrimary.takeIf { options.showSeed },
                    options.inspirationsIsPrimary.takeIf { options.showInspirations },
                    options.importIsPrimary.takeIf { true },
                ).count { it }
                assertEquals(
                    "seed=$seedEnabled inspirations=$inspirationsEnabled",
                    1,
                    primaryCount,
                )
            }
        }
    }

    // -- isWizardAvailableForFormat (Deck Wizard 60-card wave v6, plan §5 Phase 5.4, S15) --------

    @Test
    fun `STANDARD is wizard-available -- Build from seed navigates immediately`() {
        assertTrue(isWizardAvailableForFormat(DeckFormat.STANDARD))
        assertEquals(
            WizardNavDecision.NAVIGATE_NOW,
            resolveWizardNavDecision(WizardEntryPoint.BUILD_FROM_SEED, hasTriggeredWizardNav = false),
        )
    }

    @Test
    fun `DRAFT is NOT wizard-available -- the wizard has no build path for it`() {
        assertFalse(isWizardAvailableForFormat(DeckFormat.DRAFT))
    }

    @Test
    fun `a null (not-yet-resolved) format is NOT wizard-available`() {
        assertFalse(isWizardAvailableForFormat(null))
    }

    @Test
    fun `every non-Draft format is wizard-available`() {
        for (format in DeckFormat.entries) {
            assertEquals(format.name, format != DeckFormat.DRAFT, isWizardAvailableForFormat(format))
        }
    }

    // -- resolveWizardNavDecision ---------------------------------------------------------------

    @Test
    fun `build-from-seed entry point -- navigates immediately`() {
        assertEquals(
            WizardNavDecision.NAVIGATE_NOW,
            resolveWizardNavDecision(WizardEntryPoint.BUILD_FROM_SEED, hasTriggeredWizardNav = false),
        )
    }

    @Test
    fun `rebuild entry point -- requires confirmation`() {
        assertEquals(
            WizardNavDecision.REQUIRE_CONFIRM,
            resolveWizardNavDecision(WizardEntryPoint.REBUILD, hasTriggeredWizardNav = false),
        )
    }

    @Test
    fun `not-triggered navigation -- follows each entry point rule`() {
        assertEquals(
            WizardNavDecision.NAVIGATE_NOW,
            resolveWizardNavDecision(WizardEntryPoint.BUILD_FROM_SEED, hasTriggeredWizardNav = false),
        )
        assertEquals(
            WizardNavDecision.REQUIRE_CONFIRM,
            resolveWizardNavDecision(WizardEntryPoint.REBUILD, hasTriggeredWizardNav = false),
        )
    }

    @Test
    fun `already-triggered nav -- no-op for both entry points`() {
        assertEquals(
            WizardNavDecision.NO_OP,
            resolveWizardNavDecision(WizardEntryPoint.BUILD_FROM_SEED, hasTriggeredWizardNav = true),
        )
        assertEquals(
            WizardNavDecision.NO_OP,
            resolveWizardNavDecision(WizardEntryPoint.REBUILD, hasTriggeredWizardNav = true),
        )
    }

    // -- isBrowseInspirationsAvailable ---------------------------------------------------------

    @Test
    fun `browse inspirations is offered only for empty 60-card decks with the flag on`() {
        DeckFormat.entries.forEach { format ->
            assertEquals(format.isSixtyCardConstructed, isBrowseInspirationsAvailable(flagEnabled = true, format = format, isEmptyDeck = true))
            assertFalse(isBrowseInspirationsAvailable(flagEnabled = true, format = format, isEmptyDeck = false))
            assertFalse(isBrowseInspirationsAvailable(flagEnabled = false, format = format, isEmptyDeck = true))
        }
        assertFalse(isBrowseInspirationsAvailable(flagEnabled = true, format = null, isEmptyDeck = true))
    }
}
