package com.mmg.manahub.feature.decks.domain.engine

import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.TagCategory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Deck Engine Unification plan (D2) — [DeckIdentitySeedTags]'s "total mapping" invariant: every
 * non-[ArchetypeId.GENERIC] archetype and every [ThemeId] must yield at least one seed [CardTag],
 * and every yielded tag must be an IDENTITY-category tag ([TagCategory.STRATEGY]/
 * [TagCategory.ARCHETYPE]/[TagCategory.TRIBAL]) so it can actually be matched by
 * [DeckScorer.synergyScore] rather than sitting inert in the fingerprint (see
 * [DeckIdentitySeedTags]'s `THEME_TAGS` KDoc for the two documented exceptions -- SUPERFRIENDS/
 * VEHICLES use a proxy tag, TOOLBOX is a deliberate documented no-op via a ROLE-category tag, which
 * this suite explicitly carves out rather than silently passing).
 */
class DeckIdentitySeedTagsTest {

    private val identityCategories = setOf(TagCategory.STRATEGY, TagCategory.ARCHETYPE, TagCategory.TRIBAL)

    @Test
    fun `every non-GENERIC archetype yields at least one seed tag`() {
        ArchetypeId.entries.filter { it != ArchetypeId.GENERIC }.forEach { archetype ->
            val tags = DeckIdentitySeedTags.archetypeSeedTags(archetype)
            assertTrue(tags.isNotEmpty(), "ArchetypeId.$archetype must yield at least one seed tag (D2 total mapping)")
        }
    }

    @Test
    fun `GENERIC archetype yields no seed tags -- documented, intentional`() {
        assertTrue(DeckIdentitySeedTags.archetypeSeedTags(ArchetypeId.GENERIC).isEmpty())
    }

    @Test
    fun `every ThemeId yields at least one seed tag`() {
        ThemeId.entries.forEach { theme ->
            val tags = DeckIdentitySeedTags.themeSeedTags(listOf(theme))
            assertTrue(tags.isNotEmpty(), "ThemeId.$theme must yield at least one seed tag (D2 total mapping)")
        }
    }

    /** TOOLBOX is the one documented exception (no STRATEGY/ARCHETYPE-category tag exists yet for
     * "silver-bullet toolbox" decks -- see [DeckIdentitySeedTags]'s `THEME_TAGS` KDoc); every OTHER
     * theme's tags are all IDENTITY-category (STRATEGY/ARCHETYPE/TRIBAL), the only categories
     * [DeckScorer.synergyScore] can ever match a candidate card's own tag against. */
    @Test
    fun `every ThemeId's seed tags are IDENTITY-category, except the documented TOOLBOX exception`() {
        ThemeId.entries.filter { it != ThemeId.TOOLBOX }.forEach { theme ->
            val tags = DeckIdentitySeedTags.themeSeedTags(listOf(theme))
            assertTrue(
                tags.all { it.category in identityCategories },
                "ThemeId.$theme's seed tags must all be IDENTITY-category (STRATEGY/ARCHETYPE/TRIBAL), found: $tags",
            )
        }
    }

    @Test
    fun `tribeSeedTag produces a synthetic TRIBAL tag for a non-blank tribe key`() {
        val tags = DeckIdentitySeedTags.tribeSeedTag("tribe:elf")
        assertEquals(listOf(CardTag(key = "tribe:elf", category = TagCategory.TRIBAL)), tags)
    }

    @Test
    fun `tribeSeedTag is empty for null or blank`() {
        assertTrue(DeckIdentitySeedTags.tribeSeedTag(null).isEmpty())
        assertTrue(DeckIdentitySeedTags.tribeSeedTag("").isEmpty())
        assertTrue(DeckIdentitySeedTags.tribeSeedTag("  ").isEmpty())
    }

    @Test
    fun `forArchetype combines archetype, theme, and tribe contributions`() {
        val tags = DeckIdentitySeedTags.forArchetype(ArchetypeId.AGGRO, listOf(ThemeId.TOKENS), "tribe:goblin")
        assertTrue(CardTag.AGGRO in tags)
        assertTrue(CardTag.TOKENS in tags)
        assertTrue(CardTag(key = "tribe:goblin", category = TagCategory.TRIBAL) in tags)
    }

    // ── Reverse lookup (chip-tap resolution) ───────────────────────────────────────

    @Test
    fun `archetypeForTag resolves a macro archetype's own primary tag`() {
        assertEquals(ArchetypeId.AGGRO, DeckIdentitySeedTags.archetypeForTag(CardTag.AGGRO))
        assertEquals(ArchetypeId.RAMP, DeckIdentitySeedTags.archetypeForTag(CardTag.RAMP))
    }

    @Test
    fun `archetypeForTag returns null for a tag with no archetype home`() {
        assertNull(DeckIdentitySeedTags.archetypeForTag(CardTag.ENCHANTRESS))
    }

    @Test
    fun `themeForTag resolves the 4 pre-unification dead-chip tags onto a real theme`() {
        // Wizard Quality Campaign B1's originally-called-out dead chips -- must all resolve under
        // the new taxonomy (see DeckIdentitySeedTags' THEME_TAGS KDoc).
        assertEquals(ThemeId.PLUS1_COUNTERS, DeckIdentitySeedTags.themeForTag(CardTag.PLUS_COUNTERS))
        assertEquals(ThemeId.ARISTOCRATS, DeckIdentitySeedTags.themeForTag(CardTag("death_triggers", TagCategory.STRATEGY)))
        assertEquals(ThemeId.SPELLSLINGER, DeckIdentitySeedTags.themeForTag(CardTag("spellslinger", TagCategory.STRATEGY)))
        assertEquals(ThemeId.BLINK, DeckIdentitySeedTags.themeForTag(CardTag("etb", TagCategory.STRATEGY)))
    }

    @Test
    fun `themeForTag resolves the graveyard tag to REANIMATOR -- first-declared owner, unchanged from pre-unification`() {
        assertEquals(ThemeId.REANIMATOR, DeckIdentitySeedTags.themeForTag(CardTag.GRAVEYARD))
    }
}
