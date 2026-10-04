package com.mmg.manahub.feature.trades.data

import com.mmg.manahub.core.data.local.dao.LocalWishlistDao
import com.mmg.manahub.core.data.local.dao.TradeCollectionSyncDao
import com.mmg.manahub.core.data.local.entity.LocalWishlistEntity
import com.mmg.manahub.core.data.local.entity.TradeWishlistCleanupEntity
import com.mmg.manahub.core.data.remote.trades.WishlistRemoteDataSource
import com.mmg.manahub.core.domain.repository.TradeCollectionLine
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class TradeWishlistCleanupTest {
    private val wishlistDao = mockk<LocalWishlistDao>(relaxed = true)
    private val syncDao = mockk<TradeCollectionSyncDao>(relaxed = true)
    private val remote = mockk<WishlistRemoteDataSource>(relaxed = true)
    private val cleanup = TradeWishlistCleanup(wishlistDao, syncDao, remote) { "owner" }

    @Test
    fun stageDecrementsOnlyMatchingVariantAndQueuesAbsoluteServerQuantity() = runTest {
        val regular = LocalWishlistEntity("regular", "card", 4, false, false, "NM", "en", true, ownerUserId = "owner")
        val foil = LocalWishlistEntity("foil", "card", 3, false, true, "NM", "en", true, ownerUserId = "owner")
        coEvery { wishlistDao.getByScryfallId("card", "owner") } returns listOf(regular, foil)

        cleanup.stage("owner", listOf(TradeCollectionLine("card", true, "NM", "en", 2)))

        coVerify(exactly = 1) { wishlistDao.updateQuantity("foil", 1, "owner") }
        coVerify(exactly = 0) { wishlistDao.updateQuantity("regular", any(), any()) }
        coVerify(exactly = 1) { syncDao.upsertWishlistCleanup(TradeWishlistCleanupEntity("owner", "foil", 1)) }
    }

    @Test
    fun drainConfirmsRemoteQuantityBeforeClearingOwnerScopedOutbox() = runTest {
        coEvery { syncDao.getPendingWishlistCleanups("owner") } returns
            listOf(TradeWishlistCleanupEntity("owner", "foil", 1))
        coEvery { remote.updateWishlistQuantityForOwner("foil", "owner", 1) } returns Result.success(Unit)
        coEvery { wishlistDao.managed("foil") } returns null

        assertTrue(cleanup.drain("owner").isSuccess)

        coVerify(exactly = 1) { remote.updateWishlistQuantityForOwner("foil", "owner", 1) }
        coVerify(exactly = 1) { syncDao.clearWishlistCleanup("owner", "foil", 1) }
    }
}
