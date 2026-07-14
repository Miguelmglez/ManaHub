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
 * Instrumented test for the v42 → v43 migration ([MIGRATION_42_43]).
 *
 * Verifies that the migration:
 *  - adds the nullable `archetype_override` / `themes_override` columns to `decks`, and
 *  - preserves a pre-existing deck row, both new columns defaulting to NULL (never a fabricated
 *    non-null value).
 *
 * Schemas are loaded from androidTest assets (see `sourceSets.androidTest.assets` in
 * app/build.gradle.kts). Requires a connected device/emulator (`./gradlew connectedAndroidTest`).
 */
@RunWith(AndroidJUnit4::class)
class Migration42To43Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MtgDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate42to43_addsArchetypeOverrideColumns_andPreservesExistingDeck() {
        helper.createDatabase(TEST_DB, 42).apply {
            insert("decks", android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE, sampleDeck())
            close()
        }

        val migratedDb = helper.runMigrationsAndValidate(
            TEST_DB,
            43,
            /* validateDroppedTables = */ true,
            MIGRATION_42_43,
        )

        val columns = mutableListOf<String>()
        migratedDb.query("PRAGMA table_info(decks)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) {
                columns += cursor.getString(nameIndex)
            }
        }
        assertTrue("decks must contain archetype_override", columns.contains("archetype_override"))
        assertTrue("decks must contain themes_override", columns.contains("themes_override"))

        migratedDb.query(
            "SELECT archetype_override, themes_override FROM decks WHERE id = ?",
            arrayOf(SAMPLE_ID),
        ).use { cursor ->
            assertEquals(1, cursor.count)
            cursor.moveToFirst()
            assertTrue("archetype_override must be NULL after migration (never fabricated)", cursor.isNull(0))
            assertTrue("themes_override must be NULL after migration (never fabricated)", cursor.isNull(1))
        }

        migratedDb.close()
    }

    /** Minimal valid `decks` row for the v42 schema. */
    private fun sampleDeck(): ContentValues = ContentValues().apply {
        put("id", SAMPLE_ID)
        put("name", "Test Deck")
        put("description", "")
        put("format", "commander")
        put("is_deleted", 0)
        put("updated_at", 0L)
        put("created_at", 0L)
    }

    private companion object {
        const val TEST_DB = "migration-test-archetype-override"
        const val SAMPLE_ID = "00000000-0000-0000-0000-000000000002"
    }
}
