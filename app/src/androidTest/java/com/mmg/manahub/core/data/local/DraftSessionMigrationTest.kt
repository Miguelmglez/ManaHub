package com.mmg.manahub.core.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test for the v36 → v37 migration ([MIGRATION_36_37]).
 *
 * Verifies that the migration:
 *  - creates the new `draft_sessions` table, and
 *  - adds the `boosterVersion` column to the existing `draft_sets` table.
 *
 * Schemas are loaded from androidTest assets (see `sourceSets.androidTest.assets`
 * in app/build.gradle.kts).
 */
@RunWith(AndroidJUnit4::class)
class DraftSessionMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MtgDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate36to37_createsDraftSessions_andAddsBoosterVersion() {
        // Create the database at v36 and close it so the migration can run.
        helper.createDatabase(TEST_DB, 36).close()

        val migratedDb = helper.runMigrationsAndValidate(
            TEST_DB,
            37,
            /* validateDroppedTables = */ true,
            MIGRATION_36_37,
        )

        // draft_sessions table exists.
        migratedDb.query(
            "SELECT name FROM sqlite_master WHERE type='table' AND name='draft_sessions'",
        ).use { cursor ->
            assertEquals(1, cursor.count)
        }

        // boosterVersion column was added to draft_sets.
        val columns = mutableListOf<String>()
        migratedDb.query("PRAGMA table_info(draft_sets)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) {
                columns += cursor.getString(nameIndex)
            }
        }
        assertTrue("draft_sets must contain boosterVersion", columns.contains("boosterVersion"))

        migratedDb.close()
    }

    private companion object {
        const val TEST_DB = "migration-test-draftsim"
    }
}
