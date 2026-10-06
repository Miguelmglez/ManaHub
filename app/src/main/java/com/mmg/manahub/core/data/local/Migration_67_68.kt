package com.mmg.manahub.core.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_67_68 = object : Migration(67, 68) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE collection_import_entries ADD COLUMN duplicate_origin_id TEXT")
        db.execSQL("ALTER TABLE collection_import_entries ADD COLUMN duplicate_key TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE collection_import_entries ADD COLUMN review_order_key TEXT NOT NULL DEFAULT ''")
        db.execSQL("UPDATE collection_import_entries SET review_order_key=id")
        db.execSQL("DROP INDEX index_collection_import_entries_job_id_scryfall_id_is_foil_condition_language")
        db.execSQL("CREATE UNIQUE INDEX index_collection_import_entries_job_id_scryfall_id_is_foil_condition_language_duplicate_key ON collection_import_entries(job_id,scryfall_id,is_foil,condition,language,duplicate_key)")
        db.execSQL("CREATE INDEX index_collection_import_entries_job_id_generation_review_order_key ON collection_import_entries(job_id,generation,review_order_key)")
        db.execSQL("CREATE INDEX index_collection_import_entries_job_id_duplicate_origin_id ON collection_import_entries(job_id,duplicate_origin_id)")
    }
}
