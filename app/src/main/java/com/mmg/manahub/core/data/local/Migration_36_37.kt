package com.mmg.manahub.core.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v36 → v37 — Draft Simulator data layer.
 *
 * Additive migration (no existing table is rebuilt, so no CASCADE risk):
 *  - Creates `draft_sessions` (one row per active/completed draft or sealed session;
 *    the full DraftState is stored as JSON in `stateJson`).
 *  - Adds `boosterVersion TEXT` (nullable) to the existing `draft_sets` cache table.
 *    A null value means the set has no booster.json published and is not simulable.
 *
 * Top-level `val` (not nested in DatabaseModule like the v25→v36 migrations) so the
 * instrumented MigrationTestHelper test can reference it directly.
 */
val MIGRATION_36_37 = object : Migration(36, 37) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // ── draft_sessions ───────────────────────────────────────────────────
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `draft_sessions` (
                `id` TEXT NOT NULL,
                `setCode` TEXT NOT NULL,
                `mode` TEXT NOT NULL,
                `status` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `stateSchemaVersion` INTEGER NOT NULL,
                `stateJson` TEXT NOT NULL,
                `result_deck_id` TEXT,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_draft_sessions_status_updatedAt` " +
                "ON `draft_sessions` (`status`, `updatedAt`)"
        )

        // ── draft_sets: add boosterVersion (nullable, no default) ─────────────
        if (!columnExistsInTable(db, "draft_sets", "boosterVersion")) {
            db.execSQL("ALTER TABLE `draft_sets` ADD COLUMN `boosterVersion` TEXT")
        }
    }
}

/**
 * Idempotency guard: returns true if [columnName] already exists in [tableName].
 * Local to this file so the migration can be unit/instrument-tested without the
 * private helper inside DatabaseModule.
 */
private fun columnExistsInTable(
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
