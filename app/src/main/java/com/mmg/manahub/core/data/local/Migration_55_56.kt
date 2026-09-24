package com.mmg.manahub.core.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v55 → v56 — Trades audit 2026-09-23 (H4 + H8). Purely ADDITIVE, three columns:
 *  - `trade_collection_sync.pending_apply` — `INTEGER NOT NULL DEFAULT 0`. A row with 1 records the
 *    user's choice to update the collection once the trade reaches COMPLETED; existing rows are
 *    applied records, so the default 0 keeps their meaning.
 *  - `local_wishlists.owner_user_id` and `local_open_for_trade.owner_user_id` — `TEXT` nullable, no
 *    default. NULL means a guest row (or a pre-v56 row whose owner is unknown); the account-switch
 *    cleanup treats unknown-owner synced rows as re-downloadable.
 *
 * No table is recreated, so `CardDao`'s upsert and every index are untouched. Each `ADD COLUMN` is
 * guarded by a `columnExists` check so a retry after a mid-migration crash is safe. Column
 * types/defaults mirror what Room generates for the entity fields so `runMigrationsAndValidate`
 * passes. Top-level `val` so the instrumented `MigrationTestHelper` test can reference it.
 */
val MIGRATION_55_56 = object : Migration(55, 56) {
    override fun migrate(db: SupportSQLiteDatabase) {
        if (!columnExists(db, "trade_collection_sync", "pending_apply")) {
            db.execSQL("ALTER TABLE `trade_collection_sync` ADD COLUMN `pending_apply` INTEGER NOT NULL DEFAULT 0")
        }
        if (!columnExists(db, "local_wishlists", "owner_user_id")) {
            db.execSQL("ALTER TABLE `local_wishlists` ADD COLUMN `owner_user_id` TEXT")
        }
        if (!columnExists(db, "local_open_for_trade", "owner_user_id")) {
            db.execSQL("ALTER TABLE `local_open_for_trade` ADD COLUMN `owner_user_id` TEXT")
        }
    }

    private fun columnExists(
        db: SupportSQLiteDatabase,
        tableName: String,
        columnName: String,
    ): Boolean {
        db.query("PRAGMA table_info($tableName)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            if (nameIndex == -1) return false
            while (cursor.moveToNext()) {
                if (cursor.getString(nameIndex) == columnName) return true
            }
        }
        return false
    }
}
