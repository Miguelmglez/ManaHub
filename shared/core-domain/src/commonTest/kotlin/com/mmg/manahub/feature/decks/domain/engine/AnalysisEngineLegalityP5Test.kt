package com.mmg.manahub.feature.decks.domain.engine

import com.mmg.manahub.core.model.DeckFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// ═══════════════════════════════════════════════════════════════════════════════
//  AnalysisEngineLegalityP5Test — Deck Analysis Engine v2 Wave 2 plan, Part B / B3
//  ("Legality & construction (P5) — Standard specifics").
//
//  Covers the 2 P5 behaviors B3 adds/verifies for 60-card constructed formats:
//    1. Standard per-card legality dispatch (Card.legalityStandard) -- already correct since
//       Phase 2, this locks it in with a dedicated Standard fixture rather than relying only on
//       the Commander illegal-card golden test.
//    2. The new Finding.SideboardOversized (WARNING) -- fires above the 15-card sideboard limit
//       for a 60-card constructed format, not at or below it.
// ═══════════════════════════════════════════════════════════════════════════════

class AnalysisEngineLegalityP5Test {

    private val scorer = DeckScorer(RoleClassifier())

    private fun profileFor(mainboard: List<DeckEntry>, colorIdentity: Set<ManaColor>): DeckProfile =
        scorer.profile(mainboard = mainboard, format = DeckFormat.STANDARD, colorIdentity = colorIdentity, seedTags = emptyList())

    /** A minimal, legal 60-card mono-red Standard mainboard: 24 distinct nonland singleton spells
     * (well above [DeckFormat.STANDARD]'s 4-copy limit) + one bulk basic-land entry closing the
     * count to exactly 60 (the format's [DeckFormat.targetDeckSize] -- avoids an unrelated
     * [Finding.DeckTooSmall] finding muddying the P5-focused assertions below). */
    private fun standardMainboard(illegalCard: com.mmg.manahub.core.model.Card? = null): List<DeckEntry> {
        val nonland = (1..24).map { i ->
            entry(
                card(
                    id = "std-filler-$i",
                    name = "Filler Creature $i",
                    typeLine = "Creature — Human",
                    cmc = 2.0,
                    colorIdentity = listOf("R"),
                    power = "2",
                    toughness = "2",
                ),
            )
        }
        val withMaybeIllegal = if (illegalCard != null) listOf(entry(illegalCard)) + nonland.drop(1) else nonland
        val nonlandCount = withMaybeIllegal.sumOf { it.quantity }
        val land = entry(
            card(id = "std-mountain", name = "Mountain", typeLine = "Basic Land — Mountain", cmc = 0.0, colorIdentity = listOf("R"), producedMana = "R"),
            quantity = 60 - nonlandCount,
        )
        return withMaybeIllegal + land
    }

    // ── 1. Standard per-card legality dispatch ─────────────────────────────────────────────

    /** A card NOT legal in Standard, included in a [DeckFormat.STANDARD] deck, produces an
     * [Finding.IllegalCard] BLOCKER and caps [DeckAnalysis.totalScore] at
     * [AnalysisEngine.ILLEGAL_DECK_SCORE_CAP] -- confirms P5's per-format dispatch reads
     * [com.mmg.manahub.core.model.Card.legalityStandard] for STANDARD (it already did; this test
     * is the first dedicated Standard-format regression lock for it). */
    @Test
    fun standardIllegalCard_producesBlockerFindingAndCapsScore() {
        val illegalCard = card(
            id = "std-illegal-1", name = "Not Standard Legal Card", typeLine = "Creature — Human",
            cmc = 3.0, colorIdentity = listOf("R"), power = "2", toughness = "2",
            legalityStandard = "not_legal",
        )
        val mainboard = standardMainboard(illegalCard)
        val colorIdentity = setOf(ManaColor.R)
        val profile = profileFor(mainboard, colorIdentity)

        val analysis = AnalysisEngine.evaluate(
            mainboard = mainboard, format = DeckFormat.STANDARD, colorIdentity = colorIdentity, profile = profile,
            archetype = null, themes = emptyList(), isManualOverride = false, confidence = 0f,
        )

        val legality = analysis.pillars.first { it.id == PillarId.LEGALITY }
        assertTrue(legality.findings.any { it is Finding.IllegalCard && it.cardName == "Not Standard Legal Card" })
        assertTrue(legality.findings.first { it is Finding.IllegalCard }.severity == FindingSeverity.BLOCKER)
        assertEquals(0, legality.subscore)
        assertTrue(analysis.totalScore <= AnalysisEngine.ILLEGAL_DECK_SCORE_CAP, "a Standard-illegal card must cap totalScore at the documented ceiling")
    }

    /** Sanity companion: an all-Standard-legal 60-card deck produces NO [Finding.IllegalCard] and
     * no BLOCKER at all (confirms the dispatch is not just "always illegal"). */
    @Test
    fun standardLegalDeck_producesNoIllegalCardFinding() {
        val mainboard = standardMainboard(illegalCard = null)
        val colorIdentity = setOf(ManaColor.R)
        val profile = profileFor(mainboard, colorIdentity)

        val analysis = AnalysisEngine.evaluate(
            mainboard = mainboard, format = DeckFormat.STANDARD, colorIdentity = colorIdentity, profile = profile,
            archetype = null, themes = emptyList(), isManualOverride = false, confidence = 0f,
        )

        val legality = analysis.pillars.first { it.id == PillarId.LEGALITY }
        assertTrue(legality.findings.none { it is Finding.IllegalCard })
        assertTrue(legality.findings.none { it.severity == FindingSeverity.BLOCKER })
        assertEquals(100, legality.subscore)
    }

    // ── 2. SideboardOversized (new Finding, Wave 2 / B3) ───────────────────────────────────

    /** Above the 15-card sideboard limit for a 60-card constructed format: fires
     * [Finding.SideboardOversized] as a WARNING, and — since it's the ONLY finding on an
     * otherwise-clean deck — does NOT zero the LEGALITY subscore or trigger the illegal-deck cap
     * (it is advisory, not construction-breaking; see [AnalysisEngine.evaluateLegality]'s KDoc). */
    @Test
    fun sideboardOversized_firesAbove15() {
        val mainboard = standardMainboard(illegalCard = null)
        val colorIdentity = setOf(ManaColor.R)
        val profile = profileFor(mainboard, colorIdentity)

        val analysis = AnalysisEngine.evaluate(
            mainboard = mainboard, format = DeckFormat.STANDARD, colorIdentity = colorIdentity, profile = profile,
            archetype = null, themes = emptyList(), isManualOverride = false, confidence = 0f,
            sideboardCount = 16,
        )

        val legality = analysis.pillars.first { it.id == PillarId.LEGALITY }
        val finding = legality.findings.filterIsInstance<Finding.SideboardOversized>().singleOrNull()
        assertTrue(finding != null, "expected a SideboardOversized finding at count=16: ${legality.findings}")
        assertEquals(16, finding.count)
        assertEquals(FindingSeverity.WARNING, finding.severity)
        // Advisory only -- must not zero the pillar subscore or trip the illegal-deck cap on its own.
        assertEquals(100, legality.subscore)
        assertTrue(analysis.totalScore > AnalysisEngine.ILLEGAL_DECK_SCORE_CAP)
    }

    /** At exactly 15 (the limit itself) and below, [Finding.SideboardOversized] must NOT fire. */
    @Test
    fun sideboardOversized_doesNotFireAt15OrBelow() {
        val mainboard = standardMainboard(illegalCard = null)
        val colorIdentity = setOf(ManaColor.R)
        val profile = profileFor(mainboard, colorIdentity)

        listOf(15, 0).forEach { count ->
            val analysis = AnalysisEngine.evaluate(
                mainboard = mainboard, format = DeckFormat.STANDARD, colorIdentity = colorIdentity, profile = profile,
                archetype = null, themes = emptyList(), isManualOverride = false, confidence = 0f,
                sideboardCount = count,
            )
            val legality = analysis.pillars.first { it.id == PillarId.LEGALITY }
            assertFalse(
                legality.findings.any { it is Finding.SideboardOversized },
                "sideboardCount=$count must not fire SideboardOversized: ${legality.findings}",
            )
        }
    }

    /** Commander is never gated by the 15-card sideboard rule (no such construction convention) —
     * an oversized "sideboard" count passed for a Commander deck must not fire the finding. */
    @Test
    fun sideboardOversized_neverFiresForCommander() {
        val commander = card(id = "cmd-sb-test", name = "Krenko, Mob Boss", typeLine = "Legendary Creature — Goblin", cmc = 3.0, colorIdentity = listOf("R"))
        val nonland = (1..20).map { i ->
            entry(card(id = "cmd-sb-filler-$i", name = "Commander Filler $i", typeLine = "Creature — Goblin", cmc = 2.0, colorIdentity = listOf("R"), power = "2", toughness = "2"))
        }
        val land = entry(
            card(id = "cmd-sb-mountain", name = "Mountain", typeLine = "Basic Land — Mountain", cmc = 0.0, colorIdentity = listOf("R"), producedMana = "R"),
            quantity = 100 - (nonland.sumOf { it.quantity } + 1),
        )
        val mainboard = listOf(entry(commander)) + nonland + land
        val colorIdentity = setOf(ManaColor.R)
        val profile = scorer.profile(mainboard = mainboard, format = DeckFormat.COMMANDER, colorIdentity = colorIdentity, seedTags = emptyList())

        val analysis = AnalysisEngine.evaluate(
            mainboard = mainboard, format = DeckFormat.COMMANDER, colorIdentity = colorIdentity, profile = profile,
            archetype = null, themes = emptyList(), isManualOverride = false, confidence = 0f,
            sideboardCount = 25,
        )

        val legality = analysis.pillars.first { it.id == PillarId.LEGALITY }
        assertTrue(legality.findings.none { it is Finding.SideboardOversized })
    }
}
