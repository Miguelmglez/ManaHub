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
 * Instrumented test for the v43 → v44 migration ([MIGRATION_43_44]).
 *
 * Verifies that the migration creates the `community_aggregate_cache` table with the expected
 * columns and that a fresh migration on an empty v43 database leaves the table empty (this is a
 * new table with no data to preserve, unlike [Migration42To43Test]).
 *
 * Schemas are loaded from androidTest assets (see `sourceSets.androidTest.assets` in
 * app/build.gradle.kts). Requires a connected device/emulator (`./gradlew connectedAndroidTest`).
 */
@RunWith(AndroidJUnit4::class)
class Migration43To44Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MtgDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate43to44_createsCommunityAggregateCacheTable() {
        helper.createDatabase(TEST_DB, 43).apply {
            close()
        }

        val migratedDb = helper.runMigrationsAndValidate(
            TEST_DB,
            44,
            /* validateDroppedTables = */ true,
            MIGRATION_43_44,
        )

        val columns = mutableListOf<String>()
        migratedDb.query("PRAGMA table_info(community_aggregate_cache)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) {
                columns += cursor.getString(nameIndex)
            }
        }
        assertTrue("community_aggregate_cache must contain key", columns.contains("key"))
        assertTrue("community_aggregate_cache must contain json", columns.contains("json"))
        assertTrue("community_aggregate_cache must contain cached_at", columns.contains("cached_at"))

        migratedDb.query("SELECT COUNT(*) FROM community_aggregate_cache").use { cursor ->
            cursor.moveToFirst()
            assertTrue("new table must start empty", cursor.getInt(0) == 0)
        }

        migratedDb.close()
    }

    @Test
    fun migrate43to44_isIdempotent_onRetryAfterCrash() {
        helper.createDatabase(TEST_DB, 43).apply {
            close()
        }

        // Run the migration twice against a fresh v43 → v44 path to simulate a crash-and-retry;
        // `CREATE TABLE IF NOT EXISTS` must make a second run a no-op, not a crash.
        val db = helper.runMigrationsAndValidate(TEST_DB, 44, true, MIGRATION_43_44)
        MIGRATION_43_44.migrate(db)
        db.close()
    }

    private companion object {
        const val TEST_DB = "migration-test-community-aggregate-cache"
    }
}
