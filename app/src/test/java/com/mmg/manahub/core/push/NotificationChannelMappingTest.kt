package com.mmg.manahub.core.push

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for the notification channel selection logic in
 * [ManaHubMessagingService.onMessageReceived].
 *
 * Because the mapping is an inline `when` expression inside a FirebaseMessagingService
 * (not easily unit-testable without Robolectric), the logic is replicated here as a
 * pure function [resolveChannelId] so it can be tested without any Android framework
 * dependencies. If the production mapping in [ManaHubMessagingService] changes, this
 * function must be kept in sync.
 *
 * Channel ID reference (from ManaHubMessagingService):
 *   "trades_high"    → trade_proposed, trade_countered, trade_accepted (urgent/actioned trades)
 *   "friends"        → all friend_* notifications
 *   "trades_updates" → everything else (trade status updates)
 *
 * Covers:
 *  - GROUP 1: trade_high channel types
 *  - GROUP 2: trades_updates channel types
 *  - GROUP 3: friend channel types
 *  - GROUP 4: unknown type falls through to default channel
 */
class NotificationChannelMappingTest {

    // ── Logic under test (mirrored from ManaHubMessagingService.onMessageReceived) ──

    /**
     * Mirrors the channel selection `when` block from [ManaHubMessagingService].
     * Keep this function in sync with the production code.
     */
    private fun resolveChannelId(type: String): String = when {
        type == "trade_proposed" || type == "trade_countered" || type == "trade_accepted" -> "trades_high"
        type.startsWith("friend") -> "friends"
        else -> "trades_updates"
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 1 — trades_high channel (urgent: proposal, counter, acceptance)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given type trade_proposed then channel is trades_high`() {
        assertEquals("trades_high", resolveChannelId("trade_proposed"))
    }

    @Test
    fun `given type trade_countered then channel is trades_high`() {
        assertEquals("trades_high", resolveChannelId("trade_countered"))
    }

    @Test
    fun `given type trade_accepted then channel is trades_high`() {
        assertEquals("trades_high", resolveChannelId("trade_accepted"))
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 2 — trades_updates channel (status-change events)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given type trade_declined then channel is trades_updates`() {
        assertEquals("trades_updates", resolveChannelId("trade_declined"))
    }

    @Test
    fun `given type trade_edited then channel is trades_updates`() {
        assertEquals("trades_updates", resolveChannelId("trade_edited"))
    }

    @Test
    fun `given type trade_cancelled then channel is trades_updates`() {
        assertEquals("trades_updates", resolveChannelId("trade_cancelled"))
    }

    @Test
    fun `given type trade_revoked then channel is trades_updates`() {
        assertEquals("trades_updates", resolveChannelId("trade_revoked"))
    }

    @Test
    fun `given type trade_completed then channel is trades_updates`() {
        assertEquals("trades_updates", resolveChannelId("trade_completed"))
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 3 — friends channel
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given type friend_request then channel is friends`() {
        assertEquals("friends", resolveChannelId("friend_request"))
    }

    @Test
    fun `given type friend_accepted then channel is friends`() {
        assertEquals("friends", resolveChannelId("friend_accepted"))
    }

    @Test
    fun `given type friend_invite_joined then channel is friends`() {
        assertEquals("friends", resolveChannelId("friend_invite_joined"))
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 4 — unknown / unexpected types default to trades_updates
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given unknown type when resolveChannelId then defaults to trades_updates`() {
        // Edge: future notification types not yet in the production code must not crash
        assertEquals("trades_updates", resolveChannelId("unknown_event_type"))
    }

    @Test
    fun `given empty type when resolveChannelId then defaults to trades_updates`() {
        assertEquals("trades_updates", resolveChannelId(""))
    }

    @Test
    fun `given type that partially matches friend prefix when resolveChannelId then channel is friends`() {
        // The production rule uses startsWith("friend") — "friendly" would also match;
        // this test documents that behavior explicitly.
        assertEquals("friends", resolveChannelId("friendly_reminder"))
    }
}
