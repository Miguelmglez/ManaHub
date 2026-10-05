package com.mmg.manahub.core.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Explicit transfer guest provenance never claims ambiguous legacy NULL-owner collection rows. */
val MIGRATION_61_62 = object : Migration(61, 62) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS collection_transfer_guest_rows(row_id TEXT NOT NULL PRIMARY KEY,owner_key TEXT NOT NULL)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_collection_transfer_guest_rows_owner_key_row_id ON collection_transfer_guest_rows(owner_key,row_id)")
    }
}
