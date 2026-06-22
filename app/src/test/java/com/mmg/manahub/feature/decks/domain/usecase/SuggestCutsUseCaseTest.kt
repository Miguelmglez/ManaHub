package com.mmg.manahub.feature.decks.domain.usecase

import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.feature.decks.domain.engine.DeckScorer
import com.mmg.manahub.feature.decks.domain.engine.ManaColor
import com.mmg.manahub.feature.decks.domain.engine.RoleClassifier
import com.mmg.manahub.feature.decks.domain.engine.card
import com.mmg.manahub.feature.decks.domain.engine.entry
import com.mmg.manahub.feature.decks.domain.engine.fixedPower
import com.mmg.manahub.feature.decks.domain.engine.landCard
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [SuggestCutsUseCase].
 *
 * Exercised against a REAL [DeckScorer] (real [RoleClassifier] + deterministic fixed power) so the
 * tests also verify the protection / land / combo-core exclusions the engine performs.
 */
class SuggestCutsUseCaseTest {

    private val dispatcher = StandardTestDispatcher()
    private val scorer = DeckScorer(RoleClassifier(), fixedPower(normalized = 0.5f))
    private val useCase = SuggestCutsUseCase(scorer, dispatcher)

    private fun profileFor(mainboard: List<com.mmg.manahub.feature.decks.domain.engine.DeckEntry>) =
        scorer.profile(
            mainboard = mainboard,
            format = DeckFormat.COMMANDER,
            colorIdentity = setOf(ManaColor.U, ManaColor.G),
            seedTags = emptyList(),
        )

    @Test
    fun `the commander is never suggested for a cut`() = runTest(dispatcher) {
        val commander = card(id = "cmd", typeLine = "Legendary Creature — Elf", power = "3")
        val mainboard = listOf(
            entry(commander),
            entry(card(id = "spell-1")),
            entry(card(id = "spell-2")),
        )
        val profile = profileFor(mainboard)

        val cuts = useCase(mainboard, profile, protectedIds = setOf("cmd"))

        assertFalse("commander must be protected", cuts.any { it.card.scryfallId == "cmd" })
    }

    @Test
    fun `lands are excluded from cut candidates`() = runTest(dispatcher) {
        val mainboard = listOf(
            entry(card(id = "spell-1")),
            entry(landCard(id = "land-1")),
            entry(landCard(id = "land-2")),
        )
        val profile = profileFor(mainboard)

        val cuts = useCase(mainboard, profile)

        assertTrue("lands must never be cut candidates", cuts.none { it.card.scryfallId.startsWith("land") })
    }

    @Test
    fun `combo cores are protected from cuts`() = runTest(dispatcher) {
        val mainboard = listOf(
            entry(card(id = "infinite", tags = listOf(CardTag.INFINITE))),
            entry(card(id = "combo", tags = listOf(CardTag.COMBO))),
            entry(card(id = "vanilla")),
        )
        val profile = profileFor(mainboard)

        val cuts = useCase(mainboard, profile)

        assertFalse(cuts.any { it.card.scryfallId == "infinite" })
        assertFalse(cuts.any { it.card.scryfallId == "combo" })
        assertTrue(cuts.any { it.card.scryfallId == "vanilla" })
    }

    @Test
    fun `cuts are sorted ascending by fit so the worst fit is first`() = runTest(dispatcher) {
        val mainboard = listOf(
            entry(card(id = "a")),
            entry(card(id = "b")),
            entry(card(id = "c")),
        )
        val profile = profileFor(mainboard)

        val cuts = useCase(mainboard, profile)

        assertEquals(cuts.map { it.score }, cuts.map { it.score }.sorted())
    }
}
