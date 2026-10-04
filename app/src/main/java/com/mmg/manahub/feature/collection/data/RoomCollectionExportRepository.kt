package com.mmg.manahub.feature.collection.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.content.FileProvider
import androidx.room.withTransaction
import com.mmg.manahub.core.data.local.MtgDatabase
import com.mmg.manahub.core.data.local.entity.*
import com.mmg.manahub.core.domain.collection.transfer.*
import com.mmg.manahub.core.domain.repository.CardLookupIdentifier
import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.model.AdvancedSearchQuery
import com.mmg.manahub.core.model.CollectionGroupingMode
import com.mmg.manahub.core.model.CollectionSource
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.io.*
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** The job owns its selection and private output across navigation, destination failures and restarts. */
class RoomCollectionExportRepository(
    private val context: Context,
    private val database: MtgDatabase,
    private val selection: RoomCollectionSelectionRepository,
    private val sessions: TransferSessionGate,
    private val observer: TransferAuthSessionObserver,
    private val hydration: TransferResolutionGateway,
    private val crashReporter: CrashReporter,
    private val root: File=File(context.filesDir,"collection-transfers/exports"),
    private val cache: File=File(context.cacheDir,"exports"),
    private val now: ()->Long=System::currentTimeMillis,
    private val openOutput: (Uri)->OutputStream={ context.contentResolver.openOutputStream(it,"wt") ?: error("Destination unavailable") },
    private val deleteOutput: (Uri)->Unit={ DocumentsContract.deleteDocument(context.contentResolver,it);Unit },
) : CollectionExportRepository {
    private val mutex=Mutex()
    private val blocked=mutableSetOf<String>()
    private val dao get()=database.collectionExportDao()
    private val rows get()=database.collectionSelectionDao()
    private fun telemetry(event: String,value: CollectionExportEntity?=null,format: String?=value?.format,error: Throwable?=null) {
        exportTelemetry(crashReporter,event,format,value?.rows ?: 0L,error?.let { transferFailureCategory(it,TransferFailureCategory.STORAGE) } ?: TransferFailureCategory.UNKNOWN,error!=null && !isExpectedTransferInterruption(error))
    }
    private fun guard(owner: TransferOwner,captured: TransferSession) {
        if((captured as? TransferSession.Available)?.owner!=owner || sessions.currentSession!=captured || !observer.matchesObserved(owner))throw TransferReadException(TransferError.OWNER_CHANGED)
    }
    private fun directory(id: String): File { TransferJobId(id);return File(root,id).apply { check(isDirectory || mkdirs()) } }
    private fun output(job: CollectionExportEntity)=File(directory(job.id),"${job.id}.${CollectionFileFormat.valueOf(job.format).fileExtension}")
    private fun omissions(job: CollectionExportEntity)=File(directory(job.id),"${job.id}.omissions.csv")
    private suspend fun job(owner: TransferOwner,id: String): CollectionExportEntity {
        TransferJobId(id);check(blocked.isEmpty())
        return dao.get(id,owner.storageKey()) ?: throw TransferReadException(TransferError.NOT_FOUND)
    }
    private suspend fun update(owner: TransferOwner,captured: TransferSession,value: CollectionExportEntity) {
        sessions.withOwner(owner) { _,checkSession -> database.withTransaction { checkSession();guard(owner,captured);dao.update(value);guard(owner,captured) } } ?: throw TransferReadException(TransferError.OWNER_CHANGED)
    }
    private fun CollectionExportEntity.summary()=DurableCollectionExport(id,CollectionFileFormat.valueOf(format),CollectionExportTarget.valueOf(target),CollectionExportPhase.valueOf(phase),rows,copies,omittedRows,omittedCopies,bytes,partialDestination,availableOnly)
    override suspend fun create(owner: TransferOwner,query: CollectionSelectionQuery,format: CollectionFileFormat,target: CollectionExportTarget): String=mutex.withLock {
        val captured=sessions.currentSession;guard(owner,captured)
        val id=UUID.randomUUID().toString()
        try { selection.freeze(owner,query,id) { guard(owner,captured);dao.insert(CollectionExportEntity(id,owner.storageKey(),format.name,target.name,createdAt=now())) } }
        catch(error: Throwable) { telemetry(if(isExpectedTransferInterruption(error))"snapshot_cancelled" else "snapshot_failed",format=format.name,error=error);throw error }
        guard(owner,captured);telemetry("snapshot_frozen",format=format.name);id
    }
    override fun observe(owner: TransferOwner,id: String): Flow<DurableCollectionExport?> =dao.observe(id,owner.storageKey()).map { value ->
        if(!observer.matchesObserved(owner) || (sessions.currentSession as? TransferSession.Available)?.owner!=owner)null else value?.summary()
    }
    override suspend fun latest(owner: TransferOwner): String? {
        val captured=sessions.currentSession;guard(owner,captured)
        return dao.latest(owner.storageKey()).also { guard(owner,captured) }
    }
    private suspend fun query(owner: TransferOwner,id: String): CollectionSelectionQuery {
        val q=rows.query(id,owner.storageKey()) ?: throw TransferReadException(TransferError.NOT_FOUND)
        return CollectionSelectionQuery(CollectionSource.valueOf(q.source),q.search,q.advancedQuery.takeIf(String::isNotEmpty)?.let { Json.decodeFromString<AdvancedSearchQuery>(it) },CollectionSelectionSort.valueOf(q.sort),q.ascending,CollectionGroupingMode.valueOf(q.grouping))
    }
    private fun metadataMissing(row: CollectionSelectionRowEntity)=row.card.staleReason=="pending_hydration" || row.card.name.isBlank() || row.card.setCode.isBlank() || row.card.collectorNumber.isBlank()
    private fun represented(row: CollectionSelectionRowEntity)=!metadataMissing(row) && row.rawFoil!=null && row.rawCondition!=null && row.rawLanguage!=null && row.rawCondition in com.mmg.manahub.core.util.CardConstants.conditions.map { it.first } && row.rawLanguage in com.mmg.manahub.core.util.CardConstants.languageNames.keys && row.quantity in 1L..Int.MAX_VALUE.toLong()
    private fun omitted(row: CollectionSelectionRowEntity)=metadataMissing(row) || (row.matched && !represented(row))
    override suspend fun prepare(owner: TransferOwner,id: String,availableOnly: Boolean): DurableCollectionExport=withContext(Dispatchers.IO) { mutex.withLock {
        val captured=sessions.currentSession;guard(owner,captured)
        var value=job(owner,id)
        if(value.metadataFrozen) {
            try {
            if(!verify(output(value),value.bytes,value.sha256,owner,captured))value=generate(owner,captured,value)
            else if(value.phase !in setOf("READY","SAVED","SHARED")) { value=value.copy(phase="READY",partialDestination=value.partialDestination || value.phase=="SAVING");update(owner,captured,value) }
            telemetry("snapshot_recovered",value);return@withLock value.summary()
            } catch(error: Throwable) { telemetry(if(isExpectedTransferInterruption(error))"preparation_cancelled" else "preparation_failed",value,error=error);throw error }
        }
        try {
            if(!(availableOnly && value.phase=="NEEDS_METADATA")) {
                value=value.copy(phase="HYDRATING",availableOnly=false);update(owner,captured,value)
                var after="";var remoteAvailable=true
                while(true) {
                    currentCoroutineContext().ensureActive();guard(owner,captured)
                    val page=rows.scan(id,after);if(page.isEmpty())break
                    val missing=page.filter(::metadataMissing)
                    if(missing.isNotEmpty()) {
                        val ids=missing.map { it.card.scryfallId }.distinct()
                        val currentCache=database.cardDao().getByIds(ids).associateBy { it.scryfallId }
                        val absent=ids.filter { key -> currentCache[key]?.let { it.staleReason!="pending_hydration" && it.name.isNotBlank() && it.setCode.isNotBlank() && it.collectorNumber.isNotBlank() } != true }
                        absent.chunked(75).forEach { batch ->
                            if(!remoteAvailable)return@forEach
                            currentCoroutineContext().ensureActive();guard(owner,captured)
                            try { if(hydration.lookup(batch.map { CardLookupIdentifier(scryfallId=it) }).failure!=null)remoteAvailable=false } catch(cancelled: CancellationException) { throw cancelled } catch(_: Exception) { remoteAvailable=false }
                            guard(owner,captured)
                        }
                        guard(owner,captured)
                        val hydrated=database.cardDao().getByIds(ids).filter { it.staleReason!="pending_hydration" && it.name.isNotBlank() && it.setCode.isNotBlank() && it.collectorNumber.isNotBlank() }.associateBy { it.scryfallId }
                        sessions.withOwner(owner) { _,checkSession -> database.withTransaction {
                            checkSession();guard(owner,captured)
                            rows.updateRows(missing.mapNotNull { row -> hydrated[row.card.scryfallId]?.let { card -> row.copy(card=card.copy(tags=row.card.tags,userTags=row.card.userTags,suggestedTags=row.card.suggestedTags),groupKey="${card.setCode}|${card.oracleId.ifBlank { card.name.ifBlank { card.scryfallId } }}") } })
                            guard(owner,captured)
                        } } ?: throw TransferReadException(TransferError.OWNER_CHANGED)
                    }
                    after=page.last().sourceId;yield()
                }
                selection.evaluate(owner,id,query(owner,id),discardOnFailure=false,requireMetadata=true,includeUiSummary=false)
            }
            var includedRows=0L;var copies=0L;var missingRows=0L;var missingCopies=0L;var after=""
            val report=omissions(value)
            FileOutputStream(report).use { file -> file.bufferedWriter(Charsets.UTF_8).use { writer ->
                writer.appendLine("Source row,Printing,Quantity,Foil,Condition,Language,Reason")
                while(true) {
                    currentCoroutineContext().ensureActive();guard(owner,captured)
                    val page=rows.scan(id,after);if(page.isEmpty())break
                    page.forEach { row ->
                        if(omitted(row)) {
                            missingRows=Math.addExact(missingRows,1L);missingCopies=Math.addExact(missingCopies,row.quantity)
                            writer.appendLine(CollectionExportFormatter.csvRecord(listOf(row.sourceId,row.card.scryfallId,row.quantity.toString(),row.rawFoil?.toString().orEmpty(),row.rawCondition.orEmpty(),row.rawLanguage.orEmpty(),if(metadataMissing(row))"Metadata unavailable; filtered membership unknown" else "Attributes or quantity not representable")))
                        } else if(row.matched) { includedRows=Math.addExact(includedRows,1L);copies=Math.addExact(copies,row.quantity) }
                    }
                    after=page.last().sourceId
                }
                writer.flush();file.fd.sync()
            } }
            value=value.copy(rows=includedRows,copies=copies,omittedRows=missingRows,omittedCopies=missingCopies,availableOnly=availableOnly || value.availableOnly,sha256=null,bytes=0L)
            if(missingRows>0L && !value.availableOnly) {
                value=value.copy(phase="NEEDS_METADATA");update(owner,captured,value);telemetry("metadata_decision",value);return@withLock value.summary()
            }
            if(value.availableOnly)telemetry("available_only",value)
            generate(owner,captured,value).summary()
        } catch(cancelled: CancellationException) { telemetry("preparation_cancelled",value);throw cancelled }
        catch(failure: Exception) {
            telemetry(if(isExpectedTransferInterruption(failure))"preparation_cancelled" else "preparation_failed",value,error=failure)
            if(observer.matchesObserved(owner) && sessions.currentSession==captured)update(owner,captured,job(owner,id).copy(phase="FAILED"))
            throw failure
        }
    } }
    private suspend fun generate(owner: TransferOwner,captured: TransferSession,initial: CollectionExportEntity): CollectionExportEntity {
        var value=initial.copy(phase="WRITING",metadataFrozen=true)
        if(!initial.metadataFrozen) sessions.withOwner(owner) { _,checkSession -> database.withTransaction {
            checkSession();guard(owner,captured)
            val sql=database.openHelper.writableDatabase
            sql.execSQL("CREATE TEMP TABLE IF NOT EXISTS collection_export_positions(position INTEGER PRIMARY KEY,source_id TEXT NOT NULL UNIQUE)")
            sql.execSQL("DELETE FROM collection_export_positions")
            sql.execSQL("INSERT INTO collection_export_positions(source_id) SELECT r.source_id FROM collection_selection_rows r JOIN collection_selection_groups g ON g.query_id=r.query_id AND g.group_key=r.group_key WHERE r.query_id=? AND r.matched=1 ORDER BY g.ordinal,r.source_id",arrayOf(value.id))
            sql.execSQL("UPDATE collection_selection_rows SET export_ordinal=COALESCE((SELECT position FROM collection_export_positions p WHERE p.source_id=collection_selection_rows.source_id),0) WHERE query_id=?",arrayOf(value.id))
            dao.update(value);guard(owner,captured)
        } } ?: throw TransferReadException(TransferError.OWNER_CHANGED)
        else update(owner,captured,value)
        val format=CollectionFileFormat.valueOf(value.format);val part=File(directory(value.id),"${value.id}.part")
        var count=0L;var copies=0L
        FileOutputStream(part).use { stream -> stream.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.append(CollectionExportFormatter.header(format,"ManaHub frozen export"))
            var ordinal=0L
            while(true) {
                currentCoroutineContext().ensureActive();guard(owner,captured)
                val page=dao.page(value.id,ordinal);if(page.isEmpty())break
                for(row in page)if(represented(row)) {
                    writer.append(CollectionExportFormatter.record(CollectionExportEntry(row.quantity.toInt(),row.card.name,row.card.setCode,row.card.setName,row.card.collectorNumber,row.card.scryfallId,row.card.rarity,requireNotNull(row.rawFoil),requireNotNull(row.rawCondition),requireNotNull(row.rawLanguage)),format))
                    count=Math.addExact(count,1L);copies=Math.addExact(copies,row.quantity)
                }
                ordinal=page.last().exportOrdinal
            }
            check(count==value.rows && copies==value.copies);writer.flush();stream.fd.sync()
        } }
        val bytes=part.length();val hash=hash(part,owner,captured)
        value=value.copy(bytes=bytes,sha256=hash);update(owner,captured,value)
        guard(owner,captured);check(part.renameTo(output(value)))
        value=value.copy(phase="READY");update(owner,captured,value)
        telemetry("ready",value)
        return value
    }
    private suspend fun hash(file: File,owner: TransferOwner,captured: TransferSession): String {
        val digest=MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream -> val buffer=ByteArray(64*1024);while(true) { currentCoroutineContext().ensureActive();guard(owner,captured);val n=stream.read(buffer);if(n<0)break;digest.update(buffer,0,n) } }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
    private suspend fun verify(file: File,bytes: Long,sha: String?,owner: TransferOwner,captured: TransferSession)=file.isFile && file.length()==bytes && sha!=null && hash(file,owner,captured)==sha
    override suspend fun save(owner: TransferOwner,id: String,location: String)=withContext(Dispatchers.IO) { mutex.withLock {
        val captured=sessions.currentSession;guard(owner,captured)
        var value=job(owner,id);check(value.phase in setOf("READY","SAVED","SHARED","SAVING"));check(verify(output(value),value.bytes,value.sha256,owner,captured))
        val uri=Uri.parse(location);require(uri.scheme=="content")
        runCatching { context.contentResolver.takePersistableUriPermission(uri,android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
        value=value.copy(phase="SAVING",partialDestination=true);update(owner,captured,value)
        telemetry("save_started",value)
        try {
            copyDestination(owner,captured,id,output(value),uri,value)
            value=value.copy(phase="SAVED",partialDestination=false);update(owner,captured,value)
            telemetry("save_closed",value)
        } catch(failure: Throwable) {
            telemetry(if(isExpectedTransferInterruption(failure))"save_cancelled" else "save_failed",value,error=failure)
            withContext(NonCancellable+Dispatchers.IO) {
                if(id !in blocked)runCatching { deleteOutput(uri) }
                if(observer.matchesObserved(owner) && sessions.currentSession==captured)update(owner,captured,value.copy(phase="READY",partialDestination=true))
            }
            throw failure
        }
    } }
    private suspend fun copyDestination(owner: TransferOwner,captured: TransferSession,id: String,source: File,uri: Uri,value: CollectionExportEntity) {
        val context=currentCoroutineContext();val opened=AtomicReference<OutputStream?>();val closing=AtomicReference<CompletableDeferred<Unit>?>()
        fun closeAsync(): CompletableDeferred<Unit> {
            closing.get()?.let { return it };val done=CompletableDeferred<Unit>()
            if(!closing.compareAndSet(null,done))return requireNotNull(closing.get())
            Thread({ try { opened.getAndSet(null)?.close();done.complete(Unit) } catch(_: Throwable) { done.completeExceptionally(IOException("Destination close failed")) } },"collection-export-close").apply { isDaemon=true }.start();return done
        }
        val finished=CompletableDeferred<Unit>();val started=java.util.concurrent.atomic.AtomicBoolean(false)
        val executor=Executors.newSingleThreadExecutor { runnable -> Thread(runnable,"collection-export-output").apply { isDaemon=true } }
        val watcher=CoroutineScope(context).launch(Dispatchers.IO) {
            combine(sessions.sessions,observer.identities) { session,_ -> session }.first { it!=captured || !observer.matchesObserved(owner) }
            closeAsync();context[Job]?.cancel(CancellationException("Export owner changed"))
        }
        try {
            suspendCancellableCoroutine<Unit> { continuation ->
                val task=executor.submit {
                    started.set(true)
                    try {
                        guard(owner,captured);context.ensureActive();val out=openOutput(uri);opened.set(out)
                        if(!continuation.isActive) { out.close();opened.compareAndSet(out,null);throw CancellationException() }
                        source.inputStream().use { input -> val buffer=ByteArray(64*1024);while(true) { context.ensureActive();guard(owner,captured);val n=input.read(buffer);if(n<0)break;out.write(buffer,0,n) } }
                        out.flush();guard(owner,captured);out.close();opened.compareAndSet(out,null);guard(owner,captured)
                        if(continuation.isActive)continuation.resume(Unit)
                    } catch(failure: Throwable) { if(continuation.isActive)continuation.resumeWithException(failure) }
                    finally { if(opened.get()!=null)closeAsync();finished.complete(Unit) }
                }
                continuation.invokeOnCancellation { task.cancel(true);closeAsync() }
            }
        } finally {
            watcher.cancel();executor.shutdownNow()
            withContext(NonCancellable+Dispatchers.IO) {
                val complete=withTimeoutOrNull(1000L) { runCatching { closeAsync().await();if(started.get())finished.await() }.isSuccess }
                if(complete!=true) { blocked+=id;telemetry("destination_close_unconfirmed",value,error=IOException("Destination close unconfirmed")) }
            }
        }
    }
    override suspend fun share(owner: TransferOwner,id: String): String=withContext(Dispatchers.IO) { mutex.withLock {
        val captured=sessions.currentSession;guard(owner,captured);val value=job(owner,id)
        try {
        check(value.phase in setOf("READY","SAVED","SHARED"));check(verify(output(value),value.bytes,value.sha256,owner,captured))
        check(cache.isDirectory || cache.mkdirs());check(cache.usableSpace>Math.addExact(value.bytes,1024L*1024L))
        cache.listFiles()?.forEach { old -> if(old.isDirectory && old.name!=id && runCatching { TransferJobId(old.name) }.isSuccess && old.listFiles()?.none { it.extension=="part" }==true && now()-old.lastModified()>24L*60L*60L*1000L)old.deleteRecursively() }
        val directory=File(cache,id).apply { check(isDirectory || mkdirs()) }
        val final=File(directory,"${value.id}.${CollectionFileFormat.valueOf(value.format).fileExtension}")
        if(!verify(final,value.bytes,value.sha256,owner,captured)) {
            val part=File(directory,"${value.id}.part")
            output(value).inputStream().use { input -> FileOutputStream(part).use { out -> val buffer=ByteArray(64*1024);while(true) { currentCoroutineContext().ensureActive();guard(owner,captured);val n=input.read(buffer);if(n<0)break;out.write(buffer,0,n) };out.fd.sync() } }
            guard(owner,captured);check(part.renameTo(final))
        }
        directory.setLastModified(now());guard(owner,captured)
        val uri=FileProvider.getUriForFile(context,"${context.packageName}.fileprovider",final).toString()
        update(owner,captured,value.copy(phase="SHARED"));guard(owner,captured);telemetry("share_attachment_ready",value);uri
        } catch(error: Throwable) { telemetry(if(isExpectedTransferInterruption(error))"share_cancelled" else "share_failed",value,error=error);throw error }
    } }
    override suspend fun writeOmissions(owner: TransferOwner,id: String,location: String)=withContext(Dispatchers.IO) { mutex.withLock {
        val captured=sessions.currentSession;guard(owner,captured);val value=job(owner,id)
        try {
            val uri=Uri.parse(location);require(uri.scheme=="content");check(omissions(value).isFile)
            copyDestination(owner,captured,id,omissions(value),uri,value);guard(owner,captured);telemetry("omissions_report_saved",value)
        } catch(error: Throwable) { telemetry(if(isExpectedTransferInterruption(error))"omissions_report_cancelled" else "omissions_report_failed",value,error=error);throw error }
    } }
}







