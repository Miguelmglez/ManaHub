package com.mmg.manahub.core.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v46 → v47 — Deck Engine Unification plan (`docs/plans/deck-engine-unification-plan.md`, Phase 0.2)
 * — persists the D4 "hard no-cut guarantee" plumbing: a deck-level lock flag + tribe pin, and
 * per-card provenance.
 *
 * Purely ADDITIVE (three columns, two tables):
 *  - `decks.strategy_locked` — `INTEGER NOT NULL DEFAULT 0` (Boolean). True for a wizard-built deck;
 *    gates the Deck Doctor's cut list + Suggestions "Deck plan" editor (consumed starting Phase 2,
 *    RUN 2 — this migration only persists the column).
 *  - `decks.tribe_override` — `TEXT` nullable, no default. A SEPARATE column from the existing
 *    `archetype_override`/`themes_override` pin (v43) — see [com.mmg.manahub.core.model.Deck
 *    .tribeOverride]'s KDoc for why it is not folded into `themes_override`'s JSON list instead.
 *  - `deck_cards.source` — `TEXT NOT NULL DEFAULT 'USER'` — raw
 *    [com.mmg.manahub.core.model.DeckCardSource] enum-name string. Every pre-migration row backfills
 *    to `'USER'` (the correct provenance for every card placed before this feature existed).
 *
 * None of these deletes/recreates `decks` or `deck_cards`, so the `deck_cards` FK
 * (`ON DELETE CASCADE` to `decks`) and the `CardDao` upsert + `user_card_collection` FK
 * (`ON DELETE RESTRICT`) are both untouched (mirrors [MIGRATION_42_43]/[MIGRATION_45_46]).
 *
 * Idempotent: every `ADD COLUMN` is guarded by a `columnExists` check, so a retry after a
 * mid-migration crash is safe.
 *
 * Column names/types/defaults mirror EXACTLY what Room generates for the new
 * [com.mmg.manahub.core.data.local.entity.DeckEntity.strategyLocked] /
 * `.tribeOverride` / [com.mmg.manahub.core.data.local.entity.DeckCardEntity.source] fields,
 * verified against the v47 schema export so `runMigrationsAndValidate` passes. Top-level `val`
 * (mirroring every migration since v36) so the instrumented `MigrationTestHelper` test can
 * reference it directly.
 */
val MIGRATION_46_47 = object : Migration(46, 47) {
    override fun migrate(db: SupportSQLiteDatabase) {
        if (!columnExists(db, "decks", "strategy_locked")) {
            db.execSQL("ALTER TABLE `decks` ADD COLUMN `strategy_locked` INTEGER NOT NULL DEFAULT 0")
        }
        if (!columnExists(db, "decks", "tribe_override")) {
            db.execSQL("ALTER TABLE `decks` ADD COLUMN `tribe_override` TEXT")
        }
        if (!columnExists(db, "deck_cards", "source")) {
            db.execSQL("ALTER TABLE `deck_cards` ADD COLUMN `source` TEXT NOT NULL DEFAULT 'USER'")
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
