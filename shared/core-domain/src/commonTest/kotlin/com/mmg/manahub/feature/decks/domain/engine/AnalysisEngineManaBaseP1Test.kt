package com.mmg.manahub.feature.decks.domain.engine

import com.mmg.manahub.core.model.DeckFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Deck Wizard 60-card wave (v6), plan §5 Phase 3.1/3.2 gate: [AnalysisEngine.evaluateManaBase]'s
 * (`PillarId.MANA_BASE`) new gap-table `produces:X` sections and the new `lands` section. Mirrors
 * [AnalysisEngineSynergyP4Test]'s own `DeckScorer(RoleClassifier()).profile(...)` +
 * `AnalysisEngine.evaluate(...)` call shape.
 */
class AnalysisEngineManaBaseP1Test {

    private fun manaBaseOf(mainboard: List<DeckEntry>, identity: Set<ManaColor>, format: DeckFormat = DeckFormat.COMMANDER): PillarResult {
        val profile = DeckScorer(RoleClassifier()).profile(mainboard = mainboard, format = format, colorIdentity = identity, seedTags = emptyList())
        val analysis = AnalysisEngine.evaluate(
            mainboard = mainboard, format = format, colorIdentity = identity, profile = profile,
            archetype = null, themes = emptyList(), isManualOverride = false, confidence = 0f,
        )
        return analysis.pillars.first { it.id == PillarId.MANA_BASE }
    }

    @Test
    fun `produces X is emitted at count 0 for an identity color the deck produces nothing of`() {
        val identity = setOf(ManaColor.U, ManaColor.W) // deck only makes W, U is in identity but unproduced
        val mainboard = listOf(entry(card(id = "plains-1", name = "Plains", typeLine = "Basic Land — Plains", colorIdentity = listOf("W"), producedMana = "W"), quantity = 24))
        val manaBase = manaBaseOf(mainboard, identity)
        val uSection = manaBase.sections.firstOrNull { it.id == "produces:U" }
        assertNotNull(uSection, "identity color U with zero production must still emit a produces:U section (gap table), never be omitted")
        assertEquals(0, uSection.current)
    }

    @Test
    fun `produces X min and ideal equal the Karsten need for that color at max single-card pip intensity, max is null`() {
        // Force a real ColorSourceShortage so ManaBaseAnalyzer.analyze's requiredByColor[U] is non-trivial.
        val identity = setOf(ManaColor.U)
        val heavyBlue = card(id = "heavy-blue", typeLine = "Sorcery", colorIdentity = listOf("U"), manaCost = "{U}{U}{U}")
        val mainboard = listOf(entry(heavyBlue, quantity = 1), entry(card(id = "island-1", name = "Island", typeLine = "Basic Land — Island", colorIdentity = listOf("U"), producedMana = "U"), quantity = 2))
        val profile = DeckScorer(RoleClassifier()).profile(mainboard = mainboard, format = DeckFormat.STANDARD, colorIdentity = identity, seedTags = emptyList())
        val manaReport = ManaBaseAnalyzer().analyze(mainboard, profile)
        val expectedNeed = manaReport.requiredByColor.getValue(ManaColor.U) // the SAME number ColorSourceShortage.needed would carry
        val uSection = manaBaseOf(mainboard, identity, format = DeckFormat.STANDARD).sections.first { it.id == "produces:U" }
        assertEquals(expectedNeed, uSection.min)
        assertEquals(expectedNeed, uSection.ideal)
        assertNull(uSection.max)
    }

    @Test
    fun `an off-identity produced color is still emitted only when actually produced`() {
        // R is OUTSIDE the {U} identity, but the deck runs an off-identity dual -- current rule (unchanged).
        val identity = setOf(ManaColor.U)
        val offIdentityDual = card(id = "off-dual", name = "Off Identity Dual", typeLine = "Land", colorIdentity = emptyList(), producedMana = "UR")
        val mainboard = listOf(entry(offIdentityDual, quantity = 1))
        val manaBase = manaBaseOf(mainboard, identity)
        assertNotNull(manaBase.sections.firstOrNull { it.id == "produces:R" }, "an actually-produced off-identity color must still show (unchanged rule)")
        assertNull(manaBase.sections.firstOrNull { it.id == "produces:B" }, "an off-identity color with ZERO production must stay omitted -- only identity colors get the gap-table 0 treatment")
    }

    @Test
    fun `colorless identity emits produces C only when a C pip exists`() {
        val identity = emptySet<ManaColor>()
        val noColorlessPip = listOf(entry(card(id = "vanilla-artifact", typeLine = "Artifact"), quantity = 1))
        assertNull(manaBaseOf(noColorlessPip, identity).sections.firstOrNull { it.id == "produces:C" })
        val withColorlessPip = listOf(entry(card(id = "eldrazi-thing", typeLine = "Creature — Eldrazi", manaCost = "{2}{C}"), quantity = 1))
        assertNotNull(manaBaseOf(withColorlessPip, identity).sections.firstOrNull { it.id == "produces:C" })
    }

    @Test
    fun `a new lands section is first in MANA_BASE, band is skeleton lands, contributions are every land entry`() {
        val identity = setOf(ManaColor.R)
        val lands = listOf(entry(card(id = "mountain-1", name = "Mountain", typeLine = "Basic Land — Mountain", colorIdentity = listOf("R"), producedMana = "R"), quantity = 22))
        val spells = listOf(entry(card(id = "bolt-fixture", typeLine = "Instant", colorIdentity = listOf("R")), quantity = 20))
        val manaBase = manaBaseOf(lands + spells, identity, format = DeckFormat.STANDARD)
        val landsSection = manaBase.sections.first()
        assertEquals("lands", landsSection.id)
        assertEquals("Lands", landsSection.label)
        assertEquals(22, landsSection.current)
        assertEquals(22, landsSection.contributions.sumOf { it.quantity }, "contributions must be every land entry, matching current")
    }
}
