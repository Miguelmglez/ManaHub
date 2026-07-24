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
    ) {
        db.cardDao().upsert(
            CardEntity(
                scryfallId = scryfallId,
                name = "Card $scryfallId",
                printedName = null,
                lang = "en",
                manaCost = null,
                cmc = 0.0,
                colors = "[]",
                colorIdentity = "[]",
                typeLine = "Creature",
                printedTypeLine = null,
                oracleText = null,
                printedText = null,
                keywords = keywords,
                power = null,
                toughness = null,
                loyalty = null,
                setCode = setCode,
                setName = "Test Set",
                collectorNumber = "1",
                rarity = "common",
                releasedAt = "2020-01-01",
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
                artist = null,
                scryfallUri = "https://scryfall.com/test",
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
