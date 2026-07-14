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
 * Instrumented test for the v41 → v42 migration ([MIGRATION_41_42]).
 *
 * Verifies that the migration:
 *  - adds the NOT-NULL `produced_mana` column to `cards` with a `''` default, and
 *  - preserves a pre-existing card row, backfilled to the empty string (never NULL,
 *    never a JSON blob).
 *
 * Schemas are loaded from androidTest assets (see `sourceSets.androidTest.assets`
 * in app/build.gradle.kts). Requires a connected device/emulator
 * (`./gradlew connectedAndroidTest`).
 */
@RunWith(AndroidJUnit4::class)
class Migration41To42Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MtgDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate41to42_addsProducedManaColumn_andPreservesExistingCard() {
        // Create the database at v41 and seed one minimal card row, then close so the
        // migration can run.
        helper.createDatabase(TEST_DB, 41).apply {
            insert("cards", android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE, sampleCard())
            close()
        }

        val migratedDb = helper.runMigrationsAndValidate(
            TEST_DB,
            42,
            /* validateDroppedTables = */ true,
            MIGRATION_41_42,
        )

        // The new column exists on `cards`.
        val columns = mutableListOf<String>()
        migratedDb.query("PRAGMA table_info(cards)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) {
                columns += cursor.getString(nameIndex)
            }
        }
        assertTrue("cards must contain produced_mana", columns.contains("produced_mana"))

        // The pre-existing row survived and produced_mana backfilled to '' (never NULL).
        migratedDb.query(
            "SELECT produced_mana FROM cards WHERE scryfall_id = ?",
            arrayOf(SAMPLE_ID),
        ).use { cursor ->
            assertEquals(1, cursor.count)
            cursor.moveToFirst()
            assertTrue("produced_mana must not be NULL after migration", !cursor.isNull(0))
            assertEquals("", cursor.getString(0))
        }

        migratedDb.close()
    }

    /**
     * Minimal valid `cards` row for the v41 schema. Only NOT NULL columns must be
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
        put("legality_legacy", "not_legal")
        put("legality_vintage", "not_legal")
        put("legality_pauper", "not_legal")
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
        const val TEST_DB = "migration-test-produced-mana"
        const val SAMPLE_ID = "00000000-0000-0000-0000-000000000001"
    }
}
