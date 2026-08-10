package com.mmg.manahub.feature.decks.domain.usecase

import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.feature.decks.domain.engine.ArchetypeFormat
import com.mmg.manahub.feature.decks.domain.engine.ArchetypeId
import com.mmg.manahub.feature.decks.domain.engine.ArchetypeSkeletonResolver
import com.mmg.manahub.feature.decks.domain.engine.DeckScorer
import com.mmg.manahub.feature.decks.domain.engine.ManaColor
import com.mmg.manahub.feature.decks.domain.engine.RoleClassifier
import com.mmg.manahub.feature.decks.domain.engine.ScoreReason
import com.mmg.manahub.feature.decks.domain.engine.card
import com.mmg.manahub.feature.decks.domain.engine.entry
import com.mmg.manahub.feature.decks.domain.engine.fixedPower
import com.mmg.manahub.feature.decks.domain.engine.landCard
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [SuggestCutsUseCase].
 *
 * Exercised against a REAL [DeckScorer] (real [RoleClassifier] + deterministic fixed power) so the
 * tests also verify the protection / land / combo-core exclusions the engine performs.
 */
class SuggestCutsUseCaseTest {

    private val dispatcher = StandardTestDispatcher()
    private val scorer = DeckScorer(RoleClassifier(), fixedPower(normalized = 0.5f))
    private val useCase = SuggestCutsUseCase(scorer, dispatcher)

    private fun profileFor(mainboard: List<com.mmg.manahub.feature.decks.domain.engine.DeckEntry>) =
        scorer.profile(
            mainboard = mainboard,
            format = DeckFormat.COMMANDER,
            colorIdentity = setOf(ManaColor.U, ManaColor.G),
            seedTags = emptyList(),
        )

    @Test
    fun `the commander is never suggested for a cut`() = runTest(dispatcher) {
        val commander = card(id = "cmd", typeLine = "Legendary Creature — Elf", power = "3")
        val mainboard = listOf(
            entry(commander),
            entry(card(id = "spell-1")),
            entry(card(id = "spell-2")),
        )
        val profile = profileFor(mainboard)

        val cuts = useCase(mainboard, profile, protectedIds = setOf("cmd"))

        assertFalse("commander must be protected", cuts.any { it.card.scryfallId == "cmd" })
    }

    @Test
    fun `lands are excluded from cut candidates`() = runTest(dispatcher) {
        val mainboard = listOf(
            entry(card(id = "spell-1")),
            entry(landCard(id = "land-1")),
            entry(landCard(id = "land-2")),
        )
        val profile = profileFor(mainboard)

        val cuts = useCase(mainboard, profile)

        assertTrue("lands must never be cut candidates", cuts.none { it.card.scryfallId.startsWith("land") })
    }

    @Test
    fun `combo cores are protected from cuts`() = runTest(dispatcher) {
        val mainboard = listOf(
            entry(card(id = "infinite", tags = listOf(CardTag.INFINITE))),
            entry(card(id = "combo", tags = listOf(CardTag.COMBO))),
            entry(card(id = "vanilla")),
        )
        val profile = profileFor(mainboard)

        val cuts = useCase(mainboard, profile)

        assertFalse(cuts.any { it.card.scryfallId == "infinite" })
        assertFalse(cuts.any { it.card.scryfallId == "combo" })
        assertTrue(cuts.any { it.card.scryfallId == "vanilla" })
    }

    @Test
    fun `cuts are sorted ascending by fit so the worst fit is first`() = runTest(dispatcher) {
        val mainboard = listOf(
            entry(card(id = "a")),
            entry(card(id = "b")),
            entry(card(id = "c")),
        )
        val profile = profileFor(mainboard)

        val cuts = useCase(mainboard, profile)

        assertEquals(cuts.map { it.score }, cuts.map { it.score }.sorted())
    }

    // ── Workstream 9.5 (Deck Wizard & Engine Rework plan) -- manabase-aware cut penalty ───

    @Test
    fun `a card whose pip cost the manabase cannot reliably support ranks as a better cut than an equally-fit castable card`() = runTest(dispatcher) {
        // 4-color identity (W/U/B/R). A triple-blue card's OWN Karsten requirement (tier2, 32 at
        // ~99 lands / 23 at <33 lands) is far above the few Islands this fixture provides, while an
        // otherwise-identical single-blue card's OWN requirement (tier0, 19/14) is comfortably met
        // by the SAME sources -- isolating "this card's OWN pip cost", not a deck-wide echo.
        val commander = card(id = "cmd", typeLine = "Legendary Creature — Elf", power = "3", colorIdentity = listOf("W", "U", "B", "R"))
        val heavyPip = card(
            id = "heavy", name = "Heavy Pip", typeLine = "Sorcery", cmc = 3.0,
            colors = listOf("U"), colorIdentity = listOf("U"), manaCost = "{U}{U}{U}",
        )
        val castable = card(
            id = "castable", name = "Castable", typeLine = "Sorcery", cmc = 2.0,
            colors = listOf("U"), colorIdentity = listOf("U"), manaCost = "{1}{U}",
        )
        // 16 Islands: enough to clear the single-pip (tier0, 14 at <33 lands) requirement `castable`
        // needs, but short of the triple-pip (tier2, 23) requirement `heavyPip` needs -- isolating
        // "this card's OWN pip cost", not a uniform shortage that would penalize both equally.
        val lands = (1..16).map { i -> entry(landCard(id = "island-$i", name = "Island $i")) }
        val mainboard = listOf(entry(commander), entry(heavyPip), entry(castable)) + lands
        val profile = scorer.profile(
            mainboard = mainboard, format = DeckFormat.COMMANDER,
            colorIdentity = setOf(ManaColor.W, ManaColor.U, ManaColor.B, ManaColor.R), seedTags = emptyList(),
        )

        val cuts = useCase(mainboard, profile, protectedIds = setOf("cmd"))

        val heavyIndex = cuts.indexOfFirst { it.card.scryfallId == "heavy" }
        val castableIndex = cuts.indexOfFirst { it.card.scryfallId == "castable" }
        assertTrue("both candidates must appear in the ranking", heavyIndex in cuts.indices && castableIndex in cuts.indices)
        assertTrue(
            "the triple-pip card the manabase cannot support (index $heavyIndex) must rank as a BETTER " +
                "cut (earlier in the ascending ranking) than the equally-fit, reliably-castable single-pip card (index $castableIndex)",
            heavyIndex < castableIndex,
        )
        val heavyReason = cuts[heavyIndex].reasons.firstOrNull { it is com.mmg.manahub.feature.decks.domain.engine.ScoreReason.UnsupportedPipCost }
        assertTrue("the unsupportable-pip cut must carry a ScoreReason.UnsupportedPipCost naming it", heavyReason != null)
    }

    // ── Workstream 8.3 (Deck Wizard & Engine Rework plan) -- gap-aware, simulate-before-suggest ──

    @Test
    fun `a card whose dominant role runs over the resolved skeleton's max is penalized and carries OverArchetypeBand`() = runTest(dispatcher) {
        // AGGRO Commander's removal_mass band is an anti-role ceiling of RoleTarget(0, 0, 2)
        // (ArchetypeData.kt, ep.658 "mass disruption 1-2"). Three board wipes puts the deck at 3,
        // over the max=2 -- every wipe should be penalized and reasoned, regardless of which
        // specific copy the ranking singles out.
        val commander = card(id = "cmd", typeLine = "Legendary Creature — Elf", power = "3")
        val wipes = (1..3).map { i ->
            entry(card(id = "wipe-$i", name = "Wipe $i", typeLine = "Sorcery", oracleText = "Destroy all creatures."))
        }
        val filler = entry(card(id = "filler", name = "Filler", typeLine = "Creature — Bear", power = "2", toughness = "2"))
        val mainboard = listOf(entry(commander)) + wipes + listOf(filler)
        val profile = profileFor(mainboard)
        val resolvedSkeleton = ArchetypeSkeletonResolver.resolveWithColor(
            format = ArchetypeFormat.COMMANDER, archetype = ArchetypeId.AGGRO, themes = emptyList(),
            identity = setOf(ManaColor.U, ManaColor.G),
        )

        val cutsWithSkeleton = useCase(mainboard, profile, protectedIds = setOf("cmd"), resolvedSkeleton = resolvedSkeleton)
        val cutsWithoutSkeleton = useCase(mainboard, profile, protectedIds = setOf("cmd"))

        wipes.forEach { wipeEntry ->
            val id = wipeEntry.card.scryfallId
            val withSkeleton = cutsWithSkeleton.first { it.card.scryfallId == id }
            val withoutSkeleton = cutsWithoutSkeleton.first { it.card.scryfallId == id }
            assertTrue(
                "$id must carry ScoreReason.OverArchetypeBand once AGGRO's removal_mass anti-role is over its max",
                withSkeleton.reasons.any { it is ScoreReason.OverArchetypeBand },
            )
            assertTrue(
                "$id's score must be penalized (lower) once the over-max band applies, versus no skeleton at all -- " +
                    "was ${withoutSkeleton.score}, now ${withSkeleton.score}",
                withSkeleton.score < withoutSkeleton.score,
            )
        }
    }

    @Test
    fun `a cut that would push a currently-healthy role band below its min is dropped entirely`() = runTest(dispatcher) {
        // CONTROL Commander's removal_mass band is RoleTarget(5, 7, 9) (ArchetypeData.kt). Exactly
        // 5 (the min) board wipes means the band is CURRENTLY healthy but with zero slack -- cutting
        // ANY one of them would drop the count to 4, below min. None may appear in the ranking.
        val commander = card(id = "cmd", typeLine = "Legendary Creature — Elf", power = "3")
        val wipes = (1..5).map { i ->
            entry(card(id = "wipe-$i", name = "Wipe $i", typeLine = "Sorcery", oracleText = "Destroy all creatures."))
        }
        val filler = entry(card(id = "filler", name = "Filler", typeLine = "Creature — Bear", power = "2", toughness = "2"))
        val mainboard = listOf(entry(commander)) + wipes + listOf(filler)
        val profile = profileFor(mainboard)
        val resolvedSkeleton = ArchetypeSkeletonResolver.resolveWithColor(
            format = ArchetypeFormat.COMMANDER, archetype = ArchetypeId.CONTROL, themes = emptyList(),
            identity = setOf(ManaColor.U, ManaColor.G),
        )

        val cuts = useCase(mainboard, profile, protectedIds = setOf("cmd"), resolvedSkeleton = resolvedSkeleton)

        wipes.forEach { wipeEntry ->
            assertFalse(
                "${wipeEntry.card.scryfallId} must be DROPPED from the ranking entirely -- cutting it would " +
                    "push removal_mass (currently exactly at CONTROL's min of 5) below that min",
                cuts.any { it.card.scryfallId == wipeEntry.card.scryfallId },
            )
        }
        assertTrue("a card unrelated to the protected band must still be a normal cut candidate", cuts.any { it.card.scryfallId == "filler" })
    }
}
