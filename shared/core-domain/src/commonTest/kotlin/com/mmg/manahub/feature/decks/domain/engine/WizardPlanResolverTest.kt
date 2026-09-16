package com.mmg.manahub.feature.decks.domain.engine

import com.mmg.manahub.core.domain.usecase.decks.BasicLandCalculator
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.feature.decks.domain.engine.analysisv3.fixture14MonoRedBurn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Deck Wizard 60-card wave (v6), plan §5 Phase 1.2 gate: [WizardPlanResolver]'s new
 * [BuildAnchor]-based `resolve` overload. (a) the Commander anchor must resolve BYTE-IDENTICAL
 * output to the pre-v6 4-arg overload (plan rule 0.3 -- reuses `CommanderPlanResolverTest`'s own
 * `identities`/`commanderFormats`/`testCommander` fixtures). (b)-(d) cover the new
 * [BuildAnchor.Sixty] branch.
 */
class WizardPlanResolverTest {

    private val identities: List<Set<ManaColor>> = listOf(
        setOf(ManaColor.G), // mono
        setOf(ManaColor.U, ManaColor.B), // 2-color
        setOf(ManaColor.W, ManaColor.U, ManaColor.B), // 3-color
        setOf(ManaColor.W, ManaColor.U, ManaColor.B, ManaColor.R, ManaColor.G), // 5-color
    )

    private val commanderFormats = listOf(DeckFormat.COMMANDER, DeckFormat.COMMANDER_CASUAL)

    private fun testCommander(identity: Set<ManaColor>): Card = card(
        id = "cmd-${identity.joinToString("") { it.symbol }}",
        name = "Test Commander",
        typeLine = "Legendary Creature — Human",
        cmc = 4.0,
        colors = identity.map { it.symbol },
        colorIdentity = identity.map { it.symbol },
    )

    @Test
    fun `(a) Commander anchor equals the old 4-arg resolve for every catalog entry and Custom`() {
        commanderFormats.forEach { format ->
            val entries = CuratedStrategyCatalog.ALL.filter { it.availableIn(format) }
            assertTrue(entries.isNotEmpty(), "expected at least one catalog entry for $format")

            entries.forEach { strategy ->
                val tribe = if (strategy.requiresTribe) "tribe:elf" else null
                val pick = StrategyPick.Curated(strategy, tribe)

                identities.forEach { identity ->
                    val commander = testCommander(identity)
                    assertEquals(
                        WizardPlanResolver.resolve(format, commander, pick, identity),
                        WizardPlanResolver.resolve(format, BuildAnchor.Commander(commander), pick),
                        "mismatch for '${strategy.id}' format=$format identity=$identity",
                    )
                }
            }

            val commander = testCommander(setOf(ManaColor.G))
            assertEquals(
                WizardPlanResolver.resolve(format, commander, StrategyPick.Custom, setOf(ManaColor.G)),
                WizardPlanResolver.resolve(format, BuildAnchor.Commander(commander), StrategyPick.Custom),
            )
        }
    }

    @Test
    fun `(b) Sixty Custom with fixture-14 seeds resolves an AGGRO-hinted skeleton`() {
        val seeds = fixture14MonoRedBurn().mainboard
            .filterNot { BasicLandCalculator.isLand(it.card) }
            .map { it.card }

        val plan = WizardPlanResolver.resolve(
            DeckFormat.MODERN,
            BuildAnchor.Sixty(setOf(ManaColor.R), seeds),
            StrategyPick.Custom,
        )
        assertEquals(ArchetypeId.AGGRO, plan.skeleton.archetype)
    }

    @Test
    fun `(c) Sixty Curated tribal + tribe merfolk puts TRIBE colon merfolk in targetAxes`() {
        val tribal = CuratedStrategyCatalog.ALL.first { it.id == "tribal" }
        val pick = StrategyPick.Curated(tribal, "tribe:merfolk")

        val plan = WizardPlanResolver.resolve(
            DeckFormat.STANDARD,
            BuildAnchor.Sixty(setOf(ManaColor.U), emptyList()),
            pick,
        )
        assertTrue(WizardPlanResolver.tribeAxisKey("tribe:merfolk") in plan.targetAxes)
    }

    @Test
    fun `(d) colorless identity resolves without a mana_fix band and lands 22-24-26 for MODERN`() {
        val plan = WizardPlanResolver.resolve(
            DeckFormat.MODERN,
            BuildAnchor.Sixty(emptySet(), emptyList()),
            StrategyPick.Custom,
        )
        assertNull(plan.manaFixBand)
        assertEquals(RoleTarget(22, 24, 26), plan.landBand)
    }
}
