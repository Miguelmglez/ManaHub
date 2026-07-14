package com.mmg.manahub.core.data.local

import android.content.ContentValues
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
 * Instrumented test for the v44 → v45 migration ([MIGRATION_44_45]).
 *
 * Verifies that the migration:
 *  - adds `last_fetched_at` (INTEGER NOT NULL DEFAULT 0), `etag` (nullable TEXT) and
 *    `last_modified` (nullable TEXT) to `content_sources`, and
 *  - preserves a pre-existing source row, with the three new columns defaulting to
 *    `0`/`NULL`/`NULL` respectively (never a fabricated non-default value) — see
 *    News feature improvements Phase 1.
 *
 * Schemas are loaded from androidTest assets (see `sourceSets.androidTest.assets` in
 * app/build.gradle.kts). Requires a connected device/emulator (`./gradlew connectedAndroidTest`).
 */
@RunWith(AndroidJUnit4::class)
class Migration44To45Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MtgDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate44to45_addsWatermarkColumns_andPreservesExistingSource() {
        helper.createDatabase(TEST_DB, 44).apply {
            insert("content_sources", android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE, sampleSource())
            close()
        }

        val migratedDb = helper.runMigrationsAndValidate(
            TEST_DB,
            45,
            /* validateDroppedTables = */ true,
            MIGRATION_44_45,
        )

        val columns = mutableListOf<String>()
        migratedDb.query("PRAGMA table_info(content_sources)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) {
                columns += cursor.getString(nameIndex)
            }
        }
        assertTrue("content_sources must contain last_fetched_at", columns.contains("last_fetched_at"))
        assertTrue("content_sources must contain etag", columns.contains("etag"))
        assertTrue("content_sources must contain last_modified", columns.contains("last_modified"))

        migratedDb.query(
            "SELECT last_fetched_at, etag, last_modified FROM content_sources WHERE id = ?",
            arrayOf(SAMPLE_ID),
        ).use { cursor ->
            assertEquals(1, cursor.count)
            cursor.moveToFirst()
            assertEquals(
                "last_fetched_at must default to 0 for a pre-existing row",
                0L,
                cursor.getLong(0),
            )
            assertTrue("etag must be NULL after migration (never fabricated)", cursor.isNull(1))
            assertTrue("last_modified must be NULL after migration (never fabricated)", cursor.isNull(2))
        }

        migratedDb.close()
    }

    @Test
    fun migrate44to45_isIdempotent_onRetryAfterCrash() {
        helper.createDatabase(TEST_DB, 44).apply {
            insert("content_sources", android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE, sampleSource())
            close()
        }

        // Run the migration twice against a fresh v44 → v45 path to simulate a crash-and-retry;
        // the columnExists guard must make a second run a no-op, not a crash.
        val db = helper.runMigrationsAndValidate(TEST_DB, 45, true, MIGRATION_44_45)
        MIGRATION_44_45.migrate(db)
        db.close()
    }

    @Test
    fun migrate44to45_onEmptyTable_leavesItEmpty() {
        helper.createDatabase(TEST_DB, 44).apply {
            close()
        }

        val migratedDb = helper.runMigrationsAndValidate(TEST_DB, 45, true, MIGRATION_44_45)

        migratedDb.query("SELECT COUNT(*) FROM content_sources").use { cursor ->
            cursor.moveToFirst()
            assertEquals("an empty v44 table must stay empty after migration", 0, cursor.getInt(0))
        }

        migratedDb.close()
    }

    /** Minimal valid `content_sources` row for the v44 schema (pre-Phase-1 columns only). */
    private fun sampleSource(): ContentValues = ContentValues().apply {
        put("id", SAMPLE_ID)
        put("name", "Test Source")
        put("feed_url", "https://example.com/feed")
        put("type", "ARTICLE")
        put("is_enabled", 1)
        put("is_default", 1)
        put("icon_url", null as String?)
        put("language", "en")
    }

    private companion object {
        const val TEST_DB = "migration-test-content-sources-watermark"
        const val SAMPLE_ID = "default_article_test"
    }
}
