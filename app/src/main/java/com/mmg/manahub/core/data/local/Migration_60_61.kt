package com.mmg.manahub.core.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Preserve user edits separately from changing source membership during reconstruction. */
val MIGRATION_60_61 = object : Migration(60, 61) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE collection_import_entries ADD COLUMN source_key TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE collection_import_entries ADD COLUMN source_signature TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE collection_import_entries ADD COLUMN source_quantity INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE collection_import_entries ADD COLUMN payload_edited INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE collection_import_rows ADD COLUMN source_group_key TEXT NOT NULL DEFAULT ''")
        db.execSQL("UPDATE collection_import_rows SET source_group_key=hex(resolved_id)||':'||hex(CAST(is_foil AS TEXT))||':'||hex(condition)||':'||hex(language) WHERE state='RESOLVED'")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_collection_import_rows_job_id_state_source_group_key_file_id ON collection_import_rows(job_id,state,source_group_key,file_id)")
        db.execSQL("UPDATE collection_import_entries SET source_key=hex(scryfall_id)||':'||hex(CAST(is_foil AS TEXT))||':'||hex(condition)||':'||hex(language), source_quantity=quantity, payload_edited=(entry_version>0)")
        db.execSQL("CREATE TABLE IF NOT EXISTS collection_transfer_review_decisions(job_id TEXT NOT NULL, entry_id TEXT NOT NULL, generation INTEGER NOT NULL, source_key TEXT NOT NULL, reason TEXT NOT NULL, status TEXT NOT NULL, scryfall_id TEXT NOT NULL, is_foil INTEGER NOT NULL, condition TEXT NOT NULL, language TEXT NOT NULL, quantity INTEGER NOT NULL, destination TEXT NOT NULL, excluded INTEGER NOT NULL, source_signature TEXT NOT NULL, source_quantity INTEGER NOT NULL, payload_edited INTEGER NOT NULL, entry_version INTEGER NOT NULL, PRIMARY KEY(job_id,entry_id))")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_collection_transfer_review_decisions_job_id_status_entry_id ON collection_transfer_review_decisions(job_id,status,entry_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_collection_transfer_review_decisions_job_id_source_key ON collection_transfer_review_decisions(job_id,source_key)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_collection_import_entries_job_id_source_key ON collection_import_entries(job_id,source_key)")
    }
}
