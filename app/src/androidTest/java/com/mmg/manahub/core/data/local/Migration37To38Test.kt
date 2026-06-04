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
 * Instrumented test for the v37 → v38 migration ([MIGRATION_37_38]).
 *
 * Verifies that the migration:
 *  - adds the nullable `edhrec_rank` and `penny_rank` columns to `cards`, and
 *  - preserves a pre-existing card row, with both new columns NULL.
 *
 * Schemas are loaded from androidTest assets (see `sourceSets.androidTest.assets`
 * in app/build.gradle.kts). Requires a connected device/emulator
 * (`./gradlew connectedAndroidTest`).
 */
@RunWith(AndroidJUnit4::class)
class Migration37To38Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MtgDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate37to38_addsRankColumns_andPreservesExistingCard() {
        // Create the database at v37 and seed one minimal card row, then close so the
        // migration can run.
        helper.createDatabase(TEST_DB, 37).apply {
            insert("cards", android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE, sampleCard())
            close()
        }

        val migratedDb = helper.runMigrationsAndValidate(
            TEST_DB,
            38,
            /* validateDroppedTables = */ true,
            MIGRATION_37_38,
        )

        // Both new columns exist on `cards`.
        val columns = mutableListOf<String>()
        migratedDb.query("PRAGMA table_info(cards)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) {
                columns += cursor.getString(nameIndex)
            }
        }
        assertTrue("cards must contain edhrec_rank", columns.contains("edhrec_rank"))
        assertTrue("cards must contain penny_rank", columns.contains("penny_rank"))

        // The pre-existing row survived and both ranks are NULL.
        migratedDb.query(
            "SELECT edhrec_rank, penny_rank FROM cards WHERE scryfall_id = ?",
            arrayOf(SAMPLE_ID),
        ).use { cursor ->
            assertEquals(1, cursor.count)
            cursor.moveToFirst()
            assertTrue("edhrec_rank must be NULL after migration", cursor.isNull(0))
            assertTrue("penny_rank must be NULL after migration", cursor.isNull(1))
        }

        migratedDb.close()
    }

    /**
     * Minimal valid `cards` row for the v37 schema. Only NOT NULL columns must be
     * populated; nullable columns are omitted so they default to NULL.
     */
    private fun sampleCard(): ContentValues = ContentValues().apply {
        put("scryfall_id", SAMPLE_ID)
        put("name", "Sol Ring")
        put("lang", "en")
        put("cmc", 1.0)
        put("colors", "[]")
        put("color_identity", "[]")
        put("type_line", "Artifact")
        put("keywords", "[]")
        put("set_code", "cmr")
        put("set_name", "Commander Legends")
        put("collector_number", "1")
        put("rarity", "uncommon")
        put("released_at", "2020-11-20")
        put("frame_effects", "[]")
        put("promo_types", "[]")
        put("legality_standard", "not_legal")
        put("legality_pioneer", "not_legal")
        put("legality_modern", "not_legal")
        put("legality_commander", "legal")
        put("scryfall_uri", "https://scryfall.com/card/cmr/1")
        put("cached_at", 0L)
        put("is_stale", 0)
        put("tags", "[]")
        put("user_tags", "[]")
        put("suggested_tags", "[]")
        put("related_uris", "{}")
        put("purchase_uris", "{}")
        put("game_changer", 0)
    }

    private companion object {
        const val TEST_DB = "migration-test-edhrec-rank"
        const val SAMPLE_ID = "00000000-0000-0000-0000-000000000001"
    }
}
