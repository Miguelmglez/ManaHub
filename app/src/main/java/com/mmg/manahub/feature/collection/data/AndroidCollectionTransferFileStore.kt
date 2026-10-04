package com.mmg.manahub.feature.collection.data

import android.content.ContentResolver
import android.net.Uri
import android.os.CancellationSignal
import android.system.ErrnoException
import android.system.OsConstants
import com.mmg.manahub.core.data.local.dao.CollectionTransferDao
import com.mmg.manahub.core.data.local.entity.CollectionImportRowEntity
import com.mmg.manahub.core.data.local.entity.CollectionTransferFileEntity
import com.mmg.manahub.core.data.local.entity.CollectionTransferReceiptEntity
import com.mmg.manahub.core.domain.collection.transfer.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.*
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** A source identity is transient; provider names and grants never enter persisted paths. */
interface TransferInputSource {
    val identity: String
    fun open(): InputStream
    fun cancelOpen() {}
}

/** CancellationSignal releases a provider blocked while opening its descriptor. */
class ContentTransferInputSource(private val resolver: ContentResolver, private val uri: Uri) : TransferInputSource {
    private val signal = CancellationSignal()
    override val identity: String get() = uri.toString()
    override fun open(): InputStream {
        if (uri.scheme != "content") throw TransferStorageException(TransferError.INVALID_SOURCE)
        val descriptor = resolver.openAssetFileDescriptor(uri, "r", signal) ?: throw FileNotFoundException()
        return try { descriptor.createInputStream() } catch (failure: Throwable) { descriptor.close(); throw failure }
    }
    override fun cancelOpen() = signal.cancel()
}

/** Defaults are inclusive byte limits and monotonic, independently bounded deadlines. */
data class TransferCopyPolicy(
    val fileBytes: Long = TransferLimits.MAX_FILE_BYTES,
    val batchBytes: Long = TransferLimits.MAX_BATCH_BYTES,
    val noProgressMillis: Long = 45_000L,
    val fileMillis: Long = 300_000L,
    val receiptMillis: Long = 600_000L,
) {
    init { require(fileBytes in 1..TransferLimits.MAX_FILE_BYTES && batchBytes in 1..TransferLimits.MAX_BATCH_BYTES && noProgressMillis > 0 && fileMillis > 0 && receiptMillis > 0) }
}

/** Typed categories carry neither an external URI nor a provider exception chain. */
class TransferStorageException(val category: TransferError) : IOException(category.name)

/** Receipt checkpoints expose durable boundaries without replacing private file operations. */
enum class TransferReceiptCheckpoint { AFTER_HASH_PERSISTED, AFTER_RENAME }

/** Private copies remain neutral until the existing receipt-binding transaction resolves ownership. */
class AndroidCollectionTransferFileStore(
    private val filesDir: File,
    private val dao: CollectionTransferDao,
    private val session: () -> TransferSession,
    private val policy: TransferCopyPolicy = TransferCopyPolicy(),
    private val monotonicMillis: () -> Long = { System.nanoTime() / 1_000_000L },
    private val openOutput: (File) -> FileOutputStream = { FileOutputStream(it) },
    private val checkpoint: suspend (TransferReceiptCheckpoint) -> Unit = {},
) : CollectionTransferFileStore {
    private val intakeMutex = Mutex()
    private val root get() = File(filesDir, "collection-transfers").canonicalFile

    /** Cardinality and the known-owner admission limit are checked before opening any provider. */
    suspend fun receive(id: TransferJobId, authGeneration: Long, sources: List<TransferInputSource>, capturedSession: TransferSession? = null): List<CollectionTransferFileEntity> = intakeMutex.withLock {
        val distinct = sources.distinctBy { it.identity }
        if (distinct.size !in 1..TransferLimits.MAX_FILES) throw TransferStorageException(TransferError.TOO_MANY_FILES)
        require(authGeneration >= 0L)
        val captured = ((capturedSession ?: session()) as? TransferSession.Available)?.takeIf { it.generation == authGeneration }?.owner?.key()
        val existing = dao.getReceipt(id.value)
        if (existing != null) return@withLock recoverReceipt(id)
        val files = distinct.mapIndexed { index, _ -> CollectionTransferFileEntity(UUID.randomUUID().toString(), id.value, fileOrder = index) }
        if (!dao.createReceipt(CollectionTransferReceiptEntity(id.value, authGeneration, captured, createdAt = System.currentTimeMillis()), files)) throw TransferStorageException(TransferError.SELECTION_LIMIT)
        val executor = Executors.newSingleThreadExecutor { task -> Thread(task, "collection-source-copy").apply { isDaemon = true } }
        var receiptError: TransferError? = null
        try {
            withTimeout(policy.receiptMillis) {
                for ((index, source) in distinct.withIndex()) {
                    currentCoroutineContext().ensureActive()
                    val file = dao.getReceiptFiles(id.value).first { it.id == files[index].id }
                    try {
                        withTimeout(policy.fileMillis) { copySource(id, file, source, executor) }
                    } catch (failure: TimeoutCancellationException) {
                        currentCoroutineContext().ensureActive()
                        receiptError = TransferError.COPY_TIMEOUT
                        failMember(id, file.id, TransferError.COPY_TIMEOUT)
                    } catch (failure: TransferStorageException) {
                        receiptError = failure.category
                        failMember(id, file.id, failure.category)
                        if (failure.category in setOf(TransferError.BATCH_TOO_LARGE, TransferError.STORAGE_FAILURE)) break
                    } catch (failure: SecurityException) {
                        receiptError = TransferError.GRANT_LOST
                        failMember(id, file.id, TransferError.GRANT_LOST)
                    } catch (failure: IOException) {
                        val category = if (failure.isNoSpace()) TransferError.NO_SPACE else TransferError.STORAGE_FAILURE
                        receiptError = category
                        failMember(id, file.id, category)
                    }
                }
            }
        } catch (failure: CancellationException) {
            withContext(NonCancellable) {
                dao.getReceiptFiles(id.value).filter { it.phase == "RECEIVING" }.forEach { failMember(id, it.id, TransferError.COPY_TIMEOUT) }
                dao.finishReception(id.value, TransferError.COPY_TIMEOUT.name)
            }
            if (failure !is TimeoutCancellationException) throw failure
            receiptError = TransferError.COPY_TIMEOUT
        } finally { executor.shutdownNow() }
        dao.getReceiptFiles(id.value).filter { it.phase == "RECEIVING" }.forEach { failMember(id, it.id, receiptError ?: TransferError.INVALID_SOURCE) }
        dao.finishReception(id.value, receiptError?.name)
        dao.getReceiptFiles(id.value)
    }

    private suspend fun failMember(id: TransferJobId, fileId: String, error: TransferError) {
        val file = dao.getReceiptFiles(id.value).first { it.id == fileId }
        dao.recordReception(id.value, fileId, file.bytes, if (error in setOf(TransferError.FILE_TOO_LARGE, TransferError.BATCH_TOO_LARGE)) "REJECTED" else "FAILED_RETRYABLE", file.sha256, file.sourcePath, error.name)
    }

    suspend fun replaceSource(id: TransferJobId,owner: TransferOwner,generation: Long,fileId: TransferFileId,source: TransferInputSource): Boolean = intakeMutex.withLock {
        val captured=session()
        if((captured as? TransferSession.Available)?.owner!=owner)throw TransferStorageException(TransferError.OWNER_CHANGED)
        val replacement=dao.reserveSourceReplacement(id.value,owner.key(),generation,fileId.value,UUID.randomUUID().toString()) ?: return@withLock false
        val executor=Executors.newSingleThreadExecutor { task -> Thread(task,"collection-source-copy").apply { isDaemon=true } }
        try {
            withTimeout(minOf(policy.fileMillis,policy.receiptMillis)) { copySource(id,replacement,source,executor) }
            if(session()!=captured)return@withLock false
            dao.completeSourceReplacement(id.value,owner.key(),generation,fileId.value,replacement.id)
        } catch(cancelled: CancellationException) {
            withContext(NonCancellable) { failMember(id,replacement.id,TransferError.COPY_TIMEOUT) }
            if(cancelled !is TimeoutCancellationException)throw cancelled
            false
        } catch(failure: Exception) {
            val category=(failure as? TransferStorageException)?.category ?: if(failure is SecurityException)TransferError.GRANT_LOST else if(failure.isNoSpace())TransferError.NO_SPACE else TransferError.STORAGE_FAILURE
            failMember(id,replacement.id,category)
            false
        } finally { executor.shutdownNow() }
    }

    private suspend fun copySource(id: TransferJobId, file: CollectionTransferFileEntity, source: TransferInputSource, executor: ExecutorService) {
        val directory = directory(id, TransferFileId(file.id))
        withContext(Dispatchers.IO) { if (!directory.mkdirs() && !directory.isDirectory) throw TransferStorageException(TransferError.NO_SPACE) }
        val part = File(directory, "source.part")
        val final = File(directory, "source")
        if (final.exists() || part.exists()) throw TransferStorageException(TransferError.INVALID_SOURCE)
        val stream = AtomicReference<InputStream?>()
        val output = AtomicReference<FileOutputStream?>()
        val closing = AtomicReference<CompletableDeferred<Unit>?>()
        val bytes = ByteArray(TransferLimits.CHARACTER_BUFFER_SIZE)
        val digest = MessageDigest.getInstance("SHA-256")
        var total = 0L
        var lastProgress = monotonicMillis()
        val preceding = dao.getReceipt(id.value)!!.receivedBytes
        try {
            withTimeout(policy.noProgressMillis) { blocking(executor, stream, output, source, closing) { stream.set(source.open()); output.set(openOutput(part)) } }
            while (true) {
                val remainingTime = policy.noProgressMillis - (monotonicMillis() - lastProgress)
                if (remainingTime <= 0L) throw TransferStorageException(TransferError.COPY_TIMEOUT)
                val count = withTimeout(remainingTime) { blocking(executor, stream, output, source, closing) { stream.get()!!.read(bytes) } }
                if (count < 0) break
                if (count == 0) { delay(1L); continue }
                total += count
                check(dao.recordReception(id.value, file.id, total, "RECEIVING", null, null, null))
                if (total > policy.fileBytes) throw TransferStorageException(TransferError.FILE_TOO_LARGE)
                if (total > policy.batchBytes - preceding) throw TransferStorageException(TransferError.BATCH_TOO_LARGE)
                blocking(executor, stream, output, source, closing) {
                    if (directory.usableSpace < count) throw TransferStorageException(TransferError.NO_SPACE)
                    output.get()!!.write(bytes, 0, count)
                    digest.update(bytes, 0, count)
                }
                lastProgress = monotonicMillis()
            }
            blocking(executor, stream, output, source, closing) { output.get()!!.fd.sync(); output.getAndSet(null)?.close(); stream.getAndSet(null)?.close() }
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            val path = "${id.value}/${file.id}/source"
            check(dao.recordReception(id.value, file.id, total, "RECEIVING", hash, path, null))
            checkpoint(TransferReceiptCheckpoint.AFTER_HASH_PERSISTED)
            withContext(Dispatchers.IO) { if (!part.renameTo(final)) throw TransferStorageException(TransferError.STORAGE_FAILURE) }
            checkpoint(TransferReceiptCheckpoint.AFTER_RENAME)
            check(dao.recordReception(id.value, file.id, total, "PARSING", hash, path, null))
        } finally {
            withContext(NonCancellable) {
                if (withTimeoutOrNull(1000L) { closeAsync(stream, output, source, closing).await(); true } != true) throw TransferStorageException(TransferError.STORAGE_FAILURE)
            }
        }
    }

    private suspend fun <T> blocking(executor: ExecutorService, input: AtomicReference<InputStream?>, output: AtomicReference<FileOutputStream?>, source: TransferInputSource, closing: AtomicReference<CompletableDeferred<Unit>?>, action: () -> T): T = suspendCancellableCoroutine { continuation ->
        val future = executor.submit {
            try { val result = action(); if (continuation.isActive) continuation.resume(result) else { runCatching { input.getAndSet(null)?.close() }; runCatching { output.getAndSet(null)?.close() } } }
            catch (failure: Throwable) { if (continuation.isActive) continuation.resumeWithException(failure) }
        }
        continuation.invokeOnCancellation { future.cancel(true); closeAsync(input, output, source, closing) }
    }

    private fun closeAsync(input: AtomicReference<InputStream?>, output: AtomicReference<FileOutputStream?>, source: TransferInputSource, closing: AtomicReference<CompletableDeferred<Unit>?>): CompletableDeferred<Unit> {
        val completion = CompletableDeferred<Unit>()
        if (!closing.compareAndSet(null, completion)) return closing.get()!!
        val opened = input.getAndSet(null)
        val target = output.getAndSet(null)
        Thread({
            runCatching { source.cancelOpen() }
            val inputClosed = runCatching { opened?.close() }.isSuccess
            val outputClosed = runCatching { target?.close() }.isSuccess
            if (inputClosed && outputClosed) completion.complete(Unit)
            else completion.completeExceptionally(TransferStorageException(TransferError.STORAGE_FAILURE))
        }, "collection-source-close").apply { isDaemon = true }.start()
        return completion
    }
    /** Rename gaps are recovered from app-generated final paths; partial files remain unreceived. */
    suspend fun recoverReceipt(id: TransferJobId): List<CollectionTransferFileEntity> = withContext(Dispatchers.IO) {
        val receipt = dao.getReceipt(id.value) ?: return@withContext emptyList()
        val active = (session() as? TransferSession.Available)?.owner?.key()
        if (receipt.capturedOwner != null && receipt.capturedOwner != active) throw TransferStorageException(TransferError.OWNER_CHANGED)
        var preceding = 0L
        for (file in dao.getReceiptFiles(id.value)) {
            val receivedBefore = preceding
            preceding += file.bytes
            if (file.phase !in setOf("RECEIVING", "FAILED_RETRYABLE")) continue
            val final = File(directory(id, TransferFileId(file.id)), "source")
            if (!final.isFile) { failMember(id, file.id, TransferError.INVALID_SOURCE); continue }
            val size = final.length()
            if (size > policy.batchBytes - receivedBefore) { failMember(id, file.id, TransferError.BATCH_TOO_LARGE); continue }
            if (size > policy.fileBytes || size != file.bytes || file.sha256 == null) { failMember(id, file.id, TransferError.INVALID_SOURCE); continue }
            val digest = MessageDigest.getInstance("SHA-256")
            final.inputStream().use { input ->
                val block = ByteArray(TransferLimits.CHARACTER_BUFFER_SIZE)
                while (true) { currentCoroutineContext().ensureActive(); val count = input.read(block); if (count < 0) break; digest.update(block, 0, count) }
            }
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            if (hash != file.sha256) { failMember(id, file.id, TransferError.INVALID_SOURCE); continue }
            dao.recordReception(id.value, file.id, size, "PARSING", hash, "${id.value}/${file.id}/source", null)
        }
        dao.finishReception(id.value, if (dao.getReceiptFiles(id.value).any { it.phase !in setOf("PARSING", "RESOLVING", "REVIEW_READY") }) TransferError.INVALID_SOURCE.name else null)
        dao.getReceiptFiles(id.value)
    }

    /** Recovered parsing validates the complete private source before replaying staged ordinals. */
    suspend fun verifySource(id: TransferJobId,file: TransferFileId) = withContext(Dispatchers.IO) {
        val captured=available()
        val stored=dao.getFile(id.value,captured.owner.key(),file.value) ?: throw TransferStorageException(TransferError.OWNER_CHANGED)
        val source=File(directory(id,file),"source")
        if(!source.isFile || source.length()!=stored.bytes || stored.sha256==null)throw TransferStorageException(TransferError.INVALID_SOURCE)
        val digest=MessageDigest.getInstance("SHA-256")
        source.inputStream().use { input ->
            val block=ByteArray(TransferLimits.CHARACTER_BUFFER_SIZE)
            while(true) {
                currentCoroutineContext().ensureActive()
                if(session()!=captured)throw TransferStorageException(TransferError.OWNER_CHANGED)
                val count=input.read(block); if(count<0)break; digest.update(block,0,count)
            }
        }
        if(session()!=captured)throw TransferStorageException(TransferError.OWNER_CHANGED)
        if(digest.digest().joinToString("") { "%02x".format(it) }!=stored.sha256)throw TransferStorageException(TransferError.INVALID_SOURCE)
    }

    /** The strict decoder rejects malformed sequences rather than manufacturing replacement cards. */
    override suspend fun <T> readSource(id: TransferJobId, file: TransferFileId, consume: suspend (TransferCharacterSource) -> T): T = withContext(Dispatchers.IO) {
        val captured = available()
        val stored = dao.getFile(id.value, captured.owner.key(), file.value) ?: throw TransferStorageException(TransferError.OWNER_CHANGED)
        if (stored.phase !in setOf("PARSING", "RESOLVING", "REVIEW_READY")) throw TransferStorageException(TransferError.INVALID_SOURCE)
        val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        try {
            InputStreamReader(File(directory(id, file), "source").inputStream(), decoder).use { reader ->
                consume(TransferCharacterSource { block, length ->
                    currentCoroutineContext().ensureActive()
                    if (session() != captured) throw TransferStorageException(TransferError.OWNER_CHANGED)
                    reader.read(block, 0, minOf(length, TransferLimits.CHARACTER_BUFFER_SIZE)).coerceAtLeast(0)
                })
            }
        } catch (failure: CharacterCodingException) { throw TransferStorageException(TransferError.INVALID_ENCODING) }
    }

    /** Replays start at byte zero; Room skips only already-committed source ordinals. */
    suspend fun parseSource(id: TransferJobId, file: TransferFileId): TransferParseSummary? {
        val captured = available()
        val owner = captured.owner.key()
        val pending = mutableListOf<CollectionImportRowEntity>()
        suspend fun flush() {
            if (session() != captured) throw TransferStorageException(TransferError.OWNER_CHANGED)
            if (pending.isNotEmpty()) { check(dao.stageParserRows(id.value, owner, file.value, pending.toList())); pending.clear() }
        }
        val sink = object : TransferRecordSink {
            override suspend fun record(record: TransferParsedRecord) {
                val line = record.line
                pending += CollectionImportRowEntity(id.value, file.value, record.ordinal, record.range.start, record.range.endExclusive, record.kind.name, name=line?.name, setCode=line?.setCode, collectorNumber=line?.collectorNumber, scryfallId=line?.scryfallId, quantity=line?.quantity?.toLong(), isFoil=line?.isFoil ?: false, condition=line?.condition ?: "NM", language=line?.language ?: "en", error=record.error?.name, preview=record.preview)
                if (pending.size == 200) flush()
            }
            override suspend fun completed(summary: TransferParseSummary) { flush(); check(dao.completeParsing(id.value, owner, file.value, summary)) }
            override suspend fun rejected(error: TransferParseError) { flush(); check(dao.rejectFile(id.value, owner, file.value, error.name)) }
        }
        return try { readSource(id, file) { StreamingCollectionImportParser().parse(it, sink) } }
        catch (failure: TransferStorageException) {
            if (failure.category == TransferError.INVALID_ENCODING) { flush(); dao.rejectFile(id.value, owner, file.value, failure.category.name); null } else throw failure
        }
    }

    override suspend fun discardSources(id: TransferJobId) = withContext(Dispatchers.IO) {
        val captured = available()
        val job = dao.getJob(id.value, captured.owner.key()) ?: throw TransferStorageException(TransferError.OWNER_CHANGED)
        if (job.phase != "DISCARDED") throw TransferStorageException(TransferError.CONFIRMATION_REQUIRED)
        val target = File(root, id.value).canonicalFile
        check(target.parentFile == root)
        check(!target.exists() || target.deleteRecursively())
    }

    private fun available(): TransferSession.Available = session() as? TransferSession.Available ?: throw TransferStorageException(TransferError.OWNER_UNAVAILABLE)
    private fun directory(id: TransferJobId, file: TransferFileId): File {
        val target = File(File(root, id.value), file.value).canonicalFile
        check(target.parentFile?.parentFile == root)
        return target
    }
    private fun TransferOwner.key(): String = when(this) { is TransferOwner.Account -> "account:$id"; is TransferOwner.VerifiedGuest -> "guest:$installationToken" }
    private fun Throwable.isNoSpace(): Boolean = generateSequence(this) { it.cause }.any { it is ErrnoException && it.errno == OsConstants.ENOSPC }
}
