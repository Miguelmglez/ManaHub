package com.mmg.manahub.feature.collection

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mmg.manahub.core.data.local.*
import com.mmg.manahub.core.data.local.dao.TransferStoredResolution
import com.mmg.manahub.core.data.local.entity.*
import com.mmg.manahub.core.domain.auth.*
import com.mmg.manahub.core.domain.collection.transfer.*
import com.mmg.manahub.feature.collection.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class CollectionTransferApplyTest {
    @get:Rule val helper=MigrationTestHelper(InstrumentationRegistry.getInstrumentation(),MtgDatabase::class.java,emptyList(),FrameworkSQLiteOpenHelperFactory())
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val owner=TransferOwner.Account("apply-fixture")
    private val key="account:apply-fixture"
    private fun id()=UUID.randomUUID().toString()
    private fun ownerKey(value: TransferOwner)=when(value) { is TransferOwner.Account -> "account:${value.id}"; is TransferOwner.VerifiedGuest -> "guest:${value.installationToken}" }
    private fun open(name: String)=Room.databaseBuilder(context,MtgDatabase::class.java,name).addMigrations(MIGRATION_61_62).build()
    private suspend fun gate(value: TransferOwner=owner)=TransferSessionGate().also { it.changeOwner(value) }
    private data class Fixture(val job: String,val file: String,val entries: List<CollectionImportEntryEntity>)
    private suspend fun source(db: MtgDatabase,count: Int=2,value: TransferOwner=owner,quantity: Long=1L): Fixture {
        val job=id(); val file=id(); val dao=db.collectionTransferDao(); val ownerKey=ownerKey(value)
        assertTrue(dao.createReceipt(CollectionTransferReceiptEntity(job,1L,ownerKey,createdAt=1L),listOf(CollectionTransferFileEntity(file,job,fileOrder=0,phase="PARSING",sha256="a".repeat(64)))))
        assertTrue(dao.bindReceipt(job,ownerKey,1L,"SAF",1L))
        for(start in 0 until count step 200) assertTrue(dao.stageParserRows(job,ownerKey,file,(start until minOf(start+200,count)).map { n -> CollectionImportRowEntity(job,file,n+1L,n*2L,n*2L+1L,"DATA",name="Fixture",quantity=quantity) }))
        assertTrue(dao.completeParsing(job,ownerKey,file,TransferParseSummary(CollectionFileFormat.TEXT,count.toLong(),0L,0L,count.toLong(),count.toLong(),0L,count*quantity,emptyList())))
        for(start in 0 until count step 75) assertTrue(dao.stageResolution(job,ownerKey,file,(start until minOf(start+75,count)).map { TransferStoredResolution(it+1L,"printing-$it",null) }))
        assertTrue(dao.completeResolution(job,ownerKey,file))
        val builder=RoomTransferReviewBuilder(dao,{ TransferSession.Available(value,1L) },{ 1L })
        assertEquals(TransferSliceResult.FINISHED,builder.runSlice(TransferJobId(job),value,maxBatches=100).result)
        val entries=mutableListOf<CollectionImportEntryEntity>(); var cursor=""
        while(true) { val page=dao.reviewPage(job,ownerKey,dao.getJob(job,ownerKey)!!.generation,cursor); if(page.isEmpty())break; entries+=page; cursor=page.last().id }
        return Fixture(job,file,entries)
    }
    private suspend fun command(db: MtgDatabase,fixture: Fixture,value: TransferOwner=owner,freeze: Boolean=true): TransferActionId {
        val dao=db.collectionTransferDao(); val ownerKey=ownerKey(value); var job=dao.getJob(fixture.job,ownerKey)!!
        assertEquals(fixture.entries.size.toLong(),dao.chooseAllDestination(job.id,ownerKey,job.generation,job.intentRevision,TransferDestination.COLLECTION))
        job=dao.getJob(job.id,ownerKey)!!; val action=TransferActionId(id())
        assertTrue(dao.confirmAction(ownerKey,TransferActionRequest(action,TransferJobId(job.id),job.generation,TransferDestination.COLLECTION,TransferActionScope.DestinationSelection(job.intentRevision)),1L))
        if(freeze)assertTrue(dao.freezeAction(action.value,ownerKey)); return action
    }
    private suspend fun totals(db: MtgDatabase): Pair<Long,Long> = withContext(Dispatchers.IO) {
        db.openHelper.writableDatabase.query("SELECT COUNT(*),COALESCE(SUM(quantity),0) FROM user_card_collection WHERE is_deleted=0").use { it.moveToFirst(); it.getLong(0) to it.getLong(1) }
    }
    private suspend fun existing(db: MtgDatabase,printing: String,quantity: Int,deleted: Boolean=false,user: String?="apply-fixture"): String=withContext(Dispatchers.IO) {
        val row=id(); db.userCardCollectionDao().upsert(UserCardCollectionEntity(row,user,printing,quantity,false,"NM","en",true,deleted,1L,1L)); row
    }
    @Test fun migration61to62PreservesMarkersCollectionAndBothTradeOutboxes() {
        val name="apply-migration-${id()}"; val before=helper.createDatabase(name,61)
        before.execSQL("INSERT INTO user_card_collection(id,user_id,scryfall_id,quantity,is_foil,condition,language,is_for_trade,is_deleted,updated_at,created_at) VALUES ('legacy',NULL,'printing',42,0,'NM','en',0,0,1,1)")
        before.execSQL("INSERT INTO trade_offer_cleanup(proposal_id,user_id,collection_id) VALUES ('proposal','fixture','legacy')")
        before.execSQL("INSERT INTO trade_wishlist_cleanup(user_id,wishlist_id,target_quantity) VALUES ('fixture','wish',7)")
        before.execSQL("INSERT INTO collection_transfer_action_entries(action_id,entry_id,entry_version,scryfall_id,is_foil,condition,language,quantity,state,completed_quantity) VALUES ('action','entry',1,'printing',0,'NM','en',3,'COMPLETED',3)")
        val tables=mutableSetOf<String>(); before.query("SELECT name FROM sqlite_master WHERE type='table'").use { while(it.moveToNext())tables+=it.getString(0) }; before.close()
        val after=helper.runMigrationsAndValidate(name,62,true,MIGRATION_61_62)
        val migrated=mutableSetOf<String>(); after.query("SELECT name FROM sqlite_master WHERE type='table'").use { while(it.moveToNext())migrated+=it.getString(0) }; assertTrue(migrated.containsAll(tables))
        for(table in listOf("trade_offer_cleanup","trade_wishlist_cleanup","collection_transfer_action_entries")) after.query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); assertEquals(1,it.getInt(0)) }
        after.query("SELECT quantity FROM user_card_collection").use { it.moveToFirst(); assertEquals(42,it.getInt(0)) }
        after.query("SELECT COUNT(*) FROM collection_transfer_guest_rows").use { it.moveToFirst(); assertEquals(0,it.getInt(0)) }
        after.query("PRAGMA foreign_key_list(user_card_collection)").use { assertEquals(0,it.count) }; after.close(); context.deleteDatabase(name)
    }
    @Test fun independentlyEditedAndRemovedClonesApplyOnlyChosenCopies()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,MtgDatabase::class.java).build()
        try {
            val fixture=source(db,count=1,quantity=3L);val dao=db.collectionTransferDao();val original=fixture.entries.single();val first=id();val second=id()
            for(copy in listOf(first,second)) {
                val job=dao.getJob(fixture.job,key)!!
                assertTrue(dao.duplicatePendingEntry(fixture.job,key,job.generation,job.payloadVersion,original.id,original.entryVersion,copy,2L))
            }
            assertTrue(dao.editPendingEntry(fixture.job,key,first,0L,original.scryfallId,false,"NM","en",5L,TransferDestination.NONE))
            assertTrue(dao.editPendingEntry(fixture.job,key,second,0L,original.scryfallId,false,"NM","en",3L,TransferDestination.NONE,true))
            var job=dao.getJob(fixture.job,key)!!
            assertEquals(2L,dao.chooseAllDestination(job.id,key,job.generation,job.intentRevision,TransferDestination.COLLECTION))
            job=dao.getJob(job.id,key)!!;val action=TransferActionId(id())
            assertTrue(dao.confirmAction(key,TransferActionRequest(action,TransferJobId(job.id),job.generation,TransferDestination.COLLECTION,TransferActionScope.DestinationSelection(job.intentRevision),acceptExclusions=true),3L))
            assertTrue(dao.freezeAction(action.value,key))
            assertEquals(TransferCollectionApplyResult.FINISHED,RoomTransferCollectionExecutor(db,gate(),{ 4L },{}).runSlice(action,owner))
            assertEquals(1L to 8L,totals(db))
            assertEquals(3L,dao.reviewEntry(job.id,key,original.id)!!.appliedQuantity)
            assertEquals(5L,dao.reviewEntry(job.id,key,first)!!.appliedQuantity)
            assertEquals(0L,dao.reviewEntry(job.id,key,second)!!.appliedQuantity)
            assertEquals(2L,dao.getJob(job.id,key)!!.appliedEntries)
            assertEquals(3L,dao.provenance(job.id,key,first).single().sourceCopies)
            assertTrue(dao.provenance(job.id,key,original.id).single().participated)
        } finally { db.close() }
    }
    @Test fun realTransactionRollsBackIncrementMarkerCountersAndParticipation()=runBlocking {
        for(point in listOf(TransferApplyCheckpoint.AFTER_INCREMENT,TransferApplyCheckpoint.AFTER_MARKER,TransferApplyCheckpoint.AFTER_PROVENANCE)) {
            val db=Room.inMemoryDatabaseBuilder(context,MtgDatabase::class.java).build()
            try {
                val fixture=source(db,quantity=2L); val action=command(db,fixture); val gate=gate()
                existing(db,fixture.entries[0].scryfallId,4)
                val executor=RoomTransferCollectionExecutor(db,gate,{ 2L },{},checkpoint={ checkpoint,_ -> if(checkpoint==point)throw IllegalStateException("Injected storage failure") })
                assertEquals(TransferCollectionApplyResult.FAILED_RETRYABLE,executor.runSlice(action,owner))
                assertEquals(1L to 4L,totals(db)); val dao=db.collectionTransferDao()
                assertEquals(0L,dao.getJob(fixture.job,key)!!.appliedCopies); assertFalse(dao.getJob(fixture.job,key)!!.collectionEventPending)
                assertTrue(dao.actionPage(action.value,key,"").all { it.completedQuantity==0L && it.state=="PENDING" })
                for(entry in fixture.entries) { assertEquals(0L,dao.reviewEntry(fixture.job,key,entry.id)!!.appliedQuantity); assertFalse(dao.provenance(fixture.job,key,entry.id).single().participated) }
                assertNull(dao.history(key,"a".repeat(64)))
                assertEquals(TransferCollectionApplyResult.FINISHED,RoomTransferCollectionExecutor(db,gate,{ 3L },{}).runSlice(action,owner)); assertEquals(2L to 8L,totals(db))
            } finally { db.close() }
        }
    }
    @Test fun slices500Plus1SurviveSecondBatchFailureAndLastCommitCrash()=runBlocking {
        val name="apply-reopen-${id()}"; var db=open(name); val gate=gate(); val events=AtomicInteger()
        try {
            val fixture=source(db,501); val action=command(db,fixture)
            assertEquals(TransferCollectionApplyResult.CONTINUE,RoomTransferCollectionExecutor(db,gate,{ 2L },{ events.incrementAndGet() }).runSlice(action,owner))
            assertEquals(500L to 500L,totals(db)); assertNotNull(db.collectionTransferDao().history(key,"a".repeat(64)))
            assertEquals(500L,db.collectionTransferDao().getJob(fixture.job,key)!!.appliedEntries)
            assertEquals(TransferCollectionApplyResult.FAILED_RETRYABLE,RoomTransferCollectionExecutor(db,gate,{ 3L },{},checkpoint={ point,_ -> if(point==TransferApplyCheckpoint.AFTER_MARKER)throw IllegalStateException("Injected second batch failure") }).runSlice(action,owner))
            assertEquals(500L to 500L,totals(db)); db.close(); db=open(name)
            assertEquals(1,db.collectionTransferDao().pendingCollectionActionPage(action.value,key).size)
            assertEquals(TransferCollectionApplyResult.FAILED_RETRYABLE,RoomTransferCollectionExecutor(db,gate,{ 4L },{},checkpoint={ point,_ -> if(point==TransferApplyCheckpoint.AFTER_COMMIT)throw IllegalStateException("Injected postcommit crash") }).runSlice(action,owner))
            assertEquals(501L to 501L,totals(db)); assertTrue(db.collectionTransferDao().getJob(fixture.job,key)!!.collectionEventPending)
            db.close(); db=open(name)
            val recovered=RoomTransferCollectionExecutor(db,gate,{ 5L },{ events.incrementAndGet() })
            assertEquals(1,recovered.reconcilePendingEvents(owner)); assertEquals(1,events.get()); assertFalse(db.collectionTransferDao().getJob(fixture.job,key)!!.collectionEventPending)
            assertEquals(TransferCollectionApplyResult.FINISHED,recovered.runSlice(action,owner)); assertEquals(501L to 501L,totals(db)); assertEquals(1,events.get())
        } finally { db.close(); context.deleteDatabase(name) }
    }
    @Test fun independentExecutorsAndRepeatedTapApplyExactlyOnce()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,MtgDatabase::class.java).build()
        try {
            val fixture=source(db,501); val action=command(db,fixture)
            val first=RoomTransferCollectionExecutor(db,gate(),{ 2L },{}); val second=RoomTransferCollectionExecutor(db,gate(),{ 3L },{})
            coroutineScope { awaitAll(async(Dispatchers.IO) { first.runSlice(action,owner) },async(Dispatchers.IO) { second.runSlice(action,owner) }) }
            assertEquals(501L to 501L,totals(db)); assertEquals(501L,db.collectionTransferDao().getJob(fixture.job,key)!!.appliedCopies)
            assertEquals(TransferCollectionApplyResult.FINISHED,first.runSlice(action,owner)); assertEquals(501L to 501L,totals(db))
        } finally { db.close() }
    }
    @Test fun overflowAfterReviewRollsBackWholeBatchAndRequiresNewSubsetConsent()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,MtgDatabase::class.java).build()
        try {
            val fixture=source(db,quantity=2L); val action=command(db,fixture,freeze=false); val gate=gate(); val dao=db.collectionTransferDao()
            assertEquals("CONFIRMED",dao.action(action.value,key)!!.phase)
            val overflowing=fixture.entries.last(); val row=existing(db,overflowing.scryfallId,Int.MAX_VALUE)
            val before=dao.getJob(fixture.job,key)!!
            assertEquals(TransferCollectionApplyResult.REVIEW_REQUIRED,RoomTransferCollectionExecutor(db,gate,{ 2L },{}).runSlice(action,owner))
            assertEquals(1L to Int.MAX_VALUE.toLong(),totals(db)); assertEquals(0L,dao.getJob(fixture.job,key)!!.appliedCopies)
            assertEquals(before.payloadVersion+1L,dao.getJob(fixture.job,key)!!.payloadVersion); assertEquals("REVIEW_REQUIRED",dao.action(action.value,key)!!.phase)
            assertTrue(fixture.entries.all { dao.reviewEntry(fixture.job,key,it.id)!!.activeActionId==null }); assertNull(dao.history(key,"a".repeat(64)))
            assertFalse(dao.selectFile(fixture.job,key,fixture.file,before.generation,false))
            withContext(Dispatchers.IO) { db.userCardCollectionDao().upsert(db.userCardCollectionDao().getById(row)!!.copy(quantity=1)) }
            val edited=dao.reviewEntry(fixture.job,key,overflowing.id)!!
            assertTrue(dao.editPendingEntry(fixture.job,key,edited.id,edited.entryVersion,edited.scryfallId,false,"NM","en",1L,TransferDestination.COLLECTION))
            assertEquals(TransferCollectionApplyResult.CONFIRMATION_REQUIRED,RoomTransferCollectionExecutor(db,gate,{ 3L },{}).runSlice(action,owner))
            val job=dao.getJob(fixture.job,key)!!; val replacement=TransferActionId(id())
            assertTrue(dao.confirmAction(key,TransferActionRequest(replacement,TransferJobId(job.id),job.generation,TransferDestination.COLLECTION,TransferActionScope.DestinationSelection(job.intentRevision)),3L))
            assertEquals(TransferCollectionApplyResult.FINISHED,RoomTransferCollectionExecutor(db,gate,{ 4L },{}).runSlice(replacement,owner)); assertEquals(2L to 4L,totals(db))
        } finally { db.close() }
    }
    @Test fun reviveRetainsRowUuidAndLegacySaturationPolicyStaysSeparate()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,MtgDatabase::class.java).build()
        try {
            val fixture=source(db,1,quantity=3L); val row=existing(db,fixture.entries.single().scryfallId,Int.MAX_VALUE,deleted=true)
            val action=command(db,fixture); assertEquals(TransferCollectionApplyResult.FINISHED,RoomTransferCollectionExecutor(db,gate(),{ 99L },{}).runSlice(action,owner))
            val revived=withContext(Dispatchers.IO) { db.userCardCollectionDao().getById(row)!! }; assertEquals(row,revived.id); assertEquals(3,revived.quantity); assertEquals(99L,revived.createdAt); assertFalse(revived.isForTrade)
            withContext(Dispatchers.IO) { val current=revived.copy(quantity=Int.MAX_VALUE); db.userCardCollectionDao().upsert(current); assertEquals(Int.MAX_VALUE,writeCollectionRow(db.userCardCollectionDao(),current,"apply-fixture",current.scryfallId,false,"NM","en",1,false,100L,strictOverflow=false).second.quantity) }
        } finally { db.close() }
    }
    @Test fun ownerSwitchDuringBatchRollsBackAndReturningOwnerResumes()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,MtgDatabase::class.java).build()
        try {
            val fixture=source(db); val action=command(db,fixture); val gate=gate(); val entered=CompletableDeferred<Unit>(); val release=CompletableDeferred<Unit>(); val b=TransferOwner.Account("b")
            val executor=RoomTransferCollectionExecutor(db,gate,{ 2L },{},checkpoint={ point,index -> if(point==TransferApplyCheckpoint.AFTER_INCREMENT && index==0) { entered.complete(Unit); release.await() } })
            coroutineScope {
                val work=async(Dispatchers.IO) { executor.runSlice(action,owner) }; entered.await()
                val transition=async(Dispatchers.IO) { gate.changeOwner(b) { db.collectionTransferDao().pauseOwnerJobs(key) } }
                gate.sessions.first { (it as? TransferSession.Available)?.owner==b }; release.complete(Unit)
                assertEquals(TransferCollectionApplyResult.WAITING,work.await()); transition.await()
            }
            assertEquals(0L to 0L,totals(db)); assertEquals("PAUSED_OWNER",db.collectionTransferDao().getJob(fixture.job,key)!!.phase)
            assertEquals(TransferCollectionApplyResult.WAITING,executor.runSlice(action,owner)); assertNull(db.collectionTransferDao().getJob(fixture.job,"account:b"))
            gate.changeOwner(owner); assertEquals(TransferCollectionApplyResult.FINISHED,RoomTransferCollectionExecutor(db,gate,{ 3L },{}).runSlice(action,owner)); assertEquals(2L to 2L,totals(db))
        } finally { db.close() }
    }
    @Test fun observedAuthGuardRejectsLoadingOrNewIdentityBeforeCollectorCatchesUp()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,MtgDatabase::class.java).build()
        try {
            val fixture=source(db); val action=command(db,fixture); val gate=gate(); var current=false
            val executor=RoomTransferCollectionExecutor(db,gate,{ 2L },{},matchesObservedOwner={ current })
            assertEquals(TransferCollectionApplyResult.WAITING,executor.runSlice(action,owner)); assertEquals(0L to 0L,totals(db))
            current=true
            val interrupted=RoomTransferCollectionExecutor(db,gate,{ 3L },{},checkpoint={ point,_ -> if(point==TransferApplyCheckpoint.AFTER_INCREMENT)current=false },matchesObservedOwner={ current })
            assertEquals(TransferCollectionApplyResult.WAITING,interrupted.runSlice(action,owner)); assertEquals(0L to 0L,totals(db)); assertNull(db.collectionTransferDao().history(key,"a".repeat(64)))
        } finally { db.close() }
    }
    @Test fun guestProvenanceNeverClaimsMatchingLegacyNullRows()=runBlocking {
        val name="apply-guest-${id()}"; var db=open(name)
        try {
            val guest=TransferOwner.VerifiedGuest(id()); val guestKey=ownerKey(guest); val fixture=source(db,1,guest,3L)
            val legacy=existing(db,"printing-0",9,user=null); val action=command(db,fixture,guest)
            val loading=TransferSessionGate(); assertEquals(TransferCollectionApplyResult.WAITING,RoomTransferCollectionExecutor(db,loading,{ 2L },{}).runSlice(action,guest))
            val executor=RoomTransferCollectionExecutor(db,gate(guest),{ 3L },{})
            assertEquals(TransferCollectionApplyResult.FINISHED,executor.runSlice(action,guest)); assertEquals(2L to 12L,totals(db))
            val verified=db.collectionTransferDao().verifiedGuestCollectionRow(guestKey,"printing-0",false,"NM","en")!!
            assertNotEquals(legacy,verified.id); assertEquals(3,verified.quantity); assertNull(verified.userId)
            assertNull(db.collectionTransferDao().verifiedGuestCollectionRow("guest:${id()}","printing-0",false,"NM","en"))
            assertEquals(9,withContext(Dispatchers.IO) { db.userCardCollectionDao().getById(legacy)!!.quantity })
            db.close(); db=open(name)
            assertEquals(verified.id,db.collectionTransferDao().verifiedGuestCollectionRow(guestKey,"printing-0",false,"NM","en")!!.id)
            assertEquals(TransferCollectionApplyResult.FINISHED,RoomTransferCollectionExecutor(db,gate(guest),{ 4L },{}).runSlice(action,guest)); assertEquals(2L to 12L,totals(db))
        } finally { db.close(); context.deleteDatabase(name) }
    }
    @Test fun mixedWishlistAndUndecidedEntriesNeverUseCollectionMarkers()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,MtgDatabase::class.java).build()
        try {
            val fixture=source(db,3); val dao=db.collectionTransferDao(); val first=fixture.entries[0]; val wish=fixture.entries[1]
            assertTrue(dao.editPendingEntry(fixture.job,key,first.id,0L,first.scryfallId,false,"NM","en",1L,TransferDestination.COLLECTION))
            assertTrue(dao.editPendingEntry(fixture.job,key,wish.id,0L,wish.scryfallId,false,"NM","en",1L,TransferDestination.WISHLIST))
            val generation=dao.getJob(fixture.job,key)!!.generation; val collection=TransferActionId(id()); val wishlist=TransferActionId(id())
            assertTrue(dao.confirmAction(key,TransferActionRequest(collection,TransferJobId(fixture.job),generation,TransferDestination.COLLECTION,TransferActionScope.Entry(first.id,1L)),1L))
            assertTrue(dao.confirmAction(key,TransferActionRequest(wishlist,TransferJobId(fixture.job),generation,TransferDestination.WISHLIST,TransferActionScope.Entry(wish.id,1L)),1L))
            val executor=RoomTransferCollectionExecutor(db,gate(),{ 2L },{})
            assertEquals(TransferCollectionApplyResult.WRONG_DESTINATION,executor.runSlice(wishlist,owner)); assertEquals(TransferCollectionApplyResult.FINISHED,executor.runSlice(collection,owner))
            assertEquals(1L to 1L,totals(db)); assertEquals(1L,dao.getJob(fixture.job,key)!!.appliedCopies); assertEquals("REVIEW_READY",dao.getJob(fixture.job,key)!!.phase)
            assertEquals(0L,dao.actionPage(wishlist.value,key,"").single().completedQuantity); assertEquals("PENDING",dao.reviewEntry(fixture.job,key,wish.id)!!.state)
            val remaining=fixture.entries[2]; assertTrue(dao.editPendingEntry(fixture.job,key,remaining.id,0L,remaining.scryfallId,false,"NM","en",2L,TransferDestination.WISHLIST))
            assertEquals(TransferWishlistApplyResult.UNAVAILABLE,PendingTransferWishlistExecutor().runSlice(wishlist,owner))
            assertNotNull(dao.history(key,"a".repeat(64))); assertFalse(dao.provenance(fixture.job,key,wish.id).single().participated)
        } finally { db.close() }
    }
    @Test fun eventCrashBeforeAcknowledgementReplaysOnlyDerivedSignal()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,MtgDatabase::class.java).build(); val events=AtomicInteger()
        try {
            val fixture=source(db,1); val action=command(db,fixture); val gate=gate()
            val executor=RoomTransferCollectionExecutor(db,gate,{ 2L },{ events.incrementAndGet() },checkpoint={ point,_ -> if(point==TransferApplyCheckpoint.BEFORE_EVENT_ACK)throw IllegalStateException("Injected event crash") })
            assertEquals(TransferCollectionApplyResult.FINISHED,executor.runSlice(action,owner)); assertEquals(1,events.get()); assertTrue(db.collectionTransferDao().getJob(fixture.job,key)!!.collectionEventPending)
            assertTrue(RoomTransferCollectionExecutor(db,gate,{ 3L },{ events.incrementAndGet() }).reconcileEvent(TransferJobId(fixture.job),owner)); assertEquals(2,events.get()); assertEquals(1L to 1L,totals(db))
            assertFalse(db.collectionTransferDao().getJob(fixture.job,key)!!.collectionEventPending)
        } finally { db.close() }
    }
    @Test fun lateOverflowNeverUnlocksOrRewritesAlreadyAppliedEntries()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,MtgDatabase::class.java).build()
        try {
            val fixture=source(db,501); val action=command(db,fixture); val gate=gate(); val dao=db.collectionTransferDao(); val executor=RoomTransferCollectionExecutor(db,gate,{ 2L },{})
            assertEquals(TransferCollectionApplyResult.CONTINUE,executor.runSlice(action,owner))
            val pending=dao.pendingCollectionActionPage(action.value,key).single(); val row=existing(db,pending.scryfallId,Int.MAX_VALUE)
            assertEquals(TransferCollectionApplyResult.REVIEW_REQUIRED,executor.runSlice(action,owner)); assertEquals(500L,dao.getJob(fixture.job,key)!!.appliedCopies)
            val applied=fixture.entries.first { it.id!=pending.entryId }; val marker=dao.reviewEntry(fixture.job,key,applied.id)!!
            assertEquals("APPLIED",marker.state); assertEquals(action.value,marker.activeActionId); assertEquals(1L,marker.appliedQuantity)
            assertFalse(dao.editPendingEntry(fixture.job,key,marker.id,marker.entryVersion,marker.scryfallId,false,"NM","en",2L,TransferDestination.WISHLIST))
            withContext(Dispatchers.IO) { db.userCardCollectionDao().upsert(db.userCardCollectionDao().getById(row)!!.copy(quantity=1)) }
            val remaining=dao.reviewEntry(fixture.job,key,pending.entryId)!!
            assertTrue(dao.editPendingEntry(fixture.job,key,remaining.id,remaining.entryVersion,remaining.scryfallId,false,"NM","en",1L,TransferDestination.COLLECTION))
            val next=TransferActionId(id()); assertTrue(dao.confirmAction(key,TransferActionRequest(next,TransferJobId(fixture.job),remaining.generation,TransferDestination.COLLECTION,TransferActionScope.Entry(remaining.id,remaining.entryVersion+1L)),3L))
            assertEquals(TransferCollectionApplyResult.FINISHED,executor.runSlice(next,owner)); assertEquals(501L,dao.getJob(fixture.job,key)!!.appliedCopies); assertEquals(501L to 502L,totals(db))
            assertEquals(marker,dao.reviewEntry(fixture.job,key,marker.id))
        } finally { db.close() }
    }
    @Test fun ownedRoomFlowClearsCachedEmissionAndReloadsReturningOwner()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,MtgDatabase::class.java).build(); val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
        try {
            val fixture=source(db,1); val dao=db.collectionTransferDao(); val gate=gate(); val b=TransferOwner.Account("b")
            val visible=MutableStateFlow<CollectionTransferJobEntity?>(null); val loaded=CompletableDeferred<Unit>(); val cleared=CompletableDeferred<Unit>()
            scope.launch { gate.observeOwned(owner) { dao.observeJob(fixture.job,key) }.collect { value -> visible.value=value; if(value!=null)loaded.complete(Unit) else if((gate.currentSession as? TransferSession.Available)?.owner==b)cleared.complete(Unit) } }
            withTimeout(5000L) { loaded.await() }; gate.changeOwner(b); withTimeout(5000L) { cleared.await() }; assertNull(visible.value)
            val next=dao.beginReviewRebuild(fixture.job,key)!!; withTimeout(5000L) { dao.observeJob(fixture.job,key).first { it?.generation==next } }; assertNull(visible.value)
            gate.changeOwner(owner); withTimeout(5000L) { visible.first { it?.generation==next } }; assertEquals(key,visible.value!!.ownerKey)
        } finally { withContext(NonCancellable) { scope.coroutineContext[Job]!!.cancelAndJoin(); db.close() } }
    }
    @Test fun authObserverPersistsExplicitGuestButLoadingNeverClaimsIt()=runBlocking<Unit> {
        val prefix="transfer-identity-test-${id()}"; val isolated=object : ContextWrapper(context) { override fun getSharedPreferences(name: String,mode: Int)=context.getSharedPreferences("$prefix-$name",mode) }
        val db=Room.inMemoryDatabaseBuilder(context,MtgDatabase::class.java).build(); val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
        try {
            val auth=MutableStateFlow<SessionState>(SessionState.Loading); val guest=VerifiedTransferGuestIdentity(isolated); val gate=TransferSessionGate(); val observer=TransferAuthSessionObserver(auth,guest,gate,db.collectionTransferDao()); observer.start(scope)
            val explicit=guest.ensureLocalIdentity(); assertEquals(explicit,VerifiedTransferGuestIdentity(isolated).read()); assertEquals(TransferSession.Loading,gate.currentSession)
            auth.value=SessionState.Unauthenticated
            withTimeout(5000L) { gate.sessions.first { (it as? TransferSession.Available)?.owner==explicit } }
            val account=TransferOwner.Account("observer-fixture"); auth.value=SessionState.Authenticated(AuthUser(account.id,null,null,null,null,"fixture"))
            withTimeout(5000L) { gate.sessions.first { (it as? TransferSession.Available)?.owner==account } }
            assertTrue(observer.matchesObserved(account)); assertFalse(observer.matchesObserved(explicit))
            auth.value=SessionState.Loading; assertFalse(observer.matchesObserved(account))
            withTimeout(5000L) { gate.sessions.first { it==TransferSession.Loading } }
        } finally { withContext(NonCancellable) { scope.coroutineContext[Job]!!.cancelAndJoin(); db.close(); context.deleteSharedPreferences("$prefix-collection_transfer_identity") } }
    }
}

