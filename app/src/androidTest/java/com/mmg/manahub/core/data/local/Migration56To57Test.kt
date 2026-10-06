package com.mmg.manahub.core.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration56To57Test {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MtgDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate56to57AddsTradeOfferCleanupOutbox() {
        helper.createDatabase(TEST_DB, 56).close()

        val database = helper.runMigrationsAndValidate(TEST_DB, 57, true, MIGRATION_56_57)
        database.execSQL("INSERT INTO trade_offer_cleanup (proposal_id, user_id, collection_id) VALUES ('p', 'a', 'c')")
        database.query("SELECT COUNT(*) FROM trade_offer_cleanup WHERE user_id = 'a'").use { cursor ->
            cursor.moveToFirst()
            assertEquals(1, cursor.getInt(0))
        }
        database.close()
    }

    private companion object {
        const val TEST_DB = "migration-test-trade-offer-cleanup"
    }
}