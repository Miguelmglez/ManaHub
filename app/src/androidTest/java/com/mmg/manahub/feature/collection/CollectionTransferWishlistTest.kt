package com.mmg.manahub.feature.collection

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mmg.manahub.core.data.local.*
import com.mmg.manahub.core.data.local.dao.TransferStoredResolution
import com.mmg.manahub.core.data.local.entity.*
import com.mmg.manahub.core.domain.collection.transfer.*
import com.mmg.manahub.feature.collection.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class CollectionTransferWishlistTest {
    @get:Rule val helper=MigrationTestHelper(InstrumentationRegistry.getInstrumentation(),MtgDatabase::class.java,emptyList(),FrameworkSQLiteOpenHelperFactory())
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val owner=TransferOwner.Account("wishlist-fixture")
    private val key="account:wishlist-fixture"
    private fun id()=UUID.randomUUID().toString()
    private fun key(owner: TransferOwner)=when(owner) { is TransferOwner.Account -> "account:${owner.id}"; is TransferOwner.VerifiedGuest -> "guest:${owner.installationToken}" }
    private suspend fun gate(owner: TransferOwner=this.owner)=TransferSessionGate().also { it.changeOwner(owner) }
    private data class Fixture(val job: String,val entries: List<CollectionImportEntryEntity>)
    private suspend fun source(db: MtgDatabase,count: Int=2,owner: TransferOwner=this.owner): Fixture {
        val job=id(); val file=id(); val key=key(owner); val dao=db.collectionTransferDao()
        assertTrue(dao.createReceipt(CollectionTransferReceiptEntity(job,1L,key,createdAt=1L),listOf(CollectionTransferFileEntity(file,job,fileOrder=0,phase="PARSING",sha256="b".repeat(64)))))
        assertTrue(dao.bindReceipt(job,key,1L,"SAF",1L))
        assertTrue(dao.stageParserRows(job,key,file,(0 until count).map { CollectionImportRowEntity(job,file,it+1L,it*2L,it*2L+1L,"DATA",name="Fixture",quantity=2L,isFoil=true,condition="LP",language="ja") }))
        assertTrue(dao.completeParsing(job,key,file,TransferParseSummary(CollectionFileFormat.TEXT,count.toLong(),0L,0L,count.toLong(),count.toLong(),0L,count*2L,emptyList())))
        assertTrue(dao.stageResolution(job,key,file,(0 until count).map { TransferStoredResolution(it+1L,"printing-$it",null) })); assertTrue(dao.completeResolution(job,key,file))
        assertEquals(TransferSliceResult.FINISHED,RoomTransferReviewBuilder(dao,{ TransferSession.Available(owner,1L) },{ 1L }).runSlice(TransferJobId(job),owner).result)
        return Fixture(job,dao.reviewPage(job,key,dao.getJob(job,key)!!.generation,""))
    }
    private suspend fun command(db: MtgDatabase,f: Fixture,owner: TransferOwner=this.owner,entry: CollectionImportEntryEntity?=null): TransferActionId {
        val dao=db.collectionTransferDao(); val key=key(owner); var job=dao.getJob(f.job,key)!!
        if(entry==null)assertEquals(f.entries.size.toLong(),dao.chooseAllDestination(job.id,key,job.generation,job.intentRevision,TransferDestination.WISHLIST))
        else assertTrue(dao.editPendingEntry(job.id,key,entry.id,entry.entryVersion,entry.scryfallId,entry.isFoil,entry.condition,entry.language,entry.quantity,TransferDestination.WISHLIST))
        job=dao.getJob(job.id,key)!!; val action=TransferActionId(id()); val scope=if(entry==null)TransferActionScope.DestinationSelection(job.intentRevision) else TransferActionScope.Entry(entry.id,entry.entryVersion+1L)
        assertTrue(dao.confirmAction(key,TransferActionRequest(action,TransferJobId(job.id),job.generation,TransferDestination.WISHLIST,scope,acceptRepeatedFiles=true),1L)); return action
    }
    private fun executor(db: MtgDatabase,gate: TransferSessionGate,checkpoint: suspend (TransferWishlistCheckpoint,Int)->Unit={ _,_ -> })=RoomTransferWishlistExecutor(db,gate,{ 2L },{ (gate.currentSession as? TransferSession.Available)?.owner==it },checkpoint)
    @Test fun migration62to63PreservesWishlistCollectionMarkersAndGuestProvenance() {
        val name="wishlist-migration-${id()}"; val before=helper.createDatabase(name,62)
        before.execSQL("INSERT INTO local_wishlists(id,scryfall_id,quantity,match_any_variant,is_foil,condition,language,synced,created_at,owner_user_id) VALUES ('legacy','printing',7,0,1,'LP','ja',0,1,NULL)")
        before.execSQL("INSERT INTO collection_transfer_guest_rows(row_id,owner_key) VALUES ('row','guest:fixture')")
        before.execSQL("INSERT INTO trade_wishlist_cleanup(user_id,wishlist_id,target_quantity) VALUES ('fixture','wish',4)")
        before.execSQL("INSERT INTO trade_offer_cleanup(proposal_id,user_id,collection_id) VALUES ('proposal','fixture','row')")
        before.execSQL("INSERT INTO user_card_collection(id,user_id,scryfall_id,quantity,is_foil,condition,language,is_for_trade,is_deleted,updated_at,created_at) VALUES ('row','fixture','printing',42,0,'NM','en',0,0,1,1)")
        before.execSQL("INSERT INTO collection_import_provenance(job_id,entry_id,file_id,generation,source_records,source_copies,participated) VALUES ('job','entry','file',1,1,2,1)"); before.close()
        val after=helper.runMigrationsAndValidate(name,63,true,MIGRATION_62_63)
        after.query("SELECT quantity,owner_user_id FROM local_wishlists").use { it.moveToFirst(); assertEquals(7,it.getInt(0)); assertTrue(it.isNull(1)) }
        for(table in listOf("collection_transfer_guest_rows","trade_wishlist_cleanup","trade_offer_cleanup"))after.query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); assertEquals(1,it.getInt(0)) }
        after.query("SELECT quantity FROM user_card_collection").use { it.moveToFirst(); assertEquals(42,it.getInt(0)) }
        after.query("SELECT participated,wishlist_participated FROM collection_import_provenance").use { it.moveToFirst(); assertEquals(1,it.getInt(0)); assertEquals(0,it.getInt(1)) }; after.close(); context.deleteDatabase(name)
    }
    @Test fun individualWishlistPreservesExactAttributesAndOtherDestinations()=runBlocking<Unit> {
        val db=Room.inMemoryDatabaseBuilder(context,MtgDatabase::class.java).build()
        try {
            val f=source(db,3); val action=command(db,f,entry=f.entries[0]); val dao=db.collectionTransferDao(); val gate=gate()
            assertEquals(TransferWishlistApplyResult.LOCAL_FINISHED,executor(db,gate).runSlice(action,owner))
            val row=db.localWishlistDao().getByScryfallId(f.entries[0].scryfallId,owner.id).single(); assertEquals(2,row.quantity); assertEquals(true,row.isFoil); assertEquals("LP",row.condition); assertEquals("ja",row.language); assertFalse(row.matchAnyVariant); assertFalse(row.synced)
            val applied=dao.reviewEntry(f.job,key,f.entries[0].id)!!; assertEquals("WISHLIST_APPLIED",applied.state); assertEquals(0L,applied.appliedQuantity)
            assertFalse(dao.editPendingEntry(f.job,key,applied.id,applied.entryVersion,applied.scryfallId,false,"NM","en",1L,TransferDestination.COLLECTION))
            val next=dao.reviewEntry(f.job,key,f.entries[1].id)!!; assertTrue(dao.editPendingEntry(f.job,key,next.id,next.entryVersion,next.scryfallId,false,"NM","en",3L,TransferDestination.COLLECTION))
            val collection=TransferActionId(id()); assertTrue(dao.confirmAction(key,TransferActionRequest(collection,TransferJobId(f.job),next.generation,TransferDestination.COLLECTION,TransferActionScope.Entry(next.id,next.entryVersion+1L)),3L))
            assertEquals(TransferWishlistApplyResult.WRONG_DESTINATION,executor(db,gate).runSlice(collection,owner))
            assertEquals(TransferCollectionApplyResult.FINISHED,RoomTransferCollectionExecutor(db,gate,{ 3L },{}).runSlice(collection,owner))
            assertEquals(1L,dao.getJob(f.job,key)!!.appliedEntries); assertEquals(3L,dao.getJob(f.job,key)!!.appliedCopies); assertEquals("NONE",dao.reviewEntry(f.job,key,f.entries[2].id)!!.destination)
            assertEquals(applied,dao.reviewEntry(f.job,key,applied.id)); assertTrue(dao.provenance(f.job,key,applied.id).single().wishlistParticipated); assertFalse(dao.provenance(f.job,key,applied.id).single().participated)
        } finally { db.close() }
    }
    @Test fun realRollbackAfterWriteMarkerAndProvenanceKeepsAllResultsPending()=runBlocking<Unit> {
        for(point in listOf(TransferWishlistCheckpoint.AFTER_WRITE,TransferWishlistCheckpoint.AFTER_MARKER,TransferWishlistCheckpoint.AFTER_PROVENANCE)) {
            val db=Room.inMemoryDatabaseBuilder(context,MtgDatabase::class.java).build()
            try {
                val f=source(db); val action=command(db,f); val gate=gate(); val dao=db.collectionTransferDao()
                assertEquals(TransferWishlistApplyResult.FAILED_RETRYABLE,executor(db,gate) { actual,_ -> if(actual==point)error("Injected storage failure") }.runSlice(action,owner))
                for(e in f.entries) { assertTrue(db.localWishlistDao().getByScryfallId(e.scryfallId,owner.id).isEmpty()); assertEquals("PENDING",dao.reviewEntry(f.job,key,e.id)!!.state); assertFalse(dao.provenance(f.job,key,e.id).single().wishlistParticipated) }
                assertTrue(dao.wishlistDeliveryPage(key,owner.id).isEmpty()); assertNull(dao.history(key,"b".repeat(64))); assertEquals(0L,dao.getJob(f.job,key)!!.appliedCopies)
                assertEquals(TransferWishlistApplyResult.LOCAL_FINISHED,executor(db,gate).runSlice(action,owner))
            } finally { db.close() }
        }
    }
    @Test fun independentExecutorsReopenAndPostcommitRetryIncrementExactlyOnce()=runBlocking<Unit> {
        val name="wishlist-reopen-${id()}"
        fun open()=Room.databaseBuilder(context,MtgDatabase::class.java,name).build()
        var db=open()
        try {
            val f=source(db); val action=command(db,f); val first=executor(db,gate()); val second=executor(db,gate())
            coroutineScope { awaitAll(async(Dispatchers.IO) { first.runSlice(action,owner) },async(Dispatchers.IO) { second.runSlice(action,owner) }) }
            db.close(); db=open(); assertEquals(TransferWishlistApplyResult.LOCAL_FINISHED,executor(db,gate()).runSlice(action,owner))
            for(e in f.entries)assertEquals(2,db.localWishlistDao().getByScryfallId(e.scryfallId,owner.id).single().quantity)
            assertEquals(0L,db.collectionTransferDao().getJob(f.job,key)!!.appliedCopies); assertFalse(db.collectionTransferDao().getJob(f.job,key)!!.collectionEventPending)
            val g=source(db,1); val a=command(db,g); assertEquals(TransferWishlistApplyResult.FAILED_RETRYABLE,executor(db,gate()) { point,_ -> if(point==TransferWishlistCheckpoint.AFTER_COMMIT)error("Injected postcommit crash") }.runSlice(a,owner))
            assertEquals(TransferWishlistApplyResult.LOCAL_FINISHED,executor(db,gate()).runSlice(a,owner)); assertEquals(4,db.localWishlistDao().getByScryfallId(g.entries.single().scryfallId,owner.id).single().quantity)
        } finally { db.close(); context.deleteDatabase(name) }
    }
    @Test fun failedDeliveryStaysDirtyOldAckAndRemotePullCannotOverwriteNewQuantity()=runBlocking<Unit> {
        val db=Room.inMemoryDatabaseBuilder(context,MtgDatabase::class.java).build()
        try {
            val f=source(db,1); val gate=gate(); assertEquals(TransferWishlistApplyResult.LOCAL_FINISHED,executor(db,gate).runSlice(command(db,f),owner))
            val store=RoomTransferWishlistDeliveryStore(db,gate,{ it==owner }); val old=store.pending(owner).single(); var fail=true
            val sync=TransferWishlistSync(gate,store,{ captured,_ -> assertEquals(owner,captured); if(fail)error("Ambiguous remote timeout") },{ it==owner })
            assertEquals(TransferSliceResult.WAITING,sync.runSlice(owner)); assertFalse(db.localWishlistDao().getById(old.id,owner.id)!!.synced)
            val g=source(db,1); executor(db,gate).runSlice(command(db,g),owner); val newest=store.pending(owner).single(); assertEquals(4,newest.quantity); assertEquals(2L,newest.revision)
            assertEquals(0,store.acknowledge(owner,listOf(old))); assertFalse(db.localWishlistDao().getById(old.id,owner.id)!!.synced)
            db.localWishlistDao().upsertRemoteProtected(listOf(db.localWishlistDao().getById(old.id,owner.id)!!.copy(quantity=1,synced=true)),owner.id); assertEquals(4,db.localWishlistDao().getById(old.id,owner.id)!!.quantity)
            assertTrue(db.localWishlistDao().getUnsynced(owner.id).isEmpty()); fail=false; assertEquals(TransferSliceResult.FINISHED,sync.runSlice(owner)); assertTrue(db.localWishlistDao().getById(old.id,owner.id)!!.synced); assertFalse(db.collectionTransferDao().wishlistDirty(old.id)!!.pending)
        } finally { db.close() }
    }
    @Test fun guestUsesExplicitLocalGuestAndNeverClaimsNullOwnerRows()=runBlocking<Unit> {
        val db=Room.inMemoryDatabaseBuilder(context,MtgDatabase::class.java).build()
        try {
            val guest=TransferOwner.VerifiedGuest(id()); val f=source(db,1,guest); val printing=f.entries.single().scryfallId
            db.localWishlistDao().insert(LocalWishlistEntity(id(),printing,9,false,true,"LP","ja",ownerUserId=null))
            val action=command(db,f,guest); assertEquals(TransferWishlistApplyResult.LOCAL_FINISHED,executor(db,gate(guest)).runSlice(action,guest))
            val wanted=db.localWishlistDao().getByScryfallId(printing,TradeListOwner.GUEST).single(); assertEquals(2,wanted.quantity); assertEquals(key(guest),db.collectionTransferDao().wishlistDirty(wanted.id)!!.ownerKey)
            assertTrue(db.collectionTransferDao().wishlistDeliveryPage(key,owner.id).isEmpty()); assertTrue(db.localWishlistDao().getUnsynced(owner.id).isEmpty()); assertEquals(0L,db.collectionTransferDao().getJob(f.job,key(guest))!!.appliedEntries)
        } finally { db.close() }
    }
    @Test fun overflowFromConfirmedUnlocksPendingAndRequiresCorrectedNewCommand()=runBlocking<Unit> {
        val db=Room.inMemoryDatabaseBuilder(context,MtgDatabase::class.java).build()
        try {
            val f=source(db,1); val e=f.entries.single(); val existing=LocalWishlistEntity(id(),e.scryfallId,Int.MAX_VALUE,false,e.isFoil,e.condition,e.language,ownerUserId=owner.id); db.localWishlistDao().insert(existing)
            val action=command(db,f); val gate=gate(); val dao=db.collectionTransferDao(); val originalDirty=dao.wishlistDirty(existing.id)
            assertEquals(TransferWishlistApplyResult.REVIEW_REQUIRED,executor(db,gate).runSlice(action,owner)); assertEquals(Int.MAX_VALUE,db.localWishlistDao().getById(existing.id,owner.id)!!.quantity); assertEquals(originalDirty,dao.wishlistDirty(existing.id)); assertEquals("REVIEW_REQUIRED",dao.action(action.value,key)!!.phase)
            val editable=dao.reviewEntry(f.job,key,e.id)!!; assertNull(editable.activeActionId); assertTrue(dao.editPendingEntry(f.job,key,e.id,editable.entryVersion,e.scryfallId,true,"LP","ja",1L,TransferDestination.WISHLIST))
            db.localWishlistDao().update(existing.copy(quantity=1)); val current=dao.reviewEntry(f.job,key,e.id)!!; val replacement=TransferActionId(id()); assertTrue(dao.confirmAction(key,TransferActionRequest(replacement,TransferJobId(f.job),current.generation,TransferDestination.WISHLIST,TransferActionScope.Entry(current.id,current.entryVersion)),3L))
            assertEquals(TransferWishlistApplyResult.LOCAL_FINISHED,executor(db,gate).runSlice(replacement,owner)); assertEquals(2,db.localWishlistDao().getById(existing.id,owner.id)!!.quantity)
        } finally { db.close() }
    }
    @Test fun ownerSwitchDuringWriteRollsBackAndReturningOwnerResumes()=runBlocking<Unit> {
        val db=Room.inMemoryDatabaseBuilder(context,MtgDatabase::class.java).build()
        try {
            val f=source(db,1); val action=command(db,f); val gate=gate(); val entered=CompletableDeferred<Unit>(); val release=CompletableDeferred<Unit>(); val b=TransferOwner.Account("other")
            val writer=executor(db,gate) { point,_ -> if(point==TransferWishlistCheckpoint.AFTER_WRITE) { entered.complete(Unit); release.await() } }
            coroutineScope { val apply=async(Dispatchers.IO) { writer.runSlice(action,owner) }; entered.await(); val change=async(Dispatchers.IO) { gate.changeOwner(b) }; gate.sessions.first { (it as? TransferSession.Available)?.owner==b }; release.complete(Unit); assertEquals(TransferWishlistApplyResult.WAITING,apply.await()); change.await() }
            assertTrue(db.localWishlistDao().getByScryfallId(f.entries.single().scryfallId,owner.id).isEmpty()); assertEquals(TransferWishlistApplyResult.WAITING,writer.runSlice(action,owner)); gate.changeOwner(owner); assertEquals(TransferWishlistApplyResult.LOCAL_FINISHED,executor(db,gate).runSlice(action,owner))
        } finally { db.close() }
    }
    @Test fun explicitFollowupAfterBulkKeepsWishlistSnapshotAndSourcesWithoutCloning()=runBlocking<Unit> {
        val name="wishlist-followup-${id()}"
        fun open()=Room.databaseBuilder(context,MtgDatabase::class.java,name).build()
        var db=open()
        try {
            val f=source(db); val gate=gate(); val wish=command(db,f); assertEquals(TransferWishlistApplyResult.LOCAL_FINISHED,executor(db,gate).runSlice(wish,owner))
            val dao=db.collectionTransferDao(); val snapshot=dao.actionPage(wish.value,key,""); val applied=dao.reviewEntry(f.job,key,f.entries[0].id)!!; val before=dao.getJob(f.job,key)!!
            assertFalse(dao.reopenWishlistEntry(f.job,"account:other",applied.id,applied.entryVersion,before.payloadVersion))
            assertTrue(dao.reopenWishlistEntry(f.job,key,applied.id,applied.entryVersion,before.payloadVersion))
            assertFalse(dao.reopenWishlistEntry(f.job,key,applied.id,applied.entryVersion,before.payloadVersion)); assertEquals(snapshot,dao.actionPage(wish.value,key,""))
            db.close(); db=open(); val reopened=db.collectionTransferDao().reviewEntry(f.job,key,applied.id)!!
            assertEquals("NONE",reopened.destination); assertEquals("PENDING",reopened.state); assertNull(reopened.activeActionId); assertEquals(applied.entryVersion+1L,reopened.entryVersion)
            assertTrue(db.collectionTransferDao().editPendingEntry(f.job,key,reopened.id,reopened.entryVersion,reopened.scryfallId,reopened.isFoil,reopened.condition,reopened.language,reopened.quantity,TransferDestination.COLLECTION))
            val collect=TransferActionId(id()); assertTrue(db.collectionTransferDao().confirmAction(key,TransferActionRequest(collect,TransferJobId(f.job),reopened.generation,TransferDestination.COLLECTION,TransferActionScope.Entry(reopened.id,reopened.entryVersion+1L)),3L))
            assertEquals(TransferCollectionApplyResult.FINISHED,RoomTransferCollectionExecutor(db,gate,{ 3L },{}).runSlice(collect,owner)); assertEquals(TransferCollectionApplyResult.FINISHED,RoomTransferCollectionExecutor(db,gate,{ 3L },{}).runSlice(collect,owner))
            assertEquals(2,db.localWishlistDao().getByScryfallId(reopened.scryfallId,owner.id).single().quantity); assertEquals(snapshot,db.collectionTransferDao().actionPage(wish.value,key,""))
            assertEquals(2,db.userCardCollectionDao().getByCompositeKey(owner.id,reopened.scryfallId,true,"LP","ja")!!.quantity)
            assertEquals("WISHLIST_APPLIED",db.collectionTransferDao().reviewEntry(f.job,key,f.entries[1].id)!!.state)
            assertEquals(2L,db.collectionTransferDao().getJob(f.job,key)!!.dataRecords); assertEquals(2,db.collectionTransferDao().reviewPage(f.job,key,reopened.generation,"").size)
            assertEquals(1,db.collectionTransferDao().provenance(f.job,key,reopened.id).size); assertTrue(db.collectionTransferDao().provenance(f.job,key,reopened.id).single().wishlistParticipated)
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
