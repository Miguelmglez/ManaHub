package com.mmg.manahub.feature.decks.domain.engine

import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.DeckFormat
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Multiplatform (commonTest) coverage for [ManaBaseAnalyzer] — KMP migration remediation P1.4.
 *
 * Ported subset of the JVM-only `app/src/test/.../ManaBaseAnalyzerTest.kt` (the BEHAVIOR SPEC).
 * [ManaBaseAnalyzer] is a pure, stateless engine (no MockK, no dispatcher, no GoldenDeck harness
 * needed), which makes it the ideal first candidate to prove the shared `feature/decks/domain/engine`
 * package behaves identically on the JVM host AND wasmJs — the two target runtimes for this KMP
 * migration. The GoldenDeck-fixture-dependent end-to-end `evaluate()` tests stay JVM-only for now
 * (GoldenDecks.kt is a large harness not yet ported to commonTest); this suite covers the pure
 * pip/land/Karsten/dynamic-land-ideal building blocks directly.
 */
class ManaBaseAnalyzerTest {

    private lateinit var analyzer: ManaBaseAnalyzer
    private lateinit var scorer: DeckScorer

    @BeforeTest
    fun setUp() {
        analyzer = ManaBaseAnalyzer()
        scorer = DeckScorer(RoleClassifier(), NeutralPowerResolver, analyzer)
    }

    // ── Pip distribution ──────────────────────────────────────────────────────────

    @Test
    fun pipDistributionCountsColoredSymbolsQuantityWeighted() {
        val nonLand = listOf(
            entry(card(id = "ww", manaCost = "{2}{W}{W}", colors = listOf("W")), quantity = 2),
            entry(card(id = "u", manaCost = "{1}{U}", colors = listOf("U"))),
        )
        val pips = analyzer.pipDistribution(nonLand)
        assertEquals(4, pips[ManaColor.W]) // 2 white pips x 2 copies
        assertEquals(1, pips[ManaColor.U])
        assertEquals(null, pips[ManaColor.B])
    }

    @Test
    fun genericAndXSymbolsContributeNoColoredPip() {
        val nonLand = listOf(entry(card(id = "x", manaCost = "{X}{X}{2}", colors = emptyList())))
        assertTrue(analyzer.pipDistribution(nonLand).isEmpty())
    }

    @Test
    fun hybridSymbolsCountTowardBothHalves() {
        val nonLand = listOf(entry(card(id = "hy", manaCost = "{W/U}", colors = listOf("W", "U"))))
        val pips = analyzer.pipDistribution(nonLand)
        assertEquals(1, pips[ManaColor.W])
        assertEquals(1, pips[ManaColor.U])
    }

    @Test
    fun phyrexianSymbolContributesNoColoredPip() {
        val nonLand = listOf(entry(card(id = "phy", manaCost = "{W/P}", colors = listOf("W"))))
        assertTrue(analyzer.pipDistribution(nonLand).isEmpty())
    }

    // ── Land source counting ────────────────────────────────────────────────────────

    @Test
    fun basicLandProducesItsSubtypeColor() {
        val forest = card(id = "f", typeLine = "Basic Land — Forest", colorIdentity = listOf("G"), cmc = 0.0)
        assertEquals(setOf(ManaColor.G), analyzer.producedColors(forest, deckIdentity = setOf(ManaColor.G)))
    }

    @Test
    fun oracleAddClauseIsParsedIntoProducedColors() {
        val dual = card(
            id = "dual", typeLine = "Land", colorIdentity = listOf("U", "B"), cmc = 0.0,
            oracleText = "{T}: Add {U} or {B}.",
        )
        assertEquals(setOf(ManaColor.U, ManaColor.B), analyzer.producedColors(dual, setOf(ManaColor.U, ManaColor.B)))
    }

    @Test
    fun anyColorLandProducesEveryColorInTheDeckIdentity() {
        val tower = card(
            id = "tower", typeLine = "Land", colorIdentity = emptyList(), cmc = 0.0,
            oracleText = "{T}: Add one mana of any color.",
        )
        val identity = setOf(ManaColor.U, ManaColor.B)
        assertEquals(identity, analyzer.producedColors(tower, identity))
    }

    @Test
    fun sourceCountingIsQuantityWeightedAcrossTheLandBase() {
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
    fun karstenThresholdsScaleWithSingleCardPipIntensityAndDeckSize() {
        // 60-card row (few lands): 1 pip -> single (14), 2 pips -> double (20), 3+ pips -> triple+ (23).
        assertEquals(14, analyzer.requiredSources(singleCardPips = 1, totalLands = 24))
        assertEquals(20, analyzer.requiredSources(singleCardPips = 2, totalLands = 24))
        assertEquals(23, analyzer.requiredSources(singleCardPips = 3, totalLands = 24))
        assertEquals(23, analyzer.requiredSources(singleCardPips = 5, totalLands = 24))
        // 99-card row (>= 33 lands): strictly higher requirements.
        assertEquals(19, analyzer.requiredSources(singleCardPips = 1, totalLands = 37))
        assertEquals(28, analyzer.requiredSources(singleCardPips = 2, totalLands = 37))
        assertEquals(32, analyzer.requiredSources(singleCardPips = 3, totalLands = 37))
    }

    // ── Dynamic land ideal ────────────────────────────────────────────────────────────

    @Test
    fun dynamicLandIdealShiftsDownWhenEightCheapRampPiecesArePresent() {
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
            idealWithRamp < idealNoRamp,
            "8 cheap ramp pieces must lower the land ideal ($idealWithRamp) vs no ramp ($idealNoRamp)",
        )
        // Stays bounded to the Commander land band (min 35).
        assertTrue(idealWithRamp >= 35, "Land ideal must not drop below the format floor")
    }
}
