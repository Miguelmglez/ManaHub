package com.mmg.manahub.core.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v37 → v38 — Deck Doctor scoring prerequisite (`edhrec_rank` / `penny_rank`).
 *
 * Purely additive, non-destructive migration. Adds two NULLABLE Int columns to the
 * existing `cards` table:
 *  - `edhrec_rank`  (INTEGER, nullable) — EDHREC popularity rank (lower = more played).
 *  - `penny_rank`   (INTEGER, nullable) — Penny Dreadful popularity rank.
 *
 * Both are nullable with NO default: absence of a rank is meaningful (the card is not
 * ranked) and must not be coerced to a sentinel. Existing rows get NULL automatically.
 * No table is rebuilt, so there is no CASCADE risk to `user_card_collection` (the
 * `CardEntity` upsert + FK ON DELETE RESTRICT invariant is untouched).
 *
 * Top-level `val` (mirroring [MIGRATION_36_37]) so the instrumented MigrationTestHelper
 * test can reference it directly. The `columnExistsInTable` guard keeps the migration
 * idempotent if Room retries after a mid-migration crash.
 */
val MIGRATION_37_38 = object : Migration(37, 38) {
    override fun migrate(db: SupportSQLiteDatabase) {
        if (!columnExistsInTable(db, "cards", "edhrec_rank")) {
            db.execSQL("ALTER TABLE `cards` ADD COLUMN `edhrec_rank` INTEGER")
        }
        if (!columnExistsInTable(db, "cards", "penny_rank")) {
            db.execSQL("ALTER TABLE `cards` ADD COLUMN `penny_rank` INTEGER")
        }
    }
}

/**
 * Idempotency guard: returns true if [columnName] already exists in [tableName].
 * File-private here so this migration can be unit/instrument-tested without depending
 * on the private helper inside [MIGRATION_36_37]'s file or DatabaseModule.
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
