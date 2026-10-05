package com.mmg.manahub.feature.collection

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mmg.manahub.core.data.queue.SharedPreferencesCardQueueStore
import com.mmg.manahub.core.domain.collection.transfer.LegacyImportRecoveryState
import com.mmg.manahub.feature.collection.data.LegacyCollectionImportQuarantine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.*
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class LegacyCollectionImportQuarantineTest {
    private val queueKey=SharedPreferencesCardQueueStore.COLLECTION_IMPORT_QUEUE_KEY
    private val unresolvedKey=SharedPreferencesCardQueueStore.COLLECTION_IMPORT_UNRESOLVED_KEY
    private class Fixture {
        private val actual=InstrumentationRegistry.getInstrumentation().targetContext
        private val prefix="quarantine-test-${UUID.randomUUID()}-"
        val context=object : ContextWrapper(actual) { override fun getSharedPreferences(name: String,mode: Int)=actual.getSharedPreferences(prefix+name,mode) }
        val prefs=context.getSharedPreferences(SharedPreferencesCardQueueStore.COLLECTION_IMPORT_PREF_FILE,Context.MODE_PRIVATE)
        val root=File(actual.cacheDir,prefix)
        fun close() { root.deleteRecursively(); for(name in listOf(SharedPreferencesCardQueueStore.COLLECTION_IMPORT_PREF_FILE,"collection_import_recovery","scanner_prefs"))actual.deleteSharedPreferences(prefix+name) }
    }
    @Test fun explicitRecoveryArchivePreservesVerifiedOpaqueSourcesWithoutRestoringQueue()=runBlocking<Unit> {
        val f=Fixture()
        try {
            f.prefs.edit().putString(queueKey,"private𐐀日本").putString(unresolvedKey,"unowned errors").commit()
            val quarantine=LegacyCollectionImportQuarantine(f.context,f.root)
            assertEquals(LegacyImportRecoveryState.RECOVERY_AVAILABLE,quarantine.run().state)
            val source=f.root.listFiles()!!.single().resolve("source").readBytes()
            val output=ByteArrayOutputStream()
            assertEquals(1L,quarantine.saveRecovery(android.net.Uri.parse("content://fixture/recovery")) { output })
            DataInputStream(ByteArrayInputStream(output.toByteArray())).use { archive ->
                assertEquals(0x4d485232,archive.readInt());assertTrue(archive.readBoolean());archive.readUTF()
                assertEquals(source.size.toLong(),archive.readLong())
                val hash=ByteArray(32).also(archive::readFully)
                val recovered=ByteArray(source.size).also(archive::readFully)
                assertArrayEquals(source,recovered)
                assertArrayEquals(java.security.MessageDigest.getInstance("SHA-256").digest(source),hash)
                assertFalse(archive.readBoolean());assertEquals(-1,archive.read())
            }
            assertNull(quarantine.queueStore().read());assertTrue(quarantine.notice.value.previousApplicationUnknown)
            assertArrayEquals(source,f.root.listFiles()!!.single().resolve("source").readBytes())
        } finally { f.close() }
    }
    @Test fun opaqueUnicodeCopyIsVerifiedBeforeRemovalAndScannerIsUntouched()=runBlocking<Unit> {
        val f=Fixture()
        try {
            val payload="private𐐀日本"+"x".repeat(70_000)
            f.prefs.edit().putString(queueKey,payload).putString(unresolvedKey,"unresolved-private").commit()
            val scanner=f.context.getSharedPreferences("scanner_prefs",Context.MODE_PRIVATE); scanner.edit().putString("scanner_queue_v1","scanner-private").putString("scanner_deck_queue_v1_fixture","deck-private").commit()
            val q=LegacyCollectionImportQuarantine(f.context,f.root); assertNull(q.queueStore().read())
            val notice=q.run(); assertEquals(LegacyImportRecoveryState.RECOVERY_AVAILABLE,notice.state); assertTrue(notice.previousApplicationUnknown); assertFalse(notice.toString().contains("private"))
            assertFalse(f.prefs.contains(queueKey)); assertFalse(f.prefs.contains(unresolvedKey)); assertEquals("scanner-private",scanner.getString("scanner_queue_v1",null)); assertEquals("deck-private",scanner.getString("scanner_deck_queue_v1_fixture",null))
            val source=f.root.listFiles()!!.single().resolve("source")
            val recovered=mutableMapOf<String,String>()
            DataInputStream(source.inputStream().buffered()).use { data -> assertEquals(0x4d485131,data.readInt()); repeat(data.readInt()) { val key=data.readUTF(); val count=data.readInt(); recovered[key]=buildString { repeat(count) { append(data.readChar()) } } }; assertEquals(-1,data.read()) }
            assertEquals(payload,recovered[queueKey]); assertEquals("unresolved-private",recovered[unresolvedKey]); assertNull(q.queueStore().read())
        } finally { f.close() }
    }
    @Test fun failedCopyPreservesOriginalAndCannotRestoreOrOverwriteIt()=runBlocking<Unit> {
        val f=Fixture()
        try {
            f.prefs.edit().putString(queueKey,"private-original").putString(unresolvedKey,"private-errors").commit()
            val q=LegacyCollectionImportQuarantine(f.context,f.root) { file -> object : FileOutputStream(file) { override fun write(bytes: ByteArray,offset: Int,length: Int) { super.write(bytes,offset,minOf(5,length)); throw IOException("Injected storage failure") } } }
            assertEquals(LegacyImportRecoveryState.RECOVERY_FAILED,q.run().state); assertNull(q.queueStore().read())
            withContext(Dispatchers.IO) {
                assertTrue(q.unresolvedStore().read().isEmpty())
                try { q.queueStore().write("replacement"); fail("Failed quarantine must protect original") } catch(_: IllegalStateException) { }
            }
            assertEquals("private-original",f.prefs.getString(queueKey,null)); assertEquals("private-errors",f.prefs.getString(unresolvedKey,null))
            assertTrue(f.root.listFiles()!!.single().resolve("source.part").exists())
            assertEquals(LegacyImportRecoveryState.RECOVERY_AVAILABLE,LegacyCollectionImportQuarantine(f.context,f.root).run().state)
            assertEquals(2,f.root.listFiles()!!.size)
        } finally { f.close() }
    }
    @Test fun reopenAndRepeatedPayloadNeverOverwriteRecovery()=runBlocking<Unit> {
        val f=Fixture()
        try {
            f.prefs.edit().putString(queueKey,"same-private-payload").commit(); assertEquals(LegacyImportRecoveryState.RECOVERY_AVAILABLE,LegacyCollectionImportQuarantine(f.context,f.root).run().state)
            val source=f.root.listFiles()!!.single().resolve("source"); val original=source.readBytes(); val modified=source.lastModified()
            assertEquals(LegacyImportRecoveryState.RECOVERY_AVAILABLE,LegacyCollectionImportQuarantine(f.context,f.root).run().state)
            f.prefs.edit().putString(queueKey,"same-private-payload").commit(); assertEquals(LegacyImportRecoveryState.RECOVERY_AVAILABLE,LegacyCollectionImportQuarantine(f.context,f.root).run().state)
            assertEquals(1,f.root.listFiles()!!.size); assertArrayEquals(original,source.readBytes()); assertEquals(modified,source.lastModified())
        } finally { f.close() }
    }
    @Test fun corruptedRecoveryIsNeverReplacedAndOriginalIsRetained()=runBlocking<Unit> {
        val f=Fixture()
        try {
            f.prefs.edit().putString(queueKey,"private-payload").commit(); LegacyCollectionImportQuarantine(f.context,f.root).run()
            val source=f.root.listFiles()!!.single().resolve("source"); source.writeText("injected-corruption"); f.prefs.edit().putString(queueKey,"private-payload").commit()
            assertEquals(LegacyImportRecoveryState.RECOVERY_FAILED,LegacyCollectionImportQuarantine(f.context,f.root).run().state)
            assertEquals("injected-corruption",source.readText()); assertEquals("private-payload",f.prefs.getString(queueKey,null))
        } finally { f.close() }
    }
    @Test fun sourceChangedDuringCopyIsNotRemovedAndBothRecoveriesRemainSeparate()=runBlocking<Unit> {
        val f=Fixture()
        try {
            f.prefs.edit().putString(queueKey,"before-copy").commit()
            val q=LegacyCollectionImportQuarantine(f.context,f.root) { file -> object : FileOutputStream(file) { override fun close() { super.close(); f.prefs.edit().putString(queueKey,"changed-during-copy").commit() } } }
            assertEquals(LegacyImportRecoveryState.RECOVERY_FAILED,q.run().state); assertEquals("changed-during-copy",f.prefs.getString(queueKey,null))
            assertEquals(LegacyImportRecoveryState.RECOVERY_AVAILABLE,LegacyCollectionImportQuarantine(f.context,f.root).run().state); assertEquals(2,f.root.listFiles()!!.size)
        } finally { f.close() }
    }
}
