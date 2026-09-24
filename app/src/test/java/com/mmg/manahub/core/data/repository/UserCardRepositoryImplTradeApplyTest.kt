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
import com.mmg.manahub.core.domain.repository.TradeCollectionLine
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
 * Tests for [UserCardRepositoryImpl.decrementById] and
 * [UserCardRepositoryImpl.applyTradeCollectionChanges]: a trade removes only the traded copies,
 * a stale ref falls back to the attribute tuple, and a reversal restores the original quantity.
 *
 * The collection DAO is backed by an in-memory map so a full apply/reverse round-trip can be
 * observed; `database.withTransaction` just runs its block.
 */
class UserCardRepositoryImplTradeApplyTest {

    private val userCardCollectionDao = mockk<UserCardCollectionDao>(relaxed = true)
    private val database = mockk<MtgDatabase>(relaxed = true)
    private val authRepository = mockk<AuthRepository>(relaxed = true)
    private val localOpenForTradeDao = mockk<LocalOpenForTradeDao>(relaxed = true)
    private val crashlytics = mockk<FirebaseCrashlytics>(relaxed = true)

    private val rows = linkedMapOf<String, UserCardCollectionEntity>()
    private lateinit var repository: UserCardRepositoryImpl

    private val userId = "user-1"

    private fun row(
        id: String = "row-1",
        scryfallId: String = "card-1",
        quantity: Int = 4,
        isFoil: Boolean = false,
        condition: String = "NM",
        language: String = "en",
        owner: String? = userId,
    ) = UserCardCollectionEntity(
        id = id,
        userId = owner,
        scryfallId = scryfallId,
        quantity = quantity,
        isFoil = isFoil,
        condition = condition,
        language = language,
        isForTrade = false,
        isDeleted = false,
        updatedAt = 1L,
        createdAt = 1L,
    )

    private fun offer(collectionId: String, quantity: Int, synced: Boolean = true) = LocalOpenForTradeEntity(
        id = "offer-$collectionId",
        localCollectionId = collectionId,
        scryfallId = "card-1",
        quantity = quantity,
        synced = synced,
    )

    @Before
    fun setUp() {
        mockkStatic(FirebaseCrashlytics::class)
        every { FirebaseCrashlytics.getInstance() } returns crashlytics
        mockkStatic("androidx.room.RoomDatabaseKt")
        val block = slot<suspend () -> Any?>()
        coEvery { database.withTransaction<Any?>(capture(block)) } coAnswers { block.captured.invoke() }

        every { userCardCollectionDao.getById(any()) } answers { rows[firstArg()]?.takeIf { !it.isDeleted } }
        every { userCardCollectionDao.getByCompositeKey(any(), any(), any(), any(), any()) } answers {
            val (owner, card, foil, cond, lang) = args
            rows.values.firstOrNull {
                it.userId == owner && it.scryfallId == card && it.isFoil == foil && it.condition == cond && it.language == lang
            }
        }
        every { userCardCollectionDao.upsert(any()) } answers {
            val entity = firstArg<UserCardCollectionEntity>()
            rows[entity.id] = entity
            1L
        }
        every { userCardCollectionDao.softDelete(any(), any()) } answers {
            val id = firstArg<String>()
            rows[id]?.let { rows[id] = it.copy(isDeleted = true) }
        }
        coEvery { localOpenForTradeDao.getByCollectionId(any()) } returns null
        coEvery { localOpenForTradeDao.getByAttributes(any(), any(), any(), any()) } returns null

        repository = UserCardRepositoryImpl(
            userCardCollectionDao = userCardCollectionDao,
            collectionRemoteDataSource = mockk<CollectionRemoteDataSource>(relaxed = true),
            remoteKeyDao = mockk<RemoteKeyDao>(relaxed = true),
            database = database,
            supabaseClient = mockk<SupabaseClient>(relaxed = true),
            authRepository = authRepository,
            localOpenForTradeDao = localOpenForTradeDao,
            ioDispatcher = UnconfinedTestDispatcher(),
        )
    }

    @After
    fun tearDown() {
        unmockkStatic(FirebaseCrashlytics::class)
        unmockkStatic("androidx.room.RoomDatabaseKt")
    }

    private suspend fun apply(deductions: List<TradeCollectionLine> = emptyList(), additions: List<TradeCollectionLine> = emptyList()) =
        repository.applyTradeCollectionChanges(userId, deductions, additions, shouldApply = { true }, onApplied = {})

    private fun line(quantity: Int, ref: String? = "row-1", isFoil: Boolean = false) =
        TradeCollectionLine("card-1", isFoil, "NM", "en", quantity, userCardIdRef = ref)

    @Test
    fun `given four copies when one is traded then three remain and the row stays live`() = runTest {
        rows["row-1"] = row(quantity = 4)

        apply(deductions = listOf(line(1)))

        assertEquals(3, rows.getValue("row-1").quantity)
        assertFalse(rows.getValue("row-1").isDeleted)
    }

    @Test
    fun `given four copies when all four are traded then the row is soft-deleted`() = runTest {
        rows["row-1"] = row(quantity = 4)

        apply(deductions = listOf(line(4)))

        assertTrue(rows.getValue("row-1").isDeleted)
    }

    @Test
    fun `given one of four traded when the trade is reversed then the original quantity comes back`() = runTest {
        rows["row-1"] = row(quantity = 4)

        apply(deductions = listOf(line(1)))
        apply(additions = listOf(line(1, ref = null)))

        assertEquals(4, rows.getValue("row-1").quantity)
        assertEquals(1, rows.size)
    }

    @Test
    fun `given a fully traded row when reversed then it is revived with the traded quantity`() = runTest {
        rows["row-1"] = row(quantity = 2)

        apply(deductions = listOf(line(2)))
        apply(additions = listOf(line(2, ref = null)))

        assertFalse(rows.getValue("row-1").isDeleted)
        assertEquals(2, rows.getValue("row-1").quantity)
    }

    @Test
    fun `given a ref pointing at a different variant then the attribute row is decremented and a mismatch is reported`() = runTest {
        rows["row-foil"] = row(id = "row-foil", isFoil = true, quantity = 1)
        rows["row-1"] = row(quantity = 3)

        val result = apply(deductions = listOf(line(1, ref = "row-foil")))

        assertEquals(1, result?.refFallbackCount)
        assertEquals(2, rows.getValue("row-1").quantity)
        assertEquals(1, rows.getValue("row-foil").quantity)
        verify { crashlytics.recordException(any()) }
    }

    @Test
    fun `given a ref owned by another user then it is ignored and the attribute row is used`() = runTest {
        rows["row-other"] = row(id = "row-other", owner = "someone-else", quantity = 5)
        rows["row-1"] = row(quantity = 2)

        apply(deductions = listOf(line(1, ref = "row-other")))

        assertEquals(5, rows.getValue("row-other").quantity)
        assertEquals(1, rows.getValue("row-1").quantity)
    }

    @Test
    fun `given a null ref then the row is found by attributes`() = runTest {
        rows["row-1"] = row(quantity = 2)

        val result = apply(deductions = listOf(line(1, ref = null)))

        assertEquals(1, rows.getValue("row-1").quantity)
        assertEquals(0, result?.refFallbackCount)
    }

    @Test
    fun `given no matching row then nothing is written and the deduction is counted as unmatched`() = runTest {
        val result = apply(deductions = listOf(line(1, ref = null)))

        assertEquals(1, result?.unmatchedDeductionCount)
        verify(exactly = 0) { userCardCollectionDao.upsert(any()) }
    }

    @Test
    fun `given a partial trade then the offer is trimmed to the remaining copies and kept`() = runTest {
        rows["row-1"] = row(quantity = 4)
        coEvery { localOpenForTradeDao.getByCollectionId("row-1") } returns offer("row-1", quantity = 4)

        val result = apply(deductions = listOf(line(2)))

        coVerify { localOpenForTradeDao.upsert(match { it.quantity == 2 && it.localCollectionId == "row-1" }) }
        coVerify(exactly = 0) { localOpenForTradeDao.deleteById(any()) }
        assertTrue(result!!.remoteOfferRemovals.isEmpty())
    }

    @Test
    fun `given the last copy is traded then a synced offer is deleted and scheduled for remote removal`() = runTest {
        rows["row-1"] = row(quantity = 1)
        coEvery { localOpenForTradeDao.getByCollectionId("row-1") } returns offer("row-1", quantity = 1)

        val result = apply(deductions = listOf(line(1)))

        coVerify { localOpenForTradeDao.deleteById("offer-row-1") }
        assertEquals(listOf("row-1"), result?.remoteOfferRemovals)
    }

    @Test
    fun `given the gate refuses then nothing is written`() = runTest {
        rows["row-1"] = row(quantity = 4)

        val result = repository.applyTradeCollectionChanges(
            userId, listOf(line(1)), emptyList(), shouldApply = { false }, onApplied = { error("must not run") },
        )

        assertNull(result)
        assertEquals(4, rows.getValue("row-1").quantity)
    }

    @Test
    fun `given decrementById on a live row then only that quantity is removed`() = runTest {
        rows["row-1"] = row(quantity = 4)

        val result = repository.decrementById("row-1", 1, "card-1", false, "NM", "en", userId)

        assertEquals("row-1", result.rowId)
        assertEquals(3, result.remainingQuantity)
        assertFalse(result.usedAttributeFallback)
    }
}
