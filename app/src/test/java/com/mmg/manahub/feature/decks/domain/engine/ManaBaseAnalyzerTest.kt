package com.mmg.manahub.feature.decks.domain.engine

import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.DeckFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Phase 5 (plan C3) — unit tests for [ManaBaseAnalyzer].
 *
 * The analyzer is pure, so it is tested directly (no MockK / dispatcher). Covered:
 *  · pip/devotion counting from `manaCost` (incl. hybrid),
 *  · land source counting (basic subtypes, oracle "add {X}", rainbow "any color"),
 *  · the Karsten threshold lookup,
 *  · a 4-colour-no-fixing deck flags ColorSourceShortage / UnfixedSplash,
 *  · a mono-colour deck flags NONE,
 *  · the dynamic land ideal SHIFTS down when 8 cheap ramp pieces are present.
 */
class ManaBaseAnalyzerTest {

    private lateinit var analyzer: ManaBaseAnalyzer
    private lateinit var scorer: DeckScorer

    @Before
    fun setUp() {
        analyzer = ManaBaseAnalyzer()
        scorer = DeckScorer(RoleClassifier(), NeutralPowerResolver, analyzer)
    }

    private fun profileOf(deck: GoldenDeck): DeckProfile =
        scorer.profile(deck.mainboard, deck.format, deck.colorIdentity, deck.seedTags)

    // ── Pip distribution ──────────────────────────────────────────────────────────

    @Test
    fun `pip distribution counts colored symbols quantity-weighted`() {
        val nonLand = listOf(
            entry(card(id = "ww", manaCost = "{2}{W}{W}", colors = listOf("W")), quantity = 2),
            entry(card(id = "u", manaCost = "{1}{U}", colors = listOf("U"))),
        )
        val pips = analyzer.pipDistribution(nonLand)
        assertEquals(4, pips[ManaColor.W]) // 2 white pips × 2 copies
        assertEquals(1, pips[ManaColor.U])
        assertEquals(null, pips[ManaColor.B])
    }

    @Test
    fun `generic and X symbols contribute no colored pip`() {
        val nonLand = listOf(entry(card(id = "x", manaCost = "{X}{X}{2}", colors = emptyList())))
        assertTrue(analyzer.pipDistribution(nonLand).isEmpty())
    }

    @Test
    fun `hybrid symbols count toward both halves`() {
        val nonLand = listOf(entry(card(id = "hy", manaCost = "{W/U}", colors = listOf("W", "U"))))
        val pips = analyzer.pipDistribution(nonLand)
        assertEquals(1, pips[ManaColor.W])
        assertEquals(1, pips[ManaColor.U])
    }

    // ── Land source counting ────────────────────────────────────────────────────────

    @Test
    fun `basic land produces its subtype color`() {
        val forest = card(id = "f", typeLine = "Basic Land — Forest", colorIdentity = listOf("G"), cmc = 0.0)
        assertEquals(setOf(ManaColor.G), analyzer.producedColors(forest, deckIdentity = setOf(ManaColor.G)))
    }

    @Test
    fun `oracle add clause is parsed into produced colors`() {
        val dual = card(
            id = "dual", typeLine = "Land", colorIdentity = listOf("U", "B"), cmc = 0.0,
            oracleText = "{T}: Add {U} or {B}.",
        )
        assertEquals(setOf(ManaColor.U, ManaColor.B), analyzer.producedColors(dual, setOf(ManaColor.U, ManaColor.B)))
    }

    @Test
    fun `any-color land produces every color in the deck identity`() {
        val tower = card(
            id = "tower", typeLine = "Land", colorIdentity = emptyList(), cmc = 0.0,
            oracleText = "{T}: Add one mana of any color.",
        )
        val identity = setOf(ManaColor.U, ManaColor.B)
        assertEquals(identity, analyzer.producedColors(tower, identity))
    }

    @Test
    fun `source counting is quantity-weighted across the land base`() {
        val lands = listOf(
            entry(card(id = "p", typeLine = "Basic Land — Plains", colorIdentity = listOf("W"), cmc = 0.0), quantity = 10),
            entry(card(id = "i", typeLine = "Basic Land — Island", colorIdentity = listOf("U"), cmc = 0.0), quantity = 7),
        )
        val sources = analyzer.landSources(lands, deckIdentity = setOf(ManaColor.W, ManaColor.U))
        assertEquals(10, sources[ManaColor.W])
        assertEquals(7, sources[ManaColor.U])
    }

    // ── Karsten threshold lookup ──────────────────────────────────────────────────────

    @Test
    fun `karsten thresholds scale with single-card pip intensity and deck size`() {
        // The argument is now a SINGLE CARD's pip count (per-card intensity), not the
        // deck-wide sum: 1 pip → single (14), 2 pips → double (20), 3+ pips → triple+ (23).
        // 60-card row (few lands).
        assertEquals(14, analyzer.requiredSources(singleCardPips = 1, totalLands = 24))   // single
        assertEquals(20, analyzer.requiredSources(singleCardPips = 2, totalLands = 24))   // double
        assertEquals(23, analyzer.requiredSources(singleCardPips = 3, totalLands = 24))   // triple+
        assertEquals(23, analyzer.requiredSources(singleCardPips = 5, totalLands = 24))   // ≥3 ⇒ triple+
        // 99-card row (≥33 lands): strictly higher requirements.
        assertEquals(19, analyzer.requiredSources(singleCardPips = 1, totalLands = 37))
        assertEquals(28, analyzer.requiredSources(singleCardPips = 2, totalLands = 37))
        assertEquals(32, analyzer.requiredSources(singleCardPips = 3, totalLands = 37))
    }

    // ── End-to-end fixing checks ──────────────────────────────────────────────────────

    @Test
    fun `four-color no-fixing deck flags shortage and unfixed splash`() {
        val deck = GoldenDecks.fourColorNoFixing()
        val report = analyzer.analyze(deck.mainboard, profileOf(deck))
        // White is well-supported (37 Plains) → no white warning.
        assertFalse(
            "White must not be flagged (37 Plains)",
            report.shortages.any {
                (it as? DeckWarning.UnfixedSplash)?.color == ManaColor.W ||
                    (it as? DeckWarning.ColorSourceShortage)?.color == ManaColor.W
            },
        )
        // U/B/R each demanded with 0 producing sources → unfixed splash.
        listOf(ManaColor.U, ManaColor.B, ManaColor.R).forEach { color ->
            assertTrue(
                "$color must be flagged as an unfixed splash (shortages=${report.shortages})",
                report.shortages.any { (it as? DeckWarning.UnfixedSplash)?.color == color },
            )
        }
    }

    @Test
    fun `four-color no-fixing deck surfaces a mana-base warning through evaluate`() {
        val deck = GoldenDecks.fourColorNoFixing()
        val profile = profileOf(deck)
        val eval = scorer.evaluate(profile, deck.nonLand, fullMainboard = deck.mainboard)
        assertTrue(
            "evaluate() must surface UnfixedSplash for the no-fixing deck",
            eval.warnings.any { it is DeckWarning.UnfixedSplash },
        )
    }

    @Test
    fun `healthy two-color constructed deck flags no shortage or unfixed splash`() {
        // Regression guard for C1: a realistic 60-card WU deck (16 W + 16 U sources,
        // single-pip costs) must NOT be flagged. Under the old deck-wide-pip-SUM model
        // every 2+ colour deck demanded the triple tier (23 sources) and was falsely
        // flagged; the per-card-intensity model correctly requires only 14 here.
        val deck = GoldenDecks.healthyTwoColorConstructed()
        val report = analyzer.analyze(deck.mainboard, profileOf(deck))
        assertTrue(
            "A healthy WU deck (16 sources/colour, single-pip) must have no fixing " +
                "shortage (shortages=${report.shortages})",
            report.shortages.none {
                it is DeckWarning.ColorSourceShortage || it is DeckWarning.UnfixedSplash
            },
        )
    }

    @Test
    fun `clean mono-color deck flags no mana-base shortage`() {
        val deck = GoldenDecks.cleanMonoColor()
        val report = analyzer.analyze(deck.mainboard, profileOf(deck))
        assertTrue(
            "A mono-green deck with 37 Forests must have no fixing shortage (shortages=${report.shortages})",
            report.shortages.isEmpty(),
        )
    }

    @Test
    fun `clean mono-color deck has no mana-base warning in evaluate`() {
        val deck = GoldenDecks.cleanMonoColor()
        val profile = profileOf(deck)
        val eval = scorer.evaluate(profile, deck.nonLand, fullMainboard = deck.mainboard)
        assertFalse(
            "Mono-green deck must not emit ColorSourceShortage / UnfixedSplash",
            eval.warnings.any { it is DeckWarning.ColorSourceShortage || it is DeckWarning.UnfixedSplash },
        )
    }

    // ── Dynamic land ideal ────────────────────────────────────────────────────────────

    @Test
    fun `dynamic land ideal shifts down when 8 cheap ramp pieces are present`() {
        // Two Commander decks identical except for 8 cheap (cmc 1) ramp rocks.
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
        val landPack = listOf(
            entry(card(id = "forest", typeLine = "Basic Land — Forest", colorIdentity = listOf("G"), cmc = 0.0), quantity = 37),
        )
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

        val noRamp = baseSpells + landPack
        val withRamp = baseSpells + cheapRamp + landPack

        val idealNoRamp = analyzer.dynamicLandIdeal(
            scorer.profile(noRamp, DeckFormat.COMMANDER, setOf(ManaColor.G), emptyList()),
        )
        val idealWithRamp = analyzer.dynamicLandIdeal(
            scorer.profile(withRamp, DeckFormat.COMMANDER, setOf(ManaColor.G), emptyList()),
        )

        assertTrue(
            "8 cheap ramp pieces must lower the land ideal ($idealWithRamp) vs no ramp ($idealNoRamp)",
            idealWithRamp < idealNoRamp,
        )
        // Stays bounded to the Commander land band (min 35).
        assertTrue("Land ideal must not drop below the format floor", idealWithRamp >= 35)
    }
}
