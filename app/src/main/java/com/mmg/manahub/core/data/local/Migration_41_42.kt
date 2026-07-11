package com.mmg.manahub.core.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v41 → v42 — Deck Doctor / Community-Powered Suggestions plan, Phase 0.3 (D14): persist
 * `produced_mana` on `cards` as a compact WUBRG-subset string (e.g. `"WU"`), NOT a JSON blob.
 *
 * Purely ADDITIVE: adds one NOT-NULL `produced_mana` TEXT column to `cards`, DEFAULT `''`, so
 * existing rows backfill to "no known production" safely and no data is touched. Because this
 * only appends a column to `cards`, it does NOT delete/recreate the table and therefore does NOT
 * trigger the CASCADE on `user_card_collection` (the `CardEntity` upsert + FK ON DELETE RESTRICT
 * invariant is untouched — see CLAUDE.md "CardDao upsert"). The real value is backfilled lazily
 * per-card via the existing [com.mmg.manahub.core.data.repository.CachePolicy] on next Scryfall
 * touch — this migration intentionally does NOT trigger a mass re-fetch.
 *
 * Idempotent: the ADD COLUMN is guarded by a `columnExists` check, so a retry after a
 * mid-migration crash is safe (mirrors [MIGRATION_39_40]/[MIGRATION_40_41]).
 *
 * Column name/type/default mirrors EXACTLY what Room generates for the new
 * [com.mmg.manahub.core.data.local.entity.CardEntity.producedMana] field (`produced_mana`,
 * `TEXT NOT NULL DEFAULT ''`), verified against the v42 schema export so
 * `runMigrationsAndValidate` passes. Top-level `val` (mirroring [MIGRATION_39_40]/
 * [MIGRATION_40_41]) so the instrumented MigrationTestHelper test can reference it directly.
 */
val MIGRATION_41_42 = object : Migration(41, 42) {
    override fun migrate(db: SupportSQLiteDatabase) {
        if (!columnExists(db, "cards", "produced_mana")) {
            db.execSQL("ALTER TABLE `cards` ADD COLUMN `produced_mana` TEXT NOT NULL DEFAULT ''")
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
