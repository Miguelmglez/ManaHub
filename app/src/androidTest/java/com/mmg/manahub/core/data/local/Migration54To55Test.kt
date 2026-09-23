package com.mmg.manahub.core.data.local

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
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
 * Instrumented test for the v54 → v55 migration ([MIGRATION_54_55]).
 *
 * Verifies that the migration adds `trade_collection_sync.pending_apply` (existing records default
 * to 0, i.e. still "applied") and the nullable `owner_user_id` on `local_wishlists` and
 * `local_open_for_trade` (existing rows default to NULL, never a fabricated owner), and that the
 * result validates against Room's v55 schema.
 *
 * Schemas are loaded from androidTest assets (see `sourceSets.androidTest.assets` in
 * app/build.gradle.kts). Requires a connected device/emulator (`./gradlew connectedAndroidTest`).
 */
@RunWith(AndroidJUnit4::class)
class Migration54To55Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MtgDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate54to55_addsTradeColumns_andPreservesExistingRows() {
        helper.createDatabase(TEST_DB, 54).apply {
            insert("trade_collection_sync", SQLiteDatabase.CONFLICT_REPLACE, ContentValues().apply {
                put("proposal_id", "proposal-1")
                put("user_id", "user-1")
                put("synced_at", 1L)
            })
            insert("local_wishlists", SQLiteDatabase.CONFLICT_REPLACE, ContentValues().apply {
                put("id", "wish-1")
                put("scryfall_id", "card-1")
                put("quantity", 1)
                put("match_any_variant", 1)
                put("synced", 1)
                put("created_at", 1L)
            })
            insert("local_open_for_trade", SQLiteDatabase.CONFLICT_REPLACE, ContentValues().apply {
                put("id", "offer-1")
                put("local_collection_id", "row-1")
                put("scryfall_id", "card-1")
                put("quantity", 1)
                put("is_foil", 0)
                put("condition", "NM")
                put("language", "en")
                put("synced", 1)
                put("created_at", 1L)
            })
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 55, /* validateDroppedTables = */ true, MIGRATION_54_55)

        db.query("SELECT pending_apply FROM trade_collection_sync WHERE proposal_id = 'proposal-1'").use { cursor ->
            assertEquals(1, cursor.count)
            cursor.moveToFirst()
            assertEquals("an existing record stays an applied record", 0, cursor.getInt(0))
        }
        for (table in listOf("local_wishlists", "local_open_for_trade")) {
            db.query("SELECT owner_user_id FROM $table").use { cursor ->
                assertEquals(1, cursor.count)
                cursor.moveToFirst()
                assertTrue("$table.owner_user_id must be NULL after migration", cursor.isNull(0))
            }
        }
        db.close()
    }

    private companion object {
        const val TEST_DB = "migration-test-trades-owner-pending"
    }
}
