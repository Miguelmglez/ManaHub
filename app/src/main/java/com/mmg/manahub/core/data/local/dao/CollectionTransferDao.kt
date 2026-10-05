package com.mmg.manahub.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.mmg.manahub.core.data.local.entity.*
import com.mmg.manahub.core.domain.collection.transfer.*
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Row accounting computed on disk instead of trusting external completion counters. */
data class TransferStoredRowCounts(val records: Long, val dataRecords: Long, val preambleRecords: Long, val headerRecords: Long, val validRecords: Long, val invalidRecords: Long, val copies: Long)

/** Resolution results never overwrite source identifiers or represented attributes. */
data class TransferStoredResolution(val ordinal: Long, val resolvedId: String?, val error: String?)

/** Entry totals are computed from the durable review, including explicit per-entry exclusions. */
data class TransferStoredEntryCounts(val acceptedCopies: Long, val appliedCopies: Long, val pendingEntries: Long, val excludedEntries: Long, val invalidEntries: Long)

/** Provenance totals distinguish original contributions from later manual quantity edits. */
data class TransferStoredSourceCounts(val records: Long, val copies: Long)

/** SQL groups are bounded by the selection budget before their checked source totals are consumed. */
data class TransferReviewSourceGroup(val sourceKey: String, val printing: String, val foil: Boolean, val condition: String, val language: String, val records: Long, val copies: Long)

/** A group has at most ten per-file contributions, independently from its source-row count. */
data class TransferReviewSourceContribution(val fileId: String, val records: Long, val copies: Long)

/** Collection apply totals exclude independent wishlist command completions. */
data class TransferCollectionAppliedTotals(val entries: Long, val copies: Long)

/** Summary accounting keeps wishlist completions separate from pending and collection quantities. */
data class TransferRepositoryTotals(val pendingCopies: Long,val pendingEntries: Long,val excludedEntries: Long)
data class TransferRepositoryActionTotals(val entries: Long,val copies: Long,val completedEntries: Long,val completedCopies: Long)

/** Owner-scoped persistence primitives; active-session serialization belongs to the coordinator. */
@Dao
abstract class CollectionTransferDao {
    @Insert(onConflict=OnConflictStrategy.ABORT)
    protected abstract suspend fun insertDelivery(delivery: CollectionTransferDeliveryEntity)
    @Query("SELECT d.bound_job_id FROM collection_transfer_deliveries d JOIN collection_transfer_jobs j ON j.id=d.bound_job_id AND j.owner_key=d.owner_key WHERE d.receipt_id=:receiptId AND d.owner_key=:ownerKey")
    abstract suspend fun deliveryJob(receiptId: String,ownerKey: String): String?
    @Query("SELECT d.bound_job_id FROM collection_transfer_deliveries d JOIN collection_transfer_jobs j ON j.id=d.bound_job_id AND j.owner_key=d.owner_key WHERE d.owner_key=:ownerKey AND d.fingerprint=:fingerprint AND j.phase NOT IN ('COMPLETED','COMPLETED_WITH_EXCLUSIONS','DISCARDED','REJECTED') ORDER BY j.created_at,j.id LIMIT 1")
    protected abstract suspend fun existingDelivery(ownerKey: String,fingerprint: String): String?
    @Transaction
    open suspend fun bindReceiptOrExisting(receiptId: String,ownerKey: String,authGeneration: Long,origin: String,now: Long,destinationChosen: Boolean): String? {
        deliveryJob(receiptId,ownerKey)?.let { return it }
        val receipt=getReceipt(receiptId) ?: return null
        if(receipt.phase=="RECEIVING" || receipt.consumedAt!=null || (receipt.capturedOwner!=null && receipt.capturedOwner!=ownerKey) || (receipt.capturedOwner==null && receipt.authGeneration!=authGeneration && !destinationChosen))return null
        val members=getReceiptFiles(receiptId).filterNot { it.retired }
        val fingerprint=members.takeIf { it.isNotEmpty() && it.all { file -> file.sha256!=null && file.phase in setOf("PARSING","RESOLVING","REVIEW_READY") } }?.let {
            java.security.MessageDigest.getInstance("SHA-256").digest(transferFingerprintInput(it.map { file -> file.sha256!! }).toByteArray(Charsets.UTF_8)).joinToString("") { byte -> "%02x".format(byte) }
        }
        val pendingMatch=if(fingerprint==null)null else existingDelivery(ownerKey,fingerprint) ?: unfinishedJobs(ownerKey).firstOrNull { pending ->
            val files=currentFiles(pending.id,ownerKey)
            files.isNotEmpty() && files.all { it.sha256!=null && it.phase in setOf("PARSING","RESOLVING","REVIEW_READY") } &&
                transferFingerprintInput(files.map { it.sha256!! })==transferFingerprintInput(members.map { it.sha256!! })
        }?.id
        if(fingerprint!=null)pendingMatch?.let { existing ->
            insertDelivery(CollectionTransferDeliveryEntity(receiptId,ownerKey,fingerprint,existing))
            updateReceipt(receipt.copy(consumedAt=now,phase="DISCARDED"))
            return existing
        }
        if(!bindReceipt(receiptId,ownerKey,authGeneration,origin,now,destinationChosen))return null
        if(fingerprint!=null)insertDelivery(CollectionTransferDeliveryEntity(receiptId,ownerKey,fingerprint,receiptId))
        return receiptId
    }
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertReceipt(receipt: CollectionTransferReceiptEntity): Long
    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertFiles(files: List<CollectionTransferFileEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertJob(job: CollectionTransferJobEntity)
    @Update
    protected abstract suspend fun updateReceipt(receipt: CollectionTransferReceiptEntity)
    @Update
    protected abstract suspend fun updateFile(file: CollectionTransferFileEntity)
    @Update
    protected abstract suspend fun updateJob(job: CollectionTransferJobEntity)
    @Update
    protected abstract suspend fun updateRow(row: CollectionImportRowEntity)
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertRows(rows: List<CollectionImportRowEntity>)
    @Upsert
    protected abstract suspend fun upsertEntries(entries: List<CollectionImportEntryEntity>)
    @Upsert
    protected abstract suspend fun upsertProvenance(entries: List<CollectionImportProvenanceEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertNameClaim(claim: CollectionTransferNameClaimEntity): Long
    @Upsert
    protected abstract suspend fun upsertSnapshotQuery(query: CollectionTransferSnapshotQueryEntity)
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertSnapshotRows(rows: List<CollectionTransferSnapshotRowEntity>)

    @Query("SELECT * FROM collection_transfer_receipts WHERE id = :receiptId")
    abstract suspend fun getReceipt(receiptId: String): CollectionTransferReceiptEntity?
    @Query("SELECT * FROM collection_transfer_receipts WHERE id = :receiptId")
    abstract fun observeReceipt(receiptId: String): Flow<CollectionTransferReceiptEntity?>
    @Query("SELECT * FROM collection_transfer_files WHERE receipt_id = :receiptId ORDER BY file_order, revision, id")
    abstract suspend fun getReceiptFiles(receiptId: String): List<CollectionTransferFileEntity>
    @Query("SELECT * FROM collection_transfer_jobs WHERE id = :jobId AND owner_key = :ownerKey")
    abstract suspend fun getJob(jobId: String, ownerKey: String): CollectionTransferJobEntity?
    @Query("SELECT * FROM collection_transfer_jobs WHERE id = :jobId AND owner_key = :ownerKey")
    abstract fun observeJob(jobId: String, ownerKey: String): Flow<CollectionTransferJobEntity?>
    @Query("SELECT * FROM collection_transfer_jobs WHERE owner_key = :ownerKey AND phase NOT IN ('COMPLETED','COMPLETED_WITH_EXCLUSIONS','DISCARDED','REJECTED') ORDER BY created_at, id LIMIT 3")
    abstract suspend fun unfinishedJobs(ownerKey: String): List<CollectionTransferJobEntity>
    @Query("SELECT * FROM collection_transfer_jobs WHERE owner_key=:ownerKey AND id>:afterId AND phase NOT IN ('COMPLETED','COMPLETED_WITH_EXCLUSIONS','DISCARDED','REJECTED') ORDER BY id LIMIT 50")
    abstract suspend fun reconciliationPage(ownerKey: String, afterId: String): List<CollectionTransferJobEntity>
    @Query("SELECT * FROM collection_transfer_jobs WHERE owner_key=:ownerKey AND phase NOT IN ('COMPLETED','COMPLETED_WITH_EXCLUSIONS','DISCARDED','REJECTED') ORDER BY id LIMIT 3")
    abstract fun observeReconciliationJobs(ownerKey: String): Flow<List<CollectionTransferJobEntity>>
    @Query("SELECT a.* FROM collection_transfer_actions a JOIN collection_transfer_jobs j ON j.id=a.job_id WHERE j.id=:jobId AND j.owner_key=:ownerKey AND a.owner_key=:ownerKey AND a.phase IN ('CONFIRMED','FROZEN') ORDER BY a.created_at,a.id LIMIT 1")
    abstract suspend fun executableAction(jobId: String, ownerKey: String): CollectionTransferActionEntity?
    @Query("SELECT a.* FROM collection_transfer_actions a JOIN collection_transfer_jobs j ON j.id=a.job_id WHERE a.job_id=:jobId AND j.owner_key=:ownerKey AND a.owner_key=:ownerKey AND a.id>:afterId ORDER BY a.id LIMIT 50")
    abstract suspend fun reportActionPage(jobId: String,ownerKey: String,afterId: String): List<CollectionTransferActionEntity>
    @Query("SELECT COUNT(*) FROM collection_transfer_wishlist_dirty WHERE owner_key=:ownerKey AND pending=1")
    abstract fun observeWishlistPending(ownerKey: String): Flow<Long>
    @Query("UPDATE collection_transfer_jobs SET next_attempt_at=:nextAttemptAt,error='STORAGE_FAILURE' WHERE id=:jobId AND owner_key=:ownerKey AND phase NOT IN ('PAUSED_BY_USER','PAUSED_OWNER','FAILED_RETRYABLE','DISCARDED','REJECTED')")
    abstract suspend fun deferTransferApplication(jobId: String,ownerKey: String,nextAttemptAt: Long)
    /** Preparation failure preserves its checkpoint and rejects every failed source prefix. */
    @Transaction
    open suspend fun failTransferPreparation(jobId: String,ownerKey: String,phase: String,error: String,now: Long,fileId: String?=null): Boolean {
        require(phase in setOf("FAILED_RETRYABLE","WAITING_FILE_DECISION"))
        val job=getJob(jobId,ownerKey) ?: return false
        if(job.filesFrozen || job.phase in setOf("PAUSED_BY_USER","PAUSED_OWNER","FAILED_RETRYABLE","DISCARDED","REJECTED","COMPLETED","COMPLETED_WITH_EXCLUSIONS"))return false
        if(fileId!=null) {
            val file=getFile(jobId,ownerKey,fileId) ?: return false
            if(file.phase !in setOf("PARSING","RECEIVING"))return false
            updateFile(file.copy(phase="FAILED_RETRYABLE",error=error))
        }
        updateJob(job.copy(previousPhase=job.phase,phase=phase,error=error,updatedAt=now)); return true
    }
    @Query("SELECT COUNT(*) FROM collection_transfer_jobs WHERE owner_key = :ownerKey AND phase NOT IN ('COMPLETED','COMPLETED_WITH_EXCLUSIONS','DISCARDED','REJECTED')")
    protected abstract suspend fun unfinishedCount(ownerKey: String): Long
    @Query("SELECT COUNT(*) FROM collection_transfer_receipts WHERE captured_owner=:ownerKey AND consumed_at IS NULL AND phase!='DISCARDED'")
    protected abstract suspend fun unboundReceiptCount(ownerKey: String): Long
    @Query("SELECT f.* FROM collection_transfer_files f INNER JOIN collection_transfer_jobs j ON j.id = f.job_id WHERE j.id = :jobId AND j.owner_key = :ownerKey AND f.retired = 0 ORDER BY f.file_order, f.id LIMIT 10")
    abstract suspend fun currentFiles(jobId: String, ownerKey: String): List<CollectionTransferFileEntity>
    @Query("SELECT f.* FROM collection_transfer_files f JOIN collection_transfer_jobs j ON j.id=f.job_id WHERE j.id=:jobId AND j.owner_key=:ownerKey AND f.retired=0 ORDER BY f.file_order,f.id LIMIT 10")
    abstract fun observeCurrentFiles(jobId: String, ownerKey: String): Flow<List<CollectionTransferFileEntity>>
    @Query("SELECT COALESCE(SUM(CASE WHEN e.state='PENDING' AND e.excluded=0 THEN e.quantity ELSE 0 END),0) AS pendingCopies,COALESCE(SUM(e.state='PENDING' AND e.excluded=0),0) AS pendingEntries,COALESCE(SUM(e.excluded=1),0) AS excludedEntries FROM collection_import_entries e JOIN collection_transfer_jobs j ON j.id=e.job_id WHERE j.id=:jobId AND j.owner_key=:ownerKey AND e.generation=j.generation")
    abstract suspend fun repositoryTotals(jobId: String, ownerKey: String): TransferRepositoryTotals
    @Query("SELECT COUNT(*) AS entries,COALESCE(SUM(x.quantity),0) AS copies,COALESCE(SUM(x.state='COMPLETED'),0) AS completedEntries,COALESCE(SUM(x.completed_quantity),0) AS completedCopies FROM collection_transfer_action_entries x JOIN collection_transfer_actions a ON a.id=x.action_id WHERE a.id=:actionId AND a.owner_key=:ownerKey")
    abstract suspend fun repositoryActionTotals(actionId: String, ownerKey: String): TransferRepositoryActionTotals
    @Query("SELECT COUNT(*) AS entries,COALESCE(SUM(x.quantity),0) AS copies,COUNT(*) AS completedEntries,COALESCE(SUM(x.completed_quantity),0) AS completedCopies FROM collection_transfer_action_entries x JOIN collection_transfer_actions a ON a.id=x.action_id WHERE a.job_id=:jobId AND a.owner_key=:ownerKey AND a.destination='WISHLIST' AND x.state='COMPLETED'")
    abstract suspend fun repositoryWishlistTotals(jobId: String, ownerKey: String): TransferRepositoryActionTotals
    @Query("SELECT f.* FROM collection_transfer_files f INNER JOIN collection_transfer_jobs j ON j.id = f.job_id WHERE j.id = :jobId AND j.owner_key = :ownerKey AND f.id = :fileId")
    abstract suspend fun getFile(jobId: String, ownerKey: String, fileId: String): CollectionTransferFileEntity?
    @Query("UPDATE collection_transfer_files SET job_id = :jobId WHERE receipt_id = :receiptId AND job_id IS NULL")
    protected abstract suspend fun linkFiles(receiptId: String, jobId: String)

    /** Exact receipt replay never recreates members or changes a previously captured owner. */
    @Transaction
    open suspend fun createReceipt(receipt: CollectionTransferReceiptEntity, files: List<CollectionTransferFileEntity>): Boolean {
        TransferJobId(receipt.id)
        require(files.size in 1..TransferLimits.MAX_FILES && files.map { it.id }.distinct().size == files.size)
        require(files.all { it.receiptId == receipt.id && it.jobId == null })
        files.forEach { TransferFileId(it.id) }
        val existing = getReceipt(receipt.id)
        if (existing != null) return existing.capturedOwner == receipt.capturedOwner && existing.authGeneration == receipt.authGeneration && getReceiptFiles(receipt.id).map { it.id } == files.sortedBy { it.fileOrder }.map { it.id }
        if(receipt.capturedOwner!=null) {
            requireOwner(receipt.capturedOwner)
            if(unfinishedCount(receipt.capturedOwner)+unboundReceiptCount(receipt.capturedOwner)>=3L) return false
        }
        insertReceipt(receipt)
        insertFiles(files)
        return true
    }

    /** Copy accounting includes failed partial sources; a final file is never overwritten by replay. */
    @Transaction
    open suspend fun recordReception(receiptId: String, fileId: String, bytes: Long, phase: String, sha256: String?, sourcePath: String?, error: String?): Boolean {
        val receipt=getReceipt(receiptId) ?: return false
        val files=getReceiptFiles(receiptId)
        val file=files.firstOrNull { it.id==fileId } ?: return false
        if(file.phase !in setOf("RECEIVING","FAILED_RETRYABLE") || bytes<file.bytes || bytes>TransferLimits.MAX_FILE_BYTES+TransferLimits.CHARACTER_BUFFER_SIZE || phase !in setOf("RECEIVING","PARSING","FAILED_RETRYABLE","REJECTED")) return false
        if(phase=="PARSING" && (sha256?.matches(Regex("[0-9a-f]{64}"))!=true || sourcePath==null || bytes>TransferLimits.MAX_FILE_BYTES)) return false
        updateFile(file.copy(bytes=bytes,phase=phase,sha256=sha256,sourcePath=sourcePath,error=error))
        updateReceipt(receipt.copy(receivedBytes=files.sumOf { if(it.id==fileId)bytes else it.bytes }))
        return true
    }

    /** Finishing intake retains every failed/unreceived member for later reporting. */
    @Transaction
    open suspend fun finishReception(receiptId: String, error: String?): Boolean {
        val receipt=getReceipt(receiptId) ?: return false
        updateReceipt(receipt.copy(phase=if(error==null)"PARSING" else "FAILED_RETRYABLE",error=error))
        return true
    }

    /** Binding consumes intake atomically; changed auth requires an explicit destination choice. */
    @Transaction
    open suspend fun bindReceipt(receiptId: String, ownerKey: String, authGeneration: Long, origin: String, now: Long, destinationChosen: Boolean = false): Boolean {
        requireOwner(ownerKey)
        getJob(receiptId, ownerKey)?.let { return true }
        val receipt = getReceipt(receiptId) ?: return false
        if (receipt.consumedAt != null || unfinishedCount(ownerKey) >= 3L) return false
        if (receipt.capturedOwner != null && receipt.capturedOwner != ownerKey) return false
        if (receipt.capturedOwner == null && receipt.authGeneration != authGeneration && !destinationChosen) return false
        insertJob(CollectionTransferJobEntity(receiptId, ownerKey, authGeneration, origin = origin, createdAt = now, updatedAt = now))
        linkFiles(receiptId, receiptId)
        updateReceipt(receipt.copy(consumedAt = now))
        return true
    }

    @Query("SELECT r.* FROM collection_import_rows r INNER JOIN collection_transfer_jobs j ON j.id = r.job_id WHERE j.id = :jobId AND j.owner_key = :ownerKey AND r.file_id = :fileId AND r.source_ordinal > :after ORDER BY r.source_ordinal LIMIT 200")
    abstract suspend fun rowPage(jobId: String, ownerKey: String, fileId: String, after: Long): List<CollectionImportRowEntity>

    @Query("SELECT c.scryfall_id FROM cards c WHERE c.scryfall_id IN (:printings) AND (EXISTS(SELECT 1 FROM cards owned INDEXED BY index_cards_oracle_id JOIN user_card_collection u INDEXED BY index_user_card_collection_scryfall_id ON u.scryfall_id=owned.scryfall_id WHERE c.oracle_id!='' AND owned.oracle_id=c.oracle_id AND u.is_deleted=0 AND ((substr(:ownerKey,1,8)='account:' AND u.user_id=substr(:ownerKey,9)) OR (substr(:ownerKey,1,6)='guest:' AND u.user_id IS NULL AND EXISTS(SELECT 1 FROM collection_transfer_guest_rows g WHERE g.row_id=u.id AND g.owner_key=:ownerKey)))) OR EXISTS(SELECT 1 FROM cards owned INDEXED BY index_cards_name JOIN user_card_collection u INDEXED BY index_user_card_collection_scryfall_id ON u.scryfall_id=owned.scryfall_id WHERE owned.name=c.name AND u.is_deleted=0 AND ((substr(:ownerKey,1,8)='account:' AND u.user_id=substr(:ownerKey,9)) OR (substr(:ownerKey,1,6)='guest:' AND u.user_id IS NULL AND EXISTS(SELECT 1 FROM collection_transfer_guest_rows g WHERE g.row_id=u.id AND g.owner_key=:ownerKey)))))")
    abstract suspend fun ownedReviewPrintings(ownerKey: String,printings: List<String>): List<String>
    @Query("SELECT COUNT(*) FROM collection_import_rows WHERE job_id = :jobId AND file_id = :fileId")
    protected abstract suspend fun rowCount(jobId: String, fileId: String): Long
    @Query("SELECT COUNT(*) AS records, COALESCE(SUM(kind = 'DATA'),0) AS dataRecords, COALESCE(SUM(kind = 'PREAMBLE'),0) AS preambleRecords, COALESCE(SUM(kind = 'HEADER'),0) AS headerRecords, COALESCE(SUM(kind = 'DATA' AND quantity IS NOT NULL AND error IS NULL),0) AS validRecords, COALESCE(SUM(kind = 'DATA' AND (quantity IS NULL OR error IS NOT NULL)),0) AS invalidRecords, COALESCE(SUM(CASE WHEN kind = 'DATA' AND error IS NULL THEN quantity ELSE 0 END),0) AS copies FROM collection_import_rows WHERE job_id = :jobId AND file_id = :fileId")
    protected abstract suspend fun storedRowCounts(jobId: String, fileId: String): TransferStoredRowCounts
    @Query("SELECT * FROM collection_import_rows WHERE job_id = :jobId AND file_id = :fileId AND source_ordinal = :ordinal")
    protected abstract suspend fun getStoredRow(jobId: String, fileId: String, ordinal: Long): CollectionImportRowEntity?
    @Query("SELECT MIN(source_ordinal) FROM collection_import_rows WHERE job_id = :jobId AND file_id = :fileId AND kind = 'DATA' AND state = 'PENDING'")
    protected abstract suspend fun firstPendingOrdinal(jobId: String, fileId: String): Long?
    @Query("SELECT COUNT(*) FROM collection_import_rows WHERE job_id = :jobId AND file_id = :fileId AND state = :state AND kind = 'DATA'")
    protected abstract suspend fun resolutionCount(jobId: String, fileId: String, state: String): Long
    @Query("SELECT r.* FROM collection_import_rows r INNER JOIN collection_transfer_jobs j ON j.id = r.job_id INNER JOIN collection_transfer_files f ON f.id = r.file_id AND f.job_id = j.id WHERE j.id = :jobId AND j.owner_key = :ownerKey AND r.file_id = :fileId AND r.state = 'PENDING' AND r.kind = 'DATA' AND f.phase = 'RESOLVING' AND f.selected = 1 AND f.retired = 0 AND r.source_ordinal > :after ORDER BY r.source_ordinal LIMIT 200")
    abstract suspend fun pendingResolutionPage(jobId: String, ownerKey: String, fileId: String, after: Long): List<CollectionImportRowEntity>

    /** Rows and their contiguous checkpoint commit together; parser replay ignores existing rows. */
    @Transaction
    open suspend fun stageParserRows(jobId: String, ownerKey: String, fileId: String, rows: List<CollectionImportRowEntity>): Boolean {
        val job = getJob(jobId, ownerKey) ?: return false
        val file = getFile(jobId, ownerKey, fileId) ?: return false
        if (job.filesFrozen || file.phase !in setOf("PARSING", "RECEIVING")) return false
        require(rows.size <= 200 && rows.all { it.jobId == jobId && it.fileId == fileId && it.sourceOrdinal > 0 && it.byteStart >= 0 && it.byteEnd >= it.byteStart && it.preview.length <= 300 })
        require(rows.zipWithNext().all { (a, b) -> b.sourceOrdinal == a.sourceOrdinal + 1L })
        val fresh = rows.filter { it.sourceOrdinal > file.parseOrdinal }
        if (fresh.isNotEmpty()) require(fresh.first().sourceOrdinal == file.parseOrdinal + 1L)
        require(fresh.all { it.kind in setOf("DATA", "HEADER", "PREAMBLE") && (it.quantity == null || it.quantity in 1L..Int.MAX_VALUE.toLong()) })
        insertRows(fresh.map { it.copy(state = if (it.kind != "DATA") "FORMAT" else if (it.quantity != null && it.error == null) "PENDING" else "INVALID") })
        updateFile(file.copy(phase = "PARSING", parseOrdinal = fresh.lastOrNull()?.sourceOrdinal ?: file.parseOrdinal))
        return true
    }

    /** Completion makes a source eligible for resolution, never for application. */
    @Transaction
    open suspend fun completeParsing(jobId: String, ownerKey: String, fileId: String, summary: TransferParseSummary): Boolean {
        val job = getJob(jobId, ownerKey) ?: return false
        val file = getFile(jobId, ownerKey, fileId) ?: return false
        if (job.filesFrozen || file.phase !in setOf("PARSING", "RECEIVING")) return false
        require(summary.records == file.parseOrdinal && rowCount(jobId, fileId) == summary.records)
        require(summary.records == summary.dataRecords + summary.preambleRecords + summary.headerRecords && summary.dataRecords == summary.validRecords + summary.invalidRecords)
        val counts = storedRowCounts(jobId, fileId)
        require(counts == TransferStoredRowCounts(summary.records, summary.dataRecords, summary.preambleRecords, summary.headerRecords, summary.validRecords, summary.invalidRecords, summary.copies))
        updateFile(file.copy(format = summary.format.name, phase = "RESOLVING", previousPhase = "PARSING", dataRecords = summary.dataRecords, preambleRecords = summary.preambleRecords, headerRecords = summary.headerRecords, validRecords = summary.validRecords, invalidRecords = summary.invalidRecords, copies = summary.copies, unrepresentedColumns = Json.encodeToString(summary.unrepresentedColumns)))
        return true
    }

    /** Partial successes commit before retry; a missing printing is distinct from network failure. */
    @Transaction
    open suspend fun stageResolution(jobId: String, ownerKey: String, fileId: String, results: List<TransferStoredResolution>): Boolean {
        val job = getJob(jobId, ownerKey) ?: return false
        val file = getFile(jobId, ownerKey, fileId) ?: return false
        if (job.filesFrozen || file.phase != "RESOLVING" || !file.selected || file.retired) return false
        require(results.size <= 75 && results.map { it.ordinal }.distinct().size == results.size)
        for (result in results) {
            require((result.resolvedId != null && result.error == null) || (result.resolvedId == null && result.error in setOf("NOT_FOUND", "AMBIGUOUS")))
            val row = getStoredRow(jobId, fileId, result.ordinal) ?: error("Missing staged record")
            require(row.kind == "DATA" && row.quantity != null)
            if (row.state == "PENDING") updateRow(row.copy(resolvedId = result.resolvedId, state = if (result.resolvedId != null) "RESOLVED" else "UNRESOLVED", error = result.error, sourceGroupKey=result.resolvedId?.let { transferSourceKey(it,row.isFoil,row.condition,row.language) } ?: ""))
        }
        val contiguous = firstPendingOrdinal(jobId, fileId)?.minus(1L) ?: file.parseOrdinal
        updateFile(file.copy(resolveOrdinal = contiguous, resolvedRecords = resolutionCount(jobId, fileId, "RESOLVED"), unresolvedRecords = resolutionCount(jobId, fileId, "UNRESOLVED")))
        return true
    }

    /** Results, resume cursor and successful failure reset share one transaction. */
    @Transaction
    open suspend fun commitPreparationResults(jobId: String, ownerKey: String, fileId: String, results: List<TransferStoredResolution>, now: Long): Boolean {
        val job=getJob(jobId,ownerKey) ?: return false
        if(job.phase in setOf("FAILED_RETRYABLE","PAUSED_OWNER","PAUSED_BY_USER","DISCARDED") || job.filesFrozen) return false
        if(!stageResolution(jobId,ownerKey,fileId,results)) return false
        val file=getFile(jobId,ownerKey,fileId)!!
        updateJob(job.copy(phase="RESOLVING",previousPhase="RESOLVING",networkFailures=0,nextAttemptAt=0L,workCursor="$fileId:${file.resolveOrdinal}",updatedAt=now,error=null))
        return true
    }

    /** Offline/shared cooldown waits do not consume the five genuine attempt failures. */
    @Transaction
    open suspend fun waitPreparation(jobId: String, ownerKey: String, nextAttemptAt: Long, realFailure: Boolean): Boolean {
        val job=getJob(jobId,ownerKey) ?: return false
        if(job.filesFrozen || job.phase in setOf("FAILED_RETRYABLE","PAUSED_OWNER","PAUSED_BY_USER","DISCARDED")) return false
        val failures=if(realFailure)minOf(5,job.networkFailures+1) else job.networkFailures
        updateJob(job.copy(phase=if(failures>=5)"FAILED_RETRYABLE" else "WAITING_NETWORK",previousPhase="RESOLVING",networkFailures=failures,nextAttemptAt=maxOf(job.nextAttemptAt,nextAttemptAt),error="NETWORK_RETRYABLE"))
        return true
    }

    /** A source becomes review-ready only after every valid data record has a durable outcome. */
    @Transaction
    open suspend fun completeResolution(jobId: String, ownerKey: String, fileId: String): Boolean {
        val job = getJob(jobId, ownerKey) ?: return false
        val file = getFile(jobId, ownerKey, fileId) ?: return false
        if (job.filesFrozen || file.phase != "RESOLVING" || firstPendingOrdinal(jobId, fileId) != null) return false
        val resolved = resolutionCount(jobId, fileId, "RESOLVED")
        val unresolved = resolutionCount(jobId, fileId, "UNRESOLVED")
        require(file.validRecords == resolved + unresolved)
        updateFile(file.copy(phase = "REVIEW_READY", resolveOrdinal = file.parseOrdinal, resolvedRecords = resolved, unresolvedRecords = unresolved))
        return true
    }

    /** Rebuilds the current selection without mutating an already-frozen apply payload. */
    @Transaction
    open suspend fun beginReviewRebuild(jobId: String, ownerKey: String): Long? {
        val job = getJob(jobId, ownerKey) ?: return null
        if (job.filesFrozen || job.appliedEntries != 0L) return null
        invalidateReview(job)
        return job.generation + 1L
    }

    /** Failed file prefixes remain reconstructable but cannot feed resolution or review. */
    @Transaction
    open suspend fun rejectFile(jobId: String, ownerKey: String, fileId: String, error: String): Boolean {
        val job = getJob(jobId, ownerKey) ?: return false
        val file = getFile(jobId, ownerKey, fileId) ?: return false
        if (job.filesFrozen) return false
        updateFile(file.copy(previousPhase = file.phase, phase = "REJECTED", error = error))
        invalidateReview(job)
        return true
    }

    @Query("DELETE FROM collection_import_entries WHERE job_id = :jobId AND applied_quantity = 0")
    protected abstract suspend fun deleteUnappliedEntries(jobId: String)
    @Query("DELETE FROM collection_import_provenance WHERE job_id = :jobId AND participated = 0")
    protected abstract suspend fun deleteUnappliedProvenance(jobId: String)
    @Query("UPDATE collection_import_rows SET entry_id = NULL WHERE job_id = :jobId")
    protected abstract suspend fun clearEntryLinks(jobId: String)

    private suspend fun invalidateReview(job: CollectionTransferJobEntity) {
        require(!job.filesFrozen && job.appliedEntries == 0L && job.generation < Long.MAX_VALUE && job.payloadVersion < Long.MAX_VALUE)
        clearSavedReviewEdits(job.id)
        saveReviewEdits(job.id,job.generation+1L)
        advanceReviewDecisions(job.id,job.generation+1L)
        deleteUnappliedProvenance(job.id)
        deleteUnappliedEntries(job.id)
        clearEntryLinks(job.id)
        invalidateAllReviewActions(job.id)
        updateJob(job.copy(generation = job.generation + 1L, payloadVersion = job.payloadVersion + 1L, phase = "RESOLVING", confirmedGeneration = null, confirmedPayloadVersion = null, acceptExclusions = false, acceptRepeats = false, fingerprint = null, workCursor = null))
    }

    /** Membership changes invalidate all review consent before rebuilding aggregates. */
    @Transaction
    open suspend fun selectFile(jobId: String, ownerKey: String, fileId: String, generation: Long, selected: Boolean, acceptRepeat: Boolean = false): Boolean {
        val job = getJob(jobId, ownerKey) ?: return false
        val file = getFile(jobId, ownerKey, fileId) ?: return false
        if (job.filesFrozen || job.generation != generation || file.retired) return false
        if (selected && file.duplicateOf != null && !acceptRepeat) return false
        if (file.selected == selected) return true
        updateFile(file.copy(selected = selected))
        invalidateReview(job)
        if (selected && acceptRepeat) updateJob(getJob(jobId, ownerKey)!!.copy(acceptRepeats = true))
        return true
    }

    @Query("SELECT e.* FROM collection_import_entries e INNER JOIN collection_transfer_jobs j ON j.id = e.job_id WHERE j.id = :jobId AND j.owner_key = :ownerKey AND j.generation = :generation AND e.generation = :generation AND e.id > :afterId ORDER BY e.id LIMIT 50")
    abstract suspend fun reviewPage(jobId: String, ownerKey: String, generation: Long, afterId: String): List<CollectionImportEntryEntity>
    @Query("SELECT p.* FROM collection_import_provenance p INNER JOIN collection_transfer_jobs j ON j.id = p.job_id WHERE j.id = :jobId AND j.owner_key = :ownerKey AND p.entry_id = :entryId ORDER BY p.file_id LIMIT 10")
    abstract suspend fun provenance(jobId: String, ownerKey: String, entryId: String): List<CollectionImportProvenanceEntity>
    @Query("SELECT * FROM collection_transfer_file_history WHERE owner_key = :ownerKey AND sha256 = :sha256")
    abstract suspend fun history(ownerKey: String, sha256: String): CollectionTransferFileHistoryEntity?
    @Query("SELECT * FROM collection_import_entries WHERE id = :entryId")
    protected abstract suspend fun storedEntry(entryId: String): CollectionImportEntryEntity?
    @Query("UPDATE collection_import_rows SET entry_id = :entryId WHERE job_id = :jobId AND file_id = :fileId AND resolved_id = :scryfallId AND is_foil = :foil AND condition = :condition AND language = :language AND state = 'RESOLVED'")
    protected abstract suspend fun linkEntryRows(jobId: String, fileId: String, entryId: String, scryfallId: String, foil: Boolean, condition: String, language: String)
    @Query("SELECT COUNT(*) AS records, COALESCE(SUM(quantity),0) AS copies FROM collection_import_rows WHERE job_id = :jobId AND file_id = :fileId AND entry_id = :entryId AND state = 'RESOLVED'")
    protected abstract suspend fun entrySourceCounts(jobId: String, fileId: String, entryId: String): TransferStoredSourceCounts
    @Query("SELECT COALESCE(SUM(source_records),0) AS records, COALESCE(SUM(source_copies),0) AS copies FROM collection_import_provenance WHERE job_id = :jobId AND generation = :generation")
    protected abstract suspend fun provenanceCounts(jobId: String, generation: Long): TransferStoredSourceCounts
    @Query("SELECT COUNT(*) AS records, COALESCE(SUM(r.quantity),0) AS copies FROM collection_import_rows r INNER JOIN collection_transfer_files f ON f.id = r.file_id AND f.job_id = r.job_id WHERE r.job_id = :jobId AND r.state = 'RESOLVED' AND f.selected = 1 AND f.retired = 0 AND f.phase = 'REVIEW_READY'")
    protected abstract suspend fun selectedResolvedCounts(jobId: String): TransferStoredSourceCounts
    @Query("SELECT COALESCE(SUM(source_copies),0) FROM collection_import_provenance WHERE job_id = :jobId AND entry_id = :entryId")
    protected abstract suspend fun entrySourceCopies(jobId: String, entryId: String): Long
    @Query("SELECT COALESCE(SUM(CASE WHEN excluded = 0 THEN quantity ELSE 0 END),0) AS acceptedCopies, COALESCE(SUM(applied_quantity),0) AS appliedCopies, COALESCE(SUM(state = 'PENDING' AND excluded = 0),0) AS pendingEntries, COALESCE(SUM(excluded = 1),0) AS excludedEntries, COALESCE(SUM(excluded = 0 AND (error IS NOT NULL OR quantity < 1 OR quantity > 2147483647)),0) AS invalidEntries FROM collection_import_entries WHERE job_id = :jobId AND generation = :generation")
    protected abstract suspend fun entryCounts(jobId: String, generation: Long): TransferStoredEntryCounts

    /** Joint review is published only after all selected members have complete preparation. */
    @Transaction
    open suspend fun publishReview(jobId: String, ownerKey: String, generation: Long, fingerprint: String, now: Long): Boolean {
        val job = getJob(jobId, ownerKey) ?: return false
        if (job.filesFrozen || job.generation != generation || job.phase != "RESOLVING") return false
        val selected = currentFiles(jobId, ownerKey).filter { it.selected }
        if (selected.isEmpty() || selected.any { it.phase != "REVIEW_READY" } || !withinBudget(selected, TransferLimits.MAX_DATA_RECORDS) { it.dataRecords } || !withinBudget(selected, TransferLimits.MAX_PREAMBLE_RECORDS) { it.preambleRecords }) {
            updateJob(job.copy(phase = "WAITING_FILE_DECISION", error = "SELECTION_LIMIT", updatedAt = now))
            return false
        }
        val counts = entryCounts(jobId, generation)
        require(provenanceCounts(jobId, generation) == selectedResolvedCounts(jobId))
        updateJob(job.copy(phase = if (counts.invalidEntries > 0L || pendingReviewDecisions(jobId)>0L) "REVIEW_REQUIRED" else "REVIEW_READY", fingerprint = fingerprint, dataRecords = selected.sumOf { it.dataRecords }, preambleRecords = selected.sumOf { it.preambleRecords }, validRecords = selected.sumOf { it.validRecords }, invalidRecords = selected.sumOf { it.invalidRecords }, resolvedRecords = selected.sumOf { it.resolvedRecords }, unresolvedRecords = selected.sumOf { it.unresolvedRecords }, acceptedCopies = counts.acceptedCopies, updatedAt = now, error = if (pendingReviewDecisions(jobId)>0L) "SOURCE_MEMBERSHIP_CHANGED" else if (counts.invalidEntries > 0L) "QUANTITY_OVERFLOW" else null))
        return true
    }

    /** Consent is versioned independently from worker scheduling and freezes only on apply start. */
    @Transaction
    open suspend fun acknowledgeReview(jobId: String, ownerKey: String, confirmation: TransferConfirmation): Boolean {
        val job = getJob(jobId, ownerKey) ?: return false
        val counts = entryCounts(jobId, job.generation)
        if (counts.invalidEntries != 0L || pendingReviewDecisions(jobId) != 0L) return false
        val files = currentFiles(jobId, ownerKey)
        val summary = TransferSummary(TransferJobId(jobId), if (ownerKey.startsWith("account:")) TransferOwner.Account(ownerKey.removePrefix("account:")) else TransferOwner.VerifiedGuest(ownerKey.removePrefix("guest:")), TransferPhase.valueOf(job.phase), job.generation, job.payloadVersion, files.map { file ->
            TransferFileSummary(TransferFileId(file.id), file.fileOrder, file.format?.let { CollectionFileFormat.valueOf(it) }, TransferPhase.valueOf(file.phase), file.selected, file.duplicateOf != null, file.priorParticipation, file.bytes, file.dataRecords, file.preambleRecords, file.validRecords, file.invalidRecords, file.resolvedRecords, file.unresolvedRecords, null)
        }, counts.acceptedCopies, counts.appliedCopies, counts.acceptedCopies - counts.appliedCopies, job.appliedEntries, counts.pendingEntries, null, counts.excludedEntries)
        if (!confirmation.matches(summary)) return false
        updateJob(job.copy(confirmedGeneration = confirmation.generation, confirmedPayloadVersion = confirmation.payloadVersion, acceptExclusions = confirmation.acceptExclusions, acceptRepeats = confirmation.acceptRepeatedFiles))
        return true
    }

    /** Review acknowledgement cannot authorize either destination. */
    open suspend fun freezeConfirmedPayload(jobId: String, ownerKey: String): Boolean {
        return false
    }

    @Query("SELECT e.* FROM collection_import_entries e JOIN collection_transfer_jobs j ON j.id=e.job_id WHERE e.job_id=:jobId AND j.owner_key=:ownerKey AND e.id=:entryId AND e.generation=j.generation")
    abstract suspend fun reviewEntry(jobId: String, ownerKey: String, entryId: String): CollectionImportEntryEntity?
    @Update
    protected abstract suspend fun updateEntry(entry: CollectionImportEntryEntity)
    @Query("UPDATE collection_transfer_actions SET phase='INVALIDATED' WHERE job_id=:jobId AND phase='CONFIRMED' AND id IN (SELECT action_id FROM collection_transfer_action_entries WHERE entry_id=:entryId)")
    protected abstract suspend fun invalidateEntryActions(jobId: String, entryId: String)
    @Query("SELECT EXISTS(SELECT 1 FROM collection_import_entries WHERE job_id=:jobId AND id!=:entryId AND scryfall_id=:printing AND is_foil=:foil AND condition=:condition AND language=:language)")
    protected abstract suspend fun variantCollision(jobId: String, entryId: String, printing: String, foil: Boolean, condition: String, language: String): Boolean
    @Query("SELECT e.id FROM collection_import_entries e JOIN collection_transfer_jobs j ON j.id=e.job_id WHERE e.job_id=:jobId AND j.owner_key=:ownerKey AND e.id!=:entryId AND e.scryfall_id=:printing AND e.is_foil=:foil AND e.condition=:condition AND e.language=:language LIMIT 1")
    abstract suspend fun collidingReviewEntry(jobId: String,ownerKey: String,entryId: String,printing: String,foil: Boolean,condition: String,language: String): String?

    /** Editing one pending entry invalidates only commands that captured that entry. */
    @Transaction
    open suspend fun editPendingEntry(jobId: String, ownerKey: String, entryId: String, expectedVersion: Long, printing: String, foil: Boolean, condition: String, language: String, quantity: Long, destination: TransferDestination, excluded: Boolean = false): Boolean {
        val job = getJob(jobId, ownerKey) ?: return false
        val entry = reviewEntry(jobId, ownerKey, entryId) ?: return false
        if(reviewDecision(jobId,ownerKey,entryId)?.status=="PENDING") return false
        if (job.phase !in setOf("REVIEW_READY", "REVIEW_REQUIRED") || entry.entryVersion != expectedVersion || entry.activeActionId != null || entry.appliedQuantity != 0L || entry.state != "PENDING" || entry.entryVersion == Long.MAX_VALUE || job.intentRevision == Long.MAX_VALUE || job.payloadVersion == Long.MAX_VALUE) return false
        if (quantity<1L || (!excluded && quantity>Int.MAX_VALUE.toLong()) || printing.isBlank() || CollectionCardAttributes.conditionCodeOrNull(condition) == null || CollectionCardAttributes.languageCodeOrNull(language) == null) return false
        if (variantCollision(jobId, entryId, printing, foil, condition, language)) return false
        invalidateEntryActions(jobId, entryId)
        val payloadChanged=entry.scryfallId!=printing || entry.isFoil!=foil || entry.condition!=condition || entry.language!=language || entry.quantity!=quantity
        updateEntry(entry.copy(scryfallId=printing, isFoil=foil, condition=condition, language=language, quantity=quantity, destination=destination.name, entryVersion=entry.entryVersion+1L, payloadEdited=entry.payloadEdited || payloadChanged, excluded=excluded, error=if(excluded)entry.error else null))
        val acceptedCopies=if(entry.quantity!=quantity || entry.excluded!=excluded)entryCounts(jobId,job.generation).acceptedCopies else job.acceptedCopies
        updateJob(job.copy(intentRevision=job.intentRevision+1L,payloadVersion=job.payloadVersion+1L,acceptedCopies=acceptedCopies,confirmedGeneration=null,confirmedPayloadVersion=null,acceptExclusions=false))
        return true
    }

    @Query("UPDATE collection_transfer_actions SET phase='INVALIDATED' WHERE job_id=:jobId AND phase='CONFIRMED' AND id IN (SELECT a.action_id FROM collection_transfer_action_entries a JOIN collection_import_entries e ON e.id=a.entry_id WHERE e.job_id=:jobId AND e.active_action_id IS NULL AND e.applied_quantity=0 AND e.state='PENDING' AND e.excluded=0)")
    protected abstract suspend fun invalidatePendingActions(jobId: String)
    @Query("UPDATE collection_import_entries SET destination=:destination, entry_version=entry_version+1 WHERE job_id=:jobId AND generation=:generation AND active_action_id IS NULL AND applied_quantity=0 AND state='PENDING' AND excluded=0 AND error IS NULL AND entry_version<9223372036854775807")
    protected abstract suspend fun choosePendingDestination(jobId: String, generation: Long, destination: String): Int

    /** A deliberate bulk choice covers only unlocked pending entries in the current review. */
    @Transaction
    open suspend fun chooseAllDestination(jobId: String, ownerKey: String, generation: Long, intentRevision: Long, destination: TransferDestination): Long? {
        val job = getJob(jobId, ownerKey) ?: return null
        if (job.generation!=generation || job.intentRevision!=intentRevision || intentRevision==Long.MAX_VALUE || job.payloadVersion==Long.MAX_VALUE || job.phase !in setOf("REVIEW_READY", "REVIEW_REQUIRED")) return null
        invalidatePendingActions(jobId)
        val count=choosePendingDestination(jobId,generation,destination.name).toLong()
        updateJob(job.copy(intentRevision=intentRevision+1L,payloadVersion=job.payloadVersion+1L,confirmedGeneration=null,confirmedPayloadVersion=null,acceptExclusions=false))
        return count
    }

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertAction(action: CollectionTransferActionEntity)
    @Query("SELECT a.* FROM collection_transfer_actions a JOIN collection_transfer_jobs j ON j.id=a.job_id WHERE a.id=:actionId AND a.owner_key=:ownerKey AND j.owner_key=:ownerKey")
    abstract suspend fun action(actionId: String, ownerKey: String): CollectionTransferActionEntity?
    @Query("SELECT x.* FROM collection_transfer_action_entries x JOIN collection_transfer_actions a ON a.id=x.action_id JOIN collection_transfer_jobs j ON j.id=a.job_id WHERE x.action_id=:actionId AND a.owner_key=:ownerKey AND j.owner_key=:ownerKey AND x.entry_id>:afterId ORDER BY x.entry_id LIMIT 50")
    abstract suspend fun actionPage(actionId: String, ownerKey: String, afterId: String): List<CollectionTransferActionEntryEntity>
    @Query("INSERT INTO collection_transfer_action_entries(action_id,entry_id,entry_version,scryfall_id,is_foil,condition,language,quantity,state,completed_quantity) SELECT :actionId,id,entry_version,scryfall_id,is_foil,condition,language,quantity,'PENDING',0 FROM collection_import_entries WHERE job_id=:jobId AND generation=:generation AND destination=:destination AND (:entryId IS NULL OR (id=:entryId AND entry_version=:version)) AND excluded=0 AND error IS NULL AND state='PENDING' AND applied_quantity=0 AND active_action_id IS NULL AND quantity BETWEEN 1 AND 2147483647")
    protected abstract suspend fun captureActionEntries(actionId: String, jobId: String, generation: Long, destination: String, entryId: String?, version: Long)
    @Query("SELECT COUNT(*) FROM collection_transfer_action_entries WHERE action_id=:actionId")
    protected abstract suspend fun actionEntryCount(actionId: String): Long
    @Query("DELETE FROM collection_transfer_actions WHERE id=:actionId")
    protected abstract suspend fun deleteEmptyAction(actionId: String)

    /** Confirmation snapshots explicit intentions; recognition and review acknowledgement are insufficient. */
    @Transaction
    open suspend fun confirmAction(ownerKey: String, request: TransferActionRequest, now: Long): Boolean {
        val job=getJob(request.jobId.value,ownerKey) ?: return false
        if (request.destination==TransferDestination.NONE || job.generation!=request.generation || job.phase !in setOf("REVIEW_READY","REVIEW_REQUIRED")) return false
        val scope=request.scope
        val scopeId=if(scope is TransferActionScope.Entry) scope.id else "*"
        val version=when(scope) { is TransferActionScope.Entry -> scope.version; is TransferActionScope.DestinationSelection -> scope.intentRevision }
        val existing=action(request.id.value,ownerKey)
        if(existing!=null) return existing.jobId==job.id && existing.generation==job.generation && existing.destination==request.destination.name && existing.scope==scopeId && existing.scopeVersion==version && existing.phase in setOf("CONFIRMED","FROZEN")
        if(scope is TransferActionScope.DestinationSelection && scope.intentRevision!=job.intentRevision) return false
        val files=currentFiles(job.id,ownerKey)
        if(scope is TransferActionScope.DestinationSelection && (job.invalidRecords+job.unresolvedRecords>0L || entryCounts(job.id,job.generation).excludedEntries>0L) && !request.acceptExclusions) return false
        if(files.any { it.selected && (it.duplicateOf!=null || it.priorParticipation) } && !request.acceptRepeatedFiles) return false
        insertAction(CollectionTransferActionEntity(request.id.value,job.id,ownerKey,job.generation,request.destination.name,scopeId,version,createdAt=now))
        captureActionEntries(request.id.value,job.id,job.generation,request.destination.name,if(scope is TransferActionScope.Entry)scope.id else null,version)
        if(actionEntryCount(request.id.value)==0L) { deleteEmptyAction(request.id.value); return false }
        if(request.acceptRepeatedFiles) updateJob(job.copy(acceptRepeats=true))
        return true
    }

    @Query("SELECT COUNT(*) FROM collection_transfer_action_entries x JOIN collection_import_entries e ON e.id=x.entry_id WHERE x.action_id=:actionId AND e.job_id=:jobId AND e.generation=:generation AND e.entry_version=x.entry_version AND e.destination=:destination AND e.active_action_id IS NULL AND e.applied_quantity=0 AND e.state='PENDING' AND e.excluded=0 AND e.error IS NULL AND e.quantity=x.quantity AND e.scryfall_id=x.scryfall_id AND e.is_foil=x.is_foil AND e.condition=x.condition AND e.language=x.language")
    protected abstract suspend fun eligibleActionCount(actionId: String, jobId: String, generation: Long, destination: String): Long
    @Query("UPDATE collection_import_entries SET active_action_id=:actionId WHERE id IN (SELECT entry_id FROM collection_transfer_action_entries WHERE action_id=:actionId)")
    protected abstract suspend fun lockActionEntries(actionId: String)
    @Query("UPDATE collection_transfer_actions SET phase='FROZEN' WHERE id=:actionId")
    protected abstract suspend fun freezeActionRecord(actionId: String)

    /** Only captured entries lock; other pending destinations remain editable after this transaction. */
    @Transaction
    open suspend fun freezeAction(actionId: String, ownerKey: String): Boolean {
        val action=action(actionId,ownerKey) ?: return false
        val job=getJob(action.jobId,ownerKey) ?: return false
        if(action.phase=="FROZEN") return true
        if(action.phase!="CONFIRMED" || job.generation!=action.generation || job.phase !in setOf("REVIEW_READY","REVIEW_REQUIRED")) return false
        val count=actionEntryCount(actionId)
        if(count==0L || eligibleActionCount(actionId,job.id,job.generation,action.destination)!=count) return false
        lockActionEntries(actionId)
        freezeActionRecord(actionId)
        updateJob(job.copy(filesFrozen=true))
        return true
    }

    /** Aggregate replacement is confined to the current unconfirmed, unapplied generation. */
    @Transaction
    open suspend fun stageReviewEntries(jobId: String, ownerKey: String, generation: Long, entries: List<CollectionImportEntryEntity>, sources: List<CollectionImportProvenanceEntity>): Boolean {
        val job = getJob(jobId, ownerKey) ?: return false
        if (job.filesFrozen || job.generation != generation || job.phase != "RESOLVING" || job.appliedEntries != 0L) return false
        require(entries.size <= 200 && sources.size <= 2000)
        require(entries.all { it.jobId == jobId && it.generation == generation && it.appliedQuantity == 0L && it.state == "PENDING" })
        require(sources.all { it.jobId == jobId && it.generation == generation && !it.participated && entries.any { entry -> entry.id == it.entryId } })
        val members = currentFiles(jobId, ownerKey).filter { it.selected && it.phase == "REVIEW_READY" }.map { it.id }.toSet()
        require(sources.all { it.fileId in members })
        for (entry in entries) {
            val existing = storedEntry(entry.id)
            require(existing == null || (existing.jobId == jobId && existing.appliedQuantity == 0L && existing.generation == generation))
        }
        upsertEntries(entries.map { it.copy(sourceKey=transferSourceKey(it.scryfallId,it.isFoil,it.condition,it.language),sourceQuantity=it.quantity,sourceSignature=sources.filter { source -> source.entryId==it.id }.sortedBy { source -> source.fileId }.joinToString("|") { source -> "${source.fileId}:${source.sourceRecords}:${source.sourceCopies}" }) })
        for (source in sources) {
            val entry = entries.first { it.id == source.entryId }
            linkEntryRows(jobId, source.fileId, entry.id, entry.scryfallId, entry.isFoil, entry.condition, entry.language)
            require(entrySourceCounts(jobId, source.fileId, entry.id) == TransferStoredSourceCounts(source.sourceRecords, source.sourceCopies))
        }
        upsertProvenance(sources)
        for (entry in entries) require(entry.quantity == entrySourceCopies(jobId, entry.id))
        return true
    }

    @Query("SELECT COUNT(*) FROM collection_transfer_name_claims WHERE job_id = :jobId")
    protected abstract suspend fun nameClaimCount(jobId: String): Long
    @Query("SELECT EXISTS(SELECT 1 FROM collection_transfer_name_claims WHERE job_id = :jobId AND name_key = :nameKey)")
    protected abstract suspend fun hasNameClaim(jobId: String, nameKey: String): Boolean

    /** Repeated names reuse their claim after process death; only 100 distinct names are allowed. */
    @Transaction
    open suspend fun claimFallbackName(jobId: String, ownerKey: String, nameKey: String, now: Long): Boolean {
        val job = getJob(jobId, ownerKey) ?: return false
        if (job.filesFrozen || nameKey.isBlank()) return false
        if (hasNameClaim(jobId, nameKey)) return true
        if (nameClaimCount(jobId) >= 100L) return false
        insertNameClaim(CollectionTransferNameClaimEntity(jobId, nameKey, now))
        return true
    }

    @Query("SELECT q.* FROM collection_transfer_snapshot_queries q INNER JOIN collection_transfer_jobs j ON j.id = q.job_id WHERE j.id = :jobId AND j.owner_key = :ownerKey AND q.generation = :generation")
    abstract suspend fun snapshotQuery(jobId: String, ownerKey: String, generation: Long): CollectionTransferSnapshotQueryEntity?
    @Query("SELECT s.* FROM collection_transfer_snapshot_rows s INNER JOIN collection_transfer_jobs j ON j.id = s.job_id WHERE j.id = :jobId AND j.owner_key = :ownerKey AND s.generation = :generation AND s.ordinal > :after ORDER BY s.ordinal LIMIT 200")
    abstract suspend fun snapshotPage(jobId: String, ownerKey: String, generation: Long, after: Long): List<CollectionTransferSnapshotRowEntity>
    @Query("SELECT COUNT(*) AS records, COALESCE(SUM(quantity),0) AS copies FROM collection_transfer_snapshot_rows WHERE job_id = :jobId AND generation = :generation AND matched = 1")
    protected abstract suspend fun snapshotCounts(jobId: String, generation: Long): TransferStoredSourceCounts

    /** Frozen snapshots are immutable once published; generation metadata and rows commit together. */
    @Transaction
    open suspend fun stageSnapshot(jobId: String, ownerKey: String, query: CollectionTransferSnapshotQueryEntity, rows: List<CollectionTransferSnapshotRowEntity>): Boolean {
        getJob(jobId, ownerKey) ?: return false
        val existing = snapshotQuery(jobId, ownerKey, query.generation)
        if (existing?.phase == "READY") return false
        if (existing != null && (existing.source != query.source || existing.search != query.search || existing.advancedQuery != query.advancedQuery || existing.sort != query.sort || existing.grouping != query.grouping || existing.sortAscending != query.sortAscending)) return false
        require(query.jobId == jobId && rows.size <= 200 && rows.all { it.jobId == jobId && it.generation == query.generation && it.ordinal > 0L })
        insertSnapshotRows(rows)
        if (query.phase == "READY") require(snapshotCounts(jobId, query.generation) == TransferStoredSourceCounts(query.totalRows, query.totalCopies))
        upsertSnapshotQuery(query)
        return true
    }

    @Query("UPDATE collection_transfer_actions SET phase='INVALIDATED' WHERE job_id=:jobId AND phase='CONFIRMED'")
    protected abstract suspend fun invalidateAllReviewActions(jobId: String)
    @Query("DELETE FROM collection_transfer_review_decisions WHERE job_id=:jobId AND status!='PENDING' AND entry_id IN (SELECT id FROM collection_import_entries WHERE job_id=:jobId)")
    protected abstract suspend fun clearSavedReviewEdits(jobId: String)
    @Query("INSERT OR IGNORE INTO collection_transfer_review_decisions(job_id,entry_id,generation,source_key,reason,status,scryfall_id,is_foil,condition,language,quantity,destination,excluded,source_signature,source_quantity,payload_edited,entry_version) SELECT job_id,id,:generation,source_key,'REBUILD','SAVED',scryfall_id,is_foil,condition,language,quantity,destination,excluded,source_signature,source_quantity,payload_edited,entry_version FROM collection_import_entries WHERE job_id=:jobId AND applied_quantity=0 AND active_action_id IS NULL")
    protected abstract suspend fun saveReviewEdits(jobId: String, generation: Long)
    @Query("UPDATE collection_transfer_review_decisions SET generation=:generation WHERE job_id=:jobId")
    protected abstract suspend fun advanceReviewDecisions(jobId: String, generation: Long)
    @Query("SELECT COUNT(*) FROM collection_transfer_review_decisions WHERE job_id=:jobId AND status='PENDING'")
    protected abstract suspend fun pendingReviewDecisions(jobId: String): Long
    @Query("SELECT d.* FROM collection_transfer_review_decisions d JOIN collection_transfer_jobs j ON j.id=d.job_id WHERE d.job_id=:jobId AND j.owner_key=:ownerKey AND d.entry_id=:entryId")
    abstract suspend fun reviewDecision(jobId: String, ownerKey: String, entryId: String): CollectionTransferReviewDecisionEntity?
    @Query("SELECT d.* FROM collection_transfer_review_decisions d JOIN collection_transfer_jobs j ON j.id=d.job_id WHERE d.job_id=:jobId AND j.owner_key=:ownerKey AND d.status='PENDING' AND d.entry_id>:afterId ORDER BY d.entry_id LIMIT 50")
    abstract suspend fun reviewDecisionPage(jobId: String, ownerKey: String, afterId: String): List<CollectionTransferReviewDecisionEntity>
    @Query("SELECT * FROM collection_transfer_review_decisions WHERE job_id=:jobId AND source_key=:sourceKey LIMIT 1")
    protected abstract suspend fun savedReviewEdit(jobId: String, sourceKey: String): CollectionTransferReviewDecisionEntity?
    @Update
    protected abstract suspend fun updateReviewDecision(decision: CollectionTransferReviewDecisionEntity)
    @Query("UPDATE collection_transfer_review_decisions SET status='PENDING', reason='SOURCE_REMOVED' WHERE job_id=:jobId AND payload_edited=1 AND entry_id NOT IN (SELECT id FROM collection_import_entries WHERE job_id=:jobId)")
    protected abstract suspend fun markRemovedReviewEdits(jobId: String)
    @Query("SELECT r.source_group_key AS sourceKey,r.resolved_id AS printing,r.is_foil AS foil,r.condition,r.language,COUNT(*) AS records,SUM(r.quantity) AS copies FROM collection_import_rows r JOIN collection_transfer_files f ON f.id=r.file_id AND f.job_id=r.job_id JOIN collection_transfer_jobs j ON j.id=r.job_id WHERE r.job_id=:jobId AND j.owner_key=:ownerKey AND r.state='RESOLVED' AND r.source_group_key>:afterKey AND f.selected=1 AND f.retired=0 AND f.phase='REVIEW_READY' GROUP BY r.source_group_key ORDER BY r.source_group_key LIMIT 200")
    protected abstract suspend fun reviewSourceGroups(jobId: String, ownerKey: String, afterKey: String): List<TransferReviewSourceGroup>
    @Query("SELECT r.file_id AS fileId,COUNT(*) AS records,SUM(r.quantity) AS copies FROM collection_import_rows r JOIN collection_transfer_files f ON f.id=r.file_id AND f.job_id=r.job_id WHERE r.job_id=:jobId AND r.state='RESOLVED' AND r.resolved_id=:printing AND r.is_foil=:foil AND r.condition=:condition AND r.language=:language AND f.selected=1 AND f.retired=0 AND f.phase='REVIEW_READY' GROUP BY r.file_id ORDER BY r.file_id LIMIT 10")
    protected abstract suspend fun reviewSourceContributions(jobId: String, printing: String, foil: Boolean, condition: String, language: String): List<TransferReviewSourceContribution>
    @Query("SELECT EXISTS(SELECT 1 FROM collection_import_rows r JOIN collection_transfer_files f ON f.id=r.file_id AND f.job_id=r.job_id WHERE r.job_id=:jobId AND r.resolved_id=:printing AND r.is_foil=:foil AND r.condition=:condition AND r.language=:language AND r.state='RESOLVED' AND f.selected=1 AND f.retired=0 AND f.phase='REVIEW_READY')")
    protected abstract suspend fun selectedSourceVariant(jobId: String, printing: String, foil: Boolean, condition: String, language: String): Boolean
    @Query("SELECT j.* FROM collection_transfer_jobs j WHERE j.owner_key=:ownerKey AND j.id!=:jobId AND j.fingerprint=:fingerprint AND j.phase NOT IN ('COMPLETED','COMPLETED_WITH_EXCLUSIONS','DISCARDED','REJECTED') ORDER BY j.created_at,j.id LIMIT 1")
    abstract suspend fun matchingPendingJob(jobId: String, ownerKey: String, fingerprint: String): CollectionTransferJobEntity?
    @Query("SELECT f.* FROM collection_transfer_files f JOIN collection_transfer_jobs j ON j.id=f.job_id WHERE f.job_id=:jobId AND j.owner_key=:ownerKey AND f.id>:afterId ORDER BY f.id LIMIT 50")
    abstract suspend fun fileInventoryPage(jobId: String, ownerKey: String, afterId: String): List<CollectionTransferFileEntity>
    @Query("SELECT r.* FROM collection_import_rows r JOIN collection_transfer_jobs j ON j.id=r.job_id WHERE r.job_id=:jobId AND j.owner_key=:ownerKey AND r.error IS NOT NULL AND (r.file_id>:afterFile OR (r.file_id=:afterFile AND r.source_ordinal>:afterOrdinal)) ORDER BY r.file_id,r.source_ordinal LIMIT 200")
    abstract suspend fun errorPreviewPage(jobId: String, ownerKey: String, afterFile: String, afterOrdinal: Long): List<CollectionImportRowEntity>
    @Query("SELECT COUNT(*) FROM collection_import_rows r JOIN collection_transfer_jobs j ON j.id=r.job_id WHERE j.id=:jobId AND j.owner_key=:ownerKey AND r.error IS NOT NULL")
    abstract suspend fun errorPreviewCount(jobId: String, ownerKey: String): Long

    /** Default content deduplication changes membership before a review can be published. */
    @Transaction
    open suspend fun prepareReviewMembership(jobId: String, ownerKey: String): Boolean {
        val job=getJob(jobId,ownerKey) ?: return false
        if(job.filesFrozen || job.appliedEntries!=0L) return false
        val seen=mutableMapOf<String,String>()
        var changed=false
        for(file in currentFiles(jobId,ownerKey)) {
            val hash=file.sha256 ?: continue
            val duplicate=seen[hash]
            if(duplicate==null) seen[hash]=file.id
            val selected=if(duplicate!=null && file.duplicateOf==null)false else file.selected
            if(selected!=file.selected) changed=true
            updateFile(file.copy(duplicateOf=duplicate,selected=selected,priorParticipation=history(ownerKey,hash)!=null))
        }
        if(changed) invalidateReview(job)
        return true
    }

    /** Reselected private sources retire only the failed member and preserve its historical inventory. */
    @Transaction
    open suspend fun reserveSourceReplacement(jobId: String,ownerKey: String,generation: Long,fileId: String,newId: String): CollectionTransferFileEntity? {
        val job=getJob(jobId,ownerKey) ?: return null
        val original=getFile(jobId,ownerKey,fileId) ?: return null
        if(job.filesFrozen || job.generation!=generation || original.retired || original.phase !in setOf("REJECTED","FAILED_RETRYABLE"))return null
        TransferFileId(newId)
        val replacement=CollectionTransferFileEntity(newId,original.receiptId,jobId,original.fileOrder,revision=original.revision+1L,replacesId=original.id,retired=true,selected=false)
        insertFiles(listOf(replacement))
        return replacement
    }
    @Transaction
    open suspend fun completeSourceReplacement(jobId: String,ownerKey: String,generation: Long,fileId: String,newId: String): Boolean {
        val job=getJob(jobId,ownerKey) ?: return false
        val original=getFile(jobId,ownerKey,fileId) ?: return false
        val replacement=getFile(jobId,ownerKey,newId) ?: return false
        if(job.filesFrozen || job.generation!=generation || original.retired || replacement.replacesId!=original.id || !replacement.retired || replacement.phase!="PARSING" || replacement.sha256==null)return false
        updateFile(original.copy(retired=true,selected=false))
        updateFile(replacement.copy(retired=false,selected=original.selected))
        invalidateReview(job)
        updateJob(getJob(jobId,ownerKey)!!.copy(phase="PARSING",previousPhase="PARSING",nextAttemptAt=0L,error=null))
        return true
    }
    @Transaction
    open suspend fun replaceFailedSource(jobId: String, ownerKey: String, generation: Long, fileId: String, replacement: CollectionTransferFileEntity): Boolean {
        val job=getJob(jobId,ownerKey) ?: return false
        val file=getFile(jobId,ownerKey,fileId) ?: return false
        if(job.filesFrozen || job.generation!=generation || file.retired || file.phase !in setOf("REJECTED","FAILED_RETRYABLE")) return false
        TransferFileId(replacement.id)
        require(replacement.id!=file.id && replacement.receiptId==file.receiptId && replacement.jobId==jobId && replacement.fileOrder==file.fileOrder && replacement.phase=="PARSING" && replacement.parseOrdinal==0L && replacement.bytes in 0L..TransferLimits.MAX_FILE_BYTES && replacement.sha256?.matches(Regex("[0-9a-f]{64}"))==true && replacement.sourcePath=="collection-transfers/$jobId/${replacement.id}/source")
        val receipt=getReceipt(file.receiptId) ?: return false
        if(replacement.bytes>TransferLimits.MAX_BATCH_BYTES-receipt.receivedBytes) return false
        insertFiles(listOf(replacement.copy(revision=file.revision+1L,replacesId=file.id,selected=file.selected)))
        updateFile(file.copy(retired=true,selected=false))
        updateReceipt(receipt.copy(receivedBytes=receipt.receivedBytes+replacement.bytes))
        invalidateReview(job)
        updateJob(getJob(jobId,ownerKey)!!.copy(phase="PARSING",previousPhase="PARSING",nextAttemptAt=0L,error=null))
        return true
    }

    /** One transaction builds at most200 groups, including their source links and resume cursor. */
    @Transaction
    open suspend fun rebuildReviewSlice(jobId: String, ownerKey: String, generation: Long, now: Long): Boolean? {
        var job=getJob(jobId,ownerKey) ?: return null
        if(job.filesFrozen || job.generation!=generation || job.appliedEntries!=0L || job.phase!="RESOLVING") return null
        val selected=currentFiles(jobId,ownerKey).filter { it.selected }
        if(selected.isEmpty() || selected.any { it.phase!="REVIEW_READY" || it.sha256?.matches(Regex("[0-9a-f]{64}"))!=true } || !withinBudget(selected,TransferLimits.MAX_DATA_RECORDS) { it.dataRecords } || !withinBudget(selected,TransferLimits.MAX_PREAMBLE_RECORDS) { it.preambleRecords }) {
            updateJob(job.copy(phase="WAITING_FILE_DECISION",error="SELECTION_LIMIT",updatedAt=now)); return null
        }
        val after=job.workCursor?.takeIf { it.startsWith("review:") }?.removePrefix("review:") ?: ""
        val groups=reviewSourceGroups(jobId,ownerKey,after)
        for(group in groups) {
            val contributions=reviewSourceContributions(jobId,group.printing,group.foil,group.condition,group.language)
            val copies=contributions.fold(0L) { total,part -> checkedTransferCopies(total,part.copies) }
            require(copies==group.copies && contributions.sumOf { it.records }==group.records)
            val signature=contributions.joinToString("|") { "${it.fileId}:${it.records}:${it.copies}" }
            val saved=savedReviewEdit(jobId,group.sourceKey)
            val changed=saved?.payloadEdited==true && saved.sourceSignature!=signature
            val id=saved?.entryId ?: java.util.UUID.nameUUIDFromBytes("$jobId:${group.sourceKey}".toByteArray(Charsets.UTF_8)).toString()
            val keep=saved!=null && saved.payloadEdited && !changed && saved.status!="PENDING"
            var printing=if(keep)saved!!.scryfallId else group.printing
            var foil=if(keep)saved!!.isFoil else group.foil
            var condition=if(keep)saved!!.condition else group.condition
            var language=if(keep)saved!!.language else group.language
            val collision=variantCollision(jobId,id,printing,foil,condition,language) || (keep && transferSourceKey(printing,foil,condition,language)!=group.sourceKey && selectedSourceVariant(jobId,printing,foil,condition,language))
            if(collision) { printing=group.printing; foil=group.foil; condition=group.condition; language=group.language }
            val needsDecision=changed || saved?.status=="PENDING" || collision
            val quantity=if(keep && !collision)saved!!.quantity else copies
            val error=if(needsDecision)"SOURCE_MEMBERSHIP_CHANGED" else if(quantity>Int.MAX_VALUE.toLong())"QUANTITY_OVERFLOW" else null
            val entry=CollectionImportEntryEntity(id,jobId,generation,printing,foil,condition,language,quantity,destination=saved?.destination ?: "NONE",entryVersion=saved?.entryVersion ?: 0L,excluded=saved?.excluded ?: false,error=error,sourceKey=group.sourceKey,sourceSignature=signature,sourceQuantity=copies,payloadEdited=keep && !collision)
            upsertEntries(listOf(entry))
            upsertProvenance(contributions.map { CollectionImportProvenanceEntity(jobId,id,it.fileId,generation,it.records,it.copies) })
            for(part in contributions) linkEntryRows(jobId,part.fileId,id,group.printing,group.foil,group.condition,group.language)
            if(saved!=null) updateReviewDecision(saved.copy(generation=generation,status=if(needsDecision)"PENDING" else "RESTORED",reason=if(collision)"VARIANT_COLLISION" else if(needsDecision)"SOURCE_MEMBERSHIP_CHANGED" else "REBUILD"))
        }
        if(groups.isNotEmpty()) {
            job=job.copy(workCursor="review:${groups.last().sourceKey}",updatedAt=now)
            updateJob(job)
            return false
        }
        markRemovedReviewEdits(jobId)
        val fingerprint=java.security.MessageDigest.getInstance("SHA-256").digest(transferFingerprintInput(selected.map { it.sha256!! }).toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        return publishReview(jobId,ownerKey,generation,fingerprint,now)
    }

    /** Invalidated source edits require an explicit choice; disappearing sources cannot be restored implicitly. */
    @Transaction
    open suspend fun decideReviewEdit(jobId: String, ownerKey: String, entryId: String, generation: Long, choice: TransferReviewDecision): Boolean {
        val job=getJob(jobId,ownerKey) ?: return false
        val decision=reviewDecision(jobId,ownerKey,entryId) ?: return false
        if(job.generation!=generation || decision.generation!=generation || decision.status!="PENDING" || job.phase !in setOf("REVIEW_READY","REVIEW_REQUIRED")) return false
        val entry=reviewEntry(jobId,ownerKey,entryId)
        if(job.intentRevision==Long.MAX_VALUE || job.payloadVersion==Long.MAX_VALUE || entry?.entryVersion==Long.MAX_VALUE) return false
        if(entry==null) {
            if(choice!=TransferReviewDecision.DISMISS_REMOVED) return false
            updateReviewDecision(decision.copy(status="DISMISSED"))
            updateJob(job.copy(intentRevision=job.intentRevision+1L,payloadVersion=job.payloadVersion+1L,acceptedCopies=entryCounts(jobId,job.generation).acceptedCopies,confirmedGeneration=null,confirmedPayloadVersion=null,acceptExclusions=false)); return true
        }
        if(entry.activeActionId!=null || entry.appliedQuantity!=0L || choice==TransferReviewDecision.DISMISS_REMOVED) return false
        if(choice==TransferReviewDecision.KEEP_EDIT) {
            if(variantCollision(jobId,entryId,decision.scryfallId,decision.isFoil,decision.condition,decision.language) || decision.quantity !in 1L..Int.MAX_VALUE.toLong()) return false
            updateEntry(entry.copy(scryfallId=decision.scryfallId,isFoil=decision.isFoil,condition=decision.condition,language=decision.language,quantity=decision.quantity,destination=decision.destination,excluded=decision.excluded,payloadEdited=true,error=null,entryVersion=entry.entryVersion+1L))
        } else updateEntry(entry.copy(payloadEdited=false,error=if(entry.sourceQuantity>Int.MAX_VALUE.toLong())"QUANTITY_OVERFLOW" else null,entryVersion=entry.entryVersion+1L))
        invalidateEntryActions(jobId,entryId)
        updateReviewDecision(decision.copy(status="DECIDED"))
        updateJob(job.copy(intentRevision=job.intentRevision+1L,payloadVersion=job.payloadVersion+1L,acceptedCopies=entryCounts(jobId,job.generation).acceptedCopies,confirmedGeneration=null,confirmedPayloadVersion=null,acceptExclusions=false))
        return true
    }
    @Query("SELECT x.* FROM collection_transfer_action_entries x JOIN collection_transfer_actions a ON a.id=x.action_id JOIN collection_transfer_jobs j ON j.id=a.job_id WHERE x.action_id=:actionId AND a.owner_key=:ownerKey AND j.owner_key=:ownerKey AND a.destination='COLLECTION' AND a.phase='FROZEN' AND x.state='PENDING' AND x.completed_quantity=0 ORDER BY x.entry_id LIMIT 500")
    abstract suspend fun pendingCollectionActionPage(actionId: String, ownerKey: String): List<CollectionTransferActionEntryEntity>
    @Query("SELECT c.* FROM user_card_collection c JOIN collection_transfer_guest_rows g ON g.row_id=c.id WHERE g.owner_key=:ownerKey AND c.user_id IS NULL AND c.scryfall_id=:printing AND c.is_foil=:foil AND c.condition=:condition AND c.language=:language ORDER BY c.id LIMIT 1")
    abstract suspend fun verifiedGuestCollectionRow(ownerKey: String, printing: String, foil: Boolean, condition: String, language: String): UserCardCollectionEntity?
    @Insert(onConflict=OnConflictStrategy.IGNORE)
    abstract suspend fun recordGuestCollectionRow(provenance: CollectionTransferGuestRowEntity): Long
    @Query("UPDATE collection_import_entries SET applied_quantity=:quantity,state='APPLIED' WHERE id=:entryId AND job_id=:jobId AND active_action_id=:actionId AND destination='COLLECTION' AND state='PENDING' AND applied_quantity=0 AND EXISTS(SELECT 1 FROM collection_transfer_jobs WHERE id=:jobId AND owner_key=:ownerKey)")
    abstract suspend fun markCollectionEntryApplied(jobId: String, ownerKey: String, actionId: String, entryId: String, quantity: Long): Int
    @Query("UPDATE collection_transfer_action_entries SET state='COMPLETED',completed_quantity=quantity WHERE action_id=:actionId AND entry_id=:entryId AND state='PENDING' AND completed_quantity=0 AND EXISTS(SELECT 1 FROM collection_transfer_actions WHERE id=:actionId AND owner_key=:ownerKey AND destination='COLLECTION' AND phase='FROZEN')")
    abstract suspend fun markCollectionActionEntryApplied(actionId: String, ownerKey: String, entryId: String): Int
    @Query("UPDATE collection_import_provenance SET participated=1 WHERE job_id=:jobId AND entry_id=:entryId AND EXISTS(SELECT 1 FROM collection_transfer_jobs WHERE id=:jobId AND owner_key=:ownerKey)")
    protected abstract suspend fun markAppliedProvenance(jobId: String, ownerKey: String, entryId: String)
    @Upsert
    protected abstract suspend fun upsertFileHistory(history: List<CollectionTransferFileHistoryEntity>)
    @Query("SELECT COUNT(*) AS entries,COALESCE(SUM(applied_quantity),0) AS copies FROM collection_import_entries WHERE job_id=:jobId AND destination='COLLECTION' AND applied_quantity>0")
    protected abstract suspend fun collectionAppliedTotals(jobId: String): TransferCollectionAppliedTotals
    @Query("SELECT COUNT(*) FROM collection_transfer_action_entries WHERE action_id=:actionId AND state='PENDING'")
    protected abstract suspend fun pendingActionCount(actionId: String): Long
    @Query("SELECT COUNT(*) FROM collection_import_entries WHERE job_id=:jobId AND state='WISHLIST_APPLIED'")
    protected abstract suspend fun retainedWishlistCount(jobId: String): Long
    @Query("UPDATE collection_transfer_actions SET phase='COMPLETED' WHERE id=:actionId AND owner_key=:ownerKey AND destination='COLLECTION' AND phase='FROZEN'")
    protected abstract suspend fun finishCollectionAction(actionId: String, ownerKey: String)

    /** Participation reports every contributing file without allocating edited copies among sources. */
    open suspend fun recordAppliedParticipation(jobId: String, ownerKey: String, entryId: String, now: Long) {
        val entry=reviewEntry(jobId,ownerKey,entryId) ?: error("Missing applied entry")
        require(entry.state=="APPLIED" && entry.appliedQuantity>0L && entry.destination=="COLLECTION")
        val sources=provenance(jobId,ownerKey,entryId)
        require(sources.isNotEmpty())
        val history=sources.map { source ->
            val file=getFile(jobId,ownerKey,source.fileId) ?: error("Missing source")
            require(file.selected && !file.retired && file.phase=="REVIEW_READY" && file.sha256?.matches(Regex("[0-9a-f]{64}"))==true)
            CollectionTransferFileHistoryEntity(ownerKey,file.sha256!!,jobId,now)
        }
        markAppliedProvenance(jobId,ownerKey,entryId)
        upsertFileHistory(history)
    }

    /** Caller holds the collection transaction; wishlist completions never enter these counters. */
    open suspend fun completeCollectionBatch(jobId: String, ownerKey: String, actionId: String, now: Long) {
        val job=getJob(jobId,ownerKey) ?: error("Missing job")
        val totals=collectionAppliedTotals(jobId)
        val counts=entryCounts(jobId,job.generation)
        val files=currentFiles(jobId,ownerKey)
        val losses=job.invalidRecords>0L || job.unresolvedRecords>0L || files.any { !it.selected } || counts.excludedEntries>0L
        val complete=counts.pendingEntries==0L && retainedWishlistCount(jobId)==0L && (job.acceptExclusions || !losses)
        if(pendingActionCount(actionId)==0L) finishCollectionAction(actionId,ownerKey)
        updateJob(job.copy(appliedEntries=totals.entries,appliedCopies=totals.copies,acceptedCopies=counts.acceptedCopies,collectionEventPending=true,updatedAt=now,completedAt=if(complete)now else null,phase=if(complete) { if(losses)"COMPLETED_WITH_EXCLUSIONS" else "COMPLETED" } else if(counts.invalidEntries>0L || pendingReviewDecisions(jobId)>0L)"REVIEW_REQUIRED" else "REVIEW_READY",error=null))
    }

    @Query("UPDATE collection_import_entries SET active_action_id=NULL WHERE job_id=:jobId AND active_action_id=:actionId AND applied_quantity=0 AND state='PENDING'")
    protected abstract suspend fun unlockPendingCollectionEntries(jobId: String, actionId: String)
    @Query("UPDATE collection_import_entries SET error='QUANTITY_OVERFLOW' WHERE job_id=:jobId AND id=:entryId AND applied_quantity=0 AND state='PENDING'")
    protected abstract suspend fun markPendingOverflow(jobId: String, entryId: String)
    @Query("UPDATE collection_transfer_actions SET phase='REVIEW_REQUIRED' WHERE id=:actionId AND owner_key=:ownerKey AND phase='FROZEN'")
    protected abstract suspend fun requireCollectionActionReview(actionId: String, ownerKey: String)

    /** An overflowing batch rolls back first; only its still-pending command needs new confirmation. */
    @Transaction
    open suspend fun requireCollectionReview(jobId: String, ownerKey: String, actionId: String, entryId: String): Boolean {
        val job=getJob(jobId,ownerKey) ?: return false
        val action=action(actionId,ownerKey) ?: return false
        if(action.jobId!=jobId || action.phase!="FROZEN" || action.destination!="COLLECTION" || job.payloadVersion==Long.MAX_VALUE) return false
        unlockPendingCollectionEntries(jobId,actionId)
        markPendingOverflow(jobId,entryId)
        requireCollectionActionReview(actionId,ownerKey)
        updateJob(job.copy(phase="REVIEW_REQUIRED",error="QUANTITY_OVERFLOW",payloadVersion=job.payloadVersion+1L,confirmedGeneration=null,confirmedPayloadVersion=null))
        return true
    }

    /** Owner pauses retain both applied markers and all pending command snapshots. */
    @Transaction
    open suspend fun pauseCollectionOwner(jobId: String, ownerKey: String) {
        val job=getJob(jobId,ownerKey) ?: return
        if(job.phase !in setOf("COMPLETED","COMPLETED_WITH_EXCLUSIONS","DISCARDED","REJECTED","PAUSED_OWNER","PAUSED_BY_USER","FAILED_RETRYABLE")) updateJob(job.copy(previousPhase=job.phase,phase="PAUSED_OWNER"))
    }
    @Transaction
    open suspend fun resumeCollectionOwner(jobId: String, ownerKey: String) {
        val job=getJob(jobId,ownerKey) ?: return
        if(job.phase=="PAUSED_OWNER") updateJob(job.copy(phase=job.previousPhase))
    }
    @Query("UPDATE collection_transfer_jobs SET collection_event_pending=0 WHERE id=:jobId AND owner_key=:ownerKey AND applied_entries=:appliedEntries AND collection_event_pending=1")
    abstract suspend fun clearCollectionEvent(jobId: String, ownerKey: String, appliedEntries: Long): Int
    @Query("SELECT * FROM collection_transfer_jobs WHERE owner_key=:ownerKey AND collection_event_pending=1 AND id>:afterId ORDER BY id LIMIT 50")
    abstract suspend fun pendingCollectionEventPage(ownerKey: String, afterId: String): List<CollectionTransferJobEntity>
    @Query("UPDATE collection_transfer_jobs SET previous_phase=phase,phase='PAUSED_OWNER' WHERE owner_key=:ownerKey AND phase NOT IN ('COMPLETED','COMPLETED_WITH_EXCLUSIONS','DISCARDED','REJECTED','PAUSED_OWNER','PAUSED_BY_USER','FAILED_RETRYABLE')")
    abstract suspend fun pauseOwnerJobs(ownerKey: String)

    /** User pauses retain their checkpoint even when another account becomes available. */
    @Transaction
    open suspend fun pauseTransfer(jobId: String, ownerKey: String, now: Long): Boolean {
        val job=getJob(jobId,ownerKey) ?: return false
        if(job.phase=="PAUSED_BY_USER")return true
        if(job.phase in setOf("COMPLETED","COMPLETED_WITH_EXCLUSIONS","DISCARDED","REJECTED"))return false
        val checkpoint=if(job.phase=="PAUSED_OWNER")job.previousPhase else job.phase
        updateJob(job.copy(phase="PAUSED_BY_USER",previousPhase=checkpoint,updatedAt=now)); return true
    }
    @Transaction
    open suspend fun resumeTransfer(jobId: String, ownerKey: String, now: Long): Boolean {
        val job=getJob(jobId,ownerKey) ?: return false
        if(job.phase !in setOf("PAUSED_BY_USER","PAUSED_OWNER","FAILED_RETRYABLE","WAITING_NETWORK"))return false
        val checkpoint=if(job.filesFrozen)job.previousPhase.takeIf { it in setOf("REVIEW_READY","REVIEW_REQUIRED") } ?: "REVIEW_READY" else if(job.error=="NETWORK_RETRYABLE" || job.phase=="WAITING_NETWORK")"RESOLVING" else job.previousPhase
        if(checkpoint in setOf("PAUSED_BY_USER","PAUSED_OWNER","DISCARDED","REJECTED","COMPLETED","COMPLETED_WITH_EXCLUSIONS"))return false
        updateJob(job.copy(phase=checkpoint,nextAttemptAt=0L,networkFailures=0,error=job.error.takeUnless { it=="NETWORK_RETRYABLE" },updatedAt=now)); return true
    }
    @Query("UPDATE collection_import_entries SET state='DISCARDED',excluded=1,active_action_id=NULL WHERE job_id=:jobId AND state='PENDING' AND applied_quantity=0")
    protected abstract suspend fun discardPendingReviewEntries(jobId: String)
    @Query("UPDATE collection_transfer_actions SET phase='DISCARDED' WHERE job_id=:jobId AND phase!='COMPLETED'")
    protected abstract suspend fun discardPendingCommands(jobId: String)
    @Transaction
    open suspend fun discardTransferPending(jobId: String, ownerKey: String, generation: Long, payloadVersion: Long, now: Long): Boolean {
        val job=getJob(jobId,ownerKey) ?: return false
        if(job.generation!=generation || job.payloadVersion!=payloadVersion || job.phase in setOf("DISCARDED","REJECTED") || payloadVersion==Long.MAX_VALUE)return false
        discardPendingReviewEntries(jobId); discardPendingCommands(jobId)
        updateJob(job.copy(phase="DISCARDED",payloadVersion=payloadVersion+1L,confirmedGeneration=null,confirmedPayloadVersion=null,acceptExclusions=false,acceptRepeats=false,updatedAt=now,completedAt=now)); return true
    }
    @Query("SELECT x.* FROM collection_transfer_action_entries x JOIN collection_transfer_actions a ON a.id=x.action_id JOIN collection_transfer_jobs j ON j.id=a.job_id WHERE x.action_id=:actionId AND a.owner_key=:ownerKey AND j.owner_key=:ownerKey AND a.destination='WISHLIST' AND a.phase='FROZEN' AND x.state='PENDING' AND x.completed_quantity=0 ORDER BY x.entry_id LIMIT 500")
    abstract suspend fun pendingWishlistActionPage(actionId: String, ownerKey: String): List<CollectionTransferActionEntryEntity>

    @Query("UPDATE collection_import_entries SET state='WISHLIST_APPLIED' WHERE id=:entryId AND job_id=:jobId AND active_action_id=:actionId AND destination='WISHLIST' AND state='PENDING' AND applied_quantity=0 AND EXISTS(SELECT 1 FROM collection_transfer_jobs WHERE id=:jobId AND owner_key=:ownerKey)")
    abstract suspend fun markWishlistEntryApplied(jobId: String, ownerKey: String, actionId: String, entryId: String): Int
    @Query("UPDATE collection_transfer_action_entries SET state='COMPLETED',completed_quantity=quantity WHERE action_id=:actionId AND entry_id=:entryId AND state='PENDING' AND completed_quantity=0 AND EXISTS(SELECT 1 FROM collection_transfer_actions WHERE id=:actionId AND owner_key=:ownerKey AND destination='WISHLIST' AND phase='FROZEN')")
    abstract suspend fun markWishlistActionEntryApplied(actionId: String, ownerKey: String, entryId: String): Int
    @Query("UPDATE collection_import_provenance SET wishlist_participated=1 WHERE job_id=:jobId AND entry_id=:entryId AND EXISTS(SELECT 1 FROM collection_transfer_jobs WHERE id=:jobId AND owner_key=:ownerKey)")
    protected abstract suspend fun markWishlistProvenance(jobId: String, ownerKey: String, entryId: String)
    open suspend fun recordWishlistParticipation(jobId: String, ownerKey: String, entryId: String, now: Long) {
        val entry=reviewEntry(jobId,ownerKey,entryId) ?: error("Missing wishlist entry")
        require(entry.state=="WISHLIST_APPLIED" && entry.destination=="WISHLIST" && entry.appliedQuantity==0L)
        val sources=provenance(jobId,ownerKey,entryId)
        require(sources.isNotEmpty())
        val history=sources.map { source ->
            val file=getFile(jobId,ownerKey,source.fileId) ?: error("Missing source")
            require(file.selected && !file.retired && file.phase=="REVIEW_READY" && file.sha256?.matches(Regex("[0-9a-f]{64}"))==true)
            CollectionTransferFileHistoryEntity(ownerKey,file.sha256!!,jobId,now)
        }
        markWishlistProvenance(jobId,ownerKey,entryId)
        upsertFileHistory(history)
    }
    @Query("UPDATE collection_transfer_actions SET phase='COMPLETED' WHERE id=:actionId AND owner_key=:ownerKey AND destination='WISHLIST' AND phase='FROZEN'")
    protected abstract suspend fun finishWishlistAction(actionId: String, ownerKey: String)

    /** Local wishlist outcomes change pending state, never collection quantities/events. */
    open suspend fun completeWishlistBatch(jobId: String, ownerKey: String, actionId: String, now: Long) {
        val job=getJob(jobId,ownerKey) ?: error("Missing job")
        if(pendingActionCount(actionId)==0L)finishWishlistAction(actionId,ownerKey)
        val counts=entryCounts(jobId,job.generation)
        val losses=job.invalidRecords>0L || job.unresolvedRecords>0L || currentFiles(jobId,ownerKey).any { !it.selected } || counts.excludedEntries>0L
        val complete=counts.pendingEntries==0L && retainedWishlistCount(jobId)==0L && (job.acceptExclusions || !losses)
        updateJob(job.copy(updatedAt=now,completedAt=if(complete)now else null,phase=if(complete) { if(losses)"COMPLETED_WITH_EXCLUSIONS" else "COMPLETED" } else if(counts.invalidEntries>0L || pendingReviewDecisions(jobId)>0L)"REVIEW_REQUIRED" else "REVIEW_READY",error=null))
    }

    @Query("UPDATE collection_transfer_actions SET phase='REVIEW_REQUIRED' WHERE id=:actionId AND owner_key=:ownerKey AND destination='WISHLIST' AND phase='FROZEN'")
    protected abstract suspend fun requireWishlistActionReview(actionId: String, ownerKey: String)
    @Transaction
    open suspend fun requireWishlistReview(jobId: String, ownerKey: String, actionId: String, entryId: String): Boolean {
        val job=getJob(jobId,ownerKey) ?: return false
        val action=action(actionId,ownerKey) ?: return false
        if(action.jobId!=jobId || action.phase!="FROZEN" || action.destination!="WISHLIST" || job.payloadVersion==Long.MAX_VALUE)return false
        unlockPendingCollectionEntries(jobId,actionId)
        markPendingOverflow(jobId,entryId)
        requireWishlistActionReview(actionId,ownerKey)
        updateJob(job.copy(phase="REVIEW_REQUIRED",error="QUANTITY_OVERFLOW",payloadVersion=job.payloadVersion+1L,confirmedGeneration=null,confirmedPayloadVersion=null))
        return true
    }

    @Query("SELECT * FROM collection_transfer_wishlist_dirty WHERE wishlist_id=:id")
    abstract suspend fun wishlistDirty(id: String): CollectionTransferWishlistDirtyEntity?
    @Insert(onConflict=OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertWishlistDirty(row: CollectionTransferWishlistDirtyEntity)
    @Update
    protected abstract suspend fun updateWishlistDirty(row: CollectionTransferWishlistDirtyEntity)
    @Query("SELECT * FROM local_wishlists WHERE id=:id AND owner_user_id=:userId")
    protected abstract suspend fun wishlistOwnedRow(id: String, userId: String): LocalWishlistEntity?
    @Transaction
    open suspend fun stageWishlistDirty(id: String, ownerKey: String) {
        val previous=wishlistDirty(id)
        val user=if(ownerKey.startsWith("account:"))ownerKey.removePrefix("account:") else "local_guest"
        val wanted=wishlistOwnedRow(id,user) ?: error("Missing wanted row")
        require(previous==null || previous.ownerKey==ownerKey && !previous.deleted)
        require(user!="local_guest" || previous!=null && previous.ownerKey==ownerKey)
        val row=CollectionTransferWishlistDirtyEntity(id,ownerKey,previous?.revision ?: 1L,false,true,0L,wanted.scryfallId,wanted.quantity,wanted.isFoil,wanted.condition,wanted.language,wanted.matchAnyVariant,wanted.createdAt)
        if(previous==null)insertWishlistDirty(row)
        else if(previous.copy(pending=true,nextRepairAt=0L)!=row || !previous.pending) { require(previous.revision<Long.MAX_VALUE); updateWishlistDirty(row.copy(revision=previous.revision+1L)) }
    }
    @Query("UPDATE trade_wishlist_cleanup SET target_quantity=:quantity WHERE user_id=:userId AND wishlist_id=:id")
    abstract suspend fun advancePendingWishlistCleanup(userId: String, id: String, quantity: Int)
    @Query("SELECT wishlist_id AS id,revision,printing,quantity,foil,condition,language,match_any_variant AS matchAnyVariant,created_at AS createdAt,deleted FROM collection_transfer_wishlist_dirty WHERE owner_key=:ownerKey AND owner_key='account:' || :userId AND pending=1 AND next_repair_at<=:now ORDER BY wishlist_id LIMIT 200")
    abstract suspend fun wishlistDeliveryPage(ownerKey: String, userId: String, now: Long=Long.MAX_VALUE): List<TransferWishlistDelivery>
    @Query("SELECT EXISTS(SELECT 1 FROM collection_transfer_wishlist_dirty WHERE owner_key=:ownerKey AND pending=1)")
    abstract suspend fun hasWishlistPending(ownerKey: String): Boolean
    @Query("UPDATE local_wishlists SET synced=1 WHERE id=:id AND owner_user_id=:userId AND quantity=:quantity AND scryfall_id=:printing AND match_any_variant=:matchAnyVariant AND is_foil IS :foil AND condition IS :condition AND language IS :language AND created_at=:createdAt AND EXISTS(SELECT 1 FROM collection_transfer_wishlist_dirty WHERE wishlist_id=:id AND owner_key=:ownerKey AND revision=:revision)")
    abstract suspend fun acknowledgeWishlistRow(id: String, userId: String, ownerKey: String, revision: Long, printing: String, quantity: Int, foil: Boolean?, condition: String?, language: String?, matchAnyVariant: Boolean, createdAt: Long): Int
    @Query("UPDATE collection_transfer_wishlist_dirty SET pending=0,next_repair_at=9223372036854775807 WHERE wishlist_id=:id AND owner_key=:ownerKey AND revision=:revision")
    abstract suspend fun clearWishlistDirty(id: String, ownerKey: String, revision: Long)
    @Query("UPDATE collection_transfer_wishlist_dirty SET pending=0,next_repair_at=9223372036854775807 WHERE wishlist_id=:id AND owner_key=:ownerKey AND revision=:revision AND deleted=1 AND NOT EXISTS(SELECT 1 FROM local_wishlists WHERE id=:id)")
    abstract suspend fun acknowledgeWishlistDeletion(id: String, ownerKey: String, revision: Long): Int
    @Query("UPDATE collection_transfer_wishlist_dirty SET next_repair_at=:next WHERE wishlist_id=:id AND owner_key=:ownerKey AND revision=:revision AND pending=1")
    abstract suspend fun deferWishlistDelivery(id: String, ownerKey: String, revision: Long, next: Long)
    @Query("UPDATE collection_transfer_wishlist_dirty SET pending=1,next_repair_at=:next,revision=revision+1 WHERE wishlist_id=:id AND owner_key=:ownerKey AND revision!=:oldRevision AND pending=0 AND revision<9223372036854775807")
    abstract suspend fun reactivateStaleWishlistDelivery(id: String, ownerKey: String, oldRevision: Long, next: Long)
    /** Explicit follow-up retains completed action snapshots without duplicating source aggregates. */
    @Query("SELECT COUNT(*) FROM collection_import_entries e JOIN collection_transfer_actions a ON a.id=e.active_action_id AND a.owner_key=:ownerKey WHERE e.job_id=:jobId AND e.generation=:generation AND e.state='WISHLIST_APPLIED' AND e.destination='WISHLIST' AND e.applied_quantity=0 AND e.entry_version<9223372036854775807 AND a.destination='WISHLIST' AND a.phase='COMPLETED'")
    protected abstract suspend fun retainedWishlistCount(jobId: String,ownerKey: String,generation: Long): Long
    @Query("UPDATE collection_import_entries SET state='PENDING',destination='NONE',active_action_id=NULL,entry_version=entry_version+1 WHERE job_id=:jobId AND generation=:generation AND state='WISHLIST_APPLIED' AND destination='WISHLIST' AND applied_quantity=0 AND entry_version<9223372036854775807 AND active_action_id IN (SELECT id FROM collection_transfer_actions WHERE owner_key=:ownerKey AND destination='WISHLIST' AND phase='COMPLETED')")
    protected abstract suspend fun reopenRetainedWishlist(jobId: String,ownerKey: String,generation: Long)
    @Transaction
    open suspend fun reopenWishlistSelection(jobId: String,ownerKey: String,generation: Long,payloadVersion: Long): Boolean {
        val job=getJob(jobId,ownerKey) ?: return false
        if(job.generation!=generation || job.payloadVersion!=payloadVersion || job.payloadVersion==Long.MAX_VALUE || job.intentRevision==Long.MAX_VALUE || job.phase !in setOf("REVIEW_READY","REVIEW_REQUIRED"))return false
        if(retainedWishlistCount(jobId,ownerKey,generation)==0L)return true
        reopenRetainedWishlist(jobId,ownerKey,generation)
        updateJob(job.copy(phase="REVIEW_READY",completedAt=null,payloadVersion=payloadVersion+1L,intentRevision=job.intentRevision+1L,confirmedGeneration=null,confirmedPayloadVersion=null))
        return true
    }
    @Transaction
    open suspend fun reopenWishlistEntry(jobId: String, ownerKey: String, entryId: String, version: Long, payloadVersion: Long): Boolean {
        val job=getJob(jobId,ownerKey) ?: return false
        val entry=reviewEntry(jobId,ownerKey,entryId) ?: return false
        val previous=entry.activeActionId?.let { action(it,ownerKey) } ?: return false
        if(job.payloadVersion!=payloadVersion || job.payloadVersion==Long.MAX_VALUE || job.intentRevision==Long.MAX_VALUE || entry.entryVersion!=version || version==Long.MAX_VALUE || entry.state!="WISHLIST_APPLIED" || entry.destination!="WISHLIST" || entry.appliedQuantity!=0L || previous.destination!="WISHLIST" || previous.phase!="COMPLETED")return false
        updateEntry(entry.copy(state="PENDING",activeActionId=null,destination="NONE",entryVersion=version+1L))
        updateJob(job.copy(phase="REVIEW_READY",completedAt=null,payloadVersion=payloadVersion+1L,intentRevision=job.intentRevision+1L,confirmedGeneration=null,confirmedPayloadVersion=null))
        return true
    }
    private fun requireOwner(ownerKey: String) {
        require((ownerKey.startsWith("account:") && ownerKey.length > 8) || (ownerKey.startsWith("guest:") && ownerKey.length > 6))
    }

    private inline fun withinBudget(files: List<CollectionTransferFileEntity>, limit: Long, count: (CollectionTransferFileEntity) -> Long): Boolean {
        var remaining = limit
        for (file in files) {
            val value = count(file)
            if (value !in 0..remaining) return false
            remaining -= value
        }
        return true
    }
}






