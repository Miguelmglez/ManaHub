package com.mmg.manahub.feature.collection

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mmg.manahub.core.data.local.MtgDatabase
import com.mmg.manahub.core.data.local.entity.*
import com.mmg.manahub.core.domain.collection.transfer.*
import com.mmg.manahub.core.domain.repository.CardLookupIdentifier
import com.mmg.manahub.feature.collection.data.RoomTransferResolutionStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class CollectionTransferResolutionTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val owner=TransferOwner.Account("fixture-resolution")
    private val session=TransferSession.Available(owner,1L)
    private val key="account:fixture-resolution"
    private fun id()=UUID.randomUUID().toString()
    private suspend fun source(db: MtgDatabase,count: Int,names: Boolean=false,sibling: Boolean=false): Pair<TransferJobId,TransferFileId> {
        val dao=db.collectionTransferDao(); val job=TransferJobId(id()); val file=TransferFileId(id())
        val files=mutableListOf(CollectionTransferFileEntity(file.value,job.value,fileOrder=0))
        if(sibling) files+=CollectionTransferFileEntity(id(),job.value,fileOrder=1)
        assertTrue(dao.createReceipt(CollectionTransferReceiptEntity(job.value,1L,key,createdAt=1L),files))
        assertTrue(dao.bindReceipt(job.value,key,1L,"SAF",1L))
        for(start in 1..count step 200) assertTrue(dao.stageParserRows(job.value,key,file.value,(start..minOf(start+199,count)).map { n -> CollectionImportRowEntity(job.value,file.value,n.toLong(),n.toLong(),n.toLong()+1L,"DATA",name="Name $n",scryfallId=if(names)null else "printing-$n",quantity=1L) }))
        assertTrue(dao.completeParsing(job.value,key,file.value,TransferParseSummary(CollectionFileFormat.TEXT,count.toLong(),0L,0L,count.toLong(),count.toLong(),0L,count.toLong(),emptyList())))
        if(sibling) {
            val second=files[1].id
            assertTrue(dao.stageParserRows(job.value,key,second,listOf(CollectionImportRowEntity(job.value,second,1L,0L,1L,"DATA",name="Name 1",quantity=1L),CollectionImportRowEntity(job.value,second,2L,2L,3L,"DATA",name="Name 101",quantity=1L))))
            assertTrue(dao.completeParsing(job.value,key,second,TransferParseSummary(CollectionFileFormat.TEXT,2L,0L,0L,2L,2L,0L,2L,emptyList())))
        }
        return job to file
    }
    private class Gateway : TransferResolutionGateway {
        val requests=mutableListOf<List<CardLookupIdentifier>>()
        var failed=false; var missing=false
        val names=mutableListOf<String>()
        override suspend fun cached(identifier: CardLookupIdentifier): TransferPrinting?=null
        override suspend fun lookup(identifiers: List<CardLookupIdentifier>): TransferLookupBatch {
            requests+=identifiers
            return when {
                failed -> TransferLookupBatch(emptyList(),emptyList(),TransferLookupFailure.RETRYABLE)
                missing -> TransferLookupBatch(emptyList(),identifiers)
                else -> TransferLookupBatch(identifiers.map { TransferPrinting(it.scryfallId!!,"Plains","set","1") },emptyList())
            }
        }
        override suspend fun fallbackName(name: String): TransferNameLookup { names+=name; return TransferNameLookup(notFound=true) }
    }

    @Test fun durableResultsCursorAndRemaining85SurviveReopen()=runBlocking {
        val name="transfer-resolver-${id()}"
        var db=Room.databaseBuilder(context,MtgDatabase::class.java,name).build()
        try {
            val(job,file)=source(db,160)
            val gateway=Gateway(); var now=0L
            var store=RoomTransferResolutionStore(db.collectionTransferDao(),{ session })
            assertEquals(TransferSliceResult.CONTINUE,DurableTransferResolver(store,gateway,{ now }).runSlice(job,owner,maxBatches=1))
            gateway.failed=true
            assertEquals(TransferSliceResult.WAITING,DurableTransferResolver(store,gateway,{ now }).runSlice(job,owner,maxBatches=1))
            assertEquals(75L,db.collectionTransferDao().getFile(job.value,key,file.value)!!.resolveOrdinal)
            assertEquals("${file.value}:75",db.collectionTransferDao().getJob(job.value,key)!!.workCursor)
            assertEquals(1,db.collectionTransferDao().getJob(job.value,key)!!.networkFailures)
            assertEquals(85,db.collectionTransferDao().pendingResolutionPage(job.value,key,file.value,0L).size)
            db.close(); db=Room.databaseBuilder(context,MtgDatabase::class.java,name).build()
            now=2000L; gateway.failed=false
            store=RoomTransferResolutionStore(db.collectionTransferDao(),{ session })
            assertEquals(TransferSliceResult.FINISHED,DurableTransferResolver(store,gateway,{ now }).runSlice(job,owner))
            assertEquals(listOf(75,75,75,10),gateway.requests.map { it.size })
            assertTrue(gateway.requests.last().all { it.scryfallId!!.removePrefix("printing-").toInt()>150 })
            val persisted=db.collectionTransferDao().getFile(job.value,key,file.value)!!
            assertEquals("REVIEW_READY",persisted.phase); assertEquals(160L,persisted.resolvedRecords)
            assertEquals(0,db.collectionTransferDao().getJob(job.value,key)!!.networkFailures)
            assertEquals(0L,db.collectionTransferDao().getJob(job.value,key)!!.appliedCopies)
            assertTrue(db.collectionTransferDao().reviewPage(job.value,key,0L,"").isEmpty())
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun fallbackClaimBudgetSurvivesReopenAndCrossFileClaimsReuseNames()=runBlocking {
        val name="transfer-resolver-names-${id()}"
        var db=Room.databaseBuilder(context,MtgDatabase::class.java,name).build()
        try {
            val(job,_)=source(db,101,names=true,sibling=true)
            var store=RoomTransferResolutionStore(db.collectionTransferDao(),{ session })
            repeat(100) { assertTrue(store.claimName(job,owner,"name $it",1L)) }
            db.close(); db=Room.databaseBuilder(context,MtgDatabase::class.java,name).build()
            store=RoomTransferResolutionStore(db.collectionTransferDao(),{ session })
            assertTrue(store.claimName(job,owner,"name 0",2L))
            assertFalse(store.claimName(job,owner,"another name",2L))
            val gateway=Gateway().apply { missing=true }
            assertEquals(TransferSliceResult.FINISHED,DurableTransferResolver(store,gateway,{ 3000L }).runSlice(job,owner))
            assertEquals(100,gateway.names.size)
            assertEquals(103L,db.collectionTransferDao().currentFiles(job.value,key).sumOf { it.unresolvedRecords })
            assertFalse(store.claimName(job,TransferOwner.Account("other"),"name 0",2L))
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun persistedRetryGatesNeverTurnNetworkWaitsIntoUnknownRows()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,MtgDatabase::class.java).build()
        try {
            val(job,file)=source(db,1)
            val dao=db.collectionTransferDao()
            assertTrue(dao.waitPreparation(job.value,key,1000L,false))
            assertTrue(dao.waitPreparation(job.value,key,2000L,false))
            assertEquals(0,dao.getJob(job.value,key)!!.networkFailures)
            repeat(5) { assertTrue(dao.waitPreparation(job.value,key,3000L,true)) }
            assertEquals("FAILED_RETRYABLE",dao.getJob(job.value,key)!!.phase)
            assertEquals(5,dao.getJob(job.value,key)!!.networkFailures)
            assertEquals("PENDING",dao.rowPage(job.value,key,file.value,0L).single().state)
            assertFalse(dao.waitPreparation(job.value,"account:other",9999L,true))
            assertEquals(3000L,dao.getJob(job.value,key)!!.nextAttemptAt)
        } finally { db.close() }
    }

    @Test fun exactCacheQueriesAndTransferHydrationPreserveAllTags()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,MtgDatabase::class.java).build()
        try {
            val card=CardEntity(scryfallId="printing",name="Front // Back",printedName=null,lang="en",manaCost=null,cmc=0.0,colors="[]",colorIdentity="[]",typeLine="Land",printedTypeLine=null,oracleText=null,printedText=null,keywords="[]",power=null,toughness=null,loyalty=null,setCode="set",setName="Set",collectorNumber="1",rarity="common",releasedAt="2026-01-01",imageNormal=null,imageArtCrop=null,imageBackNormal=null,priceUsd=null,priceUsdFoil=null,priceEur=null,priceEurFoil=null,legalityStandard="legal",legalityPioneer="legal",legalityModern="legal",legalityCommander="legal",flavorText=null,artist=null,scryfallUri="",tags="confirmed",userTags="manual",suggestedTags="suggested")
            db.cardDao().upsert(card)
            assertEquals("printing",db.cardDao().findTransferPrinting("SET","1")!!.scryfallId)
            assertNull(db.cardDao().findTransferPrinting("other","1"))
            assertEquals("printing",db.cardDao().findTransferName("Back","SET")!!.scryfallId)
            assertNull(db.cardDao().findTransferName("Front","other"))
            db.cardDao().cacheTransferCards(listOf(card.copy(tags="[]",userTags="[]",suggestedTags="[]",name="Updated")))
            val updated=db.cardDao().getById("printing")!!
            assertEquals("Updated",updated.name); assertEquals("confirmed",updated.tags); assertEquals("manual",updated.userTags); assertEquals("suggested",updated.suggestedTags)
        } finally { db.close() }
    }
}
