package com.mmg.manahub.feature.draft.presentation.ui

// COMMENTS_REVIEWED: 2026-09-17

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DraftGuideRichTextParserTest {

    @Test
    fun `parses mana style and card references while preserving inherited formatting`() {
        val segments = DraftGuideRichTextParser.parse(
            "**Cast {W}{2} {\"color\":\"primary\",\"text\":\"early\"} " +
                "{\"scryfall_id\":\"abc123\",\"name\":\"Dazzling Angel\"}**",
        )

        assertEquals(
            listOf("Cast ", "W", "2", " ", "early", " ", "Dazzling Angel"),
            segments.map { segment ->
                when (segment) {
                    is DraftGuideRichTextSegment.Text -> segment.value
                    is DraftGuideRichTextSegment.Mana -> segment.token
                    is DraftGuideRichTextSegment.CardReference -> segment.name
                }
            },
        )
        assertTrue((segments[0] as DraftGuideRichTextSegment.Text).style.bold)
        assertEquals("primary", (segments[4] as DraftGuideRichTextSegment.Text).style.colorToken)
        assertTrue((segments[4] as DraftGuideRichTextSegment.Text).style.bold)
        assertEquals("abc123", (segments[6] as DraftGuideRichTextSegment.CardReference).scryfallId)
        assertTrue((segments[6] as DraftGuideRichTextSegment.CardReference).style.bold)
    }

    @Test
    fun `parses markdown emphasis and keeps malformed markup literal`() {
        val segments = DraftGuideRichTextParser.parse("*italic* ~~removed~~ {broken")

        assertEquals("italic", (segments[0] as DraftGuideRichTextSegment.Text).value)
        assertTrue((segments[0] as DraftGuideRichTextSegment.Text).style.italic)
        assertEquals("removed", (segments[2] as DraftGuideRichTextSegment.Text).value)
        assertTrue((segments[2] as DraftGuideRichTextSegment.Text).style.strikeThrough)
        assertTrue(segments.filterIsInstance<DraftGuideRichTextSegment.Text>().last().value.contains("{broken"))
    }

    @Test
    fun `parses every mana token accepted by the shared parser`() {
        val segments = DraftGuideRichTextParser.parse(
            "{U/R} {2/W} {W/P} {T} {Q} {CHAOS} {∞} {1/2}",
        )

        assertEquals(
            listOf("U/R", "2/W", "W/P", "T", "Q", "CHAOS", "∞", "1/2"),
            segments.filterIsInstance<DraftGuideRichTextSegment.Mana>().map { it.token },
        )
    }

    @Test
    fun `preserves malformed structured markup containing quotes literally`() {
        val source = "before {\"text\":\"broken\",} after"

        assertEquals(
            source,
            DraftGuideRichTextParser.parse(source)
                .filterIsInstance<DraftGuideRichTextSegment.Text>()
                .joinToString(separator = "") { it.value },
        )
        assertTrue(DraftGuideRichTextParser.parse(source).none { it is DraftGuideRichTextSegment.Mana })
    }
}
