package com.mmg.manahub.core.data.local.entity

import androidx.room.*

@Entity(tableName="collection_export_jobs",indices=[Index("owner_key","created_at")])
data class CollectionExportEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name="owner_key") val ownerKey: String,
    val format: String,
    val target: String,
    val phase: String="FROZEN",
    @ColumnInfo(name="created_at") val createdAt: Long,
    val rows: Long=0L,
    val copies: Long=0L,
    @ColumnInfo(name="omitted_rows") val omittedRows: Long=0L,
    @ColumnInfo(name="omitted_copies") val omittedCopies: Long=0L,
    val bytes: Long=0L,
    val sha256: String?=null,
    @ColumnInfo(name="metadata_frozen") val metadataFrozen: Boolean=false,
    @ColumnInfo(name="available_only") val availableOnly: Boolean=false,
    @ColumnInfo(name="partial_destination") val partialDestination: Boolean=false,
)
