package com.mmg.manahub.feature.collection

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mmg.manahub.core.data.local.MtgDatabase
import com.mmg.manahub.core.data.local.dao.TransferStoredResolution
import com.mmg.manahub.core.data.local.entity.*
import com.mmg.manahub.core.domain.collection.transfer.*
import com.mmg.manahub.feature.collection.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class CollectionTransferRepositoryTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val owner=TransferOwner.Account("repository-fixture")
    private val key="account:repository-fixture"
    private fun id()=UUID.randomUUID().toString()
    private suspend fun gate()=TransferSessionGate().also { it.changeOwner(owner) }
    private fun repository(db: MtgDatabase,gate: TransferSessionGate,matches: (TransferOwner)->Boolean={ (gate.currentSession as? TransferSession.Available)?.owner==it })=RoomCollectionTransferRepository(db,gate,matches,{ 50L })
    private suspend fun summary(repo: CollectionTransferRepository,id: TransferJobId)=withTimeout(3000L) { repo.observeSummary(id,owner).filterNotNull().first() }
    private data class Fixture(val job: TransferJobId,val files: List<TransferFileId>)
    private suspend fun source(db: MtgDatabase,count: Int=3,quantity: Int=2,files: Int=1,fixedPrinting: String?=null,invalid: Boolean=false,groupFirstPair: Boolean=false): Fixture {
        val job=TransferJobId(id()); val members=List(files) { TransferFileId(id()) }; val dao=db.collectionTransferDao()
        assertTrue(dao.createReceipt(CollectionTransferReceiptEntity(job.value,1L,key,createdAt=1L),members.mapIndexed { index,file -> CollectionTransferFileEntity(file.value,job.value,fileOrder=index,phase="PARSING",sha256=(if(index==0)"a" else "b").repeat(64)) }))
        assertTrue(dao.finishReception(job.value,null))
        assertTrue(dao.bindReceipt(job.value,key,1L,"SAF",1L))
        for((index,file) in members.withIndex()) {
            val indexes=(0 until count).filter { it%files==index }
            val rows=indexes.mapIndexed { ordinal,global -> CollectionImportRowEntity(job.value,file.value,ordinal+1L,ordinal*2L,ordinal*2L+1L,"DATA",name="Fixture",quantity=if(invalid)null else quantity.toLong(),error=if(invalid)"INVALID_QUANTITY" else null,preview=if(invalid)"x".repeat(300) else "") }
            rows.chunked(200).forEach { assertTrue(dao.stageParserRows(job.value,key,file.value,it)) }
            assertTrue(dao.completeParsing(job.value,key,file.value,TransferParseSummary(CollectionFileFormat.TEXT,rows.size.toLong(),0L,0L,rows.size.toLong(),if(invalid)0L else rows.size.toLong(),if(invalid)rows.size.toLong() else 0L,if(invalid)0L else rows.size.toLong()*quantity,emptyList())))
            if(!invalid)indexes.mapIndexed { ordinal,global -> TransferStoredResolution(ordinal+1L,fixedPrinting ?: if(groupFirstPair && global<2)"paired-printing" else "printing-${global.toString().padStart(4,'0')}",null) }.chunked(75).forEach { assertTrue(dao.stageResolution(job.value,key,file.value,it)) }
            assertTrue(dao.completeResolution(job.value,key,file.value))
        }
        assertEquals(TransferSliceResult.FINISHED,RoomTransferReviewBuilder(dao,{ TransferSession.Available(owner,1L) },{ 1L }).runSlice(job,owner,100).result)
        return Fixture(job,members)
    }
    private suspend fun action(repo: CollectionTransferRepository,job: TransferJobId,entry: TransferReviewEntry,destination: TransferDestination): TransferActionId {
        val current=summary(repo,job); assertEquals(TransferMutationResult.Accepted,repo.editPendingEntry(job,owner,current.generation,entry.copy(destination=destination)))
        val command=TransferActionId(id())
        assertEquals(TransferMutationResult.Accepted,repo.confirmAction(owner,TransferActionRequest(command,job,current.generation,destination,TransferActionScope.Entry(entry.id,entry.version+1L))))
        return command
    }
    @Test fun page501UsesKeysetsAndSummaryAndProvenanceRemainBounded()=runBlocking<Unit> {
        val db=Room.inMemoryDatabaseBuilder(context,MtgDatabase::class.java).build()
        try {
            val f=source(db,501); val repo=repository(db,gate()); val ids=mutableSetOf<String>(); var cursor: TransferPageCursor?=null
            do { val page=repo.readPage(f.job,owner,cursor); assertTrue(page.entries.size<=50); assertTrue(page.entries.all { it.destination==TransferDestination.NONE }); page.entries.forEach { assertTrue(ids.add(it.id)) }; cursor=page.next } while(cursor!=null)
            assertEquals(501,ids.size); val summary=summary(repo,f.job); assertEquals(1002L,summary.pendingCopies); assertEquals(501L,summary.pendingEntries); assertEquals(0L,summary.appliedCopies); assertEquals(1,summary.files.size)
            val provenance=repo.readProvenance(f.job,owner,ids.first()); assertEquals(1,provenance.size); assertEquals(2L,provenance.single().copies); assertEquals(1,repo.readFileInventory(f.job,owner,"").files.size)
        } finally { db.close() }
    }
    @Test fun pendingPagesSkipExcludedRowsAndReloadEvictedPagesInBothDirections()=runBlocking<Unit> {
        val db=Room.inMemoryDatabaseBuilder(context,MtgDatabase::class.java).build()
        try {
            val f=source(db,275); val repo=repository(db,gate())
            val excluded=repo.readPage(f.job,owner,null).entries
            val generation=summary(repo,f.job).generation
            for(entry in excluded)assertEquals(TransferMutationResult.Accepted,repo.editPendingEntry(f.job,owner,generation,entry.copy(excluded=true)))
            val pages=mutableListOf<TransferReviewPage>(); var cursor: TransferReviewCursor?=null
            do { val page=repo.readReviewPage(f.job,owner,cursor); pages.add(page); cursor=page.next } while(cursor!=null)
            val entries=pages.flatMap { it.entries }
            assertEquals(225,entries.size); assertEquals(225,entries.map { it.id }.distinct().size)
            assertTrue(entries.none { it.excluded }); assertEquals(25,pages.last().entries.size)
            assertNull(pages.first().previous); assertNull(pages.last().next)
            val reloaded=repo.readReviewPage(f.job,owner,pages[4].previous, direction=TransferPageDirection.BACKWARD)
            assertEquals(pages[3].entries,reloaded.entries)
            assertEquals(pages[4].entries,repo.readReviewPage(f.job,owner,reloaded.next).entries)
            assertEquals(50,repo.readReviewPage(f.job,owner,null,TransferReviewScope.EXCLUDED).entries.size)
            try { repo.readReviewPage(f.job,owner,pages.first().next,TransferReviewScope.EXCLUDED); fail("Scope cursor must expire") } catch(error: TransferReadException) { assertEquals(TransferError.REVIEW_CHANGED,error.error) }
            try { repo.readReviewPage(f.job,owner,pages.first().next!!.copy(jobId=TransferJobId(id()))); fail("Job cursor must expire") } catch(error: TransferReadException) { assertEquals(TransferError.REVIEW_CHANGED,error.error) }
        } finally { db.close() }
    }
    @Test fun capturedAccountReceiptRejectsReturningGenerationUntilFreshExplicitConsent()=runBlocking<Unit> {
        val db=Room.inMemoryDatabaseBuilder(context,MtgDatabase::class.java).build()
        try {
            val gate=gate(); val repo=repository(db,gate); val job=TransferJobId(id())
            val captured=(gate.currentSession as TransferSession.Available).generation
            assertTrue(db.collectionTransferDao().createReceipt(CollectionTransferReceiptEntity(job.value,captured,key,createdAt=1L),listOf(CollectionTransferFileEntity(id(),job.value,fileOrder=0,phase="PARSING"))))
            assertTrue(db.collectionTransferDao().finishReception(job.value,null))
            gate.changeOwner(TransferOwner.Account("other")); gate.changeOwner(owner)
            val returned=(gate.currentSession as TransferSession.Available).generation
            assertEquals(TransferMutationResult.Rejected(TransferError.OWNER_CHANGED),repo.bindReceipt(job,owner,returned,TransferOrigin.SAF))
            assertNull(db.collectionTransferDao().getJob(job.value,key))
            assertEquals(TransferMutationResult.Rejected(TransferError.OWNER_CHANGED),repo.bindReceipt(job,owner,captured,TransferOrigin.SAF,true))
            assertEquals(TransferMutationResult.Accepted,repo.bindReceipt(job,owner,returned,TransferOrigin.SAF,true))
        } finally { db.close() }
    }
    @Test fun receiptStorageFailureRetainsItsOwnCategory()=runBlocking<Unit> {
        val db=Room.inMemoryDatabaseBuilder(context,MtgDatabase::class.java).build()
        try {
            val gate=gate(); val repo=repository(db,gate); val job=TransferJobId(id())
            val generation=(gate.currentSession as TransferSession.Available).generation
            assertTrue(db.collectionTransferDao().createReceipt(CollectionTransferReceiptEntity(job.value,generation,key,createdAt=1L),listOf(CollectionTransferFileEntity(id(),job.value,fileOrder=0,phase="PARSING"))))
            assertTrue(db.collectionTransferDao().finishReception(job.value,null))
            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_fixture_job_write BEFORE INSERT ON collection_transfer_jobs BEGIN SELECT RAISE(ABORT,'fixture storage failure'); END")
            assertEquals(TransferMutationResult.Rejected(TransferError.STORAGE_FAILURE),repo.bindReceipt(job,owner,generation,TransferOrigin.SAF))
            assertNull(db.collectionTransferDao().getJob(job.value,key))
            assertNull(db.collectionTransferDao().getReceipt(job.value)!!.consumedAt)
        } finally { db.close() }
    }
    @Test fun ownerSwitchClearsVisibleFlowAndReturningOwnerCanReadAgain()=runBlocking<Unit> {
        val db=Room.inMemoryDatabaseBuilder(context,MtgDatabase::class.java).build()
        try {
            val f=source(db); val gate=gate(); var observed: TransferOwner?=owner; val repo=repository(db,gate) { it==observed }
            coroutineScope {
                val visible=MutableStateFlow<TransferSummary?>(null); val collector=launch(Dispatchers.IO) { repo.observeSummary(f.job,owner).collect { visible.value=it } }
                withTimeout(3000L) { visible.filterNotNull().first() }; observed=TransferOwner.Account("other"); gate.changeOwner(observed) { db.collectionTransferDao().pauseOwnerJobs(key) }
                withTimeout(3000L) { visible.first { it==null } }
                try { repo.readPage(f.job,owner,null); fail("Unavailable owner must not read") } catch(error: TransferReadException) { assertEquals(TransferError.OWNER_UNAVAILABLE,error.error) }
                assertTrue(repo.pause(f.job,owner) is TransferMutationResult.Rejected); observed=owner; gate.changeOwner(owner); assertEquals(3,repo.readPage(f.job,owner,null).entries.size); collector.cancelAndJoin()
            }
        } finally { db.close() }
    }
    @Test fun neutralAcceptanceDoesNotApplyAndMixedActionsLeaveOtherChoices()=runBlocking<Unit> {
        val db=Room.inMemoryDatabaseBuilder(context,MtgDatabase::class.java).build()
        try {
            val f=source(db); val gate=gate(); val repo=repository(db,gate); val before=summary(repo,f.job)
            assertEquals(TransferMutationResult.Accepted,repo.acknowledgeReview(f.job,owner,TransferConfirmation(before.generation,before.payloadVersion,false,false)))
            assertFalse(db.collectionTransferDao().freezeConfirmedPayload(f.job.value,key)); assertEquals(0L,summary(repo,f.job).appliedCopies)
            val entries=repo.readPage(f.job,owner,null).entries; val wish=action(repo,f.job,entries[0],TransferDestination.WISHLIST)
            assertEquals(TransferWishlistApplyResult.LOCAL_FINISHED,RoomTransferWishlistExecutor(db,gate,{ 10L },{ it==owner }).runSlice(wish,owner))
            var after=summary(repo,f.job); assertEquals(2L,after.pendingEntries); assertEquals(4L,after.pendingCopies); assertEquals(0L,after.appliedCopies); assertEquals(1L,after.wishlistCompletedEntries); assertEquals(2L,after.wishlistCompletedCopies)
            assertEquals(1L,repo.readAction(owner,wish).completedEntries)
            val collect=action(repo,f.job,entries[1],TransferDestination.COLLECTION); assertEquals(TransferCollectionApplyResult.FINISHED,RoomTransferCollectionExecutor(db,gate,{ 10L },{}).runSlice(collect,owner))
            after=summary(repo,f.job); assertEquals(1L,after.pendingEntries); assertEquals(2L,after.pendingCopies); assertEquals(2L,after.appliedCopies)
            val remaining=repo.readPage(f.job,owner,null).entries.single { it.id==entries[2].id }; assertEquals(TransferDestination.NONE,remaining.destination)
            assertEquals(TransferMutationResult.Accepted,repo.editPendingEntry(f.job,owner,after.generation,remaining.copy(quantity=5,condition="LP",destination=TransferDestination.WISHLIST)))
            assertEquals(2L,repo.readAction(owner,wish).completedCopies)
        } finally { db.close() }
    }
    @Test fun userPauseCheckpointSurvivesAuthChangeAndReopenWithoutAgeCleanup()=runBlocking<Unit> {
        val name="repository-reopen-${id()}"
        fun open()=Room.databaseBuilder(context,MtgDatabase::class.java,name).build()
        var db=open()
        try {
            val job=TransferJobId(id()); val file=TransferFileId(id()); val gate=gate(); var repo=repository(db,gate); val generation=(gate.currentSession as TransferSession.Available).generation
            assertTrue(db.collectionTransferDao().createReceipt(CollectionTransferReceiptEntity(job.value,generation,key,createdAt=1L),listOf(CollectionTransferFileEntity(file.value,job.value,fileOrder=0,phase="PARSING"))))
            assertTrue(db.collectionTransferDao().finishReception(job.value,null))
            assertEquals(TransferMutationResult.Accepted,repo.bindReceipt(job,owner,generation,TransferOrigin.SAF))
            assertTrue(db.collectionTransferDao().stageParserRows(job.value,key,file.value,listOf(CollectionImportRowEntity(job.value,file.value,1L,0L,1L,"DATA",name="Fixture",quantity=1L))))
            assertEquals(TransferMutationResult.Accepted,repo.pause(job,owner)); gate.changeOwner(TransferOwner.Account("other")) { db.collectionTransferDao().pauseOwnerJobs(key) }
            assertEquals("PAUSED_BY_USER",db.collectionTransferDao().getJob(job.value,key)!!.phase); assertEquals("PARSING",db.collectionTransferDao().getJob(job.value,key)!!.previousPhase)
            db.close(); db=open(); gate.changeOwner(owner); repo=repository(db,gate); assertEquals(1,db.collectionTransferDao().unfinishedJobs(key).size)
            assertEquals(TransferMutationResult.Accepted,repo.resume(job,owner)); assertEquals("PARSING",db.collectionTransferDao().getJob(job.value,key)!!.phase); assertEquals(1L,db.collectionTransferDao().getFile(job.value,key,file.value)!!.parseOrdinal)
        } finally { db.close(); context.deleteDatabase(name) }
    }
    @Test fun explicitDiscardIsVersionedAndDoesNotRevertAppliedRowsOrHistory()=runBlocking<Unit> {
        val db=Room.inMemoryDatabaseBuilder(context,MtgDatabase::class.java).build()
        try {
            val f=source(db); val gate=gate(); val repo=repository(db,gate); val e=repo.readPage(f.job,owner,null).entries.first(); val command=action(repo,f.job,e,TransferDestination.COLLECTION)
            RoomTransferCollectionExecutor(db,gate,{ 10L },{}).runSlice(command,owner); val before=summary(repo,f.job); val snapshot=repo.readAction(owner,command); val provenance=repo.readProvenance(f.job,owner,e.id)
            assertTrue(repo.discardPending(f.job,owner,before.generation,before.payloadVersion-1L) is TransferMutationResult.Rejected)
            assertEquals(TransferMutationResult.Accepted,repo.discardPending(f.job,owner,before.generation,before.payloadVersion))
            val after=summary(repo,f.job); assertEquals(TransferPhase.DISCARDED,after.phase); assertEquals(0L,after.pendingEntries); assertEquals(0L,after.pendingCopies); assertEquals(2L,after.appliedCopies)
            assertEquals(snapshot,repo.readAction(owner,command)); assertEquals(provenance,repo.readProvenance(f.job,owner,e.id)); assertNotNull(db.collectionTransferDao().history(key,"a".repeat(64)))
            assertEquals(2,db.userCardCollectionDao().getByCompositeKey(owner.id,e.scryfallId,e.isFoil,e.condition,e.language)!!.quantity)
        } finally { db.close() }
    }
    @Test fun errorPreviewUsesFullCountAndOverflowCanBeExcludedWithoutClamping()=runBlocking<Unit> {
        val db=Room.inMemoryDatabaseBuilder(context,MtgDatabase::class.java).build()
        try {
            val invalid=source(db,201,invalid=true); val gate=gate(); val repo=repository(db,gate); val preview=repo.readErrorPreview(invalid.job,owner)
            assertEquals(201L,preview.total); assertEquals(200,preview.examples.size); assertTrue(preview.examples.all { it.preview.length==300 })
            val overflow=source(db,2,Int.MAX_VALUE,fixedPrinting="same-printing"); val before=summary(repo,overflow.job); val entry=repo.readPage(overflow.job,owner,null).entries.single()
            assertEquals(Int.MAX_VALUE.toLong()*2,entry.quantity); assertEquals(TransferMutationResult.Accepted,repo.editPendingEntry(overflow.job,owner,before.generation,entry.copy(excluded=true)))
            val after=summary(repo,overflow.job); assertEquals(0L,after.pendingCopies); assertEquals(1L,after.excludedEntries)
            assertEquals(entry.quantity,repo.readPage(overflow.job,owner,null).entries.single().quantity)
        } finally { db.close() }
    }
    @Test fun selectionRejectsStaleGenerationAndSavedRemovedEditNeedsExplicitDecision()=runBlocking<Unit> {
        val db=Room.inMemoryDatabaseBuilder(context,MtgDatabase::class.java).build()
        try {
            val f=source(db,2,files=2); val gate=gate(); val repo=repository(db,gate); val before=summary(repo,f.job); val entry=repo.readPage(f.job,owner,null).entries.first()
            assertEquals(TransferMutationResult.Accepted,repo.editPendingEntry(f.job,owner,before.generation,entry.copy(quantity=5,destination=TransferDestination.WISHLIST)))
            val removed=repo.readProvenance(f.job,owner,entry.id).single().file
            assertEquals(TransferMutationResult.Accepted,repo.selectFile(f.job,owner,removed,before.generation,false))
            try { repo.readPage(f.job,owner,TransferPageCursor(before.generation,"")); fail("Stale page") } catch(error: TransferReadException) { assertEquals(TransferError.REVIEW_CHANGED,error.error) }
            assertTrue(repo.editPendingEntry(f.job,owner,before.generation,entry) is TransferMutationResult.Rejected)
            assertEquals(TransferSliceResult.FINISHED,RoomTransferReviewBuilder(db.collectionTransferDao(),{ gate.currentSession },{ 20L }).runSlice(f.job,owner,100).result)
            val decision=repo.readDecisions(f.job,owner,"").single(); assertEquals(5L,decision.entry.quantity); assertEquals(TransferDestination.WISHLIST,decision.entry.destination)
            assertTrue(repo.decideReviewEdit(f.job,owner,decision.entryId,decision.generation,TransferReviewDecision.KEEP_EDIT) is TransferMutationResult.Rejected)
            assertEquals(TransferMutationResult.Accepted,repo.decideReviewEdit(f.job,owner,decision.entryId,decision.generation,TransferReviewDecision.DISMISS_REMOVED)); assertTrue(repo.readDecisions(f.job,owner,"").isEmpty()); assertEquals(1,repo.readPage(f.job,owner,null).entries.size)
        } finally { db.close() }
    }
    @Test fun neutralReceiptBindingRequiresAvailableIdentityAndChangedGenerationChoice()=runBlocking<Unit> {
        val db=Room.inMemoryDatabaseBuilder(context,MtgDatabase::class.java).build()
        try {
            val gate=TransferSessionGate(); var available=false; val repo=repository(db,gate) { available && it==owner }; val job=TransferJobId(id())
            assertTrue(db.collectionTransferDao().createReceipt(CollectionTransferReceiptEntity(job.value,0L,null,createdAt=1L),listOf(CollectionTransferFileEntity(id(),job.value,fileOrder=0))))
            assertTrue(db.collectionTransferDao().finishReception(job.value,null))
            assertTrue(repo.bindReceipt(job,owner,0L,TransferOrigin.SHARE) is TransferMutationResult.Rejected); assertNull(db.collectionTransferDao().getJob(job.value,key))
            available=true; gate.changeOwner(owner); val generation=(gate.currentSession as TransferSession.Available).generation
            assertTrue(repo.bindReceipt(job,owner,generation,TransferOrigin.SHARE) is TransferMutationResult.Rejected)
            assertEquals(TransferMutationResult.Accepted,repo.bindReceipt(job,owner,generation,TransferOrigin.SHARE,true)); assertEquals(key,db.collectionTransferDao().getJob(job.value,key)!!.ownerKey)
        } finally { db.close() }
    }
    @Test fun bulkWishlistKeepsReviewAndExplicitFollowupDoesNotReplayWishlist()=runBlocking<Unit> {
        val db=Room.inMemoryDatabaseBuilder(context,MtgDatabase::class.java).build()
        try {
            val f=source(db); val gate=gate(); val repo=repository(db,gate); var current=summary(repo,f.job)
            assertEquals(TransferMutationResult.Accepted,repo.chooseDestination(f.job,owner,current.generation,current.intentRevision,TransferDestination.WISHLIST))
            current=summary(repo,f.job); val wish=TransferActionId(id())
            assertEquals(TransferMutationResult.Accepted,repo.confirmAction(owner,TransferActionRequest(wish,f.job,current.generation,TransferDestination.WISHLIST,TransferActionScope.DestinationSelection(current.intentRevision))))
            assertEquals(TransferWishlistApplyResult.LOCAL_FINISHED,RoomTransferWishlistExecutor(db,gate,{ 10L },{ it==owner }).runSlice(wish,owner))
            current=summary(repo,f.job); assertEquals(0L,current.pendingEntries); assertEquals(6L,current.wishlistCompletedCopies); assertEquals(TransferPhase.REVIEW_READY,current.phase)
            val original=repo.readPage(f.job,owner,null).entries.first(); val snapshot=repo.readAction(owner,wish)
            assertEquals(TransferMutationResult.Accepted,repo.reopenWishlistEntry(f.job,owner,original.id,original.version,current.payloadVersion))
            assertTrue(repo.reopenWishlistEntry(f.job,owner,original.id,original.version,current.payloadVersion) is TransferMutationResult.Rejected)
            val pending=repo.readPage(f.job,owner,null).entries.single { it.id==original.id }; assertEquals(TransferDestination.NONE,pending.destination)
            val collect=action(repo,f.job,pending,TransferDestination.COLLECTION)
            assertEquals(TransferCollectionApplyResult.FINISHED,RoomTransferCollectionExecutor(db,gate,{ 10L },{}).runSlice(collect,owner))
            assertEquals(snapshot,repo.readAction(owner,wish)); current=summary(repo,f.job); assertEquals(6L,current.wishlistCompletedCopies); assertEquals(2L,current.appliedCopies)
        } finally { db.close() }
    }
    @Test fun partialValidApplyKeepsInvalidPendingCountUntilExplicitExclusion()=runBlocking<Unit> {
        val db=Room.inMemoryDatabaseBuilder(context,MtgDatabase::class.java).build()
        try {
            val f=source(db,3,Int.MAX_VALUE,groupFirstPair=true); val gate=gate(); val repo=repository(db,gate)
            val entries=repo.readPage(f.job,owner,null).entries
            val overflow=entries.single { it.quantity>Int.MAX_VALUE.toLong() }
            assertEquals(1L,summary(repo,f.job).invalidPendingEntries)
            val valid=entries.single { it.quantity==Int.MAX_VALUE.toLong() }
            val command=action(repo,f.job,valid,TransferDestination.COLLECTION)
            assertEquals(TransferCollectionApplyResult.FINISHED,RoomTransferCollectionExecutor(db,gate,{ 10L },{}).runSlice(command,owner))
            val after=summary(repo,f.job)
            assertEquals(1L,after.pendingEntries); assertEquals(1L,after.invalidPendingEntries)
            assertEquals(overflow.quantity,after.pendingCopies)
            assertEquals(TransferMutationResult.Accepted,repo.editPendingEntry(f.job,owner,after.generation,overflow.copy(excluded=true)))
            assertEquals(0L,summary(repo,f.job).invalidPendingEntries)
            assertEquals(overflow.quantity,repo.readReviewPage(f.job,owner,null,TransferReviewScope.EXCLUDED).entries.single().quantity)
        } finally { db.close() }
    }
    @Test fun ordinaryBulkCollectionLeavesRetainedWishlistUntilExplicitCompletedFollowup()=runBlocking<Unit> {
        val db=Room.inMemoryDatabaseBuilder(context,MtgDatabase::class.java).build()
        try {
            val f=source(db,71); val gate=gate(); val repo=repository(db,gate)
            val wish=action(repo,f.job,repo.readPage(f.job,owner,null).entries.first(),TransferDestination.WISHLIST)
            assertEquals(TransferWishlistApplyResult.LOCAL_FINISHED,RoomTransferWishlistExecutor(db,gate,{ 10L },{ it==owner }).runSlice(wish,owner))
            val wishSnapshot=repo.readAction(owner,wish); val wishlistHistory=repo.readReviewPage(f.job,owner,null,TransferReviewScope.WISHLIST); var current=summary(repo,f.job)
            assertEquals("COMPLETED",wishlistHistory.entries.single().state)
            assertNotNull(wishlistHistory.entries.single().sourceEntryId)
            assertEquals(1L,current.retainedWishlistEntries); assertEquals(2L,current.retainedWishlistCopies)
            assertEquals(70L,current.pendingEntries)
            assertEquals(TransferMutationResult.Accepted,repo.chooseDestination(f.job,owner,current.generation,current.intentRevision,TransferDestination.COLLECTION))
            current=summary(repo,f.job); val collection=TransferActionId(id())
            assertEquals(TransferMutationResult.Accepted,repo.confirmAction(owner,TransferActionRequest(collection,f.job,current.generation,TransferDestination.COLLECTION,TransferActionScope.DestinationSelection(current.intentRevision))))
            assertEquals(70L,repo.readAction(owner,collection).entries)
            assertEquals(TransferCollectionApplyResult.FINISHED,RoomTransferCollectionExecutor(db,gate,{ 10L },{}).runSlice(collection,owner))
            assertTrue(repo.readReviewPage(f.job,owner,null).entries.isEmpty())
            assertEquals(1,repo.readReviewPage(f.job,owner,null,TransferReviewScope.WISHLIST).entries.size)
            assertEquals(wishSnapshot,repo.readAction(owner,wish))
            val firstCollectionPage=repo.readReviewPage(f.job,owner,null,TransferReviewScope.COLLECTION)
            assertEquals(50,firstCollectionPage.entries.size)
            val lastCollectionPage=repo.readReviewPage(f.job,owner,firstCollectionPage.next,TransferReviewScope.COLLECTION)
            assertEquals(20,lastCollectionPage.entries.size)
            assertEquals(firstCollectionPage.entries,repo.readReviewPage(f.job,owner,lastCollectionPage.previous,TransferReviewScope.COLLECTION,TransferPageDirection.BACKWARD).entries)
            current=summary(repo,f.job)
            assertEquals(TransferMutationResult.Accepted,repo.reopenWishlistSelection(f.job,owner,current.generation,current.payloadVersion))
            assertEquals(1,repo.readReviewPage(f.job,owner,null).entries.size)
            assertEquals(wishSnapshot,repo.readAction(owner,wish))
            assertEquals(0L,summary(repo,f.job).retainedWishlistEntries)
            assertEquals(0L,summary(repo,f.job).retainedWishlistCopies)
            assertEquals(wishlistHistory,repo.readReviewPage(f.job,owner,null,TransferReviewScope.WISHLIST))
            val retained=repo.readReviewPage(f.job,owner,null).entries.single()
            current=summary(repo,f.job)
            assertEquals(TransferMutationResult.Accepted,repo.editPendingEntry(f.job,owner,current.generation,retained.copy(quantity=5L,condition="LP")))
            assertEquals(wishlistHistory,repo.readReviewPage(f.job,owner,null,TransferReviewScope.WISHLIST))
            assertEquals(firstCollectionPage,repo.readReviewPage(f.job,owner,null,TransferReviewScope.COLLECTION))
        } finally { db.close() }
    }
    @Test fun authoritativePayloadVersionRejectsStaleEntryConsentAfterUnrelatedEdit()=runBlocking<Unit> {
        val db=Room.inMemoryDatabaseBuilder(context,MtgDatabase::class.java).build()
        try {
            val f=source(db); val repo=repository(db,gate()); val entries=repo.readPage(f.job,owner,null).entries
            var current=summary(repo,f.job)
            assertEquals(TransferMutationResult.Accepted,repo.editPendingEntry(f.job,owner,current.generation,entries[0].copy(destination=TransferDestination.COLLECTION)))
            current=summary(repo,f.job)
            val captured=TransferActionRequest(TransferActionId(id()),f.job,current.generation,TransferDestination.COLLECTION,TransferActionScope.Entry(entries[0].id,entries[0].version+1L),expectedPayloadVersion=current.payloadVersion)
            assertEquals(TransferMutationResult.Accepted,repo.editPendingEntry(f.job,owner,current.generation,entries[1].copy(quantity=5L)))
            assertFalse(db.collectionTransferDao().confirmAction(key,captured,50L))
            assertNull(db.collectionTransferDao().action(captured.id.value,key))
            assertEquals(TransferMutationResult.Rejected(TransferError.REVIEW_CHANGED),repo.confirmAction(owner,captured))
            val renewed=captured.copy(expectedPayloadVersion=summary(repo,f.job).payloadVersion)
            assertEquals(TransferMutationResult.Accepted,repo.confirmAction(owner,renewed))
            assertEquals(1L,repo.readAction(owner,renewed.id).entries)
        } finally { db.close() }
    }
}
