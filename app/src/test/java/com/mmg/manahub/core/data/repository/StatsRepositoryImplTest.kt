package com.mmg.manahub.core.data.repository

import com.mmg.manahub.core.data.local.dao.DeckDao
import com.mmg.manahub.core.data.local.dao.StatsDao
import com.mmg.manahub.core.data.local.entity.projection.CardValueProjection
import com.mmg.manahub.core.data.local.entity.projection.DuplicateCardProjection
import com.mmg.manahub.core.data.local.entity.projection.FormatCoverageProjection
import com.mmg.manahub.core.data.local.entity.projection.TotalsProjection
import com.mmg.manahub.core.data.local.entity.projection.UniqueCardPriceProjection
import com.mmg.manahub.core.domain.auth.AuthRepository
import com.mmg.manahub.core.domain.auth.SessionState
import com.mmg.manahub.core.common.DispatcherProvider
import com.mmg.manahub.core.model.PreferredCurrency
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [StatsRepositoryImpl.observeCollectionStats]'s Phase 2 (2026-07 stats expansion)
 * mapping logic: avg/median unique-card value, top-10 value concentration, foil value share (with
 * the 2026-07-23 `coerceIn` clamp regression), most-duplicated-card passthrough, and format
 * coverage. Every unrelated DAO flow is stubbed to a harmless default in [setUp] so each test only
 * overrides the flow(s) it actually exercises (see memory `testing_conventions`).
 *
 * Division-by-zero for set completion (`SetCompletion.completionRatio`) lives on a presentation
 * model, not this repository -- covered separately in `StatsUiStateTest`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StatsRepositoryImplTest {

    private val statsDao = mockk<StatsDao>()
    private val deckDao = mockk<DeckDao>()
    private val authRepository = mockk<AuthRepository>()

    @Before
    fun setUp() {
        every { authRepository.sessionState } returns MutableStateFlow(SessionState.Unauthenticated)

        every { statsDao.observeTotals(any(), any(), any()) } returns flowOf(TotalsProjection(0, 0))
        every { statsDao.observeTotalValueUsd(any(), any(), any()) } returns flowOf(0.0)
        every { statsDao.observeTotalValueEur(any(), any(), any()) } returns flowOf(0.0)
        every { statsDao.observeMostValuableCards(any(), any(), any(), any(), any()) } returns flowOf(emptyList())
        every { statsDao.observeCountByColorIdentity(any(), any(), any()) } returns flowOf(emptyList())
        every { statsDao.observeCountByRarity(any(), any(), any()) } returns flowOf(emptyList())
        every { statsDao.observeCountByTypeLine(any(), any(), any()) } returns flowOf(emptyList())
        every { statsDao.observeManaCurve(any(), any(), any()) } returns flowOf(emptyList())
        every { statsDao.observeCountBySet(any(), any(), any()) } returns flowOf(emptyList())
        every { deckDao.observeDeckCount() } returns flowOf(0)
        every { statsDao.observeTotalFoil(any(), any(), any()) } returns flowOf(0)
        every { statsDao.observeTotalFullArt(any(), any(), any()) } returns flowOf(0)
        every { statsDao.observeTopArtist(any(), any(), any()) } returns flowOf(null)
        every { statsDao.observeAvgManaValue(any(), any(), any()) } returns flowOf(null)
        every { statsDao.observeAvgPower(any(), any(), any()) } returns flowOf(null)
        every { statsDao.observeAvgToughness(any(), any(), any()) } returns flowOf(null)
        every { statsDao.observeOldestCard(any(), any(), any()) } returns flowOf(null)
        every { statsDao.observeNewestCard(any(), any(), any()) } returns flowOf(null)
        every { statsDao.observeTopSetByCount(any(), any(), any()) } returns flowOf(null)
        every { statsDao.observeTopSetByValue(any(), any(), any(), any()) } returns flowOf(null)
        every { statsDao.observeAllCollectionTags(any(), any(), any()) } returns flowOf(emptyList())
        every { statsDao.observeUniqueCardPrices(any(), any(), any()) } returns flowOf(emptyList())
        every { statsDao.observeTotalFoilValueUsd(any(), any(), any()) } returns flowOf(0.0)
        every { statsDao.observeTotalFoilValueEur(any(), any(), any()) } returns flowOf(0.0)
        every { statsDao.observeMostDuplicatedCard(any(), any(), any()) } returns flowOf(null)
        every { statsDao.observeFormatCoverage(any(), any(), any()) } returns flowOf(FormatCoverageProjection(0, 0, 0))
        every { statsDao.observeAllCollectionKeywords(any(), any(), any()) } returns flowOf(emptyList())
        // Hall of Fame enrichment (2026-07 stats expansion)
        every { statsDao.observeMostVariantsCard(any(), any(), any()) } returns flowOf(null)
        every { statsDao.observeCardsByArtist(any(), any(), any(), any(), any()) } returns flowOf(emptyList())
        every { statsDao.observeCountByDecade(any(), any(), any()) } returns flowOf(emptyList())
    }

    private fun repo() = StatsRepositoryImpl(statsDao, deckDao, authRepository, DispatcherProvider())

    private fun cardValueProjection(id: String, priceUsd: Double, priceEur: Double = priceUsd) =
        CardValueProjection(scryfallId = id, name = id, priceUsd = priceUsd, priceEur = priceEur, isFoil = false, imageArtCrop = null)

    private fun duplicateCardProjection(id: String, totalQuantity: Int, priceUsd: Double = 1.0, priceEur: Double = 1.0) =
        DuplicateCardProjection(
            scryfallId = id, name = id, imageArtCrop = null, isFoil = false, colorIdentity = "[]",
            setCode = "tst", setName = "Test", rarity = "common", priceUsd = priceUsd, priceEur = priceEur,
            totalQuantity = totalQuantity,
        )

    // ── Average / median unique-card value ──────────────────────────────────────

    @Test
    fun `median card value with an odd count returns the middle price`() = runTest {
        every { statsDao.observeUniqueCardPrices(any(), any(), any()) } returns flowOf(listOf(
            UniqueCardPriceProjection("a", priceUsd = 1.0, priceEur = 1.0),
            UniqueCardPriceProjection("b", priceUsd = 5.0, priceEur = 5.0),
            UniqueCardPriceProjection("c", priceUsd = 9.0, priceEur = 9.0),
        ))

        val stats = repo().observeCollectionStats(PreferredCurrency.USD, null, null).first()

        assertEquals(5.0, stats.medianCardValue, 0.0001)
        assertEquals(5.0, stats.avgCardValue, 0.0001)
    }

    @Test
    fun `median card value with an even count averages the two middle prices`() = runTest {
        every { statsDao.observeUniqueCardPrices(any(), any(), any()) } returns flowOf(listOf(
            UniqueCardPriceProjection("a", priceUsd = 1.0, priceEur = 1.0),
            UniqueCardPriceProjection("b", priceUsd = 3.0, priceEur = 3.0),
            UniqueCardPriceProjection("c", priceUsd = 5.0, priceEur = 5.0),
            UniqueCardPriceProjection("d", priceUsd = 9.0, priceEur = 9.0),
        ))

        val stats = repo().observeCollectionStats(PreferredCurrency.USD, null, null).first()

        assertEquals(4.0, stats.medianCardValue, 0.0001) // (3 + 5) / 2
    }

    @Test
    fun `median and average card value exclude zero priced cards`() = runTest {
        every { statsDao.observeUniqueCardPrices(any(), any(), any()) } returns flowOf(listOf(
            UniqueCardPriceProjection("a", priceUsd = 0.0, priceEur = 0.0),
            UniqueCardPriceProjection("b", priceUsd = 10.0, priceEur = 10.0),
        ))

        val stats = repo().observeCollectionStats(PreferredCurrency.USD, null, null).first()

        assertEquals(10.0, stats.medianCardValue, 0.0001)
        assertEquals(10.0, stats.avgCardValue, 0.0001)
    }

    @Test
    fun `median and average card value are zero when no priced cards exist`() = runTest {
        val stats = repo().observeCollectionStats(PreferredCurrency.USD, null, null).first()

        assertEquals(0.0, stats.medianCardValue, 0.0001)
        assertEquals(0.0, stats.avgCardValue, 0.0001)
    }

    @Test
    fun `card value uses the EUR price column when the active currency is EUR`() = runTest {
        every { statsDao.observeUniqueCardPrices(any(), any(), any()) } returns flowOf(listOf(
            UniqueCardPriceProjection("a", priceUsd = 100.0, priceEur = 8.0),
        ))

        val stats = repo().observeCollectionStats(PreferredCurrency.EUR, null, null).first()

        assertEquals(8.0, stats.medianCardValue, 0.0001)
        assertEquals(8.0, stats.avgCardValue, 0.0001)
    }

    // ── Value concentration (top 10) ────────────────────────────────────────────

    @Test
    fun `value concentration top 10 sums fewer than ten cards when the collection is smaller`() = runTest {
        every { statsDao.observeTotalValueUsd(any(), any(), any()) } returns flowOf(30.0)
        every { statsDao.observeMostValuableCards(any(), any(), any(), any(), any()) } returns flowOf(listOf(
            cardValueProjection("a", priceUsd = 20.0),
            cardValueProjection("b", priceUsd = 10.0),
        ))

        val stats = repo().observeCollectionStats(PreferredCurrency.USD, null, null).first()

        assertEquals(1f, stats.valueConcentrationTop10Percent, 0.0001f) // (20 + 10) / 30 == 1.0
    }

    @Test
    fun `value concentration top 10 is zero when total collection value is zero`() = runTest {
        every { statsDao.observeTotalValueUsd(any(), any(), any()) } returns flowOf(0.0)
        every { statsDao.observeMostValuableCards(any(), any(), any(), any(), any()) } returns flowOf(listOf(
            cardValueProjection("a", priceUsd = 20.0),
        ))

        val stats = repo().observeCollectionStats(PreferredCurrency.USD, null, null).first()

        assertEquals(0f, stats.valueConcentrationTop10Percent)
    }

    @Test
    fun `value concentration top 10 clamps to 100 percent when the top-cards flow outruns the total-value flow`() = runTest {
        // Regression: separate Room Flows (topCards vs totalValueUsd) can emit out of sync during
        // a refreshPrices() call -- previously produced a reading over 100% before the
        // 2026-07-23 coerceIn fix (see StatsRepositoryImpl KDoc).
        every { statsDao.observeTotalValueUsd(any(), any(), any()) } returns flowOf(5.0)
        every { statsDao.observeMostValuableCards(any(), any(), any(), any(), any()) } returns flowOf(listOf(
            cardValueProjection("a", priceUsd = 50.0),
        ))

        val stats = repo().observeCollectionStats(PreferredCurrency.USD, null, null).first()

        assertEquals(1f, stats.valueConcentrationTop10Percent)
    }

    // ── Foil value share ─────────────────────────────────────────────────────────

    @Test
    fun `foil value share divides foil value by total value`() = runTest {
        every { statsDao.observeTotalValueUsd(any(), any(), any()) } returns flowOf(100.0)
        every { statsDao.observeTotalFoilValueUsd(any(), any(), any()) } returns flowOf(25.0)

        val stats = repo().observeCollectionStats(PreferredCurrency.USD, null, null).first()

        assertEquals(0.25f, stats.foilValueSharePercent)
    }

    @Test
    fun `foil value share clamps to 100 percent when the foil-value flow outruns the total-value flow`() = runTest {
        every { statsDao.observeTotalValueUsd(any(), any(), any()) } returns flowOf(10.0)
        every { statsDao.observeTotalFoilValueUsd(any(), any(), any()) } returns flowOf(40.0)

        val stats = repo().observeCollectionStats(PreferredCurrency.USD, null, null).first()

        assertEquals(1f, stats.foilValueSharePercent)
    }

    @Test
    fun `foil value share is zero when total value is zero`() = runTest {
        every { statsDao.observeTotalValueUsd(any(), any(), any()) } returns flowOf(0.0)
        every { statsDao.observeTotalFoilValueUsd(any(), any(), any()) } returns flowOf(5.0)

        val stats = repo().observeCollectionStats(PreferredCurrency.USD, null, null).first()

        assertEquals(0f, stats.foilValueSharePercent)
    }

    // ── Most-duplicated card ─────────────────────────────────────────────────────

    @Test
    fun `most duplicated card and its quantity pass through from the DAO projection`() = runTest {
        every { statsDao.observeMostDuplicatedCard(any(), any(), any()) } returns flowOf(
            duplicateCardProjection("dup-1", totalQuantity = 7)
        )

        val stats = repo().observeCollectionStats(PreferredCurrency.USD, null, null).first()

        assertEquals("dup-1", stats.mostDuplicatedCard?.scryfallId)
        assertEquals(7, stats.mostDuplicatedCardCount)
    }

    @Test
    fun `most duplicated card is null when the collection is empty`() = runTest {
        val stats = repo().observeCollectionStats(PreferredCurrency.USD, null, null).first()

        assertNull(stats.mostDuplicatedCard)
        assertEquals(0, stats.mostDuplicatedCardCount)
    }

    // ── Format coverage ──────────────────────────────────────────────────────────

    @Test
    fun `format coverage maps the DAO projection to named Commander Modern Standard keys`() = runTest {
        every { statsDao.observeFormatCoverage(any(), any(), any()) } returns flowOf(
            FormatCoverageProjection(commanderCount = 12, modernCount = 8, standardCount = 3)
        )

        val stats = repo().observeCollectionStats(PreferredCurrency.USD, null, null).first()

        assertEquals(mapOf("Commander" to 12, "Modern" to 8, "Standard" to 3), stats.formatCoverage)
    }
}
