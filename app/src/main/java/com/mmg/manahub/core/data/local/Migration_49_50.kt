package com.mmg.manahub.core.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v49 → v50 — Daily Puzzle feature, Batch B1 (foundation). A new `puzzle_results` table caching
 * one row per calendar day the user attempted a puzzle, keyed by `puzzle_date` (ISO `yyyy-MM-dd`).
 *
 * Purely ADDITIVE: a single new table, no FK, no dependents, no existing table touched — mirrors
 * [MIGRATION_48_49]'s `card_strategy_tags_cache` addition exactly.
 *
 * Idempotent: the table is created with `IF NOT EXISTS`, so a retry after a mid-migration crash is
 * safe.
 *
 * Top-level `val` (mirroring [MIGRATION_48_49]) so the instrumented MigrationTestHelper test can
 * reference it directly. Column names/types mirror EXACTLY what Room generates for
 * [com.mmg.manahub.core.data.local.entity.PuzzleResultEntity] so `runMigrationsAndValidate` passes.
 */
val MIGRATION_49_50 = object : Migration(49, 50) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `puzzle_results` (
                `puzzle_date`   TEXT NOT NULL,
                `type`          TEXT NOT NULL,
                `attempts`      INTEGER NOT NULL,
                `solved`        INTEGER NOT NULL,
                `perfect`       INTEGER NOT NULL,
                `elapsed_ms`    INTEGER NOT NULL,
                `started_at`    INTEGER NOT NULL,
                `guesses_json`  TEXT NOT NULL,
                `completed_at`  INTEGER,
                PRIMARY KEY(`puzzle_date`)
            )
            """.trimIndent()
        )
    }
}
