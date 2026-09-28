package com.mmg.manahub.core.data.local
// COMMENTS_REVIEWED: 2026-09-08

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
 * Instrumented test for the v53 → v54 migration ([MIGRATION_53_54]).
 *
 * Verifies that the migration:
 *  - adds the nullable `posture_override` column to `decks`, and
 *  - preserves a pre-existing deck row, the new column defaulting to NULL (never a fabricated
 *    non-null value) -- mirrors [Migration42To43Test]'s shape for the sibling
 *    `archetype_override`/`themes_override` columns.
 *
 * Schemas are loaded from androidTest assets (see `sourceSets.androidTest.assets` in
 * app/build.gradle.kts). Requires a connected device/emulator (`./gradlew connectedAndroidTest`).
 */
@RunWith(AndroidJUnit4::class)
class Migration53To54Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MtgDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate53to54_addsPostureOverrideColumn_andPreservesExistingDeck() {
        helper.createDatabase(TEST_DB, 53).apply {
            insert("decks", android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE, sampleDeck())
            close()
        }

        val migratedDb = helper.runMigrationsAndValidate(
            TEST_DB,
            54,
            /* validateDroppedTables = */ true,
            MIGRATION_53_54,
        )

        val columns = mutableListOf<String>()
        migratedDb.query("PRAGMA table_info(decks)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) {
                columns += cursor.getString(nameIndex)
            }
        }
        assertTrue("decks must contain posture_override", columns.contains("posture_override"))

        migratedDb.query(
            "SELECT posture_override FROM decks WHERE id = ?",
            arrayOf(SAMPLE_ID),
        ).use { cursor ->
            assertEquals(1, cursor.count)
            cursor.moveToFirst()
            assertTrue("posture_override must be NULL after migration (never fabricated)", cursor.isNull(0))
        }

        migratedDb.close()
    }

    /** Minimal valid `decks` row for the v53 schema. */
    private fun sampleDeck(): ContentValues = ContentValues().apply {
        put("id", SAMPLE_ID)
        put("name", "Test Deck")
        put("description", "")
        put("format", "commander")
        put("is_deleted", 0)
        put("updated_at", 0L)
        put("created_at", 0L)
        put("strategy_locked", 0)
    }

    private companion object {
        const val TEST_DB = "migration-test-posture-override"
        const val SAMPLE_ID = "00000000-0000-0000-0000-000000000003"
    }
}
