package com.mmg.manahub.core.domain.collection.transfer

import kotlinx.coroutines.flow.Flow

enum class CollectionExportPhase { FROZEN, HYDRATING, NEEDS_METADATA, WRITING, READY, SAVING, SAVED, SHARED, FAILED }
enum class CollectionExportTarget { SAVE, SHARE }

/** Durable accounting refers to individual source rows, never grouped representatives. */
data class DurableCollectionExport(
    val id: String,
    val format: CollectionFileFormat,
    val target: CollectionExportTarget,
    val phase: CollectionExportPhase,
    val rows: Long,
    val copies: Long,
    val omittedRows: Long,
    val omittedCopies: Long,
    val bytes: Long,
    val partialDestination: Boolean,
    val availableOnly: Boolean,
)

/** Every retry uses the job's retained snapshot; metadata loss requires explicit consent. */
interface CollectionExportRepository {
    suspend fun create(owner: TransferOwner,query: CollectionSelectionQuery,format: CollectionFileFormat,target: CollectionExportTarget): String
    fun observe(owner: TransferOwner,id: String): Flow<DurableCollectionExport?>
    suspend fun latest(owner: TransferOwner): String?
    suspend fun prepare(owner: TransferOwner,id: String,availableOnly: Boolean=false): DurableCollectionExport
    suspend fun save(owner: TransferOwner,id: String,location: String)
    suspend fun share(owner: TransferOwner,id: String): String
    suspend fun writeOmissions(owner: TransferOwner,id: String,location: String)
}
