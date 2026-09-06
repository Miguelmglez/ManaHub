package com.mmg.manahub.core.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v52 → v53 — Collection sync data-loss fix (`linear-moseying-yeti` plan), Phase 2.
 *
 * Drops the `RESTRICT` foreign key from `user_card_collection.scryfall_id` -> `cards.scryfall_id`
 * (see [com.mmg.manahub.core.data.local.entity.UserCardCollectionEntity]'s class KDoc for the
 * full rationale). An ownership record must never depend on cache metadata being available: the
 * old FK forced Room to reject a collection row whenever its card wasn't cached yet, which is
 * exactly what made `SyncManager`'s watermark-skip-on-missing-metadata bug a PERMANENT data-loss
 * path instead of a transient one.
 *
 * SQLite has no `ALTER TABLE ... DROP CONSTRAINT` — this uses the project's standard 12-step
 * table-recreation pattern (see `MIGRATION_35_36`/`MIGRATION_32_33` for prior art): create a new
 * table with the target schema (no `foreignKeys`), copy every row verbatim, drop the old table,
 * rename, then recreate every index. The five index names below are asserted to match Room's
 * generated names BYTE FOR BYTE by `Migration52To53Test.migrate52To53_indicesMatchRoomSchema` —
 * `runMigrationsAndValidate()` fails at the FIRST APP LAUNCH for EVERY user if a generated name
 * here doesn't match what `@Entity`'s `indices` produces, so this was verified against a fresh
 * `app/schemas/.../53.json` export, not guessed from the v35→v36 precedent alone.
 *
 * No `PRAGMA foreign_keys` statement here — Room already runs each migration inside its own
 * transaction with `PRAGMA foreign_keys` handling done at the connection level; issuing it again
 * inside the migration body is a no-op.
 *
 * `CardDao.evictStaleCache`'s cache-eviction query already excludes ids referenced by
 * `user_card_collection`/`deck_cards` via `NOT IN` subqueries (verified before writing this
 * migration, not assumed) — it never depended on this FK, so eviction behaviour is unchanged.
 */
val MIGRATION_52_53 = object : Migration(52, 53) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Step 1: create the new table with the target schema (identical columns, no FK).
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `user_card_collection_new` (
                `id` TEXT NOT NULL,
                `user_id` TEXT,
                `scryfall_id` TEXT NOT NULL,
                `quantity` INTEGER NOT NULL,
                `is_foil` INTEGER NOT NULL,
                `condition` TEXT NOT NULL,
                `language` TEXT NOT NULL,
                `is_for_trade` INTEGER NOT NULL,
                `is_deleted` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL,
                `created_at` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        // Step 2: copy every row verbatim -- no column shape change, so this is a straight copy.
        db.execSQL(
            """
            INSERT INTO `user_card_collection_new` (
                `id`, `user_id`, `scryfall_id`, `quantity`, `is_foil`, `condition`,
                `language`, `is_for_trade`, `is_deleted`, `updated_at`, `created_at`
            )
            SELECT `id`, `user_id`, `scryfall_id`, `quantity`, `is_foil`, `condition`,
                   `language`, `is_for_trade`, `is_deleted`, `updated_at`, `created_at`
            FROM `user_card_collection`
            """.trimIndent()
        )
        // Step 3-4: drop the old table, rename the new one into place.
        db.execSQL("DROP TABLE `user_card_collection`")
        db.execSQL("ALTER TABLE `user_card_collection_new` RENAME TO `user_card_collection`")

        // Step 5: recreate every index Room's @Entity(indices = [...]) declaration expects.
        // Names must match Room's generated `index_<table>_<col1>_<col2>...` convention exactly.
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_user_card_collection_scryfall_id` ON `user_card_collection` (`scryfall_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_user_card_collection_user_id` ON `user_card_collection` (`user_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_user_card_collection_updated_at` ON `user_card_collection` (`updated_at`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_user_card_collection_is_deleted` ON `user_card_collection` (`is_deleted`)")
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_user_card_collection_user_id_scryfall_id_is_foil_condition_language` " +
                "ON `user_card_collection` (`user_id`, `scryfall_id`, `is_foil`, `condition`, `language`)"
        )
    }
}
