package com.mmg.manahub.core.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v50 → v51 — Competitive feature, Phase 2: two new cache tables backed by the
 * `manahub-competitive` Cloudflare Worker (weekly MTG metagame rankings + 17lands Limited card
 * ratings).
 *
 * Purely ADDITIVE: two new tables, no FK, no dependents, no existing table touched — mirrors
 * [MIGRATION_43_44]'s `community_aggregate_cache` addition (the closest "denormalized JSON blob
 * cache, keyed by a Worker-owned key" precedent in this schema, itself mirrored again by
 * [MIGRATION_47_48]'s `combo_cache`). Both new tables follow the same "fetch from Worker, cache
 * the raw JSON, TTL-check on read" shape:
 *  - `competitive_meta_cache`: one row per format, PK `format`.
 *  - `competitive_limited_ratings_cache`: one row per set, PK `set_code`. The true upstream
 *    identity is (`set_code`, `format`) — 17lands ratings differ per draft format — but this
 *    table treats `set_code` alone as the primary key for v1 simplicity, since the app only ever
 *    queries PremierDraft ratings today; `format` is still stored as a plain column for
 *    traceability. Unlike `community_aggregate_cache`/`combo_cache`, which fold their whole
 *    identity into a single opaque `key` string, these two keep typed identity columns (`format`,
 *    `set_code`) because callers need to query/filter by them directly (e.g. "which sets are
 *    cached"), not just do point lookups by an opaque key.
 *
 * Idempotent: both tables are created with `IF NOT EXISTS`, so a retry after a mid-migration
 * crash is safe.
 *
 * Top-level `val` (mirroring [MIGRATION_49_50]) so the instrumented MigrationTestHelper test can
 * reference it directly. Column names/types mirror EXACTLY what Room generates for the new
 * [com.mmg.manahub.core.data.local.entity.CompetitiveMetaCacheEntity] and
 * [com.mmg.manahub.core.data.local.entity.CompetitiveLimitedRatingsCacheEntity] so
 * `runMigrationsAndValidate` passes.
 */
val MIGRATION_50_51 = object : Migration(50, 51) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `competitive_meta_cache` (
                `format` TEXT NOT NULL,
                `response_json` TEXT NOT NULL,
                `cached_at` INTEGER NOT NULL,
                PRIMARY KEY(`format`)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `competitive_limited_ratings_cache` (
                `set_code` TEXT NOT NULL,
                `format` TEXT NOT NULL,
                `response_json` TEXT NOT NULL,
                `cached_at` INTEGER NOT NULL,
                PRIMARY KEY(`set_code`)
            )
            """.trimIndent()
        )
    }
}
