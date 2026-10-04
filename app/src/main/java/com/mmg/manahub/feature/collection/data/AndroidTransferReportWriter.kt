package com.mmg.manahub.feature.collection.data

import android.content.ContentResolver
import android.net.Uri
import com.mmg.manahub.core.data.local.MtgDatabase
import com.mmg.manahub.core.domain.collection.transfer.*
import kotlinx.coroutines.*
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.io.OutputStream
import java.io.File
import java.io.RandomAccessFile
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest

class AndroidTransferReportWriter(
    private val database: MtgDatabase,
    private val sessions: TransferSessionGate,
    private val observer: TransferAuthSessionObserver,
    private val resolver: ContentResolver,
    private val filesDir: File,
    private val openOutput: (Uri)->OutputStream = { uri -> resolver.openOutputStream(uri,"wt") ?: throw TransferStorageException(TransferError.STORAGE_FAILURE) },
) {
    suspend fun write(id: TransferJobId,owner: TransferOwner,target: Uri): Long=withContext(Dispatchers.IO) {
        val captured=sessions.currentSession
        val reportContext=currentCoroutineContext()
        fun guard() {
            if((captured as? TransferSession.Available)?.owner!=owner || sessions.currentSession!=captured || !observer.matchesObserved(owner))throw TransferReadException(TransferError.OWNER_CHANGED)
        }
        guard()
        val dao=database.collectionTransferDao()
        val version=dao.getJob(id.value,owner.storageKey()) ?: throw TransferReadException(TransferError.NOT_FOUND)
        var records=0L
        BufferedWriter(OutputStreamWriter(openOutput(target),Charsets.UTF_8),64*1024).use { writer ->
            fun line(vararg values: Any?) { writer.appendLine(values.joinToString(",") { value -> "\"${value?.toString().orEmpty().replace("\"","\"\"")}\"" }) }
            line("File or action ID","File or action state","Selected","Retired","Ordinal or entry ID","Record kind","Record state","Quantity (source record or entry snapshot)","Printing","Foil","Condition","Language","Error","Preview","Destination","Original record or rejected source","Original text availability")
            var afterFile=""
            while(true) {
                currentCoroutineContext().ensureActive();guard()
                val files=dao.fileInventoryPage(id.value,owner.storageKey(),afterFile)
                if(files.isEmpty())break
                for(file in files) {
                    guard()
                    val source=File(filesDir,"collection-transfers/${id.value}/${file.id}/source")
                    val expected="${id.value}/${file.id}/source"
                    val availability=when {
                        !source.isFile -> if(File(source.parentFile,"source.part").exists())"INCOMPLETE" else "UNAVAILABLE"
                        file.sourcePath!=expected || file.sha256==null || source.length()!=file.bytes -> "INCOMPLETE"
                        else -> {
                            val digest=MessageDigest.getInstance("SHA-256")
                            source.inputStream().use { input -> val buffer=ByteArray(64*1024);while(true) { currentCoroutineContext().ensureActive();guard();val count=input.read(buffer);if(count<0)break;digest.update(buffer,0,count) } }
                            if(digest.digest().joinToString("") { "%02x".format(it) }==file.sha256)"COMPLETE" else "INCOMPLETE"
                        }
                    }
                    val original=if(availability=="COMPLETE")RandomAccessFile(source,"r") else null
                    fun sourceLine(values: Array<out Any?>,start: Long?,end: Long?) {
                        val rangeReady=original!=null && start!=null && end!=null && start>=0L && end>=start && end<=file.bytes
                        writer.append(values.joinToString(",") { value -> "\"${value?.toString().orEmpty().replace("\"","\"\"")}\"" }).append(",\"")
                        if(rangeReady) {
                            val inputFile=requireNotNull(original);inputFile.seek(requireNotNull(start));var remaining=requireNotNull(end)-start
                            val limited=object: InputStream() {
                                override fun read(): Int=if(remaining==0L)-1 else inputFile.read().also { if(it>=0)remaining-- }
                                override fun read(buffer: ByteArray,offset: Int,length: Int): Int=if(remaining==0L)-1 else inputFile.read(buffer,offset,minOf(length.toLong(),remaining).toInt()).also { if(it>0)remaining-=it }
                            }
                            val decoder=Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                            InputStreamReader(limited,decoder).use { reader -> val buffer=CharArray(16*1024);while(true) { reportContext.ensureActive();guard();val count=reader.read(buffer);if(count<0)break;writer.append(String(buffer,0,count).replace("\"","\"\"")) } }
                            check(remaining==0L)
                        }
                        writer.append("\",\"").append(if(start==null)availability else if(rangeReady)"COMPLETE" else if(availability=="COMPLETE")"RANGE_UNAVAILABLE" else availability).appendLine("\"")
                    }
                    try {
                    sourceLine(arrayOf<Any?>(file.id,file.phase,file.selected,file.retired,"","FILE",if(file.phase=="REJECTED")"SCAN_INCOMPLETE" else "",file.copies,"","","","",file.error,"",""),if(file.phase=="REJECTED")0L else null,if(file.phase=="REJECTED")file.bytes else null)
                    var after=0L
                    while(true) {
                        currentCoroutineContext().ensureActive();guard()
                        val rows=dao.rowPage(id.value,owner.storageKey(),file.id,after)
                        if(rows.isEmpty())break
                        for(row in rows) { sourceLine(arrayOf<Any?>(file.id,file.phase,file.selected,file.retired,row.sourceOrdinal,row.kind,if(file.retired || !file.selected || file.phase=="REJECTED")"SOURCE_EXCLUDED" else row.state,row.quantity,row.resolvedId ?: row.scryfallId,row.isFoil,row.condition,row.language,row.error,row.preview,""),row.byteStart,row.byteEnd);records++ }
                        after=rows.last().sourceOrdinal
                    }
                    } finally { original?.close() }
                }
                afterFile=files.last().id
            }
            var afterEntry=""
            while(true) {
                currentCoroutineContext().ensureActive();guard()
                val entries=dao.reviewPage(id.value,owner.storageKey(),version.generation,afterEntry)
                if(entries.isEmpty())break
                entries.forEach { entry -> line("","",!entry.excluded,"",entry.id,"ENTRY",entry.state,entry.quantity,entry.scryfallId,entry.isFoil,entry.condition,entry.language,entry.error,"",entry.destination,"","NOT_A_SOURCE_RECORD") }
                afterEntry=entries.last().id
            }
            var afterAction=""
            while(true) {
                currentCoroutineContext().ensureActive();guard()
                val actions=dao.reportActionPage(id.value,owner.storageKey(),afterAction)
                if(actions.isEmpty())break
                for(action in actions) {
                    var after=""
                    while(true) {
                        currentCoroutineContext().ensureActive();guard()
                        val entries=dao.actionPage(action.id,owner.storageKey(),after)
                        if(entries.isEmpty())break
                        entries.forEach { entry -> line(action.id,action.phase,"","",entry.entryId,"COMMAND",entry.state,entry.quantity,entry.scryfallId,entry.isFoil,entry.condition,entry.language,"","",action.destination,"","NOT_A_SOURCE_RECORD") }
                        after=entries.last().entryId
                    }
                }
                afterAction=actions.last().id
            }
            guard()
            val current=dao.getJob(id.value,owner.storageKey()) ?: throw TransferReadException(TransferError.NOT_FOUND)
            if(current.generation!=version.generation || current.payloadVersion!=version.payloadVersion)throw TransferReadException(TransferError.REVIEW_CHANGED)
            writer.flush()
        }
        guard();records
    }
}

