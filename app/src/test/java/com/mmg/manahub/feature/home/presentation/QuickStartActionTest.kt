package com.mmg.manahub.feature.home.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickStartActionTest {

    @Test
    fun `fromPersistedId returns SCAN_CARD for scan_card`() {
        assertEquals(QuickStartAction.SCAN_CARD, QuickStartAction.fromPersistedId("scan_card"))
    }

    @Test
    fun `fromPersistedId returns CREATE_DECK for create_deck`() {
        assertEquals(QuickStartAction.CREATE_DECK, QuickStartAction.fromPersistedId("create_deck"))
    }

    @Test
    fun `fromPersistedId returns DRAFT_GUIDE for draft_guide`() {
        assertEquals(QuickStartAction.DRAFT_GUIDE, QuickStartAction.fromPersistedId("draft_guide"))
    }

    @Test
    fun `fromPersistedId returns SEARCH_CARD for search_card`() {
        assertEquals(QuickStartAction.SEARCH_CARD, QuickStartAction.fromPersistedId("search_card"))
    }

    @Test
    fun `fromPersistedId returns DECKS for decks`() {
        assertEquals(QuickStartAction.DECKS, QuickStartAction.fromPersistedId("decks"))
    }

    @Test
    fun `fromPersistedId returns NEWS for news`() {
        assertEquals(QuickStartAction.NEWS, QuickStartAction.fromPersistedId("news"))
    }

    @Test
    fun `fromPersistedId returns STATS for stats`() {
        assertEquals(QuickStartAction.STATS, QuickStartAction.fromPersistedId("stats"))
    }

    @Test
    fun `fromPersistedId returns FRIENDS for friends`() {
        assertEquals(QuickStartAction.FRIENDS, QuickStartAction.fromPersistedId("friends"))
    }

    @Test
    fun `fromPersistedId returns TRADES for trades`() {
        assertEquals(QuickStartAction.TRADES, QuickStartAction.fromPersistedId("trades"))
    }

    @Test
    fun `fromPersistedId returns SETTINGS for settings`() {
        assertEquals(QuickStartAction.SETTINGS, QuickStartAction.fromPersistedId("settings"))
    }

    @Test
    fun `fromPersistedId returns null for unknown id`() {
        assertNull(QuickStartAction.fromPersistedId("unknown_action"))
    }

    @Test
    fun `fromPersistedId returns null for empty string`() {
        assertNull(QuickStartAction.fromPersistedId(""))
    }

    @Test
    fun `fromPersistedId returns null for id with wrong casing`() {
        assertNull(QuickStartAction.fromPersistedId("START_GAME"))
    }

    @Test
    fun `fromPersistedId returns null for whitespace-only string`() {
        assertNull(QuickStartAction.fromPersistedId("   "))
    }

    @Test
    fun `defaults has exactly 4 items`() {
        assertEquals(4, QuickStartAction.defaults.size)
    }

    @Test
    fun `defaults contains SCAN_CARD CREATE_DECK DRAFT_GUIDE STATS in order`() {
        assertEquals(
            listOf(
                QuickStartAction.SCAN_CARD,
                QuickStartAction.CREATE_DECK,
                QuickStartAction.DRAFT_GUIDE,
                QuickStartAction.STATS,
            ),
            QuickStartAction.defaults,
        )
    }

    @Test
    fun `all enum entries have unique persistedId values`() {
        val ids = QuickStartAction.entries.map { it.persistedId }
        assertEquals(
            "Duplicate persistedId values detected",
            ids.size,
            ids.toSet().size,
        )
    }

    @Test
    fun `every entry can be round-tripped through fromPersistedId`() {
        for (action in QuickStartAction.entries) {
            assertEquals(
                "Round-trip failed for $action (persistedId='${action.persistedId}')",
                action,
                QuickStartAction.fromPersistedId(action.persistedId),
            )
        }
    }

    @Test
    fun `no persistedId contains whitespace or uppercase letters`() {
        for (action in QuickStartAction.entries) {
            assertTrue(
                "persistedId '${action.persistedId}' for $action must be lowercase with no whitespace",
                action.persistedId.matches(Regex("[a-z_]+")),
            )
        }
    }
}
