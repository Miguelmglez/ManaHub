package com.mmg.manahub.core.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test for the v50 → v51 migration ([MIGRATION_50_51]).
 *
 * Verifies that the migration creates the `competitive_meta_cache` and
 * `competitive_limited_ratings_cache` tables with the expected columns and that a fresh migration
 * on an empty v50 database leaves both tables empty (these are new tables with no data to
 * preserve, unlike a column-add migration).
 *
 * Schemas are loaded from androidTest assets (see `sourceSets.androidTest.assets` in
 * app/build.gradle.kts). Requires a connected device/emulator (`./gradlew connectedAndroidTest`).
 */
@RunWith(AndroidJUnit4::class)
class Migration50To51Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MtgDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate50To51_createsCompetitiveMetaCacheTable() {
        helper.createDatabase(TEST_DB, 50).apply {
            close()
        }

        val migratedDb = helper.runMigrationsAndValidate(
            TEST_DB,
            51,
            /* validateDroppedTables = */ true,
            MIGRATION_50_51,
        )

        val columns = mutableListOf<String>()
        migratedDb.query("PRAGMA table_info(competitive_meta_cache)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) {
                columns += cursor.getString(nameIndex)
            }
        }
        assertTrue("competitive_meta_cache must contain format", columns.contains("format"))
        assertTrue("competitive_meta_cache must contain response_json", columns.contains("response_json"))
        assertTrue("competitive_meta_cache must contain cached_at", columns.contains("cached_at"))

        migratedDb.query("SELECT COUNT(*) FROM competitive_meta_cache").use { cursor ->
            cursor.moveToFirst()
            assertTrue("new table must start empty", cursor.getInt(0) == 0)
        }

        migratedDb.close()
    }

    @Test
    fun migrate50To51_createsCompetitiveLimitedRatingsCacheTable() {
        helper.createDatabase(TEST_DB, 50).apply {
            close()
        }

        val migratedDb = helper.runMigrationsAndValidate(
            TEST_DB,
            51,
            /* validateDroppedTables = */ true,
            MIGRATION_50_51,
        )

        val columns = mutableListOf<String>()
        migratedDb.query("PRAGMA table_info(competitive_limited_ratings_cache)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) {
                columns += cursor.getString(nameIndex)
            }
        }
        assertTrue("competitive_limited_ratings_cache must contain set_code", columns.contains("set_code"))
        assertTrue("competitive_limited_ratings_cache must contain format", columns.contains("format"))
        assertTrue("competitive_limited_ratings_cache must contain response_json", columns.contains("response_json"))
        assertTrue("competitive_limited_ratings_cache must contain cached_at", columns.contains("cached_at"))

        migratedDb.query("SELECT COUNT(*) FROM competitive_limited_ratings_cache").use { cursor ->
            cursor.moveToFirst()
            assertTrue("new table must start empty", cursor.getInt(0) == 0)
        }

        migratedDb.close()
    }

    @Test
    fun migrate50To51_isIdempotent_onRetryAfterCrash() {
        helper.createDatabase(TEST_DB, 50).apply {
            close()
        }

        // Run the migration twice against a fresh v50 → v51 path to simulate a crash-and-retry;
        // `CREATE TABLE IF NOT EXISTS` must make a second run a no-op, not a crash.
        val db = helper.runMigrationsAndValidate(TEST_DB, 51, true, MIGRATION_50_51)
        MIGRATION_50_51.migrate(db)
        db.close()
    }

    private companion object {
        const val TEST_DB = "migration-test-competitive-caches"
    }
}
