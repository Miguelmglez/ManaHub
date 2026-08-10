package com.mmg.manahub.feature.trades.presentation

import androidx.lifecycle.SavedStateHandle
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.core.domain.auth.AuthRepository
import com.mmg.manahub.core.domain.auth.AuthUser
import com.mmg.manahub.core.domain.auth.SessionState
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.domain.repository.FriendRepository
import com.mmg.manahub.core.domain.repository.OpenForTradeRepository
import com.mmg.manahub.core.domain.repository.UserCardRepository
import com.mmg.manahub.core.domain.repository.WishlistRepository
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.OpenForTradeEntry
import com.mmg.manahub.core.model.TradeItem
import com.mmg.manahub.core.model.TradeProposal
import com.mmg.manahub.core.model.TradeStatus
import com.mmg.manahub.core.model.UserCardWithCard
import com.mmg.manahub.core.model.WishlistEntry
import com.mmg.manahub.core.data.repository.TradesRepository
import com.mmg.manahub.core.util.AnalyticsHelper
import com.mmg.manahub.feature.trades.domain.usecase.CounterProposalUseCase
import com.mmg.manahub.feature.trades.domain.usecase.CreateTradeProposalUseCase
import com.mmg.manahub.feature.trades.domain.usecase.EditProposalUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [TradeProposalViewModel.loadPrefillProposal]'s card-resolution path (Backend &
 * Performance Optimization plan, WS4a finding 5 — 2026-07-28). Sole owning suite for this batch
 * N+1 fix: [loadPrefillProposal] used to call `cardRepository.getCardById(id)` sequentially per
 * card (network fallback on every miss) — the exact shape already fixed in
 * `FriendRepositoryImpl.getFriendCollection`. It now does ONE `warmCacheForIds` + ONE
 * `getCardsByIds` (Room-only) instead.
 *
 * The send/save/validation and golden-matches paths of this ViewModel are covered separately by
 * [TradeProposalViewModelSendTest] / [TradeProposalViewModelMatchesTest] — this file exists only
 * to avoid duplicating THEIR full fixture surface for a single new behavior.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TradeProposalViewModelPrefillTest {

    private val testDispatcher = StandardTestDispatcher()

    private val authRepository = mockk<AuthRepository>()
    private val tradesRepository = mockk<TradesRepository>(relaxed = true)
    private val createProposal = mockk<CreateTradeProposalUseCase>()
    private val editProposal = mockk<EditProposalUseCase>()
    private val counterProposal = mockk<CounterProposalUseCase>()
    private val cardRepository = mockk<CardRepository>()
    private val userCardRepository = mockk<UserCardRepository>(relaxed = true)
    private val wishlistRepository = mockk<WishlistRepository>(relaxed = true)
    private val openForTradeRepository = mockk<OpenForTradeRepository>(relaxed = true)
    private val friendRepository = mockk<FriendRepository>(relaxed = true)
    private val analyticsHelper = mockk<AnalyticsHelper>(relaxed = true)

    private val sessionFlow = MutableStateFlow<SessionState>(SessionState.Loading)
    private val collectionFlow = MutableStateFlow<List<UserCardWithCard>>(emptyList())
    private val wishlistFlow = MutableStateFlow<List<WishlistEntry>>(emptyList())
    private val offerFlow = MutableStateFlow<List<OpenForTradeEntry>>(emptyList())

    private companion object {
        const val MY_USER_ID = "user-me-001"
        const val FRIEND_USER_ID = "user-friend-002"
        const val ROOT_ID = "root-id-001"
        const val EDITING_ID = "editing-id-001"
    }

    private fun authenticated(userId: String) = SessionState.Authenticated(
        AuthUser(id = userId, email = "$userId@test.com", nickname = "Me", gameTag = "#ME001", avatarUrl = null, provider = "email")
    )

    private fun minimalCard(id: String, name: String) = Card(
        scryfallId = id, name = name, printedName = null, manaCost = null, cmc = 1.0,
        colors = emptyList(), colorIdentity = emptyList(), typeLine = "Instant", printedTypeLine = null,
        oracleText = null, printedText = null, keywords = emptyList(), power = null, toughness = null,
        loyalty = null, setCode = "tst", setName = "Test Set", collectorNumber = "1", rarity = "common",
        releasedAt = "2020-01-01", frameEffects = emptyList(), promoTypes = emptyList(), lang = "en",
        imageNormal = "https://example.com/$id-normal.jpg", imageArtCrop = "https://example.com/$id-art.jpg",
        imageBackNormal = null, priceUsd = null, priceUsdFoil = null, priceEur = null, priceEurFoil = null,
        legalityStandard = "legal", legalityPioneer = "legal", legalityModern = "legal", legalityCommander = "legal",
        flavorText = null, artist = null, scryfallUri = "https://scryfall.com/card/$id",
    )

    private fun tradeItem(id: String, cardId: String, fromUserId: String, toUserId: String) = TradeItem(
        id = id, tradeProposalId = EDITING_ID, fromUserId = fromUserId, toUserId = toUserId,
        userCardIdRef = "uc-$id", quantity = 1, isFoil = false, condition = "NM", language = "en",
        cardId = cardId, cardName = "Card $cardId", isReviewCollectionPlaceholder = false,
    )

    private fun proposal(items: List<TradeItem>) = TradeProposal(
        id = EDITING_ID, status = TradeStatus.PROPOSED, proposerId = MY_USER_ID, receiverId = FRIEND_USER_ID,
        parentProposalId = null, rootProposalId = ROOT_ID, proposalVersion = 1,
        includesReviewCollectionFromProposer = false, includesReviewCollectionFromReceiver = false,
        proposerMarkedCompletedAt = null, receiverMarkedCompletedAt = null, cancellationReason = null,
        items = items, createdAt = 0L, updatedAt = 0L,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic(FirebaseCrashlytics::class)
        val crashlytics = mockk<FirebaseCrashlytics>(relaxed = true)
        every { FirebaseCrashlytics.getInstance() } returns crashlytics

        every { authRepository.sessionState } returns sessionFlow
        every { userCardRepository.observeCollection() } returns collectionFlow
        every { wishlistRepository.observeLocal() } returns wishlistFlow
        every { openForTradeRepository.observeLocal() } returns offerFlow
        every { friendRepository.observeFriends() } returns MutableStateFlow(emptyList())
        sessionFlow.value = authenticated(MY_USER_ID)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(FirebaseCrashlytics::class)
    }

    private fun createViewModel(): TradeProposalViewModel = TradeProposalViewModel(
        savedStateHandle = SavedStateHandle(
            mapOf(
                "receiverId" to FRIEND_USER_ID,
                "editingProposalId" to EDITING_ID,
                "rootProposalId" to ROOT_ID,
            ),
        ),
        authRepository = authRepository,
        tradesRepository = tradesRepository,
        createProposal = createProposal,
        editProposal = editProposal,
        counterProposal = counterProposal,
        cardRepository = cardRepository,
        userCardRepository = userCardRepository,
        wishlistRepository = wishlistRepository,
        openForTradeRepository = openForTradeRepository,
        friendRepository = friendRepository,
        analyticsHelper = analyticsHelper,
        ioDispatcher = testDispatcher,
        defaultDispatcher = testDispatcher,
    )

    @Test
    fun `given a prefill proposal with 2 distinct card ids when loadPrefillProposal runs then warmCacheForIds and getCardsByIds are each called once and getCardById is never called`() =
        runTest {
            val items = listOf(
                tradeItem(id = "item-1", cardId = "card-a", fromUserId = MY_USER_ID, toUserId = FRIEND_USER_ID),
                tradeItem(id = "item-2", cardId = "card-b", fromUserId = FRIEND_USER_ID, toUserId = MY_USER_ID),
            )
            every { tradesRepository.observeProposalThread(ROOT_ID) } returns flowOf(listOf(proposal(items)))
            coEvery { cardRepository.warmCacheForIds(any()) } returns Unit
            coEvery { cardRepository.getCardsByIds(any()) } returns
                listOf(minimalCard("card-a", "Card A"), minimalCard("card-b", "Card B"))

            val vm = createViewModel()
            advanceUntilIdle()

            coVerify(exactly = 1) {
                cardRepository.warmCacheForIds(match { it.toSet() == setOf("card-a", "card-b") })
            }
            coVerify(exactly = 1) { cardRepository.getCardsByIds(match { it.toSet() == setOf("card-a", "card-b") }) }
            coVerify(exactly = 0) { cardRepository.getCardById(any()) }

            val state = vm.uiState.value
            assertEquals(1, state.proposerItems.size)
            assertEquals(1, state.receiverItems.size)
            assertEquals("https://example.com/card-a-art.jpg", state.proposerItems.first().imageUrl)
        }

    @Test
    fun `given a card id Room cannot resolve when loadPrefillProposal runs then the draft renders with null image and type fields instead of crashing`() =
        runTest {
            val items = listOf(
                tradeItem(id = "item-1", cardId = "unresolvable-card", fromUserId = MY_USER_ID, toUserId = FRIEND_USER_ID),
            )
            every { tradesRepository.observeProposalThread(ROOT_ID) } returns flowOf(listOf(proposal(items)))
            coEvery { cardRepository.warmCacheForIds(any()) } returns Unit
            // Room can't resolve it even after the warm -- getCardsByIds simply omits it.
            coEvery { cardRepository.getCardsByIds(any()) } returns emptyList()

            val vm = createViewModel()
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals(1, state.proposerItems.size)
            val draft = state.proposerItems.first()
            assertEquals("unresolvable-card", draft.cardId)
            assertNull(draft.imageUrl)
            assertNull(draft.typeLine)
            coVerify(exactly = 0) { cardRepository.getCardById(any()) }
        }

    @Test
    fun `given no card items at all when loadPrefillProposal runs then warmCacheForIds is never called`() =
        runTest {
            val items = listOf(
                tradeItem(id = "item-1", cardId = "card-a", fromUserId = MY_USER_ID, toUserId = FRIEND_USER_ID)
                    .copy(isReviewCollectionPlaceholder = true),
            )
            every { tradesRepository.observeProposalThread(ROOT_ID) } returns flowOf(listOf(proposal(items)))
            coEvery { cardRepository.getCardsByIds(any()) } returns emptyList()

            val vm = createViewModel()
            advanceUntilIdle()

            // warmCacheForIds is explicitly guarded on a non-empty id list -- it must never fire
            // for an empty batch. getCardsByIds itself is still called (with an empty list), which
            // is harmless (an empty Room IN-clause), so it is not asserted against here.
            coVerify(exactly = 0) { cardRepository.warmCacheForIds(any()) }
            assertEquals(0, vm.uiState.value.proposerItems.size)
        }
}
