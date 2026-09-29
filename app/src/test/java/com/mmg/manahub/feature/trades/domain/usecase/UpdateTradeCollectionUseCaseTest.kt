package com.mmg.manahub.feature.trades.domain.usecase

import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.core.data.local.dao.TradeCollectionSyncDao
import com.mmg.manahub.core.data.local.entity.TradeOfferCleanupEntity
import com.mmg.manahub.feature.trades.data.TradeWishlistCleanup
import com.mmg.manahub.core.domain.repository.OpenForTradeRepository
import com.mmg.manahub.core.domain.repository.TradeCollectionApplyResult
import com.mmg.manahub.core.domain.repository.TradeCollectionLine
import com.mmg.manahub.core.domain.repository.UserCardRepository
import com.mmg.manahub.core.model.TradeItem
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [UpdateTradeCollectionUseCase].
 *
 * [UserCardRepository.applyTradeCollectionChanges] is mocked to run its gate and completion
 * callbacks the way the real transactional implementation does, so the tests observe which lines
 * the use case builds, whether the idempotency gate is honoured, and which network follow-ups run
 * after the local commit.
 */
class UpdateTradeCollectionUseCaseTest {

    private val userCardRepository = mockk<UserCardRepository>(relaxed = true)
    private val wishlistCleanup = mockk<TradeWishlistCleanup>(relaxed = true)
    private val openForTradeRepository = mockk<OpenForTradeRepository>(relaxed = true)
    private val syncDao = mockk<TradeCollectionSyncDao>(relaxed = true)
    private val crashlytics = mockk<FirebaseCrashlytics>(relaxed = true)

    private lateinit var useCase: UpdateTradeCollectionUseCase

    private val deductions = slot<List<TradeCollectionLine>>()
    private val additions = slot<List<TradeCollectionLine>>()
    private var applyResult = TradeCollectionApplyResult()
    private val pendingCleanups = mutableListOf<TradeOfferCleanupEntity>()
    private var isApplied = false

    private val proposalId = "proposal-id-001"
    private val userId = "user-uuid-001"

    private fun buildItem(
        id: String = "item-id-001",
        cardId: String = "card-scryfall-001",
        userCardIdRef: String? = "uc-ref-001",
        quantity: Int? = 1,
        isFoil: Boolean? = false,
        condition: String? = "NM",
        language: String? = "en",
    ) = TradeItem(
        id = id,
        tradeProposalId = proposalId,
        fromUserId = "user-a",
        toUserId = "user-b",
        userCardIdRef = userCardIdRef,
        quantity = quantity,
        isFoil = isFoil,
        condition = condition,
        language = language,
        cardId = cardId,
        isReviewCollectionPlaceholder = false,
    )

    @Before
    fun setUp() {
        mockkStatic(FirebaseCrashlytics::class)
        every { FirebaseCrashlytics.getInstance() } returns crashlytics

        coEvery {
            userCardRepository.applyTradeCollectionChanges(any(), capture(deductions), capture(additions), any(), any(), any())
        } coAnswers {
            val shouldApply = arg<suspend () -> Boolean>(3)
            val onApplied = arg<suspend () -> Unit>(4)
            val onOfferRemovals = arg<suspend (List<String>) -> Unit>(5)
            if (!shouldApply()) null else {
                onOfferRemovals(applyResult.remoteOfferRemovals)
                onApplied()
                applyResult
            }
        }
        pendingCleanups.clear()
        isApplied = false
        coEvery { syncDao.isSynced(any(), any()) } answers { if (isApplied) 1 else 0 }
        coEvery { syncDao.markSynced(any()) } coAnswers { isApplied = true }
        coEvery { syncDao.enqueueOfferCleanups(any()) } coAnswers { pendingCleanups.addAll(firstArg()); Unit }
        coEvery { syncDao.getPendingOfferCleanups(any()) } coAnswers { pendingCleanups.toList() }
        coEvery { syncDao.clearOfferCleanup(any(), any(), any()) } coAnswers {
            pendingCleanups.removeAll { it.proposalId == firstArg<String>() && it.userId == secondArg<String>() && it.collectionId == thirdArg<String>() }
            Unit
        }
        coEvery { wishlistCleanup.drain(any()) } returns Result.success(Unit)
        coEvery { openForTradeRepository.removeByCollectionIdAndSync(any()) } returns Result.success(Unit)

        useCase = UpdateTradeCollectionUseCase(
            userCardRepository = userCardRepository,
            wishlistCleanup = wishlistCleanup,
            openForTradeRepository = openForTradeRepository,
            syncDao = syncDao,
            ioDispatcher = UnconfinedTestDispatcher(),
        )
    }

    @After
    fun tearDown() {
        unmockkStatic(FirebaseCrashlytics::class)
    }

    @Test
    fun `given a partial sent item then only the traded copies are deducted from its own row`() = runTest {
        useCase(proposalId, userId, listOf(buildItem(userCardIdRef = "uc-A", quantity = 1)), emptyList())

        assertEquals(
            listOf(TradeCollectionLine("card-scryfall-001", false, "NM", "en", 1, userCardIdRef = "uc-A")),
            deductions.captured,
        )
        coVerify(exactly = 0) { userCardRepository.deleteCard(any()) }
    }

    @Test
    fun `given a sent item with a null ref then it is still deducted by attributes`() = runTest {
        useCase(proposalId, userId, listOf(buildItem(userCardIdRef = null, isFoil = true, condition = "lp", language = "DE", quantity = 2)), emptyList())

        assertEquals(
            listOf(TradeCollectionLine("card-scryfall-001", true, "LP", "de", 2, userCardIdRef = null)),
            deductions.captured,
        )
    }

    @Test
    fun `given received items then they are added and never carry the other party's ref`() = runTest {
        useCase(proposalId, userId, emptyList(), listOf(buildItem(cardId = "card-r", userCardIdRef = "their-row", quantity = 3)))

        assertEquals(listOf(TradeCollectionLine("card-r", false, "NM", "en", 3, userCardIdRef = null)), additions.captured)
        coVerify(exactly = 1) { wishlistCleanup.stage(userId, listOf(TradeCollectionLine("card-r", false, "NM", "en", 3))) }
    }

    @Test
    fun `given normal mode then the completion marker is written inside the apply`() = runTest {
        useCase(proposalId, userId, emptyList(), emptyList())

        coVerify(exactly = 1) { syncDao.markSynced(match { it.proposalId == proposalId && it.userId == userId }) }
        coVerify(exactly = 0) { syncDao.removeSyncRecord(any(), any()) }
    }

    @Test
    fun `given wishlist remote write fails then retry does not repeat collection apply`() = runTest {
        coEvery { wishlistCleanup.drain(userId) } returns Result.failure(IllegalStateException("offline"))

        val first = useCase(proposalId, userId, emptyList(), listOf(buildItem()))
        assertTrue(first.isFailure)

        coEvery { wishlistCleanup.drain(userId) } returns Result.success(Unit)
        val second = useCase(proposalId, userId, emptyList(), listOf(buildItem()))

        assertTrue(second.isSuccess)
        coVerify(exactly = 1) { wishlistCleanup.stage(userId, any()) }
        coVerify(exactly = 1) { syncDao.markSynced(any()) }
    }

    @Test
    fun `given the trade was already applied then invoking again writes nothing and runs no follow-up`() = runTest {
        coEvery { syncDao.isSynced(proposalId, userId) } returns 1

        val result = useCase(proposalId, userId, listOf(buildItem()), listOf(buildItem(id = "r1")))

        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { syncDao.markSynced(any()) }
        coVerify(exactly = 0) { wishlistCleanup.stage(any(), any()) }
        coVerify(exactly = 0) { openForTradeRepository.removeByCollectionIdAndSync(any()) }
    }

    @Test
    fun `given two sequential applies then the collection changes run once`() = runTest {
        var applied = 0
        coEvery { syncDao.isSynced(proposalId, userId) } answers { applied }
        coEvery { syncDao.markSynced(any()) } answers { applied = 1 }

        useCase(proposalId, userId, listOf(buildItem()), listOf(buildItem(id = "r1", cardId = "card-r")))
        useCase(proposalId, userId, listOf(buildItem()), listOf(buildItem(id = "r1", cardId = "card-r")))

        coVerify(exactly = 1) { syncDao.markSynced(any()) }
        coVerify(exactly = 1) { wishlistCleanup.stage(userId, any()) }
    }

    @Test
    fun `given a synced offer was dropped then its remote removal runs after the commit`() = runTest {
        applyResult = TradeCollectionApplyResult(remoteOfferRemovals = listOf("uc-A"))

        useCase(proposalId, userId, listOf(buildItem(userCardIdRef = "uc-A")), emptyList())

        coVerify(exactly = 1) { openForTradeRepository.removeByCollectionIdAndSync("uc-A") }
    }

    @Test
    fun `given remote offer removal fails then the pending cleanup survives the apply gate and retries`() = runTest {
        applyResult = TradeCollectionApplyResult(remoteOfferRemovals = listOf("uc-A"))
        coEvery { openForTradeRepository.removeByCollectionIdAndSync(any()) } returns Result.failure(RuntimeException("private-account-id"))

        val result = useCase(proposalId, userId, listOf(buildItem(userCardIdRef = "uc-A")), emptyList())

        assertTrue(result.isFailure)
        assertEquals(1, pendingCleanups.size)
        val reported = mutableListOf<Throwable>()
        verify(atLeast = 1) { crashlytics.recordException(capture(reported)) }
        assertTrue(reported.all { it.message?.contains("private-account-id") != true && it.cause == null })

        coEvery { openForTradeRepository.removeByCollectionIdAndSync("uc-A") } returns Result.success(Unit)
        val retry = useCase(proposalId, userId, listOf(buildItem()), emptyList())

        assertTrue(retry.isSuccess)
        assertTrue(pendingCleanups.isEmpty())
        coVerify(exactly = 1) { syncDao.markSynced(any()) }
        coVerify(exactly = 2) { openForTradeRepository.removeByCollectionIdAndSync("uc-A") }
    }

    @Test
    fun `given reverse mode then received items are deducted and sent items restored without refs`() = runTest {
        coEvery { syncDao.isSynced(proposalId, userId) } returns 1

        useCase(
            proposalId, userId,
            sentItems = listOf(buildItem(id = "s1", cardId = "card-s", userCardIdRef = "uc-A", quantity = 2)),
            receivedItems = listOf(buildItem(id = "r1", cardId = "card-r", userCardIdRef = "their-row", quantity = 1)),
            reverse = true,
        )

        assertEquals(listOf(TradeCollectionLine("card-r", false, "NM", "en", 1, null)), deductions.captured)
        assertEquals(listOf(TradeCollectionLine("card-s", false, "NM", "en", 2, null)), additions.captured)
        coVerify(exactly = 1) { syncDao.removeSyncRecord(proposalId, userId) }
        coVerify(exactly = 0) { wishlistCleanup.stage(any(), any()) }
    }

    @Test
    fun `given reverse mode and nothing was applied then nothing is reversed`() = runTest {
        coEvery { syncDao.isSynced(proposalId, userId) } returns 0

        val result = useCase(proposalId, userId, listOf(buildItem()), emptyList(), reverse = true)

        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { syncDao.removeSyncRecord(any(), any()) }
    }

    @Test
    fun `given the local write fails then the result is failure and no follow-up runs`() = runTest {
        coEvery {
            userCardRepository.applyTradeCollectionChanges(any(), any(), any(), any(), any(), any())
        } throws IllegalStateException("db locked")

        val result = useCase(proposalId, userId, emptyList(), listOf(buildItem()))

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { wishlistCleanup.stage(any(), any()) }
    }

    @Test
    fun `given cancellation during the apply then it is rethrown and the marker is never written`() = runTest {
        coEvery {
            userCardRepository.applyTradeCollectionChanges(any(), any(), any(), any(), any(), any())
        } coAnswers {
            arg<suspend () -> Boolean>(3).invoke()
            throw CancellationException("screen closed")
        }

        try {
            useCase(proposalId, userId, listOf(buildItem()), emptyList())
            fail("CancellationException must propagate")
        } catch (e: CancellationException) {
            assertEquals("screen closed", e.message)
        }
        coVerify(exactly = 0) { syncDao.markSynced(any()) }
    }

    @Test
    fun `given review placeholders then they never reach the collection`() = runTest {
        val placeholder = buildItem().copy(isReviewCollectionPlaceholder = true)

        useCase(proposalId, userId, listOf(placeholder), listOf(placeholder))

        assertTrue(deductions.captured.isEmpty())
        assertTrue(additions.captured.isEmpty())
    }
}
