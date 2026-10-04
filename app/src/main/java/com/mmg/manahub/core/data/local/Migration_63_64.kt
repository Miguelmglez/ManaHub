package com.mmg.manahub.core.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Retained deletion intent suppresses late remote upserts after ambiguous delivery or process death. */
val MIGRATION_63_64=object : Migration(63,64) {
    override fun migrate(db: SupportSQLiteDatabase) {
        for(definition in listOf("deleted INTEGER NOT NULL DEFAULT 0","pending INTEGER NOT NULL DEFAULT 1","next_repair_at INTEGER NOT NULL DEFAULT 0","printing TEXT NOT NULL DEFAULT ''","quantity INTEGER NOT NULL DEFAULT 0","foil INTEGER","condition TEXT","language TEXT","match_any_variant INTEGER NOT NULL DEFAULT 0","created_at INTEGER NOT NULL DEFAULT 0"))db.execSQL("ALTER TABLE collection_transfer_wishlist_dirty ADD COLUMN $definition")
        db.execSQL("UPDATE collection_transfer_wishlist_dirty SET deleted=NOT EXISTS(SELECT 1 FROM local_wishlists WHERE id=wishlist_id), printing=COALESCE((SELECT scryfall_id FROM local_wishlists WHERE id=wishlist_id),''),quantity=COALESCE((SELECT quantity FROM local_wishlists WHERE id=wishlist_id),0),foil=(SELECT is_foil FROM local_wishlists WHERE id=wishlist_id),condition=(SELECT condition FROM local_wishlists WHERE id=wishlist_id),language=(SELECT language FROM local_wishlists WHERE id=wishlist_id),match_any_variant=COALESCE((SELECT match_any_variant FROM local_wishlists WHERE id=wishlist_id),0),created_at=COALESCE((SELECT created_at FROM local_wishlists WHERE id=wishlist_id),0)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_collection_transfer_wishlist_dirty_owner_key_pending_wishlist_id ON collection_transfer_wishlist_dirty(owner_key,pending,wishlist_id)")
    }
}
