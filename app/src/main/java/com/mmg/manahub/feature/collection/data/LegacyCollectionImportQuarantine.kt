package com.mmg.manahub.feature.collection.data

import android.content.Context
import android.os.Looper
import android.net.Uri
import com.mmg.manahub.core.data.queue.CardQueueStore
import com.mmg.manahub.core.data.queue.SharedPreferencesCardQueueStore
import com.mmg.manahub.core.domain.collection.transfer.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runInterruptible
import java.io.*
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.util.UUID
import java.nio.file.Files

/** Import-only quarantine runs off Main and never restores unowned preferences into the queue. */
class LegacyCollectionImportQuarantine(
    context: Context,
    private val root: File=File(context.filesDir,"collection-transfers/legacy-quarantine"),
    private val openOutput: (File)->FileOutputStream={ FileOutputStream(it) },
) {
    private val prefs=context.getSharedPreferences(SharedPreferencesCardQueueStore.COLLECTION_IMPORT_PREF_FILE,Context.MODE_PRIVATE)
    private val metadata=context.getSharedPreferences("collection_import_recovery",Context.MODE_PRIVATE)
    private val resolver=context.contentResolver
    private val keys=listOf(SharedPreferencesCardQueueStore.COLLECTION_IMPORT_QUEUE_KEY,SharedPreferencesCardQueueStore.COLLECTION_IMPORT_UNRESOLVED_KEY)
    private val mutableNotice=MutableStateFlow(LegacyImportRecoveryNotice(LegacyImportRecoveryState.PENDING))
    val notice: StateFlow<LegacyImportRecoveryNotice> = mutableNotice.asStateFlow()
    private var initialized=false
    private val lock=Any()
    private fun snapshot()=keys.mapNotNull { key -> prefs.getString(key,null)?.let { key to it } }.toMap()
    suspend fun run(): LegacyImportRecoveryNotice = runInterruptible(Dispatchers.IO) { ensureReady() }
    private fun ensureReady(): LegacyImportRecoveryNotice {
        check(Looper.myLooper()!=Looper.getMainLooper()) { "Quarantine requires background execution" }
        return synchronized(lock) {
            if(initialized)return@synchronized mutableNotice.value
            val operation=QuarantineLegacyImport(object : LegacyImportQuarantineSource {
                override fun snapshot()=this@LegacyCollectionImportQuarantine.snapshot()
                override fun removeIfUnchanged(snapshot: Map<String,String>): Boolean {
                    if(Thread.currentThread().isInterrupted)throw InterruptedException()
                    if(this@LegacyCollectionImportQuarantine.snapshot()!=snapshot)return false
                    return prefs.edit().also { editor -> keys.forEach(editor::remove) }.commit()
                }
            },object : LegacyImportQuarantineStorage {
                override fun preserveAndVerify(snapshot: Map<String,String>)=preserve(snapshot)
            })
            var result=operation.run()
            if(result.state==LegacyImportRecoveryState.NONE && metadata.getBoolean("recovery_available",false))result=LegacyImportRecoveryNotice(LegacyImportRecoveryState.RECOVERY_AVAILABLE)
            mutableNotice.value=result
            initialized=true
            result
        }
    }
    private fun serialize(snapshot: Map<String,String>, output: OutputStream) {
        val data=DataOutputStream(output)
        data.writeInt(0x4d485131); data.writeInt(snapshot.size)
        for((key,value) in snapshot.toSortedMap()) {
            data.writeUTF(key); data.writeInt(value.length)
            for(start in value.indices step TransferLimits.CHARACTER_BUFFER_SIZE) {
                if(Thread.currentThread().isInterrupted)throw InterruptedException()
                val end=minOf(value.length,start+TransferLimits.CHARACTER_BUFFER_SIZE)
                for(index in start until end)data.writeChar(value[index].code)
            }
        }
        data.flush()
    }
    private fun hash(snapshot: Map<String,String>): ByteArray {
        val digest=MessageDigest.getInstance("SHA-256")
        serialize(snapshot,BufferedOutputStream(DigestOutputStream(object : OutputStream() { override fun write(value: Int) {}; override fun write(bytes: ByteArray,offset: Int,length: Int) {} },digest),TransferLimits.CHARACTER_BUFFER_SIZE))
        return digest.digest()
    }
    private fun hash(file: File): ByteArray {
        val digest=MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered(TransferLimits.CHARACTER_BUFFER_SIZE).use { input ->
            val buffer=ByteArray(TransferLimits.CHARACTER_BUFFER_SIZE)
            while(true) { if(Thread.currentThread().isInterrupted)throw InterruptedException(); val count=input.read(buffer); if(count<0)break; digest.update(buffer,0,count) }
        }
        return digest.digest()
    }
    private fun preserve(snapshot: Map<String,String>): Boolean {
        val expected=hash(snapshot); val stable=File(root,UUID.nameUUIDFromBytes(expected).toString())
        var folder=stable
        if(File(folder,"source.part").exists() && !File(folder,"source").exists())folder=File(root,UUID.randomUUID().toString())
        if(!folder.exists() && !folder.mkdirs())return false
        val target=File(folder,"source")
        if(!target.exists()) {
            val partial=File(folder,"source.part")
            openOutput(partial).use { output -> serialize(snapshot,BufferedOutputStream(output,TransferLimits.CHARACTER_BUFFER_SIZE)); output.fd.sync() }
            if(!hash(partial).contentEquals(expected) || !partial.renameTo(target))return false
        }
        if(!hash(target).contentEquals(expected))return false
        return metadata.edit().putBoolean("recovery_available",true).putBoolean("previous_application_unknown",true).commit()
    }
    /** Legacy restore is suppressed even when recovery fails, rather than exposing an unowned list. */
    fun queueStore(): CardQueueStore = object : CardQueueStore {
        override fun read(): String? = null
        override fun write(payload: String) { write(SharedPreferencesCardQueueStore.COLLECTION_IMPORT_QUEUE_KEY,payload) }
    }
    /** Unresolved plaintext has the same quarantine boundary as its associated legacy queue. */
    fun unresolvedStore(): CollectionImportUnresolvedStore = object : CollectionImportUnresolvedStore {
        override fun read(): List<String> {
            if(ensureReady().state==LegacyImportRecoveryState.RECOVERY_FAILED)return emptyList()
            return synchronized(lock) { prefs.getString(SharedPreferencesCardQueueStore.COLLECTION_IMPORT_UNRESOLVED_KEY,null)?.lineSequence()?.filter { it.isNotBlank() }?.take(MAX_PERSISTED_UNRESOLVED_LINES)?.toList().orEmpty() }
        }
        override fun write(lines: List<String>) { write(SharedPreferencesCardQueueStore.COLLECTION_IMPORT_UNRESOLVED_KEY,lines.take(MAX_PERSISTED_UNRESOLVED_LINES).joinToString("\n")) }
    }
    private fun write(key: String,payload: String) {
        check(ensureReady().state!=LegacyImportRecoveryState.RECOVERY_FAILED) { "Import recovery unavailable" }
        synchronized(lock) { check(prefs.edit().putString(key,payload).commit()) { "Import persistence unavailable" } }
    }

    suspend fun saveRecovery(target: Uri,recoveryOutput: (Uri)->OutputStream={ uri -> resolver.openOutputStream(uri,"wt") ?: throw IOException("Recovery output unavailable") }): Long=runInterruptible(Dispatchers.IO) {
        check(ensureReady().state==LegacyImportRecoveryState.RECOVERY_AVAILABLE)
        var copies=0L
        DataOutputStream(BufferedOutputStream(recoveryOutput(target),TransferLimits.CHARACTER_BUFFER_SIZE)).use { output ->
            output.writeInt(0x4d485232)
            Files.newDirectoryStream(root.toPath()).use { folders ->
                for(folder in folders) {
                    if(Thread.currentThread().isInterrupted)throw InterruptedException()
                    val source=folder.resolve("source").toFile()
                    if(!source.isFile)continue
                    val expected=hash(source)
                    output.writeBoolean(true);output.writeUTF(folder.fileName.toString());output.writeLong(source.length());output.write(expected)
                    val digest=MessageDigest.getInstance("SHA-256")
                    source.inputStream().buffered(TransferLimits.CHARACTER_BUFFER_SIZE).use { input ->
                        val buffer=ByteArray(TransferLimits.CHARACTER_BUFFER_SIZE)
                        while(true) {
                            if(Thread.currentThread().isInterrupted)throw InterruptedException()
                            val count=input.read(buffer);if(count<0)break
                            digest.update(buffer,0,count);output.write(buffer,0,count)
                        }
                    }
                    check(digest.digest().contentEquals(expected)) { "Recovery integrity changed" }
                    copies++
                }
            }
            check(copies>0L);output.writeBoolean(false);output.flush()
        }
        copies
    }
}
