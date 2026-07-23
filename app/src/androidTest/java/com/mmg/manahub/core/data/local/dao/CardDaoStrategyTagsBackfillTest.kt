package com.mmg.manahub.core.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mmg.manahub.core.data.local.MtgDatabase
import com.mmg.manahub.core.data.local.entity.CardEntity
import com.mmg.manahub.core.data.local.entity.CardStrategyTagsCacheEntity
import com.mmg.manahub.core.data.local.entity.DeckCardEntity
import com.mmg.manahub.core.data.local.entity.DeckEntity
import com.mmg.manahub.core.data.local.entity.LocalWishlistEntity
import com.mmg.manahub.core.data.local.entity.UserCardCollectionEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented Room tests for [CardDao.getScryfallIdsMissingStrategyTags] — the strategy-tags
 * backfill query (2026-07-22) that feeds [com.mmg.manahub.core.data.repository.CardRepositoryImpl
 * .backfillMissingStrategyTags].
 *
 * Requires a connected device or emulator (`./gradlew connectedAndroidTest`).
 *
 * Uses an in-memory [MtgDatabase] so tests are hermetic and do not touch the real on-device
 * database file. The DB is created fresh for every test method via [createDatabase]/[closeDatabase].
 */
@RunWith(AndroidJUnit4::class)
class CardDaoStrategyTagsBackfillTest {

    // ── In-memory database ────────────────────────────────────────────────────

    private lateinit var db: MtgDatabase
    private lateinit var cardDao: CardDao

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MtgDatabase::class.java).build()
        cardDao = db.cardDao()
    }

    @After
    fun closeDatabase() {
        db.close()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun makeCard(scryfallId: String, oracleId: String) = CardEntity(
        scryfallId = scryfallId,
        name = "Test Card $scryfallId",
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
        keywords = "[]",
        power = null,
        toughness = null,
        loyalty = null,
        setCode = "tst",
        setName = "Test Set",
        collectorNumber = "1",
        rarity = "common",
        releasedAt = "2020-01-01",
        imageNormal = null,
        imageArtCrop = null,
        imageBackNormal = null,
        priceUsd = null,
        priceUsdFoil = null,
        priceEur = null,
        priceEurFoil = null,
        legalityStandard = "not_legal",
        legalityPioneer = "not_legal",
        legalityModern = "not_legal",
        legalityCommander = "not_legal",
        flavorText = null,
        artist = null,
        scryfallUri = "https://scryfall.com/test",
        oracleId = oracleId,
    )

    private fun insertCollectionRow(scryfallId: String, isDeleted: Boolean = false) {
        db.userCardCollectionDao().upsert(
            UserCardCollectionEntity(
                id = "collection-$scryfallId",
                userId = null,
                scryfallId = scryfallId,
                isDeleted = isDeleted,
            )
        )
    }

    private suspend fun insertWishlistRow(scryfallId: String) {
        db.localWishlistDao().insert(
            LocalWishlistEntity(
                id = "wishlist-$scryfallId",
                scryfallId = scryfallId,
                isFoil = null,
                condition = null,
                language = null,
            )
        )
    }

    private fun insertDeckCardRow(scryfallId: String) {
        val deckId = "deck-1"
        db.deckDao().upsertDeck(DeckEntity(id = deckId, userId = null, name = "Test Deck"))
        db.deckDao().upsertDeckCard(DeckCardEntity(deckId = deckId, scryfallId = scryfallId))
    }

    private suspend fun insertCacheRow(oracleId: String) {
        db.cardStrategyTagsCacheDao().upsert(
            CardStrategyTagsCacheEntity(
                oracleId = oracleId,
                payloadJson = "{}",
                pipelineVersion = "1",
                generatedAt = "2026-07-22",
                fetchedAt = System.currentTimeMillis(),
            )
        )
    }

    // ── Tests ────────────────────────────────────────────────────────────────

    @Test
    fun `card with blank oracle id is excluded even when owned`() = runBlocking {
        cardDao.insertIgnore(makeCard("card-blank-oracle", oracleId = ""))
        insertCollectionRow("card-blank-oracle")

        val result = cardDao.getScryfallIdsMissingStrategyTags(limit = 10)

        assertTrue("blank oracle_id must never be a candidate", result.isEmpty())
    }

    @Test
    fun `card already present in the strategy tags cache is excluded`() = runBlocking {
        cardDao.insertIgnore(makeCard("card-already-cached", oracleId = "oracle-cached"))
        insertCollectionRow("card-already-cached")
        insertCacheRow("oracle-cached")

        val result = cardDao.getScryfallIdsMissingStrategyTags(limit = 10)

        assertTrue(
            "a card already resolved (present in card_strategy_tags_cache) must not be re-queried",
            result.isEmpty(),
        )
    }

    @Test
    fun `card owned via live collection and never resolved is included`() = runBlocking {
        cardDao.insertIgnore(makeCard("card-collection", oracleId = "oracle-collection"))
        insertCollectionRow("card-collection")

        val result = cardDao.getScryfallIdsMissingStrategyTags(limit = 10)

        assertEquals(listOf("card-collection"), result)
    }

    @Test
    fun `card only present in a soft-deleted collection row is excluded`() = runBlocking {
        cardDao.insertIgnore(makeCard("card-soft-deleted", oracleId = "oracle-soft-deleted"))
        insertCollectionRow("card-soft-deleted", isDeleted = true)

        val result = cardDao.getScryfallIdsMissingStrategyTags(limit = 10)

        assertTrue("a soft-deleted collection row must not count as owned", result.isEmpty())
    }

    @Test
    fun `card owned via wishlist and never resolved is included`() = runBlocking {
        cardDao.insertIgnore(makeCard("card-wishlist", oracleId = "oracle-wishlist"))
        insertWishlistRow("card-wishlist")

        val result = cardDao.getScryfallIdsMissingStrategyTags(limit = 10)

        assertEquals(listOf("card-wishlist"), result)
    }

    @Test
    fun `card owned via a deck and never resolved is included`() = runBlocking {
        cardDao.insertIgnore(makeCard("card-deck", oracleId = "oracle-deck"))
        insertDeckCardRow("card-deck")

        val result = cardDao.getScryfallIdsMissingStrategyTags(limit = 10)

        assertEquals(listOf("card-deck"), result)
    }

    @Test
    fun `card with a real oracle id that is not owned anywhere is excluded`() = runBlocking {
        cardDao.insertIgnore(makeCard("card-not-owned", oracleId = "oracle-not-owned"))
        // No collection/wishlist/deck row inserted for this card.

        val result = cardDao.getScryfallIdsMissingStrategyTags(limit = 10)

        assertTrue("an unowned card must never be a backfill candidate", result.isEmpty())
    }

    @Test
    fun `result is capped at the requested limit`() = runBlocking {
        repeat(5) { i ->
            val id = "card-$i"
            cardDao.insertIgnore(makeCard(id, oracleId = "oracle-$i"))
            insertCollectionRow(id)
        }

        val result = cardDao.getScryfallIdsMissingStrategyTags(limit = 2)

        assertEquals(2, result.size)
    }

    @Test
    fun `a card owned in more than one place is returned only once`() = runBlocking {
        cardDao.insertIgnore(makeCard("card-multi-owned", oracleId = "oracle-multi-owned"))
        insertCollectionRow("card-multi-owned")
        insertWishlistRow("card-multi-owned")

        val result = cardDao.getScryfallIdsMissingStrategyTags(limit = 10)

        assertEquals(listOf("card-multi-owned"), result)
    }
}
