package com.mmg.manahub.feature.decks.domain.usecase

import com.mmg.manahub.core.domain.model.Card
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.feature.decks.presentation.engine.DeckEvaluation
import com.mmg.manahub.feature.decks.presentation.engine.DeckRole
import com.mmg.manahub.feature.decks.presentation.engine.ManaColor
import com.mmg.manahub.feature.decks.presentation.engine.RoleCoverage
import com.mmg.manahub.feature.decks.presentation.engine.card
import com.mmg.manahub.feature.decks.presentation.engine.minimalProfile
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [CandidatePoolGenerator]. The Scryfall path ([CardRepository.searchWithRawQuery])
 * is mocked; the tests assert the QUERY STRINGS are built from the controlled allowlist (color
 * identity, legality, role oracle fragments, budget cap, exclude-basics), that exactly one query is
 * issued per gap role, and that results are merged + sorted by EDHREC rank (nulls last).
 */
class CandidatePoolGeneratorTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = mockk<CardRepository>()
    private val generator = CandidatePoolGenerator(repository, dispatcher)

    private fun evaluation(vararg gaps: Pair<DeckRole, Int>): DeckEvaluation {
        val coverage = gaps.map { (role, gap) ->
            // current = 0, ideal = gap → roleCoverage.gap == gap > 0.
            RoleCoverage(role = role, current = 0, ideal = gap)
        }
        return DeckEvaluation(
            roleCoverage = coverage,
            avgCmc = 2.5,
            curveHistogram = emptyMap(),
            landCount = 0,
            synergyDensity = 0.5f,
            healthScore = 50,
            warnings = emptyList(),
        )
    }

    @Test
    fun `issues exactly one query per gap role and includes role oracle fragments`() = runTest(dispatcher) {
        val queries = mutableListOf<String>()
        coEvery { repository.searchWithRawQuery(capture(queries)) } returns emptyList()

        val profile = minimalProfile(colorIdentity = setOf(ManaColor.U))
        generator(
            profile = profile,
            evaluation = evaluation(
                DeckRole.BOARD_WIPE to 2,
                DeckRole.CARD_ADVANTAGE to 3,
            ),
        )

        // Two gap roles → exactly two queries.
        coVerify(exactly = 2) { repository.searchWithRawQuery(any()) }
        assertEquals(2, queries.size)

        val boardWipe = queries.single { it.contains("destroy all") }
        assertTrue(boardWipe.contains("each player sacrifices"))
        val draw = queries.single { it.contains("draw a card") }
        assertTrue(draw.contains("o:\"draw a card\""))
    }

    @Test
    fun `query contains color identity, legality, budget cap and excludes basics`() = runTest(dispatcher) {
        val query = slot<String>()
        coEvery { repository.searchWithRawQuery(capture(query)) } returns emptyList()

        val profile = minimalProfile(colorIdentity = setOf(ManaColor.W, ManaColor.U))
        generator(
            profile = profile,
            evaluation = evaluation(DeckRole.SPOT_REMOVAL to 4),
            usdCap = 5.0,
        )

        val q = query.captured
        assertTrue("color identity", q.contains("id<=UW") || q.contains("id<=WU"))
        assertTrue("commander legality", q.contains("legal:commander"))
        assertTrue("budget cap", q.contains("usd<=5"))
        assertTrue("exclude basics", q.contains("-t:basic"))
        assertTrue("spot removal oracle", q.contains("destroy target") && q.contains("exile target"))
    }

    @Test
    fun `empty color identity omits the id filter`() = runTest(dispatcher) {
        val query = slot<String>()
        coEvery { repository.searchWithRawQuery(capture(query)) } returns emptyList()

        val profile = minimalProfile(colorIdentity = emptySet())
        generator(profile = profile, evaluation = evaluation(DeckRole.RAMP to 3))

        assertTrue(!query.captured.contains("id<="))
    }

    @Test
    fun `no usd cap omits the usd filter`() = runTest(dispatcher) {
        val query = slot<String>()
        coEvery { repository.searchWithRawQuery(capture(query)) } returns emptyList()

        generator(
            profile = minimalProfile(colorIdentity = setOf(ManaColor.G)),
            evaluation = evaluation(DeckRole.RAMP to 2),
            usdCap = null,
        )

        assertTrue(!query.captured.contains("usd<="))
    }

    @Test
    fun `results are merged de-duplicated and sorted by edhrec rank nulls last`() = runTest(dispatcher) {
        val highRank = card(id = "a", colorIdentity = listOf("U")).copy(edhrecRank = 100)
        val lowRank = card(id = "b", colorIdentity = listOf("U")).copy(edhrecRank = 5)
        val noRank = card(id = "c", colorIdentity = listOf("U")).copy(edhrecRank = null)
        val duplicate = card(id = "a", colorIdentity = listOf("U")).copy(edhrecRank = 100)

        // First role query returns a + c, second returns b + duplicate-of-a.
        coEvery { repository.searchWithRawQuery(match { it.contains("destroy all") }) } returns
            listOf(highRank, noRank)
        coEvery { repository.searchWithRawQuery(match { it.contains("draw a card") }) } returns
            listOf(lowRank, duplicate)

        val result = generator(
            profile = minimalProfile(colorIdentity = setOf(ManaColor.U)),
            evaluation = evaluation(DeckRole.BOARD_WIPE to 2, DeckRole.CARD_ADVANTAGE to 2),
        )

        // De-dup: 3 unique ids (a, b, c).
        assertEquals(listOf("b", "a", "c"), result.map { it.scryfallId })
    }

    @Test
    fun `roles without a generic query fragment are skipped`() = runTest(dispatcher) {
        coEvery { repository.searchWithRawQuery(any()) } returns emptyList()

        // PAYOFF / THREAT have no fragment → zero queries.
        val result = generator(
            profile = minimalProfile(colorIdentity = setOf(ManaColor.U)),
            evaluation = evaluation(DeckRole.PAYOFF to 5, DeckRole.THREAT to 5),
        )

        coVerify(exactly = 0) { repository.searchWithRawQuery(any()) }
        assertTrue(result.isEmpty())
    }

    @Test
    fun `a failing role query does not abort the others`() = runTest(dispatcher) {
        coEvery { repository.searchWithRawQuery(match { it.contains("destroy all") }) } throws
            RuntimeException("Scryfall down")
        val survivor = card(id = "ok", colorIdentity = listOf("U"))
        coEvery { repository.searchWithRawQuery(match { it.contains("draw a card") }) } returns
            listOf(survivor)

        val result = generator(
            profile = minimalProfile(colorIdentity = setOf(ManaColor.U)),
            evaluation = evaluation(DeckRole.BOARD_WIPE to 2, DeckRole.CARD_ADVANTAGE to 2),
        )

        assertEquals(listOf("ok"), result.map { it.scryfallId })
    }
}
