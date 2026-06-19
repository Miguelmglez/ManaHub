package com.mmg.manahub.feature.decks.domain.usecase

import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.feature.decks.domain.engine.DeckEvaluation
import com.mmg.manahub.feature.decks.domain.engine.DeckRole
import com.mmg.manahub.feature.decks.domain.engine.ManaColor
import com.mmg.manahub.feature.decks.domain.engine.RoleCoverage
import com.mmg.manahub.feature.decks.domain.engine.card
import com.mmg.manahub.feature.decks.domain.engine.minimalProfile
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [CandidatePoolGenerator]. The Scryfall path ([CardRepository.searchWithRawQuery])
 * is mocked; the tests assert:
 *  - role queries lead with curated oracle tags (`otag:`) (plan E2),
 *  - an `otag:` query that errors or returns empty FALLS BACK to the legacy substring fragment (E2),
 *  - strategy / tribe queries are built from the profile fingerprint via the fixed allowlists (E3),
 *  - every query string is assembled from the controlled allowlist only (no user free text),
 *  - results are merged + de-duplicated + sorted by EDHREC rank (nulls last).
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

    // ── E2: oracle tag primary queries ──────────────────────────────────────────

    @Test
    fun `E2 - role queries lead with otag oracle tags`() = runTest(dispatcher) {
        val queries = mutableListOf<String>()
        // Non-empty result → no fallback fires; we see only the primary otag queries.
        coEvery { repository.searchWithRawQuery(capture(queries)) } returns
            listOf(card(id = "x", colorIdentity = listOf("U")))

        generator(
            profile = minimalProfile(colorIdentity = setOf(ManaColor.U)),
            evaluation = evaluation(
                DeckRole.BOARD_WIPE to 2,
                DeckRole.SPOT_REMOVAL to 3,
                DeckRole.CARD_ADVANTAGE to 3,
                DeckRole.RAMP to 2,
                DeckRole.INTERACTION to 2,
                DeckRole.TUTOR to 1,
            ),
        )

        assertTrue(queries.any { it.contains("otag:board-wipe") })
        assertTrue(queries.any { it.contains("otag:removal") })
        assertTrue(queries.any { it.contains("otag:card-advantage") })
        assertTrue(queries.any { it.contains("otag:ramp") })
        assertTrue(queries.any { it.contains("otag:counterspell") })
        assertTrue(queries.any { it.contains("otag:tutor") })
        // The legacy substrings must NOT appear when the otag query succeeded.
        assertFalse(queries.any { it.contains("destroy all") })
    }

    @Test
    fun `E2 - empty otag result falls back to the legacy substring fragment`() = runTest(dispatcher) {
        val queries = mutableListOf<String>()
        // Every query (primary + fallback) returns empty → fallback always fires.
        coEvery { repository.searchWithRawQuery(capture(queries)) } returns emptyList()

        generator(
            profile = minimalProfile(colorIdentity = setOf(ManaColor.U)),
            evaluation = evaluation(DeckRole.BOARD_WIPE to 2),
        )

        // Primary otag + substring fallback for the single role.
        assertTrue("primary otag issued", queries.any { it.contains("otag:board-wipe") })
        assertTrue("fallback substring issued", queries.any { it.contains("destroy all") })
        coVerify(exactly = 2) { repository.searchWithRawQuery(any()) }
    }

    @Test
    fun `E2 - erroring otag query falls back to the substring and recovers cards`() = runTest(dispatcher) {
        coEvery { repository.searchWithRawQuery(match { it.contains("otag:board-wipe") }) } throws
            RuntimeException("otag not supported")
        val survivor = card(id = "wrath", colorIdentity = listOf("U"))
        coEvery { repository.searchWithRawQuery(match { it.contains("destroy all") }) } returns
            listOf(survivor)

        val result = generator(
            profile = minimalProfile(colorIdentity = setOf(ManaColor.U)),
            evaluation = evaluation(DeckRole.BOARD_WIPE to 2),
        )

        assertEquals(listOf("wrath"), result.map { it.scryfallId })
    }

    @Test
    fun `E2 - successful otag query does NOT issue the fallback`() = runTest(dispatcher) {
        val queries = mutableListOf<String>()
        coEvery { repository.searchWithRawQuery(capture(queries)) } returns
            listOf(card(id = "ok", colorIdentity = listOf("U")))

        generator(
            profile = minimalProfile(colorIdentity = setOf(ManaColor.U)),
            evaluation = evaluation(DeckRole.SPOT_REMOVAL to 3),
        )

        // Only the primary otag query — no fallback substring.
        coVerify(exactly = 1) { repository.searchWithRawQuery(any()) }
        assertTrue(queries.single().contains("otag:removal"))
        assertFalse(queries.single().contains("destroy target"))
    }

    // ── Query composition (controlled allowlist only) ─────────────────────────────

    @Test
    fun `query contains color identity, legality, budget cap and excludes basics`() = runTest(dispatcher) {
        val query = slot<String>()
        coEvery { repository.searchWithRawQuery(capture(query)) } returns
            listOf(card(id = "x", colorIdentity = listOf("W")))

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
        assertTrue("spot removal otag", q.contains("otag:removal"))
    }

    @Test
    fun `empty color identity omits the id filter`() = runTest(dispatcher) {
        val query = slot<String>()
        coEvery { repository.searchWithRawQuery(capture(query)) } returns
            listOf(card(id = "x", colorIdentity = emptyList()))

        val profile = minimalProfile(colorIdentity = emptySet())
        generator(profile = profile, evaluation = evaluation(DeckRole.RAMP to 3))

        assertFalse(query.captured.contains("id<="))
    }

    @Test
    fun `no usd cap omits the usd filter`() = runTest(dispatcher) {
        val query = slot<String>()
        coEvery { repository.searchWithRawQuery(capture(query)) } returns
            listOf(card(id = "x", colorIdentity = listOf("G")))

        generator(
            profile = minimalProfile(colorIdentity = setOf(ManaColor.G)),
            evaluation = evaluation(DeckRole.RAMP to 2),
            usdCap = null,
        )

        assertFalse(query.captured.contains("usd<="))
    }

    // ── E3: strategy & tribe queries from the profile ─────────────────────────────

    @Test
    fun `E3 - elf tribe fingerprint adds a t-elf query`() = runTest(dispatcher) {
        val queries = mutableListOf<String>()
        coEvery { repository.searchWithRawQuery(capture(queries)) } returns
            listOf(card(id = "elf", colorIdentity = listOf("G")))

        // PAYOFF gap has no role query, but the derived `tribe:elf` fingerprint key drives a t:elf query.
        generator(
            profile = minimalProfile(
                colorIdentity = setOf(ManaColor.G),
                tagFingerprint = mapOf("tribe:elf" to 1.0f),
            ),
            evaluation = evaluation(DeckRole.PAYOFF to 5),
        )

        assertTrue("tribe query issued", queries.any { it.contains("t:elf") })
    }

    @Test
    fun `E3 - dominant strategy fingerprint adds an otag strategy query`() = runTest(dispatcher) {
        val queries = mutableListOf<String>()
        coEvery { repository.searchWithRawQuery(capture(queries)) } returns
            listOf(card(id = "lg", colorIdentity = listOf("W")))

        generator(
            profile = minimalProfile(
                colorIdentity = setOf(ManaColor.W),
                tagFingerprint = mapOf("lifegain" to 1.0f, "aggro" to 0.4f),
            ),
            evaluation = evaluation(DeckRole.SYNERGY to 5),
        )

        assertTrue("strategy otag issued", queries.any { it.contains("otag:lifegain") })
    }

    @Test
    fun `E3 - a strategy key NOT in the allowlist adds no extra query`() = runTest(dispatcher) {
        coEvery { repository.searchWithRawQuery(any()) } returns emptyList()

        // `control` is an ARCHETYPE key with no allowlisted otag → no strategy query, and PAYOFF has
        // no role query → zero queries overall.
        val result = generator(
            profile = minimalProfile(
                colorIdentity = setOf(ManaColor.U),
                tagFingerprint = mapOf("control" to 1.0f),
            ),
            evaluation = evaluation(DeckRole.PAYOFF to 5),
        )

        coVerify(exactly = 0) { repository.searchWithRawQuery(any()) }
        assertTrue(result.isEmpty())
    }

    // ── Merge / de-dup / sort ──────────────────────────────────────────────────────

    @Test
    fun `results are merged de-duplicated and sorted by edhrec rank nulls last`() = runTest(dispatcher) {
        val highRank = card(id = "a", colorIdentity = listOf("U")).copy(edhrecRank = 100)
        val lowRank = card(id = "b", colorIdentity = listOf("U")).copy(edhrecRank = 5)
        val noRank = card(id = "c", colorIdentity = listOf("U")).copy(edhrecRank = null)
        val duplicate = card(id = "a", colorIdentity = listOf("U")).copy(edhrecRank = 100)

        // First role's otag returns a + c, second role's otag returns b + duplicate-of-a.
        coEvery { repository.searchWithRawQuery(match { it.contains("otag:board-wipe") }) } returns
            listOf(highRank, noRank)
        coEvery { repository.searchWithRawQuery(match { it.contains("otag:card-advantage") }) } returns
            listOf(lowRank, duplicate)

        val result = generator(
            profile = minimalProfile(colorIdentity = setOf(ManaColor.U)),
            evaluation = evaluation(DeckRole.BOARD_WIPE to 2, DeckRole.CARD_ADVANTAGE to 2),
        )

        // De-dup: 3 unique ids (a, b, c), sorted by rank (5, 100, null).
        assertEquals(listOf("b", "a", "c"), result.map { it.scryfallId })
    }

    @Test
    fun `roles without a query fragment and no strategy or tribe produce zero queries`() = runTest(dispatcher) {
        coEvery { repository.searchWithRawQuery(any()) } returns emptyList()

        // PAYOFF / THREAT have no role fragment and the profile has no strategy/tribe fingerprint.
        val result = generator(
            profile = minimalProfile(colorIdentity = setOf(ManaColor.U)),
            evaluation = evaluation(DeckRole.PAYOFF to 5, DeckRole.THREAT to 5),
        )

        coVerify(exactly = 0) { repository.searchWithRawQuery(any()) }
        assertTrue(result.isEmpty())
    }

    @Test
    fun `a failing role query does not abort the others`() = runTest(dispatcher) {
        // Both the otag AND its fallback fail for board-wipe; card-advantage's otag succeeds.
        coEvery { repository.searchWithRawQuery(match { it.contains("otag:board-wipe") }) } throws
            RuntimeException("Scryfall down")
        coEvery { repository.searchWithRawQuery(match { it.contains("destroy all") }) } throws
            RuntimeException("Scryfall down")
        val survivor = card(id = "ok", colorIdentity = listOf("U"))
        coEvery { repository.searchWithRawQuery(match { it.contains("otag:card-advantage") }) } returns
            listOf(survivor)

        val result = generator(
            profile = minimalProfile(colorIdentity = setOf(ManaColor.U)),
            evaluation = evaluation(DeckRole.BOARD_WIPE to 2, DeckRole.CARD_ADVANTAGE to 2),
        )

        assertEquals(listOf("ok"), result.map { it.scryfallId })
    }
}
