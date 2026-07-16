package com.mmg.manahub.core.data.repository

import androidx.room.withTransaction
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.core.data.local.MtgDatabase
import com.mmg.manahub.core.data.local.dao.LocalOpenForTradeDao
import com.mmg.manahub.core.data.local.dao.UserCardCollectionDao
import com.mmg.manahub.core.data.local.entity.LocalOpenForTradeEntity
import com.mmg.manahub.core.data.local.entity.UserCardCollectionEntity
import com.mmg.manahub.core.data.local.paging.RemoteKeyDao
import com.mmg.manahub.core.data.remote.collection.CollectionRemoteDataSource
import com.mmg.manahub.core.domain.auth.AuthRepository
import com.mmg.manahub.core.domain.repository.UpdateEntryOutcome
import io.github.jan.supabase.SupabaseClient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [UserCardRepositoryImpl.updateEntryWithMerge] (Card Versions & Languages,
 * Phase 1A) — kept in a SEPARATE test class from [UserCardRepositoryImplTest] per the
 * android-unit-test-writer instructions: that file has 8 pre-existing `addOrIncrement`
 * failures on master unrelated to this feature, and this suite must stay isolated from them.
 *
 * `database.withTransaction { }` is mocked via `mockkStatic("androidx.room.RoomDatabaseKt")` so
 * the lambda body actually executes against the mocked DAOs (Room's real `withTransaction`
 * extension cannot run against a MockK proxy).
 *
 * Covers:
 *  - live-collision merge (survivor quantity summed, edited row soft-deleted, no third row)
 *  - soft-deleted-revive branch (target tuple reused instead of duplicated)
 *  - in-place branch (attrs rewritten in place, updatedAt bumped)
 *  - entryId not found -> ENTRY_NOT_FOUND, no writes
 *  - self-merge guard (editing an entry to its own current tuple never "merges" with itself)
 *  - open-for-trade re-pointing/merging/capping across all three branches
 */
class UserCardRepositoryImplUpdateEntryTest {

    // ── Mocks ─────────────────────────────────────────────────────────────────

    private val userCardCollectionDao      = mockk<UserCardCollectionDao>(relaxed = true)
    private val collectionRemoteDataSource = mockk<CollectionRemoteDataSource>(relaxed = true)
    private val remoteKeyDao               = mockk<RemoteKeyDao>(relaxed = true)
    private val database                   = mockk<MtgDatabase>(relaxed = true)
    private val supabaseClient             = mockk<SupabaseClient>(relaxed = true)
    private val authRepository             = mockk<AuthRepository>(relaxed = true)
    private val localOpenForTradeDao       = mockk<LocalOpenForTradeDao>(relaxed = true)

    private lateinit var repository: UserCardRepositoryImpl

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private fun buildEntity(
        id:         String  = "entity-uuid-001",
        scryfallId: String  = "card-001",
        userId:     String? = "user-uuid-001",
        quantity:   Int     = 1,
        isFoil:     Boolean = false,
        condition:  String  = "NM",
        language:   String  = "en",
        isForTrade: Boolean = false,
        isDeleted:  Boolean = false,
        updatedAt:  Long    = 1_000L,
        createdAt:  Long    = 1_000L,
    ) = UserCardCollectionEntity(
        id         = id,
        userId     = userId,
        scryfallId = scryfallId,
        quantity   = quantity,
        isFoil     = isFoil,
        condition  = condition,
        language   = language,
        isForTrade = isForTrade,
        isDeleted  = isDeleted,
        updatedAt  = updatedAt,
        createdAt  = createdAt,
    )

    private fun buildOffer(
        id:                String  = "offer-001",
        localCollectionId: String  = "entity-uuid-001",
        scryfallId:        String  = "card-001",
        quantity:          Int     = 1,
        isFoil:            Boolean = false,
        condition:         String  = "NM",
        language:          String  = "en",
        synced:            Boolean = true,
    ) = LocalOpenForTradeEntity(
        id                = id,
        localCollectionId = localCollectionId,
        scryfallId        = scryfallId,
        quantity          = quantity,
        isFoil            = isFoil,
        condition         = condition,
        language          = language,
        synced            = synced,
    )

    // ── Setup ─────────────────────────────────────────────────────────────────

    @Before
    fun setUp() {
        mockkStatic(FirebaseCrashlytics::class)
        val crashlytics = mockk<FirebaseCrashlytics>(relaxed = true)
        every { FirebaseCrashlytics.getInstance() } returns crashlytics

        // database.withTransaction { } is a suspend extension function on RoomDatabase
        // (compiled into androidx.room.RoomDatabaseKt) — Room's real implementation can't run
        // against a MockK proxy, so it's mocked to simply invoke the passed lambda directly,
        // making the mocked `database` behave transactionally for test purposes.
        mockkStatic("androidx.room.RoomDatabaseKt")
        val block = slot<suspend () -> Any?>()
        coEvery { database.withTransaction<Any?>(capture(block)) } coAnswers { block.captured.invoke() }

        // No offer linked by default; individual tests override this per-collection-id as needed.
        coEvery { localOpenForTradeDao.getByCollectionId(any()) } returns null

        repository = UserCardRepositoryImpl(
            userCardCollectionDao      = userCardCollectionDao,
            collectionRemoteDataSource = collectionRemoteDataSource,
            remoteKeyDao               = remoteKeyDao,
            database                   = database,
            supabaseClient             = supabaseClient,
            authRepository             = authRepository,
            localOpenForTradeDao       = localOpenForTradeDao,
            ioDispatcher               = UnconfinedTestDispatcher(),
        )
    }

    @After
    fun tearDown() {
        unmockkStatic(FirebaseCrashlytics::class)
        unmockkStatic("androidx.room.RoomDatabaseKt")
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 1 — entryId not found
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given entryId does not exist when updateEntryWithMerge then returns ENTRY_NOT_FOUND and performs no writes`() = runTest {
        every { userCardCollectionDao.getById("missing-id") } returns null

        val outcome = repository.updateEntryWithMerge(
            entryId       = "missing-id",
            newScryfallId = "card-002",
            isFoil        = false,
            condition     = "NM",
            language      = "en",
            quantity      = 1,
            userId        = "user-uuid-001",
        )

        assertEquals(UpdateEntryOutcome.ENTRY_NOT_FOUND, outcome)
        verify(exactly = 0) { userCardCollectionDao.upsert(any()) }
        verify(exactly = 0) { userCardCollectionDao.softDelete(any(), any()) }
        coVerifyNoOfferWrites()
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 2 — live-collision merge
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given a live row at the target tuple when updateEntryWithMerge then survivor quantity is summed and edited row is soft-deleted`() = runTest {
        val edited = buildEntity(id = "edit-1", scryfallId = "card-a", userId = "user-1", quantity = 1, condition = "NM", language = "en")
        val survivor = buildEntity(id = "survivor-1", scryfallId = "card-b", userId = "user-1", quantity = 3, isFoil = true, condition = "LP", language = "es", isDeleted = false)

        every { userCardCollectionDao.getById("edit-1") } returns edited
        every {
            userCardCollectionDao.getByCompositeKey("user-1", "card-b", true, "LP", "es")
        } returns survivor

        val outcome = repository.updateEntryWithMerge(
            entryId       = "edit-1",
            newScryfallId = "card-b",
            isFoil        = true,
            condition     = "lp",
            language      = "ES",
            quantity      = 2,
            userId        = "user-1",
        )

        assertEquals(UpdateEntryOutcome.UPDATED, outcome)

        // Survivor's quantity is the SUM (survivor.quantity + edited-new-quantity), never overwritten.
        verify(exactly = 1) {
            userCardCollectionDao.upsert(match { it.id == "survivor-1" && it.quantity == 5 })
        }
        // The edited row is soft-deleted, never re-upserted with the new tuple — no third row.
        verify(exactly = 1) { userCardCollectionDao.softDelete("edit-1", any()) }
        verify(exactly = 0) { userCardCollectionDao.upsert(match { it.id == "edit-1" }) }
        verify(exactly = 1) { userCardCollectionDao.upsert(any()) }
    }

    @Test
    fun `given live merge with no linked open-for-trade offers when updateEntryWithMerge then no offer writes occur`() = runTest {
        val edited = buildEntity(id = "edit-1", scryfallId = "card-a", userId = "user-1", quantity = 1)
        val survivor = buildEntity(id = "survivor-1", scryfallId = "card-b", userId = "user-1", quantity = 3, isDeleted = false)
        every { userCardCollectionDao.getById("edit-1") } returns edited
        every { userCardCollectionDao.getByCompositeKey("user-1", "card-b", false, "NM", "en") } returns survivor
        coEvery { localOpenForTradeDao.getByCollectionId("edit-1") } returns null

        repository.updateEntryWithMerge("edit-1", "card-b", false, "NM", "en", 2, "user-1")

        coVerifyNoOfferWrites()
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 3 — soft-deleted-revive branch
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given a soft-deleted row at the target tuple when updateEntryWithMerge then it is revived live and the edited row is soft-deleted`() = runTest {
        val edited = buildEntity(id = "edit-1", scryfallId = "card-a", userId = "user-1", quantity = 1)
        val deletedSurvivor = buildEntity(id = "revive-1", scryfallId = "card-b", userId = "user-1", quantity = 9, isDeleted = true)

        every { userCardCollectionDao.getById("edit-1") } returns edited
        every {
            userCardCollectionDao.getByCompositeKey("user-1", "card-b", false, "NM", "en")
        } returns deletedSurvivor

        val outcome = repository.updateEntryWithMerge("edit-1", "card-b", false, "NM", "en", 4, "user-1")

        assertEquals(UpdateEntryOutcome.UPDATED, outcome)
        // Revived row: quantity is the NEW quantity (not summed with the stale 9), isDeleted flips to false.
        verify(exactly = 1) {
            userCardCollectionDao.upsert(match { it.id == "revive-1" && it.quantity == 4 && !it.isDeleted })
        }
        verify(exactly = 1) { userCardCollectionDao.softDelete("edit-1", any()) }
        verify(exactly = 0) { userCardCollectionDao.upsert(match { it.id == "edit-1" }) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 4 — in-place branch (no collision)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given no row at the target tuple when updateEntryWithMerge then the edited row is rewritten in place`() = runTest {
        val edited = buildEntity(id = "edit-1", scryfallId = "card-a", userId = "user-1", quantity = 1, isFoil = false, condition = "NM", language = "en", updatedAt = 500L)
        every { userCardCollectionDao.getById("edit-1") } returns edited
        every { userCardCollectionDao.getByCompositeKey("user-1", "card-c", true, "LP", "fr") } returns null

        val before = System.currentTimeMillis()
        val outcome = repository.updateEntryWithMerge("edit-1", "card-c", true, "lp", "FR", 7, "user-1")
        val after = System.currentTimeMillis()

        assertEquals(UpdateEntryOutcome.UPDATED, outcome)
        verify(exactly = 1) {
            userCardCollectionDao.upsert(match {
                it.id == "edit-1" &&
                    it.scryfallId == "card-c" &&
                    it.isFoil &&
                    it.condition == "LP" &&
                    it.language == "fr" &&
                    it.quantity == 7 &&
                    it.updatedAt in before..after
            })
        }
        // No merge/revive side effects.
        verify(exactly = 0) { userCardCollectionDao.softDelete(any(), any()) }
        verify(exactly = 1) { userCardCollectionDao.upsert(any()) }
    }

    @Test
    fun `given editing an entry to its own current tuple when updateEntryWithMerge then it is treated as in-place and never merges with itself`() = runTest {
        val edited = buildEntity(id = "edit-1", scryfallId = "card-a", userId = "user-1", quantity = 1, isFoil = false, condition = "NM", language = "en")
        every { userCardCollectionDao.getById("edit-1") } returns edited
        // The composite-key lookup for the (unchanged) target tuple finds the SAME row being edited.
        every {
            userCardCollectionDao.getByCompositeKey("user-1", "card-a", false, "NM", "en")
        } returns edited

        val outcome = repository.updateEntryWithMerge("edit-1", "card-a", false, "NM", "en", 5, "user-1")

        assertEquals(UpdateEntryOutcome.UPDATED, outcome)
        // Only the in-place update happens: no soft-delete, no summed-quantity merge math.
        verify(exactly = 0) { userCardCollectionDao.softDelete(any(), any()) }
        verify(exactly = 1) {
            userCardCollectionDao.upsert(match { it.id == "edit-1" && it.quantity == 5 })
        }
        verify(exactly = 1) { userCardCollectionDao.upsert(any()) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 5 — open-for-trade re-pointing (merge branch)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given a merge with offers on both rows when updateEntryWithMerge then the survivor offer is summed and capped at the survivor total`() = runTest {
        val edited = buildEntity(id = "edit-1", scryfallId = "card-a", userId = "user-1", quantity = 1)
        val survivor = buildEntity(id = "survivor-1", scryfallId = "card-b", userId = "user-1", quantity = 1, isDeleted = false)
        every { userCardCollectionDao.getById("edit-1") } returns edited
        every { userCardCollectionDao.getByCompositeKey("user-1", "card-b", false, "NM", "en") } returns survivor

        val fromOffer = buildOffer(id = "offer-from", localCollectionId = "edit-1", scryfallId = "card-a", quantity = 3)
        val toOffer = buildOffer(id = "offer-to", localCollectionId = "survivor-1", scryfallId = "card-b", quantity = 2)
        coEvery { localOpenForTradeDao.getByCollectionId("edit-1") } returns fromOffer
        coEvery { localOpenForTradeDao.getByCollectionId("survivor-1") } returns toOffer

        // mergedQuantity (quantityCap) = survivor.quantity(1) + quantity(2) = 3.
        // fromOffer(3) + toOffer(2) = 5, capped at 3.
        repository.updateEntryWithMerge("edit-1", "card-b", false, "NM", "en", 2, "user-1")

        coVerify(exactly = 1) {
            localOpenForTradeDao.upsert(match { it.id == "offer-to" && it.quantity == 3 && !it.synced })
        }
        coVerify(exactly = 1) { localOpenForTradeDao.deleteByCollectionId("edit-1") }
    }

    @Test
    fun `given a merge with an offer only on the edited row when updateEntryWithMerge then the offer is moved wholesale and capped at the survivor total`() = runTest {
        val edited = buildEntity(id = "edit-1", scryfallId = "card-a", userId = "user-1", quantity = 1)
        val survivor = buildEntity(id = "survivor-1", scryfallId = "card-b", userId = "user-1", quantity = 2, isDeleted = false)
        every { userCardCollectionDao.getById("edit-1") } returns edited
        every { userCardCollectionDao.getByCompositeKey("user-1", "card-b", false, "NM", "en") } returns survivor

        val fromOffer = buildOffer(id = "offer-from", localCollectionId = "edit-1", scryfallId = "card-a", quantity = 5)
        coEvery { localOpenForTradeDao.getByCollectionId("edit-1") } returns fromOffer
        coEvery { localOpenForTradeDao.getByCollectionId("survivor-1") } returns null

        // mergedQuantity (quantityCap) = survivor.quantity(2) + quantity(2) = 4. fromOffer(5) capped at 4.
        repository.updateEntryWithMerge("edit-1", "card-b", false, "NM", "en", 2, "user-1")

        coVerify(exactly = 1) {
            localOpenForTradeDao.upsert(match {
                it.id == "offer-from" && it.localCollectionId == "survivor-1" && it.scryfallId == "card-b" && it.quantity == 4 && !it.synced
            })
        }
        coVerify(exactly = 0) { localOpenForTradeDao.deleteByCollectionId(any()) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 6 — open-for-trade re-pointing (revive branch)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given a revive merge with an offer on the edited row when updateEntryWithMerge then the offer moves and is capped at the plain edit quantity`() = runTest {
        val edited = buildEntity(id = "edit-1", scryfallId = "card-a", userId = "user-1", quantity = 1)
        val deletedSurvivor = buildEntity(id = "revive-1", scryfallId = "card-b", userId = "user-1", quantity = 9, isDeleted = true)
        every { userCardCollectionDao.getById("edit-1") } returns edited
        every { userCardCollectionDao.getByCompositeKey("user-1", "card-b", false, "NM", "en") } returns deletedSurvivor

        val fromOffer = buildOffer(id = "offer-from", localCollectionId = "edit-1", scryfallId = "card-a", quantity = 6)
        coEvery { localOpenForTradeDao.getByCollectionId("edit-1") } returns fromOffer
        coEvery { localOpenForTradeDao.getByCollectionId("revive-1") } returns null

        // Revive branch's quantityCap is the plain edit `quantity` (2), NOT a sum with the stale row.
        repository.updateEntryWithMerge("edit-1", "card-b", false, "NM", "en", 2, "user-1")

        coVerify(exactly = 1) {
            localOpenForTradeDao.upsert(match {
                it.id == "offer-from" && it.localCollectionId == "revive-1" && it.quantity == 2 && !it.synced
            })
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 7 — open-for-trade re-pointing (in-place branch)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given an in-place edit that lowers quantity when updateEntryWithMerge then the linked offer is capped to the new lower quantity`() = runTest {
        val edited = buildEntity(id = "edit-1", scryfallId = "card-a", userId = "user-1", quantity = 5, isFoil = false, condition = "NM", language = "en")
        every { userCardCollectionDao.getById("edit-1") } returns edited
        every { userCardCollectionDao.getByCompositeKey("user-1", "card-a", true, "LP", "en") } returns null

        val offer = buildOffer(id = "offer-1", localCollectionId = "edit-1", scryfallId = "card-a", quantity = 5, synced = true)
        coEvery { localOpenForTradeDao.getByCollectionId("edit-1") } returns offer

        // In-place edit lowers the collection quantity to 2; the offer (previously 5) must be capped.
        repository.updateEntryWithMerge("edit-1", "card-a", true, "lp", "en", 2, "user-1")

        coVerify(exactly = 1) {
            localOpenForTradeDao.upsert(match {
                it.id == "offer-1" && it.quantity == 2 && it.isFoil && it.condition == "LP" && !it.synced
            })
        }
        coVerify(exactly = 0) { localOpenForTradeDao.deleteByCollectionId(any()) }
    }

    @Test
    fun `given an in-place edit with no linked offer when updateEntryWithMerge then no offer is created`() = runTest {
        val edited = buildEntity(id = "edit-1", scryfallId = "card-a", userId = "user-1", quantity = 5)
        every { userCardCollectionDao.getById("edit-1") } returns edited
        every { userCardCollectionDao.getByCompositeKey("user-1", "card-a", false, "NM", "en") } returns null
        coEvery { localOpenForTradeDao.getByCollectionId("edit-1") } returns null

        repository.updateEntryWithMerge("edit-1", "card-a", false, "NM", "en", 2, "user-1")

        coVerifyNoOfferWrites()
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 8 — guest fallback (null userId)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given a guest edited row and null userId when updateEntryWithMerge then the guest composite-key lookup is used`() = runTest {
        val edited = buildEntity(id = "edit-1", scryfallId = "card-a", userId = null, quantity = 1)
        every { userCardCollectionDao.getById("edit-1") } returns edited
        every {
            userCardCollectionDao.getByCompositeKeyGuest("card-b", false, "NM", "en")
        } returns null

        val outcome = repository.updateEntryWithMerge("edit-1", "card-b", false, "NM", "en", 3, userId = null)

        assertEquals(UpdateEntryOutcome.UPDATED, outcome)
        verify(exactly = 1) { userCardCollectionDao.getByCompositeKeyGuest("card-b", false, "NM", "en") }
        verify(exactly = 0) { userCardCollectionDao.getByCompositeKey(any(), any(), any(), any(), any()) }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun coVerifyNoOfferWrites() {
        coVerify(exactly = 0) { localOpenForTradeDao.upsert(any()) }
        coVerify(exactly = 0) { localOpenForTradeDao.deleteByCollectionId(any()) }
    }
}
