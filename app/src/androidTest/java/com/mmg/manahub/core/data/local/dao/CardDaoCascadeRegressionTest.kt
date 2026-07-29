package com.mmg.manahub.core.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mmg.manahub.core.data.local.MtgDatabase
import com.mmg.manahub.core.data.local.entity.CardEntity
import com.mmg.manahub.core.data.local.entity.UserCardCollectionEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented Room regression tests for [CardDao]'s upsert contract (CLAUDE.md "CardDao upsert"
 * invariant): [CardDao.upsert]/[CardDao.upsertAll] MUST use `INSERT OR IGNORE` + `@Update`, and
 * MUST NEVER use `OnConflictStrategy.REPLACE` on [CardEntity]. `REPLACE` is internally a
 * DELETE+INSERT, and [UserCardCollectionEntity] has an `ON DELETE RESTRICT` FK to `cards` — under
 * the old (buggy) `REPLACE` implementation, refreshing a card that is already in a user's
 * collection would either throw (RESTRICT blocking the implicit delete) or, prior to RESTRICT
 * being added, silently cascade-delete every [UserCardCollectionEntity] row referencing it.
 *
 * This class restores DAO-level coverage for that invariant. It replaces the repository-level
 * regression group that used to live in `CardRepositoryImplTest` (exercised indirectly via
 * `CardRepositoryImpl.refreshCollectionPrices()`), which was retired end-to-end when that method
 * was deleted (Backend & Performance Optimization plan, WS1+WS3 Part B item 7a, 2026-07-28 — see
 * `RefreshCollectionPricesUseCase`, which never calls `upsert`/`upsertAll` at all). Testing
 * straight against [CardDao] instead of through a now-deleted call site is also a strictly better
 * regression guard: it pins the DAO's own contract, not one caller's usage of it, so it survives
 * any future repository refactor.
 *
 * Requires a connected device or emulator (`./gradlew connectedAndroidTest`). Uses an in-memory
 * [MtgDatabase] so tests are hermetic and do not touch the real on-device database file.
 */
@RunWith(AndroidJUnit4::class)
class CardDaoCascadeRegressionTest {

    // ── In-memory database ────────────────────────────────────────────────────

    private lateinit var db: MtgDatabase
    private lateinit var cardDao: CardDao
    private lateinit var userCardCollectionDao: UserCardCollectionDao

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MtgDatabase::class.java).build()
        cardDao = db.cardDao()
        userCardCollectionDao = db.userCardCollectionDao()
    }

    @After
    fun closeDatabase() {
        db.close()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun makeCard(scryfallId: String, priceUsd: Double? = null) = CardEntity(
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
        priceUsd = priceUsd,
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
    )

    private fun insertCollectionRow(scryfallId: String) {
        userCardCollectionDao.upsert(
            UserCardCollectionEntity(
                id = "collection-$scryfallId",
                userId = null,
                scryfallId = scryfallId,
            )
        )
    }

    // ── Regression: CardDao.upsert() must never cascade-delete the user's collection ──

    @Test
    fun `given a card already in the users collection when upsert refreshes it then the collection row survives`() = runBlocking {
        // Arrange — insert the card, then a collection row that FK-references it.
        cardDao.upsert(makeCard("card-1", priceUsd = 1.0))
        insertCollectionRow("card-1")
        assertNotNull(userCardCollectionDao.getById("collection-card-1"))

        // Act — refresh the SAME card (simulates a Scryfall price/data refresh landing on an
        // already-cached row). If upsert() ever regresses to OnConflictStrategy.REPLACE, this
        // either throws SQLiteConstraintException (RESTRICT blocking the implicit delete) or,
        // were RESTRICT ever weakened back to CASCADE, silently wipes the collection row below.
        cardDao.upsert(makeCard("card-1", priceUsd = 2.0))

        // Assert — the collection row still exists, AND the card was actually updated (proves
        // this is a real UPDATE path, not a silent no-op from INSERT OR IGNORE alone).
        val survivingRow = userCardCollectionDao.getById("collection-card-1")
        assertNotNull(survivingRow)
        assertEquals("card-1", survivingRow?.scryfallId)
        assertEquals(2.0, cardDao.getById("card-1")?.priceUsd)
    }

    @Test
    fun `given multiple cards already in the collection when upsertAll refreshes the chunk then every collection row survives`() = runBlocking {
        // Arrange — three cards, each with a collection row referencing it (mirrors
        // SyncManager.ensureCardsExist's chunked upsertAll call).
        val ids = listOf("card-a", "card-b", "card-c")
        cardDao.upsertAll(ids.map { makeCard(it, priceUsd = 1.0) })
        ids.forEach { insertCollectionRow(it) }

        // Act — refresh the whole chunk at once, as a price-refresh slice or a sync pass would.
        cardDao.upsertAll(ids.map { makeCard(it, priceUsd = 3.0) })

        // Assert — every collection row survives, and every card was actually updated.
        ids.forEach { id ->
            assertNotNull(userCardCollectionDao.getById("collection-$id"))
            assertEquals(3.0, cardDao.getById(id)?.priceUsd)
        }
    }

    @Test
    fun `given a brand new card with no prior row when upsert runs then it is inserted normally`() = runBlocking {
        // Sanity check on the other branch of upsert()'s @Transaction: insertIgnore succeeds
        // (id != -1L), so updateCard must NOT be reached for a genuinely new row.
        cardDao.upsert(makeCard("card-new", priceUsd = 5.0))

        assertEquals(5.0, cardDao.getById("card-new")?.priceUsd)
    }
}
