package com.mmg.manahub.feature.decks.domain.usecase

import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.feature.decks.domain.engine.CardFit
import com.mmg.manahub.feature.decks.domain.engine.DeckEvaluation
import com.mmg.manahub.feature.decks.domain.engine.DeckScorer
import com.mmg.manahub.feature.decks.domain.engine.ManaColor
import com.mmg.manahub.feature.decks.domain.engine.RoleCoverage
import com.mmg.manahub.feature.decks.domain.engine.ScoreComponents
import com.mmg.manahub.feature.decks.domain.engine.card
import com.mmg.manahub.feature.decks.domain.engine.minimalProfile
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [SuggestAddsWithBudgetUseCase] (Phase 6: multi-source pipeline).
 *
 * [DeckScorer] is mocked so that every candidate that passes the HARD filter is always scored with
 * a fixed [CardFit]; the tests focus on origin priority, deduplication, wishlist resolution,
 * external-pool fallback, and the [limit] parameter — NOT on the scorer's own logic.
 */
class SuggestAddsWithBudgetUseCaseTest {

    // ── Collaborator mocks ────────────────────────────────────────────────────

    private val deckScorer = mockk<DeckScorer>()
    private val candidatePoolGenerator = mockk<CandidatePoolGenerator>()
    private val budgetOptimizer = BudgetOptimizer() // real — we only want its pass-through behavior
    private val cardRepository = mockk<CardRepository>()
    private val dispatcher = UnconfinedTestDispatcher()

    private val useCase = SuggestAddsWithBudgetUseCase(
        deckScorer = deckScorer,
        candidatePoolGenerator = candidatePoolGenerator,
        budgetOptimizer = budgetOptimizer,
        cardRepository = cardRepository,
        ioDispatcher = dispatcher,
    )

    // ── Shared fixtures ───────────────────────────────────────────────────────

    private val profile = minimalProfile(
        format = DeckFormat.COMMANDER,
        colorIdentity = setOf(ManaColor.U),
        seedTags = emptyList(),
    )

    private val evaluation = DeckEvaluation(
        roleCoverage = emptyList<RoleCoverage>(),
        avgCmc = 2.5,
        curveHistogram = emptyMap(),
        landCount = 36,
        synergyDensity = 0.5f,
        healthScore = 70,
        warnings = emptyList(),
    )

    private val unconstrained = BudgetConstraints() // no caps → optimizer is a pass-through

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Stubs [DeckScorer.rankAdds] to return a [CardFit] with score 0.5 for each candidate in the
     * same order they arrive, filtering nothing. This isolates the use case's origin-priority logic
     * from the real scorer.
     */
    private fun stubRankAdds() {
        every {
            deckScorer.rankAdds(
                candidates = any(),
                profile = any(),
                ownedIds = any(),
                weights = any(),
                limit = any(),
            )
        } answers {
            val candidates = firstArg<List<com.mmg.manahub.core.model.Card>>()
            val ownedIds = thirdArg<Set<String>>()
            val limit = arg<Int>(4)
            candidates.take(limit).map { c ->
                CardFit(
                    card = c,
                    score = 0.5f,
                    components = ScoreComponents(0f, 0f, 0f, 0.5f, 0f, 0f),
                    roles = emptySet(),
                    reasons = emptyList(),
                    isOwned = c.scryfallId in ownedIds,
                    isLegal = true,
                    withinColorIdentity = true,
                )
            }
        }
    }

    @Before
    fun setUp() {
        stubRankAdds()
    }

    // ── Group 1: Origin priority — COLLECTION beats WISHLIST beats NEW ─────────

    @Test
    fun `given card in collection wishlist and external pool when invoked then origin is COLLECTION`() =
        runTest(dispatcher) {
            val sharedId = "shared-card"
            val sharedCard = card(id = sharedId, colorIdentity = listOf("U"))

            // The card is in the collection, on the wishlist, and returned by the external pool.
            coEvery { cardRepository.getCardById(sharedId) } returns DataResult.Success(sharedCard)
            coEvery { candidatePoolGenerator(any(), any(), any(), any()) } returns listOf(sharedCard)

            val result = useCase(
                collection = listOf(sharedCard),
                wishlistIds = setOf(sharedId),
                mainboardIds = emptySet(),
                profile = profile,
                evaluation = evaluation,
                constraints = unconstrained,
            )

            val suggestion = result.selected.single { it.fit.card.scryfallId == sharedId }
            assertEquals(AddOrigin.COLLECTION, suggestion.origin)
        }

    // ── Group 2: Origin priority — WISHLIST beats NEW ─────────────────────────

    @Test
    fun `given card on wishlist and in external pool but not in collection when invoked then origin is WISHLIST`() =
        runTest(dispatcher) {
            val wishlistId = "wishlist-card"
            val wishlistCard = card(id = wishlistId, colorIdentity = listOf("U"))

            coEvery { cardRepository.getCardById(wishlistId) } returns DataResult.Success(wishlistCard)
            coEvery { candidatePoolGenerator(any(), any(), any(), any()) } returns listOf(wishlistCard)

            val result = useCase(
                collection = emptyList(),
                wishlistIds = setOf(wishlistId),
                mainboardIds = emptySet(),
                profile = profile,
                evaluation = evaluation,
                constraints = unconstrained,
            )

            val suggestion = result.selected.single { it.fit.card.scryfallId == wishlistId }
            assertEquals(AddOrigin.WISHLIST, suggestion.origin)
        }

    @Test
    fun `given card only in external pool when invoked then origin is NEW`() =
        runTest(dispatcher) {
            val externalCard = card(id = "ext-card", colorIdentity = listOf("U"))

            coEvery { candidatePoolGenerator(any(), any(), any(), any()) } returns listOf(externalCard)

            val result = useCase(
                collection = emptyList(),
                wishlistIds = emptySet(),
                mainboardIds = emptySet(),
                profile = profile,
                evaluation = evaluation,
                constraints = unconstrained,
            )

            val suggestion = result.selected.single { it.fit.card.scryfallId == "ext-card" }
            assertEquals(AddOrigin.NEW, suggestion.origin)
        }

    // ── Group 3: Wishlist card that fails to resolve is silently dropped ───────

    @Test
    fun `given wishlist card that fails to resolve when invoked then card is absent from result`() =
        runTest(dispatcher) {
            val missingId = "missing-wishlist-card"
            val validCard = card(id = "valid-card", colorIdentity = listOf("U"))

            // Wishlist id returns an error from the repository.
            coEvery { cardRepository.getCardById(missingId) } returns DataResult.Error("not found")
            coEvery { candidatePoolGenerator(any(), any(), any(), any()) } returns emptyList()

            // No exception should propagate out of the use case.
            val result = useCase(
                collection = listOf(validCard),
                wishlistIds = setOf(missingId),
                mainboardIds = emptySet(),
                profile = profile,
                evaluation = evaluation,
                constraints = unconstrained,
            )

            assertFalse(
                "failed wishlist resolution must not appear in results",
                result.selected.any { it.fit.card.scryfallId == missingId },
            )
            // The valid collection card is still present.
            assertTrue(result.selected.any { it.fit.card.scryfallId == "valid-card" })
        }

    @Test
    fun `given multiple wishlist cards when one fails to resolve then remaining cards are included`() =
        runTest(dispatcher) {
            val badId = "bad-wishlist"
            val goodId = "good-wishlist"
            val goodCard = card(id = goodId, colorIdentity = listOf("U"))

            coEvery { cardRepository.getCardById(badId) } returns DataResult.Error("timeout")
            coEvery { cardRepository.getCardById(goodId) } returns DataResult.Success(goodCard)
            coEvery { candidatePoolGenerator(any(), any(), any(), any()) } returns emptyList()

            val result = useCase(
                collection = emptyList(),
                wishlistIds = setOf(badId, goodId),
                mainboardIds = emptySet(),
                profile = profile,
                evaluation = evaluation,
                constraints = unconstrained,
            )

            assertFalse(result.selected.any { it.fit.card.scryfallId == badId })
            assertTrue(result.selected.any { it.fit.card.scryfallId == goodId })
        }

    // ── Group 4: External pool failure falls back to collection + wishlist only ─

    @Test
    fun `given CandidatePoolGenerator throws when invoked then result contains collection cards and no crash`() =
        runTest(dispatcher) {
            val ownedCard = card(id = "owned-a", colorIdentity = listOf("U"))

            // Simulate Scryfall being unreachable.
            coEvery { candidatePoolGenerator(any(), any(), any(), any()) } throws RuntimeException("offline")

            val result = useCase(
                collection = listOf(ownedCard),
                wishlistIds = emptySet(),
                mainboardIds = emptySet(),
                profile = profile,
                evaluation = evaluation,
                constraints = unconstrained,
            )

            // Collection card is still present; no crash.
            assertTrue(result.selected.any { it.fit.card.scryfallId == "owned-a" })
            // Every surviving suggestion must be COLLECTION-origin (no external cards made it in).
            assertTrue(result.selected.all { it.origin == AddOrigin.COLLECTION })
        }

    @Test
    fun `given pool generator throws and wishlist card resolves when invoked then wishlist card is present`() =
        runTest(dispatcher) {
            val wishlistCard = card(id = "wl-offline", colorIdentity = listOf("U"))

            coEvery { candidatePoolGenerator(any(), any(), any(), any()) } throws RuntimeException("dns failure")
            coEvery { cardRepository.getCardById("wl-offline") } returns DataResult.Success(wishlistCard)

            val result = useCase(
                collection = emptyList(),
                wishlistIds = setOf("wl-offline"),
                mainboardIds = emptySet(),
                profile = profile,
                evaluation = evaluation,
                constraints = unconstrained,
            )

            val suggestion = result.selected.single { it.fit.card.scryfallId == "wl-offline" }
            assertEquals(AddOrigin.WISHLIST, suggestion.origin)
        }

    // ── Group 5: limit parameter caps the pre-budget ranked suggestions ────────

    @Test
    fun `given 20 collection cards and limit 3 when invoked then selected size does not exceed 3`() =
        runTest(dispatcher) {
            val collection = (1..20).map { card(id = "col-$it", colorIdentity = listOf("U")) }
            coEvery { candidatePoolGenerator(any(), any(), any(), any()) } returns emptyList()

            val result = useCase(
                collection = collection,
                wishlistIds = emptySet(),
                mainboardIds = emptySet(),
                profile = profile,
                evaluation = evaluation,
                constraints = unconstrained,
                limit = 3,
            )

            assertTrue(
                "expected at most 3 suggestions but got ${result.selected.size}",
                result.selected.size <= 3,
            )
        }

    @Test
    fun `given limit 0 when invoked then selected is empty`() = runTest(dispatcher) {
        val collection = listOf(card(id = "any", colorIdentity = listOf("U")))
        coEvery { candidatePoolGenerator(any(), any(), any(), any()) } returns emptyList()

        val result = useCase(
            collection = collection,
            wishlistIds = emptySet(),
            mainboardIds = emptySet(),
            profile = profile,
            evaluation = evaluation,
            constraints = unconstrained,
            limit = 0,
        )

        assertTrue(result.selected.isEmpty())
    }

    // ── Group 6: mainboardIds exclusion ───────────────────────────────────────

    @Test
    fun `given card already in mainboard when invoked then card is excluded from suggestions`() =
        runTest(dispatcher) {
            val inDeck = card(id = "in-deck", colorIdentity = listOf("U"))
            val notInDeck = card(id = "not-in-deck", colorIdentity = listOf("U"))

            coEvery { candidatePoolGenerator(any(), any(), any(), any()) } returns emptyList()

            val result = useCase(
                collection = listOf(inDeck, notInDeck),
                wishlistIds = emptySet(),
                mainboardIds = setOf("in-deck"),
                profile = profile,
                evaluation = evaluation,
                constraints = unconstrained,
            )

            assertFalse(result.selected.any { it.fit.card.scryfallId == "in-deck" })
            assertTrue(result.selected.any { it.fit.card.scryfallId == "not-in-deck" })
        }

    // ── Group 7: Budget summary is populated ──────────────────────────────────

    @Test
    fun `given unconstrained budget when all suggestions are owned then total cost is zero`() =
        runTest(dispatcher) {
            val ownedCards = (1..3).map { card(id = "own-$it", colorIdentity = listOf("U")) }
            coEvery { candidatePoolGenerator(any(), any(), any(), any()) } returns emptyList()

            val result = useCase(
                collection = ownedCards,
                wishlistIds = emptySet(),
                mainboardIds = emptySet(),
                profile = profile,
                evaluation = evaluation,
                constraints = unconstrained,
            )

            assertEquals(0.0, result.totalCostEur, 0.001)
            assertEquals(0, result.cardsToBuy)
        }
}
