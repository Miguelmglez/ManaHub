package com.mmg.manahub.feature.trades.data.repository

import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.core.data.local.dao.LocalOpenForTradeDao
import com.mmg.manahub.core.data.local.dao.LocalWishlistDao
import com.mmg.manahub.core.data.local.entity.LocalOpenForTradeEntity
import com.mmg.manahub.core.data.local.entity.LocalWishlistEntity
import com.mmg.manahub.core.data.remote.dto.OpenForTradeEntryDto
import com.mmg.manahub.core.data.remote.dto.WishlistEntryDto
import com.mmg.manahub.core.data.remote.trades.KeysetDrain
import com.mmg.manahub.core.data.remote.trades.OpenForTradeRemoteDataSource
import com.mmg.manahub.core.data.remote.trades.WishlistRemoteDataSource
import com.mmg.manahub.core.model.WishlistEntry
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Trades audit 2026-09-23, H8: a previous account's wishlist / open-for-trade rows must never be
 * shown to, or migrated into, the next account. Guest rows (null owner) still migrate.
 */
class TradeListsAccountOwnershipTest {

    private val wishlistDao = mockk<LocalWishlistDao>(relaxed = true)
    private val wishlistRemote = mockk<WishlistRemoteDataSource>(relaxed = true)
    private val offerDao = mockk<LocalOpenForTradeDao>(relaxed = true)
    private val offerRemote = mockk<OpenForTradeRemoteDataSource>(relaxed = true)

    private var signedInUser: String? = "user-b"
    private lateinit var wishlistRepository: WishlistRepositoryImpl
    private lateinit var offerRepository: OpenForTradeRepositoryImpl

    @Before
    fun setUp() {
        mockkStatic(FirebaseCrashlytics::class)
        every { FirebaseCrashlytics.getInstance() } returns mockk(relaxed = true)
        wishlistRepository = WishlistRepositoryImpl(wishlistDao, wishlistRemote, currentUserId = { signedInUser })
        offerRepository = OpenForTradeRepositoryImpl(offerDao, offerRemote, currentUserId = { signedInUser })
    }

    @After
    fun tearDown() {
        unmockkStatic(FirebaseCrashlytics::class)
    }

    private fun guestWish(id: String) = LocalWishlistEntity(
        id = id, scryfallId = "card-1", isFoil = null, condition = null, language = null, synced = false,
    )

    @Test
    fun `given a new account when wishlist migrates then foreign rows are evicted before reading what to push`() = runTest {
        coEvery { wishlistDao.getUnsynced() } returns listOf(guestWish("guest-1"))
        coEvery { wishlistRemote.batchAddWishlistEntries(any()) } returns Result.success(Unit)

        wishlistRepository.migrateLocalToRemote("user-b")

        coVerifyOrder {
            wishlistDao.deleteForeignAccountRows("user-b")
            wishlistDao.getUnsynced()
            wishlistDao.markSynced(listOf("guest-1"))
            wishlistDao.stampOwner(listOf("guest-1"), "user-b")
        }
    }

    @Test
    fun `given open-for-trade migrates then foreign rows are evicted first and migrated rows are claimed`() = runTest {
        coEvery { offerDao.getUnsynced() } returns listOf(
            LocalOpenForTradeEntity(id = "offer-1", localCollectionId = "row-1", scryfallId = "card-1"),
        )
        coEvery { offerRemote.batchAddOpenForTradeEntries(any()) } returns Result.success(Unit)

        offerRepository.migrateLocalToRemote("user-b")

        coVerifyOrder {
            offerDao.deleteForeignAccountRows("user-b")
            offerDao.getUnsynced()
            offerDao.stampOwner(listOf("offer-1"), "user-b")
        }
    }

    @Test
    fun `given a remote wishlist sync then downloaded rows are owned by that account`() = runTest {
        coEvery { wishlistRemote.drainWishlist("user-b") } returns KeysetDrain.complete(
            listOf(WishlistEntryDto(id = "w1", userId = "user-b", cardId = "card-1", matchAnyVariant = true, createdAt = "2024-01-01T00:00:00Z")),
        )
        val saved = slot<List<LocalWishlistEntity>>()
        coEvery { wishlistDao.upsertAll(capture(saved)) } returns Unit

        wishlistRepository.syncFromRemote("user-b")

        coVerify { wishlistDao.deleteForeignAccountRows("user-b") }
        assertEquals(listOf("user-b"), saved.captured.map { it.ownerUserId })
    }

    @Test
    fun `given a remote open-for-trade sync then downloaded rows are owned by that account`() = runTest {
        coEvery { offerRemote.drainOpenForTrade("user-b") } returns KeysetDrain.complete(
            listOf(OpenForTradeEntryDto(id = "o1", userId = "user-b", userCardId = "row-1", createdAt = "2024-01-01T00:00:00Z")),
        )
        val saved = slot<List<LocalOpenForTradeEntity>>()
        coEvery { offerDao.upsertAll(capture(saved)) } returns Unit

        offerRepository.syncFromRemote("user-b")

        coVerify { offerDao.deleteForeignAccountRows("user-b") }
        assertEquals(listOf("user-b"), saved.captured.map { it.ownerUserId })
    }

    @Test
    fun `given a signed-in user when adding to the wishlist then the new row is owned by that user`() = runTest {
        coEvery { wishlistDao.getByAttributes(any(), any(), any(), any(), any()) } returns null
        val inserted = slot<LocalWishlistEntity>()
        coEvery { wishlistDao.insert(capture(inserted)) } returns Unit

        wishlistRepository.addLocal(
            WishlistEntry(id = "", userId = "", cardId = "card-1", matchAnyVariant = true, condition = null, language = null, createdAt = 0L),
        )

        assertEquals("user-b", inserted.captured.ownerUserId)
    }

    @Test
    fun `given a guest when adding an open-for-trade offer then the row stays ownerless`() = runTest {
        signedInUser = null
        coEvery { offerDao.getByCollectionId(any()) } returns null
        val saved = slot<LocalOpenForTradeEntity>()
        coEvery { offerDao.upsert(capture(saved)) } returns Unit

        offerRepository.addLocal("card-1", "row-1", 1, false, "NM", "en")

        assertTrue(saved.captured.ownerUserId == null)
    }
}
