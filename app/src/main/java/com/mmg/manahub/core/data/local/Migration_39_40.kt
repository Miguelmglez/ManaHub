package com.mmg.manahub.core.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v39 → v40 — Deck Doctor Phase 4 (D2): persist the missing Scryfall format legalities.
 *
 * Purely ADDITIVE: adds three NOT-NULL `legality_*` columns to the `cards` table, each with a
 * `'not_legal'` DEFAULT so existing rows backfill safely and no data is touched. Because this only
 * appends columns to `cards`, it does NOT delete/recreate the table and therefore does NOT trigger
 * the CASCADE on `user_card_collection` (the `CardEntity` upsert + FK ON DELETE RESTRICT invariant
 * is untouched). Correct per-format values arrive on the next Scryfall fetch for each card.
 *
 * Idempotent: each ADD COLUMN is guarded by a `columnExists` check, so a retry after a
 * mid-migration crash is safe.
 *
 * Column names/types/defaults mirror EXACTLY what Room generates for the three new
 * [com.mmg.manahub.core.data.local.entity.CardEntity] fields (`legality_legacy`,
 * `legality_vintage`, `legality_pauper`, all `TEXT NOT NULL DEFAULT 'not_legal'`), verified against
 * the v40 schema export so `runMigrationsAndValidate` passes. Top-level `val` (mirroring
 * [MIGRATION_38_39]) so the instrumented MigrationTestHelper test can reference it directly.
 */
val MIGRATION_39_40 = object : Migration(39, 40) {
    override fun migrate(db: SupportSQLiteDatabase) {
        if (!columnExists(db, "cards", "legality_legacy")) {
            db.execSQL("ALTER TABLE `cards` ADD COLUMN `legality_legacy` TEXT NOT NULL DEFAULT 'not_legal'")
        }
        if (!columnExists(db, "cards", "legality_vintage")) {
            db.execSQL("ALTER TABLE `cards` ADD COLUMN `legality_vintage` TEXT NOT NULL DEFAULT 'not_legal'")
        }
        if (!columnExists(db, "cards", "legality_pauper")) {
            db.execSQL("ALTER TABLE `cards` ADD COLUMN `legality_pauper` TEXT NOT NULL DEFAULT 'not_legal'")
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
