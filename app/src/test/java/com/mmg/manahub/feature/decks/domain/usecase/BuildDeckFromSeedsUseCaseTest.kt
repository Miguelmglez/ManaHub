package com.mmg.manahub.feature.decks.domain.usecase

import com.mmg.manahub.core.domain.model.Card
import com.mmg.manahub.core.domain.model.CardTag
import com.mmg.manahub.core.domain.model.DeckFormat
import com.mmg.manahub.feature.decks.presentation.engine.DeckScorer
import com.mmg.manahub.feature.decks.presentation.engine.ManaColor
import com.mmg.manahub.feature.decks.presentation.engine.RoleClassifier
import com.mmg.manahub.feature.decks.presentation.engine.SeedStrategy
import com.mmg.manahub.feature.decks.presentation.engine.card
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [BuildDeckFromSeedsUseCase].
 *
 * The engine collaborators ([DeckScorer], [RoleClassifier], [BudgetOptimizer]) are REAL (pure logic),
 * so the test exercises the genuine ranking + skeleton-fill behavior. Only [CandidatePoolGenerator]
 * (the Scryfall-backed source) is mocked, so the network can be made to succeed, return nothing, or
 * fail. Tests assert: the skeleton fills toward role ideals, owned cards win over external ones at
 * equal usefulness, the budget filter drops over-priced externals, and a Scryfall failure falls back
 * to a collection + seeds build.
 */
class BuildDeckFromSeedsUseCaseTest {

    private val dispatcher = StandardTestDispatcher()
    private val roleClassifier = RoleClassifier()
    private val deckScorer = DeckScorer(roleClassifier) // NeutralPowerResolver default is fine for tests
    private val budgetOptimizer = BudgetOptimizer()
    private val candidatePool = mockk<CandidatePoolGenerator>()

    private val useCase = BuildDeckFromSeedsUseCase(
        deckScorer = deckScorer,
        roleClassifier = roleClassifier,
        candidatePoolGenerator = candidatePool,
        budgetOptimizer = budgetOptimizer,
        ioDispatcher = dispatcher,
    )

    // ── Fixtures ────────────────────────────────────────────────────────────────

    /** A blue removal card (classifies into SPOT_REMOVAL). */
    private fun removal(id: String, priceEur: Double? = null): Card =
        card(id = id, name = "Removal $id", tags = listOf(CardTag.REMOVAL), colorIdentity = listOf("U"))
            .copy(priceEur = priceEur)

    /** A blue card-draw card (classifies into CARD_ADVANTAGE). */
    private fun draw(id: String, priceEur: Double? = null): Card =
        card(id = id, name = "Draw $id", tags = listOf(CardTag.DRAW_ENGINE), colorIdentity = listOf("U"))
            .copy(priceEur = priceEur)

    private val seed = card(
        id = "seed-1",
        name = "Counterspell",
        tags = listOf(CardTag.COUNTERSPELL, CardTag.CONTROL),
        colorIdentity = listOf("U"),
    )

    private val identity = InferredIdentity(
        colorIdentity = setOf(ManaColor.U),
        strategy = SeedStrategy.CONTROL,
        seedTags = SeedStrategy.CONTROL.primaryTags,
    )

    private val noNetwork = BudgetConstraints() // unconstrained

    @Test
    fun `seeds are always in the mainboard first`() = runTest(dispatcher) {
        coEvery { candidatePool(any(), any(), any(), any()) } returns emptyList()

        val result = useCase(
            seeds = listOf(seed),
            identity = identity,
            format = DeckFormat.STANDARD,
            constraints = noNetwork,
            collection = listOf(removal("r1")),
        )

        assertEquals(seed.scryfallId, result.mainboard.first().card.scryfallId)
    }

    @Test
    fun `skeleton is filled toward role ideals from the collection`() = runTest(dispatcher) {
        coEvery { candidatePool(any(), any(), any(), any()) } returns emptyList()

        // STANDARD skeleton: SPOT_REMOVAL ideal = 8, CARD_ADVANTAGE ideal = 4.
        // Supply many removal + draw cards; the gap pass should pull a healthy number of each in.
        val collection = (1..12).map { removal("r$it") } + (1..8).map { draw("d$it") }

        val result = useCase(
            seeds = listOf(seed),
            identity = identity,
            format = DeckFormat.STANDARD,
            constraints = noNetwork,
            collection = collection,
        )

        val mainboardIds = result.mainboard.map { it.card.scryfallId }.toSet()
        val removalPicked = collection.count { it.scryfallId.startsWith("r") && it.scryfallId in mainboardIds }
        val drawPicked = collection.count { it.scryfallId.startsWith("d") && it.scryfallId in mainboardIds }

        // The gap pass should pick at least the ideals (8 removal, 4 draw) since enough are available.
        assertTrue("expected >= 8 removal picked, got $removalPicked", removalPicked >= 8)
        assertTrue("expected >= 4 draw picked, got $drawPicked", drawPicked >= 4)
    }

    @Test
    fun `non-land target is never exceeded`() = runTest(dispatcher) {
        coEvery { candidatePool(any(), any(), any(), any()) } returns emptyList()

        // Provide far more cards than the deck can hold.
        val collection = (1..200).map { removal("r$it") }

        val result = useCase(
            seeds = listOf(seed),
            identity = identity,
            format = DeckFormat.STANDARD,
            constraints = noNetwork,
            collection = collection,
        )

        // STANDARD: 60 - 24 lands = 36 non-land slots. Seeds are added on top of the picked fills,
        // so the mainboard size is bounded by (non-land target picks) + seeds.
        val nonLandTarget = DeckFormat.STANDARD.targetDeckSize - DeckFormat.STANDARD.targetLandCount
        assertTrue(
            "mainboard ${result.mainboard.size} should not exceed target+seeds",
            result.mainboard.size <= nonLandTarget + 1,
        )
    }

    @Test
    fun `reserved land slots come from the format skeleton`() = runTest(dispatcher) {
        coEvery { candidatePool(any(), any(), any(), any()) } returns emptyList()

        val result = useCase(
            seeds = listOf(seed),
            identity = identity,
            format = DeckFormat.STANDARD,
            constraints = noNetwork,
            collection = emptyList(),
        )

        // STANDARD skeleton LAND ideal = 24.
        assertEquals(24, result.reservedLandSlots)
        assertFalse(result.usedExternalCandidates)
    }

    @Test
    fun `owned cards are preferred over external cards at equal usefulness`() = runTest(dispatcher) {
        // One owned removal and one external removal, both identical fit. Owned must be selected.
        val owned = removal("owned-removal")
        val external = removal("external-removal")
        coEvery { candidatePool(any(), any(), any(), any()) } returns listOf(external)

        // Tight non-land room is not needed; we just check ordering: owned appears before external.
        val result = useCase(
            seeds = listOf(seed),
            identity = identity,
            format = DeckFormat.STANDARD,
            constraints = noNetwork,
            collection = listOf(owned),
        )

        val ids = result.mainboard.map { it.card.scryfallId }
        val ownedIdx = ids.indexOf("owned-removal")
        val externalIdx = ids.indexOf("external-removal")
        assertTrue("owned removal must be in the deck", ownedIdx >= 0)
        if (externalIdx >= 0) {
            assertTrue("owned must rank before external", ownedIdx < externalIdx)
        }
        // The MagicCard for the owned card must be flagged owned, the external one must not.
        assertTrue(result.mainboard.first { it.card.scryfallId == "owned-removal" }.isOwned)
        result.mainboard.firstOrNull { it.card.scryfallId == "external-removal" }
            ?.let { assertFalse(it.isOwned) }
    }

    @Test
    fun `budget per-card cap drops over-priced external cards`() = runTest(dispatcher) {
        // External removal priced over the per-card cap must be excluded; the cheap one must remain.
        val cheap = removal("ext-cheap", priceEur = 1.0)
        val pricey = removal("ext-pricey", priceEur = 100.0)
        coEvery { candidatePool(any(), any(), any(), any()) } returns listOf(cheap, pricey)

        val result = useCase(
            seeds = listOf(seed),
            identity = identity,
            format = DeckFormat.STANDARD,
            constraints = BudgetConstraints(maxPerCardEur = 10.0),
            collection = emptyList(),
        )

        val ids = result.mainboard.map { it.card.scryfallId }.toSet()
        assertTrue("cheap external should be kept", "ext-cheap" in ids)
        assertFalse("pricey external should be dropped by the per-card cap", "ext-pricey" in ids)
    }

    @Test
    fun `scryfall failure falls back to a collection plus seeds build`() = runTest(dispatcher) {
        // CandidatePoolGenerator throws (offline / Scryfall down). The build must still succeed.
        coEvery { candidatePool(any(), any(), any(), any()) } throws RuntimeException("offline")

        val collection = listOf(removal("r1"), draw("d1"))

        val result = useCase(
            seeds = listOf(seed),
            identity = identity,
            format = DeckFormat.STANDARD,
            constraints = noNetwork,
            collection = collection,
        )

        // No external cards were used, but the deck still contains the seed + collection picks.
        assertFalse(result.usedExternalCandidates)
        assertTrue(result.mainboard.any { it.card.scryfallId == "seed-1" })
        assertTrue(result.mainboard.any { it.card.scryfallId == "r1" })
    }

    @Test
    fun `empty seeds list produces a valid result with reserved land slots and no exception`() =
        runTest(dispatcher) {
            // When no seeds are provided, the profile is built from an empty mainboard.
            // The use case must not throw, and the land-slot reservation must still fire.
            coEvery { candidatePool(any(), any(), any(), any()) } returns emptyList()

            val emptyIdentity = InferredIdentity(
                colorIdentity = emptySet(),
                strategy = null,
                seedTags = emptyList(),
            )

            val result = useCase(
                seeds = emptyList(),
                identity = emptyIdentity,
                format = DeckFormat.STANDARD,
                constraints = noNetwork,
                collection = emptyList(),
            )

            // No seeds → nothing is force-added, so the mainboard comes solely from the picked
            // fills (also empty here because the collection is empty too).
            assertTrue(
                "mainboard should be empty when seeds=[] and collection=[]",
                result.mainboard.isEmpty(),
            )
            // The land-slot reservation must fall back to the format target (24 for STANDARD).
            assertTrue(
                "reservedLandSlots must be > 0 (skeleton ideal or format target)",
                result.reservedLandSlots > 0,
            )
            assertEquals(DeckFormat.STANDARD.targetLandCount, result.reservedLandSlots)
        }

    @Test
    fun `duplicate seed cards are deduplicated in the mainboard`() = runTest(dispatcher) {
        // Providing the same Card object twice must not result in the same scryfallId appearing
        // twice in the mainboard — seeds are added with a simple forEach which would normally
        // produce duplicates without an explicit guard.
        val duplicateSeed = card(
            id = "dup-seed",
            name = "Clone Seed",
            tags = listOf(com.mmg.manahub.core.domain.model.CardTag.COUNTERSPELL),
            colorIdentity = listOf("U"),
        )
        coEvery { candidatePool(any(), any(), any(), any()) } returns emptyList()

        val result = useCase(
            seeds = listOf(duplicateSeed, duplicateSeed),
            identity = identity,
            format = DeckFormat.STANDARD,
            constraints = noNetwork,
            collection = emptyList(),
        )

        val idsInMainboard = result.mainboard.map { it.card.scryfallId }
        assertEquals(
            "duplicate seed id must appear exactly once in the mainboard",
            1,
            idsInMainboard.count { it == "dup-seed" },
        )
    }
}
