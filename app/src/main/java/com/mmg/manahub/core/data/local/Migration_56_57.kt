package com.mmg.manahub.core.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** v56 → v57 (MTG Today): creates `news_saved_items`, adds `content_sources.site_url`, drops both Competitive caches. */
val MIGRATION_56_57 = object : Migration(56, 57) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Must stay byte-identical to Room's generated SQL for NewsSavedItemEntity or runMigrationsAndValidate fails.
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `news_saved_items` (" +
                "`id` TEXT NOT NULL, `kind` TEXT NOT NULL, `title` TEXT NOT NULL, `description` TEXT NOT NULL, " +
                "`image_url` TEXT, `published_at` INTEGER NOT NULL, `source_id` TEXT NOT NULL, " +
                "`source_name` TEXT NOT NULL, `url` TEXT NOT NULL, `author` TEXT, `video_id` TEXT, " +
                "`channel_name` TEXT, `duration` TEXT, `saved_at` INTEGER NOT NULL, PRIMARY KEY(`id`))"
        )
        if (!columnExists(db, "content_sources", "site_url")) {
            db.execSQL("ALTER TABLE `content_sources` ADD COLUMN `site_url` TEXT")
        }
        db.execSQL("DROP TABLE IF EXISTS `competitive_meta_cache`")
        db.execSQL("DROP TABLE IF EXISTS `competitive_limited_ratings_cache`")
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
