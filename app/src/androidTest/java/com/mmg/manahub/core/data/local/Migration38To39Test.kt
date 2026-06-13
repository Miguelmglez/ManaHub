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
 * Instrumented test for the v38 → v39 migration ([MIGRATION_38_39]).
 *
 * Verifies that the additive gamification migration:
 *  - creates all six new tables,
 *  - seeds the singleton `player_progression` row (id = 1, total_xp = 0, level = 1), and
 *  - preserves a pre-existing `cards` row (no user data touched).
 *
 * Schemas are loaded from androidTest assets. Requires a connected device/emulator
 * (`./gradlew connectedAndroidTest`).
 */
@RunWith(AndroidJUnit4::class)
class Migration38To39Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MtgDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate38to39_createsGamificationTables_seedsProgression_andPreservesCard() {
        // Create the database at v38 and seed one minimal card row, then close so the
        // migration can run.
        helper.createDatabase(TEST_DB, 38).apply {
            insert("cards", android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE, sampleCard())
            close()
        }

        val migratedDb = helper.runMigrationsAndValidate(
            TEST_DB,
            39,
            /* validateDroppedTables = */ true,
            MIGRATION_38_39,
        )

        // All six new tables exist.
        val expectedTables = listOf(
            "player_progression",
            "xp_transactions",
            "achievement_progress",
            "quest_instances",
            "streaks",
            "entitlements",
        )
        for (table in expectedTables) {
            migratedDb.query(
                "SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?",
                arrayOf(table),
            ).use { cursor ->
                assertEquals("table $table must exist after migration", 1, cursor.count)
            }
        }

        // The singleton progression row was seeded.
        migratedDb.query(
            "SELECT total_xp, level FROM player_progression WHERE id = 1",
        ).use { cursor ->
            assertEquals(1, cursor.count)
            cursor.moveToFirst()
            assertEquals("seeded total_xp", 0L, cursor.getLong(0))
            assertEquals("seeded level", 1, cursor.getInt(1))
        }

        // The unique idempotency index exists on xp_transactions.
        migratedDb.query(
            "SELECT name FROM sqlite_master WHERE type = 'index' AND name = ?",
            arrayOf("index_xp_transactions_idempotency_key"),
        ).use { cursor ->
            assertTrue("unique idempotency index must exist", cursor.count == 1)
        }

        // The pre-existing card survived.
        migratedDb.query(
            "SELECT scryfall_id FROM cards WHERE scryfall_id = ?",
            arrayOf(SAMPLE_ID),
        ).use { cursor ->
            assertEquals("pre-existing card must survive", 1, cursor.count)
        }

        migratedDb.close()
    }

    /**
     * Minimal valid `cards` row for the v38 schema. Only NOT NULL columns must be populated;
     * nullable columns are omitted so they default to NULL. Mirrors the v37→v38 test, plus the
     * v38 ranks are nullable and therefore omitted.
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
        const val TEST_DB = "migration-test-gamification"
        const val SAMPLE_ID = "00000000-0000-0000-0000-000000000001"
    }
}
