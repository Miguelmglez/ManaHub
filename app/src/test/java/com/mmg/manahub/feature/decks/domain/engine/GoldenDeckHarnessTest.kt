package com.mmg.manahub.feature.decks.domain.engine

import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.DeckFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Test

/**
 * Phase 0 — Golden-deck quality-invariant harness.
 *
 * These tests assert RELATIVE quality invariants over the [GoldenDecks] fixtures
 * (e.g. "the broken deck scores below the tuned deck") rather than exact numbers,
 * so they survive engine re-tuning while still pinning down quality.
 *
 * LIVE vs @Ignore policy (per the Phase 0 acceptance criteria):
 *  - An invariant the CURRENT engine already satisfies is a LIVE @Test.
 *  - An invariant the current engine FAILS (a documented gap in the plan, sections
 *    A–E) is encoded but @Ignore'd with the Phase that is scheduled to fix it.
 *    Later phases delete the @Ignore and the assertion becomes a regression guard.
 *
 * Power signal: [EdhrecPowerResolver] backed by `Card.edhrecRank` (the recommended
 * resolver) so staple-vs-jank ordering is meaningful. The fixtures set realistic
 * ranks; unranked cards (Standard fixtures) fall back to the resolver's
 * "slightly below average" default, which is fine for the relative assertions here.
 */
class GoldenDeckHarnessTest {

    private lateinit var scorer: DeckScorer

    @Before
    fun setUp() {
        // EdhrecPowerResolver { it.edhrecRank } is the resolver the production app
        // will use once ranks are persisted (they already are, on Card.edhrecRank).
        scorer = DeckScorer(RoleClassifier(), EdhrecPowerResolver { it.edhrecRank })
    }

    // ── Small helper: profile + evaluate a golden deck in one step. ────────────
    private fun health(deck: GoldenDeck): Int = evaluate(deck).healthScore

    private fun evaluate(deck: GoldenDeck): DeckEvaluation {
        val profile = scorer.profile(deck.mainboard, deck.format, deck.colorIdentity, deck.seedTags)
        // Phase 4: pass the FULL mainboard so construction validation (C5) runs in the harness.
        return scorer.evaluate(profile, deck.nonLand, fullMainboard = deck.mainboard)
    }

    private fun profileOf(deck: GoldenDeck): DeckProfile =
        scorer.profile(deck.mainboard, deck.format, deck.colorIdentity, deck.seedTags)

    // ═══════════════════════════════════════════════════════════════════════════
    //  Group 1: health ordering invariants (LIVE)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `tuned commander deck is healthier than a deck with no interaction`() {
        val tuned = health(GoldenDecks.tunedCommander())
        val broken = health(GoldenDecks.brokenNoInteraction())
        assertTrue(
            "Tuned ($tuned) must be healthier than no-interaction broken ($broken)",
            tuned > broken,
        )
    }

    @Test
    fun `tuned commander deck is healthier than the 25-land broken deck`() {
        val tuned = health(GoldenDecks.tunedCommander())
        val fewLands = health(GoldenDecks.brokenTwentyFiveLands())
        assertTrue(
            "Tuned ($tuned) must be healthier than the 25-land deck ($fewLands)",
            tuned > fewLands,
        )
    }

    @Test
    fun `the no-interaction broken deck does not score as healthy`() {
        // It covers zero functional roles → role coverage drags health down.
        val broken = health(GoldenDecks.brokenNoInteraction())
        assertTrue("No-interaction deck health ($broken) should be well below 60", broken < 60)
    }

    @Test
    fun `the 25-land commander deck triggers a too-few-lands warning`() {
        val eval = evaluate(GoldenDecks.brokenTwentyFiveLands())
        assertTrue(
            "25 lands in Commander must flag TooFewLands",
            eval.warnings.any { it is DeckWarning.TooFewLands },
        )
    }

    @Test
    fun `the tuned commander deck has no land warning`() {
        val eval = evaluate(GoldenDecks.tunedCommander())
        assertFalse(
            "Tuned deck (37 lands) must not flag a land warning",
            eval.warnings.any { it is DeckWarning.TooFewLands || it is DeckWarning.TooManyLands },
        )
    }

    @Test
    fun `the no-interaction deck flags missing functional roles`() {
        val eval = evaluate(GoldenDecks.brokenNoInteraction())
        val missing = eval.warnings.filterIsInstance<DeckWarning.MissingRole>().map { it.role }
        assertTrue("Expected a MissingRole for RAMP", missing.contains(DeckRole.RAMP))
        assertTrue("Expected a MissingRole for SPOT_REMOVAL", missing.contains(DeckRole.SPOT_REMOVAL))
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Group 2: classification sanity on real staples (LIVE)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `Wrath of God classifies as BOARD_WIPE`() {
        val roles = RoleClassifier().classify(GoldenStaples.wrathOfGod())
        assertTrue(roles.contains(DeckRole.BOARD_WIPE))
        assertFalse("A wipe must not also be tagged spot removal", roles.contains(DeckRole.SPOT_REMOVAL))
    }

    @Test
    fun `tagged staples in the tuned deck cover every functional skeleton role`() {
        val profile = profileOf(GoldenDecks.tunedCommander())
        val covered = profile.roleCounts.filterValues { it > 0 }.keys
        listOf(
            DeckRole.RAMP, DeckRole.CARD_ADVANTAGE, DeckRole.SPOT_REMOVAL,
            DeckRole.BOARD_WIPE, DeckRole.INTERACTION, DeckRole.TUTOR,
        ).forEach { role ->
            assertTrue("Tuned deck should cover $role", covered.contains(role))
        }
    }

    @Test
    fun `cEDH deck is dense in tutors and interaction`() {
        val profile = profileOf(GoldenDecks.cedhCommander())
        assertTrue(
            "cEDH should run many tutors",
            (profile.roleCounts[DeckRole.TUTOR] ?: 0f) >= 5f,
        )
        assertTrue(
            "cEDH should run heavy interaction",
            (profile.roleCounts[DeckRole.INTERACTION] ?: 0f) >= 8f,
        )
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Group 3: cut quality — staples are not cut (LIVE)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `no top EDHREC staple appears in the tuned deck's top-5 cut candidates`() {
        val deck = GoldenDecks.tunedCommander()
        val profile = profileOf(deck)
        val cuts = scorer.rankCuts(deck.mainboard, profile).take(5).map { it.card.scryfallId }.toSet()
        // The deck's most-played staples (lowest edhrecRank) must survive the cut list.
        val topStaples = setOf("sol-ring", "rhystic-study", "swords", "demonic-tutor")
        topStaples.forEach { staple ->
            assertFalse(
                "Top staple $staple must not be in the top-5 cut candidates ($cuts)",
                cuts.contains(staple),
            )
        }
    }

    @Test
    fun `combo core cards are never offered as cuts`() {
        val deck = GoldenDecks.cedhCommander()
        val profile = profileOf(deck)
        val cuts = scorer.rankCuts(deck.mainboard, profile).map { it.card.scryfallId }.toSet()
        assertFalse("INFINITE-tagged combo piece must be protected", cuts.contains("combo-a"))
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Group 4: add quality — Elf tribal suggestions (LIVE part)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Controlled candidate pool: 8 Elves + 4 off-tribe cards. The Elf deck's
     * fingerprint (elf + tribal) should rank Elves highly. This is the LIVE half
     * of the Elf invariant — the current engine DOES match the single `tribal`/
     * `elf` key, so on-tribe cards rank above unrelated colorless filler.
     */
    @Test
    fun `Elf deck top-10 adds contain at least 6 elf-relevant cards`() {
        val deck = GoldenDecks.elfTribalCommander()
        val profile = profileOf(deck)
        val elf = tribeTag("elf")
        val candidates = buildList {
            // 8 Elf candidates
            repeat(8) { i ->
                add(
                    goldenCard(
                        id = "cand-elf-$i", name = "Candidate Elf $i",
                        typeLine = "Creature — Elf Scout",
                        cmc = 2.0, colors = listOf("G"), colorIdentity = listOf("G"),
                        power = "2", toughness = "2",
                        oracleText = "Other Elves you control get +1/+1.",
                        tags = listOf(elf, CardTag.TRIBAL), edhrecRank = 1500 + i,
                    ),
                )
            }
            // 4 off-tribe filler (in-color so they pass the hard filter)
            repeat(4) { i ->
                add(
                    goldenCard(
                        id = "cand-filler-$i", name = "Filler $i",
                        typeLine = "Creature — Bear",
                        cmc = 4.0, colors = listOf("G"), colorIdentity = listOf("G"),
                        power = "2", toughness = "2", oracleText = "Vigilance.",
                        tags = emptyList(), edhrecRank = 9000 + i,
                    ),
                )
            }
        }
        val top10 = scorer.rankAdds(candidates, profile, ownedIds = emptySet(), limit = 10)
        val elfCount = top10.count { fit ->
            (fit.card.tags + fit.card.userTags).any { it.key == elf.key }
        }
        assertTrue(
            "Top-10 Elf-deck adds should contain >= 6 Elf cards (was $elfCount)",
            elfCount >= 6,
        )
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Group 5: degenerate decks must not crash (LIVE)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `empty deck evaluates with a bounded health score and does not crash`() {
        val eval = evaluate(GoldenDecks.emptyDeck())
        assertTrue(eval.healthScore in 0..100)
        assertEquals(0f, eval.synergyDensity, 0.001f)
    }

    @Test
    fun `tiny deck evaluates with a bounded health score and does not crash`() {
        val eval = evaluate(GoldenDecks.tinyDeck())
        assertTrue(eval.healthScore in 0..100)
    }

    @Test
    fun `every golden deck evaluates within bounds`() {
        GoldenDecks.all().forEach { deck ->
            val eval = evaluate(deck)
            assertTrue("${deck.name} health out of bounds", eval.healthScore in 0..100)
            assertTrue("${deck.name} density out of bounds", eval.synergyDensity in 0f..1f)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Group 6: 20-board-wipes deck (LIVE: not high; @Ignore: over-coverage math)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `the 20-board-wipes deck is not rated highly healthy`() {
        // LIVE: with zero coverage of the other roles, role-averaging already keeps
        // this deck's health modest. We only assert the weak invariant here.
        val wipes = health(GoldenDecks.brokenTwentyWipes())
        assertTrue("20-wipes deck health ($wipes) should not be high (< 70)", wipes < 70)
    }

    @Test
    fun `the 20-board-wipes deck health drops below 60 once over-coverage is penalised`() {
        val wipes = health(GoldenDecks.brokenTwentyWipes())
        assertTrue("20-wipes deck health ($wipes) should drop below 60", wipes < 60)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Group 7: curve invariants (@Ignore — Phase 3 C1/C2)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `standard aggro has a healthier curve profile than a top-heavy build`() {
        val aggro = profileOf(GoldenDecks.standardAggro())
        // Aggro avg CMC should sit low; a properly-tuned engine should reward it.
        assertTrue("Aggro avg CMC should be low (was ${aggro.avgCmc})", aggro.avgCmc < 2.0)
        // Placeholder for the future target-curve comparison; un-ignored in Phase 3.
        assertTrue(aggro.curveHistogram.isNotEmpty())
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Group 4 (Phase 4): format correctness + construction validation (LIVE)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `standard aggro deck is validated against the 60-card construction rules`() {
        val deck = GoldenDecks.standardAggro()
        val total = deck.mainboard.sumOf { it.quantity }
        assertEquals(60, total)
        // A well-formed 60-card Standard deck triggers NO construction warnings.
        val eval = evaluate(deck)
        assertFalse(
            "A legal 60-card Standard deck must not be flagged DeckTooSmall",
            eval.warnings.any { it is DeckWarning.DeckTooSmall },
        )
        assertFalse(
            "A legal 60-card Standard deck must not be flagged TooManyCopies",
            eval.warnings.any { it is DeckWarning.TooManyCopies },
        )
    }

    @Test
    fun `a 5-copy non-basic in a Standard deck flags TooManyCopies`() {
        val eval = evaluate(GoldenDecks.standardFiveCopyViolation())
        val tooMany = eval.warnings.filterIsInstance<DeckWarning.TooManyCopies>()
        assertTrue(
            "A 5-of non-basic in Standard must flag TooManyCopies (warnings=${eval.warnings})",
            tooMany.any { it.copies == 5 && it.maxCopies == 4 },
        )
    }

    @Test
    fun `a Commander deck with a duplicated non-basic flags SingletonViolation`() {
        val eval = evaluate(GoldenDecks.commanderSingletonViolation())
        assertTrue(
            "A duplicated non-basic in Commander must flag SingletonViolation (warnings=${eval.warnings})",
            eval.warnings.any { it is DeckWarning.SingletonViolation },
        )
    }

    @Test
    fun `a deck below the format minimum flags DeckTooSmall`() {
        // The tiny deck (2 spells + 3 lands) is far below the 100-card Commander minimum.
        val eval = evaluate(GoldenDecks.tinyDeck())
        assertTrue(
            "A 5-card Commander deck must flag DeckTooSmall (warnings=${eval.warnings})",
            eval.warnings.any { it is DeckWarning.DeckTooSmall },
        )
    }

    @Ignore("Pauper format hidden for release — the DeckFormat.PAUPER entry is commented out " +
        "in production, so the fixture now builds as CASUAL and these Pauper-skeleton/legality " +
        "assertions no longer apply. Un-ignore if/when Pauper is restored.")
    @Test
    fun `every Pauper golden card is legal in Pauper and the deck uses the Pauper skeleton`() {
        val deck = GoldenDecks.pauperDeck()
        // The fixture's spells are legal:pauper ONLY — assert the legality field the engine reads.
        deck.nonLand.forEach { entry ->
            assertEquals(
                "Pauper fixture card ${entry.card.name} must be legal:pauper",
                "legal", entry.card.legalityPauper,
            )
            assertEquals(
                "Pauper fixture card ${entry.card.name} must NOT be standard-legal",
                "not_legal", entry.card.legalityStandard,
            )
        }
        val profile = scorer.profile(deck.mainboard, deck.format, deck.colorIdentity, deck.seedTags)
        // Pauper hidden for release: the fixture now resolves to the CASUAL skeleton.
        assertEquals(DeckFormat.CASUAL, profile.skeleton.format)
    }

    @Ignore("Pauper format hidden for release — see the companion Pauper test above. The fixture " +
        "now builds as CASUAL (permissive legality), so Pauper-vs-Standard add filtering no longer " +
        "applies. Un-ignore if/when Pauper is restored.")
    @Test
    fun `Pauper add candidates are filtered by Pauper legality, not Standard`() {
        val deck = GoldenDecks.pauperDeck()
        val profile = scorer.profile(deck.mainboard, deck.format, deck.colorIdentity, deck.seedTags)
        // A common (Pauper-legal, Standard-illegal) card must be a legal add candidate in Pauper.
        val pauperCommon = goldenCard(
            id = "pool-common", name = "Pool Common", typeLine = "Instant",
            cmc = 1.0, colors = listOf("U"), colorIdentity = listOf("U"),
            oracleText = "Counter target spell.", tags = listOf(CardTag.COUNTERSPELL),
            legalityStandard = "not_legal", legalityCommander = "legal",
            legalityPioneer = "not_legal", legalityModern = "not_legal",
            legalityLegacy = "not_legal", legalityVintage = "not_legal",
            legalityPauper = "legal",
        )
        // A rare (Standard-legal, Pauper-illegal) card must NOT be a legal add candidate in Pauper.
        val standardRare = goldenCard(
            id = "pool-rare", name = "Pool Rare", typeLine = "Instant",
            cmc = 1.0, colors = listOf("U"), colorIdentity = listOf("U"),
            oracleText = "Counter target spell.", tags = listOf(CardTag.COUNTERSPELL),
            legalityStandard = "legal", legalityCommander = "legal",
            legalityPioneer = "legal", legalityModern = "legal",
            legalityLegacy = "legal", legalityVintage = "legal",
            legalityPauper = "not_legal",
        )
        assertTrue(
            "A Pauper-legal common must be a legal add candidate in a Pauper deck",
            scorer.fit(pauperCommon, profile, isOwned = false).isLegal,
        )
        assertFalse(
            "A Pauper-illegal rare must NOT be a legal add candidate in a Pauper deck",
            scorer.fit(standardRare, profile, isOwned = false).isLegal,
        )
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Group 4 (Phase 5): mana-base fixing analysis (LIVE)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `a four-color deck with no fixing flags an unfixed splash`() {
        val eval = evaluate(GoldenDecks.fourColorNoFixing())
        assertTrue(
            "A 4-color deck whose lands are all Plains must flag UnfixedSplash (warnings=${eval.warnings})",
            eval.warnings.any { it is DeckWarning.UnfixedSplash },
        )
    }

    @Test
    fun `a clean mono-color deck flags no mana-base shortage`() {
        val eval = evaluate(GoldenDecks.cleanMonoColor())
        assertFalse(
            "A mono-green deck with 37 Forests must not flag any color-source shortage",
            eval.warnings.any { it is DeckWarning.ColorSourceShortage || it is DeckWarning.UnfixedSplash },
        )
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Group 8: tribal precision (@Ignore — Phase 2 B1)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `Elf deck does not rate an off-tribe Dragon as on-strategy`() {
        val deck = GoldenDecks.elfTribalCommander()
        val profile = profileOf(deck)
        val dragon = goldenCard(
            id = "off-dragon", name = "Random Dragon", typeLine = "Creature — Dragon",
            cmc = 5.0, colors = listOf("G"), colorIdentity = listOf("G"), power = "5", toughness = "5",
            oracleText = "Flying. Other Dragons you control get +1/+1.",
            tags = listOf(tribeTag("dragon"), CardTag.TRIBAL), edhrecRank = 1300,
        )
        val fit = scorer.fit(dragon, profile, isOwned = false)
        assertTrue(
            "An off-tribe Dragon should NOT match the Elf strategy (synergy ${fit.components.synergy})",
            fit.components.synergy < 0.3f,
        )
    }

    @Test
    fun `coherent Elf deck does not trigger LowSynergyDensity`() {
        val eval = evaluate(GoldenDecks.elfTribalCommander())
        assertFalse(
            "A coherent Elf tribal deck should not be flagged LowSynergyDensity",
            eval.warnings.any { it is DeckWarning.LowSynergyDensity },
        )
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Group 9: damage-based removal recall (@Ignore — Phase 1 A1)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `untagged damage-based removal classifies as SPOT_REMOVAL`() {
        val bolt = goldenCard(
            id = "raw-bolt", name = "Raw Bolt", typeLine = "Instant",
            cmc = 1.0, colors = listOf("R"), colorIdentity = listOf("R"),
            oracleText = "Lightning Bolt deals 3 damage to any target.",
            tags = emptyList(), // no REMOVAL tag → exercises the oracle fallback only
        )
        val roles = RoleClassifier().classify(bolt)
        assertTrue("Damage removal must classify as SPOT_REMOVAL", roles.contains(DeckRole.SPOT_REMOVAL))
    }
}
