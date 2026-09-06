package com.mmg.manahub.core.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test for the v52 → v53 migration ([MIGRATION_52_53]) — collection sync data-loss
 * fix (`linear-moseying-yeti` plan), Phase 2.
 *
 * Verifies:
 * 1. All 11 columns of every `user_card_collection` row -- a normal row, a row referencing a
 *    `scryfall_id` NOT present in `cards` (the exact shape a post-fix pull now produces before
 *    hydration), and a soft-deleted tombstone row -- survive the 12-step table recreation
 *    byte-for-byte.
 * 2. The RESTRICT foreign key to `cards` is actually gone (`PRAGMA foreign_key_list` is empty),
 *    AND that this holds functionally, not just structurally: with `PRAGMA foreign_keys = ON`
 *    explicitly set (matching the real app's connection), inserting a row whose `scryfall_id` has
 *    no matching `cards` row still succeeds.
 * 3. The composite UNIQUE index still rejects a duplicate `(user_id, scryfall_id, is_foil,
 *    condition, language)` tuple.
 * 4. Every index name matches EXACTLY what `@Entity(indices = [...])` on
 *    [com.mmg.manahub.core.data.local.entity.UserCardCollectionEntity] generates — a mismatch here
 *    fails `runMigrationsAndValidate()`, and therefore app startup, for every user -- AND that the
 *    composite index is flagged UNIQUE while the other four are not (`PRAGMA index_list`'s
 *    `unique` column), not just present by name.
 *
 * Schemas are loaded from androidTest assets (see `sourceSets.androidTest.assets` in
 * app/build.gradle.kts). Requires a connected device/emulator (`./gradlew connectedAndroidTest`).
 */
@RunWith(AndroidJUnit4::class)
class Migration52To53Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MtgDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    /** All 11 columns of `user_card_collection`, in the order they're read back and compared. */
    private val allColumns = listOf(
        "id", "user_id", "scryfall_id", "quantity", "is_foil", "condition", "language",
        "is_for_trade", "is_deleted", "updated_at", "created_at",
    )

    @Test
    fun migrate52To53_preservesRowsIncludingUnhydratedCardAndTombstone() {
        // Fixture rows, one per interesting shape. Values below are the SOURCE OF TRUTH the
        // post-migration query results are compared against column-by-column.
        val row1 = listOf("row-1", "user-1", "scryfall-cached", 2, 0, "NM", "en", 0, 0, 1000L, 1000L)
        val row2 = listOf("row-2", "user-1", "scryfall-not-cached-yet", 1, 1, "NM", "en", 0, 0, 2000L, 2000L)
        // A soft-deleted (tombstone) row -- the exact shape a remote deletion produces client-side.
        val row3 = listOf("row-3", "user-1", "scryfall-tombstoned", 0, 0, "LP", "ja", 1, 1, 3000L, 1500L)

        helper.createDatabase(TEST_DB, 52).apply {
            for (row in listOf(row1, row2, row3)) {
                execSQL(
                    """
                    INSERT INTO user_card_collection
                        (id, user_id, scryfall_id, quantity, is_foil, condition, language,
                         is_for_trade, is_deleted, updated_at, created_at)
                    VALUES
                        ('${row[0]}', '${row[1]}', '${row[2]}', ${row[3]}, ${row[4]}, '${row[5]}',
                         '${row[6]}', ${row[7]}, ${row[8]}, ${row[9]}, ${row[10]})
                    """.trimIndent()
                )
            }
            // row-2 references a scryfall_id with NO matching `cards` row -- exactly what the
            // Phase 3/4 pull now writes before the hydration worker resolves it. Pre-migration
            // this only succeeds because MigrationTestHelper does not enable
            // `PRAGMA foreign_keys` (matching the real app's connection-level behaviour being
            // moot here since we are about to drop the constraint anyway); the point of this
            // fixture is to prove the row survives the recreation, not to re-prove FK semantics --
            // that is covered separately by
            // [migrate52To53_allowsInsertWithUnresolvedCardReference_withForeignKeysEnforced].
            close()
        }

        val migratedDb = helper.runMigrationsAndValidate(
            TEST_DB,
            53,
            /* validateDroppedTables = */ true,
            MIGRATION_52_53,
        )

        migratedDb.query("SELECT COUNT(*) FROM user_card_collection").use { cursor ->
            cursor.moveToFirst()
            assertEquals("all three rows must survive the recreation", 3, cursor.getInt(0))
        }

        for (expected in listOf(row1, row2, row3)) {
            val id = expected[0] as String
            migratedDb.query(
                "SELECT ${allColumns.joinToString(", ")} FROM user_card_collection WHERE id = ?",
                arrayOf(id),
            ).use { cursor ->
                assertTrue("row $id must exist after migration", cursor.moveToFirst())
                for ((index, column) in allColumns.withIndex()) {
                    val actual: Any = when (val e = expected[index]) {
                        is String -> cursor.getString(cursor.getColumnIndexOrThrow(column))
                        is Int -> cursor.getInt(cursor.getColumnIndexOrThrow(column))
                        is Long -> cursor.getLong(cursor.getColumnIndexOrThrow(column))
                        else -> error("unsupported fixture value type for column $column: $e")
                    }
                    assertEquals(
                        "row $id column '$column' must be byte-identical after recreation",
                        expected[index],
                        actual,
                    )
                }
            }
        }

        migratedDb.close()
    }

    @Test
    fun migrate52To53_allowsInsertWithUnresolvedCardReference_withForeignKeysEnforced() {
        helper.createDatabase(TEST_DB, 52).close()

        val migratedDb = helper.runMigrationsAndValidate(TEST_DB, 53, true, MIGRATION_52_53)

        // Explicitly turn FK enforcement ON for this connection -- proves the constraint is
        // functionally gone (not merely absent from PRAGMA foreign_key_list's static schema
        // dump), matching the real app's connection which always runs with
        // `PRAGMA foreign_keys = ON`.
        migratedDb.execSQL("PRAGMA foreign_keys = ON")

        // No row for 'scryfall-never-cached' exists in `cards` -- pre-v53 this insert would throw
        // a foreign key constraint violation.
        migratedDb.execSQL(
            """
            INSERT INTO user_card_collection
                (id, user_id, scryfall_id, quantity, is_foil, condition, language,
                 is_for_trade, is_deleted, updated_at, created_at)
            VALUES
                ('row-unresolved', 'user-1', 'scryfall-never-cached', 1, 0, 'NM', 'en', 0, 0, 1000, 1000)
            """.trimIndent()
        )

        migratedDb.query(
            "SELECT COUNT(*) FROM user_card_collection WHERE id = 'row-unresolved'"
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals(
                "an ownership row must insert successfully even when its card is not yet cached",
                1,
                cursor.getInt(0),
            )
        }

        migratedDb.close()
    }

    @Test
    fun migrate52To53_dropsRestrictForeignKey() {
        helper.createDatabase(TEST_DB, 52).close()

        val migratedDb = helper.runMigrationsAndValidate(TEST_DB, 53, true, MIGRATION_52_53)

        migratedDb.query("PRAGMA foreign_key_list(user_card_collection)").use { cursor ->
            assertFalse(
                "user_card_collection must have ZERO foreign keys after v53",
                cursor.moveToFirst(),
            )
        }

        migratedDb.close()
    }

    @Test
    fun migrate52To53_compositeUniqueIndexStillRejectsDuplicateTuple() {
        helper.createDatabase(TEST_DB, 52).close()

        val migratedDb = helper.runMigrationsAndValidate(TEST_DB, 53, true, MIGRATION_52_53)

        migratedDb.execSQL(
            """
            INSERT INTO user_card_collection
                (id, user_id, scryfall_id, quantity, is_foil, condition, language,
                 is_for_trade, is_deleted, updated_at, created_at)
            VALUES
                ('row-a', 'user-1', 'scryfall-x', 1, 0, 'NM', 'en', 0, 0, 1000, 1000)
            """.trimIndent()
        )

        var threw = false
        try {
            migratedDb.execSQL(
                """
                INSERT INTO user_card_collection
                    (id, user_id, scryfall_id, quantity, is_foil, condition, language,
                     is_for_trade, is_deleted, updated_at, created_at)
                VALUES
                    ('row-b', 'user-1', 'scryfall-x', 5, 0, 'NM', 'en', 0, 0, 2000, 2000)
                """.trimIndent()
            )
        } catch (e: Exception) {
            threw = true
        }
        assertTrue(
            "the composite unique index (user_id, scryfall_id, is_foil, condition, language) " +
                "must still reject a duplicate tuple after the FK drop",
            threw,
        )

        migratedDb.close()
    }

    @Test
    fun migrate52To53_indicesMatchRoomGeneratedNames() {
        helper.createDatabase(TEST_DB, 52).close()

        val migratedDb = helper.runMigrationsAndValidate(TEST_DB, 53, true, MIGRATION_52_53)

        val compositeIndexName = "index_user_card_collection_user_id_scryfall_id_is_foil_condition_language"
        val indexUniqueness = mutableMapOf<String, Boolean>()
        migratedDb.query("PRAGMA index_list(user_card_collection)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            val uniqueIndex = cursor.getColumnIndex("unique")
            while (cursor.moveToNext()) {
                indexUniqueness[cursor.getString(nameIndex)] = cursor.getInt(uniqueIndex) == 1
            }
        }

        val expected = setOf(
            "index_user_card_collection_scryfall_id",
            "index_user_card_collection_user_id",
            "index_user_card_collection_updated_at",
            "index_user_card_collection_is_deleted",
            compositeIndexName,
        )
        for (name in expected) {
            assertTrue(
                "expected index $name to exist, found: ${indexUniqueness.keys}",
                indexUniqueness.containsKey(name),
            )
        }
        assertTrue(
            "the composite index must be UNIQUE (mirrors the Supabase unique constraint)",
            indexUniqueness.getValue(compositeIndexName),
        )
        for (name in expected - compositeIndexName) {
            assertFalse(
                "non-composite index $name must NOT be unique",
                indexUniqueness.getValue(name),
            )
        }

        migratedDb.close()
    }

    @Test
    fun migrate52To53_isIdempotent_onRetryAfterCrash() {
        helper.createDatabase(TEST_DB, 52).apply {
            execSQL(
                """
                INSERT INTO user_card_collection
                    (id, user_id, scryfall_id, quantity, is_foil, condition, language,
                     is_for_trade, is_deleted, updated_at, created_at)
                VALUES
                    ('row-1', 'user-1', 'scryfall-x', 1, 0, 'NM', 'en', 0, 0, 1000, 1000)
                """.trimIndent()
            )
            close()
        }

        // Run the migration twice to simulate a crash-and-retry. The SELECT in step 2 always
        // targets whichever table currently holds the `user_card_collection` name -- the original
        // on the first run, the already-migrated one on the second -- so re-running from an
        // ALREADY-migrated v53 shape is also safe, not just from a half-migrated state.
        val db = helper.runMigrationsAndValidate(TEST_DB, 53, true, MIGRATION_52_53)
        MIGRATION_52_53.migrate(db)

        db.query("SELECT COUNT(*) FROM user_card_collection").use { cursor ->
            cursor.moveToFirst()
            assertEquals("re-running the migration must not duplicate or lose rows", 1, cursor.getInt(0))
        }

        db.close()
    }

    private companion object {
        const val TEST_DB = "migration-test-user-card-collection-fk-drop"
    }
}
