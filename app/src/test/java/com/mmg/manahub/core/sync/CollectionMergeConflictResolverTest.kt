package com.mmg.manahub.core.sync
// COMMENTS_REVIEWED: 2026-09-06

import com.mmg.manahub.core.data.local.dao.UserCardCollectionDao
import com.mmg.manahub.core.data.local.entity.UserCardCollectionEntity
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Write-path hardening audit (2026-09-06). Covers:
 * - HIGH 3: [CollectionMergeConflictResolver.resolve] is atomic (a single
 *   [UserCardCollectionDao.resolveMergeConflict] call), not a separate upsert + delete.
 * - CRITICAL 2 follow-up: a tombstoned [CollectionMergeConflict.accountRow] is revived
 *   (`isDeleted = false`) and contributes nothing to SUM, instead of the merge silently landing
 *   in a row every observe query still filters out.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CollectionMergeConflictResolverTest {

    private val collectionDao = mockk<UserCardCollectionDao>(relaxed = true)
    private val resolver = CollectionMergeConflictResolver(collectionDao, Dispatchers.Unconfined)

    private fun buildRow(
        id: String,
        quantity: Int,
        isForTrade: Boolean = false,
        isDeleted: Boolean = false,
    ) = UserCardCollectionEntity(
        id = id,
        userId = "user-1",
        scryfallId = "card-a",
        quantity = quantity,
        isFoil = false,
        condition = "NM",
        language = "en",
        isForTrade = isForTrade,
        isDeleted = isDeleted,
        updatedAt = 1_000L,
        createdAt = 500L,
    )

    @Test
    fun `resolve is atomic -- exactly one resolveMergeConflict call, never a separate upsert plus deleteById`() = runTest {
        val conflict = CollectionMergeConflict(
            guestRow = buildRow("guest-1", quantity = 2),
            accountRow = buildRow("account-1", quantity = 3),
        )

        resolver.resolve(conflict, MergeConflictResolution.SUM)

        verify(exactly = 1) { collectionDao.resolveMergeConflict(any(), eq("guest-1")) }
        verify(exactly = 0) { collectionDao.upsert(any()) }
        verify(exactly = 0) { collectionDao.deleteById(any()) }
    }

    @Test
    fun `resolve SUM against a LIVE account row sums both quantities and keeps it live`() = runTest {
        val conflict = CollectionMergeConflict(
            guestRow = buildRow("guest-1", quantity = 2),
            accountRow = buildRow("account-1", quantity = 3, isForTrade = true),
        )
        val captured = slot<UserCardCollectionEntity>()

        resolver.resolve(conflict, MergeConflictResolution.SUM)

        verify { collectionDao.resolveMergeConflict(capture(captured), eq("guest-1")) }
        assertEquals(5, captured.captured?.quantity)
        assertTrue(captured.captured?.isForTrade == true)
        assertFalse(captured.captured?.isDeleted == true)
    }

    @Test
    fun `resolve KEEP_ACCOUNT passes null -- nothing is written, only the guest row drops`() = runTest {
        val conflict = CollectionMergeConflict(
            guestRow = buildRow("guest-1", quantity = 2),
            accountRow = buildRow("account-1", quantity = 3),
        )

        resolver.resolve(conflict, MergeConflictResolution.KEEP_ACCOUNT)

        // A nullable arg can't be captured into a non-null CapturingSlot -- assert via isNull().
        verify { collectionDao.resolveMergeConflict(isNull(), eq("guest-1")) }
    }

    @Test
    fun `resolve KEEP_OFFLINE writes the guest quantity onto the account row's id`() = runTest {
        val conflict = CollectionMergeConflict(
            guestRow = buildRow("guest-1", quantity = 7, isForTrade = true),
            accountRow = buildRow("account-1", quantity = 3),
        )
        val captured = slot<UserCardCollectionEntity>()

        resolver.resolve(conflict, MergeConflictResolution.KEEP_OFFLINE)

        verify { collectionDao.resolveMergeConflict(capture(captured), eq("guest-1")) }
        assertEquals("account-1", captured.captured?.id)
        assertEquals(7, captured.captured?.quantity)
        assertTrue(captured.captured?.isForTrade == true)
    }

    @Test
    fun `resolve SUM against a TOMBSTONED account row revives it and does not carry over the stale pre-deletion quantity`() = runTest {
        // The account row was soft-deleted with quantity=5 still on the row (softDelete never
        // touches quantity) -- that stale value must contribute ZERO to the sum, or resolving
        // would resurrect quantity the user explicitly discarded.
        val conflict = CollectionMergeConflict(
            guestRow = buildRow("guest-1", quantity = 2),
            accountRow = buildRow("account-1", quantity = 5, isForTrade = true, isDeleted = true),
        )
        val captured = slot<UserCardCollectionEntity>()

        resolver.resolve(conflict, MergeConflictResolution.SUM)

        verify { collectionDao.resolveMergeConflict(capture(captured), eq("guest-1")) }
        assertEquals(2, captured.captured?.quantity)
        assertFalse(
            "A tombstone's stale isForTrade flag must not survive into the revived row",
            captured.captured?.isForTrade == true,
        )
        assertFalse(
            "The resolved row must be revived (visible again), or the merge silently vanishes into a dead tombstone",
            captured.captured?.isDeleted == true,
        )
    }

    @Test
    fun `resolve KEEP_OFFLINE against a TOMBSTONED account row revives it`() = runTest {
        val conflict = CollectionMergeConflict(
            guestRow = buildRow("guest-1", quantity = 4),
            accountRow = buildRow("account-1", quantity = 5, isDeleted = true),
        )
        val captured = slot<UserCardCollectionEntity>()

        resolver.resolve(conflict, MergeConflictResolution.KEEP_OFFLINE)

        verify { collectionDao.resolveMergeConflict(capture(captured), eq("guest-1")) }
        assertEquals(4, captured.captured?.quantity)
        assertFalse(captured.captured?.isDeleted == true)
    }

    @Test
    fun `getPendingConflicts surfaces a tombstoned account row as a conflict`() = runTest {
        val guestRow = buildRow("guest-1", quantity = 2)
        val tombstonedAccountRow = buildRow("account-1", quantity = 5, isDeleted = true)
        every { collectionDao.getAllGuestRows() } returns listOf(guestRow)
        every {
            collectionDao.getByCompositeKey("user-1", "card-a", false, "NM", "en")
        } returns tombstonedAccountRow

        val conflicts = resolver.getPendingConflicts("user-1")

        assertEquals(1, conflicts.size)
        assertTrue(conflicts[0].accountRow.isDeleted)
    }
}
