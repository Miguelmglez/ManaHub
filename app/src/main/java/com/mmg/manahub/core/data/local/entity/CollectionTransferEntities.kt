package com.mmg.manahub.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName="collection_transfer_deliveries",indices=[Index("owner_key","fingerprint")])
data class CollectionTransferDeliveryEntity(
    @PrimaryKey @ColumnInfo(name="receipt_id") val receiptId: String,
    @ColumnInfo(name="owner_key") val ownerKey: String,
    val fingerprint: String,
    @ColumnInfo(name="bound_job_id") val boundJobId: String,
)

/** Dirty delivery identity survives local completion and remote timeouts. */
@Entity(tableName="collection_transfer_wishlist_dirty",indices=[Index("owner_key","wishlist_id"),Index("owner_key","pending","wishlist_id")])
data class CollectionTransferWishlistDirtyEntity(
    @PrimaryKey @ColumnInfo(name="wishlist_id") val wishlistId: String,
    @ColumnInfo(name="owner_key") val ownerKey: String,
    val revision: Long,
    @ColumnInfo(defaultValue="0") val deleted: Boolean = false,
    @ColumnInfo(defaultValue="1") val pending: Boolean = true,
    @ColumnInfo(name="next_repair_at",defaultValue="0") val nextRepairAt: Long = 0L,
    @ColumnInfo(defaultValue="''") val printing: String = "",
    @ColumnInfo(defaultValue="0") val quantity: Int = 0,
    val foil: Boolean? = null,
    val condition: String? = null,
    val language: String? = null,
    @ColumnInfo(name="match_any_variant",defaultValue="0") val matchAnyVariant: Boolean = false,
    @ColumnInfo(name="created_at",defaultValue="0") val createdAt: Long = 0L,
)

/** A receipt records private intake without granting collection ownership. */
@Entity(tableName = "collection_transfer_receipts")
data class CollectionTransferReceiptEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "auth_generation") val authGeneration: Long,
    @ColumnInfo(name = "captured_owner") val capturedOwner: String?,
    val phase: String = "RECEIVING",
    @ColumnInfo(name = "received_bytes") val receivedBytes: Long = 0L,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "consumed_at") val consumedAt: Long? = null,
    val error: String? = null,
)

/** Job checkpoints and consent live in the same Room database as collection writes. */
@Entity(tableName = "collection_transfer_jobs", indices = [Index("owner_key", "phase")])
data class CollectionTransferJobEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "owner_key") val ownerKey: String,
    @ColumnInfo(name = "auth_generation") val authGeneration: Long,
    val type: String = "IMPORT",
    val origin: String,
    val phase: String = "PARSING",
    @ColumnInfo(name = "previous_phase") val previousPhase: String = "PARSING",
    val generation: Long = 0L,
    @ColumnInfo(name = "payload_version") val payloadVersion: Long = 0L,
    @ColumnInfo(name = "confirmed_generation") val confirmedGeneration: Long? = null,
    @ColumnInfo(name = "confirmed_payload_version") val confirmedPayloadVersion: Long? = null,
    @ColumnInfo(name = "accept_exclusions") val acceptExclusions: Boolean = false,
    @ColumnInfo(name = "accept_repeats") val acceptRepeats: Boolean = false,
    @ColumnInfo(name = "files_frozen") val filesFrozen: Boolean = false,
    val fingerprint: String? = null,
    @ColumnInfo(name = "work_cursor") val workCursor: String? = null,
    @ColumnInfo(name = "next_attempt_at") val nextAttemptAt: Long = 0L,
    @ColumnInfo(name = "network_failures") val networkFailures: Int = 0,
    @ColumnInfo(name = "data_records") val dataRecords: Long = 0L,
    @ColumnInfo(name = "preamble_records") val preambleRecords: Long = 0L,
    @ColumnInfo(name = "valid_records") val validRecords: Long = 0L,
    @ColumnInfo(name = "invalid_records") val invalidRecords: Long = 0L,
    @ColumnInfo(name = "resolved_records") val resolvedRecords: Long = 0L,
    @ColumnInfo(name = "unresolved_records") val unresolvedRecords: Long = 0L,
    @ColumnInfo(name = "accepted_copies") val acceptedCopies: Long = 0L,
    @ColumnInfo(name = "applied_copies") val appliedCopies: Long = 0L,
    @ColumnInfo(name = "applied_entries") val appliedEntries: Long = 0L,
    @ColumnInfo(name = "collection_event_pending") val collectionEventPending: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "completed_at") val completedAt: Long? = null,
    val error: String? = null,
    @ColumnInfo(name = "intent_revision", defaultValue = "0") val intentRevision: Long = 0L,
)

/** Each source retains its own checkpoint and survives a sibling's reception failure. */
@Entity(tableName = "collection_transfer_files", indices = [Index("receipt_id", "file_order"), Index("job_id", "file_order"), Index("sha256")])
data class CollectionTransferFileEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "receipt_id") val receiptId: String,
    @ColumnInfo(name = "job_id") val jobId: String? = null,
    @ColumnInfo(name = "file_order") val fileOrder: Int,
    val revision: Long = 0L,
    @ColumnInfo(name = "replaces_id") val replacesId: String? = null,
    @ColumnInfo(name = "source_path") val sourcePath: String? = null,
    val format: String? = null,
    val bytes: Long = 0L,
    val sha256: String? = null,
    val phase: String = "RECEIVING",
    @ColumnInfo(name = "previous_phase") val previousPhase: String = "RECEIVING",
    val selected: Boolean = true,
    val retired: Boolean = false,
    @ColumnInfo(name = "duplicate_of") val duplicateOf: String? = null,
    @ColumnInfo(name = "prior_participation") val priorParticipation: Boolean = false,
    @ColumnInfo(name = "parse_ordinal") val parseOrdinal: Long = 0L,
    @ColumnInfo(name = "resolve_ordinal") val resolveOrdinal: Long = 0L,
    @ColumnInfo(name = "data_records") val dataRecords: Long = 0L,
    @ColumnInfo(name = "preamble_records") val preambleRecords: Long = 0L,
    @ColumnInfo(name = "header_records") val headerRecords: Long = 0L,
    @ColumnInfo(name = "valid_records") val validRecords: Long = 0L,
    @ColumnInfo(name = "invalid_records") val invalidRecords: Long = 0L,
    @ColumnInfo(name = "resolved_records") val resolvedRecords: Long = 0L,
    @ColumnInfo(name = "unresolved_records") val unresolvedRecords: Long = 0L,
    val copies: Long = 0L,
    @ColumnInfo(name = "unrepresented_columns") val unrepresentedColumns: String = "[]",
    val error: String? = null,
)

/** Source records remain unmerged; replay is keyed by file and logical source ordinal. */
@Entity(tableName = "collection_import_rows", primaryKeys = ["job_id", "file_id", "source_ordinal"], indices = [Index("job_id", "state", "file_id", "source_ordinal"), Index("job_id", "entry_id"), Index("job_id", "resolved_id", "is_foil", "condition", "language", "file_id"), Index("job_id", "state", "source_group_key", "file_id")])
data class CollectionImportRowEntity(
    @ColumnInfo(name = "job_id") val jobId: String,
    @ColumnInfo(name = "file_id") val fileId: String,
    @ColumnInfo(name = "source_ordinal") val sourceOrdinal: Long,
    @ColumnInfo(name = "byte_start") val byteStart: Long,
    @ColumnInfo(name = "byte_end") val byteEnd: Long,
    val kind: String,
    val name: String? = null,
    @ColumnInfo(name = "set_code") val setCode: String? = null,
    @ColumnInfo(name = "collector_number") val collectorNumber: String? = null,
    @ColumnInfo(name = "scryfall_id") val scryfallId: String? = null,
    val quantity: Long? = null,
    @ColumnInfo(name = "is_foil") val isFoil: Boolean = false,
    val condition: String = "NM",
    val language: String = "en",
    val state: String = "PENDING",
    @ColumnInfo(name = "resolved_id") val resolvedId: String? = null,
    @ColumnInfo(name = "entry_id") val entryId: String? = null,
    val error: String? = null,
    val preview: String = "",
    @ColumnInfo(name = "source_group_key", defaultValue = "''") val sourceGroupKey: String = "",
)

/** Stable entry IDs and applied quantities are never recreated when resuming a frozen payload. */
@Entity(tableName = "collection_import_entries", indices = [Index("job_id", "scryfall_id", "is_foil", "condition", "language", unique = true), Index("job_id", "generation", "id"), Index("job_id", "state", "id"), Index("job_id", "source_key")])
data class CollectionImportEntryEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "job_id") val jobId: String,
    val generation: Long,
    @ColumnInfo(name = "scryfall_id") val scryfallId: String,
    @ColumnInfo(name = "is_foil") val isFoil: Boolean,
    val condition: String,
    val language: String,
    val quantity: Long,
    @ColumnInfo(name = "applied_quantity") val appliedQuantity: Long = 0L,
    val state: String = "PENDING",
    val excluded: Boolean = false,
    val error: String? = null,
    @ColumnInfo(defaultValue = "'NONE'") val destination: String = "NONE",
    @ColumnInfo(name = "entry_version", defaultValue = "0") val entryVersion: Long = 0L,
    @ColumnInfo(name = "active_action_id") val activeActionId: String? = null,
    @ColumnInfo(name = "source_key", defaultValue = "''") val sourceKey: String = "",
    @ColumnInfo(name = "source_signature", defaultValue = "''") val sourceSignature: String = "",
    @ColumnInfo(name = "source_quantity", defaultValue = "0") val sourceQuantity: Long = 0L,
    @ColumnInfo(name = "payload_edited", defaultValue = "0") val payloadEdited: Boolean = false,
)

/** An invalidated edit remains visible after its original source contribution changes or disappears. */
@Entity(tableName = "collection_transfer_review_decisions", primaryKeys = ["job_id", "entry_id"], indices = [Index("job_id", "status", "entry_id"), Index("job_id", "source_key")])
data class CollectionTransferReviewDecisionEntity(
    @ColumnInfo(name = "job_id") val jobId: String,
    @ColumnInfo(name = "entry_id") val entryId: String,
    val generation: Long,
    @ColumnInfo(name = "source_key") val sourceKey: String,
    val reason: String,
    val status: String = "PENDING",
    @ColumnInfo(name = "scryfall_id") val scryfallId: String,
    @ColumnInfo(name = "is_foil") val isFoil: Boolean,
    val condition: String,
    val language: String,
    val quantity: Long,
    val destination: String,
    val excluded: Boolean,
    @ColumnInfo(name = "source_signature") val sourceSignature: String,
    @ColumnInfo(name = "source_quantity") val sourceQuantity: Long,
    @ColumnInfo(name = "payload_edited") val payloadEdited: Boolean,
    @ColumnInfo(name = "entry_version") val entryVersion: Long,
)

/** Destination-specific commands retain immutable subsets independently of collection counters. */
@Entity(tableName = "collection_transfer_actions", indices = [Index("job_id", "phase")])
data class CollectionTransferActionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "job_id") val jobId: String,
    @ColumnInfo(name = "owner_key") val ownerKey: String,
    val generation: Long,
    val destination: String,
    val scope: String,
    @ColumnInfo(name = "scope_version") val scopeVersion: Long,
    var phase: String = "CONFIRMED",
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

/** A command captures the exact printing and attributes selected at confirmation. */
@Entity(tableName = "collection_transfer_action_entries", primaryKeys = ["action_id", "entry_id"], indices = [Index("entry_id", "action_id")])
data class CollectionTransferActionEntryEntity(
    @ColumnInfo(name = "action_id") val actionId: String,
    @ColumnInfo(name = "entry_id") val entryId: String,
    @ColumnInfo(name = "entry_version") val entryVersion: Long,
    @ColumnInfo(name = "scryfall_id") val scryfallId: String,
    @ColumnInfo(name = "is_foil") val isFoil: Boolean,
    val condition: String,
    val language: String,
    val quantity: Long,
    val state: String = "PENDING",
    @ColumnInfo(name = "completed_quantity") val completedQuantity: Long = 0L,
)

/** Aggregated source participation remains distinguishable from edited accepted quantities. */
@Entity(tableName = "collection_import_provenance", primaryKeys = ["job_id", "entry_id", "file_id"], indices = [Index("job_id", "file_id")])
data class CollectionImportProvenanceEntity(
    @ColumnInfo(name = "job_id") val jobId: String,
    @ColumnInfo(name = "entry_id") val entryId: String,
    @ColumnInfo(name = "file_id") val fileId: String,
    val generation: Long,
    @ColumnInfo(name = "source_records") val sourceRecords: Long,
    @ColumnInfo(name = "source_copies") val sourceCopies: Long,
    val participated: Boolean = false,
    @ColumnInfo(name="wishlist_participated",defaultValue="0") val wishlistParticipated: Boolean = false,
)

/** Owner/hash history survives source/session cleanup and records partial application participation. */
@Entity(tableName = "collection_transfer_file_history", primaryKeys = ["owner_key", "sha256"])
data class CollectionTransferFileHistoryEntity(
    @ColumnInfo(name = "owner_key") val ownerKey: String,
    val sha256: String,
    @ColumnInfo(name = "last_job_id") val lastJobId: String,
    @ColumnInfo(name = "participated_at") val participatedAt: Long,
)

/** NULL collection ownership is readable by this feature only with verified transfer provenance. */
@Entity(tableName = "collection_transfer_guest_rows", indices = [Index("owner_key", "row_id")])
data class CollectionTransferGuestRowEntity(
    @PrimaryKey @ColumnInfo(name = "row_id") val rowId: String,
    @ColumnInfo(name = "owner_key") val ownerKey: String,
)

/** Distinct fallback names are claimed durably before a network request consumes the budget. */
@Entity(tableName = "collection_transfer_name_claims", primaryKeys = ["job_id", "name_key"])
data class CollectionTransferNameClaimEntity(
    @ColumnInfo(name = "job_id") val jobId: String,
    @ColumnInfo(name = "name_key") val nameKey: String,
    @ColumnInfo(name = "claimed_at") val claimedAt: Long,
)

/** Frozen query metadata describes one immutable snapshot generation. */
@Entity(tableName = "collection_transfer_snapshot_queries", primaryKeys = ["job_id", "generation"])
data class CollectionTransferSnapshotQueryEntity(
    @ColumnInfo(name = "job_id") val jobId: String,
    val generation: Long,
    val source: String,
    val search: String,
    @ColumnInfo(name = "advanced_query") val advancedQuery: String,
    val sort: String,
    val grouping: String,
    @ColumnInfo(name = "sort_ascending") val sortAscending: Boolean,
    @ColumnInfo(name = "build_cursor") val buildCursor: String? = null,
    val phase: String = "BUILDING",
    @ColumnInfo(name = "total_rows") val totalRows: Long = 0L,
    @ColumnInfo(name = "total_copies") val totalCopies: Long = 0L,
)

/** Snapshot values are disk-backed, independent of subsequent collection or metadata edits. */
@Entity(tableName = "collection_transfer_snapshot_rows", primaryKeys = ["job_id", "generation", "ordinal"], indices = [Index("job_id", "generation", "sort_key", "source_id"), Index("job_id", "generation", "group_key")])
data class CollectionTransferSnapshotRowEntity(
    @ColumnInfo(name = "job_id") val jobId: String,
    val generation: Long,
    val ordinal: Long,
    @ColumnInfo(name = "source_id") val sourceId: String,
    @ColumnInfo(name = "scryfall_id") val scryfallId: String,
    @ColumnInfo(name = "is_foil") val isFoil: Boolean,
    val condition: String,
    val language: String,
    val quantity: Long,
    @ColumnInfo(name = "card_metadata") val cardMetadata: String,
    @ColumnInfo(name = "sort_key") val sortKey: String,
    @ColumnInfo(name = "group_key") val groupKey: String,
    val matched: Boolean,
)

