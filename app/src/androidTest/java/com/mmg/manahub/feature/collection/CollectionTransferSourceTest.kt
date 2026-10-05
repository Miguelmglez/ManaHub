package com.mmg.manahub.feature.collection

import android.system.ErrnoException
import android.system.OsConstants
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mmg.manahub.core.data.local.MtgDatabase
import com.mmg.manahub.core.data.local.entity.CollectionTransferFileEntity
import com.mmg.manahub.core.data.local.entity.CollectionTransferReceiptEntity
import com.mmg.manahub.core.domain.collection.transfer.*
import com.mmg.manahub.feature.collection.data.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.*
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class CollectionTransferSourceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val owner=TransferOwner.Account("fixture-source-a")
    private val available=TransferSession.Available(owner,1L)
    private fun id()=TransferJobId(UUID.randomUUID().toString())
    private fun source(identity: String=UUID.randomUUID().toString(), open: () -> InputStream) = object : TransferInputSource {
        override val identity=identity
        override fun open()=open()
    }
    private fun bytes(text: String)=source { ByteArrayInputStream(text.toByteArray(Charsets.UTF_8)) }
    private suspend fun fixture(persistent: Boolean = false, test: suspend (MtgDatabase, File) -> Unit) {
        val name="source-fixture-${UUID.randomUUID()}"
        val db=if(persistent) Room.databaseBuilder(context,MtgDatabase::class.java,name).build() else Room.inMemoryDatabaseBuilder(context,MtgDatabase::class.java).build()
        val root=File(context.cacheDir,"transfer-source-test-${UUID.randomUUID()}")
        root.mkdirs()
        try { test(db,root) } finally { db.close(); root.deleteRecursively(); if(persistent) context.deleteDatabase(name) }
    }
    private class LazyBytes(private var remaining: Long, private val closed: () -> Unit = {}) : InputStream() {
        override fun read(): Int = if(remaining-- > 0) 32 else -1
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if(remaining==0L) return -1
            val count=minOf(remaining,length.toLong()).toInt()
            buffer.fill(32,offset,offset+count); remaining-=count; return count
        }
        override fun close()=closed()
    }

    @Test fun inclusiveRealByteBudgetsAndOneByteOverRetainAllMembers() = runBlocking {
        fixture { db,root ->
            val dao=db.collectionTransferDao()
            val store=AndroidCollectionTransferFileStore(root,dao,{ available })
            val job=id()
            val files=store.receive(job,1L,listOf(source { LazyBytes(TransferLimits.MAX_FILE_BYTES) },source { LazyBytes(TransferLimits.MAX_FILE_BYTES) },source { LazyBytes(1L) }))
            assertEquals(listOf("PARSING","PARSING","REJECTED"),files.map { it.phase })
            assertEquals(TransferError.BATCH_TOO_LARGE.name,files.last().error)
            assertEquals(TransferLimits.MAX_BATCH_BYTES+1L,dao.getReceipt(job.value)!!.receivedBytes)
            assertTrue(files.take(2).all { it.bytes==TransferLimits.MAX_FILE_BYTES && it.sha256?.length==64 })
            val over=id()
            val tooLarge=store.receive(over,1L,listOf(source { LazyBytes(TransferLimits.MAX_FILE_BYTES+1L) })).single()
            assertEquals("REJECTED",tooLarge.phase)
            assertEquals(TransferError.FILE_TOO_LARGE.name,tooLarge.error)
            assertEquals(TransferLimits.MAX_FILE_BYTES+1L,tooLarge.bytes)
        }
    }

    @Test fun sourcesAreSequentialAndFailedSiblingKeepsFirstAndReportsAbsentSource() = runBlocking {
        fixture { db,root ->
            val active=AtomicInteger(); val peak=AtomicInteger()
            fun good(label: String)=source(label) {
                val count=active.incrementAndGet(); peak.updateAndGet { maxOf(it,count) }
                LazyBytes(4L) { active.decrementAndGet() }
            }
            val store=AndroidCollectionTransferFileStore(root,db.collectionTransferDao(),{ available })
            val job=id()
            val files=store.receive(job,1L,listOf(good("../../unsafe.csv"),source("bad") { throw FileNotFoundException() },good("third")))
            assertEquals(listOf("PARSING","FAILED_RETRYABLE","PARSING"),files.map { it.phase })
            assertEquals(1,peak.get()); assertEquals(0,active.get())
            assertNull(files[1].sourcePath)
            assertTrue(files.filter { it.phase=="PARSING" }.all { File(root,"collection-transfers/${job.value}/${it.id}/source").isFile })
            assertFalse(File(root.parentFile,"unsafe.csv").exists())
            assertEquals(8L,db.collectionTransferDao().getReceipt(job.value)!!.receivedBytes)
        }
    }

    @Test fun cardinalityAndKnownOwnerAdmissionStopBeforeOpeningAnyStream() = runBlocking {
        fixture { db,root ->
            val opened=AtomicInteger()
            val store=AndroidCollectionTransferFileStore(root,db.collectionTransferDao(),{ available })
            val eleven=(1..11).map { source("uri-$it") { opened.incrementAndGet(); LazyBytes(1L) } }
            try { store.receive(id(),1L,eleven); fail() } catch(failure: TransferStorageException) { assertEquals(TransferError.TOO_MANY_FILES,failure.category) }
            assertEquals(0,opened.get())
            repeat(3) { store.receive(id(),1L,listOf(bytes("1 Plains"))) }
            try { store.receive(id(),1L,listOf(source { opened.incrementAndGet(); LazyBytes(1L) })); fail() } catch(failure: TransferStorageException) { assertEquals(TransferError.SELECTION_LIMIT,failure.category) }
            assertEquals(0,opened.get())
        }
    }

    @Test fun loadingReceiptRemainsNeutralAndBindingNeverClaimsAnotherOwner() = runBlocking {
        fixture { db,root ->
            val dao=db.collectionTransferDao()
            var session: TransferSession=TransferSession.Loading
            val store=AndroidCollectionTransferFileStore(root,dao,{ session })
            val job=id()
            val file=store.receive(job,0L,listOf(bytes("1 Plains"))).single()
            assertNull(dao.getReceipt(job.value)!!.capturedOwner)
            assertNull(dao.getJob(job.value,"account:fixture-source-a"))
            session=available
            assertFalse(dao.bindReceipt(job.value,"account:fixture-source-a",1L,"SAF",1L))
            assertTrue(dao.bindReceipt(job.value,"account:fixture-source-a",1L,"SAF",1L,destinationChosen=true))
            session=TransferSession.Available(TransferOwner.Account("fixture-source-b"),2L)
            try { store.readSource(job,TransferFileId(file.id)) { fail() }; fail() } catch(failure: TransferStorageException) { assertEquals(TransferError.OWNER_CHANGED,failure.category) }
            session=available
            assertNotNull(store.parseSource(job,TransferFileId(file.id)))
            assertEquals("RESOLVING",dao.getFile(job.value,"account:fixture-source-a",file.id)!!.phase)
            val changedBeforeEntry=id()
            store.receive(changedBeforeEntry,0L,listOf(bytes("1 Island")))
            assertNull(dao.getReceipt(changedBeforeEntry.value)!!.capturedOwner)
            assertFalse(dao.bindReceipt(changedBeforeEntry.value,"account:fixture-source-a",1L,"SAF",3L))
            assertTrue(dao.bindReceipt(changedBeforeEntry.value,"account:fixture-source-a",1L,"SAF",3L,destinationChosen=true))
        }
    }

    private class BlockingStream : InputStream() {
        val entered=CountDownLatch(1); val closed=CountDownLatch(1)
        override fun read(): Int {
            entered.countDown()
            while(closed.count>0L) try { closed.await() } catch(_: InterruptedException) { }
            throw IOException()
        }
        override fun read(buffer: ByteArray,offset: Int,length: Int)=read()
        override fun close() { closed.countDown() }
    }

    @Test fun noProgressAndFileDeadlinesCloseAnInterruptIgnoringProvider() = runBlocking {
        fixture { db,root ->
            for(policy in listOf(TransferCopyPolicy(noProgressMillis=150L),TransferCopyPolicy(noProgressMillis=1000L,fileMillis=150L))) {
                val blocked=BlockingStream()
                val store=AndroidCollectionTransferFileStore(root,db.collectionTransferDao(),{ TransferSession.Loading },policy)
                val files=withTimeout(3000L) { store.receive(id(),0L,listOf(source { blocked })) }
                assertTrue(blocked.closed.await(1L,TimeUnit.SECONDS))
                assertEquals("FAILED_RETRYABLE",files.single().phase)
                assertEquals(TransferError.COPY_TIMEOUT.name,files.single().error)
            }
        }
    }

    @Test fun externalCancellationClosesProviderAndPersistsUnreceivedSiblings() = runBlocking {
        fixture { db,root ->
            val blocked=BlockingStream(); val job=id()
            val store=AndroidCollectionTransferFileStore(root,db.collectionTransferDao(),{ TransferSession.Loading })
            val copying=launch(Dispatchers.Default) { store.receive(job,0L,listOf(bytes("1 Plains"),source { blocked },bytes("1 Island"))) }
            assertTrue(withContext(Dispatchers.IO) { blocked.entered.await(3L,TimeUnit.SECONDS) })
            withTimeout(3000L) { copying.cancelAndJoin() }
            assertTrue(blocked.closed.await(1L,TimeUnit.SECONDS))
            val files=db.collectionTransferDao().getReceiptFiles(job.value)
            assertEquals(listOf("PARSING","FAILED_RETRYABLE","FAILED_RETRYABLE"),files.map { it.phase })
            assertNull(files.last().sourcePath)
            assertTrue(copying.isCancelled)
        }
    }

    @Test fun pendingOrFailedCloseNeverOverlapsTheNextProvider() = runBlocking {
        fixture { db,root ->
            for(failClose in listOf(false,true)) {
                val closeEntered=CompletableDeferred<Unit>(); val release=CountDownLatch(1)
                val nextOpened=AtomicInteger()
                val bad=source { object : InputStream() {
                    override fun read(): Int=throw IOException()
                    override fun close() {
                        closeEntered.complete(Unit)
                        release.await()
                        if(failClose) throw IOException()
                    }
                } }
                val store=AndroidCollectionTransferFileStore(root,db.collectionTransferDao(),{ TransferSession.Loading })
                val copying=async(Dispatchers.Default) { store.receive(id(),0L,listOf(bad,source { nextOpened.incrementAndGet(); LazyBytes(1L) })) }
                withTimeout(3000L) { closeEntered.await() }
                assertEquals(0,nextOpened.get())
                release.countDown()
                val files=withTimeout(3000L) { copying.await() }
                assertEquals(if(failClose)0 else 1,nextOpened.get())
                if(failClose) assertEquals(listOf("FAILED_RETRYABLE","FAILED_RETRYABLE"),files.map { it.phase })
            }
        }
    }

    @Test fun receiptDeadlineClosesCurrentSourceAndRecordsUnreceivedMembers() = runBlocking {
        fixture { db,root ->
            val blocked=BlockingStream()
            val store=AndroidCollectionTransferFileStore(root,db.collectionTransferDao(),{ TransferSession.Loading },TransferCopyPolicy(noProgressMillis=2000L,fileMillis=2000L,receiptMillis=500L))
            val files=withTimeout(3000L) { store.receive(id(),0L,listOf(source { blocked },bytes("1 Plains"))) }
            assertTrue(blocked.closed.await(1L,TimeUnit.SECONDS))
            assertEquals(listOf("FAILED_RETRYABLE","FAILED_RETRYABLE"),files.map { it.phase })
        }
    }

    @Test fun enospcIsTypedAndPreservesCompletedSibling() = runBlocking {
        fixture { db,root ->
            var outputs=0
            val store=AndroidCollectionTransferFileStore(root,db.collectionTransferDao(),{ available },openOutput={ file ->
                outputs++
                if(outputs==1) FileOutputStream(file) else object : FileOutputStream(file) {
                    override fun write(buffer: ByteArray,offset: Int,length: Int) { throw IOException(ErrnoException("write",OsConstants.ENOSPC)) }
                }
            })
            val files=store.receive(id(),1L,listOf(bytes("1 Plains"),bytes("1 Island")))
            assertEquals("PARSING",files.first().phase)
            assertEquals(TransferError.NO_SPACE.name,files.last().error)
            assertEquals("FAILED_RETRYABLE",files.last().phase)
        }
    }

    @Test fun renameGapRecoversWithoutReopeningProviderAndPartNeverBecomesReady() = runBlocking {
        fixture(persistent=true) { db,root ->
            var dao=db.collectionTransferDao(); val job=id()
            val first=UUID.randomUUID().toString(); val second=UUID.randomUUID().toString()
            assertTrue(dao.createReceipt(CollectionTransferReceiptEntity(job.value,1L,"account:fixture-source-a",createdAt=1L),listOf(CollectionTransferFileEntity(first,job.value,fileOrder=0),CollectionTransferFileEntity(second,job.value,fileOrder=1))))
            val data="1 Plains\r\n".toByteArray()
            val hash=MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }
            val firstDir=File(root,"collection-transfers/${job.value}/$first"); firstDir.mkdirs(); File(firstDir,"source").writeBytes(data)
            assertTrue(dao.recordReception(job.value,first,data.size.toLong(),"RECEIVING",hash,"${job.value}/$first/source",null))
            val secondDir=File(root,"collection-transfers/${job.value}/$second"); secondDir.mkdirs(); File(secondDir,"source.part").writeBytes(data)
            val name=db.openHelper.databaseName!!
            db.close()
            val reopened=Room.databaseBuilder(context,MtgDatabase::class.java,name).build()
            try {
                dao=reopened.collectionTransferDao()
                val store=AndroidCollectionTransferFileStore(root,dao,{ available })
                val recovered=store.receive(job,1L,listOf(source { fail("Recovery must not open provider"); LazyBytes(0L) }))
                assertEquals(listOf("PARSING","FAILED_RETRYABLE"),recovered.map { it.phase })
                assertArrayEquals(data,File(firstDir,"source").readBytes())
                assertTrue(File(secondDir,"source.part").isFile)
                assertTrue(dao.bindReceipt(job.value,"account:fixture-source-a",1L,"SAF",2L))
                assertNotNull(store.parseSource(job,TransferFileId(first)))
            } finally { reopened.close() }
        }
    }

    @Test fun strictUtf8AcrossBlocksRejectsMalformedSuffixAndOrdinalReplayIsIdempotent() = runBlocking {
        fixture { db,root ->
            val dao=db.collectionTransferDao()
            val store=AndroidCollectionTransferFileStore(root,dao,{ available })
            val job=id()
            val text="\uFEFF1 Café 😀\r\n"+(1..501).joinToString("\n") { "1 Plains" }
            val files=store.receive(job,1L,listOf(bytes(text),source { ByteArrayInputStream(("1 Island\n".repeat(300)).toByteArray()+byteArrayOf(0xC3.toByte())) }))
            assertTrue(dao.bindReceipt(job.value,"account:fixture-source-a",1L,"SAF",1L))
            val valid=TransferFileId(files.first().id)
            var staged=0
            val partial=mutableListOf<com.mmg.manahub.core.data.local.entity.CollectionImportRowEntity>()
            try {
                store.readSource(job,valid) { input -> StreamingCollectionImportParser().parse(input,object : TransferRecordSink {
                    override suspend fun record(record: TransferParsedRecord) {
                        val line=record.line!!
                        partial+=com.mmg.manahub.core.data.local.entity.CollectionImportRowEntity(job.value,valid.value,record.ordinal,record.range.start,record.range.endExclusive,record.kind.name,name=line.name,quantity=line.quantity.toLong())
                        if(partial.size==200) { assertTrue(dao.stageParserRows(job.value,"account:fixture-source-a",valid.value,partial)); staged=200; throw CancellationException("Fixture checkpoint") }
                    }
                    override suspend fun completed(summary: TransferParseSummary) { fail() }
                    override suspend fun rejected(error: TransferParseError) { fail() }
                }) }
                fail()
            } catch(_: CancellationException) { }
            assertEquals(200,staged)
            assertEquals(200L,dao.getFile(job.value,"account:fixture-source-a",valid.value)!!.parseOrdinal)
            val summary=store.parseSource(job,valid)!!
            assertEquals(502L,summary.records)
            assertEquals("Café 😀",dao.rowPage(job.value,"account:fixture-source-a",valid.value,0L).first().name)
            val invalid=TransferFileId(files.last().id)
            assertNull(store.parseSource(job,invalid))
            assertEquals("REJECTED",dao.getFile(job.value,"account:fixture-source-a",invalid.value)!!.phase)
            assertEquals(TransferError.INVALID_ENCODING.name,dao.getFile(job.value,"account:fixture-source-a",invalid.value)!!.error)
            assertEquals("RESOLVING",dao.getFile(job.value,"account:fixture-source-a",valid.value)!!.phase)
        }
    }
}
