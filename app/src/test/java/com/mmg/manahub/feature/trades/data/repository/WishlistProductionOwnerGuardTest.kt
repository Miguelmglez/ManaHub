package com.mmg.manahub.feature.trades.data.repository

import com.mmg.manahub.core.data.local.dao.LocalWishlistDao
import com.mmg.manahub.core.data.remote.trades.WishlistRemoteDataSource
import com.mmg.manahub.feature.trades.data.WishlistMutationCoordinator
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class WishlistProductionOwnerGuardTest {
    @Test fun wrongAccountNeverDispatchesRemoteRead()=runTest {
        val remote=mockk<WishlistRemoteDataSource>(relaxed=true)
        val repository=WishlistRepositoryImpl(mockk<LocalWishlistDao>(),remote,{ "other" },mutations=mockk<WishlistMutationCoordinator>())
        assertTrue(repository.getRemote("fixture").isFailure)
        coVerify(exactly=0) { remote.getWishlist(any()) }
    }
    @Test fun obsoleteRemoteReadIsNotPublishedAfterSwitch()=runTest {
        val remote=mockk<WishlistRemoteDataSource>(); var owner="fixture"
        coEvery { remote.getWishlist("fixture") } coAnswers { owner="other"; Result.success(emptyList()) }
        val repository=WishlistRepositoryImpl(mockk<LocalWishlistDao>(),remote,{ owner },mutations=mockk<WishlistMutationCoordinator>())
        assertTrue(repository.getRemote("fixture").isFailure)
    }
}
