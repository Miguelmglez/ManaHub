package com.mmg.manahub.core.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v38 → v39 — Gamification engine foundations (ADR-002 §8).
 *
 * Purely ADDITIVE: creates the six new gamification tables and their indices. No existing
 * table is touched, so there is no CASCADE risk to user data (the `CardEntity` upsert + FK
 * ON DELETE RESTRICT invariant is untouched). The singleton `player_progression` row (id = 1)
 * is seeded with `INSERT OR IGNORE` so the engine always finds a row to advance.
 *
 * Idempotent: every statement uses `IF NOT EXISTS` / `OR IGNORE`, so a retry after a
 * mid-migration crash is safe.
 *
 * Column types/names mirror EXACTLY what Room generates for the six entities (verified against
 * the v39 schema export) so `runMigrationsAndValidate` passes. Top-level `val` (mirroring
 * [MIGRATION_37_38]) so the instrumented MigrationTestHelper test can reference it directly.
 */
val MIGRATION_38_39 = object : Migration(38, 39) {
    override fun migrate(db: SupportSQLiteDatabase) {

        // ── player_progression (singleton row id = 1) ───────────────────────────
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `player_progression` (
                `id`         INTEGER NOT NULL,
                `total_xp`   INTEGER NOT NULL,
                `level`      INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        // Seed the singleton row. INSERT OR IGNORE so a retry does not reset progression.
        db.execSQL(
            "INSERT OR IGNORE INTO `player_progression` (`id`, `total_xp`, `level`, `updated_at`) " +
                "VALUES (1, 0, 1, 0)"
        )

        // ── xp_transactions (append-only ledger; UNIQUE idempotency_key) ─────────
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `xp_transactions` (
                `id`              INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `idempotency_key` TEXT NOT NULL,
                `amount`          INTEGER NOT NULL,
                `source_category` TEXT NOT NULL,
                `source_ref`      TEXT,
                `created_at`      INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_xp_transactions_idempotency_key` " +
                "ON `xp_transactions` (`idempotency_key`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_xp_transactions_source_category` " +
                "ON `xp_transactions` (`source_category`)"
        )

        // ── achievement_progress ─────────────────────────────────────────────────
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `achievement_progress` (
                `achievement_id` TEXT NOT NULL,
                `current_value`  INTEGER NOT NULL,
                `tier_reached`   INTEGER NOT NULL,
                `unlocked_at`    INTEGER,
                `celebrated_at`  INTEGER,
                PRIMARY KEY(`achievement_id`)
            )
            """.trimIndent()
        )

        // ── quest_instances ──────────────────────────────────────────────────────
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `quest_instances` (
                `id`           TEXT NOT NULL,
                `template_id`  TEXT NOT NULL,
                `period`       TEXT NOT NULL,
                `period_key`   TEXT NOT NULL,
                `target`       INTEGER NOT NULL,
                `progress`     INTEGER NOT NULL,
                `status`       TEXT NOT NULL,
                `expires_at`   INTEGER NOT NULL,
                `xp_reward`    INTEGER NOT NULL,
                `token_reward` INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_quest_instances_period_key` " +
                "ON `quest_instances` (`period_key`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_quest_instances_status` " +
                "ON `quest_instances` (`status`)"
        )

        // ── streaks ──────────────────────────────────────────────────────────────
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `streaks` (
                `type`             TEXT NOT NULL,
                `current`          INTEGER NOT NULL,
                `longest`          INTEGER NOT NULL,
                `last_active_date` TEXT NOT NULL,
                `freeze_tokens`    INTEGER NOT NULL,
                PRIMARY KEY(`type`)
            )
            """.trimIndent()
        )

        // ── entitlements ─────────────────────────────────────────────────────────
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `entitlements` (
                `unlockable_id` TEXT NOT NULL,
                `unlocked_at`   INTEGER NOT NULL,
                `source`        TEXT NOT NULL,
                PRIMARY KEY(`unlockable_id`)
            )
            """.trimIndent()
        )
    }
}
