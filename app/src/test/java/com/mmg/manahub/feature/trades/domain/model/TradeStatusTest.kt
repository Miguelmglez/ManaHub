package com.mmg.manahub.feature.trades.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [TradeStatus.canTransitionTo], [TradeStatus.isTerminal], and [TradeStatus.isActive].
 *
 * Covers:
 *  - GROUP 1: Valid transitions from DRAFT
 *  - GROUP 2: Valid transitions from PROPOSED
 *  - GROUP 3: Valid transitions from ACCEPTED
 *  - GROUP 4: Invalid transitions (terminal states cannot transition anywhere)
 *  - GROUP 5: Invalid cross-state transitions that skip steps
 *  - GROUP 6: isTerminal — all five terminal states
 *  - GROUP 7: isActive — only DRAFT, PROPOSED, ACCEPTED
 */
class TradeStatusTest {

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 1 — DRAFT transitions
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given DRAFT when canTransitionTo PROPOSED then returns true`() {
        assertTrue(TradeStatus.DRAFT.canTransitionTo(TradeStatus.PROPOSED))
    }

    @Test
    fun `given DRAFT when canTransitionTo ACCEPTED then returns false`() {
        assertFalse(TradeStatus.DRAFT.canTransitionTo(TradeStatus.ACCEPTED))
    }

    @Test
    fun `given DRAFT when canTransitionTo CANCELLED then returns false`() {
        assertFalse(TradeStatus.DRAFT.canTransitionTo(TradeStatus.CANCELLED))
    }

    @Test
    fun `given DRAFT when canTransitionTo DECLINED then returns false`() {
        assertFalse(TradeStatus.DRAFT.canTransitionTo(TradeStatus.DECLINED))
    }

    @Test
    fun `given DRAFT when canTransitionTo COUNTERED then returns false`() {
        assertFalse(TradeStatus.DRAFT.canTransitionTo(TradeStatus.COUNTERED))
    }

    @Test
    fun `given DRAFT when canTransitionTo COMPLETED then returns false`() {
        assertFalse(TradeStatus.DRAFT.canTransitionTo(TradeStatus.COMPLETED))
    }

    @Test
    fun `given DRAFT when canTransitionTo REVOKED then returns false`() {
        assertFalse(TradeStatus.DRAFT.canTransitionTo(TradeStatus.REVOKED))
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 2 — PROPOSED transitions
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given PROPOSED when canTransitionTo ACCEPTED then returns true`() {
        assertTrue(TradeStatus.PROPOSED.canTransitionTo(TradeStatus.ACCEPTED))
    }

    @Test
    fun `given PROPOSED when canTransitionTo DECLINED then returns true`() {
        assertTrue(TradeStatus.PROPOSED.canTransitionTo(TradeStatus.DECLINED))
    }

    @Test
    fun `given PROPOSED when canTransitionTo CANCELLED then returns true`() {
        assertTrue(TradeStatus.PROPOSED.canTransitionTo(TradeStatus.CANCELLED))
    }

    @Test
    fun `given PROPOSED when canTransitionTo COUNTERED then returns true`() {
        assertTrue(TradeStatus.PROPOSED.canTransitionTo(TradeStatus.COUNTERED))
    }

    @Test
    fun `given PROPOSED when canTransitionTo COMPLETED then returns false`() {
        assertFalse(TradeStatus.PROPOSED.canTransitionTo(TradeStatus.COMPLETED))
    }

    @Test
    fun `given PROPOSED when canTransitionTo REVOKED then returns false`() {
        assertFalse(TradeStatus.PROPOSED.canTransitionTo(TradeStatus.REVOKED))
    }

    @Test
    fun `given PROPOSED when canTransitionTo DRAFT then returns false`() {
        assertFalse(TradeStatus.PROPOSED.canTransitionTo(TradeStatus.DRAFT))
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 3 — ACCEPTED transitions
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given ACCEPTED when canTransitionTo COMPLETED then returns true`() {
        assertTrue(TradeStatus.ACCEPTED.canTransitionTo(TradeStatus.COMPLETED))
    }

    @Test
    fun `given ACCEPTED when canTransitionTo REVOKED then returns true`() {
        assertTrue(TradeStatus.ACCEPTED.canTransitionTo(TradeStatus.REVOKED))
    }

    @Test
    fun `given ACCEPTED when canTransitionTo PROPOSED then returns false`() {
        assertFalse(TradeStatus.ACCEPTED.canTransitionTo(TradeStatus.PROPOSED))
    }

    @Test
    fun `given ACCEPTED when canTransitionTo CANCELLED then returns false`() {
        assertFalse(TradeStatus.ACCEPTED.canTransitionTo(TradeStatus.CANCELLED))
    }

    @Test
    fun `given ACCEPTED when canTransitionTo DECLINED then returns false`() {
        assertFalse(TradeStatus.ACCEPTED.canTransitionTo(TradeStatus.DECLINED))
    }

    @Test
    fun `given ACCEPTED when canTransitionTo DRAFT then returns false`() {
        assertFalse(TradeStatus.ACCEPTED.canTransitionTo(TradeStatus.DRAFT))
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 4 — Terminal states cannot transition anywhere
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given CANCELLED which is terminal when canTransitionTo any state then all return false`() {
        TradeStatus.entries.forEach { next ->
            assertFalse(
                "CANCELLED should not transition to $next",
                TradeStatus.CANCELLED.canTransitionTo(next),
            )
        }
    }

    @Test
    fun `given DECLINED which is terminal when canTransitionTo any state then all return false`() {
        TradeStatus.entries.forEach { next ->
            assertFalse(
                "DECLINED should not transition to $next",
                TradeStatus.DECLINED.canTransitionTo(next),
            )
        }
    }

    @Test
    fun `given COUNTERED which is terminal when canTransitionTo any state then all return false`() {
        TradeStatus.entries.forEach { next ->
            assertFalse(
                "COUNTERED should not transition to $next",
                TradeStatus.COUNTERED.canTransitionTo(next),
            )
        }
    }

    @Test
    fun `given COMPLETED which is terminal when canTransitionTo any state then all return false`() {
        TradeStatus.entries.forEach { next ->
            assertFalse(
                "COMPLETED should not transition to $next",
                TradeStatus.COMPLETED.canTransitionTo(next),
            )
        }
    }

    @Test
    fun `given REVOKED which is terminal when canTransitionTo any state then all return false`() {
        TradeStatus.entries.forEach { next ->
            assertFalse(
                "REVOKED should not transition to $next",
                TradeStatus.REVOKED.canTransitionTo(next),
            )
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 5 — Cross-state skip transitions are invalid
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given DRAFT when canTransitionTo COMPLETED skipping all steps then returns false`() {
        assertFalse(TradeStatus.DRAFT.canTransitionTo(TradeStatus.COMPLETED))
    }

    @Test
    fun `given PROPOSED when canTransitionTo REVOKED skipping ACCEPTED then returns false`() {
        assertFalse(TradeStatus.PROPOSED.canTransitionTo(TradeStatus.REVOKED))
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 6 — isTerminal property
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given CANCELLED then isTerminal is true`() {
        assertTrue(TradeStatus.CANCELLED.isTerminal)
    }

    @Test
    fun `given DECLINED then isTerminal is true`() {
        assertTrue(TradeStatus.DECLINED.isTerminal)
    }

    @Test
    fun `given COUNTERED then isTerminal is true`() {
        assertTrue(TradeStatus.COUNTERED.isTerminal)
    }

    @Test
    fun `given COMPLETED then isTerminal is true`() {
        assertTrue(TradeStatus.COMPLETED.isTerminal)
    }

    @Test
    fun `given REVOKED then isTerminal is true`() {
        assertTrue(TradeStatus.REVOKED.isTerminal)
    }

    @Test
    fun `given DRAFT then isTerminal is false`() {
        assertFalse(TradeStatus.DRAFT.isTerminal)
    }

    @Test
    fun `given PROPOSED then isTerminal is false`() {
        assertFalse(TradeStatus.PROPOSED.isTerminal)
    }

    @Test
    fun `given ACCEPTED then isTerminal is false`() {
        assertFalse(TradeStatus.ACCEPTED.isTerminal)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 7 — isActive property (inverse of isTerminal)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given DRAFT then isActive is true`() {
        assertTrue(TradeStatus.DRAFT.isActive)
    }

    @Test
    fun `given PROPOSED then isActive is true`() {
        assertTrue(TradeStatus.PROPOSED.isActive)
    }

    @Test
    fun `given ACCEPTED then isActive is true`() {
        assertTrue(TradeStatus.ACCEPTED.isActive)
    }

    @Test
    fun `given CANCELLED then isActive is false`() {
        assertFalse(TradeStatus.CANCELLED.isActive)
    }

    @Test
    fun `given DECLINED then isActive is false`() {
        assertFalse(TradeStatus.DECLINED.isActive)
    }

    @Test
    fun `given COUNTERED then isActive is false`() {
        assertFalse(TradeStatus.COUNTERED.isActive)
    }

    @Test
    fun `given COMPLETED then isActive is false`() {
        assertFalse(TradeStatus.COMPLETED.isActive)
    }

    @Test
    fun `given REVOKED then isActive is false`() {
        assertFalse(TradeStatus.REVOKED.isActive)
    }

    @Test
    fun `given all statuses then isActive is always the inverse of isTerminal`() {
        // Structural invariant: isActive = !isTerminal for every status
        TradeStatus.entries.forEach { status ->
            val expectedActive = !status.isTerminal
            val msg = "For $status: isActive(${status.isActive}) should equal !isTerminal(${status.isTerminal})"
            assertTrue(msg, status.isActive == expectedActive)
        }
    }
}
