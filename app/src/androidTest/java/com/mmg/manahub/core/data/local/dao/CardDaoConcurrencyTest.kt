package com.mmg.manahub.core.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mmg.manahub.core.data.local.MtgDatabase
import com.mmg.manahub.core.data.local.entity.CardEntity
import com.mmg.manahub.core.data.local.mapper.toTagList
import com.mmg.manahub.core.data.local.mapper.toTagsJson
import com.mmg.manahub.core.model.CardTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented Room tests for [CardDao.unionTags] — the Deck Engine Unification plan RUN 7b
 * (BUG 2) lost-update regression.
 *
 * Requires a connected device or emulator.
 *
 * Uses an in-memory [MtgDatabase] so tests are hermetic and do not touch the real on-device
 * database file. The DB is created fresh for every test method via [createDatabase]/[closeDatabase].
 *
 * The concurrency test deliberately runs REAL concurrent transactions on `Dispatchers.IO` (via
 * `runBlocking`, NOT `runTest`), mirroring [GamificationDaoConcurrencyTest] — the whole point is
 * that [CardDao.unionTags] reads the CURRENT tags INSIDE its own `@Transaction` (not a use-case-
 * level read-then-write), so two racing enrichment jobs targeting the same card (the on-device
 * analyzer's background resolution and `RefreshCardStrategyTagsUseCase`'s precomputed-table
 * lookup — both fired on the same cache-miss card-detail view, see `CardRepositoryImpl
 * .scheduleTagResolution` / `RefreshCardStrategyTagsUseCase`) must both land, regardless of which
 * one's write wins the ordering.
 *
 * NOTE: These tests were written correctly but were NOT executed against a device or emulator
 * because no connected device was available at the time of writing. Run them with
 * `./gradlew connectedAndroidTest` after connecting a device.
 */
@RunWith(AndroidJUnit4::class)
class CardDaoConcurrencyTest {

    // ── In-memory database ────────────────────────────────────────────────────

    private lateinit var db: MtgDatabase
    private lateinit var dao: CardDao

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // NOTE: deliberately NOT allowMainThreadQueries() — the concurrency test needs real
        // IO-thread transactions so Room's locking is genuinely exercised.
        db = Room.inMemoryDatabaseBuilder(context, MtgDatabase::class.java).build()
        dao = db.cardDao()
    }

    @After
    fun closeDatabase() {
        db.close()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun makeCard(scryfallId: String, tagsJson: String = "[]") = CardEntity(
        scryfallId = scryfallId,
        name = "Test Card",
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
        tags = tagsJson,
    )

    // ── Race: two concurrent unionTags calls never lose either contribution ────

    @Test
    fun `two concurrent unionTags calls for the same card both land`() = runBlocking {
        val scryfallId = "card-1"
        dao.insertIgnore(makeCard(scryfallId))

        // Simulates scheduleTagResolution (on-device analyzer) and RefreshCardStrategyTagsUseCase
        // (precomputed table) racing on the SAME cache-miss card view, each contributing a
        // DIFFERENT tag. Before the fix, whichever plain-overwrote last would silently discard
        // the other's contribution.
        coroutineScope {
            listOf(
                async(Dispatchers.IO) { dao.unionTags(scryfallId, listOf(CardTag.REMOVAL)) },
                async(Dispatchers.IO) { dao.unionTags(scryfallId, listOf(CardTag.RAMP)) },
            ).awaitAll()
        }

        val persisted = dao.getById(scryfallId)!!.tags.toTagList()
        assertTrue("REMOVAL must survive the concurrent race", persisted.contains(CardTag.REMOVAL))
        assertTrue("RAMP must survive the concurrent race", persisted.contains(CardTag.RAMP))
        assertEquals("no duplicate/lost entries", 2, persisted.size)
    }

    @Test
    fun `sequential unionTags calls with overlapping tags merge without duplicates`() = runBlocking {
        val scryfallId = "card-2"
        dao.insertIgnore(makeCard(scryfallId, tagsJson = listOf(CardTag.REMOVAL).toTagsJson()))

        dao.unionTags(scryfallId, listOf(CardTag.REMOVAL, CardTag.RAMP))
        dao.unionTags(scryfallId, listOf(CardTag.RAMP, CardTag.TUTOR))

        val persisted = dao.getById(scryfallId)!!.tags.toTagList()
        assertEquals(
            "union of all three contributions, no duplicates",
            setOf(CardTag.REMOVAL, CardTag.RAMP, CardTag.TUTOR),
            persisted.toSet(),
        )
        assertEquals(3, persisted.size)
    }

    // ── Safe no-ops ──────────────────────────────────────────────────────────

    @Test
    fun `unionTags with an empty additional list is a no-op`() = runBlocking {
        val scryfallId = "card-3"
        dao.insertIgnore(makeCard(scryfallId, tagsJson = listOf(CardTag.REMOVAL).toTagsJson()))

        dao.unionTags(scryfallId, emptyList())

        val persisted = dao.getById(scryfallId)!!.tags.toTagList()
        assertEquals(listOf(CardTag.REMOVAL), persisted)
    }

    @Test
    fun `unionTags for a row that does not exist never throws`() = runBlocking {
        // No insert — the row simply isn't cached yet. Must be a silent no-op, not a crash.
        dao.unionTags("missing-card", listOf(CardTag.REMOVAL))
    }
}
