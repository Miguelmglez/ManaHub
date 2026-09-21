package com.mmg.manahub.core.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mmg.manahub.core.data.local.MtgDatabase
import com.mmg.manahub.core.data.local.entity.DeckCardEntity
import com.mmg.manahub.core.data.local.entity.DeckEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented Room proof for the Deck Wizard Commander v3 plan (Phase 6, D12): a real SQLite
 * transaction failure inside [DeckDao.replaceAllCardsWithSource] rolls back the ENTIRE method body
 * (the clear, every prior insert in the loop, and the `updated_at` bump), leaving the deck's card
 * list exactly as it was before the call -- the guarantee `DeckRepository.replaceAllCardsWithSource`'s
 * commonMain default method (clearDeck + addCardToDeck loop, still used by `WebDeckRepository`)
 * cannot provide, and which `DeckRepositoryReplaceAllCardsWithSourceTest` (commonTest) documents as
 * UNMET for that default path.
 *
 * The mid-transaction failure is a genuine FK violation (`deck_cards.deck_id -> decks.id`), not a
 * simulated throw: the last entity in the write list references a deck id that does not exist, so
 * Room's own FK enforcement aborts the transaction exactly like a real Room write error would.
 *
 * Requires a connected device or emulator (`./gradlew connectedAndroidTest`). Uses an in-memory
 * [MtgDatabase] so tests are hermetic and do not touch the real on-device database file.
 */
@RunWith(AndroidJUnit4::class)
class DeckDaoReplaceAllCardsWithSourceTransactionTest {

    private lateinit var db: MtgDatabase
    private lateinit var deckDao: DeckDao

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MtgDatabase::class.java).build()
        deckDao = db.deckDao()
    }

    @After
    fun closeDatabase() {
        db.close()
    }

    private fun makeDeck(id: String, updatedAt: Long) = DeckEntity(
        id = id,
        userId = null,
        name = "Test Deck",
        format = "commander",
        updatedAt = updatedAt,
        createdAt = updatedAt,
    )

    @Test
    fun happyPath_replaceAllCardsWithSource_replacesCardsAndBumpsUpdatedAt() {
        val deckId = "deck-1"
        deckDao.upsertDeck(makeDeck(deckId, updatedAt = 100L))
        deckDao.upsertDeckCard(DeckCardEntity(deckId = deckId, scryfallId = "old-card", quantity = 1))

        deckDao.replaceAllCardsWithSource(
            deckId,
            listOf(
                DeckCardEntity(deckId = deckId, scryfallId = "new-card-1", quantity = 1, source = "WIZARD"),
                DeckCardEntity(deckId = deckId, scryfallId = "new-card-2", quantity = 1, source = "USER"),
            ),
            updatedAt = 200L,
        )

        val cards = deckDao.getDeckCards(deckId)
        assertEquals(setOf("new-card-1", "new-card-2"), cards.map { it.scryfallId }.toSet())
        assertEquals(200L, deckDao.getDeckById(deckId)?.updatedAt)
    }

    @Test
    fun failurePath_midTransactionFkViolation_rollsBackEverything_deckLeftUntouched() {
        val deckId = "deck-1"
        deckDao.upsertDeck(makeDeck(deckId, updatedAt = 100L))
        deckDao.upsertDeckCard(DeckCardEntity(deckId = deckId, scryfallId = "pre-existing-card", quantity = 1))

        var threw = false
        try {
            deckDao.replaceAllCardsWithSource(
                deckId,
                listOf(
                    DeckCardEntity(deckId = deckId, scryfallId = "card-1", quantity = 1),
                    DeckCardEntity(deckId = deckId, scryfallId = "card-2", quantity = 1),
                    // FK violation: no deck with this id exists -- aborts the transaction.
                    DeckCardEntity(deckId = "no-such-deck", scryfallId = "card-3", quantity = 1),
                ),
                updatedAt = 200L,
            )
        } catch (e: Exception) {
            threw = true
        }

        assertTrue("the FK violation must surface as a thrown exception", threw)
        val cards = deckDao.getDeckCards(deckId)
        assertEquals(
            "the pre-existing card must survive -- the clear + all prior inserts rolled back with the failing one",
            setOf("pre-existing-card"),
            cards.map { it.scryfallId }.toSet(),
        )
        assertEquals(
            "updated_at must NOT be bumped -- the whole transaction, including the touch, rolled back",
            100L,
            deckDao.getDeckById(deckId)?.updatedAt,
        )
    }

    // ── Phase 8, JOB 2: persistWizardBuild spans cards + pin in ONE transaction (renamed from
    //    persistCommanderBuild, Deck Wizard 60-card wave v6, plan §5 Phase 1.3 -- pure rename) ────

    @Test
    fun happyPath_persistWizardBuild_writesCardsAndPinTogether() = runBlocking {
        val deckId = "deck-pcb-1"
        deckDao.upsertDeck(makeDeck(deckId, updatedAt = 100L))

        deckDao.persistWizardBuild(
            deckId = deckId,
            cards = listOf(DeckCardEntity(deckId = deckId, scryfallId = "commander-1", quantity = 1, source = "WIZARD")),
            archetypeOverride = "AGGRO",
            themesOverrideJson = null,
            postureOverride = null,
            tribeOverride = "tribe:Goblin",
            strategyLocked = true,
            updatedAt = 200L,
        )

        val cards = deckDao.getDeckCards(deckId)
        assertEquals(setOf("commander-1"), cards.map { it.scryfallId }.toSet())
        val deck = deckDao.getDeckById(deckId)
        assertEquals("AGGRO", deck?.archetypeOverride)
        assertEquals("tribe:Goblin", deck?.tribeOverride)
        assertEquals(true, deck?.strategyLocked)
        assertEquals(200L, deck?.updatedAt)
    }

    @Test
    fun failurePath_persistWizardBuild_midTransactionFkViolation_rollsBackCardsAndPinTogether() = runBlocking {
        val deckId = "deck-pcb-2"
        deckDao.upsertDeck(makeDeck(deckId, updatedAt = 100L))
        deckDao.upsertDeckCard(DeckCardEntity(deckId = deckId, scryfallId = "pre-existing-card", quantity = 1))

        var threw = false
        try {
            deckDao.persistWizardBuild(
                deckId = deckId,
                cards = listOf(
                    DeckCardEntity(deckId = deckId, scryfallId = "commander-1", quantity = 1, source = "WIZARD"),
                    // FK violation: no deck with this id exists -- aborts the whole transaction,
                    // including the pin/tribe/strategyLocked writes below it in the method body.
                    DeckCardEntity(deckId = "no-such-deck", scryfallId = "commander-2", quantity = 1),
                ),
                archetypeOverride = "AGGRO",
                themesOverrideJson = null,
                postureOverride = null,
                tribeOverride = "tribe:Goblin",
                strategyLocked = true,
                updatedAt = 200L,
            )
        } catch (e: Exception) {
            threw = true
        }

        assertTrue("the FK violation must surface as a thrown exception", threw)
        val cards = deckDao.getDeckCards(deckId)
        assertEquals(
            "the pre-existing card must survive -- cards AND pin roll back together, never cards-only",
            setOf("pre-existing-card"),
            cards.map { it.scryfallId }.toSet(),
        )
        val deck = deckDao.getDeckById(deckId)
        assertEquals("a rolled-back write must NOT leave a stale archetype pin", null, deck?.archetypeOverride)
        assertEquals("a rolled-back write must NOT leave a stale tribe pin", null, deck?.tribeOverride)
        assertEquals("a rolled-back write must NOT leave a stale strategyLocked flag", false, deck?.strategyLocked)
        assertEquals(100L, deck?.updatedAt)
    }

    // ── Run 4a (F2): commanderCardId/coverCardId ride the SAME transaction as the cards ────────

    @Test
    fun happyPath_persistWizardBuild_writesCommanderAndCoverWithTheCards() = runBlocking {
        val deckId = "deck-pcb-3"
        deckDao.upsertDeck(makeDeck(deckId, updatedAt = 100L))

        deckDao.persistWizardBuild(
            deckId = deckId,
            cards = listOf(DeckCardEntity(deckId = deckId, scryfallId = "commander-1", quantity = 1, source = "WIZARD")),
            archetypeOverride = null,
            themesOverrideJson = null,
            postureOverride = null,
            tribeOverride = null,
            strategyLocked = false,
            commanderCardId = "commander-1",
            updatedAt = 200L,
        )

        val deck = deckDao.getDeckById(deckId)
        assertEquals("commander-1", deck?.commanderCardId)
        assertEquals("commander-1", deck?.coverCardId)
        assertEquals(200L, deck?.updatedAt)
    }

    @Test
    fun failurePath_persistWizardBuild_fkViolation_neverLeavesACommanderOnAnEmptyDeck() = runBlocking {
        val deckId = "deck-pcb-4"
        deckDao.upsertDeck(makeDeck(deckId, updatedAt = 100L))

        var threw = false
        try {
            deckDao.persistWizardBuild(
                deckId = deckId,
                cards = listOf(
                    DeckCardEntity(deckId = deckId, scryfallId = "commander-1", quantity = 1, source = "WIZARD"),
                    DeckCardEntity(deckId = "no-such-deck", scryfallId = "commander-2", quantity = 1),
                ),
                archetypeOverride = null,
                themesOverrideJson = null,
                postureOverride = null,
                tribeOverride = null,
                strategyLocked = false,
                commanderCardId = "commander-1",
                updatedAt = 200L,
            )
        } catch (e: Exception) {
            threw = true
        }

        assertTrue("the FK violation must surface as a thrown exception", threw)
        assertTrue(deckDao.getDeckCards(deckId).isEmpty())
        val deck = deckDao.getDeckById(deckId)
        assertEquals("a rolled-back build must NOT leave a commander set on a card-less deck", null, deck?.commanderCardId)
        assertEquals(null, deck?.coverCardId)
        assertEquals(100L, deck?.updatedAt)
    }
}
