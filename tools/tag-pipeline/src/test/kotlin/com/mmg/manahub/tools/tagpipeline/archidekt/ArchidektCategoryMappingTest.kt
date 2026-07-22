package com.mmg.manahub.tools.tagpipeline.archidekt

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ArchidektCategoryMappingTest {

    @Test
    fun `real functional categories from the live sample resolve to expected CardTag keys`() {
        // Real observed strings from the 2026-07-21 150-deck live sample (see the mapping file's
        // KDoc) — every one of these was actually seen in a real Archidekt deck response.
        assertEquals("removal", mapArchidektCategoryToCardTag("Removal"))
        assertEquals("ramp", mapArchidektCategoryToCardTag("Ramp"))
        assertEquals("ramp", mapArchidektCategoryToCardTag("Mana Ramp"))
        assertEquals("card_draw", mapArchidektCategoryToCardTag("Draw"))
        assertEquals("card_draw", mapArchidektCategoryToCardTag("Card Draw"))
        assertEquals("tutor", mapArchidektCategoryToCardTag("Tutor"))
        assertEquals("protection", mapArchidektCategoryToCardTag("Protection"))
        assertEquals("recursion", mapArchidektCategoryToCardTag("Recursion"))
        assertEquals("stax", mapArchidektCategoryToCardTag("Stax"))
    }

    @Test
    fun `lookup is case-insensitive and trims whitespace`() {
        assertEquals("removal", mapArchidektCategoryToCardTag("  removal  "))
        assertEquals("removal", mapArchidektCategoryToCardTag("REMOVAL"))
        assertEquals("board_wipe", mapArchidektCategoryToCardTag("SWEEPER"))
    }

    @Test
    fun `type-echo buckets are deliberately excluded, never mapped`() {
        listOf("Land", "Creature", "Artifact", "Sorcery", "Instant", "Enchantment", "Planeswalker").forEach {
            assertNull(mapArchidektCategoryToCardTag(it), "'$it' is a type-echo bucket and must not map")
        }
    }

    @Test
    fun `not-in-deck and role-marker buckets are excluded`() {
        assertNull(mapArchidektCategoryToCardTag("Maybeboard"))
        assertNull(mapArchidektCategoryToCardTag("Sideboard"))
        assertNull(mapArchidektCategoryToCardTag("Commander"))
    }

    @Test
    fun `ambiguous categories with no single confident target are dropped`() {
        assertNull(mapArchidektCategoryToCardTag("Interaction"))
        assertNull(mapArchidektCategoryToCardTag("Copy"))
        assertNull(mapArchidektCategoryToCardTag("Counters"))
        assertNull(mapArchidektCategoryToCardTag("Control"))
    }

    @Test
    fun `real custom junk category strings from the live sample are dropped, never guessed`() {
        // Real single-deck custom category strings observed live — must never leak in as if they
        // were genuine Archidekt default categories.
        assertNull(mapArchidektCategoryToCardTag("Pingers"))
        assertNull(mapArchidektCategoryToCardTag("The Cranberries"))
        assertNull(mapArchidektCategoryToCardTag("Tavs"))
        assertNull(mapArchidektCategoryToCardTag("Mmmm tasty slide"))
    }

    @Test
    fun `every mapped target is a real CardTag key from the production TagDictionary`() {
        ARCHIDEKT_CATEGORY_TO_CARD_TAG.values.toSet().forEach { tagKey ->
            val entry = com.mmg.manahub.core.data.tagging.TagDictionary.get(tagKey)
            assert(entry != null) { "mapped target '$tagKey' does not exist in TagDictionary" }
        }
    }
}
