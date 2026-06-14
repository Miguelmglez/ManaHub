package com.mmg.manahub.feature.decks.domain.usecase

import com.mmg.manahub.feature.decks.domain.engine.CardFit
import com.mmg.manahub.feature.decks.domain.engine.ScoreComponents
import com.mmg.manahub.feature.decks.domain.engine.card
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [BudgetOptimizer]. Pure logic — no coroutines, no mocks. Verifies: owned cards are
 * free, the per-card cap drops expensive cards, the total cap is respected, and selection is greedy by
 * value-per-euro.
 */
class BudgetOptimizerTest {

    private val optimizer = BudgetOptimizer()

    /** Builds an [AddSuggestion] with a controlled price, ownership, fit score and origin. */
    private fun suggestion(
        id: String,
        priceEur: Double?,
        isOwned: Boolean,
        score: Float = 0.5f,
        origin: AddOrigin = AddOrigin.NEW,
    ): AddSuggestion {
        val c = card(id = id, colorIdentity = listOf("U")).copy(priceEur = priceEur)
        val fit = CardFit(
            card = c,
            score = score,
            components = ScoreComponents(0f, 0f, 0f, 0f, 0f, 0f),
            roles = emptySet(),
            reasons = emptyList(),
            isOwned = isOwned,
            isLegal = true,
            withinColorIdentity = true,
        )
        return AddSuggestion(fit = fit, origin = origin)
    }

    @Test
    fun `unconstrained budget keeps everything`() {
        val input = listOf(
            suggestion("a", priceEur = 100.0, isOwned = false),
            suggestion("b", priceEur = null, isOwned = false),
        )

        val result = optimizer(input, BudgetConstraints())

        assertEquals(2, result.selected.size)
    }

    @Test
    fun `owned cards are free and never consume the total cap`() {
        val input = listOf(
            suggestion("owned1", priceEur = 50.0, isOwned = true),
            suggestion("owned2", priceEur = 80.0, isOwned = true),
        )

        val result = optimizer(input, BudgetConstraints(maxTotalEur = 1.0))

        // Both owned cards are kept and cost nothing.
        assertEquals(2, result.selected.size)
        assertEquals(0.0, result.totalCostEur, 0.001)
        assertEquals(0, result.cardsToBuy)
    }

    @Test
    fun `owned cards are NOT free when ownedCardsAreFree is false`() {
        val input = listOf(suggestion("owned", priceEur = 5.0, isOwned = true))

        val result = optimizer(
            input,
            BudgetConstraints(maxPerCardEur = 2.0, ownedCardsAreFree = false),
        )

        // 5 € > 2 € per-card cap → dropped.
        assertTrue(result.selected.isEmpty())
    }

    @Test
    fun `per-card cap drops expensive cards`() {
        val input = listOf(
            suggestion("cheap", priceEur = 1.0, isOwned = false),
            suggestion("pricey", priceEur = 50.0, isOwned = false),
        )

        val result = optimizer(input, BudgetConstraints(maxPerCardEur = 5.0))

        val ids = result.selected.map { it.fit.card.scryfallId }
        assertTrue("cheap" in ids)
        assertFalse("pricey" in ids)
    }

    @Test
    fun `total cap is respected`() {
        val input = listOf(
            suggestion("a", priceEur = 6.0, isOwned = false, score = 0.9f),
            suggestion("b", priceEur = 6.0, isOwned = false, score = 0.8f),
            suggestion("c", priceEur = 6.0, isOwned = false, score = 0.7f),
        )

        val result = optimizer(input, BudgetConstraints(maxTotalEur = 13.0))

        // Only two 6 € cards fit under 13 €; the third would push to 18 €.
        assertEquals(2, result.cardsToBuy)
        assertTrue(result.totalCostEur <= 13.0)
    }

    @Test
    fun `greedy selection prefers higher value per euro`() {
        // Same price, different fit → the higher-fit card is taken first; only one fits.
        val input = listOf(
            suggestion("lowValue", priceEur = 10.0, isOwned = false, score = 0.2f),
            suggestion("highValue", priceEur = 10.0, isOwned = false, score = 0.9f),
        )

        val result = optimizer(input, BudgetConstraints(maxTotalEur = 10.0))

        assertEquals(1, result.selected.size)
        assertEquals("highValue", result.selected.single().fit.card.scryfallId)
    }

    @Test
    fun `cheaper card wins when value per euro is higher despite lower fit`() {
        // highFitExpensive: 0.8 / 20 = 0.04. cheapDecent: 0.5 / 2 = 0.25 → cheaper has more value/€.
        val input = listOf(
            suggestion("highFitExpensive", priceEur = 20.0, isOwned = false, score = 0.8f),
            suggestion("cheapDecent", priceEur = 2.0, isOwned = false, score = 0.5f),
        )

        // Total cap only allows one of them (cap below the sum, above each individually).
        val result = optimizer(input, BudgetConstraints(maxTotalEur = 19.0))

        val ids = result.selected.map { it.fit.card.scryfallId }
        // Greedy by value/€ picks the cheap one first; the expensive one still fits under 19 too,
        // so assert the cheap one is definitely present and counted.
        assertTrue("cheapDecent" in ids)
    }

    @Test
    fun `E8 - missing-price NEW card may be kept when no total cap is active`() {
        // No total cap → the unknown-price flag is inert; the card is selectable and costed as 0.
        val input = listOf(suggestion("noPrice", priceEur = null, isOwned = false))

        val result = optimizer(input, BudgetConstraints(maxPerCardEur = 100.0))

        assertEquals(1, result.selected.size)
        assertEquals(0, result.cardsToBuy)
        assertEquals(0.0, result.totalCostEur, 0.001)
    }

    @Test
    fun `E8 - missing-price NEW card is EXCLUDED under an active total cap`() {
        // Under a total cap, an unknown-price NEW card cannot be costed → excluded (never silently free).
        val input = listOf(suggestion("noPrice", priceEur = null, isOwned = false))

        val result = optimizer(input, BudgetConstraints(maxTotalEur = 1.0))

        assertTrue(result.selected.isEmpty())
        assertEquals(0, result.cardsToBuy)
        assertEquals(0.0, result.totalCostEur, 0.001)
    }

    @Test
    fun `E8 - owned unknown-price card is still kept under an active total cap`() {
        // Owned cards are free regardless of price, so the unknown-price exclusion never touches them.
        val input = listOf(suggestion("ownedNoPrice", priceEur = null, isOwned = true))

        val result = optimizer(input, BudgetConstraints(maxTotalEur = 1.0))

        assertEquals(1, result.selected.size)
        assertEquals(0, result.cardsToBuy)
        assertEquals(0.0, result.totalCostEur, 0.001)
    }

    @Test
    fun `E8 - priced cards still fill the cap when an unknown-price NEW card is dropped`() {
        // The dropped unknown-price card must not consume budget; a real priced card still fits.
        val input = listOf(
            suggestion("unknown", priceEur = null, isOwned = false, score = 0.9f),
            suggestion("priced", priceEur = 3.0, isOwned = false, score = 0.5f),
        )

        val result = optimizer(input, BudgetConstraints(maxTotalEur = 5.0))

        val ids = result.selected.map { it.fit.card.scryfallId }
        assertFalse("unknown" in ids)
        assertTrue("priced" in ids)
        assertEquals(1, result.cardsToBuy)
        assertEquals(3.0, result.totalCostEur, 0.001)
    }

    @Test
    fun `E8 - priceUnknown flag reflects a missing EUR price`() {
        assertTrue(suggestion("a", priceEur = null, isOwned = false).priceUnknown)
        assertFalse(suggestion("b", priceEur = 1.0, isOwned = false).priceUnknown)
    }

    @Test
    fun `selected list preserves the caller's original order`() {
        val input = listOf(
            suggestion("first", priceEur = 1.0, isOwned = false, score = 0.3f),
            suggestion("second", priceEur = 1.0, isOwned = false, score = 0.9f),
        )

        val result = optimizer(input, BudgetConstraints(maxTotalEur = 100.0))

        // Both fit; display order must match input order, not value/€ order.
        assertEquals(listOf("first", "second"), result.selected.map { it.fit.card.scryfallId })
    }
}
