package com.mmg.manahub.feature.collection

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mmg.manahub.core.data.local.*
import com.mmg.manahub.core.data.local.entity.*
import com.mmg.manahub.core.data.local.mapper.toDomainCard
import com.mmg.manahub.core.domain.auth.*
import com.mmg.manahub.core.domain.collection.transfer.*
import com.mmg.manahub.core.domain.search.AdvancedSearchCardMatcher
import com.mmg.manahub.core.model.*
import com.mmg.manahub.feature.collection.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.channels.Channel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class CollectionSelectionSnapshotTest {
    @get:Rule val helper=MigrationTestHelper(InstrumentationRegistry.getInstrumentation(),MtgDatabase::class.java,emptyList(),FrameworkSQLiteOpenHelperFactory())
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val owner=TransferOwner.Account("snapshot-fixture")
    private fun id()=UUID.randomUUID().toString()
    private fun auth()=SessionState.Authenticated(AuthUser(owner.id,null,null,null,null,"fixture"))
    private fun card(index: Int)=CardEntity(scryfallId="printing-$index",name=listOf("Zebra","Alpha","Token","Land","Echo","Token")[index%6],printedName=null,lang="en",manaCost=null,cmc=(index%8).toDouble(),colors=if(index%3==0)"[\"W\"]" else "[]",colorIdentity="[]",typeLine=listOf("Creature","Instant","Token","Land","Artifact","Token")[index%6],printedTypeLine=null,oracleText="draw a card",printedText=null,keywords="[]",power=null,toughness=null,loyalty=null,setCode=if(index%2==0)"aaa" else "bbb",setName=if(index%2==0)"Zulu set" else "Alpha set",collectorNumber=index.toString(),rarity=listOf("common","rare","special","mythic","uncommon","bonus")[index%6],releasedAt="2026-01-01",imageNormal="image-$index",imageArtCrop=null,imageBackNormal=null,priceUsd=if(index%2==0)null else index.toDouble(),priceUsdFoil=null,priceEur=null,priceEurFoil=null,legalityStandard="legal",legalityPioneer="legal",legalityModern="legal",legalityCommander="legal",flavorText=null,artist=null,scryfallUri="",oracleId="oracle-$index")
    private fun query(sort: CollectionSelectionSort=CollectionSelectionSort.NAME,ascending: Boolean=true,grouping: CollectionGroupingMode=CollectionGroupingMode.NONE,source: CollectionSource=CollectionSource.COLLECTION,search: String="",advanced: AdvancedSearchQuery?=null)=CollectionSelectionQuery(source,search,advanced,sort,ascending,grouping)

    @Test fun candidateOwnershipMatchesSiblingsAndLiveOwnerChangesWithoutFullCollection()=runBlocking<Unit> { fixture { db,gate,state,_,_ ->
        val observer=TransferAuthSessionObserver(state,VerifiedTransferGuestIdentity(context),gate,db.collectionTransferDao())
        val repository=RoomCollectionOwnershipRepository(db,gate,observer)
        val sibling=card(0).copy(scryfallId="different-printing",name="Different name").toDomainCard()
        val sameName=card(1).copy(scryfallId="name-sibling",oracleId="different-oracle").toDomainCard()
        val missing=card(3).copy(scryfallId="missing",oracleId="missing-oracle",name="Missing").toDomainCard()
        assertEquals(setOf(sibling.scryfallId,sameName.scryfallId),repository.lookup(owner,listOf(sibling,sameName,missing)))
        assertEquals(setOf(sibling.scryfallId),repository.lookup(owner,List(200) { sibling }))
        try { repository.lookup(owner,List(201) { sibling });fail("Lookup must remain bounded") } catch(_: IllegalArgumentException) { }
        val candidates=MutableStateFlow(CollectionOwnershipCandidates(emptyList(),sibling))
        val values=Channel<Set<String>>(Channel.UNLIMITED)
        val job=launch(Dispatchers.Default) { repository.observe(candidates).collect { values.send(it) } }
        try {
            withTimeout(10000) { while(sibling.oracleId !in values.receive()) { } }
            state.value=SessionState.Loading
            assertEquals(emptySet<String>(),withTimeout(10000) { values.receive() })
            state.value=auth()
            withTimeout(10000) { while(sibling.oracleId !in values.receive()) { } }
            candidates.value=CollectionOwnershipCandidates(emptyList(),missing)
            withTimeout(10000) { while(values.receive().isNotEmpty()) { } }
            db.cardDao().upsert(card(0).copy(scryfallId="new-owned",oracleId=missing.oracleId,name=missing.name))
            db.userCardCollectionDao().upsert(UserCardCollectionEntity("new-owned-row",owner.id,"new-owned",1))
            withTimeout(10000) { while(missing.oracleId !in values.receive()) { } }
        } finally { job.cancelAndJoin();values.close() }
    } }

    @Test fun candidateOwnershipRejectsAmbiguousAndOtherTokenGuestRows()=runBlocking<Unit> { fixture { db,gate,state,_,_ ->
        val preferences=context.getSharedPreferences("collection_transfer_identity",android.content.Context.MODE_PRIVATE)
        val previous=preferences.getString("verified_guest",null)
        try {
            val identity=VerifiedTransferGuestIdentity(context);val guest=identity.ensureLocalIdentity()
            state.value=SessionState.Unauthenticated;gate.changeOwner(guest)
            db.userCardCollectionDao().upsert(UserCardCollectionEntity("ambiguous",null,"printing-0",90))
            db.userCardCollectionDao().upsert(UserCardCollectionEntity("other-token",null,"printing-1",90))
            db.userCardCollectionDao().upsert(UserCardCollectionEntity("current-token",null,"printing-2",7))
            db.openHelper.writableDatabase.execSQL("INSERT INTO collection_transfer_guest_rows(row_id,owner_key) VALUES(?,?)",arrayOf("other-token","guest:${id()}"))
            db.openHelper.writableDatabase.execSQL("INSERT INTO collection_transfer_guest_rows(row_id,owner_key) VALUES(?,?)",arrayOf("current-token",guest.storageKey()))
            val repository=RoomCollectionOwnershipRepository(db,gate,TransferAuthSessionObserver(state,identity,gate,db.collectionTransferDao()))
            assertEquals(setOf("printing-2"),repository.lookup(guest,(0..2).map { card(it).toDomainCard() }))
        } finally { preferences.edit().also { if(previous==null)it.remove("verified_guest") else it.putString("verified_guest",previous) }.commit() }
    } }
    private suspend fun fixture(block: suspend (MtgDatabase,TransferSessionGate,MutableStateFlow<SessionState>,RoomCollectionSelectionRepository,List<UserCardWithCard>)->Unit) {
        val db=Room.inMemoryDatabaseBuilder(context,MtgDatabase::class.java).build()
        try {
            val gate=TransferSessionGate().also { it.changeOwner(owner) }
            val state=MutableStateFlow<SessionState>(auth())
            val observer=TransferAuthSessionObserver(state,VerifiedTransferGuestIdentity(context),gate,db.collectionTransferDao())
            val cards=(0..5).map(::card)
            db.cardDao().upsertAll(cards)
            val rows=(0..5).map { index -> UserCardCollectionEntity("row-$index",owner.id,cards[index].scryfallId,quantity=index+1,createdAt=(index+1)*10L) }+
                UserCardCollectionEntity("row-extra",owner.id,cards[0].scryfallId,quantity=7,isFoil=true,createdAt=100L)
            db.userCardCollectionDao().upsertAll(rows)
            val legacy=rows.map { row -> UserCardWithCard(UserCard(row.id,row.scryfallId,row.quantity,row.isFoil,row.condition,row.language,createdAt=row.createdAt),cards.first { it.scryfallId==row.scryfallId }.toDomainCard()) }
            block(db,gate,state,RoomCollectionSelectionRepository(db,gate,observer),legacy)
        } finally { db.close() }
    }
    private fun legacy(rows: List<UserCardWithCard>,query: CollectionSelectionQuery): List<CollectionCardGroup> {
        val advanced=query.advanced
        val groups=rows.filter { (query.search.isBlank() || it.card.name.contains(query.search,true)) && (advanced==null || AdvancedSearchCardMatcher.matches(it.card,advanced)) }.groupByCard()
        fun weight(card: Card)=when(card.rarity.lowercase()) { "mythic"->4;"rare"->3;"uncommon"->2;else->1 }
        val primary=when(query.sort) {
            CollectionSelectionSort.NAME -> compareBy<CollectionCardGroup> { it.card.name }
            CollectionSelectionSort.PRICE -> compareBy { it.card.priceUsd ?: 0.0 }
            CollectionSelectionSort.RARITY -> compareBy { weight(it.card) }
            CollectionSelectionSort.DATE_ADDED -> compareBy { it.latestAddedAt }
        }.let { if(query.ascending)it else it.reversed() }
        val sorted=groups.sortedWith(primary.thenBy { it.card.name }.thenBy { it.groupKey })
        return groupCollection(sorted,query.grouping).flatMap { it.items }
    }
    @Test fun allSortDirectionsAndSectionModesMatchLegacyAfterFiltering()=runBlocking<Unit> { fixture { _,_,_,repository,rows ->
        for(sort in CollectionSelectionSort.entries)for(ascending in listOf(true,false))for(grouping in CollectionGroupingMode.entries) {
            val q=query(sort,ascending,grouping)
            val summary=repository.capture(owner,q)
            val actual=repository.page(owner,summary.id,0L).groups
            val expected=legacy(rows,q)
            assertEquals("$sort/$ascending/$grouping",expected.map { it.groupKey },actual.map { it.groupKey })
            assertEquals(expected.map { it.totalQuantity.toLong() },actual.map { it.quantity })
            assertEquals(expected.map { it.distinctCopies.toLong() },actual.map { it.distinctCopies })
            assertEquals(expected.map { it.card.scryfallId },actual.map { it.card.scryfallId })
            assertEquals(rows.sumOf { it.userCard.quantity.toLong() },summary.copies)
            repository.discard(owner,summary.id)
        }
        for(q in listOf(query(search="a"),query(advanced=AdvancedSearchQuery(listOf(SearchCriterion.CardType(setOf("Creature"))))),query(advanced=AdvancedSearchQuery(listOf(SearchCriterion.Colors(setOf("W"),ColorMatchMode.EXACTLY)))))) {
            val summary=repository.capture(owner,q)
            assertEquals(legacy(rows,q).map { it.groupKey },repository.page(owner,summary.id,0L).groups.map { it.groupKey })
            repository.discard(owner,summary.id)
        }
    } }
    @Test fun snapshotFreezesQuantitiesMetadataMembershipAndLongTotals()=runBlocking<Unit> { fixture { db,_,_,repository,_ ->
        val summary=repository.capture(owner,query())
        val original=repository.page(owner,summary.id,0L).groups
        db.userCardCollectionDao().upsert(UserCardCollectionEntity("row-0",owner.id,"printing-0",99,createdAt=10L))
        db.cardDao().upsert(card(0).copy(name="Changed remotely",priceUsd=900.0))
        assertEquals(original,repository.page(owner,summary.id,0L).groups)
        db.userCardCollectionDao().upsert(UserCardCollectionEntity("row-0",owner.id,"printing-0",Int.MAX_VALUE,createdAt=10L))
        db.userCardCollectionDao().upsert(UserCardCollectionEntity("row-extra",owner.id,"printing-0",Int.MAX_VALUE,isFoil=true,createdAt=100L))
        val updated=repository.capture(owner,query())
        assertEquals(4294967294L,repository.page(owner,updated.id,0L).groups.first { it.card.scryfallId=="printing-0" }.quantity)
        assertEquals(summary.copies,original.sumOf { it.quantity })
    } }
    @Test fun foreignImageOverrideFreezesCachedEnglishSiblingAndMissingMetadataStillCounts()=runBlocking<Unit> { fixture { db,_,_,repository,_ ->
        db.cardDao().upsert(card(0).copy(lang="ja",imageNormal="foreign-image"))
        db.cardDao().upsert(card(0).copy(scryfallId="english-sibling",lang="en",imageNormal="english-image"))
        db.userCardCollectionDao().upsert(UserCardCollectionEntity("uncached",owner.id,"uncached-printing",11,createdAt=1000L))
        val frozen=repository.capture(owner,query())
        assertEquals(1L,frozen.missingMetadataRows)
        val first=repository.page(owner,frozen.id,0L).groups
        assertEquals("english-image",first.first { it.card.scryfallId=="printing-0" }.card.imageNormal)
        assertEquals(11L,first.single { it.card.scryfallId=="uncached-printing" }.quantity)
        db.cardDao().upsert(card(0).copy(scryfallId="english-sibling",lang="en",imageNormal="changed-image"))
        assertEquals(first,repository.page(owner,frozen.id,0L).groups)
    } }
    @Test fun verifiedGuestNeverReadsAmbiguousLegacyNullRows()=runBlocking<Unit> { fixture { db,gate,state,_,_ ->
        val preferences=context.getSharedPreferences("collection_transfer_identity",android.content.Context.MODE_PRIVATE)
        val previous=preferences.getString("verified_guest",null)
        try {
            val guestIdentity=VerifiedTransferGuestIdentity(context);val guest=guestIdentity.ensureLocalIdentity()
            state.value=SessionState.Unauthenticated;gate.changeOwner(guest)
            db.userCardCollectionDao().upsert(UserCardCollectionEntity("legacy-null",null,"printing-0",90))
            db.userCardCollectionDao().upsert(UserCardCollectionEntity("proven-guest",null,"printing-1",7))
            db.openHelper.writableDatabase.execSQL("INSERT INTO collection_transfer_guest_rows(row_id,owner_key) VALUES(?,?)",arrayOf("proven-guest","guest:${guest.installationToken}"))
            db.localWishlistDao().insert(LocalWishlistEntity("legacy-guest-wish","printing-1",90,false,false,"NM","en",ownerUserId="local_guest"))
            db.localOpenForTradeDao().insert(LocalOpenForTradeEntity("legacy-guest-trade","proven-guest","printing-1",90,ownerUserId="local_guest"))
            val repository=RoomCollectionSelectionRepository(db,gate,TransferAuthSessionObserver(state,guestIdentity,gate,db.collectionTransferDao()))
            val snapshot=repository.capture(guest,query())
            assertEquals(7L,snapshot.copies);assertEquals("printing-1",repository.page(guest,snapshot.id,0L).groups.single().card.scryfallId)
            assertEquals(0L,repository.capture(guest,query(source=CollectionSource.WISHLIST)).copies)
            assertEquals(0L,repository.capture(guest,query(source=CollectionSource.FOR_TRADE)).copies)
            val otherGuest=TransferOwner.VerifiedGuest(UUID.randomUUID().toString())
            preferences.edit().putString("verified_guest",otherGuest.installationToken).commit();guestIdentity.read();gate.changeOwner(otherGuest)
            assertEquals(0L,repository.capture(otherGuest,query()).copies)
            assertEquals(0L,repository.capture(otherGuest,query(source=CollectionSource.WISHLIST)).copies)
            assertEquals(0L,repository.capture(otherGuest,query(source=CollectionSource.FOR_TRADE)).copies)
            assertEquals(90,db.localWishlistDao().getById("legacy-guest-wish","local_guest")!!.quantity)
            assertEquals(90,db.userCardCollectionDao().getById("legacy-null")!!.quantity)
        } finally { preferences.edit().also { if(previous==null)it.remove("verified_guest") else it.putString("verified_guest",previous) }.commit() }
    } }
    @Test fun sourceQueriesAreOwnerBoundAndSyntheticIdsDoNotBecomeCollectionRows()=runBlocking<Unit> { fixture { db,gate,state,repository,_ ->
        db.localWishlistDao().insert(LocalWishlistEntity("wish","printing-1",8,false,false,"NM","en",ownerUserId=owner.id))
        db.localWishlistDao().insert(LocalWishlistEntity("foreign-wish","printing-0",90,false,false,"NM","en",ownerUserId="other"))
        db.localOpenForTradeDao().insert(LocalOpenForTradeEntity("trade","row-2","printing-2",9,ownerUserId=owner.id))
        val wish=repository.capture(owner,query(source=CollectionSource.WISHLIST))
        val trade=repository.capture(owner,query(source=CollectionSource.FOR_TRADE))
        assertEquals(8L,wish.copies);assertEquals(9L,trade.copies)
        assertNull(db.userCardCollectionDao().getById("wish"));assertNull(db.userCardCollectionDao().getById("trade"))
        state.value=SessionState.Authenticated(AuthUser("other",null,null,null,null,"fixture"));gate.changeOwner(TransferOwner.Account("other"))
        try { repository.page(owner,wish.id,0L);fail("Old owner must not read") } catch(_: TransferReadException) { }
        state.value=auth();gate.changeOwner(owner)
        assertEquals(8L,repository.page(owner,wish.id,0L).groups.single().quantity)
    } }
    @Test fun keysetPagesRemainStableAcrossReopenAndConcurrentCollectionChanges()=runBlocking<Unit> {
        val name="snapshot-${id()}"
        var db=Room.databaseBuilder(context,MtgDatabase::class.java,name).build()
        try {
            val gate=TransferSessionGate().also { it.changeOwner(owner) };val state=MutableStateFlow<SessionState>(auth())
            fun repository()=RoomCollectionSelectionRepository(db,gate,TransferAuthSessionObserver(state,VerifiedTransferGuestIdentity(context),gate,db.collectionTransferDao()))
            for(index in 0..120) {
                val c=card(index).copy(name="Card ${index.toString().padStart(3,'0')}")
                db.cardDao().upsert(c);db.userCardCollectionDao().upsert(UserCardCollectionEntity("row-$index",owner.id,c.scryfallId,createdAt=index.toLong()))
            }
            val snapshot=repository().capture(owner,query())
            db.close();db=Room.databaseBuilder(context,MtgDatabase::class.java,name).build()
            val repo=repository();var after=0L;val keys=mutableListOf<String>();val sizes=mutableListOf<Int>()
            do { val page=repo.page(owner,snapshot.id,after);sizes+=page.groups.size;keys+=page.groups.map { it.groupKey };after=page.nextOrdinal ?: -1L } while(after>=0L)
            assertEquals(listOf(50,50,21),sizes);assertEquals(121,keys.distinct().size)
            assertEquals(121L,snapshot.groups)
        } finally { db.close();context.deleteDatabase(name) }
    }
    @Test fun migration64to65PreservesCollectionOutboxesAndTransferLedger() {
        val name="snapshot65-${id()}";val before=helper.createDatabase(name,64)
        before.execSQL("INSERT INTO user_card_collection(id,user_id,scryfall_id,quantity,is_foil,condition,language,is_for_trade,is_deleted,updated_at,created_at) VALUES('row','owner','uncached',7,0,'NM','en',0,0,1,1)")
        before.execSQL("INSERT INTO trade_wishlist_cleanup(user_id,wishlist_id,target_quantity) VALUES('owner','wish',4)")
        before.execSQL("INSERT INTO collection_transfer_wishlist_dirty(wishlist_id,owner_key,revision) VALUES('wish','account:owner',9)")
        before.close()
        val after=helper.runMigrationsAndValidate(name,65,true,MIGRATION_64_65)
        for(table in listOf("user_card_collection","trade_wishlist_cleanup","collection_transfer_wishlist_dirty"))after.query("SELECT COUNT(*) FROM $table").use { it.moveToFirst();assertEquals(1,it.getInt(0)) }
        after.query("PRAGMA foreign_key_list(user_card_collection)").use { assertFalse(it.moveToFirst()) }
        after.close();context.deleteDatabase(name)
    }
    private fun gateway(block: suspend (List<com.mmg.manahub.core.domain.repository.CardLookupIdentifier>)->TransferLookupBatch={ TransferLookupBatch(emptyList(),it) })=object: TransferResolutionGateway {
        override suspend fun cached(identifier: com.mmg.manahub.core.domain.repository.CardLookupIdentifier): TransferPrinting?=null
        override suspend fun lookup(identifiers: List<com.mmg.manahub.core.domain.repository.CardLookupIdentifier>)=block(identifiers)
        override suspend fun fallbackName(name: String)=TransferNameLookup(notFound=true)
    }
    private val reporter=object: com.mmg.manahub.core.common.CrashReporter {
        override fun log(message: String) { }
        override fun recordException(throwable: Throwable) { }
        override fun setCustomKey(key: String,value: String) { }
    }
    private class RecordingReporter: com.mmg.manahub.core.common.CrashReporter {
        val logs=mutableListOf<String>();val keys=mutableMapOf<String,String>();val errors=mutableListOf<Throwable>()
        override fun log(message: String) { logs+=message }
        override fun setCustomKey(key: String,value: String) { keys[key]=value }
        override fun recordException(throwable: Throwable) { errors+=throwable }
        fun payload()=(logs+keys.entries.map { "${it.key}=${it.value}" }+errors.map { it.stackTraceToString() }).joinToString()
    }
    @Test fun durableExportsFreezeEveryRawVariantForAllSourcesAndRegenerateIdentically()=runBlocking<Unit> { fixture { db,gate,state,selection,_ ->
        val root=java.io.File(context.cacheDir,"export-test-${id()}")
        val observer=TransferAuthSessionObserver(state,VerifiedTransferGuestIdentity(context),gate,db.collectionTransferDao())
        val repo=RoomCollectionExportRepository(context,db,selection,gate,observer,gateway(),reporter,root=root)
        try {
            for(index in 10..132) {
                val c=card(index).copy(name="Frozen ${index.toString().padStart(3,'0')}")
                db.cardDao().upsert(c)
                db.userCardCollectionDao().upsert(UserCardCollectionEntity("export-$index",owner.id,c.scryfallId,index%7+1,index%2==0,"LP","ja",createdAt=index.toLong()))
                db.localWishlistDao().insert(LocalWishlistEntity("wish-$index",c.scryfallId,index%7+1,false,index%2==0,"LP","ja",ownerUserId=owner.id))
                db.localOpenForTradeDao().insert(LocalOpenForTradeEntity("trade-$index","export-$index",c.scryfallId,index%7+1,index%2==0,"LP","ja",ownerUserId=owner.id))
            }
            db.userCardCollectionDao().upsert(UserCardCollectionEntity("export-variant",owner.id,"printing-10",13,false,"NM","de"))
            db.localWishlistDao().insert(LocalWishlistEntity("wish-variant","printing-10",13,false,false,"NM","de",ownerUserId=owner.id))
            db.localOpenForTradeDao().insert(LocalOpenForTradeEntity("trade-variant","export-variant","printing-10",13,false,"NM","de",ownerUserId=owner.id))
            for(source in CollectionSource.entries) {
                val job=repo.create(owner,query(source=source,search="Frozen",ascending=false),CollectionFileFormat.MANABOX_CSV,CollectionExportTarget.SAVE)
                val browsing=selection.capture(owner,query());selection.discard(owner,browsing.id)
                db.cardDao().upsert(card(10).copy(name="Edited after capture"))
                val summary=repo.prepare(owner,job)
                assertEquals(124L,summary.rows);assertEquals(0L,summary.omittedRows)
                val file=java.io.File(root,"$job/$job.csv");val bytes=file.readBytes()
                val parsed=CollectionImportParser.parse(bytes.toString(Charsets.UTF_8))
                assertEquals(124,parsed.lines.size);assertEquals("Frozen 132",parsed.lines.first().name)
                assertEquals(13,parsed.lines.single { it.language=="de" }.quantity)
                assertTrue(parsed.lines.filter { it.language!="de" }.all { it.condition=="LP" && it.language=="ja" && it.isFoil==(it.collectorNumber!!.toInt()%2==0) })
                assertEquals(summary.copies,parsed.lines.sumOf { it.quantity.toLong() })
                assertTrue(file.delete());db.collectionExportDao().update(db.collectionExportDao().get(job,"account:${owner.id}")!!.copy(phase="WRITING"))
                val restarted=RoomCollectionExportRepository(context,db,selection,gate,observer,gateway(),reporter,root=root)
                assertEquals(summary.rows,restarted.prepare(owner,job).rows)
                assertArrayEquals(bytes,file.readBytes())
                db.cardDao().upsert(card(10).copy(name="Frozen 010"))
            }
        } finally { root.deleteRecursively() }
    } }
    @Test fun hydrationRetainsFrozenTagsAndMissingRowsRequireExplicitOmissionConsent()=runBlocking<Unit> { fixture { db,gate,state,selection,_ ->
        val root=java.io.File(context.cacheDir,"export-test-${id()}")
        val observer=TransferAuthSessionObserver(state,VerifiedTransferGuestIdentity(context),gate,db.collectionTransferDao())
        val tags="[{\"k\":\"tagA\",\"c\":\"STRATEGY\"}]"
        val suggestions="[{\"k\":\"tagA\",\"c\":\"STRATEGY\",\"p\":0.8,\"s\":\"fixture\"}]"
        db.cardDao().upsert(card(0).copy(staleReason="pending_hydration",tags=tags,userTags=tags,suggestedTags=suggestions))
        var calls=0
        val network=gateway { ids ->
            calls++;assertTrue(ids.size<=75)
            db.cardDao().upsert(card(0).copy(tags=tags.replace("tagA","tagB"),userTags=tags.replace("tagA","tagB"),suggestedTags=suggestions.replace("tagA","tagB")))
            TransferLookupBatch(listOf(TransferPrinting("printing-0","Zebra","aaa","0")),emptyList())
        }
        val repo=RoomCollectionExportRepository(context,db,selection,gate,observer,network,reporter,root=root)
        try {
            val job=repo.create(owner,query(advanced=AdvancedSearchQuery(listOf(SearchCriterion.HasTag(listOf("tagA"))))),CollectionFileFormat.MANABOX_CSV,CollectionExportTarget.SAVE)
            assertEquals(2L,repo.prepare(owner,job).rows);assertEquals(1,calls)
            val frozen=db.collectionSelectionDao().scan(job,"").first { it.card.scryfallId=="printing-0" }.card
            assertEquals(tags,frozen.tags);assertEquals(tags,frozen.userTags);assertEquals(suggestions,frozen.suggestedTags)
            db.userCardCollectionDao().upsert(UserCardCollectionEntity("missing",owner.id,"uncached-export",17))
            val unresolved=repo.create(owner,query(),CollectionFileFormat.MANABOX_CSV,CollectionExportTarget.SAVE)
            assertEquals(CollectionExportPhase.NEEDS_METADATA,repo.prepare(owner,unresolved).phase)
            val available=repo.prepare(owner,unresolved,true)
            assertEquals(CollectionExportPhase.READY,available.phase);assertEquals(1L,available.omittedRows);assertEquals(17L,available.omittedCopies)
            val report=java.io.File(root,"$unresolved/$unresolved.omissions.csv").readText()
            assertTrue(report.contains("uncached-export"));assertTrue(report.contains("membership unknown"))
            db.localWishlistDao().insert(LocalWishlistEntity("unspecified","printing-1",9,true,null,null,null,ownerUserId=owner.id))
            val generic=repo.create(owner,query(source=CollectionSource.WISHLIST),CollectionFileFormat.MANABOX_CSV,CollectionExportTarget.SAVE)
            assertEquals(CollectionExportPhase.NEEDS_METADATA,repo.prepare(owner,generic).phase)
            assertEquals(9L,repo.prepare(owner,generic,true).omittedCopies)
        } finally { root.deleteRecursively() }
    } }
    @Test fun exportOperationTelemetryRecordsBoundariesWithoutPrivateRowsDestinationsOrCauses()=runBlocking<Unit> { fixture { db,gate,state,selection,_ ->
        val root=java.io.File(context.cacheDir,"export-telemetry-${id()}");val cache=java.io.File(context.cacheDir,"exports/${id()}")
        val observer=TransferAuthSessionObserver(state,VerifiedTransferGuestIdentity(context),gate,db.collectionTransferDao());val recorded=RecordingReporter();val marker="PRIVATE_SQL_URI_THROWABLE_MARKER"
        var failClose=true
        fun repository()=RoomCollectionExportRepository(context,db,selection,gate,observer,gateway(),recorded,root=root,cache=cache,openOutput={ object: java.io.ByteArrayOutputStream() { override fun close() { if(failClose)throw java.io.IOException(marker,IllegalStateException(marker));super.close() } } },deleteOutput={})
        try {
            var repo=repository();val job=repo.create(owner,query(),CollectionFileFormat.MANABOX_CSV,CollectionExportTarget.SAVE);repo.prepare(owner,job)
            assertTrue(recorded.logs.contains("collection_export_snapshot_frozen"));assertTrue(recorded.logs.contains("collection_export_ready"))
            try { repo.save(owner,job,"content://$marker/output");fail("Destination close failure expected") } catch(_: java.io.IOException) { }
            assertTrue(recorded.logs.contains("collection_export_save_started"));assertTrue(recorded.logs.contains("collection_export_save_failed"));assertTrue(recorded.logs.contains("collection_export_destination_close_unconfirmed"));assertFalse(recorded.logs.contains("collection_export_save_closed"))
            failClose=false;repo=repository();repo.prepare(owner,job);repo.save(owner,job,"content://$marker/output");repo.share(owner,job)
            assertTrue(recorded.logs.contains("collection_export_snapshot_recovered"));assertTrue(recorded.logs.contains("collection_export_save_closed"));assertTrue(recorded.logs.contains("collection_export_share_attachment_ready"));assertFalse(recorded.logs.any { it.contains("delivered") })
            db.userCardCollectionDao().upsert(UserCardCollectionEntity(marker,owner.id,marker,12345))
            val incomplete=repo.create(owner,query(),CollectionFileFormat.MANABOX_CSV,CollectionExportTarget.SAVE);assertEquals(CollectionExportPhase.NEEDS_METADATA,repo.prepare(owner,incomplete).phase)
            repo.prepare(owner,incomplete,true);repo.writeOmissions(owner,incomplete,"content://$marker/report")
            assertTrue(recorded.logs.contains("collection_export_metadata_decision"));assertTrue(recorded.logs.contains("collection_export_available_only"));assertTrue(recorded.logs.contains("collection_export_omissions_report_saved"))
            assertEquals(4,recorded.keys.size);assertTrue(recorded.errors.isNotEmpty());assertTrue(recorded.errors.all { it.cause==null && it.suppressed.isEmpty() })
            assertFalse(recorded.payload().contains(marker));assertFalse(recorded.payload().contains(owner.id));assertFalse(recorded.payload().contains("12345"));assertFalse(recorded.payload().contains(job))
        } finally { root.deleteRecursively();cache.deleteRecursively() }
    } }
    @Test fun unfilteredSelectionPreservesPagedTagsAndMissingMetadataEligibility()=runBlocking<Unit> { fixture { db,_,_,repository,_ ->
        val lateTag="[{\"k\":\"late-tag\",\"c\":\"STRATEGY\"}]"
        val malformedMetadata=card(0).copy(scryfallId="tag-printing",tags=lateTag,userTags=lateTag,colors="malformed-json")
        db.cardDao().upsertAll((0..205).map { malformedMetadata.copy(scryfallId="tag-printing-$it",tags=if(it==205)lateTag else "[]",userTags=if(it==205)lateTag else "[]") })
        db.userCardCollectionDao().upsertAll((0..205).map { UserCardCollectionEntity("tag-row-${it.toString().padStart(4,'0')}",owner.id,"tag-printing-$it",1) })
        db.userCardCollectionDao().upsert(UserCardCollectionEntity("uncached-tag-row",owner.id,"missing-printing",11))
        val summary=repository.capture(owner,query())
        assertEquals(245L,summary.copies)
        assertEquals(setOf("late-tag"),summary.tags.map { it.key }.toSet())
        assertEquals(1L,summary.missingMetadataRows)
        val frozen=id()
        repository.freeze(owner,query(),frozen)
        val eligible=repository.evaluate(owner,frozen,query(),requireMetadata=true)
        assertEquals(234L,eligible.copies)
        assertEquals(summary.tags,eligible.tags)
    } }
    @Test fun cancellationAfterCommittedFreezeDiscardsOnlyItsAllocatedSnapshot()=runBlocking<Unit> { fixture { db,gate,state,repository,_ ->
        val retained=repository.capture(owner,query())
        val frozen=CompletableDeferred<Unit>()
        val blocked=CompletableDeferred<Unit>()
        val observer=TransferAuthSessionObserver(state,VerifiedTransferGuestIdentity(context),gate,db.collectionTransferDao())
        val cancellable=RoomCollectionSelectionRepository(db,gate,observer,onSnapshotFrozen={ frozen.complete(Unit);blocked.await() })
        val job=launch(Dispatchers.Default) { cancellable.capture(owner,query()) }
        withTimeout(10000) { frozen.await() }
        job.cancelAndJoin()
        val queryIds=db.openHelper.readableDatabase.query("SELECT id FROM collection_selection_queries").use { cursor -> buildList { while(cursor.moveToNext())add(cursor.getString(0)) } }
        assertEquals(listOf(retained.id),queryIds)
        assertEquals(retained.groups,db.collectionSelectionDao().groupCount(retained.id))
        assertEquals(retained.copies,repository.page(owner,retained.id,0L).groups.sumOf { it.quantity })
    } }
    @Test fun unfilteredAndMatcherSelectionsAgreeOnNullMetadataTagsLongTotalsAndOrdering()=runBlocking<Unit> { fixture { db,_,_,repository,_ ->
        db.cardDao().upsert(card(0).copy(tags="[{\"k\":\"shared-tag\",\"c\":\"STRATEGY\"}]",userTags="[{\"k\":\"manual-tag\",\"c\":\"ROLE\"}]",staleReason=null))
        db.cardDao().upsert(card(1).copy(tags="[{\"k\":\"pending-tag\",\"c\":\"STRATEGY\"}]",staleReason="pending_hydration"))
        db.cardDao().upsert(card(2).copy(tags="",userTags="[]"))
        db.cardDao().upsert(card(3).copy(tags="null",userTags="   "))
        db.cardDao().upsert(card(4).copy(tags="[malformed",userTags="[{\"k\":\"shared-tag\",\"c\":\"ROLE\"}]"))
        db.cardDao().upsert(card(5).copy(tags=" [ ] ",userTags="[{\"k\":\"legacy-tag\",\"c\":\"UNKNOWN\"}]"))
        db.userCardCollectionDao().upsert(UserCardCollectionEntity("row-0",owner.id,"printing-0",Int.MAX_VALUE,createdAt=10L))
        db.userCardCollectionDao().upsert(UserCardCollectionEntity("row-extra",owner.id,"printing-0",Int.MAX_VALUE,isFoil=true,createdAt=100L))
        for(grouping in listOf(CollectionGroupingMode.NONE,CollectionGroupingMode.SET))for(ascending in listOf(true,false))for(requireMetadata in listOf(true,false)) {
            val q=query(ascending=ascending,grouping=grouping)
            suspend fun evaluated(selection: CollectionSelectionQuery): CollectionSelectionSummary {
                val snapshot=id();repository.freeze(owner,selection,snapshot)
                return repository.evaluate(owner,snapshot,selection,requireMetadata=requireMetadata)
            }
            val fast=evaluated(q)
            val matcher=evaluated(q.copy(advanced=AdvancedSearchQuery(emptyList())))
            assertEquals(matcher.copies,fast.copies)
            assertTrue(fast.copies>Int.MAX_VALUE)
            assertEquals(matcher.groups,fast.groups)
            assertEquals(matcher.tags,fast.tags)
            assertEquals(matcher.sections,fast.sections)
            assertEquals(repository.page(owner,matcher.id,0L).groups,repository.page(owner,fast.id,0L).groups)
            repository.discard(owner,fast.id);repository.discard(owner,matcher.id)
        }
    } }
    @Test fun destinationCloseFailureRetainsPrivateFileAndSuccessfulRetryTruncates()=runBlocking<Unit> { fixture { db,gate,state,selection,_ ->
        val root=java.io.File(context.cacheDir,"export-test-${id()}")
        val destination=java.io.File(context.cacheDir,"export-destination-${id()}").apply { writeText("old longer destination".repeat(1000)) }
        val observer=TransferAuthSessionObserver(state,VerifiedTransferGuestIdentity(context),gate,db.collectionTransferDao())
        var failClose=true
        val repo=RoomCollectionExportRepository(context,db,selection,gate,observer,gateway(),reporter,root=root,openOutput={
            if(failClose)object: java.io.ByteArrayOutputStream() { override fun close() { throw java.io.IOException("synthetic close") } }
            else java.io.FileOutputStream(destination,false)
        },deleteOutput={})
        try {
            val job=repo.create(owner,query(),CollectionFileFormat.MANABOX_CSV,CollectionExportTarget.SAVE);repo.prepare(owner,job)
            try { repo.save(owner,job,"content://fixture/output");fail("Close failure cannot certify success") } catch(_: java.io.IOException) { }
            val durable=db.collectionExportDao().get(job,"account:${owner.id}")!!
            assertEquals("READY",durable.phase);assertTrue(durable.partialDestination)
            val file=java.io.File(root,"$job/$job.csv");assertTrue(file.isFile)
            failClose=false
            val restarted=RoomCollectionExportRepository(context,db,selection,gate,observer,gateway(),reporter,root=root,openOutput={java.io.FileOutputStream(destination,false)},deleteOutput={})
            restarted.save(owner,job,"content://fixture/output")
            assertArrayEquals(file.readBytes(),destination.readBytes());assertEquals("SAVED",db.collectionExportDao().get(job,"account:${owner.id}")!!.phase)
        } finally { root.deleteRecursively();destination.delete() }
    } }
    @Test fun migration65to66RetainsRowsAndAddsIndependentExportState() {
        val name="export66-${id()}";val before=helper.createDatabase(name,65)
        before.execSQL("INSERT INTO user_card_collection(id,user_id,scryfall_id,quantity,is_foil,condition,language,is_for_trade,is_deleted,updated_at,created_at) VALUES('row','owner','uncached',7,0,'NM','en',0,0,1,1)")
        before.execSQL("INSERT INTO trade_wishlist_cleanup(user_id,wishlist_id,target_quantity) VALUES('owner','wish',4)")
        before.execSQL("INSERT INTO collection_transfer_wishlist_dirty(wishlist_id,owner_key,revision) VALUES('wish','account:owner',9)")
        before.close()
        val after=helper.runMigrationsAndValidate(name,66,true,MIGRATION_65_66)
        for(table in listOf("user_card_collection","trade_wishlist_cleanup","collection_transfer_wishlist_dirty"))after.query("SELECT COUNT(*) FROM $table").use { it.moveToFirst();assertEquals(1,it.getInt(0)) }
        after.close();context.deleteDatabase(name)
    }
    @Test fun sharedAttachmentsRemainIndependentForDelayedReadersAndCleanupIsAgeOnly()=runBlocking<Unit> { fixture { db,gate,state,selection,_ ->
        val root=java.io.File(context.cacheDir,"export-test-${id()}")
        val cache=java.io.File(context.cacheDir,"exports/${id()}").apply { mkdirs() }
        val observer=TransferAuthSessionObserver(state,VerifiedTransferGuestIdentity(context),gate,db.collectionTransferDao())
        val now=System.currentTimeMillis()
        val old=java.io.File(cache,id()).apply { mkdirs();java.io.File(this,"old.csv").writeText("old");setLastModified(now-25*60*60*1000L) }
        val writing=java.io.File(cache,id()).apply { mkdirs();java.io.File(this,"active.part").writeText("active");setLastModified(now-25*60*60*1000L) }
        val repo=RoomCollectionExportRepository(context,db,selection,gate,observer,gateway(),reporter,root=root,cache=cache,now={now})
        try {
            val first=repo.create(owner,query(),CollectionFileFormat.MANABOX_CSV,CollectionExportTarget.SHARE);repo.prepare(owner,first)
            val uri=android.net.Uri.parse(repo.share(owner,first))
            db.userCardCollectionDao().upsert(UserCardCollectionEntity("later",owner.id,"printing-0",91))
            val second=repo.create(owner,query(),CollectionFileFormat.MANABOX_CSV,CollectionExportTarget.SHARE);repo.prepare(owner,second)
            val next=android.net.Uri.parse(repo.share(owner,second));assertNotEquals(uri,next)
            assertArrayEquals(java.io.File(root,"$first/$first.csv").readBytes(),context.contentResolver.openInputStream(uri)!!.use { it.readBytes() })
            assertTrue(writing.isDirectory);assertFalse(old.exists());assertTrue(java.io.File(cache,first).isDirectory)
            state.value=SessionState.Authenticated(AuthUser("other",null,null,null,null,"fixture"));gate.changeOwner(TransferOwner.Account("other"))
            try { repo.share(owner,first);fail("Obsolete owner cannot prepare share") } catch(_: TransferReadException) { }
        } finally { root.deleteRecursively();cache.deleteRecursively() }
    } }
    @Test fun existingCollectionExportsMoreThanImportBudgetWithoutWholeOutputAllocation()=runBlocking<Unit> { fixture { db,gate,state,selection,_ ->
        val root=java.io.File(context.cacheDir,"export-test-${id()}")
        val observer=TransferAuthSessionObserver(state,VerifiedTransferGuestIdentity(context),gate,db.collectionTransferDao())
        db.cardDao().upsert(card(0).copy(name="+2 Mace"))
        val sql=db.openHelper.writableDatabase
        val columns=sql.query("PRAGMA table_info(cards)").use { cursor ->
            buildList { while(cursor.moveToNext())add(cursor.getString(cursor.getColumnIndex("name"))) }
        }
        sql.beginTransaction()
        try {
            for(start in 0..100 step 1) {
                val base=start*1000;val limit=if(start==100)0 else 999
                val values=columns.joinToString(",") { if(it=="scryfall_id")"printf('00000000-0000-0000-0000-%012d',$base+x)" else "c.$it" }
                sql.execSQL("WITH RECURSIVE n(x) AS (SELECT 0 UNION ALL SELECT x+1 FROM n WHERE x<$limit) INSERT INTO cards(${columns.joinToString(",")}) SELECT $values FROM cards c CROSS JOIN n WHERE c.scryfall_id='printing-0'")
                sql.execSQL("WITH RECURSIVE n(x) AS (SELECT 0 UNION ALL SELECT x+1 FROM n WHERE x<$limit) INSERT INTO user_card_collection(id,user_id,scryfall_id,quantity,is_foil,condition,language,is_for_trade,is_deleted,updated_at,created_at) SELECT 'large-'||($base+x),?,printf('00000000-0000-0000-0000-%012d',$base+x),1,0,'NM','en',0,0,1,$base+x FROM n",arrayOf(owner.id))
            }
            sql.setTransactionSuccessful()
        } finally { sql.endTransaction() }
        val repo=RoomCollectionExportRepository(context,db,selection,gate,observer,gateway { fail("Cache fixture must not call network");TransferLookupBatch(emptyList(),it) },reporter,root=root)
        try {
            val job=repo.create(owner,query(search="+2 Mace"),CollectionFileFormat.MANABOX_CSV,CollectionExportTarget.SAVE)
            val summary=repo.prepare(owner,job)
            assertEquals(100003L,summary.rows)
            var lines=0L
            java.io.File(root,"$job/$job.csv").bufferedReader().use { reader -> while(reader.readLine()!=null)lines++ }
            assertEquals(summary.rows+1L,lines)
        } finally { root.deleteRecursively() }
    } }
    @Test fun exportPickerConsentRejectsAuthAbaAndSerializesPendingPickers()=runBlocking<Unit> { fixture { db,gate,state,_,_ ->
        val recorded=RecordingReporter()
        val observer=TransferAuthSessionObserver(state,VerifiedTransferGuestIdentity(context),gate,db.collectionTransferDao())
        val values=MutableStateFlow<DurableCollectionExport?>(null)
        var writes=0
        val fake=object: CollectionExportRepository {
            override suspend fun create(owner: TransferOwner,query: CollectionSelectionQuery,format: CollectionFileFormat,target: CollectionExportTarget): String {
                val key=id();values.value=DurableCollectionExport(key,format,target,CollectionExportPhase.FROZEN,1L,1L,0L,0L,0L,false,false);return key
            }
            override fun observe(owner: TransferOwner,id: String)=values
            override suspend fun latest(owner: TransferOwner): String?=null
            override suspend fun prepare(owner: TransferOwner,id: String,availableOnly: Boolean): DurableCollectionExport=values.value!!.copy(phase=CollectionExportPhase.READY).also { values.value=it }
            override suspend fun save(owner: TransferOwner,id: String,location: String) { writes++ }
            override suspend fun share(owner: TransferOwner,id: String)="content://fixture/export"
            override suspend fun writeOmissions(owner: TransferOwner,id: String,location: String) { writes++ }
        }
        val store=androidx.lifecycle.ViewModelStore()
        val vm=withContext(Dispatchers.Main) { com.mmg.manahub.feature.collection.presentation.importexport.DurableExportViewModel(androidx.lifecycle.SavedStateHandle(),fake,gate,observer,recorded).also { store.put("export",it) } }
        try {
            withTimeout(5000) { while(!vm.isCurrentOwner())delay(10) }
            withContext(Dispatchers.Main) { vm.begin(query(),CollectionFileFormat.MANABOX_CSV,CollectionExportTarget.SAVE) }
            withTimeout(5000) { while(!vm.state.value.requestSave)delay(10) }
            withContext(Dispatchers.Main) { vm.cancelPickerRequest();vm.save("content://fixture/unlaunched") }
            assertEquals(0,writes);assertFalse(vm.state.value.requestSave)
            withContext(Dispatchers.Main) { vm.begin(query(),CollectionFileFormat.MANABOX_CSV,CollectionExportTarget.SAVE) }
            withTimeout(5000) { while(!vm.state.value.requestSave)delay(10) }
            state.value=SessionState.Authenticated(AuthUser("other",null,null,null,null,"fixture"));gate.changeOwner(TransferOwner.Account("other"))
            state.value=auth();gate.changeOwner(owner)
            withContext(Dispatchers.Main) { vm.save("content://fixture/obsolete") }
            delay(100);assertEquals(0,writes)
            withTimeout(5000) { while(!vm.isCurrentOwner())delay(10) }
            withContext(Dispatchers.Main) { vm.begin(query(),CollectionFileFormat.MANABOX_CSV,CollectionExportTarget.SAVE) }
            withTimeout(5000) { while(!vm.state.value.requestSave)delay(10) }
            withContext(Dispatchers.Main) { vm.pickerLaunched();vm.begin(query(search="replacement"),CollectionFileFormat.TEXT,CollectionExportTarget.SAVE) }
            withTimeout(5000) { while(vm.state.value.busy)delay(10) }
            withContext(Dispatchers.Main) { vm.save("content://fixture/old-job") }
            withTimeout(5000) { while(writes==0)delay(10) }
            assertEquals(1,writes)
            withTimeout(5000) { while(vm.state.value.busy)delay(10) }
            withContext(Dispatchers.Main) { vm.begin(query(),CollectionFileFormat.MANABOX_CSV,CollectionExportTarget.SHARE) }
            withTimeout(5000) { while(vm.state.value.share==null || vm.state.value.busy)delay(10) }
            assertTrue(vm.canLaunchShare())
            withContext(Dispatchers.Main) { vm.shared(cancelled=true) }
            assertNull(vm.state.value.share);assertFalse(vm.canLaunchShare())
            withContext(Dispatchers.Main) { vm.retry() }
            withTimeout(5000) { while(vm.state.value.share==null)delay(10) }
            assertTrue(vm.canLaunchShare())
            assertTrue(recorded.logs.contains("collection_export_save_cancelled"));assertTrue(recorded.logs.contains("collection_export_share_cancelled"));assertTrue(recorded.logs.contains("collection_export_retry"));assertTrue(recorded.errors.isEmpty())
            withContext(Dispatchers.Main) { vm.shareChooserFailed() }
            assertEquals("collection_export_share_chooser_failed",recorded.logs.last());assertEquals(1,recorded.errors.size);assertNull(recorded.errors.single().cause);assertTrue(recorded.errors.single().suppressed.isEmpty())
            assertFalse(recorded.payload().contains("content://fixture"));assertFalse(recorded.payload().contains(owner.id))
            withContext(Dispatchers.Main) { values.value=values.value!!.copy(phase=CollectionExportPhase.NEEDS_METADATA) }
            withTimeout(5000) { while(vm.state.value.value?.phase!=CollectionExportPhase.NEEDS_METADATA)delay(10) }
            withContext(Dispatchers.Main) { vm.availableOnly() }
            withTimeout(5000) { while(!recorded.logs.contains("collection_export_available_only_requested"))delay(10) }
        } finally { withContext(Dispatchers.Main) { store.clear() } }
    } }
    @Test fun lostGrantWriteFailureAndCancellationRetainTheSameVerifiedOutput()=runBlocking<Unit> { fixture { db,gate,state,selection,_ ->
        val recorded=RecordingReporter()
        val root=java.io.File(context.cacheDir,"export-test-${id()}")
        val observer=TransferAuthSessionObserver(state,VerifiedTransferGuestIdentity(context),gate,db.collectionTransferDao())
        val factory: ((android.net.Uri)->java.io.OutputStream)->RoomCollectionExportRepository={ open -> RoomCollectionExportRepository(context,db,selection,gate,observer,gateway(),recorded,root=root,openOutput=open,deleteOutput={}) }
        try {
            val initial=factory { java.io.ByteArrayOutputStream() }
            val job=initial.create(owner,query(),CollectionFileFormat.MANABOX_CSV,CollectionExportTarget.SAVE);initial.prepare(owner,job)
            val file=java.io.File(root,"$job/$job.csv");val original=file.readBytes()
            for(open in listOf<(android.net.Uri)->java.io.OutputStream>(
                { throw SecurityException("synthetic lost grant") },
                { object: java.io.OutputStream() { override fun write(value: Int) { throw java.io.IOException("synthetic write failure") } } })) {
                try { factory(open).save(owner,job,"content://fixture/failed");fail("Destination failure must remain explicit") } catch(_: Exception) { }
                assertArrayEquals(original,file.readBytes());assertTrue(db.collectionExportDao().get(job,"account:${owner.id}")!!.partialDestination)
            }
            val started=java.util.concurrent.CountDownLatch(1);val closed=java.util.concurrent.CountDownLatch(1)
            recorded.errors.clear()
            val blocking=factory { object: java.io.OutputStream() {
                override fun write(value: Int) { started.countDown();closed.await() }
                override fun write(buffer: ByteArray,offset: Int,length: Int) { started.countDown();closed.await() }
                override fun close() { closed.countDown() }
            } }
            val copying=launch(Dispatchers.IO) { blocking.save(owner,job,"content://fixture/cancelled") }
            assertTrue(withContext(Dispatchers.IO) { started.await(5,java.util.concurrent.TimeUnit.SECONDS) })
            copying.cancelAndJoin();assertEquals(0L,closed.count)
            assertEquals("READY",db.collectionExportDao().get(job,"account:${owner.id}")!!.phase)
            assertArrayEquals(original,file.readBytes())
            assertTrue(recorded.errors.isEmpty());assertTrue(recorded.logs.contains("collection_export_save_cancelled"))
        } finally { root.deleteRecursively() }
    } }
    @Test fun actualSafPersistedGrantTruncatesSyntheticLongDestinationAfterClose()=runBlocking<Unit> { fixture { db,gate,state,selection,_ ->
        val resolver=context.contentResolver
        val permission=resolver.persistedUriPermissions.firstOrNull { grant ->
            runCatching { resolver.query(grant.uri,arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),null,null,null)?.use { cursor -> cursor.moveToFirst() && cursor.getString(0)=="transfer-export-20261001-check.csv" }==true }.getOrDefault(false)
        }
        org.junit.Assume.assumeNotNull(permission)
        val destination=permission!!.uri
        resolver.openOutputStream(destination,"wt")!!.use { it.write(ByteArray(32768)) }
        assertTrue(resolver.openInputStream(destination)!!.use { it.readBytes().size }>=32768)
        val root=java.io.File(context.cacheDir,"export-test-${id()}")
        val observer=TransferAuthSessionObserver(state,VerifiedTransferGuestIdentity(context),gate,db.collectionTransferDao())
        val repo=RoomCollectionExportRepository(context,db,selection,gate,observer,gateway(),reporter,root=root)
        try {
            val job=repo.create(owner,query(),CollectionFileFormat.MANABOX_CSV,CollectionExportTarget.SAVE)
            repo.prepare(owner,job);repo.save(owner,job,destination.toString())
            val bytes=java.io.File(root,"$job/$job.csv").readBytes()
            assertTrue(bytes.size<32768)
            assertArrayEquals(bytes,resolver.openInputStream(destination)!!.use { it.readBytes() })
            assertEquals("SAVED",db.collectionExportDao().get(job,"account:${owner.id}")!!.phase)
        } finally { root.deleteRecursively() }
    } }
    @Test fun durableExportReopensRoomAndRegeneratesFrozenBytesAfterSourceAndCacheEdits()=runBlocking<Unit> {
        val name="export-reopen-${id()}"
        val root=java.io.File(context.cacheDir,"export-test-${id()}")
        var db=Room.databaseBuilder(context,MtgDatabase::class.java,name).build()
        val state=MutableStateFlow<SessionState>(auth())
        val advanced=AdvancedSearchQuery(listOf(SearchCriterion.HasTag(listOf("tagA"))))
        val printing=id()
        suspend fun repository(): RoomCollectionExportRepository {
            val gate=TransferSessionGate().also { it.changeOwner(owner) }
            val observer=TransferAuthSessionObserver(state,VerifiedTransferGuestIdentity(context),gate,db.collectionTransferDao())
            return RoomCollectionExportRepository(context,db,RoomCollectionSelectionRepository(db,gate,observer),gate,observer,gateway { fail("Frozen recovery must not hydrate");TransferLookupBatch(emptyList(),it) },reporter,root=root)
        }
        try {
            db.cardDao().upsert(card(0).copy(scryfallId=printing,name="+2 Mace Ω, \"quoted\"\nsecond line",tags="[{\"k\":\"tagA\",\"c\":\"STRATEGY\"}]"))
            db.userCardCollectionDao().upsert(UserCardCollectionEntity("first",owner.id,printing,3,false,"NM","en"))
            db.userCardCollectionDao().upsert(UserCardCollectionEntity("variant",owner.id,printing,5,true,"LP","ja"))
            val initial=repository()
            val job=initial.create(owner,query(advanced=advanced),CollectionFileFormat.MANABOX_CSV,CollectionExportTarget.SAVE)
            assertEquals(8L,initial.prepare(owner,job).copies)
            val output=java.io.File(root,"$job/$job.csv");val bytes=output.readBytes()
            db.cardDao().upsert(card(0).copy(scryfallId=printing,name="Changed by sync",tags="[]"))
            db.userCardCollectionDao().upsert(UserCardCollectionEntity("first",owner.id,printing,99,false,"NM","en"))
            db.collectionExportDao().update(db.collectionExportDao().get(job,"account:${owner.id}")!!.copy(phase="WRITING"))
            assertTrue(output.delete());db.close()
            db=Room.databaseBuilder(context,MtgDatabase::class.java,name).build()
            val stored=db.collectionSelectionDao().query(job,"account:${owner.id}")!!
            assertEquals(advanced,kotlinx.serialization.json.Json.decodeFromString(AdvancedSearchQuery.serializer(),stored.advancedQuery))
            val recovered=repository().prepare(owner,job)
            assertEquals(CollectionExportPhase.READY,recovered.phase);assertEquals(2L,recovered.rows);assertEquals(8L,recovered.copies)
            assertArrayEquals(bytes,output.readBytes())
        } finally { db.close();context.deleteDatabase(name);root.deleteRecursively() }
    }
}
