package com.mmg.manahub.core.data.local

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mmg.manahub.core.data.local.dao.CollectionTransferDao
import com.mmg.manahub.core.data.local.dao.TransferStoredResolution
import com.mmg.manahub.core.data.local.entity.*
import com.mmg.manahub.core.domain.collection.transfer.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class CollectionTransferPersistenceTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), MtgDatabase::class.java, emptyList(), FrameworkSQLiteOpenHelperFactory())
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val owner = "account:fixture-a"
    private val other = "account:fixture-b"
    private fun id() = UUID.randomUUID().toString()
    private fun open(name: String) = Room.databaseBuilder(context, MtgDatabase::class.java, name).addMigrations(MIGRATION_58_59, MIGRATION_59_60).build()

    @Test
    fun migration58to59PreservesEveryPredecessorTableAndOutboxes() {
        val name = "transfer-migration-test"
        context.deleteDatabase(name)
        val before = helper.createDatabase(name, 58)
        before.execSQL("INSERT INTO user_card_collection (id,user_id,scryfall_id,quantity,is_foil,condition,language,is_for_trade,is_deleted,updated_at,created_at) VALUES ('fixture-row','fixture-a','uncached-printing',12345,1,'LP','ja',0,0,1,1)")
        before.execSQL("INSERT INTO trade_offer_cleanup (proposal_id,user_id,collection_id) VALUES ('fixture-proposal','fixture-a','fixture-row')")
        before.execSQL("INSERT INTO trade_wishlist_cleanup (user_id,wishlist_id,target_quantity) VALUES ('fixture-a','fixture-wish',7)")
        val predecessorTables = mutableSetOf<String>()
        before.query("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'").use { cursor -> while (cursor.moveToNext()) predecessorTables += cursor.getString(0) }
        before.close()
        val after = helper.runMigrationsAndValidate(name, 59, true, MIGRATION_58_59)
        val successorTables = mutableSetOf<String>()
        after.query("SELECT name FROM sqlite_master WHERE type='table'").use { cursor -> while (cursor.moveToNext()) successorTables += cursor.getString(0) }
        assertTrue(successorTables.containsAll(predecessorTables))
        after.query("SELECT quantity,is_foil,condition,language FROM user_card_collection WHERE id='fixture-row'").use { cursor ->
            assertTrue(cursor.moveToFirst()); assertEquals(12345, cursor.getInt(0)); assertEquals(1, cursor.getInt(1)); assertEquals("LP", cursor.getString(2)); assertEquals("ja", cursor.getString(3))
        }
        after.query("SELECT COUNT(*) FROM trade_offer_cleanup").use { cursor -> cursor.moveToFirst(); assertEquals(1, cursor.getInt(0)) }
        after.query("SELECT target_quantity FROM trade_wishlist_cleanup").use { cursor -> cursor.moveToFirst(); assertEquals(7, cursor.getInt(0)) }
        after.query("PRAGMA foreign_key_list(user_card_collection)").use { assertEquals(0, it.count) }
        after.query("SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND (name LIKE 'collection_transfer_%' OR name LIKE 'collection_import_%')").use { cursor -> cursor.moveToFirst(); assertEquals(10, cursor.getInt(0)) }
        after.close()
        context.deleteDatabase(name)
    }

    private suspend fun create(dao: CollectionTransferDao, capturedOwner: String? = owner, generation: Long = 1L): Pair<String, String> {
        val job = id()
        val file = id()
        assertTrue(dao.createReceipt(CollectionTransferReceiptEntity(job, generation, capturedOwner, createdAt = 1L), listOf(CollectionTransferFileEntity(file, job, fileOrder = 0))))
        return job to file
    }

    private suspend fun prepare(dao: CollectionTransferDao, job: String, file: String, count: Int) {
        for (start in 1..count step 200) {
            val rows = (start..minOf(start + 199, count)).map { ordinal -> CollectionImportRowEntity(job, file, ordinal.toLong(), ordinal.toLong() * 10, ordinal.toLong() * 10 + 5, "DATA", name = "Fixture $ordinal", quantity = 1L) }
            assertTrue(dao.stageParserRows(job, owner, file, rows))
            assertTrue(dao.stageParserRows(job, owner, file, rows.map { it.copy(quantity = 999L) }))
        }
        assertTrue(dao.completeParsing(job, owner, file, TransferParseSummary(CollectionFileFormat.TEXT, count.toLong(), 0L, 0L, count.toLong(), count.toLong(), 0L, count.toLong(), emptyList())))
        for (start in 1..count step 75) assertTrue(dao.stageResolution(job, owner, file, (start..minOf(start + 74, count)).map { TransferStoredResolution(it.toLong(), "printing-$it", null) }))
        assertTrue(dao.completeResolution(job, owner, file))
    }

    @Test
    fun migration59to60KeepsMarkersAndDefaultsToUndecided() {
        val name="transfer-intent-migration"
        context.deleteDatabase(name)
        val before=helper.createDatabase(name,59)
        fun seed(table: String, overrides: Map<String,Any>) {
            val columns=mutableListOf<String>(); val values=mutableListOf<Any?>()
            before.query("PRAGMA table_info($table)").use { cursor ->
                while(cursor.moveToNext()) {
                    val column=cursor.getString(1)
                    columns+=column
                    values+=overrides[column] ?: if(cursor.getInt(3)==0) null else if(cursor.getString(2)=="INTEGER") 0L else ""
                }
            }
            before.execSQL("INSERT INTO $table (${columns.joinToString()}) VALUES (${columns.joinToString { "?" }})", values.toTypedArray())
        }
        seed("collection_transfer_jobs",mapOf("id" to "job", "owner_key" to owner,"phase" to "REVIEW_READY","applied_copies" to 7L))
        seed("collection_import_entries",mapOf("id" to "entry","job_id" to "job","scryfall_id" to "printing","quantity" to 12L,"applied_quantity" to 7L))
        before.close()
        val after=helper.runMigrationsAndValidate(name,60,true,MIGRATION_59_60)
        after.query("SELECT destination,entry_version,active_action_id,quantity,applied_quantity FROM collection_import_entries").use { c ->
            assertTrue(c.moveToFirst()); assertEquals("NONE",c.getString(0)); assertEquals(0L,c.getLong(1)); assertTrue(c.isNull(2)); assertEquals(12L,c.getLong(3)); assertEquals(7L,c.getLong(4))
        }
        after.query("SELECT applied_copies,intent_revision FROM collection_transfer_jobs").use { c -> c.moveToFirst(); assertEquals(7L,c.getLong(0)); assertEquals(0L,c.getLong(1)) }
        after.query("SELECT COUNT(*) FROM collection_transfer_actions").use { c -> c.moveToFirst(); assertEquals(0L,c.getLong(0)) }
        after.close(); context.deleteDatabase(name)
    }

    @Test
    fun mixedDestinationsFreezeOnlyChosenSubsetAndSurviveReopen() = runBlocking {
        val name="transfer-intent-subsets"
        context.deleteDatabase(name)
        var database=open(name)
        try {
            var dao=database.collectionTransferDao()
            val(job,file)=create(dao)
            assertTrue(dao.bindReceipt(job,owner,1L,"SAF",1L))
            prepare(dao,job,file,3)
            val generation=dao.beginReviewRebuild(job,owner)!!
            val entries=(1..3).map { CollectionImportEntryEntity("entry-$it",job,generation,"printing-$it",false,"NM","en",1L) }
            assertTrue(dao.stageReviewEntries(job,owner,generation,entries,entries.map { CollectionImportProvenanceEntity(job,it.id,file,generation,1L,1L) }))
            assertTrue(dao.publishReview(job,owner,generation,"fingerprint",2L))
            fun request(entry: String, version: Long, destination: TransferDestination) = TransferActionRequest(TransferActionId(id()),TransferJobId(job),generation,destination,TransferActionScope.Entry(entry,version))
            val noIntent=request("entry-1",0L,TransferDestination.COLLECTION)
            assertFalse(dao.confirmAction(owner,noIntent,3L))
            assertTrue(dao.acknowledgeReview(job,owner,TransferConfirmation(generation,dao.getJob(job,owner)!!.payloadVersion,false,false)))
            assertFalse(dao.freezeConfirmedPayload(job,owner))
            assertFalse(dao.editPendingEntry(job,other,"entry-1",0L,"printing-1",true,"LP","ja",12345L,TransferDestination.COLLECTION))
            assertTrue(dao.editPendingEntry(job,owner,"entry-1",0L,"printing-1",true,"LP","ja",12345L,TransferDestination.COLLECTION))
            val collection=request("entry-1",1L,TransferDestination.COLLECTION)
            assertTrue(dao.confirmAction(owner,collection,4L))
            assertFalse(dao.confirmAction(other,collection,4L))
            assertTrue(dao.editPendingEntry(job,owner,"entry-2",0L,"variant-2",false,"NM","en",2L,TransferDestination.WISHLIST))
            assertTrue(dao.freezeAction(collection.id.value,owner))
            assertFalse(dao.freezeAction(collection.id.value,other))
            assertFalse(dao.editPendingEntry(job,owner,"entry-1",1L,"printing-1",false,"NM","en",1L,TransferDestination.NONE))
            assertEquals("NONE",dao.reviewEntry(job,owner,"entry-3")!!.destination)
            val wishlist=request("entry-2",1L,TransferDestination.WISHLIST)
            assertTrue(dao.confirmAction(owner,wishlist,5L))
            assertTrue(dao.editPendingEntry(job,owner,"entry-2",1L,"variant-2",false,"NM","en",3L,TransferDestination.WISHLIST))
            assertFalse(dao.freezeAction(wishlist.id.value,owner))
            assertEquals("INVALIDATED",dao.action(wishlist.id.value,owner)!!.phase)
            val revised=request("entry-2",2L,TransferDestination.WISHLIST)
            assertTrue(dao.confirmAction(owner,revised,6L))
            assertTrue(dao.freezeAction(revised.id.value,owner))
            assertTrue(dao.editPendingEntry(job,owner,"entry-3",0L,"printing-3",false,"NM","en",4L,TransferDestination.WISHLIST))
            val revision=dao.getJob(job,owner)!!.intentRevision
            val bulk=TransferActionRequest(TransferActionId(id()),TransferJobId(job),generation,TransferDestination.WISHLIST,TransferActionScope.DestinationSelection(revision))
            assertFalse(dao.confirmAction(owner,bulk.copy(scope=TransferActionScope.DestinationSelection(revision-1L)),7L))
            assertTrue(dao.confirmAction(owner,bulk,7L))
            assertEquals(listOf("entry-3"),dao.actionPage(bulk.id.value,owner,"").map { it.entryId })
            assertFalse(dao.selectFile(job,owner,file,generation,false))
            database.close(); database=open(name); dao=database.collectionTransferDao()
            assertTrue(dao.freezeAction(collection.id.value,owner))
            assertEquals(12345L,dao.actionPage(collection.id.value,owner,"").single().quantity)
            assertEquals("ja",dao.actionPage(collection.id.value,owner,"").single().language)
            assertEquals(3L,dao.actionPage(revised.id.value,owner,"").single().quantity)
            assertTrue(dao.actionPage(collection.id.value,other,"").isEmpty())
            assertEquals(0L,dao.getJob(job,owner)!!.appliedCopies)
            assertEquals(0L,dao.getJob(job,owner)!!.appliedEntries)
            assertEquals(0L,dao.reviewEntry(job,owner,"entry-2")!!.appliedQuantity)
            assertTrue(dao.freezeAction(bulk.id.value,owner))
        } finally { database.close(); context.deleteDatabase(name) }
    }

    @Test
    fun replayPagesOwnersConsentAndFreezePersistAcrossReopen() = runBlocking {
        val name = "transfer-dao-reopen-test"
        context.deleteDatabase(name)
        var database = open(name)
        try {
            var dao = database.collectionTransferDao()
            val (job, file) = create(dao)
            assertTrue(dao.bindReceipt(job, owner, 1L, "SAF", 1L))
            assertTrue(dao.bindReceipt(job, owner, 1L, "SAF", 2L))
            assertNotNull(dao.observeJob(job, owner).first())
            assertNull(dao.observeJob(job, other).first())
            prepare(dao, job, file, 501)
            assertEquals(1L, dao.rowPage(job, owner, file, 0L).first().quantity)
            assertTrue(dao.rowPage(job, other, file, 0L).isEmpty())
            val generation = dao.beginReviewRebuild(job, owner)!!
            val entries = (1..501).map { ordinal -> CollectionImportEntryEntity(id(), job, generation, "printing-$ordinal", false, "NM", "en", 1L) }
            for (page in entries.chunked(200)) {
                assertTrue(dao.stageReviewEntries(job, owner, generation, page, page.map { CollectionImportProvenanceEntity(job, it.id, file, generation, 1L, 1L) }))
            }
            assertTrue(dao.publishReview(job, owner, generation, "fixture-fingerprint", 3L))
            assertTrue(dao.reviewPage(job, other, generation, "").isEmpty())
            assertTrue(dao.reviewPage(job, owner, generation + 1L, "").isEmpty())
            val seen = mutableListOf<String>()
            var after = ""
            while (true) {
                val page = dao.reviewPage(job, owner, generation, after)
                assertTrue(page.size <= 50)
                if (page.isEmpty()) break
                seen += page.map { it.id }
                after = page.last().id
            }
            assertEquals(501, seen.size)
            assertEquals(501, seen.distinct().size)
            assertEquals(entries.map { it.id }.sorted(), seen)
            assertEquals(1, dao.provenance(job, owner, entries.first().id).size)
            assertTrue(dao.provenance(job, other, entries.first().id).isEmpty())
            val version = dao.getJob(job, owner)!!.payloadVersion
            assertFalse(dao.acknowledgeReview(job, other, TransferConfirmation(generation, version, false, false)))
            assertFalse(dao.acknowledgeReview(job, owner, TransferConfirmation(generation - 1L, version, false, false)))
            assertTrue(dao.acknowledgeReview(job, owner, TransferConfirmation(generation, version, false, false)))
            database.close()
            database = open(name)
            dao = database.collectionTransferDao()
            val restored = dao.getJob(job, owner)!!
            assertEquals(generation, restored.confirmedGeneration)
            assertEquals(501L, restored.acceptedCopies)
            assertEquals(501L, dao.getFile(job, owner, file)!!.parseOrdinal)
            assertFalse(dao.freezeConfirmedPayload(job, owner))
            assertEquals(501L, dao.chooseAllDestination(job, owner, generation, 0L, TransferDestination.COLLECTION))
            val actionId = TransferActionId(id())
            assertTrue(dao.confirmAction(owner, TransferActionRequest(actionId, TransferJobId(job), generation, TransferDestination.COLLECTION, TransferActionScope.DestinationSelection(1L)), 4L))
            assertTrue(dao.freezeAction(actionId.value, owner))
            assertFalse(dao.selectFile(job, owner, file, generation, false))
            assertNull(dao.beginReviewRebuild(job, owner))
            assertEquals(501L, dao.getFile(job, owner, file)!!.resolveOrdinal)
            assertEquals("REVIEW_READY", dao.getJob(job, owner)!!.phase)
        } finally { database.close(); context.deleteDatabase(name) }
    }

    @Test
    fun loadingReceiptBindingAndFallbackBudgetAreOwnerBoundAndDurable() = runBlocking {
        val name = "transfer-receipt-claims-test"
        context.deleteDatabase(name)
        var database = open(name)
        try {
            var dao = database.collectionTransferDao()
            val (job, _) = create(dao, null, 0L)
            assertNull(dao.getJob(job, owner))
            assertFalse(dao.bindReceipt(job, owner, 1L, "SEND", 1L))
            assertTrue(dao.bindReceipt(job, owner, 1L, "SEND", 1L, destinationChosen = true))
            assertFalse(dao.bindReceipt(job, other, 1L, "SEND", 1L, destinationChosen = true))
            for (index in 1..100) assertTrue(dao.claimFallbackName(job, owner, "fixture-$index", 1L))
            assertFalse(dao.claimFallbackName(job, owner, "fixture-101", 1L))
            assertFalse(dao.claimFallbackName(job, other, "fixture-1", 1L))
            database.close()
            database = open(name)
            dao = database.collectionTransferDao()
            assertTrue(dao.claimFallbackName(job, owner, "fixture-1", 2L))
            assertFalse(dao.claimFallbackName(job, owner, "fixture-101", 2L))
        } finally { database.close(); context.deleteDatabase(name) }
    }

    @Test
    fun partialResolutionAndSnapshotCheckpointDoNotSkipOrExposeOtherOwners() = runBlocking {
        val name = "transfer-checkpoint-snapshot-test"
        context.deleteDatabase(name)
        val database = open(name)
        try {
            val dao = database.collectionTransferDao()
            val (job, file) = create(dao)
            assertTrue(dao.bindReceipt(job, owner, 1L, "PASTE", 1L))
            assertTrue(dao.stageParserRows(job, owner, file, (1..3).map { CollectionImportRowEntity(job, file, it.toLong(), it * 5L, it * 5L + 4L, "DATA", name = "Fixture", quantity = 1L) }))
            assertTrue(dao.completeParsing(job, owner, file, TransferParseSummary(CollectionFileFormat.TEXT, 3L, 0L, 0L, 3L, 3L, 0L, 3L, emptyList())))
            assertTrue(dao.stageResolution(job, owner, file, listOf(TransferStoredResolution(3L, "printing-3", null))))
            assertEquals(0L, dao.getFile(job, owner, file)!!.resolveOrdinal)
            assertEquals(listOf(1L, 2L), dao.pendingResolutionPage(job, owner, file, 0L).map { it.sourceOrdinal })
            assertFalse(dao.completeResolution(job, owner, file))
            assertFalse(dao.stageResolution(job, other, file, listOf(TransferStoredResolution(1L, "printing-1", null))))
            val query = CollectionTransferSnapshotQueryEntity(job, 1L, "COLLECTION", "fixture", "{}", "NAME", "NONE", true, "checkpoint-a")
            val row = CollectionTransferSnapshotRowEntity(job, 1L, 1L, "source-id", "printing-id", false, "LP", "ja", 12345L, "{}", "sort", "group", true)
            assertTrue(dao.stageSnapshot(job, owner, query, listOf(row)))
            assertTrue(runCatching { dao.stageSnapshot(job, owner, query.copy(phase = "READY", totalRows = 1L, totalCopies = 999L), listOf(row)) }.exceptionOrNull() is IllegalArgumentException)
            assertEquals("BUILDING", dao.snapshotQuery(job, owner, 1L)!!.phase)
            assertTrue(dao.stageSnapshot(job, owner, query.copy(phase = "READY", totalRows = 1L, totalCopies = 12345L), listOf(row.copy(quantity = 999L))))
            assertFalse(dao.stageSnapshot(job, owner, query, listOf(row)))
            assertEquals(12345L, dao.snapshotPage(job, owner, 1L, 0L).single().quantity)
            assertTrue(dao.snapshotPage(job, other, 1L, 0L).isEmpty())
            assertNull(dao.snapshotQuery(job, other, 1L))
        } finally { database.close(); context.deleteDatabase(name) }
    }

    @Test
    fun rejectedPrefixCannotContributeAndExclusionInvalidatesConsent() = runBlocking {
        val name = "transfer-rejected-sibling-test"
        context.deleteDatabase(name)
        val database = open(name)
        try {
            val dao = database.collectionTransferDao()
            val job = id()
            val good = id()
            val corrupt = id()
            assertTrue(dao.createReceipt(CollectionTransferReceiptEntity(job, 1L, owner, createdAt = 1L), listOf(CollectionTransferFileEntity(good, job, fileOrder = 0), CollectionTransferFileEntity(corrupt, job, fileOrder = 1))))
            assertTrue(dao.bindReceipt(job, owner, 1L, "SAF", 1L))
            assertTrue(runCatching { dao.stageParserRows(job, owner, corrupt, listOf(CollectionImportRowEntity(job, corrupt, 2L, 10L, 15L, "DATA", quantity = 1L))) }.exceptionOrNull() is IllegalArgumentException)
            assertEquals(0L, dao.getFile(job, owner, corrupt)!!.parseOrdinal)
            assertTrue(dao.rowPage(job, owner, corrupt, 0L).isEmpty())
            prepare(dao, job, good, 1)
            assertTrue(dao.stageParserRows(job, owner, corrupt, listOf(CollectionImportRowEntity(job, corrupt, 1L, 0L, 5L, "DATA", quantity = 1L))))
            assertTrue(dao.rejectFile(job, owner, corrupt, "INVALID_CSV"))
            assertTrue(dao.pendingResolutionPage(job, owner, corrupt, 0L).isEmpty())
            val oldGeneration = dao.getJob(job, owner)!!.generation
            assertFalse(dao.publishReview(job, owner, oldGeneration, "fixture", 1L))
            assertEquals("WAITING_FILE_DECISION", dao.getJob(job, owner)!!.phase)
            assertTrue(dao.selectFile(job, owner, corrupt, oldGeneration, false))
            val generation = dao.getJob(job, owner)!!.generation
            assertTrue(generation > oldGeneration)
            assertNull(dao.getJob(job, owner)!!.confirmedGeneration)
            val entry = CollectionImportEntryEntity(id(), job, generation, "printing-1", false, "NM", "en", 1L)
            assertTrue(dao.stageReviewEntries(job, owner, generation, listOf(entry), listOf(CollectionImportProvenanceEntity(job, entry.id, good, generation, 1L, 1L))))
            assertTrue(dao.publishReview(job, owner, generation, "fixture", 2L))
            val version = dao.getJob(job, owner)!!.payloadVersion
            assertFalse(dao.acknowledgeReview(job, owner, TransferConfirmation(generation, version, false, false)))
            assertTrue(dao.acknowledgeReview(job, owner, TransferConfirmation(generation, version, true, false)))
            assertEquals(1L, dao.getJob(job, owner)!!.acceptedCopies)
            assertEquals(1L, dao.getFile(job, owner, good)!!.parseOrdinal)
            assertEquals(1, dao.rowPage(job, owner, corrupt, 0L).size)
            assertEquals(entry.id, dao.rowPage(job, owner, good, 0L).single().entryId)
        } finally { database.close(); context.deleteDatabase(name) }
    }
}
