package com.mmg.manahub.feature.collection

import androidx.room.Room
import android.content.ContextWrapper
import androidx.room.withTransaction
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mmg.manahub.core.data.local.*
import com.mmg.manahub.core.data.local.entity.LocalWishlistEntity
import com.mmg.manahub.core.domain.auth.*
import com.mmg.manahub.core.domain.collection.transfer.*
import com.mmg.manahub.core.model.WishlistEntry
import com.mmg.manahub.feature.collection.data.*
import com.mmg.manahub.feature.trades.data.WishlistMutationCoordinator
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class WishlistMutationDurabilityTest {
    @get:Rule val helper=MigrationTestHelper(InstrumentationRegistry.getInstrumentation(),MtgDatabase::class.java,emptyList(),FrameworkSQLiteOpenHelperFactory())
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val owner=TransferOwner.Account("mutation-fixture")
    private fun id()=UUID.randomUUID().toString()
    private fun auth(id: String)=SessionState.Authenticated(AuthUser(id,null,null,null,null,"fixture"))
    private fun row(id: String=id(),quantity: Int=3)=LocalWishlistEntity(id,"printing",quantity,false,true,"LP","ja",ownerUserId=owner.id)
    private data class Environment(val gate: TransferSessionGate,val state: MutableStateFlow<SessionState>,val store: RoomTransferWishlistDeliveryStore,val sync: TransferWishlistSync,val edits: WishlistMutationCoordinator)
    private suspend fun environment(db: MtgDatabase,now: ()->Long={ 100L },gateway: TransferWishlistDeliveryGateway=TransferWishlistDeliveryGateway { _,_ -> },reporter: com.mmg.manahub.core.common.CrashReporter?=null): Environment {
        val gate=TransferSessionGate().also { it.changeOwner(owner) }; val state=MutableStateFlow<SessionState>(auth(owner.id))
        val matches: (TransferOwner)->Boolean={ (state.value as? SessionState.Authenticated)?.user?.id==(it as? TransferOwner.Account)?.id }
        val store=RoomTransferWishlistDeliveryStore(db,gate,matches,now); val sync=TransferWishlistSync(gate,store,gateway,matches)
        return Environment(gate,state,store,sync,WishlistMutationCoordinator(db,gate,state,VerifiedTransferGuestIdentity(context),sync,reporter))
    }
    @Test fun migration63to64PreservesRowsAndBackfillsOrphanDeletionIntent() {
        val name="wishlist64-${id()}"; val before=helper.createDatabase(name,63)
        before.execSQL("INSERT INTO local_wishlists(id,scryfall_id,quantity,match_any_variant,is_foil,condition,language,synced,created_at,owner_user_id) VALUES ('live','printing',7,0,1,'LP','ja',0,11,'fixture')")
        before.execSQL("INSERT INTO collection_transfer_wishlist_dirty(wishlist_id,owner_key,revision) VALUES ('live','account:fixture',4),('orphan','account:fixture',9)")
        before.execSQL("INSERT INTO trade_wishlist_cleanup(user_id,wishlist_id,target_quantity) VALUES ('fixture','wish',4)")
        before.execSQL("INSERT INTO collection_transfer_guest_rows(row_id,owner_key) VALUES ('row','guest:fixture')"); before.close()
        val after=helper.runMigrationsAndValidate(name,64,true,MIGRATION_63_64)
        after.query("SELECT revision,deleted,pending,quantity,foil,condition,language,created_at FROM collection_transfer_wishlist_dirty WHERE wishlist_id='live'").use { assertTrue(it.moveToFirst()); assertEquals(4L,it.getLong(0)); assertEquals(0,it.getInt(1)); assertEquals(1,it.getInt(2)); assertEquals(7,it.getInt(3)); assertEquals(1,it.getInt(4)); assertEquals("LP",it.getString(5)); assertEquals("ja",it.getString(6)); assertEquals(11L,it.getLong(7)) }
        after.query("SELECT revision,deleted,pending FROM collection_transfer_wishlist_dirty WHERE wishlist_id='orphan'").use { assertTrue(it.moveToFirst()); assertEquals(9L,it.getLong(0)); assertEquals(1,it.getInt(1)); assertEquals(1,it.getInt(2)) }
        for(table in listOf("trade_wishlist_cleanup","collection_transfer_guest_rows","local_wishlists"))after.query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); assertEquals(1,it.getInt(0)) }
        after.close(); context.deleteDatabase(name)
    }
    @Test fun realRollbackKeepsWishlistAndLedgerTogether()=runBlocking<Unit> {
        val db=Room.inMemoryDatabaseBuilder(context,MtgDatabase::class.java).build()
        try {
            val r=row(); db.localWishlistDao().insert(r); val before=db.localWishlistDao().managed(r.id)
            try { db.withTransaction { db.localWishlistDao().deleteById(r.id,owner.id); error("Injected crash after tombstone") } } catch(_: IllegalStateException) { }
            assertEquals(r,db.localWishlistDao().getById(r.id,owner.id)); assertEquals(before,db.localWishlistDao().managed(r.id))
            try { db.withTransaction { db.localWishlistDao().updateQuantity(r.id,9,owner.id); error("Injected crash after edit") } } catch(_: IllegalStateException) { }
            assertEquals(3,db.localWishlistDao().getById(r.id,owner.id)!!.quantity); assertEquals(before,db.localWishlistDao().managed(r.id))
        } finally { db.close() }
    }
    @Test fun editAndDeleteCompleteWhileOldUpsertIsInFlight()=runBlocking<Unit> {
        val db=Room.inMemoryDatabaseBuilder(context,MtgDatabase::class.java).build()
        try {
            val entered=CompletableDeferred<Unit>(); val release=CompletableDeferred<Unit>(); val r=row(); db.localWishlistDao().insert(r)
            val e=environment(db,gateway=TransferWishlistDeliveryGateway { _,rows -> assertFalse(rows.single().deleted); entered.complete(Unit); release.await() })
            coroutineScope {
                val remote=async(Dispatchers.IO) { e.sync.runSlice(owner) }; entered.await()
                withTimeout(2000) { assertTrue(e.edits.quantity(r.id,8).isSuccess); assertTrue(e.edits.remove(r.id).isSuccess) }
                assertNull(db.localWishlistDao().getById(r.id,owner.id)); release.complete(Unit); assertEquals(TransferSliceResult.CONTINUE,remote.await())
            }
            val pending=e.store.pending(owner).single(); assertTrue(pending.deleted); assertEquals(0,pending.quantity); assertEquals(3L,pending.revision)
        } finally { db.close() }
    }
    @Test fun oldAckAfterNewAckReactivatesLatestAbsoluteTarget()=runBlocking<Unit> {
        val db=Room.inMemoryDatabaseBuilder(context,MtgDatabase::class.java).build()
        try {
            val r=row(); db.localWishlistDao().insert(r); val e=environment(db); val old=e.store.pending(owner).single()
            assertTrue(e.edits.quantity(r.id,8).isSuccess); val latest=e.store.pending(owner).single(); assertEquals(1,e.store.acknowledge(owner,listOf(latest)))
            assertEquals(0,e.store.acknowledge(owner,listOf(old))); val repair=e.store.pending(owner).single(); assertEquals(8,repair.quantity); assertTrue(repair.revision>latest.revision)
            db.localWishlistDao().deleteSyncedByIds(listOf(r.id),owner.id); assertEquals(8,db.localWishlistDao().getById(r.id,owner.id)!!.quantity)
            assertEquals(1,e.store.acknowledge(owner,listOf(repair))); assertFalse(e.store.hasPending(owner)); assertEquals(8,db.localWishlistDao().getById(r.id,owner.id)!!.quantity)
        } finally { db.close() }
    }
    @Test fun acknowledgedTombstoneStaysDormantUntilPullSeesGhost()=runBlocking<Unit> {
        val db=Room.inMemoryDatabaseBuilder(context,MtgDatabase::class.java).build()
        try {
            val r=row(); db.localWishlistDao().insert(r); val e=environment(db); assertTrue(e.edits.remove(r.id).isSuccess)
            val deletion=e.store.pending(owner).single(); assertEquals(1,e.store.acknowledge(owner,listOf(deletion))); assertTrue(e.store.pending(owner).isEmpty()); assertFalse(e.store.hasPending(owner))
            db.localWishlistDao().upsertRemoteProtected(listOf(r.copy(synced=true)),owner.id)
            assertNull(db.localWishlistDao().getById(r.id,owner.id)); val repair=e.store.pending(owner).single(); assertTrue(repair.deleted); assertEquals(deletion.revision+1L,repair.revision)
            assertEquals(1,e.store.acknowledge(owner,listOf(repair))); assertTrue(e.store.pending(owner).isEmpty())
        } finally { db.close() }
    }
    @Test fun timeoutCooldownAndReopenKeepDeletionWithoutSyncedAssumptions()=runBlocking<Unit> {
        val name="wishlist-tombstone-${id()}"
        fun open()=Room.databaseBuilder(context,MtgDatabase::class.java,name).build()
        var db=open(); var now=100L
        try {
            val r=row(); db.localWishlistDao().insert(r); var e=environment(db,{ now },TransferWishlistDeliveryGateway { _,_ -> error("Ambiguous timeout") })
            assertEquals(TransferSliceResult.WAITING,e.sync.runSlice(owner)); assertTrue(e.edits.remove(r.id).isSuccess)
            assertEquals(TransferSliceResult.WAITING,e.sync.runSlice(owner)); assertTrue(e.store.hasPending(owner)); assertTrue(e.store.pending(owner).isEmpty())
            db.close(); db=open(); e=environment(db,{ now }); assertEquals(TransferSliceResult.WAITING,e.sync.runSlice(owner)); now+=30_000L
            assertEquals(TransferSliceResult.FINISHED,e.sync.runSlice(owner)); db.close(); db=open(); e=environment(db,{ now })
            assertTrue(e.store.pending(owner).isEmpty()); assertTrue(db.localWishlistDao().managed(r.id)!!.deleted); assertFalse(db.localWishlistDao().managed(r.id)!!.pending)
            db.localWishlistDao().upsertRemoteProtected(listOf(r.copy(synced=true)),owner.id); assertNull(db.localWishlistDao().getById(r.id,owner.id)); assertEquals(TransferSliceResult.FINISHED,e.sync.runSlice(owner))
        } finally { db.close(); context.deleteDatabase(name) }
    }
    @Test fun capturedOwnerNeverAcknowledgesUnderOtherAccountAndReturnsSafely()=runBlocking<Unit> {
        val db=Room.inMemoryDatabaseBuilder(context,MtgDatabase::class.java).build()
        try {
            val r=row(); db.localWishlistDao().insert(r); val entered=CompletableDeferred<Unit>(); val release=CompletableDeferred<Unit>()
            val e=environment(db,gateway=TransferWishlistDeliveryGateway { captured,_ -> assertEquals(owner,captured); entered.complete(Unit); release.await() })
            coroutineScope { val remote=async(Dispatchers.IO) { e.sync.runSlice(owner) }; entered.await(); e.state.value=auth("other"); e.gate.changeOwner(TransferOwner.Account("other")); release.complete(Unit); assertEquals(TransferSliceResult.WAITING,remote.await()) }
            assertTrue(db.localWishlistDao().managed(r.id)!!.pending); assertTrue(e.edits.quantity(r.id,99).isSuccess); assertEquals(3,db.localWishlistDao().getById(r.id,owner.id)!!.quantity)
            e.state.value=auth(owner.id); e.gate.changeOwner(owner); val next=TransferWishlistSync(e.gate,e.store,{ _,rows -> assertEquals(3,rows.single().quantity) },{ it==owner }); assertEquals(TransferSliceResult.FINISHED,next.runSlice(owner))
            e.state.value=SessionState.Loading; assertTrue(e.edits.quantity(r.id,7).isFailure); assertEquals(3,db.localWishlistDao().getById(r.id,owner.id)!!.quantity)
        } finally { db.close() }
    }
    @Test fun mergeEditorWritesSurvivorAndRemovedIdentityInSameTransaction()=runBlocking<Unit> {
        val db=Room.inMemoryDatabaseBuilder(context,MtgDatabase::class.java).build()
        try {
            val a=row(); val b=row(quantity=4).copy(isFoil=false); db.localWishlistDao().insert(a); db.localWishlistDao().insert(b); val e=environment(db)
            assertTrue(e.edits.edit(a.id,b.scryfallId,b.isFoil,b.condition,b.language,6,owner.id).isSuccess)
            assertNull(db.localWishlistDao().getById(a.id,owner.id)); assertEquals(10,db.localWishlistDao().getById(b.id,owner.id)!!.quantity)
            val pending=e.store.pending(owner); assertEquals(2,pending.size); assertTrue(pending.single { it.id==a.id }.deleted); assertEquals(10,pending.single { it.id==b.id }.quantity)
            assertEquals(0,db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM user_card_collection").use { it.moveToFirst(); it.getInt(0) })
        } finally { db.close() }
    }
    @Test fun legacyDaoWritersCannotBypassManagedIntentOrClaimGuest()=runBlocking<Unit> {
        val db=Room.inMemoryDatabaseBuilder(context,MtgDatabase::class.java).build()
        try {
            val r=row(); db.localWishlistDao().insert(r); db.localWishlistDao().updateQuantity(r.id,7,owner.id); assertEquals(7,db.localWishlistDao().managed(r.id)!!.quantity)
            db.localWishlistDao().deleteById(r.id,owner.id); assertTrue(db.localWishlistDao().managed(r.id)!!.deleted)
            val guest=row().copy(ownerUserId=TradeListOwner.GUEST); db.localWishlistDao().insertProvenLocal(guest,"guest:verified-token")
            assertEquals(0,db.localWishlistDao().unmanagedUnsyncedPage(owner.id).size); db.localWishlistDao().claimUnmanagedGuest(guest.id,owner.id); assertNotNull(db.localWishlistDao().getById(guest.id,TradeListOwner.GUEST))
            val ambiguous=row().copy(ownerUserId=null); db.localWishlistDao().insert(ambiguous); assertNull(db.localWishlistDao().managed(ambiguous.id)); db.localWishlistDao().deleteAmbiguousRows(); assertTrue(db.localWishlistDao().getByScryfallId(ambiguous.scryfallId,owner.id).isEmpty())
        } finally { db.close() }
    }
    @Test fun bulkPendingIsBoundedAndQuantityOverflowRollsBackAllEntries()=runBlocking<Unit> {
        val db=Room.inMemoryDatabaseBuilder(context,MtgDatabase::class.java).build()
        try {
            repeat(201) { db.localWishlistDao().insert(row().copy(scryfallId="printing-$it")) }; val e=environment(db)
            assertEquals(200,e.store.pending(owner).size); assertEquals(TransferSliceResult.CONTINUE,e.sync.runSlice(owner)); assertEquals(1,e.store.pending(owner).size); assertEquals(TransferSliceResult.FINISHED,e.sync.runSlice(owner))
            val full=row(quantity=Int.MAX_VALUE).copy(scryfallId="full"); db.localWishlistDao().insert(full)
            val entries=listOf(WishlistEntry(id(),owner.id,"new",1,false,false,"NM","en",1L),WishlistEntry(id(),owner.id,"full",1,false,true,"LP","ja",1L))
            assertTrue(e.edits.add(entries,owner.id).isFailure); assertTrue(db.localWishlistDao().getByScryfallId("new",owner.id).isEmpty()); assertEquals(Int.MAX_VALUE,db.localWishlistDao().getById(full.id,owner.id)!!.quantity)
        } finally { db.close() }
    }
    @Test fun localMutationFailureTelemetryNeverForwardsSqlPayloadOrExpectedOwnerChanges()=runBlocking<Unit> {
        val db=Room.inMemoryDatabaseBuilder(context,MtgDatabase::class.java).build();val marker="PRIVATE_SQL_URI_OWNER_MARKER"
        val messages=mutableListOf<String>();val failures=mutableListOf<Throwable>()
        val reporter=object: com.mmg.manahub.core.common.CrashReporter {
            override fun log(message: String) { messages+=message }
            override fun setCustomKey(key: String,value: String) { messages+="$key=$value" }
            override fun recordException(throwable: Throwable) { failures+=throwable;messages+=throwable.stackTraceToString() }
        }
        try {
            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_private_fixture BEFORE INSERT ON local_wishlists WHEN NEW.scryfall_id='$marker' BEGIN SELECT RAISE(ABORT,'$marker content://private SQL'); END")
            val e=environment(db,reporter=reporter)
            assertTrue(e.edits.add(listOf(WishlistEntry(id(),owner.id,marker,1,false,true,"LP","ja",1L)),owner.id).isFailure)
            assertEquals(1,failures.size);assertNull(failures.single().cause);assertTrue(failures.single().suppressed.isEmpty());assertTrue(messages.contains("collection_wishlist_local_mutation_failed"));assertFalse(messages.joinToString().contains(marker))
            val before=messages.size;e.state.value=SessionState.Loading
            assertTrue(e.edits.quantity("unknown",1).isFailure);assertEquals(before,messages.size);assertEquals(1,failures.size)
        } finally { db.close() }
    }
    @Test fun manualGuestMutationsRequireCurrentTokenAndPreserveAmbiguousAndOtherTokenRows()=runBlocking<Unit> {
        val prefix="manual-guest-${id()}"
        val isolated=object: ContextWrapper(context) { override fun getSharedPreferences(name: String,mode: Int)=context.getSharedPreferences("$prefix-$name",mode) }
        val db=Room.inMemoryDatabaseBuilder(context,MtgDatabase::class.java).build()
        try {
            val identity=VerifiedTransferGuestIdentity(isolated);val guest=identity.ensureLocalIdentity();val key="guest:${guest.installationToken}"
            val gate=TransferSessionGate().also { it.changeOwner(guest) };val state=MutableStateFlow<SessionState>(SessionState.Unauthenticated)
            val matches: (TransferOwner)->Boolean={ it==guest && state.value==SessionState.Unauthenticated }
            val sync=TransferWishlistSync(gate,RoomTransferWishlistDeliveryStore(db,gate,matches,{ 100L }),TransferWishlistDeliveryGateway { _,_ -> error("Local edits cannot dispatch") },matches)
            val edits=WishlistMutationCoordinator(db,gate,state,identity,sync);val dao=db.localWishlistDao()
            val legacy=row(quantity=7).copy(ownerUserId=TradeListOwner.GUEST);dao.insert(legacy)
            val foreign=row(quantity=9).copy(ownerUserId=TradeListOwner.GUEST);dao.insertProvenLocal(foreign,"guest:${id()}");val foreignProof=dao.managed(foreign.id)
            for(target in listOf(legacy,foreign)) {
                assertTrue(edits.quantity(target.id,100).isSuccess);assertTrue(edits.quantity(target.id,0).isSuccess)
                assertEquals(com.mmg.manahub.core.domain.repository.UpdateEntryOutcome.ENTRY_NOT_FOUND,edits.edit(target.id,"changed",false,"NM","en",4,null).getOrThrow())
                assertTrue(edits.remove(target.id).isSuccess)
                assertEquals(target,dao.getById(target.id,TradeListOwner.GUEST))
            }
            try { dao.stageManaged(legacy,capturedOwner=key);fail("Legacy row must not acquire provenance") } catch(_: IllegalArgumentException) { }
            try { db.collectionTransferDao().stageWishlistDirty(legacy.id,key);fail("Transfer staging must not claim legacy row") } catch(_: IllegalArgumentException) { }
            assertNull(dao.managed(legacy.id));assertEquals(foreignProof,dao.managed(foreign.id))
            val own=row(quantity=5).copy(ownerUserId=TradeListOwner.GUEST);dao.insertProvenLocal(own,key)
            val unrelated=row(quantity=6).copy(scryfallId="unrelated",ownerUserId=TradeListOwner.GUEST);dao.insertProvenLocal(unrelated,key)
            assertTrue(edits.decrement("printing",2).isSuccess)
            assertEquals(3,dao.provenLocalById(key,own.id)!!.quantity);assertEquals(unrelated,dao.provenLocalById(key,unrelated.id))
            assertEquals(legacy,dao.getById(legacy.id,TradeListOwner.GUEST));assertEquals(foreign,dao.getById(foreign.id,TradeListOwner.GUEST))
            assertTrue(edits.add(listOf(WishlistEntry(id(),"","printing",1,false,true,"LP","ja",1L))).isSuccess)
            assertEquals(4,dao.provenLocalById(key,own.id)!!.quantity)
            assertTrue(edits.edit(unrelated.id,"printing",true,"LP","ja",2,null).isSuccess)
            assertEquals(6,dao.provenLocalById(key,own.id)!!.quantity);assertNull(dao.provenLocalById(key,unrelated.id));assertTrue(dao.managed(unrelated.id)!!.deleted)
            val full=row(quantity=Int.MAX_VALUE).copy(scryfallId="full",ownerUserId=TradeListOwner.GUEST);dao.insertProvenLocal(full,key);val fullProof=dao.managed(full.id)
            assertTrue(edits.add(listOf(WishlistEntry(id(),"","new",1,false,true,"LP","ja",1L),WishlistEntry(id(),"","full",1,false,true,"LP","ja",1L))).isFailure)
            assertTrue(dao.provenLocalByPrinting(key,"new").isEmpty());assertEquals(full,dao.provenLocalById(key,full.id));assertEquals(fullProof,dao.managed(full.id))
            assertTrue(edits.quantity(own.id,2).isSuccess);assertEquals(2,dao.provenLocalById(key,own.id)!!.quantity)
            assertTrue(edits.remove(own.id).isSuccess);assertNull(dao.provenLocalById(key,own.id));assertTrue(dao.managed(own.id)!!.deleted)
            assertEquals(legacy,dao.getById(legacy.id,TradeListOwner.GUEST));assertEquals(foreignProof,dao.managed(foreign.id))
        } finally { db.close();context.deleteSharedPreferences("$prefix-collection_transfer_identity") }
    }
}
