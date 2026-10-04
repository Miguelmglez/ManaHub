package com.mmg.manahub.feature.collection

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mmg.manahub.core.data.local.MtgDatabase
import com.mmg.manahub.core.data.remote.collection.CollectionRemoteDataSource
import com.mmg.manahub.core.data.remote.collection.SupabaseCollectionDataSource
import com.mmg.manahub.core.data.remote.collection.UserCardCollectionDto
import com.mmg.manahub.core.data.remote.trades.WishlistRemoteDataSource
import com.mmg.manahub.core.data.remote.dto.WishlistEntryDto
import com.mmg.manahub.core.domain.collection.transfer.*
import com.mmg.manahub.core.domain.repository.WishlistRepository
import com.mmg.manahub.core.sync.SyncManager
import com.mmg.manahub.core.sync.SyncState
import com.mmg.manahub.feature.collection.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import java.io.ByteArrayInputStream
import java.util.UUID

/** Explicit opt-in requires the user's already signed-in test account; fixtures remain visible. */
@RunWith(AndroidJUnit4::class)
class CollectionTransferSupabaseRoundTripTest {
    private fun id()=UUID.randomUUID().toString()
    @Test fun explicitSmallAccountImportsRoundTripAndRetryWithoutDoubleIncrement()=runBlocking<Unit> {
        assumeTrue(InstrumentationRegistry.getArguments().getString("supabaseTestAccountReady")=="true")
        withTimeout(240_000L) {
            val koin=GlobalContext.get();val gate=koin.get<TransferSessionGate>();val observer=koin.get<TransferAuthSessionObserver>()
            val session=withTimeout(15000L) { gate.sessions.filterIsInstance<TransferSession.Available>().first { it.owner is TransferOwner.Account && observer.matchesObserved(it.owner) } }
            val owner=session.owner as TransferOwner.Account
            fun ensure() { check(gate.currentSession==session && observer.matchesObserved(owner)) { "Test account session changed" } }
            val repository=koin.get<CollectionTransferRepository>();val files=koin.get<AndroidCollectionTransferFileStore>();val db=koin.get<MtgDatabase>()
            val remote: CollectionRemoteDataSource=SupabaseCollectionDataSource(koin.get());val wishlistRemote=koin.get<WishlistRemoteDataSource>();val sync=koin.get<SyncManager>()
            suspend fun collectionRows(): List<UserCardCollectionDto> {
                val rows=mutableListOf<UserCardCollectionDto>();var after: Long?=null;var afterId: String?=null
                do {
                    ensure();val result=remote.getChangesPage(0L,after,afterId,500);ensure()
                    check(result.isSuccess) { "Collection remote read failed" };val page=result.getOrNull().orEmpty()
                    check(page.all { it.userId==owner.id }) { "Collection owner mismatch" };rows+=page
                    if(page.size<500)break
                    after=page.last().updatedAt;afterId=page.last().id
                } while(true)
                return rows
            }
            suspend fun wishlistRows(): List<WishlistEntryDto> {
                ensure();val result=wishlistRemote.getWishlist(owner.id);ensure()
                check(result.isSuccess) { "Wishlist remote read failed" };val rows=result.getOrNull().orEmpty()
                check(rows.all { it.userId==owner.id }) { "Wishlist owner mismatch" };return rows
            }
            check(sync.sync(owner.id).state==SyncState.SUCCESS) { "Initial account synchronization failed" };ensure()
            check(koin.get<WishlistRepository>().syncFromRemote(owner.id).isSuccess) { "Initial wishlist synchronization failed" };ensure()
            val collectionBefore=collectionRows();val wishlistBefore=wishlistRows()
            suspend fun receiveFixture(copies: Long,destination: TransferDestination): Triple<TransferJobId,TransferActionId,String> {
                ensure();val job=TransferJobId(id());val content="$copies Lightning Bolt\n".toByteArray()
                files.receive(job,session.generation,listOf(object: TransferInputSource { override val identity="supabase-acceptance-fixture";override fun open()=ByteArrayInputStream(content) }),session)
                check(repository.bindReceipt(job,owner,session.generation,TransferOrigin.PASTE)==TransferMutationResult.Accepted) { "Fixture receipt binding failed" }
                val ready=repository.observeSummary(job,owner).filterNotNull().first { it.phase==TransferPhase.REVIEW_READY || it.phase==TransferPhase.REVIEW_REQUIRED }
                ensure();val original=repository.readPage(job,owner,null).entries.single()
                check(original.destination==TransferDestination.NONE && original.error==null) { "Fixture resolution requires manual review" }
                if(destination==TransferDestination.COLLECTION) {
                    val remoteBaseline=collectionBefore.filter { it.scryfallId==original.scryfallId && it.isFoil && it.condition=="LP" && it.language=="ja" && !it.isDeleted }.sumOf { it.quantity.toLong() }
                    val local=db.userCardCollectionDao().getByCompositeKey(owner.id,original.scryfallId,true,"LP","ja")
                    val localBaseline=local?.takeUnless { it.isDeleted }?.quantity?.toLong() ?: 0L
                    check(localBaseline==remoteBaseline) { "Collection baseline differs; fixture action was not authorized" }
                } else {
                    val remoteBaseline=wishlistBefore.filter { it.cardId==original.scryfallId && it.isFoil==true && it.condition=="LP" && it.language=="ja" && !it.matchAnyVariant }.sumOf { it.quantity.toLong() }
                    val local=db.localWishlistDao().getByScryfallId(original.scryfallId,owner.id).filter { it.isFoil==true && it.condition=="LP" && it.language=="ja" && !it.matchAnyVariant }
                    check(local.none { !it.synced || db.localWishlistDao().managed(it.id)?.pending==true }) { "Wishlist variant has previous pending edits; fixture action was not authorized" }
                    check(local.sumOf { it.quantity.toLong() }==remoteBaseline) { "Wishlist baseline differs; fixture action was not authorized" }
                }
                check(repository.editPendingEntry(job,owner,ready.generation,original.copy(isFoil=true,condition="LP",language="ja",destination=destination))==TransferMutationResult.Accepted) { "Explicit fixture attributes failed" }
                val summary=repository.observeSummary(job,owner).filterNotNull().first();val edited=repository.readPage(job,owner,null).entries.single();val action=TransferActionId(id())
                check(repository.confirmAction(owner,TransferActionRequest(action,job,summary.generation,destination,TransferActionScope.Entry(edited.id,edited.version),acceptRepeatedFiles=true))==TransferMutationResult.Accepted) { "Explicit fixture action failed" }
                repository.observeSummary(job,owner).filterNotNull().first { if(destination==TransferDestination.COLLECTION)it.appliedCopies==copies else it.wishlistCompletedCopies==copies }
                ensure();return Triple(job,action,edited.scryfallId)
            }
            val collection=receiveFixture(2L,TransferDestination.COLLECTION)
            val wishlist=receiveFixture(3L,TransferDestination.WISHLIST)
            fun collectionQuantity(rows: List<UserCardCollectionDto>)=rows.filter { it.scryfallId==collection.third && it.isFoil && it.condition=="LP" && it.language=="ja" && !it.isDeleted }.sumOf { it.quantity.toLong() }
            fun wishlistQuantity(rows: List<WishlistEntryDto>)=rows.filter { it.cardId==wishlist.third && it.isFoil==true && it.condition=="LP" && it.language=="ja" && !it.matchAnyVariant }.sumOf { it.quantity.toLong() }
            val expectedCollection=collectionQuantity(collectionBefore)+2L;val expectedWishlist=wishlistQuantity(wishlistBefore)+3L
            withTimeout(90_000L) { while(collectionQuantity(collectionRows())!=expectedCollection || wishlistQuantity(wishlistRows())!=expectedWishlist) { ensure();delay(1000L) } }
            val originalCollection=repository.observeSummary(collection.first,owner).filterNotNull().first()
            check(koin.get<RoomTransferCollectionExecutor>().runSlice(collection.second,owner)==TransferCollectionApplyResult.FINISHED) { "Completed collection replay failed" }
            check(koin.get<TransferWishlistExecutor>().runSlice(wishlist.second,owner)==TransferWishlistApplyResult.LOCAL_FINISHED) { "Completed wishlist replay failed" }
            check(sync.sync(owner.id).state==SyncState.SUCCESS) { "Retry synchronization failed" }
            koin.get<TransferWishlistSync>().runSlice(owner);ensure()
            assertEquals(expectedCollection,collectionQuantity(collectionRows()));assertEquals(expectedWishlist,wishlistQuantity(wishlistRows()))
            assertEquals(originalCollection.appliedCopies,repository.observeSummary(collection.first,owner).filterNotNull().first().appliedCopies)
            check(!db.collectionTransferDao().hasWishlistPending("account:${owner.id}")) { "Account wishlist delivery remains pending" }
        }
    }
}
