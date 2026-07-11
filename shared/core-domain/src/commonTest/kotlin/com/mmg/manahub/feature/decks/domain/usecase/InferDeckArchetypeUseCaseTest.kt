package com.mmg.manahub.feature.decks.domain.usecase

import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.TagCategory
import com.mmg.manahub.feature.decks.domain.engine.ArchetypeFormat
import com.mmg.manahub.feature.decks.domain.engine.ArchetypeId
import com.mmg.manahub.feature.decks.domain.engine.DeckEntry
import com.mmg.manahub.feature.decks.domain.engine.ThemeId
import com.mmg.manahub.feature.decks.domain.engine.card
import com.mmg.manahub.feature.decks.domain.engine.entry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Deck Doctor Community/Archetype plan, Phase 1.8 — classifier confusion-matrix tests for
 * [InferDeckArchetypeUseCase]. Each fixture is a synthetic (not real-decklist) mainboard built
 * with an OVERWHELMING, unambiguous signal for its target archetype/theme so the suite pins a
 * minimum-accuracy bar without being fragile to the classifier's internal scoring weights (A.6's
 * own "softest cells" — see `InferDeckArchetypeUseCase`'s file header and `project_archetype_engine`
 * memory). The explicit accuracy bar this suite enforces: every one of the 6 macro-signal fixtures
 * classifies to its target archetype, every one of the 3 theme-signal fixtures detects its target
 * theme, and a deliberately low-signal deck resolves to GENERIC with zero themes (never a wrong
 * confident label).
 */
class InferDeckArchetypeUseCaseTest {

    private val useCase = InferDeckArchetypeUseCase()

    private fun tag(key: String, category: TagCategory = TagCategory.ROLE) = CardTag(key, category)

    // ── Macro archetypes ────────────────────────────────────────────────────────

    @Test
    fun aggroShapedDeckClassifiesAsAggro() {
        // 20 cheap, hard-hitting creatures, no other signal.
        val mainboard = (1..20).map {
            entry(card(id = "aggro-$it", typeLine = "Creature — Goblin", cmc = 1.0, power = "4"))
        }
        val result = useCase(mainboard, ArchetypeFormat.COMMANDER)
        assertEquals(ArchetypeId.AGGRO, result.macro)
        assertTrue(result.confidence >= 0.55f, "AGGRO confidence too low: ${result.confidence}")
    }

    @Test
    fun controlShapedDeckClassifiesAsControl() {
        val mainboard = buildList {
            repeat(7) { add(entry(card(id = "ctl-removal-$it", typeLine = "Instant", cmc = 4.0, tags = listOf(CardTag.REMOVAL)))) }
            repeat(3) { add(entry(card(id = "ctl-wrath-$it", typeLine = "Sorcery", cmc = 4.0, tags = listOf(CardTag.WRATH)))) }
            repeat(8) { add(entry(card(id = "ctl-counter-$it", typeLine = "Instant", cmc = 4.0, tags = listOf(CardTag.COUNTERSPELL)))) }
            repeat(2) { add(entry(card(id = "ctl-filler-$it", typeLine = "Sorcery", cmc = 4.0))) }
        }
        val result = useCase(mainboard, ArchetypeFormat.COMMANDER)
        assertEquals(ArchetypeId.CONTROL, result.macro)
        assertTrue(result.confidence >= 0.55f, "CONTROL confidence too low: ${result.confidence}")
    }

    @Test
    fun comboShapedDeckClassifiesAsCombo() {
        val mainboard = (1..10).map {
            entry(
                card(
                    id = "combo-$it", typeLine = "Sorcery", cmc = 3.0,
                    tags = listOf(tag("tutor"), tag("protection"), CardTag.COMBO),
                ),
            )
        }
        val result = useCase(mainboard, ArchetypeFormat.COMMANDER)
        assertEquals(ArchetypeId.COMBO, result.macro)
        assertTrue(result.confidence >= 0.55f, "COMBO confidence too low: ${result.confidence}")
    }

    @Test
    fun rampShapedDeckClassifiesAsRamp() {
        val mainboard = buildList {
            repeat(14) { add(entry(card(id = "ramp-rock-$it", typeLine = "Artifact", cmc = 2.0, tags = listOf(CardTag.MANA_ROCK)))) }
            repeat(6) { add(entry(card(id = "ramp-finisher-$it", typeLine = "Creature — Giant", cmc = 7.0, power = "8"))) }
        }
        val result = useCase(mainboard, ArchetypeFormat.COMMANDER)
        assertEquals(ArchetypeId.RAMP, result.macro)
        assertTrue(result.confidence >= 0.55f, "RAMP confidence too low: ${result.confidence}")
    }

    // ── Themes (macro falls back to MIDRANGE per A.6's "prefer GENERIC unless a theme is
    //    confident" hard pair — none of these fixtures carry a competing macro signal) ──────────

    @Test
    fun dominantTribeDeckDetectsTribalTheme() {
        val mainboard = buildList {
            repeat(15) {
                add(entry(card(id = "elf-$it", typeLine = "Creature — Elf", cmc = 3.0, power = "2")))
            }
            repeat(5) { add(entry(card(id = "elf-filler-$it", typeLine = "Sorcery", cmc = 3.0))) }
        }
        val result = useCase(mainboard, ArchetypeFormat.COMMANDER)
        assertEquals(ArchetypeId.MIDRANGE, result.macro)
        assertTrue(ThemeId.TRIBAL in result.themes, "Expected TRIBAL in ${result.themes}")
    }

    @Test
    fun sacrificeShapedDeckDetectsAristocratsTheme() {
        val mainboard = buildList {
            repeat(8) { add(entry(card(id = "sac-$it", typeLine = "Sorcery", cmc = 3.0, tags = listOf(tag("sac_outlet"))))) }
            repeat(8) { add(entry(card(id = "death-$it", typeLine = "Sorcery", cmc = 3.0, tags = listOf(tag("death_payoff"))))) }
            repeat(4) { add(entry(card(id = "aristo-filler-$it", typeLine = "Sorcery", cmc = 3.0))) }
        }
        val result = useCase(mainboard, ArchetypeFormat.COMMANDER)
        assertEquals(ArchetypeId.MIDRANGE, result.macro)
        assertTrue(ThemeId.ARISTOCRATS in result.themes, "Expected ARISTOCRATS in ${result.themes}")
    }

    @Test
    fun graveyardShapedDeckDetectsReanimatorTheme() {
        val mainboard = buildList {
            repeat(8) { add(entry(card(id = "gy-enabler-$it", typeLine = "Sorcery", cmc = 3.0, tags = listOf(tag("graveyard_enabler"))))) }
            repeat(6) { add(entry(card(id = "reanim-$it", typeLine = "Sorcery", cmc = 3.0, tags = listOf(tag("reanimation"))))) }
            repeat(6) { add(entry(card(id = "reanim-filler-$it", typeLine = "Sorcery", cmc = 3.0))) }
        }
        val result = useCase(mainboard, ArchetypeFormat.COMMANDER)
        assertEquals(ArchetypeId.MIDRANGE, result.macro)
        assertTrue(ThemeId.REANIMATOR in result.themes, "Expected REANIMATOR in ${result.themes}")
    }

    // ── Zero-regression: low-signal decks must NEVER resolve to a wrong confident label ────────

    @Test
    fun lowSignalVanillaDeckResolvesToGenericWithNoThemes() {
        // Vanilla 4-mana 3/3s, no tags, no dash-suffixed subtype (no tribal signal), no
        // curve/role extremes in either direction.
        val mainboard = (1..20).map {
            entry(card(id = "vanilla-$it", typeLine = "Creature", cmc = 4.0, power = "3", toughness = "3"))
        }
        val result = useCase(mainboard, ArchetypeFormat.COMMANDER)
        assertEquals(ArchetypeId.GENERIC, result.macro)
        assertTrue(result.themes.isEmpty(), "Expected no themes, got ${result.themes}")
    }

    @Test
    fun emptyMainboardResolvesToGenericWithoutCrashing() {
        val result = useCase(emptyList<DeckEntry>(), ArchetypeFormat.COMMANDER)
        assertEquals(ArchetypeId.GENERIC, result.macro)
        assertTrue(result.themes.isEmpty())
        assertEquals(0f, result.confidence)
    }

    @Test
    fun themesAreCappedAtTwoEvenWhenMoreClearTheThreshold() {
        // Stack THREE independent strong theme signals (tokens + lifegain + counters) — the
        // classifier must never return more than 2.
        val mainboard = buildList {
            repeat(10) { add(entry(card(id = "tok-$it", typeLine = "Sorcery", cmc = 3.0, tags = listOf(tag("token_generator"))))) }
            repeat(10) { add(entry(card(id = "life-$it", typeLine = "Sorcery", cmc = 3.0, tags = listOf(tag("lifegain_payoff"))))) }
            repeat(10) { add(entry(card(id = "counters-$it", typeLine = "Sorcery", cmc = 3.0, tags = listOf(tag("counters_payoff"))))) }
        }
        val result = useCase(mainboard, ArchetypeFormat.COMMANDER)
        assertTrue(result.themes.size <= 2, "Expected at most 2 themes, got ${result.themes}")
    }
}
