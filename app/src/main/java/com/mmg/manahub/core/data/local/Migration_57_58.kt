package com.mmg.manahub.core.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Persists absolute wishlist targets committed with trade collection changes. */
val MIGRATION_57_58 = object : Migration(57, 58) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `trade_wishlist_cleanup` (
                `user_id` TEXT NOT NULL,
                `wishlist_id` TEXT NOT NULL,
                `target_quantity` INTEGER NOT NULL,
                PRIMARY KEY(`user_id`, `wishlist_id`)
            )
        """.trimIndent())
    }
}
