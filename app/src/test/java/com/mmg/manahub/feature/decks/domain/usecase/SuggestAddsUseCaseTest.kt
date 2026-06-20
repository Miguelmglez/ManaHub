package com.mmg.manahub.feature.decks.domain.usecase

import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.feature.decks.domain.engine.DeckScorer
import com.mmg.manahub.feature.decks.domain.engine.ManaColor
import com.mmg.manahub.feature.decks.domain.engine.RoleClassifier
import com.mmg.manahub.feature.decks.domain.engine.card
import com.mmg.manahub.feature.decks.domain.engine.fixedPower
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [SuggestAddsUseCase] (Phase 5: collection-only candidate pool).
 *
 * Exercised against a REAL [DeckScorer] so the tests also verify the HARD color/legality filter
 * and that every Phase-5 suggestion is owned and tagged [AddOrigin.COLLECTION].
 */
class SuggestAddsUseCaseTest {

    private val dispatcher = StandardTestDispatcher()
    private val scorer = DeckScorer(RoleClassifier(), fixedPower(normalized = 0.6f))
    private val useCase = SuggestAddsUseCase(scorer, dispatcher)

    // Mono-blue profile so the color filter is meaningful.
    private val profile = scorer.profile(
        mainboard = emptyList(),
        format = DeckFormat.COMMANDER,
        colorIdentity = setOf(ManaColor.U),
        seedTags = emptyList(),
    )

    @Test
    fun `cards already in the deck are excluded from the pool`() = runTest(dispatcher) {
        val collection = listOf(
            card(id = "inDeck", colorIdentity = listOf("U")),
            card(id = "owned", colorIdentity = listOf("U")),
        )

        val adds = useCase(collection, mainboardIds = setOf("inDeck"), profile = profile)

        assertFalse(adds.any { it.fit.card.scryfallId == "inDeck" })
        assertTrue(adds.any { it.fit.card.scryfallId == "owned" })
    }

    @Test
    fun `out-of-color cards never surface as add candidates`() = runTest(dispatcher) {
        val collection = listOf(
            card(id = "blue", colorIdentity = listOf("U")),
            card(id = "red", colorIdentity = listOf("R")), // off-color for a mono-U profile
        )

        val adds = useCase(collection, mainboardIds = emptySet(), profile = profile)

        assertTrue(adds.any { it.fit.card.scryfallId == "blue" })
        assertFalse(adds.any { it.fit.card.scryfallId == "red" })
    }

    @Test
    fun `every phase-5 suggestion is owned and tagged COLLECTION`() = runTest(dispatcher) {
        val collection = listOf(
            card(id = "a", colorIdentity = listOf("U")),
            card(id = "b", colorIdentity = listOf("U")),
        )

        val adds = useCase(collection, mainboardIds = emptySet(), profile = profile)

        assertTrue(adds.isNotEmpty())
        assertTrue(adds.all { it.origin == AddOrigin.COLLECTION })
        assertTrue(adds.all { it.fit.isOwned })
    }

    @Test
    fun `duplicate printings in the collection are scored only once`() = runTest(dispatcher) {
        val collection = listOf(
            card(id = "dup", colorIdentity = listOf("U")),
            card(id = "dup", colorIdentity = listOf("U")), // same scryfallId held twice
        )

        val adds = useCase(collection, mainboardIds = emptySet(), profile = profile)

        assertTrue(adds.count { it.fit.card.scryfallId == "dup" } == 1)
    }
}
