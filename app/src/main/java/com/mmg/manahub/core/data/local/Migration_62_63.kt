package com.mmg.manahub.core.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Owner-bound dirty wishlist deliveries remain independent of collection apply markers. */
val MIGRATION_62_63 = object : Migration(62,63) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS collection_transfer_wishlist_dirty(wishlist_id TEXT NOT NULL PRIMARY KEY,owner_key TEXT NOT NULL,revision INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_collection_transfer_wishlist_dirty_owner_key_wishlist_id ON collection_transfer_wishlist_dirty(owner_key,wishlist_id)")
        db.execSQL("ALTER TABLE collection_import_provenance ADD COLUMN wishlist_participated INTEGER NOT NULL DEFAULT 0")
    }
}
