package com.mmg.manahub.feature.decks.domain.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Coverage for [EDHREC_SLUG_TO_THEME_ID]/[EDHREC_SLUG_TO_ARCHETYPE_ID] (Deck Engine Unification
 * plan §8a addendum — promoted from `:tools:tag-pipeline`'s `EdhrecThemeMappingTest`/
 * `EdhrecArchetypeMappingTest` verbatim, 2026-07-21, so the curated allowlist is pinned in the same
 * module it now lives in).
 */
class EdhrecSlugMappingTest {

    @Test
    fun `verified-live slugs map to the correct ThemeId`() {
        assertEquals(ThemeId.ARISTOCRATS, EDHREC_SLUG_TO_THEME_ID["aristocrats"])
        assertEquals(ThemeId.TOKENS, EDHREC_SLUG_TO_THEME_ID["tokens"])
        // The case where the obvious-looking slug guess was WRONG and had to be corrected against
        // real data (see the mapping file's KDoc).
        assertEquals(ThemeId.SUPERFRIENDS, EDHREC_SLUG_TO_THEME_ID["planeswalkers"])
    }

    @Test
    fun `guessed-but-nonexistent slugs are absent`() {
        assertFalse("tribal" in EDHREC_SLUG_TO_THEME_ID, "the real slug is 'typal', not 'tribal'")
        assertFalse("superfriends" in EDHREC_SLUG_TO_THEME_ID, "the real slug is 'planeswalkers'")
    }

    @Test
    fun `CLONES_THEFT is deliberately unmapped`() {
        assertFalse(EDHREC_SLUG_TO_THEME_ID.containsValue(ThemeId.CLONES_THEFT))
    }

    @Test
    fun `TRIBAL is deliberately unmapped (the 'typal' page is a per-tribe index, not a card list)`() {
        assertFalse(EDHREC_SLUG_TO_THEME_ID.containsValue(ThemeId.TRIBAL))
        assertFalse("typal" in EDHREC_SLUG_TO_THEME_ID, "typal was found to be a tribe-index page, not a ranked card list — see KDoc")
    }

    @Test
    fun `every mapped ThemeId is unique (no two slugs map to the same theme)`() {
        val themeIds = EDHREC_SLUG_TO_THEME_ID.values.toList()
        assertEquals(themeIds.distinct().size, themeIds.size)
    }

    @Test
    fun `theme slug set matches the map's own keys`() {
        assertEquals(EDHREC_SLUG_TO_THEME_ID.keys, EDHREC_THEME_SLUGS)
    }

    @Test
    fun `verified-live slugs map to the correct ArchetypeId`() {
        assertEquals(ArchetypeId.AGGRO, EDHREC_SLUG_TO_ARCHETYPE_ID["aggro"])
        assertEquals(ArchetypeId.MIDRANGE, EDHREC_SLUG_TO_ARCHETYPE_ID["midrange"])
        assertEquals(ArchetypeId.CONTROL, EDHREC_SLUG_TO_ARCHETYPE_ID["control"])
        assertEquals(ArchetypeId.TEMPO, EDHREC_SLUG_TO_ARCHETYPE_ID["tempo"])
        assertEquals(ArchetypeId.COMBO, EDHREC_SLUG_TO_ARCHETYPE_ID["combo"])
        assertEquals(ArchetypeId.RAMP, EDHREC_SLUG_TO_ARCHETYPE_ID["ramp"])
    }

    @Test
    fun `GENERIC is deliberately unmapped`() {
        assertFalse(EDHREC_SLUG_TO_ARCHETYPE_ID.containsValue(ArchetypeId.GENERIC))
    }

    @Test
    fun `every mapped ArchetypeId is unique (no two slugs map to the same archetype)`() {
        val archetypeIds = EDHREC_SLUG_TO_ARCHETYPE_ID.values.toList()
        assertEquals(archetypeIds.distinct().size, archetypeIds.size)
    }

    @Test
    fun `exactly the 6 non-generic archetypes are mapped`() {
        val expected = ArchetypeId.entries.filter { it.isSpecialized }.toSet()
        assertEquals(expected, EDHREC_SLUG_TO_ARCHETYPE_ID.values.toSet())
    }

    @Test
    fun `archetype slug set matches the map's own keys`() {
        assertEquals(EDHREC_SLUG_TO_ARCHETYPE_ID.keys, EDHREC_ARCHETYPE_SLUGS)
    }
}
