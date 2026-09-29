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
class Migration57To58Test {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MtgDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate57to58PreservesTradeCleanupAndAddsWishlistOutbox() {
        helper.createDatabase("migration-test-trade-wishlist-cleanup", 57).close()

        val database = helper.runMigrationsAndValidate(
            "migration-test-trade-wishlist-cleanup", 58, true, MIGRATION_57_58,
        )
        database.execSQL("INSERT INTO trade_wishlist_cleanup (user_id, wishlist_id, target_quantity) VALUES ('a', 'w', 2)")
        database.query("SELECT target_quantity FROM trade_wishlist_cleanup WHERE user_id = 'a' AND wishlist_id = 'w'").use { cursor ->
            cursor.moveToFirst()
            assertEquals(2, cursor.getInt(0))
        }
        database.close()
    }
}
