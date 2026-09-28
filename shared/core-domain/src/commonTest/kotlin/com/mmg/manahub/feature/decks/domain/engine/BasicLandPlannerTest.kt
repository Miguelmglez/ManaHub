package com.mmg.manahub.feature.decks.domain.engine

import com.mmg.manahub.core.domain.usecase.decks.BasicLandCalculator
import com.mmg.manahub.core.model.DeckCard
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Deck Wizard UX polish plan, Run 1 §1.1 — [BasicLandPlanner] is the extracted Stage B (pip-weighted
 * distribution) + Stage C (bounded Karsten rebalance) counts logic, shared by
 * [com.mmg.manahub.feature.decks.domain.template.BuildWizardDeckUseCase]'s `fillLandsV2` and Studio's
 * land-delta suggestion. These tests exercise the object directly (no wizard/Studio scaffolding);
 * [com.mmg.manahub.feature.decks.domain.template.LandFillV2Test] covers the same Stage B/C behaviour
 * end-to-end through the wizard use case.
 */
class BasicLandPlannerTest {

    private val analyzer = ManaBaseAnalyzer()

    @Test
    fun `pip-weighted distribution matches BasicLandCalculator's own calculateFromPips for the same inputs`() {
        val identity = setOf(ManaColor.W, ManaColor.U)
        val nonLandMainboard = listOf(
            entry(card(id = "w1", manaCost = "{W}", colors = listOf("W")), quantity = 2),
            entry(card(id = "u1", manaCost = "{U}", colors = listOf("U")), quantity = 1),
        )

        val basicCounts = BasicLandPlanner.planBasics(
            identity = identity,
            landTarget = 9,
            nonLandMainboard = nonLandMainboard,
            nonBasicLands = emptyList(),
            manaBaseAnalyzer = analyzer,
        )

        // 2:1 weighted split of 9 slots -- both colours are well below their Karsten requirement
        // (a 9-land total never clears a tier-0 14-source bar) so Stage C has no excess colour to
        // pull from and leaves Stage B's raw split untouched.
        assertEquals(6, basicCounts[ManaColor.W])
        assertEquals(3, basicCounts[ManaColor.U])

        val expected = BasicLandCalculator.calculateFromPips(
            pipsByColor = mapOf("W" to 2, "U" to 1),
            nonBasicLands = emptyList(),
            totalLandTarget = 9,
            commanderIdentity = setOf("W", "U"),
        )
        assertEquals(expected.plains, basicCounts[ManaColor.W])
        assertEquals(expected.islands, basicCounts[ManaColor.U])
    }

    @Test
    fun `empty identity with zero pip demand fills every slot with Wastes`() {
        val nonLandMainboard = listOf(
            entry(card(id = "generic", manaCost = "{2}", colors = emptyList()), quantity = 4),
        )

        val basicCounts = BasicLandPlanner.planBasics(
            identity = emptySet(),
            landTarget = 10,
            nonLandMainboard = nonLandMainboard,
            nonBasicLands = emptyList(),
            manaBaseAnalyzer = analyzer,
        )

        assertEquals(10, basicCounts[ManaColor.C])
        ManaColor.entries.filter { it != ManaColor.C }.forEach { color ->
            assertEquals(0, basicCounts[color], "a colourless identity must not assign any WUBRG basic")
        }
    }

    @Test
    fun `Karsten rebalance stops at the move cap even while a shortage persists`() {
        val identity = setOf(ManaColor.W, ManaColor.U)
        // W: huge total pip WEIGHT (100 copies) but a low single-card intensity (tier 0, needs only
        // 19 sources) -- so Stage B hands it almost every basic slot, yet it stays a shallow excess
        // reservoir. U: a single triple-pip card (tier 2, needs 32 sources) -- deeply short off
        // Stage B's 1-slot share. The gap (excess ~20 vs. shortage ~31) needs far more than
        // [BasicLandPlanner.KARSTEN_REBALANCE_CAP] single-copy moves to fully resolve.
        val nonLandMainboard = listOf(
            entry(card(id = "cheap-w", manaCost = "{W}", colors = listOf("W")), quantity = 100),
            entry(card(id = "heavy-u", manaCost = "{U}{U}{U}", colors = listOf("U")), quantity = 1),
        )

        val basicCounts = BasicLandPlanner.planBasics(
            identity = identity,
            landTarget = 40,
            nonLandMainboard = nonLandMainboard,
            nonBasicLands = emptyList(),
            manaBaseAnalyzer = analyzer,
        )

        // Stage B alone would have placed W=39/U=1 (see the parity test above for the same math);
        // exactly KARSTEN_REBALANCE_CAP single-copy moves from W to U, no more.
        assertEquals(39 - BasicLandPlanner.KARSTEN_REBALANCE_CAP, basicCounts[ManaColor.W])
        assertEquals(1 + BasicLandPlanner.KARSTEN_REBALANCE_CAP, basicCounts[ManaColor.U])

        val uRequired = analyzer.requiredSources(singleCardPips = 3, totalLands = 40)
        assertTrue(
            (basicCounts[ManaColor.U] ?: 0) < uRequired,
            "the cap must stop the rebalance before Blue's shortage is actually resolved, otherwise this test can't tell the cap apart from a natural stop",
        )
    }

    @Test
    fun `non-basic land sources are counted toward the Karsten rebalance`() {
        val identity = setOf(ManaColor.U, ManaColor.B)
        // Equal pip demand for both colours (tier 2 each) -- Stage B alone splits the 10 basic
        // slots 5/5. A 20-copy non-basic Blue source pushes Blue's TOTAL sources (20 + 5) past its
        // Karsten requirement, freeing it as Stage C's excess colour; without counting the
        // non-basic land, Blue and Black would read equally short and no move could ever fire.
        val nonLandMainboard = listOf(
            entry(card(id = "triple-u", manaCost = "{U}{U}{U}", colors = listOf("U")), quantity = 1),
            entry(card(id = "triple-b", manaCost = "{B}{B}{B}", colors = listOf("B")), quantity = 1),
        )
        val nonBasicLands = listOf(
            DeckCard(
                card(id = "big-blue-land", typeLine = "Land", colorIdentity = listOf("U"), oracleText = "{T}: Add {U}."),
                quantity = 20,
            ),
        )

        val basicCounts = BasicLandPlanner.planBasics(
            identity = identity,
            landTarget = 30,
            nonLandMainboard = nonLandMainboard,
            nonBasicLands = nonBasicLands,
            manaBaseAnalyzer = analyzer,
        )

        assertEquals(3, basicCounts[ManaColor.U], "2 units moved off Stage B's raw 5/5 split once the 20 non-basic Blue sources are counted")
        assertEquals(7, basicCounts[ManaColor.B])
    }
}
