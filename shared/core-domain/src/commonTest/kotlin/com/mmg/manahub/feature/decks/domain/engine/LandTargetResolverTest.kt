package com.mmg.manahub.feature.decks.domain.engine

import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.DeckFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Deck Wizard & Engine Rework plan, Workstream 6 ("One land engine") — [LandTargetResolver] is the
 * single shared "how many lands should this deck have" computation now called by BOTH
 * `BuildDeckFromTemplateUseCase` (the wizard, at build time) and `DeckStudioViewModel`
 * (`calculateLandDeltas`, Studio's basic-land suggestion). This suite pins down the behavioral
 * split it consolidates: Commander/other non-60-card formats use the resolved archetype skeleton's
 * own land ideal (falling back to the generic per-format default); 60-card constructed formats use
 * [ManaBaseAnalyzer.dynamicLandIdeal]'s relax-only shift over the GENERIC per-format skeleton
 * (never the archetype skeleton — see [ManaBaseAnalyzer.dynamicLandIdeal]'s own KDoc for why).
 */
class LandTargetResolverTest {

    private val analyzer = ManaBaseAnalyzer()
    private val scorer = DeckScorer(RoleClassifier(), NeutralPowerResolver, analyzer)

    // ── Commander / non-60-card branch ──────────────────────────────────────────────

    @Test
    fun `Commander with a resolved skeleton uses the skeleton's own land ideal`() {
        val skeleton = ArchetypeSkeletonResolver.resolveWithColorCount(
            format = ArchetypeFormat.COMMANDER,
            archetype = ArchetypeId.AGGRO,
            themes = emptyList(),
            colorCount = 0, // skip color modulation so the ideal is exactly the archetype's raw band
        )
        // WS5 retune (2026-07-28): AGGRO Commander lands re-anchored to (33,35,37) -- ep.658's
        // "34-36" row (plan line ~306) -- was (30,32,35) pre-retune.
        assertEquals(35, skeleton.lands.ideal, "sanity: AGGRO Commander's own land ideal is 35")
        val result = LandTargetResolver.resolve(
            format = DeckFormat.COMMANDER,
            archetypeSkeleton = skeleton,
            profile = null,
            manaBaseAnalyzer = analyzer,
        )
        assertEquals(35, result)
    }

    @Test
    fun `Commander with no skeleton (GENERIC, no themes) falls back to the generic per-format ideal`() {
        val genericIdeal = DeckSkeletons.forFormat(DeckFormat.COMMANDER).idealFor(DeckRole.LAND)
        assertEquals(37, genericIdeal, "sanity: generic Commander land ideal is 37")
        val result = LandTargetResolver.resolve(
            format = DeckFormat.COMMANDER,
            archetypeSkeleton = null,
            profile = null,
            manaBaseAnalyzer = analyzer,
        )
        assertEquals(genericIdeal, result)
    }

    // ── 60-card constructed branch (dynamicLandIdeal) ───────────────────────────────

    @Test
    fun `60-card with a ramp-heavy profile relaxes below the generic skeleton ideal via dynamicLandIdeal`() {
        val genericIdeal = DeckSkeletons.forFormat(DeckFormat.STANDARD).idealFor(DeckRole.LAND)
        val baseSpells = buildList {
            repeat(30) { i ->
                add(
                    entry(
                        card(
                            id = "spell-$i", name = "Spell $i", typeLine = "Creature — Beast",
                            cmc = 2.0, colors = listOf("G"), colorIdentity = listOf("G"),
                            tags = listOf(CardTag.WIN_CON), manaCost = "{1}{G}",
                        ),
                    ),
                )
            }
        }
        val cheapRamp = buildList {
            repeat(8) { i ->
                add(
                    entry(
                        card(
                            id = "rock-$i", name = "Cheap Rock $i", typeLine = "Artifact",
                            cmc = 1.0, colors = emptyList(), colorIdentity = emptyList(),
                            oracleText = "{T}: Add one mana of any color.", tags = listOf(CardTag.MANA_ROCK),
                            manaCost = "{1}",
                        ),
                    ),
                )
            }
        }
        val landPack = listOf(
            entry(card(id = "forest", typeLine = "Basic Land — Forest", colorIdentity = listOf("G"), cmc = 0.0), quantity = genericIdeal),
        )
        val mainboard = baseSpells + cheapRamp + landPack
        val profile = scorer.profile(mainboard, DeckFormat.STANDARD, setOf(ManaColor.G), emptyList())

        val result = LandTargetResolver.resolve(
            format = DeckFormat.STANDARD,
            archetypeSkeleton = null, // the 60-card branch never reads the archetype skeleton for its own ideal
            profile = profile,
            manaBaseAnalyzer = analyzer,
        )

        assertEquals(
            analyzer.dynamicLandIdeal(profile), result,
            "the 60-card branch must return EXACTLY dynamicLandIdeal's own output, never a re-derived value",
        )
        assertTrue(result < genericIdeal, "8 cheap ramp pieces must relax the land count below the generic ideal ($genericIdeal)")
    }

    @Test
    fun `60-card with no profile falls back to the archetype skeleton ideal when present`() {
        val skeleton = ArchetypeSkeletonResolver.resolveWithColorCount(
            format = ArchetypeFormat.SIXTY,
            archetype = ArchetypeId.AGGRO,
            themes = emptyList(),
            colorCount = 0,
        )
        assertEquals(21, skeleton.lands.ideal, "sanity: AGGRO 60-card's own land ideal is 21")
        val result = LandTargetResolver.resolve(
            format = DeckFormat.STANDARD,
            archetypeSkeleton = skeleton,
            profile = null,
            manaBaseAnalyzer = analyzer,
        )
        assertEquals(21, result)
    }

    @Test
    fun `60-card with no profile and no skeleton falls back to the generic per-format ideal`() {
        val genericIdeal = DeckSkeletons.forFormat(DeckFormat.STANDARD).idealFor(DeckRole.LAND)
        val result = LandTargetResolver.resolve(
            format = DeckFormat.STANDARD,
            archetypeSkeleton = null,
            profile = null,
            manaBaseAnalyzer = analyzer,
        )
        assertEquals(genericIdeal, result)
    }

    // ── band() ───────────────────────────────────────────────────────────────────

    @Test
    fun `band reflects the resolved skeleton's own land band when present, Commander`() {
        val skeleton = ArchetypeSkeletonResolver.resolveWithColorCount(
            format = ArchetypeFormat.COMMANDER, archetype = ArchetypeId.AGGRO, themes = emptyList(), colorCount = 0,
        )
        // WS5 retune (2026-07-28): AGGRO Commander lands band (33,35,37) -- was (30,32,35).
        assertEquals(33..37, LandTargetResolver.band(DeckFormat.COMMANDER, skeleton))
    }

    @Test
    fun `band falls back to the generic per-format skeleton's land slot bounds when no skeleton, Commander`() {
        val slot = DeckSkeletons.forFormat(DeckFormat.COMMANDER).slots.first { it.role == DeckRole.LAND }
        assertEquals(slot.min..slot.max, LandTargetResolver.band(DeckFormat.COMMANDER, null))
    }

    @Test
    fun `band reflects the resolved skeleton's own land band when present, 60-card`() {
        val skeleton = ArchetypeSkeletonResolver.resolveWithColorCount(
            format = ArchetypeFormat.SIXTY, archetype = ArchetypeId.AGGRO, themes = emptyList(), colorCount = 0,
        )
        assertEquals(19..23, LandTargetResolver.band(DeckFormat.STANDARD, skeleton))
    }

    @Test
    fun `band falls back to the generic per-format skeleton's land slot bounds when no skeleton, 60-card`() {
        val slot = DeckSkeletons.forFormat(DeckFormat.STANDARD).slots.first { it.role == DeckRole.LAND }
        assertEquals(slot.min..slot.max, LandTargetResolver.band(DeckFormat.STANDARD, null))
    }
}
