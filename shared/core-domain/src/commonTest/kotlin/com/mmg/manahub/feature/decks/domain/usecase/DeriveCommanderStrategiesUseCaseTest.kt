package com.mmg.manahub.feature.decks.domain.usecase

import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.feature.decks.domain.engine.ArchetypeId
import com.mmg.manahub.feature.decks.domain.engine.ThemeId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Deck Wizard & Engine Rework plan, Workstream 2.2 -- pins [DeriveCommanderStrategiesUseCase]'s
 * union/ranking contract: source-1 (own `card_strategy_tags`) first, source-2 (EDHREC aggregate)
 * next, source-3 (on-device fallback) ONLY when 1+2 are both empty, and the all-empty case (which
 * the picker's own GENERIC/"Balanced" escape hatch covers, tested at the picker layer, not here).
 */
class DeriveCommanderStrategiesUseCaseTest {

    private val useCase = DeriveCommanderStrategiesUseCase()

    @Test
    fun `source 1 own tags resolve into archetypes and themes via the shared taxonomy bridge`() {
        // CardTag.LIFEGAIN (not CardTag.TOKENS) -- TOKENS is a deliberately DUAL-purpose tag in
        // DeckIdentitySeedTags (it is also SeedStrategy.AGGRO's own archetype signal), so it would
        // resolve an extra ArchetypeId.AGGRO here and make this specific assertion flaky/wrong.
        // LIFEGAIN maps ONLY to ThemeId.LIFEGAIN in DeckIdentitySeedTags -- no MACRO_ARCHETYPE_TAGS
        // list references it -- so it isolates the "themes-only" resolution this test targets.
        // Deck Analysis Engine v3: RAMP moved from ArchetypeId to PostureId -- CardTag.RAMP no
        // longer resolves a macro archetype at all, so this test now uses CardTag.AGGRO (a real,
        // still-mapped MACRO_ARCHETYPE_TAGS signal) to keep demonstrating archetype resolution.
        val result = useCase(
            ownTags = listOf(CardTag.AGGRO, CardTag.LIFEGAIN),
            ownTribes = listOf("elf"),
        )
        assertEquals(listOf(ArchetypeId.AGGRO), result.archetypes)
        assertEquals(listOf(ThemeId.LIFEGAIN), result.themes)
        assertEquals(listOf(DerivedTribeCandidate("tribe:elf", "Elf")), result.tribes)
    }

    @Test
    fun `source 2 EDHREC theme names resolve when source 1 is empty`() {
        val result = useCase(
            ownTags = emptyList(),
            edhrecThemeNames = listOf("Tokens", "Not A Real Theme"),
        )
        assertEquals(emptyList<ArchetypeId>(), result.archetypes)
        assertEquals(listOf(ThemeId.TOKENS), result.themes)
    }

    @Test
    fun `source 1 and source 2 both empty falls back to source 3 (on-device tags + payoff tribes)`() {
        val result = useCase(
            ownTags = emptyList(),
            edhrecThemeNames = emptyList(),
            fallbackTags = listOf(CardTag.AGGRO),
            fallbackTribeKeys = setOf("tribe:goblin"),
        )
        assertEquals(listOf(ArchetypeId.AGGRO), result.archetypes)
        assertEquals(listOf(DerivedTribeCandidate("tribe:goblin", "Goblin")), result.tribes)
    }

    @Test
    fun `source 3 is never consulted when source 1 already resolved something`() {
        val result = useCase(
            ownTags = listOf(CardTag.AGGRO),
            fallbackTags = listOf(CardTag.TOKENS),
            fallbackTribeKeys = setOf("tribe:goblin"),
        )
        assertEquals(listOf(ArchetypeId.AGGRO), result.archetypes)
        assertTrue(result.themes.isEmpty())
        assertTrue(result.tribes.isEmpty())
    }

    @Test
    fun `all three sources empty returns fully empty candidates`() {
        val result = useCase()
        assertTrue(result.archetypes.isEmpty())
        assertTrue(result.themes.isEmpty())
        assertTrue(result.tribes.isEmpty())
    }

    @Test
    fun `an unresolvable EDHREC theme name is silently skipped, never guessed`() {
        val result = useCase(edhrecThemeNames = listOf("Some Unknown Theme"))
        assertTrue(result.themes.isEmpty())
    }

    @Test
    fun `duplicate signals across sources are deduplicated`() {
        val result = useCase(
            ownTags = listOf(CardTag.TOKENS),
            edhrecThemeNames = listOf("Tokens"),
        )
        assertEquals(listOf(ThemeId.TOKENS), result.themes)
    }

    @Test
    fun `the combined archetype+theme candidate count is capped`() {
        // Every ThemeId that maps to a real dictionary tag via a distinct EDHREC display name,
        // enough to exceed the use case's internal cap.
        val manyThemeNames = ThemeId.entries.map { it.displayName }
        val result = useCase(edhrecThemeNames = manyThemeNames)
        assertTrue(result.themes.size <= 8, "expected the candidate list capped at 8, got ${result.themes.size}")
    }
}
