package com.mmg.manahub.feature.collection

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mmg.manahub.core.data.local.*
import com.mmg.manahub.core.data.local.dao.*
import com.mmg.manahub.core.data.local.entity.*
import com.mmg.manahub.core.domain.collection.transfer.*
import com.mmg.manahub.feature.collection.data.RoomTransferReviewBuilder
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class CollectionTransferReviewTest {
    @get:Rule val helper=MigrationTestHelper(InstrumentationRegistry.getInstrumentation(),MtgDatabase::class.java,emptyList(),FrameworkSQLiteOpenHelperFactory())
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val owner=TransferOwner.Account("review-fixture")
    private val key="account:review-fixture"
    private val other="account:other"
    private fun id()=UUID.randomUUID().toString()
    private fun open(name: String)=Room.databaseBuilder(context,MtgDatabase::class.java,name).addMigrations(MIGRATION_60_61).build()
    private fun builder(db: MtgDatabase)=RoomTransferReviewBuilder(db.collectionTransferDao(),{ TransferSession.Available(owner,1L) },{ 1L })
    private data class Fixture(val job: String,val files: List<String>)
    private suspend fun source(db: MtgDatabase,quantities: List<List<Long>>,sameHash: Boolean=false,distinct: Boolean=false): Fixture {
        val job=id(); val files=quantities.map { id() }; val dao=db.collectionTransferDao()
        assertTrue(dao.createReceipt(CollectionTransferReceiptEntity(job,1L,key,createdAt=1L),files.mapIndexed { n,file -> CollectionTransferFileEntity(file,job,fileOrder=n,phase="PARSING",sha256=if(sameHash)"a".repeat(64) else (n+1).toString(16).repeat(64)) }))
        assertTrue(dao.bindReceipt(job,key,1L,"SAF",1L))
        for((n,file) in files.withIndex()) {
            val values=quantities[n]
            for(start in values.indices step 200) assertTrue(dao.stageParserRows(job,key,file,(start until minOf(start+200,values.size)).map { ordinal -> CollectionImportRowEntity(job,file,ordinal+1L,ordinal*2L,ordinal*2L+1L,"DATA",name="Fixture",quantity=values[ordinal]) }))
            assertTrue(dao.completeParsing(job,key,file,TransferParseSummary(CollectionFileFormat.TEXT,values.size.toLong(),0L,0L,values.size.toLong(),values.size.toLong(),0L,values.sum(),emptyList())))
            for(start in values.indices step 75) assertTrue(dao.stageResolution(job,key,file,(start until minOf(start+75,values.size)).map { TransferStoredResolution(it+1L,if(distinct)"printing-$it" else "printing",null) }))
            assertTrue(dao.completeResolution(job,key,file))
        }
        return Fixture(job,files)
    }
    private suspend fun finish(db: MtgDatabase,job: String) {
        repeat(10) { if(builder(db).runSlice(TransferJobId(job),owner).result==TransferSliceResult.FINISHED)return }
        fail("Review did not finish")
    }
    private suspend fun entries(db: MtgDatabase,job: String): List<CollectionImportEntryEntity> {
        val dao=db.collectionTransferDao(); val generation=dao.getJob(job,key)!!.generation
        val result=mutableListOf<CollectionImportEntryEntity>(); var cursor=""
        while(true) { val page=dao.reviewPage(job,key,generation,cursor); if(page.isEmpty())break; assertTrue(page.size<=50); result+=page; cursor=page.last().id }
        return result
    }
    @Test fun destinationAndMetadataEditsPreserveTotalsWhileQuantityAndExclusionRecompute()=runBlocking<Unit> {
        val name="review-counters-${id()}";val db=open(name)
        try {
            val fixture=source(db,listOf(listOf(2L,3L,4L)),distinct=true);finish(db,fixture.job)
            val dao=db.collectionTransferDao();val original=entries(db,fixture.job).single { it.scryfallId=="printing-0" }
            val before=dao.getJob(fixture.job,key)!!
            val consent=TransferConfirmation(before.generation,before.payloadVersion,false,false)
            assertTrue(dao.acknowledgeReview(fixture.job,key,consent))
            suspend fun edit(quantity: Long,excluded: Boolean,foil: Boolean=false) {
                val entry=dao.reviewEntry(fixture.job,key,original.id)!!
                val job=dao.getJob(fixture.job,key)!!
                assertTrue(dao.editPendingEntry(fixture.job,key,entry.id,entry.entryVersion,entry.scryfallId,foil,"LP","ja",quantity,TransferDestination.WISHLIST,excluded))
                val changed=dao.getJob(fixture.job,key)!!
                assertEquals(job.payloadVersion+1L,changed.payloadVersion)
                assertEquals(job.intentRevision+1L,changed.intentRevision)
                assertEquals(entry.entryVersion+1L,dao.reviewEntry(fixture.job,key,entry.id)!!.entryVersion)
                assertNull(changed.confirmedPayloadVersion)
                assertFalse(dao.acknowledgeReview(fixture.job,key,consent))
            }
            edit(2L,false);assertEquals(9L,dao.getJob(fixture.job,key)!!.acceptedCopies)
            edit(2L,false,true);assertEquals(9L,dao.getJob(fixture.job,key)!!.acceptedCopies)
            edit(7L,false,true);assertEquals(14L,dao.getJob(fixture.job,key)!!.acceptedCopies)
            edit(7L,true,true);assertEquals(7L,dao.getJob(fixture.job,key)!!.acceptedCopies)
            edit(100L,true,true);assertEquals(7L,dao.getJob(fixture.job,key)!!.acceptedCopies)
            edit(100L,false,true);assertEquals(107L,dao.getJob(fixture.job,key)!!.acceptedCopies)
        } finally { db.close();context.deleteDatabase(name) }
    }
    @Test fun migration60to61PreservesDestinationsCommandsAndOutboxes() {
        val name="review-migration-${id()}"; val before=helper.createDatabase(name,60)
        before.execSQL("INSERT INTO collection_import_entries(id,job_id,generation,scryfall_id,is_foil,condition,language,quantity,applied_quantity,state,excluded,error,destination,entry_version,active_action_id) VALUES ('entry','job',3,'printing',0,'NM','en',7,0,'PENDING',0,NULL,'WISHLIST',2,NULL)")
        before.execSQL("INSERT INTO collection_transfer_actions VALUES ('action','job','account:review-fixture',3,'WISHLIST','entry',2,'CONFIRMED',1)")
        before.execSQL("INSERT INTO trade_wishlist_cleanup(user_id,wishlist_id,target_quantity) VALUES ('fixture','wish',9)")
        before.close(); val after=helper.runMigrationsAndValidate(name,61,true,MIGRATION_60_61)
        after.query("SELECT destination,quantity,source_key,payload_edited FROM collection_import_entries").use { assertTrue(it.moveToFirst()); assertEquals("WISHLIST",it.getString(0)); assertEquals(7L,it.getLong(1)); assertEquals(transferSourceKey("printing",false,"NM","en"),it.getString(2)); assertEquals(1,it.getInt(3)) }
        after.query("SELECT COUNT(*) FROM collection_transfer_actions").use { it.moveToFirst(); assertEquals(1,it.getInt(0)) }
        after.query("SELECT target_quantity FROM trade_wishlist_cleanup").use { it.moveToFirst(); assertEquals(9,it.getInt(0)) }
        after.query("PRAGMA foreign_key_list(user_card_collection)").use { assertEquals(0,it.count) }
        after.close(); context.deleteDatabase(name)
    }
    @Test fun contributionsRebuildWithoutDoubleCountingAndExclusionRevokesConsent()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,MtgDatabase::class.java).build()
        try {
            val fixture=source(db,listOf(listOf(2L),listOf(3L))); val dao=db.collectionTransferDao(); finish(db,fixture.job)
            val first=entries(db,fixture.job).single(); assertEquals(5L,first.quantity); assertEquals("NONE",first.destination)
            assertEquals(listOf(2L,3L),dao.provenance(fixture.job,key,first.id).map { it.sourceCopies }.sorted())
            val job=dao.getJob(fixture.job,key)!!; val old=TransferConfirmation(job.generation,job.payloadVersion,false,false)
            assertTrue(dao.acknowledgeReview(fixture.job,key,old)); assertNotNull(dao.beginReviewRebuild(fixture.job,key)); finish(db,fixture.job)
            assertEquals(first.id,entries(db,fixture.job).single().id); assertEquals(5L,entries(db,fixture.job).single().quantity)
            assertFalse(dao.acknowledgeReview(fixture.job,key,old))
            assertTrue(dao.selectFile(fixture.job,key,fixture.files[1],dao.getJob(fixture.job,key)!!.generation,false)); finish(db,fixture.job)
            assertEquals(2L,entries(db,fixture.job).single().quantity); assertEquals(1,dao.provenance(fixture.job,key,first.id).size)
            assertNull(dao.getJob(fixture.job,key)!!.confirmedGeneration)
        } finally { db.close() }
    }
    @Test fun changedMembershipKeepsVisibleDecisionAndNeverHiddenExcludedCopies()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,MtgDatabase::class.java).build()
        try {
            val fixture=source(db,listOf(listOf(2L),listOf(3L))); val dao=db.collectionTransferDao(); finish(db,fixture.job)
            val original=entries(db,fixture.job).single()
            assertTrue(dao.editPendingEntry(fixture.job,key,original.id,0L,"edited-printing",true,"LP","ja",9L,TransferDestination.WISHLIST))
            assertTrue(dao.selectFile(fixture.job,key,fixture.files[1],dao.getJob(fixture.job,key)!!.generation,false)); finish(db,fixture.job)
            val revised=entries(db,fixture.job).single(); assertEquals(2L,revised.quantity); assertEquals("WISHLIST",revised.destination); assertEquals("SOURCE_MEMBERSHIP_CHANGED",revised.error)
            val decision=dao.reviewDecisionPage(fixture.job,key,"").single(); assertEquals(9L,decision.quantity); assertEquals("edited-printing",decision.scryfallId)
            assertTrue(dao.reviewDecisionPage(fixture.job,other,"").isEmpty()); assertFalse(dao.decideReviewEdit(fixture.job,other,original.id,revised.generation,TransferReviewDecision.KEEP_EDIT))
            assertFalse(dao.editPendingEntry(fixture.job,key,revised.id,revised.entryVersion,"printing",false,"NM","en",2L,TransferDestination.COLLECTION))
            assertTrue(dao.decideReviewEdit(fixture.job,key,revised.id,revised.generation,TransferReviewDecision.USE_SOURCE))
            assertEquals(2L,entries(db,fixture.job).single().quantity); assertEquals("WISHLIST",entries(db,fixture.job).single().destination)
            assertTrue(dao.reviewDecisionPage(fixture.job,key,"").isEmpty())
        } finally { db.close() }
    }
    @Test fun cursorReopenPreservesEditedVariantMixedDestinationsAndStableIds()=runBlocking {
        val name="review-reopen-${id()}"; var db=open(name)
        try {
            val fixture=source(db,listOf(List(501) { 1L }),distinct=true)
            assertEquals(TransferSliceResult.CONTINUE,builder(db).runSlice(TransferJobId(fixture.job),owner,maxBatches=1).result)
            assertEquals(200,entries(db,fixture.job).size); assertTrue(db.collectionTransferDao().getJob(fixture.job,key)!!.workCursor!!.startsWith("review:"))
            db.close(); db=open(name); finish(db,fixture.job)
            val all=entries(db,fixture.job); assertEquals(501,all.size); assertEquals(501,all.map { it.id }.distinct().size)
            val dao=db.collectionTransferDao(); val first=all[0]; val second=all[1]
            assertTrue(dao.editPendingEntry(fixture.job,key,first.id,0L,"user-variant",true,"LP","ja",7L,TransferDestination.WISHLIST))
            assertTrue(dao.editPendingEntry(fixture.job,key,second.id,0L,second.scryfallId,false,"NM","en",1L,TransferDestination.COLLECTION))
            assertNotNull(dao.beginReviewRebuild(fixture.job,key)); finish(db,fixture.job); db.close(); db=open(name)
            val reopened=entries(db,fixture.job); assertEquals(all.map { it.id },reopened.map { it.id })
            assertEquals("user-variant",reopened.first { it.id==first.id }.scryfallId); assertEquals(7L,reopened.first { it.id==first.id }.quantity)
            assertEquals("WISHLIST",reopened.first { it.id==first.id }.destination); assertEquals("COLLECTION",reopened.first { it.id==second.id }.destination)
            assertEquals(499,reopened.count { it.destination=="NONE" }); assertEquals(0L,db.collectionTransferDao().getJob(fixture.job,key)!!.appliedCopies)
        } finally { db.close(); context.deleteDatabase(name) }
    }
    @Test fun rejectedPrefixNeverContributesAndReplacementRetainsInventory()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,MtgDatabase::class.java).build()
        try {
            val fixture=source(db,listOf(listOf(2L),listOf(3L))); val dao=db.collectionTransferDao()
            assertTrue(dao.rejectFile(fixture.job,key,fixture.files[1],"STRUCTURAL_FILE"))
            assertEquals(TransferSliceResult.WAITING,builder(db).runSlice(TransferJobId(fixture.job),owner).result)
            assertTrue(entries(db,fixture.job).isEmpty())
            assertTrue(dao.selectFile(fixture.job,key,fixture.files[1],dao.getJob(fixture.job,key)!!.generation,false)); finish(db,fixture.job)
            assertEquals(2L,entries(db,fixture.job).single().quantity); assertNull(dao.rowPage(fixture.job,key,fixture.files[1],0L).single().entryId)
            val replacement=id()
            assertTrue(dao.replaceFailedSource(fixture.job,key,dao.getJob(fixture.job,key)!!.generation,fixture.files[1],CollectionTransferFileEntity(replacement,fixture.job,jobId=fixture.job,fileOrder=1,phase="PARSING",sha256="d".repeat(64),sourcePath="collection-transfers/${fixture.job}/$replacement/source")))
            assertEquals(3,dao.fileInventoryPage(fixture.job,key,"").size); assertTrue(dao.getFile(fixture.job,key,fixture.files[1])!!.retired)
            assertEquals(fixture.files[1],dao.getFile(fixture.job,key,replacement)!!.replacesId)
        } finally { db.close() }
    }
    @Test fun duplicateDefaultsExplicitMultiplicityAndSelectionBudgetAreDurable()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,MtgDatabase::class.java).build()
        try {
            val fixture=source(db,listOf(listOf(1L),listOf(1L),listOf(1L)),sameHash=true); val dao=db.collectionTransferDao()
            val sql=db.openHelper.writableDatabase
            for(file in fixture.files.take(2)) {
                sql.execSQL("DELETE FROM collection_import_rows WHERE file_id=?",arrayOf(file))
                sql.execSQL("WITH RECURSIVE n(v) AS (SELECT 1 UNION ALL SELECT v+1 FROM n WHERE v<50000) INSERT INTO collection_import_rows(job_id,file_id,source_ordinal,byte_start,byte_end,kind,name,quantity,is_foil,condition,language,state,resolved_id,preview,source_group_key) SELECT ?,?,v,v,v+1,'DATA','Fixture',1,0,'NM','en','RESOLVED','printing','',? FROM n",arrayOf(fixture.job,file,transferSourceKey("printing",false,"NM","en")))
                sql.execSQL("UPDATE collection_transfer_files SET data_records=50000,valid_records=50000,resolved_records=50000,copies=50000,parse_ordinal=50000,resolve_ordinal=50000 WHERE id=?",arrayOf(file))
            }
            sql.execSQL("UPDATE collection_transfer_files SET sha256=? WHERE id=?",arrayOf("c".repeat(64),fixture.files[2]))
            finish(db,fixture.job); assertEquals(50001L,entries(db,fixture.job).single().quantity)
            assertFalse(dao.getFile(fixture.job,key,fixture.files[1])!!.selected)
            assertFalse(dao.selectFile(fixture.job,key,fixture.files[1],dao.getJob(fixture.job,key)!!.generation,true))
            assertTrue(dao.selectFile(fixture.job,key,fixture.files[1],dao.getJob(fixture.job,key)!!.generation,true,acceptRepeat=true))
            assertEquals(TransferSliceResult.WAITING,builder(db).runSlice(TransferJobId(fixture.job),owner).result)
            assertEquals("SELECTION_LIMIT",dao.getJob(fixture.job,key)!!.error)
            assertTrue(dao.selectFile(fixture.job,key,fixture.files[2],dao.getJob(fixture.job,key)!!.generation,false)); finish(db,fixture.job)
            assertEquals(100000L,entries(db,fixture.job).single().quantity); assertEquals(100000L,dao.getJob(fixture.job,key)!!.dataRecords)
            assertFalse(dao.getJob(fixture.job,key)!!.acceptRepeats)
            assertEquals(2,dao.provenance(fixture.job,key,entries(db,fixture.job).single().id).size)
            assertEquals(100000L,dao.provenance(fixture.job,key,entries(db,fixture.job).single().id).sumOf { it.sourceRecords })
        } finally { db.close() }
    }
    @Test fun hashHistoryAndPendingFingerprintNeverExposeAnotherOwner()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,MtgDatabase::class.java).build()
        try {
            val first=source(db,listOf(listOf(2L)),sameHash=true); finish(db,first.job)
            db.openHelper.writableDatabase.execSQL("INSERT INTO collection_transfer_file_history(owner_key,sha256,last_job_id,participated_at) VALUES (?,?,?,1)",arrayOf(key,"a".repeat(64),first.job))
            val second=source(db,listOf(listOf(2L)),sameHash=true)
            val result=builder(db).runSlice(TransferJobId(second.job),owner)
            assertEquals(TransferJobId(first.job),result.resumeJob)
            assertTrue(db.collectionTransferDao().getFile(second.job,key,second.files[0])!!.priorParticipation)
            assertNull(db.collectionTransferDao().history(other,"a".repeat(64)))
            assertNull(db.collectionTransferDao().matchingPendingJob(second.job,other,db.collectionTransferDao().getJob(second.job,key)!!.fingerprint!!))
            assertTrue(db.collectionTransferDao().fileInventoryPage(second.job,other,"").isEmpty())
        } finally { db.close() }
    }
    @Test fun removedOverrideRemainsDurableAcrossMultipleSelectionChanges()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,MtgDatabase::class.java).build()
        try {
            val fixture=source(db,listOf(listOf(2L),listOf(3L),listOf(1L)),distinct=true)
            val dao=db.collectionTransferDao()
            db.openHelper.writableDatabase.execSQL("UPDATE collection_import_rows SET resolved_id='other',source_group_key=? WHERE file_id=?",arrayOf(transferSourceKey("other",false,"NM","en"),fixture.files[0]))
            finish(db,fixture.job)
            val edited=entries(db,fixture.job).first { it.scryfallId=="other" }
            assertTrue(dao.editPendingEntry(fixture.job,key,edited.id,0L,"manual",true,"LP","ja",9L,TransferDestination.WISHLIST))
            assertTrue(dao.selectFile(fixture.job,key,fixture.files[0],dao.getJob(fixture.job,key)!!.generation,false))
            assertTrue(dao.selectFile(fixture.job,key,fixture.files[2],dao.getJob(fixture.job,key)!!.generation,false))
            finish(db,fixture.job)
            assertEquals(3L,entries(db,fixture.job).single().quantity)
            val decision=dao.reviewDecisionPage(fixture.job,key,"").single(); assertEquals("SOURCE_REMOVED",decision.reason); assertEquals(9L,decision.quantity)
            assertNull(dao.reviewEntry(fixture.job,key,edited.id))
            assertFalse(dao.decideReviewEdit(fixture.job,key,edited.id,decision.generation,TransferReviewDecision.KEEP_EDIT))
            assertTrue(dao.decideReviewEdit(fixture.job,key,edited.id,decision.generation,TransferReviewDecision.DISMISS_REMOVED))
            assertTrue(dao.reviewDecisionPage(fixture.job,key,"").isEmpty())
        } finally { db.close() }
    }    @Test fun oldAcknowledgementCannotReturnAfterEntryOrBulkEdits()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,MtgDatabase::class.java).build()
        try {
            val fixture=source(db,listOf(listOf(2L))); finish(db,fixture.job); val dao=db.collectionTransferDao()
            var entry=entries(db,fixture.job).single()
            for(change in 0..2) {
                val job=dao.getJob(fixture.job,key)!!
                val confirmation=TransferConfirmation(job.generation,job.payloadVersion,false,false)
                assertTrue(dao.acknowledgeReview(fixture.job,key,confirmation))
                assertTrue(dao.editPendingEntry(fixture.job,key,entry.id,entry.entryVersion,if(change==2)"edited" else entry.scryfallId,change==2,if(change==2)"LP" else entry.condition,if(change==2)"ja" else entry.language,if(change==1)7L else entry.quantity,TransferDestination.WISHLIST))
                assertFalse(dao.acknowledgeReview(fixture.job,key,confirmation))
                val revised=dao.getJob(fixture.job,key)!!; assertEquals(job.payloadVersion+1L,revised.payloadVersion); assertNull(revised.confirmedGeneration)
                assertTrue(dao.acknowledgeReview(fixture.job,key,TransferConfirmation(revised.generation,revised.payloadVersion,false,false)))
                entry=dao.reviewEntry(fixture.job,key,entry.id)!!
                assertEquals(entry.quantity,dao.getJob(fixture.job,key)!!.acceptedCopies)
            }
            val before=dao.getJob(fixture.job,key)!!; val old=TransferConfirmation(before.generation,before.payloadVersion,false,false)
            assertEquals(1L,dao.chooseAllDestination(fixture.job,key,before.generation,before.intentRevision,TransferDestination.COLLECTION))
            assertFalse(dao.acknowledgeReview(fixture.job,key,old)); assertEquals(before.payloadVersion+1L,dao.getJob(fixture.job,key)!!.payloadVersion)
            assertEquals(0L,dao.getJob(fixture.job,key)!!.appliedCopies)
        } finally { db.close() }
    }    @Test fun overflowIsExactAndFrozenSubsetStillAllowsPendingDecisions()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,MtgDatabase::class.java).build()
        try {
            val overflow=source(db,listOf(listOf(Int.MAX_VALUE.toLong()),listOf(1L))); finish(db,overflow.job)
            val dao=db.collectionTransferDao(); val entry=entries(db,overflow.job).single(); assertEquals(2147483648L,entry.quantity); assertEquals("QUANTITY_OVERFLOW",entry.error)
            assertEquals(2147483648L,dao.provenance(overflow.job,key,entry.id).sumOf { it.sourceCopies })
            val fixture=source(db,listOf(listOf(1L,1L)),distinct=true); finish(db,fixture.job)
            val first=entries(db,fixture.job)[0]; val pending=entries(db,fixture.job)[1]
            assertTrue(dao.editPendingEntry(fixture.job,key,first.id,0L,first.scryfallId,false,"NM","en",1L,TransferDestination.COLLECTION))
            val action=id(); assertTrue(dao.confirmAction(key,TransferActionRequest(TransferActionId(action),TransferJobId(fixture.job),first.generation,TransferDestination.COLLECTION,TransferActionScope.Entry(first.id,1L)),1L)); assertTrue(dao.freezeAction(action,key))
            assertFalse(dao.selectFile(fixture.job,key,fixture.files[0],first.generation,false)); assertNull(dao.beginReviewRebuild(fixture.job,key))
            assertTrue(dao.editPendingEntry(fixture.job,key,pending.id,0L,pending.scryfallId,false,"NM","en",1L,TransferDestination.WISHLIST))
            assertEquals(1L,dao.actionPage(action,key,"").single().quantity); assertEquals("WISHLIST",dao.reviewEntry(fixture.job,key,pending.id)!!.destination)
            assertEquals(0L,dao.getJob(fixture.job,key)!!.appliedCopies)
        } finally { db.close() }
    }
}



