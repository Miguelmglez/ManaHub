package com.mmg.manahub.core.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Adds a durable outbox for remote trade-offer deletions after local collection commits. */
val MIGRATION_56_57 = object : Migration(56, 57) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `trade_offer_cleanup` (
                `proposal_id` TEXT NOT NULL,
                `user_id` TEXT NOT NULL,
                `collection_id` TEXT NOT NULL,
                PRIMARY KEY(`proposal_id`, `user_id`, `collection_id`)
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_trade_offer_cleanup_user_id` ON `trade_offer_cleanup` (`user_id`)")
    }
}
