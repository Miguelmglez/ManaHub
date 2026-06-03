package com.mmg.manahub.core.push

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [ForegroundScreenTracker].
 *
 * [ForegroundScreenTracker] is a Kotlin `object` — its state persists across tests
 * within the same JVM process. [setUp] and [tearDown] both call [setCurrentDeeplink](null)
 * to guarantee isolation.
 *
 * Covers:
 *  - GROUP 1: isViewingDeeplink when a deeplink is set
 *  - GROUP 2: isViewingDeeplink when no deeplink is set (or a different one)
 *  - GROUP 3: setCurrentDeeplink(null) clears the tracker
 */
class ForegroundScreenTrackerTest {

    // ── Constants ─────────────────────────────────────────────────────────────

    private val TRADE_DEEPLINK = "manahub://trade/root-uuid/prop-uuid"
    private val FRIENDS_DEEPLINK = "manahub://friends"

    // ── Setup / Teardown ──────────────────────────────────────────────────────

    @Before
    fun setUp() {
        // Reset singleton state before each test
        ForegroundScreenTracker.setCurrentDeeplink(null)
    }

    @After
    fun tearDown() {
        // Ensure state does not bleed into subsequent test classes
        ForegroundScreenTracker.setCurrentDeeplink(null)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 1 — isViewingDeeplink returns true when deeplink matches
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given setCurrentDeeplink called when isViewingDeeplink with same deeplink then returns true`() {
        // Arrange
        ForegroundScreenTracker.setCurrentDeeplink(TRADE_DEEPLINK)

        // Act + Assert
        assertTrue(ForegroundScreenTracker.isViewingDeeplink(TRADE_DEEPLINK))
    }

    @Test
    fun `given friends deeplink set when isViewingDeeplink with friends deeplink then returns true`() {
        // Arrange
        ForegroundScreenTracker.setCurrentDeeplink(FRIENDS_DEEPLINK)

        // Act + Assert
        assertTrue(ForegroundScreenTracker.isViewingDeeplink(FRIENDS_DEEPLINK))
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 2 — isViewingDeeplink returns false when no deeplink is set
    //            or the incoming deeplink differs from current
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given no deeplink set when isViewingDeeplink then returns false`() {
        // Arrange — ForegroundScreenTracker is already cleared in setUp

        // Act + Assert
        assertFalse(ForegroundScreenTracker.isViewingDeeplink(TRADE_DEEPLINK))
    }

    @Test
    fun `given trade deeplink set when isViewingDeeplink with friends deeplink then returns false`() {
        // Arrange
        ForegroundScreenTracker.setCurrentDeeplink(TRADE_DEEPLINK)

        // Act + Assert
        assertFalse(ForegroundScreenTracker.isViewingDeeplink(FRIENDS_DEEPLINK))
    }

    @Test
    fun `given friends deeplink set when isViewingDeeplink with trade deeplink then returns false`() {
        // Arrange
        ForegroundScreenTracker.setCurrentDeeplink(FRIENDS_DEEPLINK)

        // Act + Assert
        assertFalse(ForegroundScreenTracker.isViewingDeeplink(TRADE_DEEPLINK))
    }

    @Test
    fun `given deeplink set when isViewingDeeplink with empty string then returns false`() {
        // Arrange — edge: incoming deeplink is empty
        ForegroundScreenTracker.setCurrentDeeplink(TRADE_DEEPLINK)

        // Act + Assert
        assertFalse(ForegroundScreenTracker.isViewingDeeplink(""))
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 3 — setCurrentDeeplink(null) clears the tracker
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given deeplink set when setCurrentDeeplink null then isViewingDeeplink returns false`() {
        // Arrange
        ForegroundScreenTracker.setCurrentDeeplink(TRADE_DEEPLINK)

        // Act
        ForegroundScreenTracker.setCurrentDeeplink(null)

        // Assert
        assertFalse(ForegroundScreenTracker.isViewingDeeplink(TRADE_DEEPLINK))
    }

    @Test
    fun `given deeplink set when overwritten with new deeplink then old deeplink no longer matches`() {
        // Arrange — simulate navigating from one screen to another
        ForegroundScreenTracker.setCurrentDeeplink(TRADE_DEEPLINK)

        // Act: user navigates to a different screen
        ForegroundScreenTracker.setCurrentDeeplink(FRIENDS_DEEPLINK)

        // Assert: old deeplink is gone; new one matches
        assertFalse(ForegroundScreenTracker.isViewingDeeplink(TRADE_DEEPLINK))
        assertTrue(ForegroundScreenTracker.isViewingDeeplink(FRIENDS_DEEPLINK))
    }
}
