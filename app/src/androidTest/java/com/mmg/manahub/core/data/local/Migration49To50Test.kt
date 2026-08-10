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
 * Instrumented test for the v49 → v50 migration ([MIGRATION_49_50]).
 *
 * Verifies that the migration creates the `puzzle_results` table with the expected columns and
 * that a fresh migration on an empty v49 database leaves the table empty (this is a new table with
 * no data to preserve, unlike a column-add migration).
 *
 * Schemas are loaded from androidTest assets (see `sourceSets.androidTest.assets` in
 * app/build.gradle.kts). Requires a connected device/emulator (`./gradlew connectedAndroidTest`).
 */
@RunWith(AndroidJUnit4::class)
class Migration49To50Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MtgDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate49To50_createsPuzzleResultsTable() {
        helper.createDatabase(TEST_DB, 49).apply {
            close()
        }

        val migratedDb = helper.runMigrationsAndValidate(
            TEST_DB,
            50,
            /* validateDroppedTables = */ true,
            MIGRATION_49_50,
        )

        val columns = mutableListOf<String>()
        migratedDb.query("PRAGMA table_info(puzzle_results)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) {
                columns += cursor.getString(nameIndex)
            }
        }
        assertTrue("puzzle_results must contain puzzle_date", columns.contains("puzzle_date"))
        assertTrue("puzzle_results must contain type", columns.contains("type"))
        assertTrue("puzzle_results must contain attempts", columns.contains("attempts"))
        assertTrue("puzzle_results must contain solved", columns.contains("solved"))
        assertTrue("puzzle_results must contain perfect", columns.contains("perfect"))
        assertTrue("puzzle_results must contain elapsed_ms", columns.contains("elapsed_ms"))
        assertTrue("puzzle_results must contain started_at", columns.contains("started_at"))
        assertTrue("puzzle_results must contain guesses_json", columns.contains("guesses_json"))
        assertTrue("puzzle_results must contain completed_at", columns.contains("completed_at"))

        migratedDb.query("SELECT COUNT(*) FROM puzzle_results").use { cursor ->
            cursor.moveToFirst()
            assertTrue("new table must start empty", cursor.getInt(0) == 0)
        }

        migratedDb.close()
    }

    @Test
    fun migrate49To50_isIdempotent_onRetryAfterCrash() {
        helper.createDatabase(TEST_DB, 49).apply {
            close()
        }

        // Run the migration twice against a fresh v49 → v50 path to simulate a crash-and-retry;
        // `CREATE TABLE IF NOT EXISTS` must make a second run a no-op, not a crash.
        val db = helper.runMigrationsAndValidate(TEST_DB, 50, true, MIGRATION_49_50)
        MIGRATION_49_50.migrate(db)
        db.close()
    }

    private companion object {
        const val TEST_DB = "migration-test-puzzle-results"
    }
}
