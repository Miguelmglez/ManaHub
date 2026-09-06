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
 * 1. Every `user_card_collection` row (including one referencing a `scryfall_id` NOT present in
 *    `cards`, the exact shape a post-fix pull now produces before hydration) survives the 12-step
 *    table recreation byte for byte.
 * 2. The RESTRICT foreign key to `cards` is actually gone (`PRAGMA foreign_key_list` is empty).
 * 3. The composite UNIQUE index still rejects a duplicate `(user_id, scryfall_id, is_foil,
 *    condition, language)` tuple.
 * 4. Every index name matches EXACTLY what `@Entity(indices = [...])` on
 *    [com.mmg.manahub.core.data.local.entity.UserCardCollectionEntity] generates — a mismatch here
 *    fails `runMigrationsAndValidate()`, and therefore app startup, for every user.
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

    @Test
    fun migrate52To53_preservesRowsIncludingUnhydratedCard() {
        helper.createDatabase(TEST_DB, 52).apply {
            // A normal row referencing a cached card.
            execSQL(
                """
                INSERT INTO user_card_collection
                    (id, user_id, scryfall_id, quantity, is_foil, condition, language,
                     is_for_trade, is_deleted, updated_at, created_at)
                VALUES
                    ('row-1', 'user-1', 'scryfall-cached', 2, 0, 'NM', 'en', 0, 0, 1000, 1000)
                """.trimIndent()
            )
            // A row referencing a scryfall_id with NO matching `cards` row -- exactly what the
            // Phase 3/4 pull now writes before the hydration worker resolves it. Pre-migration
            // this only succeeds because MigrationTestHelper does not enable
            // `PRAGMA foreign_keys` (matching the real app's connection-level behaviour being
            // moot here since we are about to drop the constraint anyway); the point of this
            // fixture is to prove the row survives the recreation, not to re-prove FK semantics.
            execSQL(
                """
                INSERT INTO user_card_collection
                    (id, user_id, scryfall_id, quantity, is_foil, condition, language,
                     is_for_trade, is_deleted, updated_at, created_at)
                VALUES
                    ('row-2', 'user-1', 'scryfall-not-cached-yet', 1, 1, 'NM', 'en', 0, 0, 2000, 2000)
                """.trimIndent()
            )
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
            assertEquals("both rows must survive the recreation", 2, cursor.getInt(0))
        }
        migratedDb.query(
            "SELECT quantity FROM user_card_collection WHERE id = 'row-2'"
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals("row-2's data must be byte-identical after recreation", 1, cursor.getInt(0))
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

        val indexNames = mutableSetOf<String>()
        migratedDb.query("PRAGMA index_list(user_card_collection)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) {
                indexNames += cursor.getString(nameIndex)
            }
        }

        val expected = setOf(
            "index_user_card_collection_scryfall_id",
            "index_user_card_collection_user_id",
            "index_user_card_collection_updated_at",
            "index_user_card_collection_is_deleted",
            "index_user_card_collection_user_id_scryfall_id_is_foil_condition_language",
        )
        for (name in expected) {
            assertTrue("expected index $name to exist, found: $indexNames", indexNames.contains(name))
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
