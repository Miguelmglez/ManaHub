package com.mmg.manahub.core.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v40 → v41 — Community Decks foundation (Batch 1).
 *
 * Two purely ADDITIVE changes:
 *  1. Four nullable attribution columns on `decks` (`source_url`, `source_author`,
 *     `source_service`, `imported_at`) recording where an imported deck came from.
 *     All are nullable with NO DEFAULT (matching exactly what Room generates for the
 *     four new nullable [com.mmg.manahub.core.data.local.entity.DeckEntity] fields),
 *     so existing rows keep NULL and no data is touched. Because this only appends
 *     columns to `decks`, the table is not recreated and there is no CASCADE risk.
 *  2. A new `community_deck_cache` table (pure cache of fetched Archidekt deck
 *     responses; no FK, no dependents).
 *
 * Idempotent: each ADD COLUMN is guarded by a [columnExists] check and the table is
 * created with `IF NOT EXISTS`, so a retry after a mid-migration crash is safe.
 *
 * Top-level `val` (mirroring [MIGRATION_39_40]) so the instrumented MigrationTestHelper
 * test can reference it directly. Column names/types/defaults mirror EXACTLY what Room
 * generates for the v41 schema so `runMigrationsAndValidate` passes.
 */
val MIGRATION_40_41 = object : Migration(40, 41) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Attribution columns on decks (all nullable, no DEFAULT needed).
        if (!columnExists(db, "decks", "source_url")) {
            db.execSQL("ALTER TABLE `decks` ADD COLUMN `source_url` TEXT")
        }
        if (!columnExists(db, "decks", "source_author")) {
            db.execSQL("ALTER TABLE `decks` ADD COLUMN `source_author` TEXT")
        }
        if (!columnExists(db, "decks", "source_service")) {
            db.execSQL("ALTER TABLE `decks` ADD COLUMN `source_service` TEXT")
        }
        if (!columnExists(db, "decks", "imported_at")) {
            db.execSQL("ALTER TABLE `decks` ADD COLUMN `imported_at` INTEGER")
        }

        // Community deck cache table.
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `community_deck_cache` (
                `archidekt_id` INTEGER NOT NULL,
                `name` TEXT NOT NULL,
                `owner_username` TEXT NOT NULL,
                `format` TEXT NOT NULL,
                `description` TEXT NOT NULL,
                `view_count` INTEGER NOT NULL,
                `card_count` INTEGER NOT NULL,
                `response_json` TEXT NOT NULL,
                `cached_at` INTEGER NOT NULL,
                PRIMARY KEY(`archidekt_id`)
            )
            """.trimIndent()
        )
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
