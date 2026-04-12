package com.mmg.magicfolder.core.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mmg.magicfolder.core.data.local.MtgDatabase
import com.mmg.magicfolder.core.data.local.entity.CardEntity
import com.mmg.magicfolder.core.data.local.entity.UserCardEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented integration tests for [CardDao] and [UserCardDao] using a real
 * Room in-memory database.
 *
 * These tests exercise the SQLite FK constraints and transaction semantics that
 * cannot be verified with unit tests and mocks:
 *
 * 1. **Regression: upsert must NOT delete user collection entries.**
 *    Before the fix, [CardDao.upsert] used OnConflictStrategy.REPLACE which
 *    internally issues DELETE + INSERT. Combined with the CASCADE FK on
 *    user_cards.scryfall_id, this silently deleted every UserCardEntity that
 *    referenced the refreshed card.
 *
 * 2. **RESTRICT FK prevents silent data loss.**
 *    Attempting to DELETE a CardEntity that still has UserCardEntity rows
 *    referencing it must throw immediately instead of cascading silently.
 *
 * 3. **insertOrIncrement atomicity.**
 *    Inserting the same unique combination twice must increment quantity,
 *    not create a duplicate row.
 */
@RunWith(AndroidJUnit4::class)
class CardDaoCascadeTest {

    private lateinit var db: MtgDatabase
    private lateinit var cardDao: CardDao
    private lateinit var userCardDao: UserCardDao

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Before
    fun createDb() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MtgDatabase::class.java,
        )
            // Enable FK enforcement — SQLite disables it by default.
            .addCallback(object : androidx.room.RoomDatabase.Callback() {
                override fun onOpen(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL("PRAGMA foreign_keys = ON")
                }
            })
            .allowMainThreadQueries()
            .build()

        cardDao    = db.cardDao()
        userCardDao = db.userCardDao()
    }

    @After
    fun closeDb() = db.close()

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun aCard(scryfallId: String = "card-001", priceUsd: Double? = 1.0) = CardEntity(
        scryfallId        = scryfallId,
        name              = "Lightning Bolt",
        printedName       = null,
        lang              = "en",
        manaCost          = "{R}",
        cmc               = 1.0,
        colors            = "[\"R\"]",
        colorIdentity     = "[\"R\"]",
        typeLine          = "Instant",
        printedTypeLine   = null,
        oracleText        = "Lightning Bolt deals 3 damage to any target.",
        printedText       = null,
        keywords          = "[]",
        power             = null,
        toughness         = null,
        loyalty           = null,
        setCode           = "lea",
        setName           = "Limited Edition Alpha",
        collectorNumber   = "161",
        rarity            = "common",
        releasedAt        = "1993-08-05",
        frameEffects      = "[]",
        promoTypes        = "[]",
        imageNormal       = "https://img.scryfall.com/card.jpg",
        imageArtCrop      = null,
        imageBackNormal   = null,
        priceUsd          = priceUsd,
        priceUsdFoil      = null,
        priceEur          = null,
        priceEurFoil      = null,
        legalityStandard  = "not_legal",
        legalityPioneer   = "legal",
        legalityModern    = "legal",
        legalityCommander = "legal",
        flavorText        = null,
        artist            = "Christopher Rush",
        scryfallUri       = "https://scryfall.com/card/lea/161",
        cachedAt          = System.currentTimeMillis(),
        isStale           = false,
        staleReason       = null,
        tags              = "[]",
        userTags          = "[]",
        suggestedTags     = "[]",
    )

    private fun aUserCard(
        scryfallId: String  = "card-001",
        isFoil:     Boolean = false,
        condition:  String  = "NM",
        language:   String  = "en",
    ) = UserCardEntity(
        scryfallId       = scryfallId,
        quantity         = 1,
        isFoil           = isFoil,
        isAlternativeArt = false,
        condition        = condition,
        language         = language,
        isForTrade       = false,
        isInWishlist     = false,
        addedAt          = System.currentTimeMillis(),
    )

    // ── Regression: upsert must NOT cascade-delete user collection entries ────

    /**
     * Core regression test.
     *
     * Reproduces the exact scenario that caused the bug:
     * 1. User has a card in their collection (UserCardEntity exists).
     * 2. refreshCollectionPrices() calls cardDao.upsert() with fresh data for
     *    that same card (simulating a Scryfall price update).
     * 3. The UserCardEntity must still be present after the upsert.
     *
     * Before the fix, the INSERT OR REPLACE strategy issued DELETE + INSERT on
     * the `cards` table, and the ForeignKey.CASCADE on `user_cards` silently
     * deleted the user's collection entry.
     */
    @Test
    fun upsertCard_doesNotDeleteUserCollectionEntries() = runTest {
        // Arrange — card is cached and user has it in their collection.
        cardDao.upsert(aCard(priceUsd = 1.00))
        userCardDao.insertOrIncrement(aUserCard())

        val beforeUpsert = userCardDao.getById(1L)
        assertNotNull("Precondition: user card must exist before upsert", beforeUpsert)

        // Act — simulate refreshCollectionPrices() updating the card metadata.
        cardDao.upsert(aCard(priceUsd = 2.50))

        // Assert — user collection entry is unaffected.
        val afterUpsert = userCardDao.getById(1L)
        assertNotNull(
            "UserCardEntity must still exist after cardDao.upsert() — CASCADE-delete regression",
            afterUpsert,
        )
        assertEquals(1, afterUpsert!!.quantity)
    }

    /**
     * Multiple user cards (foil + non-foil) for the same scryfall_id must all
     * survive a card metadata upsert.
     */
    @Test
    fun upsertCard_doesNotDeleteMultipleUserEntriesForSameCard() = runTest {
        cardDao.upsert(aCard())
        userCardDao.insertOrIncrement(aUserCard(isFoil = false))
        userCardDao.insertOrIncrement(aUserCard(isFoil = true))

        // Act
        cardDao.upsert(aCard(priceUsd = 5.00))

        // Assert — both copies survive.
        val nonFoil = userCardDao.getById(1L)
        val foil    = userCardDao.getById(2L)
        assertNotNull("Non-foil copy must survive upsert", nonFoil)
        assertNotNull("Foil copy must survive upsert", foil)
    }

    /**
     * upsert() with updated fields must actually persist the new values.
     * Verifies that the UPDATE branch of the safe upsert works.
     */
    @Test
    fun upsertCard_updatesExistingCardFields() = runTest {
        cardDao.upsert(aCard(priceUsd = 1.00))

        // Act — same card, new price.
        cardDao.upsert(aCard(priceUsd = 9.99))

        val updated = cardDao.getById("card-001")
        assertNotNull(updated)
        assertEquals(9.99, updated!!.priceUsd!!, 0.001)
    }

    /**
     * upsertAll() must not delete user collection entries either.
     * This path is used by refreshCollectionPrices() after the N+1 fix.
     */
    @Test
    fun upsertAllCards_doesNotDeleteUserCollectionEntries() = runTest {
        cardDao.upsert(aCard("card-001"))
        cardDao.upsert(aCard("card-002"))
        userCardDao.insertOrIncrement(aUserCard("card-001"))
        userCardDao.insertOrIncrement(aUserCard("card-002"))

        // Act — batch upsert both cards (simulates refreshCollectionPrices).
        cardDao.upsertAll(listOf(
            aCard("card-001", priceUsd = 3.00),
            aCard("card-002", priceUsd = 7.00),
        ))

        // Assert — both user cards survive.
        assertNotNull("card-001 user entry must survive upsertAll", userCardDao.getById(1L))
        assertNotNull("card-002 user entry must survive upsertAll", userCardDao.getById(2L))
    }

    // ── RESTRICT FK: silent deletion replaced by explicit exception ────────────

    /**
     * Verifies that the ForeignKey.RESTRICT constraint makes any attempt to
     * directly delete a referenced CardEntity fail loudly instead of silently
     * cascade-deleting the user's collection.
     *
     * This is the safety net that prevents future regressions even if new code
     * accidentally re-introduces a DELETE on the `cards` table.
     */
    @Test
    fun deleteCard_withActiveUserCardReference_throwsConstraintException() = runTest {
        cardDao.upsert(aCard())
        userCardDao.insertOrIncrement(aUserCard())

        try {
            // This direct delete must be blocked by RESTRICT.
            db.openHelper.writableDatabase.execSQL(
                "DELETE FROM cards WHERE scryfall_id = 'card-001'"
            )
            fail("Expected SQLiteConstraintException — RESTRICT FK should block the delete")
        } catch (e: android.database.sqlite.SQLiteConstraintException) {
            // Expected: RESTRICT raised an exception, user data is safe.
        }

        // The user card must still be intact.
        assertNotNull(userCardDao.getById(1L))
    }

    /**
     * Deleting a CardEntity that has NO user_card references is allowed —
     * RESTRICT only blocks deletes that would orphan existing rows.
     */
    @Test
    fun deleteCard_withNoUserCardReference_succeeds() = runTest {
        cardDao.upsert(aCard())
        // No user card inserted for this card.

        db.openHelper.writableDatabase.execSQL(
            "DELETE FROM cards WHERE scryfall_id = 'card-001'"
        )

        assertNull(cardDao.getById("card-001"))
    }

    // ── insertOrIncrement atomicity ───────────────────────────────────────────

    /**
     * Inserting the same card variant twice must result in a single row
     * with quantity = 2, not two separate rows.
     */
    @Test
    fun insertOrIncrement_duplicateVariant_incrementsQuantity() = runTest {
        cardDao.upsert(aCard())
        val entry = aUserCard(isFoil = false)

        userCardDao.insertOrIncrement(entry)
        userCardDao.insertOrIncrement(entry)  // same unique key

        val row = userCardDao.getById(1L)
        assertNotNull(row)
        assertEquals("Quantity should be 2 after two insertOrIncrement calls", 2, row!!.quantity)

        // Verify there is no phantom second row.
        assertNull("No second row should exist", userCardDao.getById(2L))
    }

    /**
     * Foil and non-foil are different variants and must create separate rows.
     */
    @Test
    fun insertOrIncrement_foilAndNonFoil_createsSeparateRows() = runTest {
        cardDao.upsert(aCard())

        userCardDao.insertOrIncrement(aUserCard(isFoil = false))
        userCardDao.insertOrIncrement(aUserCard(isFoil = true))

        val nonFoil = userCardDao.getById(1L)
        val foil    = userCardDao.getById(2L)
        assertNotNull("Non-foil row must exist", nonFoil)
        assertNotNull("Foil row must exist", foil)
        assertEquals(false, nonFoil!!.isFoil)
        assertEquals(true,  foil!!.isFoil)
        assertEquals(1, nonFoil.quantity)
        assertEquals(1, foil.quantity)
    }

    /**
     * Collection entry and wishlist entry for the same card+attributes are
     * distinct rows (is_in_wishlist is part of the unique index).
     */
    @Test
    fun insertOrIncrement_collectionAndWishlist_createsSeparateRows() = runTest {
        cardDao.upsert(aCard())

        val collectionEntry = UserCardEntity(
            scryfallId = "card-001", isFoil = false, isAlternativeArt = false,
            condition = "NM", language = "en", isInWishlist = false,
            addedAt = System.currentTimeMillis(),
        )
        val wishlistEntry = collectionEntry.copy(isInWishlist = true)

        userCardDao.insertOrIncrement(collectionEntry)
        userCardDao.insertOrIncrement(wishlistEntry)

        assertNotNull("Collection row must exist", userCardDao.getById(1L))
        assertNotNull("Wishlist row must exist",   userCardDao.getById(2L))
    }

    // ── FK enforcement is active ───────────────────────────────────────────────

    /**
     * Inserting a UserCardEntity that references a non-existent CardEntity
     * must fail (FK constraint violation).
     */
    @Test
    fun insertUserCard_withMissingCardReference_throwsConstraintException() = runTest {
        // No CardEntity inserted for "ghost-card".
        try {
            userCardDao.insertOrIncrement(aUserCard(scryfallId = "ghost-card"))
            fail("Expected SQLiteConstraintException — FK to cards must be enforced")
        } catch (e: android.database.sqlite.SQLiteConstraintException) {
            // Expected: cannot insert user card without a parent card entity.
        }
    }
}
