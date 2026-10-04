package com.mmg.manahub.core.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_65_66=object: Migration(65,66) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS collection_export_jobs(id TEXT NOT NULL PRIMARY KEY,owner_key TEXT NOT NULL,format TEXT NOT NULL,target TEXT NOT NULL,phase TEXT NOT NULL,created_at INTEGER NOT NULL,rows INTEGER NOT NULL,copies INTEGER NOT NULL,omitted_rows INTEGER NOT NULL,omitted_copies INTEGER NOT NULL,bytes INTEGER NOT NULL,sha256 TEXT,metadata_frozen INTEGER NOT NULL,available_only INTEGER NOT NULL,partial_destination INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_collection_export_jobs_owner_key_created_at ON collection_export_jobs(owner_key,created_at)")
        addColumn(db,"raw_foil","INTEGER")
        addColumn(db,"raw_condition","TEXT")
        addColumn(db,"raw_language","TEXT")
        addColumn(db,"export_ordinal","INTEGER NOT NULL DEFAULT 0")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_collection_selection_rows_query_id_export_ordinal ON collection_selection_rows(query_id,export_ordinal)")
        db.execSQL("UPDATE collection_selection_rows SET raw_foil=foil,raw_condition=condition,raw_language=language")
    }
}

private fun addColumn(db: SupportSQLiteDatabase,name: String,type: String) {
    val exists=db.query("PRAGMA table_info(collection_selection_rows)").use { cursor ->
        val column=cursor.getColumnIndex("name")
        var found=false
        while(cursor.moveToNext())if(cursor.getString(column)==name)found=true
        found
    }
    if(!exists)db.execSQL("ALTER TABLE collection_selection_rows ADD COLUMN $name $type")
}
