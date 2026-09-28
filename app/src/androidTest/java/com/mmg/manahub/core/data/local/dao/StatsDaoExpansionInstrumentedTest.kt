package com.mmg.manahub.core.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mmg.manahub.core.data.local.MtgDatabase
import com.mmg.manahub.core.data.local.entity.CardEntity
import com.mmg.manahub.core.data.local.entity.UserCardCollectionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented Room tests for [StatsDao]'s Phase 2 (2026-07 stats expansion) queries:
 * [StatsDao.observeUniqueCardPrices], [StatsDao.observeTotalFoilValueUsd],
 * [StatsDao.observeMostDuplicatedCard], [StatsDao.observeFormatCoverage],
 * [StatsDao.observeAllCollectionKeywords], and [StatsDao.observeDistinctOwnedCountBySet].
 *
 * Requires a connected device or emulator (`./gradlew connectedAndroidTest`) -- NOT executed in
 * this environment (no adb-visible device), written and reviewed to compile/match the DAO's exact
 * query semantics, mirroring the existing `PlaytestDaoInstrumentedTest`/
 * `CardDaoStrategyTagsBackfillTest` pattern (in-memory DB, `allowMainThreadQueries()`).
 */
@RunWith(AndroidJUnit4::class)
class StatsDaoExpansionInstrumentedTest {

    private lateinit var db: MtgDatabase
    private lateinit var statsDao: StatsDao

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MtgDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        statsDao = db.statsDao()
    }

    @After
    fun closeDatabase() {
        db.close()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private suspend fun insertCard(
        scryfallId: String,
        priceUsd: Double? = null,
        priceUsdFoil: Double? = null,
        priceEur: Double? = null,
        priceEurFoil: Double? = null,
        keywords: String = "[]",
        legalityCommander: String = "not_legal",
        legalityModern: String = "not_legal",
        legalityStandard: String = "not_legal",
        setCode: String = "tst",
        releasedAt: String = "2020-01-01",
        artist: String? = null,
        colors: String = "[]",
        identity: String = "[]",
        oracleId: String = "",
        power: String? = null,
        toughness: String? = null,
    ) {
        db.cardDao().upsert(
            CardEntity(
                scryfallId = scryfallId,
                name = "Card $scryfallId",
                printedName = null,
                lang = "en",
                manaCost = null,
                cmc = 0.0,
                colors = colors,
                colorIdentity = identity,
                typeLine = "Creature",
                printedTypeLine = null,
                oracleText = null,
                printedText = null,
                keywords = keywords,
                power = power,
                toughness = toughness,
                loyalty = null,
                setCode = setCode,
                setName = "Test Set",
                collectorNumber = "1",
                rarity = "common",
                releasedAt = releasedAt,
                imageNormal = null,
                imageArtCrop = null,
                imageBackNormal = null,
                priceUsd = priceUsd,
                priceUsdFoil = priceUsdFoil,
                priceEur = priceEur,
                priceEurFoil = priceEurFoil,
                legalityStandard = legalityStandard,
                legalityPioneer = "not_legal",
                legalityModern = legalityModern,
                legalityCommander = legalityCommander,
                flavorText = null,
                artist = artist,
                scryfallUri = "https://scryfall.com/test",
                oracleId = oracleId,
            )
        )
    }

    private fun insertCollectionRow(
        id: String,
        scryfallId: String,
        quantity: Int = 1,
        isFoil: Boolean = false,
        createdAt: Long = System.currentTimeMillis(),
    ) {
        db.userCardCollectionDao().upsert(
            UserCardCollectionEntity(
                id = id,
                userId = null,
                scryfallId = scryfallId,
                quantity = quantity,
                isFoil = isFoil,
                createdAt = createdAt,
            )
        )
    }

    @Test
    fun colorAggregatesUsePrintedColorsAndMulticolorWithConcurrentSetFilter() = runTest {
        insertCard("wu", priceUsd = 3.0, priceEur = 4.0, colors = "[\"W\",\"U\"]", identity = "[\"W\",\"U\"]", setCode = "one", artist = "Shared Artist", oracleId = "oracle-wu", legalityCommander = "legal", power = "2", toughness = "3")
        insertCard("w", priceUsd = 2.0, colors = "[\"W\"]", identity = "[\"W\",\"U\"]", setCode = "one")
        insertCard("ub", priceUsd = 5.0, colors = "[\"U\",\"B\"]", setCode = "two")
        insertCard("land", priceUsd = 1.0, colors = "[]", identity = "[\"W\",\"U\"]", setCode = "one")
        insertCollectionRow("wu-row", "wu", quantity = 2)
        insertCollectionRow("w-row", "w", quantity = 3)
        insertCollectionRow("ub-row", "ub", quantity = 4)
        insertCollectionRow("land-row", "land", quantity = 5)

        assertEquals(6, statsDao.observeTotals("M", null, null).first().totalCards)
        assertEquals(2, statsDao.observeTotals("M", "one", null).first().totalCards)
        assertEquals(6.0, statsDao.observeTotalValueUsd("M", "one", null).first(), 0.0001)
        assertEquals(2, statsDao.observeCountByRarity("M", "one", null).first().single().count)
        assertEquals("[\"W\",\"U\"]", statsDao.observeCountByColorIdentity("M", "one", null).first().single().colorIdentity)
        assertEquals(5, statsDao.observeTotals("[]", "one", null).first().totalCards)
        assertEquals(5, statsDao.observeTotals("W", "one", null).first().totalCards)
        assertEquals(8.0, statsDao.observeTotalValueEur("M", "one", null).first(), 0.0001)
        assertEquals(listOf("wu"), statsDao.observeMostValuableCards(10, false, "M", "one", null).first().map { it.scryfallId })
        assertEquals(2, statsDao.observeCountByTypeLine("M", "one", null).first().single().count)
        assertEquals(2, statsDao.observeManaCurve("M", "one", null).first().single().count)
        assertEquals(2, statsDao.observeCountBySet("M", "one", null).first().single().count)
        assertEquals(0, statsDao.observeTotalFoil("M", "one", null).first())
        assertEquals(0, statsDao.observeTotalFullArt("M", "one", null).first())
        assertEquals("Shared Artist", statsDao.observeTopArtist("M", "one", null).first()?.artist)
        assertEquals(0.0, statsDao.observeAvgManaValue("M", "one", null).first() ?: -1.0, 0.0001)
        assertEquals(2.0, statsDao.observeAvgPower("M", "one", null).first() ?: -1.0, 0.0001)
        assertEquals(3.0, statsDao.observeAvgToughness("M", "one", null).first() ?: -1.0, 0.0001)
        assertEquals("wu", statsDao.observeOldestCard("M", "one", null).first()?.scryfallId)
        assertEquals("wu", statsDao.observeNewestCard("M", "one", null).first()?.scryfallId)
        assertEquals("one", statsDao.observeTopSetByCount("M", "one", null).first()?.setCode)
        assertEquals(6.0, statsDao.observeTopSetByValue("M", "one", false, null).first()?.totalValue ?: -1.0, 0.0001)
        assertEquals(8.0, statsDao.observeTopSetByValue("M", "one", true, null).first()?.totalValue ?: -1.0, 0.0001)
        assertEquals(1, statsDao.observeAllCollectionTags("M", "one", null).first().size)
        assertEquals(1, statsDao.observeUniqueCardPrices("M", "one", null).first().size)
        assertEquals(0.0, statsDao.observeTotalFoilValueUsd("M", "one", null).first(), 0.0001)
        assertEquals(0.0, statsDao.observeTotalFoilValueEur("M", "one", null).first(), 0.0001)
        assertEquals("wu", statsDao.observeMostDuplicatedCard("M", "one", null).first()?.scryfallId)
        assertEquals(1, statsDao.observeFormatCoverage("M", "one", null).first().commanderCount)
        assertEquals(1, statsDao.observeAllCollectionKeywords("M", "one", null).first().size)
        assertEquals("wu", statsDao.observeMostVariantsCard("M", "one", null).first()?.scryfallId)
        assertEquals(listOf("wu"), statsDao.observeCardsByArtist("Shared Artist", "M", "one", null, 10).first().map { it.scryfallId })
        assertEquals(2, statsDao.observeCountByDecade("M", "one", null).first().single().count)
    }

    @Test
    fun observeTotals_countsUncachedOwnershipWhileCardFieldAggregatesSkipIt() = runTest {
        insertCard("cached", priceUsd = 4.0)
        insertCollectionRow("cached-row", "cached", quantity = 2)
        insertCollectionRow("uncached-row", "uncached", quantity = 3)

        val totals = statsDao.observeTotals(null, null, null).first()

        assertEquals(5, totals.totalCards)
        assertEquals(2, totals.uniqueCards)
        assertEquals(8.0, statsDao.observeTotalValueUsd(null, null, null).first(), 0.0001)
        assertEquals(2, statsDao.observeTotals("[]", null, null).first().totalCards)
    }

    @Test
    fun cardValueProjections_includeOwnedQuantityForSingleRowsAndArtistGroups() = runTest {
        insertCard("old", priceUsd = 2.0, releasedAt = "2010-01-01", artist = "Artist")
        insertCard("new", priceUsd = 3.0, releasedAt = "2020-01-01", artist = "Artist")
        insertCollectionRow("old-row", "old", quantity = 2)
        insertCollectionRow("new-row-a", "new", quantity = 3)
        insertCollectionRow("new-row-b", "new", quantity = 4)

        assertEquals(2, statsDao.observeOldestCard(null, null, null).first()?.quantity)
        assertTrue(statsDao.observeNewestCard(null, null, null).first()?.quantity in setOf(3, 4))
        val artistCards = statsDao.observeCardsByArtist("Artist", null, null, null, 10).first()
        assertEquals(7, artistCards.first { it.scryfallId == "new" }.quantity)
    }

    // ── observeUniqueCardPrices ──────────────────────────────────────────────────

    @Test
    fun observeUniqueCardPrices_returnsOneRowPerDistinctCard_usingTheCanonicalNonFoilPrice() = runTest {
        insertCard("card-a", priceUsd = 5.0, priceUsdFoil = 15.0, priceEur = 4.0, priceEurFoil = 12.0)
        insertCollectionRow("row-1", "card-a", isFoil = false)
        insertCollectionRow("row-2", "card-a", isFoil = true) // same card, still ONE distinct row

        val rows = statsDao.observeUniqueCardPrices(null, null, null).first()

        assertEquals(1, rows.size)
        assertEquals(5.0, rows.first().priceUsd, 0.0001)
        assertEquals(4.0, rows.first().priceEur, 0.0001)
    }

    // ── observeTotalFoilValueUsd ─────────────────────────────────────────────────

    @Test
    fun observeTotalFoilValueUsd_sumsOnlyFoilRows_usingTheFoilPriceWithBaseFallback() = runTest {
        insertCard("card-a", priceUsd = 5.0, priceUsdFoil = 15.0)
        insertCard("card-b", priceUsd = 2.0, priceUsdFoil = null) // no foil price -> falls back to base
        insertCollectionRow("row-1", "card-a", quantity = 2, isFoil = true)  // 2 * 15 = 30
        insertCollectionRow("row-2", "card-b", quantity = 1, isFoil = true)  // 1 * 2  = 2 (fallback)
        insertCollectionRow("row-3", "card-a", quantity = 3, isFoil = false) // excluded -- not foil

        val total = statsDao.observeTotalFoilValueUsd(null, null, null).first()

        assertEquals(32.0, total, 0.0001)
    }

    // ── observeMostDuplicatedCard ────────────────────────────────────────────────

    @Test
    fun observeMostDuplicatedCard_picksTheCardWithTheHighestSummedQuantity() = runTest {
        // No secondary ORDER BY exists on this query (see StatsDao KDoc) -- a genuine tie is not
        // deterministically resolved by SQL, so this test uses a clear winner rather than a tie.
        insertCard("card-a")
        insertCard("card-b")
        insertCollectionRow("row-1", "card-a", quantity = 2)
        insertCollectionRow("row-2", "card-a", quantity = 3) // card-a total = 5
        insertCollectionRow("row-3", "card-b", quantity = 4) // card-b total = 4

        val result = statsDao.observeMostDuplicatedCard(null, null, null).first()

        assertEquals("card-a", result?.scryfallId)
        assertEquals(5, result?.totalQuantity)
    }

    // ── observeFormatCoverage ────────────────────────────────────────────────────

    @Test
    fun observeFormatCoverage_countsDistinctOwnedCardsLegalPerFormat() = runTest {
        insertCard("card-a", legalityCommander = "legal", legalityModern = "legal", legalityStandard = "not_legal")
        insertCard("card-b", legalityCommander = "legal", legalityModern = "not_legal", legalityStandard = "not_legal")
        insertCard("card-c", legalityCommander = "not_legal", legalityModern = "not_legal", legalityStandard = "legal")
        insertCollectionRow("row-1", "card-a")
        insertCollectionRow("row-2", "card-b")
        insertCollectionRow("row-3", "card-c")

        val coverage = statsDao.observeFormatCoverage(null, null, null).first()

        assertEquals(2, coverage.commanderCount)
        assertEquals(1, coverage.modernCount)
        assertEquals(1, coverage.standardCount)
    }

    // ── observeAllCollectionKeywords ─────────────────────────────────────────────

    @Test
    fun observeAllCollectionKeywords_returnsTheRawKeywordsJson_forEveryOwnedRow() = runTest {
        insertCard("card-a", keywords = """["Flying","Trample"]""")
        insertCollectionRow("row-1", "card-a")

        val rows = statsDao.observeAllCollectionKeywords(null, null, null).first()

        assertEquals(1, rows.size)
        assertEquals("""["Flying","Trample"]""", rows.first().keywords)
    }

    // ── observeDistinctOwnedCountBySet ───────────────────────────────────────────

    @Test
    fun observeDistinctOwnedCountBySet_countsDistinctOwnedCardsPerSet_globally() = runTest {
        insertCard("card-a", setCode = "war")
        insertCard("card-b", setCode = "war")
        insertCard("card-c", setCode = "m20")
        insertCollectionRow("row-1", "card-a")
        insertCollectionRow("row-2", "card-b")
        insertCollectionRow("row-3", "card-c")
        insertCollectionRow("row-4", "card-a") // duplicate row for card-a -- must not double count (DISTINCT)

        val rows = statsDao.observeDistinctOwnedCountBySet(null).first()
        val counts = rows.associate { it.setCode to it.count }

        assertEquals(2, counts["war"])
        assertEquals(1, counts["m20"])
    }
}
