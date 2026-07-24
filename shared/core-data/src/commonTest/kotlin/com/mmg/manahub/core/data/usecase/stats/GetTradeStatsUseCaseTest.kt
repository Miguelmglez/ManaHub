package com.mmg.manahub.core.data.usecase.stats

import com.mmg.manahub.core.data.remote.dto.TradeItemRequestDto
import com.mmg.manahub.core.data.repository.TradesRepository
import com.mmg.manahub.core.domain.repository.FriendRepository
import com.mmg.manahub.core.model.AcceptInviteResult
import com.mmg.manahub.core.model.FolderFilters
import com.mmg.manahub.core.model.Friend
import com.mmg.manahub.core.model.FriendCard
import com.mmg.manahub.core.model.FriendMatchHistory
import com.mmg.manahub.core.model.FriendRequest
import com.mmg.manahub.core.model.FriendStats
import com.mmg.manahub.core.model.OutgoingFriendRequest
import com.mmg.manahub.core.model.PreferredCurrency
import com.mmg.manahub.core.model.ReviewFlags
import com.mmg.manahub.core.model.TradeItem
import com.mmg.manahub.core.model.TradeProposal
import com.mmg.manahub.core.model.TradeStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Minimal, hand-written [TradesRepository] fake (per memory
 * `feedback_commonTest_fake_repo_convention` -- MockK is unavailable in commonTest/wasmJs).
 *
 * [allProposals] is returned unchanged from every [observeAllProposals] call regardless of
 * [refreshProposals]/[refreshProposalThread] -- these tests exercise [GetTradeStatsUseCase]'s
 * orchestration (which threads it fans out to, how it aggregates the result), not
 * [TradesRepository]'s own live-refresh semantics.
 */
private class FakeTradesRepository(
    private val allProposals: List<TradeProposal>,
    private val refreshProposalsResult: Result<Unit> = Result.success(Unit),
    private val refreshThreadResult: (String) -> Result<Unit> = { Result.success(Unit) },
) : TradesRepository {
    var refreshProposalsCallCount = 0
        private set
    val refreshedThreadIds = mutableListOf<String>()

    override fun observeActiveProposals(): Flow<List<TradeProposal>> = error("unused")
    override fun observeProposalHistory(): Flow<List<TradeProposal>> = error("unused")
    override fun observeAllProposals(): Flow<List<TradeProposal>> = flowOf(allProposals)
    override fun observeProposalThread(rootProposalId: String): Flow<List<TradeProposal>> = error("unused")

    override suspend fun refreshProposals(userId: String): Result<Unit> {
        refreshProposalsCallCount++
        return refreshProposalsResult
    }

    override suspend fun refreshProposalThread(rootProposalId: String, userId: String): Result<Unit> {
        refreshedThreadIds.add(rootProposalId)
        return refreshThreadResult(rootProposalId)
    }

    override suspend fun createProposal(
        receiverId: String,
        items: List<TradeItemRequestDto>,
        includesReviewFromProposer: Boolean,
        includesReviewFromReceiver: Boolean,
        autoSend: Boolean,
    ): Result<String> = error("unused")

    override suspend fun editProposal(
        proposalId: String,
        expectedVersion: Int,
        newItems: List<TradeItemRequestDto>,
        newReviewFlags: ReviewFlags,
    ): Result<Unit> = error("unused")

    override suspend fun sendProposal(proposalId: String): Result<Unit> = error("unused")
    override suspend fun cancelProposal(proposalId: String): Result<Unit> = error("unused")
    override suspend fun declineProposal(proposalId: String): Result<Unit> = error("unused")

    override suspend fun counterProposal(
        parentProposalId: String,
        items: List<TradeItemRequestDto>,
        reviewFlags: ReviewFlags,
    ): Result<String> = error("unused")

    override suspend fun acceptProposal(proposalId: String): Result<Unit> = error("unused")
    override suspend fun revokeAcceptance(proposalId: String): Result<Unit> = error("unused")
    override suspend fun markCompleted(proposalId: String): Result<Unit> = error("unused")
    override fun clearCache() = error("unused")
}

/** Minimal, hand-written [FriendRepository] fake -- only [observeFriends] is exercised. */
private class FakeFriendRepository(private val friends: List<Friend> = emptyList()) : FriendRepository {
    override fun observeFriends(): Flow<List<Friend>> = flowOf(friends)
    override fun observePendingRequests(): Flow<List<FriendRequest>> = error("unused")
    override fun observeOutgoingRequests(): Flow<List<OutgoingFriendRequest>> = error("unused")
    override fun observePendingCount(): Flow<Int> = error("unused")
    override fun observeFriendCount(): Flow<Int> = error("unused")
    override suspend fun refreshFriends(currentUserId: String): Result<Unit> = error("unused")
    override suspend fun refreshRequests(currentUserId: String): Result<Unit> = error("unused")
    override suspend fun refreshOutgoingRequests(currentUserId: String): Result<Unit> = error("unused")
    override suspend fun sendFriendRequest(fromUserId: String, toUserId: String): Result<Unit> = error("unused")
    override suspend fun acceptRequest(friendshipId: String, currentUserId: String): Result<Unit> = error("unused")
    override suspend fun rejectRequest(friendshipId: String): Result<Unit> = error("unused")
    override suspend fun cancelOutgoingRequest(friendshipId: String): Result<Unit> = error("unused")
    override suspend fun removeFriend(friendshipId: String): Result<Unit> = error("unused")
    override suspend fun searchByGameTag(gameTag: String): Result<Friend?> = error("unused")
    override suspend fun acceptInvite(referralCode: String): Result<AcceptInviteResult> = error("unused")
    override suspend fun getMyShareUrl(userId: String): Result<String> = error("unused")
    override suspend fun getFriendCollection(
        friendUserId: String, list: String, query: String, filters: FolderFilters?, limit: Int, offset: Int,
    ): Result<List<FriendCard>> = error("unused")
    override suspend fun getFriendStats(friendUserId: String): Result<FriendStats?> = error("unused")
    override suspend fun getFriendMatchHistory(friendUserId: String): Result<FriendMatchHistory?> = error("unused")
    override suspend fun upsertMyStats(
        uniqueCards: Int, totalCards: Int, totalValueEur: Double, totalValueUsd: Double,
        favouriteColor: String?, mostValuableColor: String?,
    ): Result<Unit> = error("unused")
}

private fun tradeItem(
    id: String,
    tradeProposalId: String,
    fromUserId: String,
    toUserId: String,
    priceUsd: Double? = null,
    priceEur: Double? = null,
    quantity: Int? = 1,
    isReviewCollectionPlaceholder: Boolean = false,
) = TradeItem(
    id = id,
    tradeProposalId = tradeProposalId,
    fromUserId = fromUserId,
    toUserId = toUserId,
    userCardIdRef = null,
    quantity = quantity,
    isFoil = false,
    condition = "NM",
    language = "en",
    cardId = "card-$id",
    priceUsd = priceUsd,
    priceEur = priceEur,
    isReviewCollectionPlaceholder = isReviewCollectionPlaceholder,
)

private fun proposal(
    id: String,
    status: TradeStatus,
    proposerId: String,
    receiverId: String,
    items: List<TradeItem> = emptyList(),
) = TradeProposal(
    id = id,
    status = status,
    proposerId = proposerId,
    receiverId = receiverId,
    parentProposalId = null,
    rootProposalId = id,
    proposalVersion = 1,
    includesReviewCollectionFromProposer = false,
    includesReviewCollectionFromReceiver = false,
    proposerMarkedCompletedAt = null,
    receiverMarkedCompletedAt = null,
    cancellationReason = null,
    items = items,
    createdAt = 0L,
    updatedAt = 0L,
)

/**
 * Covers [GetTradeStatsUseCase]'s contract (Stats screen Phase 4, 2026-07 stats expansion):
 * completed-trades count, sent/received totals, net value delta sign + currency correctness,
 * review-collection-placeholder exclusion, top-partner selection (with friend-name fallback
 * chain), metadata-refresh short-circuit, per-thread degrade-not-fail, and the Phase 0 hard
 * constraint that NO cap is applied to how many completed threads are aggregated.
 */
class GetTradeStatsUseCaseTest {

    @Test
    fun `given no completed proposals when computing stats then returns zero counts without fanning out thread refreshes`() = runTest {
        val trades = FakeTradesRepository(allProposals = listOf(proposal("p1", TradeStatus.PROPOSED, "me", "them")))
        val useCase = GetTradeStatsUseCase(trades, FakeFriendRepository())

        val result = useCase("me", PreferredCurrency.USD)

        assertTrue(result.isSuccess)
        val stats = result.getOrThrow()
        assertEquals(0, stats.completedTradesCount)
        assertEquals(0, stats.cardsSent)
        assertEquals(0, stats.cardsReceived)
        assertEquals(0.0, stats.netValueDelta, 0.0001)
        assertNull(stats.topPartner)
        assertTrue(trades.refreshedThreadIds.isEmpty())
    }

    @Test
    fun `given multiple completed proposals when computing stats then completedTradesCount matches`() = runTest {
        val proposals = listOf(
            proposal("p1", TradeStatus.COMPLETED, "me", "friend-a"),
            proposal("p2", TradeStatus.COMPLETED, "friend-b", "me"),
            proposal("p3", TradeStatus.CANCELLED, "me", "friend-a"),
        )
        val useCase = GetTradeStatsUseCase(FakeTradesRepository(proposals), FakeFriendRepository())

        val stats = useCase("me", PreferredCurrency.USD).getOrThrow()

        assertEquals(2, stats.completedTradesCount)
    }

    @Test
    fun `given completed trades with priced items when computing stats in USD then sent received and net delta are correct`() = runTest {
        val items = listOf(
            tradeItem("i1", "p1", fromUserId = "me", toUserId = "friend-a", priceUsd = 10.0, priceEur = 9.0, quantity = 2), // sent 2 x 10 = 20
            tradeItem("i2", "p1", fromUserId = "friend-a", toUserId = "me", priceUsd = 5.0, priceEur = 4.5, quantity = 3),  // received 3 x 5 = 15
        )
        val proposals = listOf(proposal("p1", TradeStatus.COMPLETED, "me", "friend-a", items))
        val useCase = GetTradeStatsUseCase(FakeTradesRepository(proposals), FakeFriendRepository())

        val stats = useCase("me", PreferredCurrency.USD).getOrThrow()

        assertEquals(2, stats.cardsSent)
        assertEquals(3, stats.cardsReceived)
        assertEquals(-5.0, stats.netValueDelta, 0.0001) // received 15 - sent 20
        assertEquals(PreferredCurrency.USD, stats.currency)
    }

    @Test
    fun `given the same completed trade when currency is EUR then the value delta uses EUR prices not USD`() = runTest {
        val items = listOf(
            tradeItem("i1", "p1", fromUserId = "me", toUserId = "friend-a", priceUsd = 10.0, priceEur = 9.0, quantity = 1),
            tradeItem("i2", "p1", fromUserId = "friend-a", toUserId = "me", priceUsd = 100.0, priceEur = 2.0, quantity = 1),
        )
        val proposals = listOf(proposal("p1", TradeStatus.COMPLETED, "me", "friend-a", items))
        val useCase = GetTradeStatsUseCase(FakeTradesRepository(proposals), FakeFriendRepository())

        val stats = useCase("me", PreferredCurrency.EUR).getOrThrow()

        // received 2.0 EUR - sent 9.0 EUR = -7.0 (would be wildly different if it used USD prices).
        assertEquals(-7.0, stats.netValueDelta, 0.0001)
    }

    @Test
    fun `given a review-collection placeholder item when computing stats then it is excluded from counts and value`() = runTest {
        val items = listOf(
            tradeItem("i1", "p1", fromUserId = "me", toUserId = "friend-a", priceUsd = 999.0, quantity = 1, isReviewCollectionPlaceholder = true),
            tradeItem("i2", "p1", fromUserId = "me", toUserId = "friend-a", priceUsd = 5.0, quantity = 1, isReviewCollectionPlaceholder = false),
        )
        val proposals = listOf(proposal("p1", TradeStatus.COMPLETED, "me", "friend-a", items))
        val useCase = GetTradeStatsUseCase(FakeTradesRepository(proposals), FakeFriendRepository())

        val stats = useCase("me", PreferredCurrency.USD).getOrThrow()

        assertEquals(1, stats.cardsSent)
        assertEquals(-5.0, stats.netValueDelta, 0.0001)
    }

    @Test
    fun `given completed trades with multiple partners when computing stats then topPartner is the highest volume counterparty`() = runTest {
        val proposals = listOf(
            proposal("p1", TradeStatus.COMPLETED, "me", "friend-a"),
            proposal("p2", TradeStatus.COMPLETED, "friend-a", "me"),
            proposal("p3", TradeStatus.COMPLETED, "me", "friend-b"),
        )
        val friends = listOf(
            Friend(id = "f1", userId = "friend-a", nickname = "Ana", gameTag = "#ANA1", avatarUrl = null),
            Friend(id = "f2", userId = "friend-b", nickname = "", gameTag = "#BEN1", avatarUrl = null),
        )
        val useCase = GetTradeStatsUseCase(FakeTradesRepository(proposals), FakeFriendRepository(friends))

        val stats = useCase("me", PreferredCurrency.USD).getOrThrow()

        assertEquals("friend-a", stats.topPartner?.userId)
        assertEquals("Ana", stats.topPartner?.displayName)
        assertEquals(2, stats.topPartner?.completedTradesCount)
    }

    @Test
    fun `given a partner with no nickname when resolving topPartner then it falls back to the game tag`() = runTest {
        val proposals = listOf(proposal("p1", TradeStatus.COMPLETED, "me", "friend-b"))
        val friends = listOf(Friend(id = "f2", userId = "friend-b", nickname = "", gameTag = "#BEN1", avatarUrl = null))
        val useCase = GetTradeStatsUseCase(FakeTradesRepository(proposals), FakeFriendRepository(friends))

        val stats = useCase("me", PreferredCurrency.USD).getOrThrow()

        assertEquals("#BEN1", stats.topPartner?.displayName)
    }

    @Test
    fun `given an unknown partner when resolving topPartner then displayName falls back to Unknown`() = runTest {
        val proposals = listOf(proposal("p1", TradeStatus.COMPLETED, "me", "stranger"))
        val useCase = GetTradeStatsUseCase(FakeTradesRepository(proposals), FakeFriendRepository(emptyList()))

        val stats = useCase("me", PreferredCurrency.USD).getOrThrow()

        assertEquals("Unknown", stats.topPartner?.displayName)
    }

    @Test
    fun `given the friend lookup throws when resolving topPartner then displayName still falls back to Unknown`() = runTest {
        val proposals = listOf(proposal("p1", TradeStatus.COMPLETED, "me", "friend-a"))
        val throwingFriends = object : FriendRepository by FakeFriendRepository() {
            override fun observeFriends(): Flow<List<Friend>> = flow { throw RuntimeException("boom") }
        }
        val useCase = GetTradeStatsUseCase(FakeTradesRepository(proposals), throwingFriends)

        val result = useCase("me", PreferredCurrency.USD)

        assertTrue(result.isSuccess)
        assertEquals("Unknown", result.getOrThrow().topPartner?.displayName)
    }

    @Test
    fun `given the metadata refresh fails when computing stats then it returns failure without reading proposals or fanning out`() = runTest {
        val trades = FakeTradesRepository(
            allProposals = listOf(proposal("p1", TradeStatus.COMPLETED, "me", "friend-a")),
            refreshProposalsResult = Result.failure(RuntimeException("network down")),
        )
        val useCase = GetTradeStatsUseCase(trades, FakeFriendRepository())

        val result = useCase("me", PreferredCurrency.USD)

        assertTrue(result.isFailure)
        assertEquals("network down", result.exceptionOrNull()?.message)
        assertTrue(trades.refreshedThreadIds.isEmpty())
    }

    @Test
    fun `given one thread refresh fails when computing stats then the aggregate still succeeds`() = runTest {
        val proposals = listOf(
            proposal("p1", TradeStatus.COMPLETED, "me", "friend-a"),
            proposal("p2", TradeStatus.COMPLETED, "me", "friend-b"),
        )
        val trades = FakeTradesRepository(
            allProposals = proposals,
            refreshThreadResult = { rootId ->
                if (rootId == "p1") Result.failure(RuntimeException("thread failed")) else Result.success(Unit)
            },
        )
        val useCase = GetTradeStatsUseCase(trades, FakeFriendRepository())

        val result = useCase("me", PreferredCurrency.USD)

        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrThrow().completedTradesCount)
    }

    @Test
    fun `given many completed trades when computing stats then every one is counted with no artificial cap`() = runTest {
        // Regression guard for the Phase 0 feasibility hard constraint: unlike Home's bounded
        // dashboard warm-up, the stats aggregate must never silently truncate to a "newest N".
        val proposals = (1..37).map { i -> proposal("p$i", TradeStatus.COMPLETED, "me", "friend-$i") }
        val trades = FakeTradesRepository(proposals)
        val useCase = GetTradeStatsUseCase(trades, FakeFriendRepository())

        val stats = useCase("me", PreferredCurrency.USD).getOrThrow()

        assertEquals(37, stats.completedTradesCount)
        assertEquals(37, trades.refreshedThreadIds.distinct().size)
    }
}
