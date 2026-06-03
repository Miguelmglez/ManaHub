package com.mmg.manahub.core.push

import com.mmg.manahub.app.navigation.Screen
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for the deeplink URI format produced by [Screen] route factories and
 * consumed by FCM payload parsing in [ManaHubMessagingService].
 *
 * These are pure unit tests — no Android framework, no mocks needed. They verify
 * that the deeplink scheme and structure matches what [Screen] createRoute functions
 * expect when the URI is split and parsed at the destination.
 *
 * Deep link format (from ManaHubMessagingService / AppNavGraph):
 *   manahub://trade/{rootProposalId}/{proposalId}   → TradeNegotiationDetail
 *   manahub://friends                               → FriendsList
 *
 * Covers:
 *  - GROUP 1: trade negotiation deeplink structure and segment extraction
 *  - GROUP 2: friends deeplink structure
 *  - GROUP 3: Screen.TradeNegotiationDetail.createRoute produces expected path
 *  - GROUP 4: Edge cases (UUIDs with hyphens, empty segments guard)
 */
class DeeplinkParsingTest {

    // ── Constants ─────────────────────────────────────────────────────────────

    private val ROOT_PROPOSAL_ID = "root-uuid-1234"
    private val PROPOSAL_ID = "prop-uuid-5678"
    private val MANAHUB_SCHEME = "manahub"

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Simulates how the Activity / NavGraph resolves a "manahub://trade/{root}/{prop}"
     * deeplink by parsing the URI string. This mirrors the logic in AppNavGraph's
     * deeplink intent handler.
     */
    private fun parseTradeDeeplink(deeplink: String): Pair<String, String>? {
        // Expected format: manahub://trade/{rootProposalId}/{proposalId}
        val prefix = "manahub://trade/"
        if (!deeplink.startsWith(prefix)) return null
        val segments = deeplink.removePrefix(prefix).split("/")
        if (segments.size != 2) return null
        val rootProposalId = segments[0]
        val proposalId = segments[1]
        if (rootProposalId.isEmpty() || proposalId.isEmpty()) return null
        return Pair(rootProposalId, proposalId)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 1 — trade negotiation deeplink structure
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given trade deeplink when parsed then rootProposalId segment is correct`() {
        // Arrange
        val deeplink = "manahub://trade/$ROOT_PROPOSAL_ID/$PROPOSAL_ID"

        // Act
        val result = parseTradeDeeplink(deeplink)

        // Assert
        assertEquals(ROOT_PROPOSAL_ID, result?.first)
    }

    @Test
    fun `given trade deeplink when parsed then proposalId segment is correct`() {
        // Arrange
        val deeplink = "manahub://trade/$ROOT_PROPOSAL_ID/$PROPOSAL_ID"

        // Act
        val result = parseTradeDeeplink(deeplink)

        // Assert
        assertEquals(PROPOSAL_ID, result?.second)
    }

    @Test
    fun `given trade deeplink with UUID IDs then both segments are non-empty`() {
        // Arrange — UUIDs contain hyphens; the parser must handle them correctly
        val rootId = "550e8400-e29b-41d4-a716-446655440000"
        val propId = "6ba7b810-9dad-11d1-80b4-00c04fd430c8"
        val deeplink = "manahub://trade/$rootId/$propId"

        // Act
        val result = parseTradeDeeplink(deeplink)

        // Assert
        assertEquals(rootId, result?.first)
        assertEquals(propId, result?.second)
    }

    @Test
    fun `given trade deeplink with missing proposalId segment then parse returns null`() {
        // Arrange — malformed deeplink (only rootProposalId, no proposalId)
        val deeplink = "manahub://trade/$ROOT_PROPOSAL_ID"

        // Act
        val result = parseTradeDeeplink(deeplink)

        // Assert: incomplete deeplink must be rejected
        assertEquals(null, result)
    }

    @Test
    fun `given deeplink with wrong scheme then parse returns null`() {
        // Arrange — wrong scheme (https instead of manahub)
        val deeplink = "https://miguelmglez.github.io/trade/$ROOT_PROPOSAL_ID/$PROPOSAL_ID"

        // Act
        val result = parseTradeDeeplink(deeplink)

        // Assert
        assertEquals(null, result)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 2 — friends deeplink structure
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given manahub friends deeplink when scheme extracted then scheme is manahub`() {
        // Arrange
        val deeplink = "manahub://friends"

        // Act
        val scheme = deeplink.substringBefore("://")

        // Assert
        assertEquals(MANAHUB_SCHEME, scheme)
    }

    @Test
    fun `given manahub friends deeplink when host extracted then host is friends`() {
        // Arrange
        val deeplink = "manahub://friends"

        // Act
        val host = deeplink.substringAfter("://")

        // Assert
        assertEquals("friends", host)
    }

    @Test
    fun `given friends deeplink when checked against trade prefix then it is not a trade deeplink`() {
        // Arrange
        val deeplink = "manahub://friends"

        // Act
        val result = parseTradeDeeplink(deeplink)

        // Assert: friends deeplink must not be mis-parsed as a trade deeplink
        assertEquals(null, result)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 3 — Screen.TradeNegotiationDetail.createRoute produces the correct path
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given rootProposalId and proposalId when createRoute called then route has correct structure`() {
        // Arrange
        val rootId = ROOT_PROPOSAL_ID
        val propId = PROPOSAL_ID

        // Act
        val route = Screen.TradeNegotiationDetail.createRoute(
            proposalId = propId,
            rootProposalId = rootId,
        )

        // Assert: route format is "trades/proposal/{proposalId}/thread/{rootProposalId}"
        assertEquals("trades/proposal/$propId/thread/$rootId", route)
    }

    @Test
    fun `given route from createRoute when segments split by slash then proposalId is at correct index`() {
        // Arrange
        val rootId = ROOT_PROPOSAL_ID
        val propId = PROPOSAL_ID
        val route = Screen.TradeNegotiationDetail.createRoute(
            proposalId = propId,
            rootProposalId = rootId,
        )

        // Act
        val segments = route.split("/")

        // Assert: "trades/proposal/{proposalId}/thread/{rootProposalId}"
        // index:   0       1          2             3       4
        assertEquals("trades", segments[0])
        assertEquals("proposal", segments[1])
        assertEquals(propId, segments[2])
        assertEquals("thread", segments[3])
        assertEquals(rootId, segments[4])
    }

    @Test
    fun `given route from createRoute when rootProposalId extracted then it matches input`() {
        // Arrange
        val rootId = "root-abc"
        val propId = "prop-xyz"
        val route = Screen.TradeNegotiationDetail.createRoute(
            proposalId = propId,
            rootProposalId = rootId,
        )

        // Act: simulate NavBackStackEntry argument extraction by splitting route
        val extractedRootId = route.split("/").last()

        // Assert
        assertEquals(rootId, extractedRootId)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 4 — edge cases
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given trade deeplink with empty rootProposalId when parsed then returns null`() {
        // Arrange — empty first segment
        val deeplink = "manahub://trade//$PROPOSAL_ID"

        // Act
        val result = parseTradeDeeplink(deeplink)

        // Assert: empty rootProposalId is invalid
        assertEquals(null, result)
    }

    @Test
    fun `given trade deeplink with extra trailing slash when parsed then returns null`() {
        // Arrange — extra segment makes it ambiguous
        val deeplink = "manahub://trade/$ROOT_PROPOSAL_ID/$PROPOSAL_ID/extra"

        // Act
        val result = parseTradeDeeplink(deeplink)

        // Assert: 3-segment path is not a valid trade deeplink
        assertEquals(null, result)
    }
}
