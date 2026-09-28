package com.mmg.manahub.core.data.local
// COMMENTS_REVIEWED: 2026-09-08

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v53 → v54 — Deck Wizard Commander v3 plan, Phase 0 / E3 (fixes F3, D5) — persists the deck's
 * posture pin (Ramp/Tempo/Voltron/Toolbox/Group Hug/Group Slug).
 *
 * Purely ADDITIVE (one column, one table):
 *  - `decks.posture_override` — `TEXT` nullable, no default. A SEPARATE column from the existing
 *    `archetype_override`/`themes_override`/`tribe_override` pins (v43/v47) — see
 *    [com.mmg.manahub.core.model.Deck.postureOverride]'s KDoc for why it is not folded into
 *    `themes_override`'s JSON list instead. LOCAL-ONLY: not part of `DeckSyncDto`'s payload, same
 *    convention as its three sibling pin columns.
 *
 * Does not delete/recreate `decks`, so the `deck_cards` FK (`ON DELETE CASCADE` to `decks`) and
 * `CardDao`'s upsert + `user_card_collection` FK are both untouched (mirrors [MIGRATION_46_47]).
 *
 * Idempotent: the `ADD COLUMN` is guarded by a `columnExists` check, so a retry after a
 * mid-migration crash is safe.
 *
 * Column name/type mirrors EXACTLY what Room generates for the new
 * [com.mmg.manahub.core.data.local.entity.DeckEntity.postureOverride] field, verified against the
 * v54 schema export so `runMigrationsAndValidate` passes. Top-level `val` (mirroring every
 * migration since v36) so the instrumented `MigrationTestHelper` test can reference it directly.
 */
val MIGRATION_53_54 = object : Migration(53, 54) {
    override fun migrate(db: SupportSQLiteDatabase) {
        if (!columnExists(db, "decks", "posture_override")) {
            db.execSQL("ALTER TABLE `decks` ADD COLUMN `posture_override` TEXT")
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
