package com.mmg.manahub.core.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Existing staging remains undecided; acknowledgements never migrate into destination consent. */
val MIGRATION_59_60 = object : Migration(59, 60) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE collection_transfer_jobs ADD COLUMN intent_revision INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE collection_import_entries ADD COLUMN destination TEXT NOT NULL DEFAULT 'NONE'")
        db.execSQL("ALTER TABLE collection_import_entries ADD COLUMN entry_version INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE collection_import_entries ADD COLUMN active_action_id TEXT")
        db.execSQL("CREATE TABLE IF NOT EXISTS collection_transfer_actions (id TEXT NOT NULL PRIMARY KEY, job_id TEXT NOT NULL, owner_key TEXT NOT NULL, generation INTEGER NOT NULL, destination TEXT NOT NULL, scope TEXT NOT NULL, scope_version INTEGER NOT NULL, phase TEXT NOT NULL, created_at INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_collection_transfer_actions_job_id_phase ON collection_transfer_actions(job_id,phase)")
        db.execSQL("CREATE TABLE IF NOT EXISTS collection_transfer_action_entries (action_id TEXT NOT NULL, entry_id TEXT NOT NULL, entry_version INTEGER NOT NULL, scryfall_id TEXT NOT NULL, is_foil INTEGER NOT NULL, condition TEXT NOT NULL, language TEXT NOT NULL, quantity INTEGER NOT NULL, state TEXT NOT NULL, completed_quantity INTEGER NOT NULL, PRIMARY KEY(action_id,entry_id))")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_collection_transfer_action_entries_entry_id_action_id ON collection_transfer_action_entries(entry_id,action_id)")
    }
}
