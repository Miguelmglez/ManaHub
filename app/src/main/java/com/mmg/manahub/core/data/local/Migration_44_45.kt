package com.mmg.manahub.core.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v44 → v45 — News feature improvements Phase 1: per-source refresh watermark + conditional GET
 * support on `content_sources`.
 *
 * Adds three columns:
 *  - `last_fetched_at` (INTEGER NOT NULL DEFAULT 0) — wall-clock millis of the last successful
 *    fetch (200 or 304) of this source; `0` means "never fetched" (always stale).
 *  - `etag` (TEXT, nullable) — HTTP `ETag` response header captured on the last 200.
 *  - `last_modified` (TEXT, nullable) — HTTP `Last-Modified` response header captured on the
 *    last 200.
 *
 * Purely ADDITIVE: three new columns on `content_sources`, no FK, no dependents, no existing
 * row data touched. `content_sources` has no FK to any other table, so this cannot cascade.
 *
 * Idempotent: every `ADD COLUMN` is guarded by a `columnExists` check (mirrors
 * [MIGRATION_39_40]/[MIGRATION_40_41]/[MIGRATION_41_42]/[MIGRATION_42_43]), so a retry after a
 * mid-migration crash is safe.
 *
 * Column names/types/defaults mirror EXACTLY what Room generates for the new
 * [com.mmg.manahub.core.data.local.entity.ContentSourceEntity.lastFetchedAt] /
 * `.etag` / `.lastModified` fields, verified against the v45 schema export so
 * `runMigrationsAndValidate` passes. Top-level `val` (mirroring the prior migrations) so the
 * instrumented `MigrationTestHelper` test can reference it directly.
 */
val MIGRATION_44_45 = object : Migration(44, 45) {
    override fun migrate(db: SupportSQLiteDatabase) {
        if (!columnExists(db, "content_sources", "last_fetched_at")) {
            db.execSQL("ALTER TABLE `content_sources` ADD COLUMN `last_fetched_at` INTEGER NOT NULL DEFAULT 0")
        }
        if (!columnExists(db, "content_sources", "etag")) {
            db.execSQL("ALTER TABLE `content_sources` ADD COLUMN `etag` TEXT")
        }
        if (!columnExists(db, "content_sources", "last_modified")) {
            db.execSQL("ALTER TABLE `content_sources` ADD COLUMN `last_modified` TEXT")
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
