package com.mmg.manahub.feature.collection.data

import com.mmg.manahub.core.data.remote.trades.WishlistRemoteDataSource
import com.mmg.manahub.core.domain.collection.transfer.*
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class AndroidTransferWishlistDeliveryGatewayTest {
    private val owner=TransferOwner.Account("fixture")
    private val row=TransferWishlistDelivery("wanted",1L,"printing",7,true,"LP","ja",false,1L)
    @Test fun mixedBatchUsesExplicitOwnerAndAbsolutePayloadWithBoundedDelete()=runTest {
        val remote=mockk<WishlistRemoteDataSource>(relaxed=true)
        coEvery { remote.batchAddWishlistEntries(any()) } returns Result.success(Unit)
        coEvery { remote.removeWishlistEntriesForOwner(any(),any()) } returns Result.success(Unit)
        AndroidTransferWishlistDeliveryGateway(remote,{ it==owner }).deliver(owner,listOf(row,row.copy(id="removed",deleted=true,quantity=0)))
        coVerify(exactly=1) { remote.batchAddWishlistEntries(match { it.single().userId==owner.id && it.single().quantity==7 && it.single().isFoil==true && it.single().condition=="LP" && it.single().language=="ja" }) }
        coVerify(exactly=1) { remote.removeWishlistEntriesForOwner(listOf("removed"),owner.id) }
        coVerify(exactly=0) { remote.removeWishlistEntry(any()) }
    }
    @Test fun switchedOwnerStopsDeletionAfterOldUpsertResult()=runTest {
        val remote=mockk<WishlistRemoteDataSource>(relaxed=true); var active=true
        coEvery { remote.batchAddWishlistEntries(any()) } coAnswers { active=false; Result.success(Unit) }
        try { AndroidTransferWishlistDeliveryGateway(remote,{ active }).deliver(owner,listOf(row,row.copy(id="removed",deleted=true))); fail("Owner changed") } catch(_: TransferSessionChangedException) { }
        coVerify(exactly=0) { remote.removeWishlistEntriesForOwner(any(),any()) }
    }
}
