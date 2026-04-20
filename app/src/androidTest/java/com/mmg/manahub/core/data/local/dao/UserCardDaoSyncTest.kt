package com.mmg.manahub.core.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.mmg.manahub.core.data.local.MtgDatabase
import com.mmg.manahub.core.data.local.entity.CardEntity
import com.mmg.manahub.core.data.local.entity.SyncStatus
import com.mmg.manahub.core.data.local.entity.UserCardEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented Room tests for the sync-related methods added to [UserCardDao].
 *
 * Uses an in-memory database with FK enforcement enabled (same pattern as [CardDaoCascadeTest]).
 *
 * Covers:
 *  - insertOrIncrement: new card has PENDING_UPLOAD, existing card stays PENDING_UPLOAD
 *  - markAsSynced: sets syncStatus = SYNCED and persists remoteId
 *  - markPendingUpload: sets syncStatus = PENDING_UPLOAD even when row was SYNCED
 *  - getPendingUpload: returns only rows with sync_status = 1
 *  - observePendingCount: emits correct count, updates when rows are synced
 *  - getByRemoteId: returns the correct row
 *  - insertOrReplace: overwrites an existing row and preserves the new syncStatus / remoteId
 */
@RunWith(AndroidJUnit4::class)
class UserCardDaoSyncTest {

    private lateinit var db:          MtgDatabase
    private lateinit var cardDao:     CardDao
    private lateinit var userCardDao: UserCardDao

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Before
    fun createDb() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MtgDatabase::class.java,
        )
            .addCallback(object : androidx.room.RoomDatabase.Callback() {
                override fun onOpen(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL("PRAGMA foreign_keys = ON")
                }
            })
            .allowMainThreadQueries()
            .build()

        cardDao     = db.cardDao()
        userCardDao = db.userCardDao()
    }

    @After
    fun closeDb() = db.close()

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private fun aCard(scryfallId: String = "card-001") = CardEntity(
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
        priceUsd          = 1.0,
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
        syncStatus: Int     = SyncStatus.PENDING_UPLOAD,
        remoteId:   String? = null,
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
        syncStatus       = syncStatus,
        remoteId         = remoteId,
    )

    // ── Precondition helper ───────────────────────────────────────────────────

    /**
     * Inserts a parent [CardEntity] and then inserts [userCard] via [insertOrIncrement].
     * Returns the auto-generated row id of the inserted user card (always 1 for first insert).
     */
    private suspend fun seedCard(
        scryfallId: String = "card-001",
        userCard:   UserCardEntity = aUserCard(scryfallId = scryfallId),
    ): Long {
        cardDao.upsert(aCard(scryfallId = scryfallId))
        userCardDao.insertOrIncrement(userCard)
        return userCardDao.getById(1L)?.id ?: 1L
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 1 — insertOrIncrement: sync_status after insert
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun insertOrIncrement_newCard_hasPendingUploadSyncStatus() = runTest {
        // Arrange
        cardDao.upsert(aCard())

        // Act
        userCardDao.insertOrIncrement(aUserCard())

        // Assert: default syncStatus for a new row is PENDING_UPLOAD
        val row = userCardDao.getById(1L)
        assertNotNull(row)
        assertEquals(SyncStatus.PENDING_UPLOAD, row!!.syncStatus)
    }

    @Test
    fun insertOrIncrement_newCard_hasNullRemoteId() = runTest {
        // A freshly inserted card was never pushed to Supabase yet
        cardDao.upsert(aCard())
        userCardDao.insertOrIncrement(aUserCard())

        val row = userCardDao.getById(1L)
        assertNotNull(row)
        assertNull(row!!.remoteId)
    }

    @Test
    fun insertOrIncrement_existingCard_remainsPendingUpload() = runTest {
        // Arrange: first insert
        cardDao.upsert(aCard())
        userCardDao.insertOrIncrement(aUserCard())

        // Act: insert same unique key again — triggers increment path
        userCardDao.insertOrIncrement(aUserCard())

        // Assert: sync status is still PENDING_UPLOAD after the increment
        val row = userCardDao.getById(1L)
        assertNotNull(row)
        assertEquals(SyncStatus.PENDING_UPLOAD, row!!.syncStatus)
    }

    @Test
    fun insertOrIncrement_existingCard_incrementsQuantity() = runTest {
        // Quantity increases — separate concern from sync status but important to verify here
        cardDao.upsert(aCard())
        userCardDao.insertOrIncrement(aUserCard())
        userCardDao.insertOrIncrement(aUserCard())   // same unique key

        val row = userCardDao.getById(1L)
        assertNotNull(row)
        assertEquals(2, row!!.quantity)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 2 — markAsSynced
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun markAsSynced_setsSyncStatusToSynced() = runTest {
        // Arrange
        seedCard()

        // Act
        userCardDao.markAsSynced(1L, "remote-uuid-abc")

        // Assert
        val row = userCardDao.getById(1L)
        assertNotNull(row)
        assertEquals(SyncStatus.SYNCED, row!!.syncStatus)
    }

    @Test
    fun markAsSynced_persists_remoteId() = runTest {
        // Arrange
        seedCard()
        val expectedRemoteId = "remote-uuid-xyz"

        // Act
        userCardDao.markAsSynced(1L, expectedRemoteId)

        // Assert
        val row = userCardDao.getById(1L)
        assertNotNull(row)
        assertEquals(expectedRemoteId, row!!.remoteId)
    }

    @Test
    fun markAsSynced_doesNotAffectOtherRows() = runTest {
        // Arrange: two cards
        cardDao.upsert(aCard("card-001"))
        cardDao.upsert(aCard("card-002"))
        userCardDao.insertOrIncrement(aUserCard("card-001"))
        userCardDao.insertOrIncrement(aUserCard("card-002"))

        // Act: only mark row 1
        userCardDao.markAsSynced(1L, "remote-1")

        // Assert: row 2 is still PENDING_UPLOAD
        val row2 = userCardDao.getById(2L)
        assertNotNull(row2)
        assertEquals(SyncStatus.PENDING_UPLOAD, row2!!.syncStatus)
        assertNull(row2.remoteId)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 3 — markPendingUpload
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun markPendingUpload_whenRowIsSynced_setsStatusToPendingUpload() = runTest {
        // Arrange: start as SYNCED
        cardDao.upsert(aCard())
        userCardDao.insertOrIncrement(aUserCard(syncStatus = SyncStatus.SYNCED))
        val initialRow = userCardDao.getById(1L)
        assertNotNull(initialRow)
        assertEquals(
            "Precondition: row must start as SYNCED",
            SyncStatus.SYNCED,
            initialRow!!.syncStatus
        )

        // Act
        userCardDao.markPendingUpload(1L)

        // Assert
        val updatedRow = userCardDao.getById(1L)
        assertNotNull(updatedRow)
        assertEquals(SyncStatus.PENDING_UPLOAD, updatedRow!!.syncStatus)
    }

    @Test
    fun markPendingUpload_whenRowAlreadyPending_remainsPending() = runTest {
        // Idempotency: calling markPendingUpload twice has no negative side-effects
        seedCard()
        userCardDao.markPendingUpload(1L)
        userCardDao.markPendingUpload(1L)

        val row = userCardDao.getById(1L)
        assertEquals(SyncStatus.PENDING_UPLOAD, row!!.syncStatus)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 4 — getPendingUpload
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun getPendingUpload_returnsOnlyPendingRows() = runTest {
        // Arrange: one pending, one synced
        cardDao.upsert(aCard("card-001"))
        cardDao.upsert(aCard("card-002"))
        userCardDao.insertOrIncrement(aUserCard("card-001"))              // PENDING_UPLOAD
        userCardDao.insertOrIncrement(aUserCard("card-002"))
        userCardDao.markAsSynced(2L, "remote-2")                         // SYNCED

        // Act
        val pending = userCardDao.getPendingUpload()

        // Assert: only card-001 should be returned
        assertEquals(1, pending.size)
        assertEquals("card-001", pending.first().scryfallId)
    }

    @Test
    fun getPendingUpload_whenNoPendingRows_returnsEmptyList() = runTest {
        // Arrange: insert and immediately mark as synced
        seedCard()
        userCardDao.markAsSynced(1L, "remote-abc")

        // Act
        val pending = userCardDao.getPendingUpload()

        // Assert
        assertEquals(0, pending.size)
    }

    @Test
    fun getPendingUpload_afterMarkingAllAsSynced_returnsEmptyList() = runTest {
        // Arrange: three pending rows
        cardDao.upsert(aCard("card-001"))
        cardDao.upsert(aCard("card-002"))
        cardDao.upsert(aCard("card-003"))
        userCardDao.insertOrIncrement(aUserCard("card-001"))
        userCardDao.insertOrIncrement(aUserCard("card-002"))
        userCardDao.insertOrIncrement(aUserCard("card-003"))
        userCardDao.markAsSynced(1L, "r-1")
        userCardDao.markAsSynced(2L, "r-2")
        userCardDao.markAsSynced(3L, "r-3")

        // Act
        val pending = userCardDao.getPendingUpload()

        // Assert
        assertEquals(0, pending.size)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 5 — observePendingCount
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun observePendingCount_emitsCorrectInitialCount() = runTest {
        // Arrange: two pending rows
        cardDao.upsert(aCard("card-001"))
        cardDao.upsert(aCard("card-002"))
        userCardDao.insertOrIncrement(aUserCard("card-001"))
        userCardDao.insertOrIncrement(aUserCard("card-002"))

        // Act / Assert using Turbine
        userCardDao.observePendingCount().test {
            val count = awaitItem()
            assertEquals(2, count)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun observePendingCount_updatesWhenRowIsMarkedSynced() = runTest {
        // Arrange: one pending row
        seedCard()

        userCardDao.observePendingCount().test {
            // Initial state: 1 pending
            assertEquals(1, awaitItem())

            // Act: mark as synced
            userCardDao.markAsSynced(1L, "remote-abc")

            // Assert: count drops to 0
            assertEquals(0, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun observePendingCount_whenNoPendingRows_emitsZero() = runTest {
        // No rows in the database at all
        userCardDao.observePendingCount().test {
            assertEquals(0, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun observePendingCount_incrementsWhenNewPendingRowInserted() = runTest {
        // Arrange: start with empty DB
        userCardDao.observePendingCount().test {
            assertEquals(0, awaitItem())

            // Act: add a card
            cardDao.upsert(aCard())
            userCardDao.insertOrIncrement(aUserCard())

            // Assert: count is now 1
            assertEquals(1, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 6 — getByRemoteId
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun getByRemoteId_returnsCorrectRow() = runTest {
        // Arrange: insert, mark as synced (assigns remoteId)
        seedCard()
        val expectedRemoteId = "remote-unique-123"
        userCardDao.markAsSynced(1L, expectedRemoteId)

        // Act
        val row = userCardDao.getByRemoteId(expectedRemoteId)

        // Assert
        assertNotNull(row)
        assertEquals(1L, row!!.id)
        assertEquals(expectedRemoteId, row.remoteId)
    }

    @Test
    fun getByRemoteId_whenNoMatchingRow_returnsNull() = runTest {
        // Arrange: no rows
        // Act
        val row = userCardDao.getByRemoteId("non-existent-remote-id")

        // Assert
        assertNull(row)
    }

    @Test
    fun getByRemoteId_withMultipleRows_returnsOnlyMatchingRow() = runTest {
        // Arrange: two synced rows with different remoteIds
        cardDao.upsert(aCard("card-001"))
        cardDao.upsert(aCard("card-002"))
        userCardDao.insertOrIncrement(aUserCard("card-001"))
        userCardDao.insertOrIncrement(aUserCard("card-002"))
        userCardDao.markAsSynced(1L, "remote-A")
        userCardDao.markAsSynced(2L, "remote-B")

        // Act
        val row = userCardDao.getByRemoteId("remote-B")

        // Assert
        assertNotNull(row)
        assertEquals(2L, row!!.id)
        assertEquals("card-002", row.scryfallId)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 7 — insertOrReplace (pull sync path)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun insertOrReplace_newRow_canBeRetrievedWithCorrectSyncStatus() = runTest {
        // Arrange: parent card must exist (FK)
        cardDao.upsert(aCard("card-001"))

        val entity = aUserCard(
            scryfallId = "card-001",
            syncStatus = SyncStatus.SYNCED,
            remoteId   = "remote-abc",
        )

        // Act
        userCardDao.insertOrReplace(entity)

        // Assert: row is stored and can be queried by remoteId
        val stored = userCardDao.getByRemoteId("remote-abc")
        assertNotNull(stored)
        assertEquals(SyncStatus.SYNCED, stored!!.syncStatus)
        assertEquals("remote-abc", stored.remoteId)
    }

    @Test
    fun insertOrReplace_existingRow_overwritesWithNewValues() = runTest {
        // Arrange: first insert via insertOrIncrement, then overwrite via insertOrReplace
        cardDao.upsert(aCard("card-001"))
        userCardDao.insertOrIncrement(aUserCard("card-001", syncStatus = SyncStatus.PENDING_UPLOAD))
        val existingId = userCardDao.getById(1L)!!.id

        val updated = UserCardEntity(
            id         = existingId,     // same PK → REPLACE triggers
            scryfallId = "card-001",
            quantity   = 99,
            isFoil     = false,
            isAlternativeArt = false,
            condition  = "NM",
            language   = "en",
            addedAt    = System.currentTimeMillis(),
            syncStatus = SyncStatus.SYNCED,
            remoteId   = "remote-xyz",
        )

        // Act
        userCardDao.insertOrReplace(updated)

        // Assert
        val stored = userCardDao.getById(existingId)
        assertNotNull(stored)
        assertEquals(99, stored!!.quantity)
        assertEquals(SyncStatus.SYNCED, stored.syncStatus)
        assertEquals("remote-xyz", stored.remoteId)
    }

    @Test
    fun insertOrReplace_syncedRow_doesNotAppearInGetPendingUpload() = runTest {
        // A row inserted via the pull path (SYNCED) must not be queued for re-upload
        cardDao.upsert(aCard("card-001"))
        val entity = aUserCard(syncStatus = SyncStatus.SYNCED, remoteId = "remote-001")
        userCardDao.insertOrReplace(entity)

        val pending = userCardDao.getPendingUpload()
        assertEquals(0, pending.size)
    }
}
