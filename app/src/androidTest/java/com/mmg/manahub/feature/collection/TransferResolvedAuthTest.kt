package com.mmg.manahub.feature.collection

import androidx.core.content.FileProvider
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.mmg.manahub.feature.collection.presentation.importexport.TransferIntakeViewModel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mmg.manahub.core.data.local.MtgDatabase
import com.mmg.manahub.core.data.local.TradeListOwner
import com.mmg.manahub.core.data.local.entity.LocalWishlistEntity
import com.mmg.manahub.core.domain.auth.*
import com.mmg.manahub.core.domain.collection.transfer.*
import com.mmg.manahub.feature.collection.data.*
import com.mmg.manahub.feature.trades.data.WishlistMutationCoordinator
import com.mmg.manahub.core.model.WishlistEntry
import com.mmg.manahub.core.model.CollectionSource
import com.mmg.manahub.core.data.local.entity.CardEntity
import com.mmg.manahub.feature.trades.data.repository.WishlistRepositoryImpl
import com.mmg.manahub.core.data.remote.trades.WishlistRemoteDataSource
import org.koin.core.context.GlobalContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class TransferResolvedAuthTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun id()=UUID.randomUUID().toString()
    private fun auth(id: String)=SessionState.Authenticated(AuthUser(id,null,null,null,null,"fixture"))
    private suspend fun fixture(block: suspend (MtgDatabase,VerifiedTransferGuestIdentity,TransferSessionGate,MutableStateFlow<SessionState>,TransferAuthSessionObserver)->Unit) {
        val name="resolved-auth-${id()}"
        val isolated=object: ContextWrapper(context) { override fun getSharedPreferences(key: String,mode: Int)=context.getSharedPreferences("$name-$key",mode) }
        val db=Room.inMemoryDatabaseBuilder(context,MtgDatabase::class.java).build()
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
        try {
            val identity=VerifiedTransferGuestIdentity(isolated); val gate=TransferSessionGate(); val state=MutableStateFlow<SessionState>(SessionState.Loading)
            val observer=TransferAuthSessionObserver(state,identity,gate,db.collectionTransferDao());observer.start(scope)
            block(db,identity,gate,state,observer)
        } finally { scope.coroutineContext[Job]!!.cancelAndJoin();db.close();context.deleteSharedPreferences("$name-collection_transfer_identity") }
    }
    private suspend fun resolved(gate: TransferSessionGate,owner: TransferOwner?=null)=withTimeout(5000L) {
        gate.sessions.filterIsInstance<TransferSession.Available>().first { owner==null || it.owner==owner }
    }
    @Test fun initialLoadingUsesResolvedAccountWithoutLocalIdentityOrCollectionIntent()=runBlocking<Unit> { fixture { db,identity,gate,state,observer ->
        val receipt=TransferJobId(id());observer.captureInitialReceipt(receipt)
        assertNull(identity.read());assertEquals(TransferSession.Loading,gate.currentSession)
        val directory=File(context.cacheDir,"auth-intake-${id()}").apply { mkdirs() }
        try {
            val files=AndroidCollectionTransferFileStore(directory,db.collectionTransferDao(),{ observer.observedSession(gate) })
            files.receive(receipt,0L,listOf(object: TransferInputSource { override val identity="fixture";override fun open()=ByteArrayInputStream("2 Fixture\n".toByteArray()) }),TransferSession.Loading)
            assertNull(db.collectionTransferDao().getReceipt(receipt.value)!!.capturedOwner)
            state.value=auth("a");val session=resolved(gate,TransferOwner.Account("a"))
            assertNull(identity.read());assertTrue(observer.canBindInitialReceipt(receipt,0L,session))
            val repository=RoomCollectionTransferRepository(db,gate,observer::matchesObserved)
            assertEquals(TransferMutationResult.Accepted,repository.bindReceipt(receipt,session.owner,session.generation,TransferOrigin.SHARE,true))
            observer.consumedReceipt(receipt);assertFalse(observer.canBindInitialReceipt(receipt,0L,session))
            assertEquals("account:a",db.collectionTransferDao().getJob(receipt.value,"account:a")!!.ownerKey)
            assertNull(db.collectionTransferDao().executableAction(receipt.value,"account:a"))
            db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM user_card_collection").use { it.moveToFirst();assertEquals(0,it.getInt(0)) }
        } finally { directory.deleteRecursively() }
    } }
    @Test fun resolvedSignedOutCreatesLocalProvenanceAutomaticallyAndSignInRejectsIt()=runBlocking<Unit> { fixture { _,identity,gate,state,observer ->
        state.value=SessionState.Unauthenticated;val local=resolved(gate).owner
        assertTrue(local is TransferOwner.VerifiedGuest);assertEquals(local,identity.read());assertTrue(observer.matchesObserved(local))
        state.value=auth("a");assertFalse(observer.matchesObserved(local));resolved(gate,TransferOwner.Account("a"))
        state.value=SessionState.Unauthenticated;assertEquals(local,resolved(gate,local).owner)
    } }
    @Test fun restoredAndLaterLoadingReceiptsAndOldAbaSessionNeverAcquireInitialProof()=runBlocking<Unit> { fixture { _,_,gate,state,observer ->
        val fresh=TransferJobId(id());observer.captureInitialReceipt(fresh)
        state.value=auth("a");val a=resolved(gate,TransferOwner.Account("a"))
        assertTrue(observer.canBindInitialReceipt(fresh,0L,a));assertFalse(observer.canBindInitialReceipt(TransferJobId(id()),0L,a))
        state.value=SessionState.Loading;withTimeout(5000L) { gate.sessions.first { it==TransferSession.Loading } }
        val later=TransferJobId(id());observer.captureInitialReceipt(later)
        state.value=auth("b");resolved(gate,TransferOwner.Account("b"));assertFalse(observer.canBindInitialReceipt(fresh,0L,a))
        state.value=auth("a");val returned=resolved(gate,TransferOwner.Account("a"))
        assertTrue(returned.generation>a.generation);assertFalse(observer.canBindInitialReceipt(fresh,0L,returned));assertFalse(observer.canBindInitialReceipt(later,0L,returned))
    } }
    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun withIntake(db: MtgDatabase,gate: TransferSessionGate,observer: TransferAuthSessionObserver,saved: SavedStateHandle=SavedStateHandle(),block: suspend (TransferIntakeViewModel,android.net.Uri)->Unit) {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val directory=File(context.cacheDir,"picker-intake-${id()}").apply { mkdirs() }
        val payload=File(context.cacheDir,"exports/picker-${id()}.txt").apply { parentFile!!.mkdirs();writeText("2 Fixture\n") }
        val store=ViewModelStore()
        val isolated=object: ContextWrapper(context) { override fun getSharedPreferences(key: String,mode: Int)=context.getSharedPreferences("${directory.name}-$key",mode) }
        try {
            val files=AndroidCollectionTransferFileStore(directory,db.collectionTransferDao(),{observer.observedSession(gate)})
            val vm=TransferIntakeViewModel(saved,context.contentResolver,files,gate,observer,LegacyCollectionImportQuarantine(isolated,File(directory,"legacy")),db)
            store.put("intake",vm)
            block(vm,FileProvider.getUriForFile(context,"${context.packageName}.fileprovider",payload))
        } finally { store.clear();payload.delete();directory.deleteRecursively();Dispatchers.resetMain() }
    }
    private suspend fun received(db: MtgDatabase,vm: TransferIntakeViewModel)=withTimeout(10_000L) {
        val receipt=vm.pending.value.single()
        db.collectionTransferDao().observeReceipt(receipt).filterNotNull().first { it.phase!="RECEIVING" }
    }
    @Test fun pickerStableAccountAndVerifiedSignedOutBindWithoutOwnerChoiceAndCancelCreatesNothing()=runBlocking<Unit> { fixture { db,_,gate,state,observer ->
        state.value=auth("picker-account");val account=resolved(gate,TransferOwner.Account("picker-account"))
        withIntake(db,gate,observer) { vm,uri ->
            assertTrue(vm.capturePicker());vm.receivePickerResult(emptyList());assertTrue(vm.pending.value.isEmpty())
            assertTrue(vm.capturePicker());vm.receivePickerResult(listOf(uri));val receipt=received(db,vm)
            assertEquals("account:picker-account",receipt.capturedOwner)
            assertEquals(TransferMutationResult.Accepted,RoomCollectionTransferRepository(db,gate,observer::matchesObserved).bindReceipt(TransferJobId(receipt.id),account.owner,account.generation,TransferOrigin.SAF))
        }
        state.value=SessionState.Unauthenticated
        val guest=withTimeout(5000L) { gate.sessions.filterIsInstance<TransferSession.Available>().first { it.owner is TransferOwner.VerifiedGuest } }
        withIntake(db,gate,observer) { vm,uri ->
            assertTrue(vm.capturePicker());vm.receivePickerResult(listOf(uri));val receipt=received(db,vm)
            assertTrue(receipt.capturedOwner!!.startsWith("guest:"))
            assertEquals(TransferMutationResult.Accepted,RoomCollectionTransferRepository(db,gate,observer::matchesObserved).bindReceipt(TransferJobId(receipt.id),guest.owner,guest.generation,TransferOrigin.SAF))
        }
    } }
    @Test fun pickerOldAbaAndRestoredRequestRetainReceiptWithoutAutomaticAuthorization()=runBlocking<Unit> { fixture { db,_,gate,state,observer ->
        state.value=auth("picker-account");val first=resolved(gate,TransferOwner.Account("picker-account"))
        withIntake(db,gate,observer) { vm,uri ->
            assertTrue(vm.capturePicker())
            state.value=auth("picker-other");resolved(gate,TransferOwner.Account("picker-other"))
            state.value=auth("picker-account");val returned=resolved(gate,first.owner)
            vm.receivePickerResult(listOf(uri));val receipt=received(db,vm)
            assertEquals(first.generation,receipt.authGeneration)
            assertEquals(TransferMutationResult.Rejected(TransferError.OWNER_CHANGED),RoomCollectionTransferRepository(db,gate,observer::matchesObserved).bindReceipt(TransferJobId(receipt.id),returned.owner,returned.generation,TransferOrigin.SAF))
        }
        val restored=SavedStateHandle(mapOf("picker_owner" to "account:picker-account","picker_generation" to gate.currentGeneration))
        withIntake(db,gate,observer,restored) { vm,uri ->
            vm.receivePickerResult(listOf(uri));val receipt=received(db,vm)
            assertNull(receipt.capturedOwner)
            assertFalse(observer.canBindInitialReceipt(TransferJobId(receipt.id),receipt.authGeneration,gate.currentSession as TransferSession.Available))
        }
    } }
    private fun local(id: String=id(),quantity: Int=3,condition: String="LP")=LocalWishlistEntity(id,"printing",quantity,false,true,condition,"ja",ownerUserId=TradeListOwner.GUEST)
    @Test fun loginAdoptsOnlyProvenLocalWishlistPagesAndRemoteFailureRetainsAbsoluteTarget()=runBlocking<Unit> { fixture { db,identity,gate,state,observer ->
        state.value=SessionState.Unauthenticated;val guest=resolved(gate).owner as TransferOwner.VerifiedGuest;val guestKey="guest:${guest.installationToken}";val dao=db.localWishlistDao()
        val ambiguous=local();dao.insert(ambiguous)
        val nullOwner=local().copy(ownerUserId=null);dao.insert(nullOwner)
        val legacySynced=local().copy(synced=true);dao.insert(legacySynced)
        val proven=(1..201).map { local(id=id(),condition="condition-$it") }
        db.withTransaction { proven.forEach { dao.insertProvenLocal(it,guestKey) } }
        state.value=auth("a");resolved(gate,TransferOwner.Account("a"))
        var requests=0
        val sync=TransferWishlistSync(gate,RoomTransferWishlistDeliveryStore(db,gate,observer::matchesObserved,{ 100L }),TransferWishlistDeliveryGateway { account,rows ->
            assertEquals(TransferOwner.Account("a"),account);assertTrue(rows.all { it.quantity==3 && it.foil==true && it.language=="ja" && !it.deleted });requests++;error("timeout")
        },observer::matchesObserved)
        val edits=WishlistMutationCoordinator(db,gate,state,identity,sync)
        assertTrue(edits.evictAmbiguous("a").isSuccess)
        assertEquals(0,edits.synchronize("a").getOrThrow());assertEquals(2,requests)
        assertEquals(legacySynced,dao.getById(legacySynced.id,TradeListOwner.GUEST))
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM local_wishlists WHERE id=? AND owner_user_id IS NULL",arrayOf(nullOwner.id)).use { it.moveToFirst();assertEquals(1,it.getInt(0)) }
        assertEquals(ambiguous,dao.getById(ambiguous.id,TradeListOwner.GUEST));assertNull(dao.managed(ambiguous.id))
        proven.forEach { assertEquals(3,dao.getById(it.id,"a")!!.quantity);assertEquals("account:a",dao.managed(it.id)!!.ownerKey);assertTrue(dao.managed(it.id)!!.pending) }
        assertEquals(0,edits.synchronize("a").getOrThrow());proven.forEach { assertEquals(2L,dao.managed(it.id)!!.revision) }
        state.value=auth("b");resolved(gate,TransferOwner.Account("b"));assertTrue(edits.synchronize("a").isFailure)
        proven.forEach { assertEquals("account:a",dao.managed(it.id)!!.ownerKey);assertNull(dao.getById(it.id,"b")) }
    } }
    @Test fun localWishlistMergePreservesAttributesAndOverflowRollsBackWholeAdoptionPage()=runBlocking<Unit> { fixture { db,identity,gate,state,_ ->
        state.value=SessionState.Unauthenticated;val guest=resolved(gate).owner as TransferOwner.VerifiedGuest;val key="guest:${guest.installationToken}";val dao=db.localWishlistDao()
        val first=local();dao.insertProvenLocal(first,key)
        val existing=first.copy(id=id(),ownerUserId="a",quantity=4);dao.insert(existing)
        assertEquals(listOf(existing.id),dao.adoptProvenLocalPage(key,"a"));assertEquals(7,dao.getById(existing.id,"a")!!.quantity)
        assertNull(dao.getById(first.id,TradeListOwner.GUEST));assertFalse(dao.managed(first.id)!!.pending)
        assertTrue(dao.adoptProvenLocalPage(key,"b").isEmpty());assertEquals(7,dao.getById(existing.id,"a")!!.quantity)
        val overflow=local(condition="NM");dao.insertProvenLocal(overflow,key)
        val saturated=overflow.copy(id=id(),ownerUserId="a",quantity=Int.MAX_VALUE);dao.insert(saturated)
        val healthy=local(id="00000000-0000-0000-0000-000000000001",condition="HP");dao.insertProvenLocal(healthy,key)
        try { dao.adoptProvenLocalPage(key,"a");fail("Expected overflow") } catch(_: TransferQuantityOverflowException) { }
        assertEquals(overflow,dao.getById(overflow.id,TradeListOwner.GUEST));assertEquals(key,dao.managed(overflow.id)!!.ownerKey)
        assertEquals(healthy,dao.getById(healthy.id,TradeListOwner.GUEST));assertNull(dao.getById(healthy.id,"a"));assertEquals(key,dao.managed(healthy.id)!!.ownerKey)
        assertEquals(Int.MAX_VALUE,dao.getById(saturated.id,"a")!!.quantity);assertNotNull(identity.read())
    } }
    @Test fun manualGuestAddPreservesLegacySevenAndLoginDeliversOnlyNewOne()=runBlocking<Unit> { fixture { db,identity,gate,state,observer ->
        state.value=SessionState.Unauthenticated;val guest=resolved(gate).owner as TransferOwner.VerifiedGuest;val key="guest:${guest.installationToken}";val dao=db.localWishlistDao()
        val legacy=local(quantity=7);dao.insert(legacy)
        var delivered=0
        val sync=TransferWishlistSync(gate,RoomTransferWishlistDeliveryStore(db,gate,observer::matchesObserved,{ 100L }),TransferWishlistDeliveryGateway { account,rows ->
            assertEquals(TransferOwner.Account("a"),account);assertEquals(1,rows.size);assertEquals(1,rows.single().quantity);assertEquals("printing",rows.single().printing);assertEquals(true,rows.single().foil);assertEquals("LP",rows.single().condition);assertEquals("ja",rows.single().language);delivered++
        },observer::matchesObserved)
        val edits=WishlistMutationCoordinator(db,gate,state,identity,sync)
        assertTrue(edits.add(listOf(WishlistEntry(legacy.id,"","printing",1,false,true,"LP","ja",1L))).isSuccess)
        val fresh=dao.provenLocalByAttributes(key,"printing",false,true,"LP","ja")!!
        assertNotEquals(legacy.id,fresh.id);assertEquals(1,fresh.quantity);assertEquals(legacy,dao.getById(legacy.id,TradeListOwner.GUEST));assertNull(dao.managed(legacy.id))
        state.value=auth("a");resolved(gate,TransferOwner.Account("a"))
        assertEquals(1,edits.synchronize("a").getOrThrow());assertEquals(1,delivered)
        assertEquals(1,dao.getById(fresh.id,"a")!!.quantity);assertEquals(legacy,dao.getById(legacy.id,TradeListOwner.GUEST));assertNull(dao.managed(legacy.id))
        assertEquals(0,edits.synchronize("a").getOrThrow());assertEquals(1,delivered)
    } }
    @Test fun guestReadersAndWishlistExportFreezeExposeOnlyTokenProofAndRejectStaleSessions()=runBlocking<Unit> { fixture { db,_,gate,state,observer ->
        state.value=SessionState.Unauthenticated;val guest=resolved(gate).owner as TransferOwner.VerifiedGuest;val key="guest:${guest.installationToken}";val dao=db.localWishlistDao()
        db.cardDao().upsert(CardEntity(scryfallId="printing",name="Fixture",printedName=null,lang="en",manaCost=null,cmc=1.0,colors="[]",colorIdentity="[]",typeLine="Instant",printedTypeLine=null,oracleText=null,printedText=null,keywords="[]",power=null,toughness=null,loyalty=null,setCode="tst",setName="Fixture",collectorNumber="1",rarity="common",releasedAt="2026-01-01",imageNormal=null,imageArtCrop=null,imageBackNormal=null,priceUsd=null,priceUsdFoil=null,priceEur=null,priceEurFoil=null,legalityStandard="legal",legalityPioneer="legal",legalityModern="legal",legalityCommander="legal",flavorText=null,artist=null,scryfallUri="",oracleId="oracle"))
        val legacy=local(quantity=7);dao.insert(legacy)
        val foreign=local(quantity=9);dao.insertProvenLocal(foreign,"guest:${id()}")
        val own=local(quantity=1);dao.insertProvenLocal(own,key)
        val account=local(quantity=4).copy(ownerUserId="a");dao.insert(account)
        fun readKey(owner: TransferOwner)=when(owner) { is TransferOwner.Account -> owner.id;is TransferOwner.VerifiedGuest -> "guest:${owner.installationToken}" }
        val guards=mutableListOf<()->Boolean>()
        val reader=WishlistRepositoryImpl(dao,GlobalContext.get().get<WishlistRemoteDataSource>(),readOwnerKeys=combine(state,gate.sessions) { _,s -> (s as? TransferSession.Available)?.owner?.takeIf(observer::matchesObserved)?.let(::readKey) },captureReadOwner={ read ->
            val captured=gate.currentSession as? TransferSession.Available
            val current: ()->Boolean={ captured!=null && gate.currentSession==captured && readKey(captured.owner)==read && observer.matchesObserved(captured.owner) };guards+=current;current
        })
        suspend fun ids(flow: Flow<List<WishlistEntry>>)=withTimeout(3000L) { flow.first { it.isNotEmpty() }.map { it.id } }
        assertEquals(listOf(own.id),ids(reader.observeLocal()));assertEquals(listOf(own.id),ids(reader.observeByScryfallId("printing")));assertEquals(listOf(own.id),ids(reader.observeVersionsByOracle("oracle","Fixture")))
        assertEquals(1,withTimeout(3000L) { reader.observeUnsyncedCount().first { it>0 } })
        val selection=RoomCollectionSelectionRepository(db,gate,observer);val koin=GlobalContext.get()
        val root=File(context.cacheDir,"guest-export-proof-${id()}")
        try {
            val exports=RoomCollectionExportRepository(context,db,selection,gate,observer,koin.get(),koin.get(),root=root)
            val query=CollectionSelectionQuery(CollectionSource.WISHLIST,"",null,CollectionSelectionSort.NAME,true,com.mmg.manahub.core.model.CollectionGroupingMode.NONE)
            val job=exports.create(guest,query,CollectionFileFormat.MANABOX_CSV,CollectionExportTarget.SAVE)
            val rows=db.collectionSelectionDao().scan(job,"");assertEquals(listOf(own.id),rows.map { it.sourceId });assertEquals(1L,rows.single().quantity);assertEquals(true,rows.single().rawFoil);assertEquals("LP",rows.single().rawCondition);assertEquals("ja",rows.single().rawLanguage);assertTrue(rows.single().wishlisted);assertFalse(rows.single().forTrade)
            val trade=id();selection.freeze(guest,query.copy(source=CollectionSource.FOR_TRADE),trade);assertTrue(db.collectionSelectionDao().scan(trade,"").isEmpty())
            val guestGuards=guards.toList();state.value=SessionState.Loading
            assertTrue(reader.observeLocal().first().isEmpty());assertTrue(guestGuards.none { it() })
            state.value=auth("a");val a=resolved(gate,TransferOwner.Account("a"));assertEquals(listOf(account.id),ids(reader.observeLocal()))
            val accountGuard=guards.last();val accountSnapshot=id();selection.freeze(a.owner,query,accountSnapshot);assertEquals(listOf(account.id),db.collectionSelectionDao().scan(accountSnapshot,"").map { it.sourceId })
            state.value=auth("b");resolved(gate,TransferOwner.Account("b"));state.value=auth("a");resolved(gate,TransferOwner.Account("a"));assertFalse(accountGuard())
            try { selection.page(guest,job,0L);fail("Old local export selection must be unavailable") } catch(_: TransferReadException) { }
            assertEquals(legacy,dao.getById(legacy.id,TradeListOwner.GUEST));assertEquals(foreign,dao.getById(foreign.id,TradeListOwner.GUEST))
        } finally { root.deleteRecursively() }
    } }
}
