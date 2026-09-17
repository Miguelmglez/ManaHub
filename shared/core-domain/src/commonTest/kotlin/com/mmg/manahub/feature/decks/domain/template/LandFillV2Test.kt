package com.mmg.manahub.feature.decks.domain.template
// COMMENTS_REVIEWED: 2026-09-09

import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.domain.usecase.decks.BasicLandCalculator
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.feature.decks.domain.engine.DeckScorer
import com.mmg.manahub.feature.decks.domain.engine.Finding
import com.mmg.manahub.feature.decks.domain.engine.ManaColor
import com.mmg.manahub.feature.decks.domain.engine.NeutralPowerResolver
import com.mmg.manahub.feature.decks.domain.engine.RoleClassifier
import com.mmg.manahub.feature.decks.domain.engine.StrategyPick
import com.mmg.manahub.feature.decks.domain.engine.card
import com.mmg.manahub.core.gamification.domain.ProgressionEventBus
import com.mmg.manahub.feature.decks.domain.usecase.DeckAnalysisPipeline
import com.mmg.manahub.feature.decks.domain.usecase.EvaluateDeckUseCase
import com.mmg.manahub.feature.decks.domain.usecase.InferDeckIdentityUseCase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private object LandFillTestCrashReporter : CrashReporter {
    override fun recordException(throwable: Throwable) = Unit
    override fun log(message: String) = Unit
    override fun setCustomKey(key: String, value: String) = Unit
}

/**
 * Deck Wizard Commander v3 plan, Phase 2 gate (2.4 land fill v2) -- the mono/2/3/5-colour test
 * coverage Run 3 explicitly scoped out (see the progress tracker's Run 3 note 3/8). Every build in
 * this file uses an owned pool containing ONLY the commander + candidate lands (no other owned
 * nonland candidates), so [com.mmg.manahub.feature.decks.domain.engine.ManaBaseAnalyzer]'s pip
 * intensity comes exclusively from the commander's own cost -- this isolates land-fill behaviour
 * from [PlacementScorer]'s placement loop, which is already covered by
 * [BuildWizardDeckUseCaseTest]/`PlacementScorerTest`.
 */
class LandFillV2Test {

    private fun newUseCase(): BuildWizardDeckUseCase {
        val pipeline = DeckAnalysisPipeline(
            EvaluateDeckUseCase(DeckScorer(RoleClassifier(), NeutralPowerResolver), ProgressionEventBus()),
            InferDeckIdentityUseCase(),
            LandFillTestCrashReporter,
        )
        return BuildWizardDeckUseCase(pipeline, LandFillTestCrashReporter)
    }

    private fun basic(name: String, symbol: String, id: String): OwnedCard =
        OwnedCard(
            card(id = id, name = name, typeLine = "Basic Land — $name", cmc = 0.0, colors = emptyList(), colorIdentity = listOf(symbol), producedMana = symbol),
            quantity = 40,
        )

    private fun dual(id: String, name: String, symbols: List<String>): OwnedCard =
        OwnedCard(
            card(id = id, name = name, typeLine = "Land", cmc = 0.0, colors = emptyList(), colorIdentity = symbols, oracleText = "{T}: Add ${symbols.joinToString(" or ") { "{$it}" }}."),
            quantity = 1,
        )

    /** A "rainbow" land producing every colour in the deck's identity -- real Commander staples
     * like Command Tower/Mana Confluence work this way; [ManaBaseAnalyzer.producedColors] resolves
     * an "any color" oracle clause against the deck's own identity, not the land's own [colorIdentity]. */
    private fun rainbow(id: String, name: String, identitySymbols: List<String>): OwnedCard =
        OwnedCard(
            card(id = id, name = name, typeLine = "Land", cmc = 0.0, colors = emptyList(), colorIdentity = identitySymbols, oracleText = "{T}: Add one mana of any color."),
            quantity = 1,
        )

    private fun shortages(entries: List<Finding>): List<Finding> = entries.filter { it is Finding.ColorSourceShortage || it is Finding.UnfixedSplash }

    // ── Mono-colour: basics alone must clear Karsten (D10, LAND_MIX bucket 1 == basics-only) ────
    @Test
    fun `mono-colour identity -- zero shortage findings when basics can satisfy Karsten need`() = runTest {
        val useCase = newUseCase()
        val commander = card(id = "cmd-mono", name = "Mono Commander", typeLine = "Legendary Creature", cmc = 2.0, colors = listOf("W"), colorIdentity = listOf("W"), manaCost = "{1}{W}")
        val owned = listOf(OwnedCard(commander, 1), basic("Plains", "W", "basic-plains-mono"))

        val outcome = useCase(DeckFormat.COMMANDER, commander, StrategyPick.Custom, setOf(ManaColor.W), owned)

        val findings = shortages(outcome.result.analysis.pillars.flatMap { it.findings })
        assertTrue(findings.isEmpty(), "mono-colour deck with unlimited owned Plains must clear Karsten: $findings")
    }

    // ── W5.2 (G10/R8/E10): default is OFF -- Stage A never fires unless explicitly requested ──────
    @Test
    fun `includeNonBasicLands defaults to OFF -- owned duals are never drawn without the caller opting in`() = runTest {
        val useCase = newUseCase()
        val commander = card(id = "cmd-default-off", name = "Default Off Commander", typeLine = "Legendary Creature", cmc = 2.0, colors = listOf("W", "U"), colorIdentity = listOf("W", "U"), manaCost = "{1}{W}{U}")
        val owned = listOf(
            OwnedCard(commander, 1),
            basic("Plains", "W", "basic-plains-off"),
            basic("Island", "U", "basic-island-off"),
            dual("dual-wu-off-1", "Hallowed Fountain", listOf("W", "U")),
            dual("dual-wu-off-2", "Tundra", listOf("W", "U")),
        )

        // No includeNonBasicLands arg passed -- exercises the DEFAULT, not an explicit false.
        val outcome = useCase(DeckFormat.COMMANDER, commander, StrategyPick.Custom, setOf(ManaColor.W, ManaColor.U), owned)

        val landEntries = outcome.result.entries.filter { BasicLandCalculator.isLand(it.card) }
        assertTrue(landEntries.isNotEmpty(), "sanity: some lands must be placed")
        assertTrue(
            landEntries.all { BasicLandCalculator.isBasicLand(it.card) },
            "the default (OFF) must place ONLY basics -- owned duals must never be drawn without opting in, got ${landEntries.map { it.card.name }}",
        )
    }

    // ── W5.2 parity: the wizard's OFF-mode basic split is BasicLandCalculator.calculateFromPips's
    //    own output for the same mainboard -- literally the same function, same inputs (E10's "one
    //    land calculation, shared"). Mono-colour so Stage C's Karsten rebalance has no second colour
    //    to move a copy from/to and can never perturb Stage B's raw distribution -- this isolates
    //    the parity claim from Stage C's own (correct, but separate) refinement. ────────────────────
    @Test
    fun `W5_2 -- OFF mode basic distribution equals BasicLandCalculator's independent calculateFromPips for the same mainboard`() = runTest {
        val useCase = newUseCase()
        val commander = card(id = "cmd-parity-mono", name = "Parity Mono Commander", typeLine = "Legendary Creature", cmc = 2.0, colors = listOf("W"), colorIdentity = listOf("W"), manaCost = "{1}{W}{W}")
        val owned = listOf(OwnedCard(commander, 1), basic("Plains", "W", "basic-plains-parity"))

        val outcome = useCase(DeckFormat.COMMANDER, commander, StrategyPick.Custom, setOf(ManaColor.W), owned, includeNonBasicLands = false)

        val landEntries = outcome.result.entries.filter { BasicLandCalculator.isLand(it.card) }
        assertTrue(
            landEntries.all { BasicLandCalculator.isBasicLand(it.card) },
            "OFF mode must place ONLY basics, got ${landEntries.map { it.card.name }}",
        )

        // Independent recompute: the SAME function, same inputs the wizard's own Stage B used
        // (commander-only pips, no non-basic lands, the ACTUAL land count the wizard placed).
        val manaBaseAnalyzer = com.mmg.manahub.feature.decks.domain.engine.ManaBaseAnalyzer()
        val pipsByColor = manaBaseAnalyzer.pipDistribution(
            listOf(com.mmg.manahub.feature.decks.domain.engine.DeckEntry(commander, 1, isOwned = true)),
        ).entries.associate { (color, count) -> color.symbol to count }
        val totalLandTarget = landEntries.sumOf { it.quantity }
        val expected = BasicLandCalculator.calculateFromPips(
            pipsByColor = pipsByColor,
            nonBasicLands = emptyList(),
            totalLandTarget = totalLandTarget,
            commanderIdentity = setOf("W"),
        )

        val actualPlains = landEntries.filter { it.card.name == "Plains" }.sumOf { it.quantity }
        assertEquals(expected.plains, actualPlains, "the wizard's OFF-mode Plains count must equal BasicLandCalculator.calculateFromPips' own independent output for the same mainboard")
    }

    // ── W5.2 ON mode: Stage A draws owned non-basics WITHOUT starving basics ───────────────────────
    @Test
    fun `includeNonBasicLands ON draws owned duals while basics still form the backbone`() = runTest {
        val useCase = newUseCase()
        val commander = card(id = "cmd-on-backbone", name = "On Backbone Commander", typeLine = "Legendary Creature", cmc = 2.0, colors = listOf("W", "U"), colorIdentity = listOf("W", "U"), manaCost = "{1}{W}{U}")
        val owned = listOf(
            OwnedCard(commander, 1),
            basic("Plains", "W", "basic-plains-backbone"),
            basic("Island", "U", "basic-island-backbone"),
            dual("dual-wu-backbone-1", "Hallowed Fountain", listOf("W", "U")),
            dual("dual-wu-backbone-2", "Tundra", listOf("W", "U")),
        )

        val outcome = useCase(DeckFormat.COMMANDER, commander, StrategyPick.Custom, setOf(ManaColor.W, ManaColor.U), owned, includeNonBasicLands = true)

        val landEntries = outcome.result.entries.filter { BasicLandCalculator.isLand(it.card) }
        val basicCount = landEntries.filter { BasicLandCalculator.isBasicLand(it.card) }.sumOf { it.quantity }
        val nonBasicCount = landEntries.filterNot { BasicLandCalculator.isBasicLand(it.card) }.sumOf { it.quantity }
        assertTrue(nonBasicCount > 0, "ON mode must actually draw the owned duals (Stage A must fire)")
        assertTrue(basicCount > nonBasicCount, "basics must still form the backbone, not be starved by non-basics -- basics=$basicCount nonBasics=$nonBasicCount")
    }

    // ── 2-colour: a couple of owned dual lands on top of basics ─────────────────────────────────
    @Test
    fun `two-colour identity -- zero shortage findings when owned duals plus basics can satisfy Karsten need`() = runTest {
        val useCase = newUseCase()
        val commander = card(id = "cmd-2c", name = "Two Colour Commander", typeLine = "Legendary Creature", cmc = 2.0, colors = listOf("W", "U"), colorIdentity = listOf("W", "U"), manaCost = "{1}{W}{U}")
        val owned = listOf(
            OwnedCard(commander, 1),
            basic("Plains", "W", "basic-plains-2c"),
            basic("Island", "U", "basic-island-2c"),
            dual("dual-wu-1", "Hallowed Fountain", listOf("W", "U")),
            dual("dual-wu-2", "Tundra", listOf("W", "U")),
            dual("dual-wu-3", "Adarkar Wastes", listOf("W", "U")),
        )

        val outcome = useCase(DeckFormat.COMMANDER, commander, StrategyPick.Custom, setOf(ManaColor.W, ManaColor.U), owned, includeNonBasicLands = true)

        val findings = shortages(outcome.result.analysis.pillars.flatMap { it.findings })
        assertTrue(findings.isEmpty(), "2-colour deck with owned WU duals + basics must clear Karsten: $findings")
    }

    // ── 3-colour: duals covering every pair, plus basics ────────────────────────────────────────
    @Test
    fun `three-colour identity -- zero shortage findings when owned duals plus basics can satisfy Karsten need`() = runTest {
        val useCase = newUseCase()
        val commander = card(id = "cmd-3c", name = "Three Colour Commander", typeLine = "Legendary Creature", cmc = 3.0, colors = listOf("W", "U", "B"), colorIdentity = listOf("W", "U", "B"), manaCost = "{1}{W}{U}{B}")
        // A real 3-colour EDH manabase leans much harder on "any color" staples than on 2-colour
        // duals -- 3 duals (1 per pair) only credit 2 colours each, while a rainbow land credits
        // all 3; Karsten's 19-source-per-colour bar at a 38-land total is NOT reachable from just
        // 3 rainbows + 3 duals + basics (2R + 3D >= 19 per pair-count D=1 needs R >= 8) -- 10
        // rainbow lands (3 real staples + 7 more of the same "any color" shape, following the
        // five-colour test's own "Rainbow Land N" naming below) clears it with margin.
        val owned = listOf(
            OwnedCard(commander, 1),
            basic("Plains", "W", "basic-plains-3c"),
            basic("Island", "U", "basic-island-3c"),
            basic("Swamp", "B", "basic-swamp-3c"),
            dual("dual-wu-3c", "Hallowed Fountain", listOf("W", "U")),
            dual("dual-ub-3c", "Watery Grave", listOf("U", "B")),
            dual("dual-wb-3c", "Godless Shrine", listOf("W", "B")),
            rainbow("rainbow-3c-1", "Command Tower", listOf("W", "U", "B")),
            rainbow("rainbow-3c-2", "Mana Confluence", listOf("W", "U", "B")),
            rainbow("rainbow-3c-3", "City of Brass", listOf("W", "U", "B")),
        ) + (4..10).map { i -> rainbow("rainbow-3c-$i", "Rainbow Land $i", listOf("W", "U", "B")) }

        val outcome = useCase(DeckFormat.COMMANDER, commander, StrategyPick.Custom, setOf(ManaColor.W, ManaColor.U, ManaColor.B), owned, includeNonBasicLands = true)

        val findings = shortages(outcome.result.analysis.pillars.flatMap { it.findings })
        assertTrue(findings.isEmpty(), "3-colour deck with owned duals/rainbow lands + basics must clear Karsten: $findings")
    }

    // ── 5-colour: real 5-colour Commander decks lean on "any color" staples, not 2-colour duals
    //    (only 10 unique pairs exist for 5 colours under the singleton rule) ────────────────────
    @Test
    fun `five-colour identity -- zero shortage findings when owned rainbow lands plus basics can satisfy Karsten need`() = runTest {
        val useCase = newUseCase()
        val symbols = listOf("W", "U", "B", "R", "G")
        val commander = card(id = "cmd-5c", name = "Five Colour Commander", typeLine = "Legendary Creature", cmc = 5.0, colors = symbols, colorIdentity = symbols, manaCost = "{W}{U}{B}{R}{G}")
        val rainbowLands = (1..20).map { i -> rainbow("rainbow-5c-$i", "Rainbow Land $i", symbols) }
        val owned = listOf(OwnedCard(commander, 1)) +
            symbols.mapIndexed { i, s -> basic(BasicLandCalculator.LAND_FOR_COLOR.getValue(s), s, "basic-5c-$i") } +
            rainbowLands

        val outcome = useCase(DeckFormat.COMMANDER, commander, StrategyPick.Custom, symbols.map { ManaColor.entries.first { c -> c.symbol == it } }.toSet(), owned, includeNonBasicLands = true)

        val findings = shortages(outcome.result.analysis.pillars.flatMap { it.findings })
        assertTrue(findings.isEmpty(), "5-colour deck with 20 owned rainbow lands + basics must clear Karsten: $findings")
    }

    // ── The negative case: a shortage the owned pool genuinely cannot cover must be DECLARED ────
    @Test
    fun `five-colour identity with only two owned land colours -- shortage is declared, not hidden`() = runTest {
        val useCase = newUseCase()
        val symbols = listOf("W", "U", "B", "R", "G")
        val commander = card(id = "cmd-5c-thin", name = "Five Colour Thin Commander", typeLine = "Legendary Creature", cmc = 5.0, colors = symbols, colorIdentity = symbols, manaCost = "{W}{U}{B}{R}{G}")
        // Only W/U basics owned -- no B/R/G land of any kind in the pool.
        val owned = listOf(
            OwnedCard(commander, 1),
            basic("Plains", "W", "basic-thin-w"),
            basic("Island", "U", "basic-thin-u"),
        )

        val outcome = useCase(DeckFormat.COMMANDER, commander, StrategyPick.Custom, symbols.map { s -> ManaColor.entries.first { it.symbol == s } }.toSet(), owned)

        val findings = outcome.result.analysis.pillars.flatMap { it.findings }
        val missingColorFindings = findings.filterIsInstance<Finding.UnfixedSplash>().map { it.color } +
            findings.filterIsInstance<Finding.ColorSourceShortage>().map { it.color }
        assertTrue(ManaColor.B in missingColorFindings, "an owned pool with zero Black sources must declare the Black shortage, never hide it: $findings")
        assertTrue(ManaColor.R in missingColorFindings, "an owned pool with zero Red sources must declare the Red shortage, never hide it: $findings")
        assertTrue(ManaColor.G in missingColorFindings, "an owned pool with zero Green sources must declare the Green shortage, never hide it: $findings")
    }

    // ── The commander's own pips must shift the basic split (F9/D10) ────────────────────────────
    @Test
    fun `commander's own pips shift the basic land split when the rest of the deck is held fixed`() = runTest {
        val useCase = newUseCase()
        val identity = setOf(ManaColor.W, ManaColor.U)
        val owned = { commanderCost: String, id: String ->
            val commander = card(id = id, name = "Pip Commander $id", typeLine = "Legendary Creature", cmc = 2.0, colors = listOf("W", "U"), colorIdentity = listOf("W", "U"), manaCost = commanderCost)
            commander to listOf(OwnedCard(commander, 1), basic("Plains", "W", "basic-plains-pip-$id"), basic("Island", "U", "basic-island-pip-$id"))
        }
        val (whiteHeavy, whitePool) = owned("{1}{W}{W}", "cmd-white-heavy")
        val (blueHeavy, bluePool) = owned("{1}{U}{U}", "cmd-blue-heavy")

        val whiteOutcome = useCase(DeckFormat.COMMANDER, whiteHeavy, StrategyPick.Custom, identity, whitePool)
        val blueOutcome = useCase(DeckFormat.COMMANDER, blueHeavy, StrategyPick.Custom, identity, bluePool)

        val whitePlains = whiteOutcome.result.entries.filter { it.card.name == "Plains" }.sumOf { it.quantity }
        val whiteIslands = whiteOutcome.result.entries.filter { it.card.name == "Island" }.sumOf { it.quantity }
        val bluePlains = blueOutcome.result.entries.filter { it.card.name == "Plains" }.sumOf { it.quantity }
        val blueIslands = blueOutcome.result.entries.filter { it.card.name == "Island" }.sumOf { it.quantity }

        assertTrue(whitePlains > whiteIslands, "a {W}{W} commander with nothing else in the deck must skew basics toward Plains, got $whitePlains Plains / $whiteIslands Islands")
        assertTrue(blueIslands > bluePlains, "a {U}{U} commander with nothing else in the deck must skew basics toward Island, got $bluePlains Plains / $blueIslands Islands")
    }

    // ── Phyrexian pips must NOT inflate a colour's basic-land share (F9/D10) ─────────────────────
    @Test
    fun `Phyrexian pips are excluded from the basic land split`() = runTest {
        val useCase = newUseCase()
        val identity = setOf(ManaColor.W, ManaColor.U)
        // {W/P} x3 would look like heavy White demand under a naive raw-character count (the OLD
        // F9 bug this test guards against), but ManaBaseAnalyzer.pipDistribution treats Phyrexian
        // pips as ZERO coloured demand -- with no OTHER pip anywhere in the deck, total demand is
        // genuinely zero and BasicLandCalculator.calculateFromPips falls back to an EVEN split
        // across the two identity colours.
        val commander = card(id = "cmd-phyrexian", name = "Phyrexian Commander", typeLine = "Legendary Creature", cmc = 2.0, colors = listOf("W", "U"), colorIdentity = listOf("W", "U"), manaCost = "{1}{W/P}{W/P}{W/P}")
        val owned = listOf(OwnedCard(commander, 1), basic("Plains", "W", "basic-plains-phy"), basic("Island", "U", "basic-island-phy"))

        val outcome = useCase(DeckFormat.COMMANDER, commander, StrategyPick.Custom, identity, owned)

        val plains = outcome.result.entries.filter { it.card.name == "Plains" }.sumOf { it.quantity }
        val islands = outcome.result.entries.filter { it.card.name == "Island" }.sumOf { it.quantity }
        assertEquals(0, kotlin.math.abs(plains - islands).let { if (it <= 1) 0 else it }, "Phyrexian-only pips must not skew the basic split beyond a rounding remainder, got $plains Plains / $islands Islands")
    }

    // ── F17: a truly colourless identity must fill with Wastes, never zero basics ────────────────
    @Test
    fun `colourless identity fills every basic slot with Wastes`() = runTest {
        val useCase = newUseCase()
        val commander = card(id = "cmd-colourless", name = "Page, Loose Leaf", typeLine = "Legendary Creature", cmc = 2.0, colors = emptyList(), colorIdentity = emptyList(), manaCost = "{2}")
        val owned = listOf(
            OwnedCard(commander, 1),
            OwnedCard(card(id = "basic-wastes", name = "Wastes", typeLine = "Basic Land", cmc = 0.0, colors = emptyList(), colorIdentity = emptyList(), producedMana = "C"), quantity = 40),
        )

        val outcome = useCase(DeckFormat.COMMANDER, commander, StrategyPick.Custom, emptySet(), owned)

        // Only the commander + Wastes are owned in this fixture (no nonland candidates), so the
        // build is deliberately thin -- DeckTooSmall is expected here and orthogonal to F17. The
        // fix under test is that basic-land fill no longer allocates ZERO for a colourless identity.
        val wastes = outcome.result.entries.filter { it.card.name == "Wastes" }.sumOf { it.quantity }
        assertTrue(wastes > 0, "a colourless commander with owned Wastes must fill basics with Wastes, got $wastes")
    }
}
