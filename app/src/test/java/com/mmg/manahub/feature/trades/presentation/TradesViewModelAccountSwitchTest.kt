package com.mmg.manahub.feature.trades.presentation

import com.mmg.manahub.core.domain.auth.AuthRepository
import com.mmg.manahub.core.domain.auth.AuthUser
import com.mmg.manahub.core.domain.auth.SessionState
import com.mmg.manahub.core.domain.repository.FriendRepository
import com.mmg.manahub.core.domain.repository.OpenForTradeRepository
import com.mmg.manahub.core.domain.repository.WishlistRepository
import com.mmg.manahub.core.model.Friend
import com.mmg.manahub.core.model.OpenForTradeEntry
import com.mmg.manahub.core.model.WishlistEntry
import com.mmg.manahub.feature.friends.domain.usecase.GetFriendsUseCase
import com.mmg.manahub.feature.trades.domain.usecase.GetLocalOpenForTradeUseCase
import com.mmg.manahub.feature.trades.domain.usecase.GetLocalWishlistUseCase
import com.mmg.manahub.feature.trades.domain.usecase.SyncTradeListsFromRemoteUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TradesViewModelAccountSwitchTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `A to B to A clears retained lists before each owner emits`() = runTest {
        val session = MutableStateFlow<SessionState>(authenticated("user-a"))
        val wishlist = MutableSharedFlow<List<WishlistEntry>>()
        val offers = MutableSharedFlow<List<OpenForTradeEntry>>()
        val friends = MutableSharedFlow<List<Friend>>()
        val auth = mockk<AuthRepository>()
        val wishlistRepo = mockk<WishlistRepository>()
        val offerRepo = mockk<OpenForTradeRepository>()
        val friendRepo = mockk<FriendRepository>()
        every { auth.sessionState } returns session
        every { wishlistRepo.observeLocal() } returns wishlist
        every { offerRepo.observeLocal() } returns offers
        every { friendRepo.observeFriends() } returns friends
        coEvery { wishlistRepo.syncFromRemote(any()) } returns Result.success(Unit)
        coEvery { offerRepo.syncFromRemote(any()) } returns Result.success(Unit)
        val vm = TradesViewModel(
            auth,
            GetLocalWishlistUseCase(wishlistRepo),
            GetLocalOpenForTradeUseCase(offerRepo),
            GetFriendsUseCase(friendRepo),
            SyncTradeListsFromRemoteUseCase(wishlistRepo, offerRepo),
        )
        runCurrent()
        val aWishlist = mockk<WishlistEntry>()
        val aOffer = mockk<OpenForTradeEntry>()
        val aFriend = mockk<Friend>()
        wishlist.emit(listOf(aWishlist))
        offers.emit(listOf(aOffer))
        friends.emit(listOf(aFriend))
        runCurrent()
        assertEquals(listOf(aWishlist), vm.uiState.value.wishlist)
        assertEquals(listOf(aOffer), vm.uiState.value.openForTrade)
        assertEquals(listOf(aFriend), vm.uiState.value.friends)

        session.value = authenticated("user-b")
        runCurrent()
        assertTrue(vm.uiState.value.isLoggedIn)
        assertTrue(vm.uiState.value.wishlist.isEmpty())
        assertTrue(vm.uiState.value.openForTrade.isEmpty())
        assertTrue(vm.uiState.value.friends.isEmpty())

        session.value = authenticated("user-a")
        runCurrent()
        assertTrue(vm.uiState.value.wishlist.isEmpty())
        val restored = mockk<WishlistEntry>()
        wishlist.emit(listOf(restored))
        runCurrent()
        assertEquals(listOf(restored), vm.uiState.value.wishlist)
    }

    private fun authenticated(id: String) = SessionState.Authenticated(
        AuthUser(id, "$id@test.com", id, "#TEST", null, "email")
    )
}
