package com.mmg.manahub.feature.collection

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mmg.manahub.core.data.local.MtgDatabase
import com.mmg.manahub.core.data.local.entity.*
import com.mmg.manahub.core.domain.collection.transfer.*
import com.mmg.manahub.core.domain.repository.CardLookupIdentifier
import com.mmg.manahub.feature.collection.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.File
import java.util.UUID
import androidx.lifecycle.ViewModelStore
import com.mmg.manahub.core.domain.auth.*
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.model.QueuedCard
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.data.local.mapper.toDomainCard
import com.mmg.manahub.feature.collection.presentation.importexport.DurableTransferViewModel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import java.lang.reflect.Proxy

@RunWith(AndroidJUnit4::class)
class CollectionTransferCoordinatorTest {
    @Test fun terminalEventsDrainAllPagesAndDoNotReplayAcknowledgedSignals()=runBlocking<Unit> { fixture { h ->
        repeat(51) {
            val job=h.ready()
            h.db.openHelper.writableDatabase.execSQL("UPDATE collection_transfer_jobs SET phase='COMPLETED' WHERE id=?",arrayOf(job.value))
        }
        h.db.openHelper.writableDatabase.execSQL("UPDATE collection_transfer_jobs SET collection_event_pending=1,applied_entries=1")
        assertEquals(51,h.collection.reconcilePendingEvents(owner))
        assertEquals(51,h.events)
        assertEquals(0,h.collection.reconcilePendingEvents(owner))
        assertTrue(h.db.collectionTransferDao().pendingCollectionEventPage(key,"").isEmpty())
    } }
    @Test fun eventFailureDoesNotStarveLaterTerminalPages()=runBlocking<Unit> { fixture { h ->
        repeat(51) {
            val job=h.ready()
            h.db.openHelper.writableDatabase.execSQL("UPDATE collection_transfer_jobs SET phase='COMPLETED' WHERE id=?",arrayOf(job.value))
        }
        h.db.openHelper.writableDatabase.execSQL("UPDATE collection_transfer_jobs SET collection_event_pending=1,applied_entries=1")
        var fail=true; var emitted=0
        val executor=RoomTransferCollectionExecutor(h.db,h.gate,{h.now},{ if(fail){fail=false;error("Injected event failure")};emitted++ },matchesObservedOwner={it==h.observed})
        assertEquals(50,executor.reconcilePendingEvents(owner))
        assertEquals(50,emitted)
        assertEquals(1,h.db.collectionTransferDao().pendingCollectionEventPage(key,"").size)
        assertEquals(1,executor.reconcilePendingEvents(owner))
        assertEquals(0,executor.reconcilePendingEvents(owner))
    } }
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val owner=TransferOwner.Account("coordinator-fixture")
    private val key="account:coordinator-fixture"
    private fun uuid()=UUID.randomUUID().toString()
    private class Scheduler: TransferWorkScheduler {
        val requests=java.util.concurrent.CopyOnWriteArrayList<TransferJobId>(); val cancelled=mutableListOf<TransferJobId>(); var wishlistRequests=0
        val requestCount=MutableStateFlow(0); val watched=MutableStateFlow(0)
        val finished=MutableStateFlow(false)
        override suspend fun enqueue(id: TransferJobId) { requests+=id; requestCount.value=requests.size }
        override suspend fun cancel(id: TransferJobId) { cancelled+=id }
        override suspend fun enqueueWishlist() { wishlistRequests++ }
        override suspend fun cancelWishlist()=Unit
        override fun observeFinished(id: TransferJobId?): Flow<Boolean> { watched.value++; return finished }
    }
    private class Gateway: TransferResolutionGateway {
        var cache=true; var failure=TransferLookupFailure.OFFLINE; var requests=0; var successful=false; val sizes=mutableListOf<Int>()
        override suspend fun cached(identifier: CardLookupIdentifier)=if(cache)TransferPrinting("printing-${identifier.name}",identifier.name ?: "Fixture","set","1") else null
        override suspend fun lookup(identifiers: List<CardLookupIdentifier>): TransferLookupBatch {
            requests++; sizes+=identifiers.size
            return if(successful)TransferLookupBatch(identifiers.map { TransferPrinting("printing-${it.name}",it.name!!,"set","1") },emptyList()) else TransferLookupBatch(emptyList(),emptyList(),failure)
        }
        override suspend fun fallbackName(name: String)=error("No fallback expected")
    }
    private inner class Harness(val db: MtgDatabase,val directory: File) {
        val gate=TransferSessionGate(); var observed: TransferOwner?=owner; var now=1000L; var events=0
        val scheduler=Scheduler(); val gateway=Gateway()
        fun session()=gate.currentSession.takeIf { (it as? TransferSession.Available)?.owner==observed } ?: TransferSession.Loading
        val files=AndroidCollectionTransferFileStore(directory,db.collectionTransferDao(),::session)
        val repository=RoomCollectionTransferRepository(db,gate,{ it==observed },{ now },scheduler)
        val commits=mutableListOf<Int>(); var failIncrement=false; var incrementAttempts=0
        val collection=RoomTransferCollectionExecutor(db,gate,{ now },{ events++ },checkpoint={ point,count ->
            if(point==TransferApplyCheckpoint.AFTER_COMMIT)commits+=count
            if(point==TransferApplyCheckpoint.AFTER_INCREMENT) { incrementAttempts++; if(failIncrement)error("Injected local write failure") }
        },matchesObservedOwner={ it==observed })
        val wishlist=RoomTransferWishlistExecutor(db,gate,{ now },{ it==observed })
        val delivery=RoomTransferWishlistDeliveryStore(db,gate,{ it==observed },{ now })
        var remoteCalls=0
        val sync=TransferWishlistSync(gate,delivery,TransferWishlistDeliveryGateway { _,_ -> remoteCalls++; error("offline") },{ it==observed })
        val coordinator=RoomCollectionTransferCoordinator(db,gate,{ it==observed },files,DurableTransferResolver(RoomTransferResolutionStore(db.collectionTransferDao(),::session),gateway,{ now }),RoomTransferReviewBuilder(db.collectionTransferDao(),::session,{ now }),collection,wishlist,sync,scheduler,{ now })
        suspend fun receive(content: String="2 Fixture\n3 Other\n"): TransferJobId {
            val job=TransferJobId(uuid()); val generation=(gate.currentSession as TransferSession.Available).generation
            files.receive(job,generation,listOf(object: TransferInputSource { override val identity="fixture"; override fun open()=ByteArrayInputStream(content.toByteArray()) }))
            assertEquals(TransferMutationResult.Accepted,repository.bindReceipt(job,owner,generation,TransferOrigin.SAF)); return job
        }
        suspend fun ready(content: String="2 Fixture\n3 Other\n",phase: String="REVIEW_READY"): TransferJobId {
            val job=receive(content); assertEquals(TransferWorkResult.SUCCESS,RunTransferWork(coordinator).run(job)); assertEquals(phase,db.collectionTransferDao().getJob(job.value,key)!!.phase); return job
        }
    }
    private suspend fun fixture(block: suspend (Harness)->Unit) {
        val directory=File(context.cacheDir,"coordinator-${uuid()}").apply { mkdirs() }
        val db=Room.inMemoryDatabaseBuilder(context,MtgDatabase::class.java).build()
        try { val h=Harness(db,directory); h.gate.changeOwner(owner); block(h) } finally { db.close(); directory.deleteRecursively() }
    }
    private fun source(name: String,text: String)=object: TransferInputSource { override val identity=name;override fun open()=ByteArrayInputStream(text.toByteArray()) }
    private fun metadata()=CardEntity(scryfallId="printing-Fixture",name="Fixture",printedName=null,lang="en",manaCost=null,cmc=1.0,colors="[]",colorIdentity="[]",typeLine="Artifact",printedTypeLine=null,oracleText=null,printedText=null,keywords="[]",power=null,toughness=null,loyalty=null,setCode="set",setName="Set",collectorNumber="1",rarity="common",releasedAt="2026-01-01",imageNormal=null,imageArtCrop=null,imageBackNormal=null,priceUsd=null,priceUsdFoil=null,priceEur=null,priceEurFoil=null,legalityStandard="legal",legalityPioneer="legal",legalityModern="legal",legalityCommander="legal",flavorText=null,artist=null,scryfallUri="")
    @Suppress("UNCHECKED_CAST")
    private fun <T> fake(type: Class<T>,property: String,value: Any): T=Proxy.newProxyInstance(type.classLoader,arrayOf(type)) { _,method,_ -> if(method.name==property)value else error("Unexpected fixture dependency call") } as T
    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun withReview(h: Harness,job: TransferJobId,block: suspend (DurableTransferViewModel)->Unit) {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val store=ViewModelStore()
        try {
            h.db.cardDao().upsert(metadata())
            val authState=MutableStateFlow<SessionState>(SessionState.Authenticated(AuthUser(owner.id,null,null,null,null,"fixture")))
            val guest=VerifiedTransferGuestIdentity(context)
            val observer=TransferAuthSessionObserver(authState,guest,h.gate,h.db.collectionTransferDao())
            val vm=DurableTransferViewModel(job,h.repository,h.db,h.gate,observer,fake(AuthRepository::class.java,"getSessionState",authState),fake(CardRepository::class.java,"unused",Unit),h.files,context.contentResolver,AndroidTransferReportWriter(h.db,h.gate,observer,context.contentResolver,h.directory))
            store.put("review",vm)
            withTimeout(10_000L) { vm.state.first { it.entries.isNotEmpty() } }
            block(vm)
        } finally { store.clear();Dispatchers.resetMain() }
    }
    @Test fun durableReviewAttributeEditPreservesTenThousandAndRejectsOldDialogVersion()=runBlocking<Unit> { fixture { h ->
        val job=h.ready("10000 Fixture\n")
        withReview(h,job) { vm ->
            val original=vm.state.value.summary!!;val entry=vm.state.value.entries.single();val queued=vm.state.value.cards.single()
            vm.update(queued.copy(isFoil=true,language="ja"))
            withTimeout(10_000L) { vm.state.first { it.entries.singleOrNull()?.language=="ja" } }
            assertEquals(10000L,vm.state.value.entries.single().quantity)
            vm.action(TransferDestination.COLLECTION,entry.id,original.generation,original.payloadVersion,entry.version,false,false)
            withTimeout(10_000L) { vm.state.first { it.error!=null } }
            assertNull(h.db.collectionTransferDao().executableAction(job.value,key))
            assertEquals(10000L,h.repository.readPage(job,owner,null).entries.single().quantity)
        }
    } }
    @Test fun overflowReviewNeverWritesPresentationClampAndRequiresExplicitCorrection()=runBlocking<Unit> { fixture { h ->
        val job=h.ready("2147483647 Fixture\n1 Fixture\n","REVIEW_REQUIRED")
        withReview(h,job) { vm ->
            val original=vm.state.value.entries.single();assertEquals(2147483648L,original.quantity)
            val queued=vm.state.value.cards.single()
            vm.adjust(queued,-1);vm.update(queued.copy(isFoil=true))
            withTimeout(10_000L) { vm.state.first { it.error!=null } }
            assertEquals(2147483648L,h.repository.readPage(job,owner,null).entries.single().quantity)
            vm.correctQuantity(original.id,original.version,10L)
            withTimeout(10_000L) { vm.state.first { it.entries.singleOrNull()?.quantity==10L } }
            assertEquals(2147483648L,h.repository.readProvenance(job,owner,original.id).sumOf { it.copies })
        }
        val excluded=h.ready("2147483647 Fixture\n1 Fixture\n\n","REVIEW_REQUIRED")
        withReview(h,excluded) { vm ->
            vm.exclude(vm.state.value.cards.single())
            withTimeout(10_000L) { vm.state.first { it.entries.singleOrNull()?.excluded==true } }
            assertEquals(2147483648L,h.repository.readPage(excluded,owner,null).entries.single().quantity)
        }
    } }
    @Test fun deliveryFingerprintIgnoresNamesAndOrderButPreservesMultiplicityAndOwner()=runBlocking<Unit> { fixture { h ->
        val generation=(h.gate.currentSession as TransferSession.Available).generation
        val first=TransferJobId(uuid());h.files.receive(first,generation,listOf(source("first","2 Fixture\n"),source("second","3 Other\n")))
        assertEquals(TransferMutationResult.Accepted,h.repository.bindReceipt(first,owner,generation,TransferOrigin.SHARE))
        val second=TransferJobId(uuid());h.files.receive(second,generation,listOf(source("renamed-other","3 Other\n"),source("renamed-first","2 Fixture\n")))
        assertEquals(TransferMutationResult.AlreadyReceived(first),h.repository.bindReceipt(second,owner,generation,TransferOrigin.SHARE))
        assertEquals(TransferMutationResult.AlreadyReceived(first),h.repository.bindReceipt(second,owner,generation,TransferOrigin.SHARE))
        assertNull(h.db.collectionTransferDao().getJob(second.value,key))
        val repeated=TransferJobId(uuid());h.files.receive(repeated,generation,listOf(source("a","2 Fixture\n"),source("b","2 Fixture\n")))
        assertEquals(TransferMutationResult.Accepted,h.repository.bindReceipt(repeated,owner,generation,TransferOrigin.SHARE))
        val other=TransferOwner.Account("another-fixture");h.observed=other;h.gate.changeOwner(other)
        val otherGeneration=(h.gate.currentSession as TransferSession.Available).generation
        val third=TransferJobId(uuid());h.files.receive(third,otherGeneration,listOf(source("third","2 Fixture\n"),source("fourth","3 Other\n")))
        assertEquals(TransferMutationResult.Accepted,h.repository.bindReceipt(third,other,otherGeneration,TransferOrigin.SHARE))
    } }
    @Test fun replacementKeepsCompleteSiblingAndCountsNewBytesBeforeParsingAgain()=runBlocking<Unit> { fixture { h ->
        val generation=(h.gate.currentSession as TransferSession.Available).generation;val job=TransferJobId(uuid())
        val received=h.files.receive(job,generation,listOf(source("good","2 Fixture\n"),object: TransferInputSource { override val identity="failed";override fun open(): java.io.InputStream=throw java.io.IOException("fixture") }))
        assertEquals(TransferMutationResult.Accepted,h.repository.bindReceipt(job,owner,generation,TransferOrigin.SAF))
        val failed=received.last();val good=received.first();val oldBytes=h.db.collectionTransferDao().getReceipt(job.value)!!.receivedBytes
        assertTrue(h.files.replaceSource(job,owner,0L,TransferFileId(failed.id),source("reselected","3 Other\n")))
        val current=h.db.collectionTransferDao().currentFiles(job.value,key)
        assertEquals(good.id,current.first().id);assertNotEquals(failed.id,current.last().id)
        assertTrue(h.db.collectionTransferDao().getFile(job.value,key,failed.id)!!.retired)
        assertEquals(oldBytes+"3 Other\n".toByteArray().size,h.db.collectionTransferDao().getReceipt(job.value)!!.receivedBytes)
        assertEquals("PARSING",h.db.collectionTransferDao().getJob(job.value,key)!!.phase)
        assertEquals(TransferWorkResult.SUCCESS,RunTransferWork(h.coordinator).run(job))
        assertEquals(5L,h.repository.readPage(job,owner,null).entries.sumOf { it.quantity })
    } }
    @Test fun reportStreamsEverySourceRecordBeyondPreviewLimitAndOwnerChangesRejectOutput()=runBlocking<Unit> { fixture { h ->
        val job=h.ready((1..1001).joinToString("\n",postfix="\n") { "1 Fixture" })
        val authState=MutableStateFlow<SessionState>(SessionState.Authenticated(AuthUser(owner.id,null,null,null,null,"fixture")))
        val observer=TransferAuthSessionObserver(authState,VerifiedTransferGuestIdentity(context),h.gate,h.db.collectionTransferDao())
        val output=java.io.ByteArrayOutputStream()
        val writer=AndroidTransferReportWriter(h.db,h.gate,observer,context.contentResolver,h.directory) { output }
        assertEquals(1001L,writer.write(job,owner,android.net.Uri.parse("content://fixture/report")))
        assertEquals(1004,output.toString("UTF-8").lineSequence().filter { it.isNotEmpty() }.count())
        assertTrue(output.toString("UTF-8").contains("\"1001\",\"DATA\""))
        authState.value=SessionState.Authenticated(AuthUser("other",null,null,null,null,"fixture"))
        try { writer.write(job,owner,android.net.Uri.parse("content://fixture/other"));fail("Owner change must reject") } catch(_: TransferReadException) { }
    } }
    @Test fun discardConsentRejectsEditedPayloadNewGenerationAndOwnerAba()=runBlocking<Unit> { fixture { h ->
        val job=h.ready()
        withReview(h,job) { vm ->
            val beforeEdit=vm.captureDiscard()!!
            val queued=vm.state.value.cards.first()
            vm.update(queued.copy(isFoil=true))
            withTimeout(10_000L) { vm.state.first { it.summary!!.payloadVersion>beforeEdit.payloadVersion } }
            val afterEdit=h.repository.readPage(job,owner,null).entries
            vm.discard(beforeEdit)
            withTimeout(10_000L) { vm.state.first { it.error!=null } }
            assertEquals(afterEdit,h.repository.readPage(job,owner,null).entries)
            vm.clearError()
            val beforeGeneration=vm.captureDiscard()!!
            val file=vm.state.value.summary!!.files.single()
            h.repository.selectFile(job,owner,file.id,beforeGeneration.generation,false)
            val excluded=h.repository.observeSummary(job,owner).filterNotNull().first()
            h.repository.selectFile(job,owner,file.id,excluded.generation,true)
            RunTransferWork(h.coordinator).run(job)
            withTimeout(10_000L) { vm.state.first { it.summary!!.generation>beforeGeneration.generation } }
            val rebuilt=h.repository.readPage(job,owner,null).entries
            vm.discard(beforeGeneration)
            withTimeout(10_000L) { vm.state.first { it.error!=null } }
            assertEquals(rebuilt,h.repository.readPage(job,owner,null).entries)
            vm.clearError()
            val beforeAba=vm.captureDiscard()!!
            h.gate.changeOwner(TransferOwner.Account("other"));h.gate.changeOwner(owner)
            withTimeout(10_000L) { vm.state.first { it.summary!=null && it.entries.isNotEmpty() } }
            vm.discard(beforeAba)
            withTimeout(10_000L) { vm.state.first { it.error!=null } }
            assertEquals(rebuilt,h.repository.readPage(job,owner,null).entries)
            assertNotEquals("DISCARDED",h.db.collectionTransferDao().getJob(job.value,key)!!.phase)
        }
    } }
    @Test fun reportReconstructsLongMultilineUnknownFieldsAndDeclaresMissingSource()=runBlocking<Unit> { fixture { h ->
        val originals=(1..205).map { index -> "Fixture,0,set,1,normal,NM,en,\"${"x".repeat(400)}-$index\nquoted \"\"value\"\"\"" }
        val content="Name,Quantity,Set code,Collector number,Foil,Condition,Language,Unknown field\n"+originals.joinToString("\n",postfix="\n")
        val job=h.receive(content);RunTransferWork(h.coordinator).run(job)
        val authState=MutableStateFlow<SessionState>(SessionState.Authenticated(AuthUser(owner.id,null,null,null,null,"fixture")))
        val observer=TransferAuthSessionObserver(authState,VerifiedTransferGuestIdentity(context),h.gate,h.db.collectionTransferDao())
        fun csv(text: String): List<List<String>> {
            val records=mutableListOf<List<String>>();var row=mutableListOf<String>();val field=StringBuilder();var quoted=false;var index=0
            while(index<text.length) { val char=text[index++];when {
                char=='"' -> if(quoted && index<text.length && text[index]=='"') { field.append('"');index++ } else quoted=!quoted
                char==',' && !quoted -> { row+=field.toString();field.setLength(0) }
                char=='\n' && !quoted -> { row+=field.toString();records+=row;row=mutableListOf();field.setLength(0) }
                else -> field.append(char)
            } };return records
        }
        val output=java.io.ByteArrayOutputStream()
        val writer=AndroidTransferReportWriter(h.db,h.gate,observer,context.contentResolver,h.directory) { output }
        val count=writer.write(job,owner,android.net.Uri.parse("content://fixture/report"))
        val rows=csv(output.toString("UTF-8"));val data=rows.filter { it[5]=="DATA" }
        assertEquals(205,data.size);assertTrue(count>=205L)
        assertEquals(originals,data.map { it[15] });assertTrue(data.all { it[16]=="COMPLETE" && it[13].length<=300 })
        val file=h.db.collectionTransferDao().fileInventoryPage(job.value,key,"").single()
        assertTrue(File(h.directory,"collection-transfers/${job.value}/${file.id}/source").delete())
        output.reset();writer.write(job,owner,android.net.Uri.parse("content://fixture/report"))
        val missing=csv(output.toString("UTF-8"));assertTrue(missing.filter { it[5] in setOf("FILE","DATA") }.all { it[16]=="UNAVAILABLE" && it[15].isEmpty() })
        val path=File(h.directory,"collection-transfers/${job.value}/${file.id}/source")
        File(path.parentFile,"source.part").writeText("partial fixture")
        output.reset();writer.write(job,owner,android.net.Uri.parse("content://fixture/report"))
        assertTrue(csv(output.toString("UTF-8")).filter { it[5] in setOf("FILE","DATA") }.all { it[16]=="INCOMPLETE" && it[15].isEmpty() })
        path.writeText(content.replace('x','y'))
        assertEquals(file.bytes,path.length())
        output.reset();writer.write(job,owner,android.net.Uri.parse("content://fixture/report"))
        assertTrue(csv(output.toString("UTF-8")).filter { it[5] in setOf("FILE","DATA") }.all { it[16]=="INCOMPLETE" && it[15].isEmpty() })
        val rejectedText="Name,Quantity,Set code,Collector number,Foil,Condition,Language,Unknown field\nFixture,1,set,1,normal,NM,en,first\nFixture,1,set,1,normal,NM,en,\"unterminated\n"
        val rejected=h.receive(rejectedText);RunTransferWork(h.coordinator).run(rejected)
        output.reset();writer.write(rejected,owner,android.net.Uri.parse("content://fixture/rejected"))
        val rejectedFile=csv(output.toString("UTF-8")).single { it[5]=="FILE" }
        assertEquals("SCAN_INCOMPLETE",rejectedFile[6]);assertEquals("COMPLETE",rejectedFile[16]);assertEquals(rejectedText,rejectedFile[15])
    } }
    @Test fun oldEditDecisionCannotAcceptReplacementFromSecondMembershipChange()=runBlocking<Unit> { fixture { h ->
        val job=TransferJobId(uuid());val session=h.gate.currentSession as TransferSession.Available
        h.files.receive(job,session.generation,listOf(source("first","2 Fixture\n"),source("second","3 Fixture\n")))
        assertEquals(TransferMutationResult.Accepted,h.repository.bindReceipt(job,owner,session.generation,TransferOrigin.SAF))
        RunTransferWork(h.coordinator).run(job)
        withReview(h,job) { vm ->
            vm.update(vm.state.value.cards.single().copy(quantity=7))
            withTimeout(10_000L) { vm.state.first { it.entries.singleOrNull()?.quantity==7L } }
            val summary=h.repository.observeSummary(job,owner).filterNotNull().first()
            h.repository.selectFile(job,owner,summary.files.last().id,summary.generation,false);RunTransferWork(h.coordinator).run(job)
            val original=h.repository.readDecisions(job,owner,"").single()
            val revised=h.repository.observeSummary(job,owner).filterNotNull().first()
            h.repository.selectFile(job,owner,revised.files.first().id,revised.generation,false);RunTransferWork(h.coordinator).run(job)
            val replacement=h.repository.readDecisions(job,owner,"").single()
            assertEquals(original.entryId,replacement.entryId);assertTrue(replacement.generation>original.generation)
            withTimeout(10_000L) { vm.state.first { it.summary?.generation==replacement.generation } }
            vm.decision(original,TransferReviewDecision.DISMISS_REMOVED)
            withTimeout(10_000L) { vm.state.first { it.error!=null } }
            assertEquals(listOf(replacement),h.repository.readDecisions(job,owner,""))
        }
    } }
    @Test fun explicitBulkCollectionFollowUpRetainsCompletedWishlistWithoutReplayingIt()=runBlocking<Unit> { fixture { h ->
        val job=h.ready();val initial=h.repository.observeSummary(job,owner).filterNotNull().first()
        h.repository.chooseDestination(job,owner,initial.generation,initial.intentRevision,TransferDestination.WISHLIST)
        val selected=h.repository.observeSummary(job,owner).filterNotNull().first()
        val wishlist=TransferActionId(uuid());assertEquals(TransferMutationResult.Accepted,h.repository.confirmAction(owner,TransferActionRequest(wishlist,job,selected.generation,TransferDestination.WISHLIST,TransferActionScope.DestinationSelection(selected.intentRevision))))
        RunTransferWork(h.coordinator).run(job)
        val retained=h.repository.observeSummary(job,owner).filterNotNull().first()
        assertEquals(5L,retained.wishlistCompletedCopies);assertEquals(0L,retained.appliedCopies)
        assertEquals(TransferMutationResult.Accepted,h.repository.reopenWishlistSelection(job,owner,retained.generation,retained.payloadVersion))
        assertTrue(h.repository.readPage(job,owner,null).entries.all { it.destination==TransferDestination.NONE })
        assertTrue(h.repository.reopenWishlistSelection(job,owner,retained.generation,retained.payloadVersion) is TransferMutationResult.Rejected)
        val reopened=h.repository.observeSummary(job,owner).filterNotNull().first()
        h.repository.chooseDestination(job,owner,reopened.generation,reopened.intentRevision,TransferDestination.COLLECTION)
        val chosen=h.repository.observeSummary(job,owner).filterNotNull().first()
        val collection=TransferActionId(uuid());h.repository.confirmAction(owner,TransferActionRequest(collection,job,chosen.generation,TransferDestination.COLLECTION,TransferActionScope.DestinationSelection(chosen.intentRevision)))
        RunTransferWork(h.coordinator).run(job)
        assertEquals(5L,h.repository.readAction(owner,collection).completedCopies);assertEquals(5L,h.repository.readAction(owner,wishlist).completedCopies)
        assertEquals(5L,h.db.localWishlistDao().observeAll(owner.id).first().sumOf { it.quantity.toLong() })
    } }
    @Test fun cachePreparationAndLocalCommandWorkOfflineWithoutForcingNone()=runBlocking<Unit> { fixture { h ->
        val job=h.ready(); assertEquals(0,h.gateway.requests)
        val entry=h.repository.readPage(job,owner,null).entries.first()
        assertEquals(TransferMutationResult.Accepted,h.repository.editPendingEntry(job,owner,0L,entry.copy(destination=TransferDestination.COLLECTION)))
        val command=TransferActionId(uuid()); assertEquals(TransferMutationResult.Accepted,h.repository.confirmAction(owner,TransferActionRequest(command,job,0L,TransferDestination.COLLECTION,TransferActionScope.Entry(entry.id,1L))))
        h.gateway.cache=false
        assertEquals(TransferWorkResult.SUCCESS,RunTransferWork(h.coordinator).run(job)); assertEquals(TransferWorkResult.SUCCESS,RunTransferWork(h.coordinator).run(job))
        assertEquals(0,h.gateway.requests); assertEquals(1,h.events); assertEquals(1L,h.db.collectionTransferDao().repositoryTotals(job.value,key).pendingEntries)
        assertEquals("COMPLETED",h.db.collectionTransferDao().action(command.value,key)!!.phase)
    } }
    @Test fun cooldownDoesNotCountAndFiveRealFailuresRequireManualResume()=runBlocking<Unit> { fixture { h ->
        h.gateway.cache=false; val job=h.receive()
        assertEquals(TransferWorkResult.RETRY,RunTransferWork(h.coordinator).run(job)); assertEquals(0,h.db.collectionTransferDao().getJob(job.value,key)!!.networkFailures)
        assertEquals(TransferWorkResult.RETRY,RunTransferWork(h.coordinator).run(job)); assertEquals(1,h.gateway.requests)
        h.gateway.failure=TransferLookupFailure.RETRYABLE
        repeat(5) { h.now+=2000L; RunTransferWork(h.coordinator).run(job) }
        assertEquals("FAILED_RETRYABLE",h.db.collectionTransferDao().getJob(job.value,key)!!.phase); assertEquals(5,h.db.collectionTransferDao().getJob(job.value,key)!!.networkFailures)
        val requests=h.gateway.requests; assertEquals(TransferWorkResult.SUCCESS,RunTransferWork(h.coordinator).run(job)); assertEquals(requests,h.gateway.requests)
        assertEquals(TransferMutationResult.Accepted,h.repository.resume(job,owner)); assertEquals(0,h.db.collectionTransferDao().getJob(job.value,key)!!.networkFailures); assertEquals(job,h.scheduler.requests.last())
    } }
    @Test fun startupOwnerReturnResumesCheckpointsButNeverUserPause()=runBlocking<Unit> { fixture { h ->
        val job=h.receive(); assertEquals(TransferMutationResult.Accepted,h.repository.pause(job,owner)); val before=h.scheduler.requests.size
        h.coordinator.reconcile(owner); assertEquals(before,h.scheduler.requests.size)
        assertEquals(TransferMutationResult.Accepted,h.repository.resume(job,owner)); h.observed=TransferOwner.Account("other")
        h.gate.changeOwner(h.observed) { h.db.collectionTransferDao().pauseOwnerJobs(key) }
        assertEquals(TransferWorkResult.SUCCESS,RunTransferWork(h.coordinator).run(job)); assertEquals("PAUSED_OWNER",h.db.collectionTransferDao().getJob(job.value,key)!!.phase)
        h.observed=owner; h.gate.changeOwner(owner); h.coordinator.reconcile(owner)
        assertEquals("PARSING",h.db.collectionTransferDao().getJob(job.value,key)!!.phase); assertEquals(job,h.scheduler.requests.last())
        val dao=h.db.collectionTransferDao(); repeat(2) { dao.waitPreparation(job.value,key,20_000L,true) }
        h.observed=TransferOwner.Account("other"); h.gate.changeOwner(h.observed) { dao.pauseOwnerJobs(key) }; h.observed=owner; h.gate.changeOwner(owner); h.coordinator.reconcile(owner)
        val retained=dao.getJob(job.value,key)!!; assertEquals("WAITING_NETWORK",retained.phase); assertEquals(2,retained.networkFailures); assertEquals(20_000L,retained.nextAttemptAt)
        repeat(3) { dao.waitPreparation(job.value,key,20_000L,true) }; val scheduled=h.scheduler.requests.size
        h.observed=TransferOwner.Account("other"); h.gate.changeOwner(h.observed) { dao.pauseOwnerJobs(key) }; h.observed=owner; h.gate.changeOwner(owner); h.coordinator.reconcile(owner)
        assertEquals("FAILED_RETRYABLE",dao.getJob(job.value,key)!!.phase); assertEquals(5,dao.getJob(job.value,key)!!.networkFailures); assertEquals(scheduled,h.scheduler.requests.size)
    } }
    @Test fun missingSourceRequiresDecisionAndNeverAcceptsStagedPrefix()=runBlocking<Unit> { fixture { h ->
        val job=h.receive(); val file=h.db.collectionTransferDao().currentFiles(job.value,key).single()
        File(h.directory,"collection-transfers/${job.value}/${file.id}/source").delete()
        assertEquals(TransferWorkResult.SUCCESS,RunTransferWork(h.coordinator).run(job))
        assertEquals("WAITING_FILE_DECISION",h.db.collectionTransferDao().getJob(job.value,key)!!.phase)
        assertEquals("FAILED_RETRYABLE",h.db.collectionTransferDao().getFile(job.value,key,file.id)!!.phase); assertTrue(h.repository.readPage(job,owner,null).entries.isEmpty())
    } }
    @Test fun renameGapVerifiesHashAndResumesThenPartOnlyNeverBecomesReady()=runBlocking<Unit> { fixture { h ->
        val job=h.receive(); val dao=h.db.collectionTransferDao(); val file=dao.currentFiles(job.value,key).single()
        h.db.openHelper.writableDatabase.execSQL("UPDATE collection_transfer_files SET phase='RECEIVING' WHERE id=?",arrayOf(file.id)); assertEquals(TransferWorkResult.SUCCESS,RunTransferWork(h.coordinator).run(job)); assertEquals("REVIEW_READY",dao.getJob(job.value,key)!!.phase)
        val partial=TransferJobId(uuid()); val member=CollectionTransferFileEntity(uuid(),partial.value,fileOrder=0,bytes=1)
        assertTrue(dao.createReceipt(CollectionTransferReceiptEntity(partial.value,1L,key,createdAt=1L),listOf(member))); assertTrue(dao.bindReceipt(partial.value,key,1L,"SAF",1L))
        val path=File(h.directory,"collection-transfers/${partial.value}/${member.id}").apply { mkdirs() }; File(path,"source.part").writeText("2 Fixture")
        assertEquals(TransferWorkResult.SUCCESS,RunTransferWork(h.coordinator).run(partial)); assertEquals("WAITING_FILE_DECISION",dao.getJob(partial.value,key)!!.phase); assertEquals("FAILED_RETRYABLE",dao.getFile(partial.value,key,member.id)!!.phase)
    } }
    @Test fun wishlistDeliveryRetainsDirtyCooldownAndDormantTombstonesMakeNoRequest()=runBlocking<Unit> { fixture { h ->
        h.db.localWishlistDao().insert(LocalWishlistEntity(uuid(),"printing",quantity=2,isFoil=null,condition=null,language=null,ownerUserId=owner.id))
        assertEquals(TransferSliceResult.CONTINUE,h.coordinator.runWishlistDelivery()); assertEquals(1,h.remoteCalls)
        assertEquals(TransferSliceResult.CONTINUE,h.coordinator.runWishlistDelivery()); assertEquals(1,h.remoteCalls)
        h.now+=30_001L; assertEquals(TransferSliceResult.CONTINUE,h.coordinator.runWishlistDelivery()); assertEquals(2,h.remoteCalls)
        h.observed=TransferOwner.Account("other"); h.gate.changeOwner(h.observed)
        assertEquals(TransferSliceResult.FINISHED,h.coordinator.runWishlistDelivery()); assertEquals(2,h.remoteCalls)
    } }
    @Test fun completedWorkReconcilesCommandThatArrivedWhileKeepWasRunning()=runBlocking<Unit> { fixture { h ->
        val job=h.ready(); val listener=h.coordinator.start(this)
        try {
            withTimeout(3000L) { h.scheduler.watched.first { it>0 } }
            val entry=h.repository.readPage(job,owner,null).entries.first()
            h.repository.editPendingEntry(job,owner,0L,entry.copy(destination=TransferDestination.COLLECTION))
            h.repository.confirmAction(owner,TransferActionRequest(TransferActionId(uuid()),job,0L,TransferDestination.COLLECTION,TransferActionScope.Entry(entry.id,1L)))
            val requested=h.scheduler.requests.size; h.scheduler.finished.value=true
            withTimeout(3000L) { h.scheduler.requestCount.first { it>requested } }
            assertEquals(job,h.scheduler.requests.last())
        } finally { listener.cancelAndJoin() }
    } }
    @Test fun reopenedJobResumesFromDurableParserOrdinalAndDeferredEvent()=runBlocking<Unit> {
        val name="coordinator-reopen-${uuid()}"; val directory=File(context.cacheDir,name).apply { mkdirs() }
        fun open()=Room.databaseBuilder(context,MtgDatabase::class.java,name).build()
        var db=open()
        try {
            var h=Harness(db,directory); h.gate.changeOwner(owner); val job=h.receive(); val file=db.collectionTransferDao().currentFiles(job.value,key).single()
            db.collectionTransferDao().stageParserRows(job.value,key,file.id,listOf(CollectionImportRowEntity(job.value,file.id,1L,0L,10L,"DATA",name="Fixture",quantity=2L)))
            db.close(); db=open(); h=Harness(db,directory); h.gate.changeOwner(owner); h.coordinator.reconcile(owner)
            assertEquals(job,h.scheduler.requests.single()); assertEquals(TransferWorkResult.SUCCESS,RunTransferWork(h.coordinator).run(job)); assertEquals(2L,db.collectionTransferDao().getFile(job.value,key,file.id)!!.parseOrdinal)
            db.openHelper.writableDatabase.execSQL("UPDATE collection_transfer_jobs SET collection_event_pending=1,applied_entries=1 WHERE id=?",arrayOf(job.value))
            h.coordinator.reconcile(owner); h.coordinator.reconcile(owner); assertEquals(1,h.events); assertFalse(db.collectionTransferDao().getJob(job.value,key)!!.collectionEventPending)
        } finally { db.close(); context.deleteDatabase(name); directory.deleteRecursively() }
    }
    @Test fun completedSourceHashIsVerifiedBeforeAnyRecoveredPrefixCanPublish()=runBlocking<Unit> { fixture { h ->
        val job=h.receive(); val file=h.db.collectionTransferDao().currentFiles(job.value,key).single()
        val path=File(h.directory,"collection-transfers/${job.value}/${file.id}/source")
        path.writeText("2 Altered\n3 Other\n")
        assertEquals(TransferWorkResult.SUCCESS,RunTransferWork(h.coordinator).run(job))
        assertEquals("WAITING_FILE_DECISION",h.db.collectionTransferDao().getJob(job.value,key)!!.phase)
        assertEquals(0L,h.db.collectionTransferDao().getFile(job.value,key,file.id)!!.parseOrdinal); assertTrue(h.repository.readPage(job,owner,null).entries.isEmpty())
    } }
    @Test fun oneSliceDrainsResolution75Plus75Plus10AndCollection500Plus1()=runBlocking<Unit> { fixture { h ->
        h.gateway.cache=false; h.gateway.successful=true
        val first=h.receive((0 until 160).joinToString("\n",postfix="\n") { "1 First$it" })
        assertEquals(TransferWorkResult.SUCCESS,RunTransferWork(h.coordinator).run(first)); assertEquals(listOf(75,75,10),h.gateway.sizes)
        h.gateway.sizes.clear()
        val job=h.receive((0 until 501).joinToString("\n",postfix="\n") { "1 Second$it" })
        assertEquals(TransferWorkResult.SUCCESS,RunTransferWork(h.coordinator).run(job)); assertEquals(listOf(75,75,75,75,75,75,51),h.gateway.sizes)
        val current=h.repository.observeSummary(job,owner).filterNotNull().first()
        h.repository.chooseDestination(job,owner,current.generation,current.intentRevision,TransferDestination.COLLECTION)
        val revision=h.repository.observeSummary(job,owner).filterNotNull().first().intentRevision
        val command=TransferActionId(uuid()); assertEquals(TransferMutationResult.Accepted,h.repository.confirmAction(owner,TransferActionRequest(command,job,current.generation,TransferDestination.COLLECTION,TransferActionScope.DestinationSelection(revision))))
        assertEquals(TransferWorkResult.SUCCESS,RunTransferWork(h.coordinator).run(job)); assertEquals(listOf(500,1),h.commits)
        assertEquals(501L,h.repository.readAction(owner,command).completedCopies); assertEquals(0L,h.db.collectionTransferDao().repositoryTotals(job.value,key).pendingEntries)
    } }
    @Test fun localStorageFailurePersistsRetryCheckpointWithoutBusyLoopOrNetworkFailures()=runBlocking<Unit> { fixture { h ->
        val job=h.ready(); val entry=h.repository.readPage(job,owner,null).entries.first()
        h.repository.editPendingEntry(job,owner,0L,entry.copy(destination=TransferDestination.COLLECTION))
        val command=TransferActionId(uuid()); h.repository.confirmAction(owner,TransferActionRequest(command,job,0L,TransferDestination.COLLECTION,TransferActionScope.Entry(entry.id,1L)))
        h.failIncrement=true
        assertEquals(TransferWorkResult.RETRY,RunTransferWork(h.coordinator).run(job)); assertEquals(1,h.incrementAttempts)
        val checkpoint=h.db.collectionTransferDao().getJob(job.value,key)!!; assertEquals(11_000L,checkpoint.nextAttemptAt); assertEquals(0,checkpoint.networkFailures)
        assertEquals(TransferWorkResult.RETRY,RunTransferWork(h.coordinator).run(job)); assertEquals(1,h.incrementAttempts)
        h.now=11_001L; h.failIncrement=false
        assertEquals(TransferWorkResult.SUCCESS,RunTransferWork(h.coordinator).run(job)); assertEquals(entry.quantity,h.repository.readAction(owner,command).completedCopies)
    } }
}

