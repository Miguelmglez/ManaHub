package com.mmg.manahub.core.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v42 → v43 — Deck Doctor / Community-Powered Suggestions plan, Phase 1.5 (D2): persist the
 * archetype pin on `decks` — `archetype_override` (raw `ArchetypeId` enum-name TEXT, nullable)
 * and `themes_override` (JSON array of `ThemeId` enum-name strings, nullable TEXT).
 *
 * Purely ADDITIVE: adds two nullable TEXT columns to `decks`, both defaulting to SQL NULL (no
 * `NOT NULL DEFAULT` needed since the domain model already treats null/empty as "no pin, infer").
 * This does not delete/recreate `decks` and therefore does not affect `deck_cards`' FK (mirrors
 * [MIGRATION_40_41]/[MIGRATION_41_42]).
 *
 * Idempotent: both `ADD COLUMN`s are guarded by a `columnExists` check, so a retry after a
 * mid-migration crash is safe.
 *
 * Column names/types mirror EXACTLY what Room generates for the new
 * [com.mmg.manahub.core.data.local.entity.DeckEntity.archetypeOverride] /
 * `.themesOverride` fields (`archetype_override` TEXT NULL, `themes_override` TEXT NULL),
 * verified against the v43 schema export so `runMigrationsAndValidate` passes. Top-level `val`
 * (mirroring the prior migrations) so the instrumented `MigrationTestHelper` test can reference
 * it directly.
 */
val MIGRATION_42_43 = object : Migration(42, 43) {
    override fun migrate(db: SupportSQLiteDatabase) {
        if (!columnExists(db, "decks", "archetype_override")) {
            db.execSQL("ALTER TABLE `decks` ADD COLUMN `archetype_override` TEXT")
        }
        if (!columnExists(db, "decks", "themes_override")) {
            db.execSQL("ALTER TABLE `decks` ADD COLUMN `themes_override` TEXT")
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
