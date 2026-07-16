package com.mmg.manahub.core.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v45 → v46 — Card Versions & Languages plan, Phase 1A (data foundations): persists Scryfall's
 * `oracle_id` on `cards` so every printing/language of the same card can be related without a
 * network round-trip.
 *
 * Purely ADDITIVE: adds one NOT-NULL `oracle_id` TEXT column to `cards`, DEFAULT `''`, plus a
 * supporting index. Existing rows backfill to `''` (treated as "unknown oracle" and related to
 * other rows by an exact match on the card's English `name` as a fallback — see
 * [com.mmg.manahub.core.model.Card.oracleId]'s KDoc) until their next Scryfall refresh populates
 * the real value. Because this only appends a column + index to `cards`, it does NOT
 * delete/recreate the table and therefore does NOT trigger the CASCADE DELETE on
 * `user_card_collection` (the `CardDao` upsert + FK `ON DELETE RESTRICT` invariant is untouched —
 * see CLAUDE.md "CardDao upsert").
 *
 * Idempotent: the ADD COLUMN is guarded by a `columnExists` check and the index creation uses
 * `IF NOT EXISTS`, so a retry after a mid-migration crash is safe (mirrors
 * [MIGRATION_39_40]/[MIGRATION_40_41]/[MIGRATION_41_42]).
 *
 * Column name/type/default and index name mirror EXACTLY what Room generates for the new
 * [com.mmg.manahub.core.data.local.entity.CardEntity.oracleId] field (`oracle_id`,
 * `TEXT NOT NULL DEFAULT ''`) and its `@Index("oracle_id")` (`index_cards_oracle_id`), verified
 * against the v46 schema export so `runMigrationsAndValidate` passes. Top-level `val` (mirroring
 * every migration since v36) so the instrumented MigrationTestHelper test can reference it
 * directly.
 */
val MIGRATION_45_46 = object : Migration(45, 46) {
    override fun migrate(db: SupportSQLiteDatabase) {
        if (!columnExists(db, "cards", "oracle_id")) {
            db.execSQL("ALTER TABLE `cards` ADD COLUMN `oracle_id` TEXT NOT NULL DEFAULT ''")
        }
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_cards_oracle_id` ON `cards` (`oracle_id`)")
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
