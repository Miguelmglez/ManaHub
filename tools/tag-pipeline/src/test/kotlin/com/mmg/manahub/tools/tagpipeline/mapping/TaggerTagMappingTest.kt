package com.mmg.manahub.tools.tagpipeline.mapping

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TaggerTagMappingTest {

    @Test
    fun `every seeded slug from the task brief is mapped`() {
        // The RUN 5 task brief explicitly named these six to seed.
        listOf("removal", "ramp", "counterspell", "tutor").forEach { slug ->
            assertTrue(slug in TAGGER_TAG_TO_CARD_TAG, "expected '$slug' to be mapped")
        }
        // "board-wipe" and "card-advantage" were the brief's OWN guesses at slug names; verified
        // against real data the actual slugs are "sweeper" and "card-advantage" (this one matched)
        // respectively — both still honored, see the mapping file's KDoc for the "sweeper" story.
        assertTrue("sweeper" in TAGGER_TAG_TO_CARD_TAG)
        assertEquals("board_wipe", TAGGER_TAG_TO_CARD_TAG["sweeper"])
        assertTrue("card-advantage" in TAGGER_TAG_TO_CARD_TAG)
    }

    @Test
    fun `mapped slug resolves to expected CardTag key`() {
        assertEquals("removal", TAGGER_TAG_TO_CARD_TAG["removal"])
        assertEquals("ramp", TAGGER_TAG_TO_CARD_TAG["ramp"])
        assertEquals("counterspell", TAGGER_TAG_TO_CARD_TAG["counterspell"])
        assertEquals("tutor", TAGGER_TAG_TO_CARD_TAG["tutor"])
        assertEquals("lifegain", TAGGER_TAG_TO_CARD_TAG["lifegain"])
        assertEquals("mill", TAGGER_TAG_TO_CARD_TAG["mill"])
        assertEquals("reanimator", TAGGER_TAG_TO_CARD_TAG["reanimate"])
    }

    @Test
    fun `unmapped slug is absent from the allowlist`() {
        // Plausible-but-unverified/nonexistent slugs — must never silently resolve to a guess.
        assertFalse("board-wipe" in TAGGER_TAG_TO_CARD_TAG)
        assertFalse("card-draw" in TAGGER_TAG_TO_CARD_TAG)
        assertFalse("totally-made-up-slug" in TAGGER_TAG_TO_CARD_TAG)
    }

    @Test
    fun `mapTaggerSlugsToCardTags drops unmapped slugs, never guesses`() {
        val result = mapTaggerSlugsToCardTags(setOf("removal", "totally-made-up-slug", "ramp"))
        assertEquals(setOf("removal", "ramp"), result)
    }

    @Test
    fun `mapTaggerSlugsToCardTags of an all-unmapped set is empty`() {
        assertEquals(emptySet(), mapTaggerSlugsToCardTags(setOf("no-such-tag", "also-not-real")))
    }

    @Test
    fun `mapTaggerSlugsToCardTags of an empty set is empty`() {
        assertEquals(emptySet(), mapTaggerSlugsToCardTags(emptySet()))
    }
}
