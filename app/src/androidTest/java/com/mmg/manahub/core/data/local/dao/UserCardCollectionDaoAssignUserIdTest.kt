package com.mmg.manahub.core.data.local.dao
// COMMENTS_REVIEWED: 2026-09-06

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mmg.manahub.core.data.local.MtgDatabase
import com.mmg.manahub.core.data.local.entity.UserCardCollectionEntity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Write-path hardening audit (2026-09-06), CRITICAL 2: [UserCardCollectionDao.assignUserId]'s
 * `NOT EXISTS` guard must match the composite UNIQUE index on `(user_id, scryfall_id, is_foil,
 * condition, language)` EXACTLY -- Room's `@Index` cannot express a partial index, so a
 * soft-deleted (tombstoned) row still occupies its tuple. Before this fix the guard filtered on
 * `existing.is_deleted = 0`, so a guest row colliding with a TOMBSTONED account row sailed past
 * the guard and hit the UPDATE, which SQLite then rejected with `SQLiteConstraintException`
 * because the tombstone still holds the tuple.
 *
 * Requires a connected device or emulator (`./gradlew connectedAndroidTest`). Test method names
 * are plain camelCase, not backtick-with-spaces -- see `CardDaoCascadeRegressionTest`'s sibling
 * files for why (D8 dexing rejects a space in a coroutine continuation's synthesized class name;
 * this suite avoids adding a 7th file to that pre-existing blocker).
 */
@RunWith(AndroidJUnit4::class)
class UserCardCollectionDaoAssignUserIdTest {

    private lateinit var db: MtgDatabase
    private lateinit var dao: UserCardCollectionDao

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MtgDatabase::class.java).build()
        dao = db.userCardCollectionDao()
    }

    @After
    fun closeDatabase() {
        db.close()
    }

    private fun row(
        id: String,
        userId: String?,
        isDeleted: Boolean = false,
        quantity: Int = 1,
    ) = UserCardCollectionEntity(
        id = id,
        userId = userId,
        scryfallId = "card-a",
        quantity = quantity,
        isFoil = false,
        condition = "NM",
        language = "en",
        isForTrade = false,
        isDeleted = isDeleted,
    )

    @Test
    fun assignUserId_guestRowCollidingWithTombstonedAccountRow_parksInsteadOfThrowing() {
        val tombstone = row(id = "account-row", userId = "user-1", isDeleted = true, quantity = 5)
        val guestRow = row(id = "guest-row", userId = null, quantity = 2)
        dao.upsert(tombstone)
        dao.upsert(guestRow)

        // Act: must not throw SQLiteConstraintException.
        val migratedCount = dao.assignUserId(newUserId = "user-1")

        // Assert: nothing migrated, guest row stays parked (user_id NULL) as a pending conflict,
        // the tombstone is untouched.
        assertEquals(0, migratedCount)
        val stillGuest = dao.getByIdIncludingDeleted("guest-row")
        assertNotNull(stillGuest)
        assertNull(stillGuest?.userId)
        val tombstoneAfter = dao.getByIdIncludingDeleted("account-row")
        assertTrue(tombstoneAfter?.isDeleted == true)
        assertEquals(5, tombstoneAfter?.quantity)

        // The guard's predicate must match getByCompositeKey exactly, so
        // CollectionMergeConflictResolver.getPendingConflicts can find this exact collision.
        val conflictTarget = dao.getByCompositeKey(
            userId = "user-1", scryfallId = "card-a", isFoil = false, condition = "NM", language = "en",
        )
        assertNotNull(conflictTarget)
        assertEquals("account-row", conflictTarget?.id)
    }

    @Test
    fun assignUserId_guestRowCollidingWithLiveAccountRow_alsoParksInsteadOfMerging() {
        val liveAccountRow = row(id = "account-row", userId = "user-1", isDeleted = false, quantity = 3)
        val guestRow = row(id = "guest-row", userId = null, quantity = 2)
        dao.upsert(liveAccountRow)
        dao.upsert(guestRow)

        val migratedCount = dao.assignUserId(newUserId = "user-1")

        assertEquals(0, migratedCount)
        assertNull(dao.getByIdIncludingDeleted("guest-row")?.userId)
        assertEquals(3, dao.getById("account-row")?.quantity)
    }

    @Test
    fun assignUserId_guestRowWithNoCollision_migratesNormally() {
        val guestRow = row(id = "guest-row", userId = null, quantity = 2)
        dao.upsert(guestRow)

        val migratedCount = dao.assignUserId(newUserId = "user-1")

        assertEquals(1, migratedCount)
        assertEquals("user-1", dao.getById("guest-row")?.userId)
    }
}
