package com.mmg.manahub.feature.trades.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [parseTradeError].
 *
 * Covers every possible error code emitted by the Supabase state-machine RPCs,
 * plus boundary cases (null, empty card list, unknown codes).
 *
 * Covers:
 *  - GROUP 1: CARD_ALREADY_LOCKED with card ids
 *  - GROUP 2: CARD_ALREADY_LOCKED with empty payload
 *  - GROUP 3: All singleton error codes
 *  - GROUP 4: Null input → Unknown(null)
 *  - GROUP 5: Unrecognised message → Unknown(message)
 */
class TradeErrorParserTest {

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 1 — CARD_ALREADY_LOCKED with card ids
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given CARD_ALREADY_LOCKED with two card ids when parseTradeError then returns CardAlreadyLocked with both ids`() {
        // Arrange
        val message = "CARD_ALREADY_LOCKED: card1,card2"

        // Act
        val error = parseTradeError(message)

        // Assert
        assertTrue("Expected CardAlreadyLocked, got ${error::class.simpleName}", error is TradeError.CardAlreadyLocked)
        assertEquals(listOf("card1", "card2"), (error as TradeError.CardAlreadyLocked).cardIds)
    }

    @Test
    fun `given CARD_ALREADY_LOCKED with single card id when parseTradeError then returns CardAlreadyLocked with one id`() {
        val error = parseTradeError("CARD_ALREADY_LOCKED: abc-uuid-123")

        assertTrue(error is TradeError.CardAlreadyLocked)
        assertEquals(listOf("abc-uuid-123"), (error as TradeError.CardAlreadyLocked).cardIds)
    }

    @Test
    fun `given CARD_ALREADY_LOCKED with trailing whitespace in card ids when parseTradeError then list is non-empty`() {
        // The parser calls trim() on the section after the colon
        val error = parseTradeError("CARD_ALREADY_LOCKED:  card-a, card-b ")

        assertTrue(error is TradeError.CardAlreadyLocked)
        // split(",") keeps inner whitespace — verify the list is non-empty
        assertTrue("Should produce at least one id", (error as TradeError.CardAlreadyLocked).cardIds.isNotEmpty())
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 2 — CARD_ALREADY_LOCKED with empty payload
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given CARD_ALREADY_LOCKED with empty card list when parseTradeError then returns CardAlreadyLocked with empty list`() {
        // Arrange — trailing space after colon; filter { it.isNotBlank() } removes blanks
        val message = "CARD_ALREADY_LOCKED: "

        // Act
        val error = parseTradeError(message)

        // Assert
        assertTrue("Expected CardAlreadyLocked, got ${error::class.simpleName}", error is TradeError.CardAlreadyLocked)
        assertTrue("cardIds should be empty when payload is blank", (error as TradeError.CardAlreadyLocked).cardIds.isEmpty())
    }

    @Test
    fun `given CARD_ALREADY_LOCKED with no colon suffix when parseTradeError then returns CardAlreadyLocked`() {
        // Edge case: message matches the prefix — no exception should be thrown
        val error = parseTradeError("CARD_ALREADY_LOCKED")

        // substringAfter(":") returns the whole string → trim() → "CARD_ALREADY_LOCKED"
        // split(",") + filter(isNotBlank) → ["CARD_ALREADY_LOCKED"] — non-empty is fine
        assertTrue("Expected CardAlreadyLocked", error is TradeError.CardAlreadyLocked)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 3 — Singleton error codes
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given PROPOSAL_VERSION_MISMATCH message when parseTradeError then returns ProposalVersionMismatch`() {
        val error = parseTradeError("PROPOSAL_VERSION_MISMATCH: expected 3 got 2")

        assertTrue(error is TradeError.ProposalVersionMismatch)
    }

    @Test
    fun `given NOT_FRIENDS message when parseTradeError then returns NotFriends`() {
        val error = parseTradeError("NOT_FRIENDS: users are not connected")

        assertTrue(error is TradeError.NotFriends)
    }

    @Test
    fun `given INVALID_STATE message when parseTradeError then returns InvalidStateTransition`() {
        val error = parseTradeError("INVALID_STATE: cannot transition from CANCELLED to ACCEPTED")

        assertTrue(error is TradeError.InvalidStateTransition)
    }

    @Test
    fun `given INVENTORY_GONE message when parseTradeError then returns InventoryGone`() {
        val error = parseTradeError("INVENTORY_GONE: card was removed from collection")

        assertTrue(error is TradeError.InventoryGone)
    }

    @Test
    fun `given CANNOT_ACCEPT_REVIEW_COLLECTION message when parseTradeError then returns CannotAcceptReviewCollection`() {
        val error = parseTradeError("CANNOT_ACCEPT_REVIEW_COLLECTION: missing collection access")

        assertTrue(error is TradeError.CannotAcceptReviewCollection)
    }

    @Test
    fun `given INITIAL_ASYMMETRY message when parseTradeError then returns InitialAsymmetryNotAllowed`() {
        val error = parseTradeError("INITIAL_ASYMMETRY: proposal items are not balanced")

        assertTrue(error is TradeError.InitialAsymmetryNotAllowed)
    }

    @Test
    fun `given REVIEW_COLLECTION_SAME_DIRECTION message when parseTradeError then returns ReviewCollectionSameDirection`() {
        val error = parseTradeError("REVIEW_COLLECTION_SAME_DIRECTION: both sides reviewing same user")

        assertTrue(error is TradeError.ReviewCollectionSameDirection)
    }

    @Test
    fun `given UNAUTHORIZED message when parseTradeError then returns Unauthorized`() {
        val error = parseTradeError("UNAUTHORIZED: user does not own this proposal")

        assertTrue(error is TradeError.Unauthorized)
    }

    @Test
    fun `given exact error code with no trailing detail when parseTradeError then still returns correct typed error`() {
        // Verify the startsWith check works even with minimal messages
        assertTrue(parseTradeError("PROPOSAL_VERSION_MISMATCH")     is TradeError.ProposalVersionMismatch)
        assertTrue(parseTradeError("NOT_FRIENDS")                    is TradeError.NotFriends)
        assertTrue(parseTradeError("INVALID_STATE")                  is TradeError.InvalidStateTransition)
        assertTrue(parseTradeError("INVENTORY_GONE")                 is TradeError.InventoryGone)
        assertTrue(parseTradeError("CANNOT_ACCEPT_REVIEW_COLLECTION") is TradeError.CannotAcceptReviewCollection)
        assertTrue(parseTradeError("INITIAL_ASYMMETRY")              is TradeError.InitialAsymmetryNotAllowed)
        assertTrue(parseTradeError("REVIEW_COLLECTION_SAME_DIRECTION") is TradeError.ReviewCollectionSameDirection)
        assertTrue(parseTradeError("UNAUTHORIZED")                   is TradeError.Unauthorized)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 4 — Null input
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given null message when parseTradeError then returns Unknown with null message`() {
        // Arrange
        val message: String? = null

        // Act
        val error = parseTradeError(message)

        // Assert
        assertTrue("Expected Unknown, got ${error::class.simpleName}", error is TradeError.Unknown)
        assertNull((error as TradeError.Unknown).message)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 5 — Unrecognised messages fall through to Unknown
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given unrecognised message when parseTradeError then returns Unknown preserving the message`() {
        // Arrange
        val rawMessage = "something completely unexpected from the server"

        // Act
        val error = parseTradeError(rawMessage)

        // Assert
        assertTrue("Expected Unknown", error is TradeError.Unknown)
        assertEquals(rawMessage, (error as TradeError.Unknown).message)
    }

    @Test
    fun `given empty string when parseTradeError then returns Unknown with empty message`() {
        val error = parseTradeError("")

        assertTrue(error is TradeError.Unknown)
        assertEquals("", (error as TradeError.Unknown).message)
    }

    @Test
    fun `given lowercase version of known code when parseTradeError then falls through to Unknown`() {
        // The parser uses startsWith which is case-sensitive
        val error = parseTradeError("card_already_locked: uuid-1")

        assertTrue(error is TradeError.Unknown)
    }

    @Test
    fun `given message with leading whitespace before known code when parseTradeError then falls through to Unknown`() {
        // startsWith requires the code to be at position 0; a leading space breaks the match
        val error = parseTradeError(" UNAUTHORIZED: extra")

        assertTrue(error is TradeError.Unknown)
    }
}
