package com.mmg.manahub.feature.decks.presentation

// COMMENTS_REVIEWED: 2026-09-16

import com.mmg.manahub.feature.decks.presentation.components.DeckAddCardsMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test
    fun `manual search stays pending while method sheet is mounted and opens after dismissal`() {
        assertNull(
            resolvePendingDeckAddCardsAction(
                method = DeckAddCardsMethod.MANUAL_SEARCH,
                methodSheetVisible = true,
                isDestinationResumed = true,
                isNavigatingToScanner = false,
                deckId = "deck-1",
            ),
        )
        assertEquals(
            PendingDeckAddCardsAction.OpenManualSearch,
            resolvePendingDeckAddCardsAction(
                method = DeckAddCardsMethod.MANUAL_SEARCH,
                methodSheetVisible = false,
                isDestinationResumed = true,
                isNavigatingToScanner = false,
                deckId = "deck-1",
            ),
        )
    }

    @Test
    fun `scanner navigation stays pending while method sheet is mounted and resolves after dismissal`() {
        assertNull(
            resolvePendingDeckAddCardsAction(
                method = DeckAddCardsMethod.SCAN_CARDS,
                methodSheetVisible = true,
                isDestinationResumed = true,
                isNavigatingToScanner = false,
                deckId = "deck-1",
            ),
        )
        assertEquals(
            PendingDeckAddCardsAction.NavigateToScanner("deck-1"),
            resolvePendingDeckAddCardsAction(
                method = DeckAddCardsMethod.SCAN_CARDS,
                methodSheetVisible = false,
                isDestinationResumed = true,
                isNavigatingToScanner = false,
                deckId = "deck-1",
            ),
        )
    }

    @Test
    fun `pending action waits for resumed destination`() {
        assertNull(
            resolvePendingDeckAddCardsAction(
                method = DeckAddCardsMethod.MANUAL_SEARCH,
                methodSheetVisible = false,
                isDestinationResumed = false,
                isNavigatingToScanner = false,
                deckId = "deck-1",
            ),
        )
    }

    @Test
    fun `scanner navigation requires a non-blank deck id and idle navigation state`() {
        assertNull(
            resolvePendingDeckAddCardsAction(
                method = DeckAddCardsMethod.SCAN_CARDS,
                methodSheetVisible = false,
                isDestinationResumed = true,
                isNavigatingToScanner = false,
                deckId = null,
            ),
        )
        assertNull(
            resolvePendingDeckAddCardsAction(
                method = DeckAddCardsMethod.SCAN_CARDS,
                methodSheetVisible = false,
                isDestinationResumed = true,
                isNavigatingToScanner = false,
                deckId = "   ",
            ),
        )
        assertNull(
            resolvePendingDeckAddCardsAction(
                method = DeckAddCardsMethod.SCAN_CARDS,
                methodSheetVisible = false,
                isDestinationResumed = true,
                isNavigatingToScanner = true,
                deckId = "deck-1",
            ),
        )
    }
}
