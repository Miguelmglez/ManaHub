package com.mmg.manahub.feature.trades.presentation

import androidx.lifecycle.SavedStateHandle
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.UserCard
import com.mmg.manahub.core.model.UserCardWithCard
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.domain.repository.UserCardRepository
import com.mmg.manahub.core.util.AnalyticsHelper
import com.mmg.manahub.core.domain.auth.AuthUser
import com.mmg.manahub.core.domain.auth.SessionState
import com.mmg.manahub.core.domain.auth.AuthRepository
import com.mmg.manahub.feature.friends.domain.model.Friend
import com.mmg.manahub.feature.friends.domain.model.FriendCard
import com.mmg.manahub.feature.friends.domain.repository.FriendRepository
import com.mmg.manahub.core.domain.model.OpenForTradeEntry
import com.mmg.manahub.feature.trades.domain.model.TradeSide
import com.mmg.manahub.core.domain.model.WishlistEntry
import com.mmg.manahub.feature.trades.domain.repository.OpenForTradeRepository
import com.mmg.manahub.feature.trades.domain.repository.TradesRepository
import com.mmg.manahub.feature.trades.domain.repository.WishlistRepository
import com.mmg.manahub.feature.trades.domain.usecase.CounterProposalUseCase
import com.mmg.manahub.feature.trades.domain.usecase.CreateTradeProposalUseCase
import com.mmg.manahub.feature.trades.domain.usecase.EditProposalUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for the golden-matches logic in [TradeProposalViewModel].
 *
 * Covers:
 *  - GROUP 1: proposerMatches — intersection of my offers ∩ friend's wishlist
 *  - GROUP 2: receiverMatches — intersection of friend's offers ∩ my wishlist
 *  - GROUP 3: updateSearchLists side-aware mapping (PROPOSER side)
 *  - GROUP 4: updateSearchLists side-aware mapping (RECEIVER side)
 *  - GROUP 5: onFriendSelected — friend selection and deselection
 *  - GROUP 6: onFriendSelected — stale job cancellation when friend changes quickly
 *  - GROUP 7: onFriendSelected null — clears friend data and resets matches
 *  - GROUP 8: Edge cases — empty lists, foil-agnostic scryfallId matching
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TradeProposalViewModelMatchesTest {

    // ── Test dispatcher ───────────────────────────────────────────────────────

    private val testDispatcher = StandardTestDispatcher()

    // ── Mocks ─────────────────────────────────────────────────────────────────

    private val authRepository = mockk<AuthRepository>()
    private val tradesRepository = mockk<TradesRepository>(relaxed = true)
    private val createProposal = mockk<CreateTradeProposalUseCase>(relaxed = true)
    private val editProposal = mockk<EditProposalUseCase>(relaxed = true)
    private val counterProposal = mockk<CounterProposalUseCase>(relaxed = true)
    private val cardRepository = mockk<CardRepository>(relaxed = true)
    private val userCardRepository = mockk<UserCardRepository>(relaxed = true)
    private val wishlistRepository = mockk<WishlistRepository>()
    private val openForTradeRepository = mockk<OpenForTradeRepository>()
    private val friendRepository = mockk<FriendRepository>()
    private val analyticsHelper = mockk<AnalyticsHelper>(relaxed = true)

    // ── Shared state flows (mutated between tests) ────────────────────────────

    private val sessionFlow = MutableStateFlow<SessionState>(
        SessionState.Authenticated(
            AuthUser(
                id = MY_USER_ID,
                email = "me@test.com",
                nickname = "Me",
                gameTag = "#ME001",
                avatarUrl = null,
                provider = "email",
            )
        )
    )
    private val collectionFlow = MutableStateFlow<List<UserCardWithCard>>(emptyList())
    private val wishlistFlow = MutableStateFlow<List<WishlistEntry>>(emptyList())
    private val offerFlow = MutableStateFlow<List<OpenForTradeEntry>>(emptyList())
    private val friendsFlow = MutableStateFlow<List<Friend>>(emptyList())

    // ── Constants ─────────────────────────────────────────────────────────────

    companion object {
        const val MY_USER_ID = "user-me-001"
        const val FRIEND_USER_ID = "user-friend-002"

        const val CARD_ID_LIGHTNING_BOLT = "scryfall-uuid-lightning-bolt"
        const val CARD_ID_COUNTERSPELL = "scryfall-uuid-counterspell"
        const val CARD_ID_DARK_RITUAL = "scryfall-uuid-dark-ritual"
        const val CARD_ID_BIRDS_OF_PARADISE = "scryfall-uuid-birds-of-paradise"
    }

    // ── Fixture builders ──────────────────────────────────────────────────────

    private fun buildCard(
        scryfallId: String,
        name: String = "Card $scryfallId",
    ) = Card(
        scryfallId = scryfallId,
        name = name,
        printedName = null,
        manaCost = null,
        cmc = 0.0,
        colors = emptyList(),
        colorIdentity = emptyList(),
        typeLine = "Instant",
        printedTypeLine = null,
        oracleText = null,
        printedText = null,
        keywords = emptyList(),
        power = null,
        toughness = null,
        loyalty = null,
        setCode = "A25",
        setName = "Masters 25",
        collectorNumber = "1",
        rarity = "common",
        releasedAt = "2018-03-16",
        frameEffects = emptyList(),
        promoTypes = emptyList(),
        lang = "en",
        imageNormal = null,
        imageArtCrop = null,
        imageBackNormal = null,
        priceUsd = 1.0,
        priceUsdFoil = 2.0,
        priceEur = 0.9,
        priceEurFoil = 1.8,
        legalityStandard = "not_legal",
        legalityPioneer = "not_legal",
        legalityModern = "legal",
        legalityCommander = "legal",
        flavorText = null,
        artist = null,
        scryfallUri = "",
    )

    private fun buildUserCardWithCard(
        scryfallId: String,
        name: String = "Card $scryfallId",
    ) = UserCardWithCard(
        userCard = UserCard(
            id = "uc-$scryfallId",
            scryfallId = scryfallId,
            quantity = 1,
            isFoil = false,
            condition = "NM",
            language = "en",
        ),
        card = buildCard(scryfallId, name),
    )

    private fun buildOfferEntry(
        scryfallId: String,
        card: Card? = buildCard(scryfallId),
        isFoil: Boolean = false,
        condition: String = "NM",
        language: String = "en",
        quantity: Int = 1,
    ) = OpenForTradeEntry(
        id = "offer-${scryfallId}_${isFoil}_${condition}_${language}",
        userId = MY_USER_ID,
        userCardId = "uc-$scryfallId",
        scryfallId = scryfallId,
        quantity = quantity,
        isFoil = isFoil,
        condition = condition,
        language = language,
        createdAt = 0L,
        card = card,
    )

    private fun buildWishlistEntry(
        scryfallId: String,
        card: Card? = buildCard(scryfallId),
        isFoil: Boolean = false,
        condition: String? = "NM",
        language: String? = "en",
    ) = WishlistEntry(
        id = "wish-$scryfallId",
        userId = MY_USER_ID,
        cardId = scryfallId,
        quantity = 1,
        matchAnyVariant = false,
        isFoil = isFoil,
        condition = condition,
        language = language,
        createdAt = 0L,
        card = card,
    )

    private fun buildFriendCard(
        scryfallId: String,
        name: String = "Card $scryfallId",
        sourceList: String = "wishlist",
        isFoil: Boolean = false,
        condition: String? = "NM",
        language: String? = "en",
        quantity: Int = 1,
    ) = FriendCard(
        sourceList = sourceList,
        scryfallId = scryfallId,
        name = name,
        typeLine = "Instant",
        imageNormal = null,
        imageArtCrop = null,
        setCode = "A25",
        setName = "Masters 25",
        rarity = "common",
        priceEur = 0.9,
        priceUsd = 1.0,
        priceEurFoil = 1.8,
        priceUsdFoil = 2.0,
        quantity = quantity,
        isFoil = isFoil,
        isStale = false,
        condition = condition,
        language = language,
    )

    private fun buildFriend(
        userId: String = FRIEND_USER_ID,
        nickname: String = "Friend",
    ) = Friend(
        id = "friendship-001",
        userId = userId,
        nickname = nickname,
        gameTag = "#FR001",
        avatarUrl = null,
    )

    // ── Setup and teardown ────────────────────────────────────────────────────

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        // Prevent FirebaseCrashlytics.getInstance() from crashing in JVM tests.
        // The ViewModel calls it in onFailure handlers inside fetchFriendData.
        mockkStatic(FirebaseCrashlytics::class)
        val crashlytics = mockk<FirebaseCrashlytics>(relaxed = true)
        every { FirebaseCrashlytics.getInstance() } returns crashlytics

        every { authRepository.sessionState } returns sessionFlow
        every { userCardRepository.observeCollection() } returns collectionFlow
        every { wishlistRepository.observeLocal() } returns wishlistFlow
        every { openForTradeRepository.observeLocal() } returns offerFlow
        every { friendRepository.observeFriends() } returns friendsFlow

        // The ViewModel's fetchFriendData calls getFriendCollection with three different list
        // values: "wishlist", "trade", and "collection". Provide a default stub for "collection"
        // so tests that only care about wishlist/offer matches don't need to stub it explicitly.
        coEvery {
            friendRepository.getFriendCollection(any(), eq("collection"), any(), any(), any())
        } returns Result.success(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(FirebaseCrashlytics::class)
    }

    // ── Helper to create ViewModel with a clean SavedStateHandle ─────────────

    private fun createViewModel(
        receiverId: String = "",
    ): TradeProposalViewModel {
        val handle = SavedStateHandle(
            if (receiverId.isNotBlank()) mapOf("receiverId" to receiverId) else emptyMap()
        )
        return TradeProposalViewModel(
            savedStateHandle = handle,
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
        )
    }

    // ── Helper to set up friend data on friendRepository mock ────────────────

    private fun stubFriendWishlist(
        friendUserId: String,
        cards: List<FriendCard>,
    ) {
        coEvery {
            friendRepository.getFriendCollection(friendUserId, "wishlist", "", any(), any())
        } returns Result.success(cards)
    }

    private fun stubFriendOffers(
        friendUserId: String,
        cards: List<FriendCard>,
    ) {
        coEvery {
            friendRepository.getFriendCollection(friendUserId, "trade", "", any(), any())
        } returns Result.success(cards)
    }

    /**
     * Stubs the "collection" list fetch (used by the ViewModel to populate the friend's
     * public collection for the "You get" search tab). Defaults to empty list.
     */
    private fun stubFriendPublicCollection(
        friendUserId: String,
        cards: List<FriendCard> = emptyList(),
    ) {
        coEvery {
            friendRepository.getFriendCollection(friendUserId, "collection", "", any(), any())
        } returns Result.success(cards)
    }

    private fun stubFriendCollectionFailing(friendUserId: String) {
        coEvery {
            friendRepository.getFriendCollection(friendUserId, any(), any(), any(), any())
        } returns Result.failure(RuntimeException("Network error"))
    }

    // =========================================================================
    // GROUP 1: proposerMatches — my offers ∩ friend's wishlist
    // =========================================================================

    @Test
    fun `given no friend selected when observing matches then proposerMatches is empty`() = runTest {
        // Arrange
        offerFlow.value = listOf(buildOfferEntry(CARD_ID_LIGHTNING_BOLT))
        val vm = createViewModel()
        advanceUntilIdle()

        // Act + Assert: no friend selected → matches must be empty
        val state = vm.uiState.value
        assertTrue(
            "proposerMatches must be empty when no friend is selected",
            state.proposerMatches.isEmpty()
        )
    }

    @Test
    fun `given friend selected with wishlist matching my offer then proposerMatches contains that card`() = runTest {
        // Arrange
        val myOffer = buildOfferEntry(CARD_ID_LIGHTNING_BOLT)
        offerFlow.value = listOf(myOffer)
        val friend = buildFriend()
        val friendWish = buildFriendCard(CARD_ID_LIGHTNING_BOLT, sourceList = "wishlist")
        stubFriendWishlist(FRIEND_USER_ID, listOf(friendWish))
        stubFriendOffers(FRIEND_USER_ID, emptyList())

        val vm = createViewModel()
        advanceUntilIdle()

        // Act
        vm.onFriendSelected(friend)
        advanceUntilIdle()

        // Assert
        val matches = vm.uiState.value.proposerMatches
        assertEquals("Expected 1 proposer match", 1, matches.size)
        assertEquals(
            "Match scryfallId should be LIGHTNING_BOLT",
            CARD_ID_LIGHTNING_BOLT,
            matches.first().card.scryfallId
        )
    }

    @Test
    fun `given friend's wishlist does not overlap my offers then proposerMatches is empty`() = runTest {
        // Arrange — I offer LIGHTNING_BOLT, friend wants COUNTERSPELL
        offerFlow.value = listOf(buildOfferEntry(CARD_ID_LIGHTNING_BOLT))
        val friend = buildFriend()
        stubFriendWishlist(FRIEND_USER_ID, listOf(buildFriendCard(CARD_ID_COUNTERSPELL, sourceList = "wishlist")))
        stubFriendOffers(FRIEND_USER_ID, emptyList())

        val vm = createViewModel()
        advanceUntilIdle()
        vm.onFriendSelected(friend)
        advanceUntilIdle()

        // Assert
        assertTrue(
            "proposerMatches should be empty when no scryfallId overlap",
            vm.uiState.value.proposerMatches.isEmpty()
        )
    }

    @Test
    fun `given multiple offer entries only matching ones appear in proposerMatches`() = runTest {
        // Arrange — I offer LIGHTNING_BOLT + DARK_RITUAL; friend wants LIGHTNING_BOLT + COUNTERSPELL
        offerFlow.value = listOf(
            buildOfferEntry(CARD_ID_LIGHTNING_BOLT),
            buildOfferEntry(CARD_ID_DARK_RITUAL),
        )
        val friend = buildFriend()
        stubFriendWishlist(
            FRIEND_USER_ID,
            listOf(
                buildFriendCard(CARD_ID_LIGHTNING_BOLT, sourceList = "wishlist"),
                buildFriendCard(CARD_ID_COUNTERSPELL, sourceList = "wishlist"),
            )
        )
        stubFriendOffers(FRIEND_USER_ID, emptyList())

        val vm = createViewModel()
        advanceUntilIdle()
        vm.onFriendSelected(friend)
        advanceUntilIdle()

        // Assert — only LIGHTNING_BOLT matches (DARK_RITUAL not wanted, COUNTERSPELL not offered)
        val matchIds = vm.uiState.value.proposerMatches.map { it.card.scryfallId }
        assertEquals(1, matchIds.size)
        assertTrue(matchIds.contains(CARD_ID_LIGHTNING_BOLT))
    }

    @Test
    fun `given foil and non-foil offer of same card then proposerMatches still includes it (scryfallId-only check)`() = runTest {
        // Arrange — I have a non-foil offer; friend wants a FOIL copy.
        // The match logic uses scryfallId only, ignoring foil flag.
        offerFlow.value = listOf(buildOfferEntry(CARD_ID_LIGHTNING_BOLT, isFoil = false))
        val friend = buildFriend()
        stubFriendWishlist(
            FRIEND_USER_ID,
            listOf(buildFriendCard(CARD_ID_LIGHTNING_BOLT, sourceList = "wishlist", isFoil = true))
        )
        stubFriendOffers(FRIEND_USER_ID, emptyList())

        val vm = createViewModel()
        advanceUntilIdle()
        vm.onFriendSelected(friend)
        advanceUntilIdle()

        // Assert — scryfallId match regardless of foil flag
        assertEquals(1, vm.uiState.value.proposerMatches.size)
        assertEquals(
            CARD_ID_LIGHTNING_BOLT,
            vm.uiState.value.proposerMatches.first().card.scryfallId
        )
    }

    @Test
    fun `given offer entry with null card then that entry is excluded from proposerMatches`() = runTest {
        // Arrange — entry with card=null should be filtered out by mapNotNull
        offerFlow.value = listOf(
            buildOfferEntry(CARD_ID_LIGHTNING_BOLT, card = null),
            buildOfferEntry(CARD_ID_DARK_RITUAL),
        )
        val friend = buildFriend()
        stubFriendWishlist(
            FRIEND_USER_ID,
            listOf(
                buildFriendCard(CARD_ID_LIGHTNING_BOLT, sourceList = "wishlist"),
                buildFriendCard(CARD_ID_DARK_RITUAL, sourceList = "wishlist"),
            )
        )
        stubFriendOffers(FRIEND_USER_ID, emptyList())

        val vm = createViewModel()
        advanceUntilIdle()
        vm.onFriendSelected(friend)
        advanceUntilIdle()

        // Assert — only DARK_RITUAL shows (LIGHTNING_BOLT entry has card=null)
        val matchIds = vm.uiState.value.proposerMatches.map { it.card.scryfallId }
        assertEquals(1, matchIds.size)
        assertEquals(CARD_ID_DARK_RITUAL, matchIds.first())
    }

    @Test
    fun `proposerMatch row carries friend wishlist metadata (wishlistEntry is set)`() = runTest {
        // Arrange
        offerFlow.value = listOf(buildOfferEntry(CARD_ID_LIGHTNING_BOLT))
        val friend = buildFriend()
        val friendWish = buildFriendCard(
            CARD_ID_LIGHTNING_BOLT,
            sourceList = "wishlist",
            isFoil = true,
            condition = "LP",
            language = "ja",
        )
        stubFriendWishlist(FRIEND_USER_ID, listOf(friendWish))
        stubFriendOffers(FRIEND_USER_ID, emptyList())

        val vm = createViewModel()
        advanceUntilIdle()
        vm.onFriendSelected(friend)
        advanceUntilIdle()

        // Assert — wishlistEntry is populated with friend's preferred variant
        val row = vm.uiState.value.proposerMatches.first()
        val wishEntry = row.wishlistEntry
        assertTrue("wishlistEntry must not be null on a proposer match", wishEntry != null)
        assertEquals(
            "wishlistEntry.isFoil should reflect friend's preference",
            true,
            wishEntry!!.isFoil
        )
        assertEquals("wishlistEntry.condition should reflect friend's preference", "LP", wishEntry.condition)
        assertEquals("wishlistEntry.language should reflect friend's preference", "ja", wishEntry.language)
    }

    // =========================================================================
    // GROUP 2: receiverMatches — friend's offers ∩ my wishlist
    // =========================================================================

    @Test
    fun `given no friend selected when observing matches then receiverMatches is empty`() = runTest {
        // Arrange
        wishlistFlow.value = listOf(buildWishlistEntry(CARD_ID_COUNTERSPELL))
        val vm = createViewModel()
        advanceUntilIdle()

        // Assert
        assertTrue(
            "receiverMatches must be empty when no friend is selected",
            vm.uiState.value.receiverMatches.isEmpty()
        )
    }

    @Test
    fun `given friend offers card in my wishlist then receiverMatches contains that card`() = runTest {
        // Arrange — my wishlist has COUNTERSPELL; friend offers COUNTERSPELL
        wishlistFlow.value = listOf(buildWishlistEntry(CARD_ID_COUNTERSPELL))
        val friend = buildFriend()
        stubFriendWishlist(FRIEND_USER_ID, emptyList())
        stubFriendOffers(FRIEND_USER_ID, listOf(buildFriendCard(CARD_ID_COUNTERSPELL, sourceList = "trade")))

        val vm = createViewModel()
        advanceUntilIdle()
        vm.onFriendSelected(friend)
        advanceUntilIdle()

        // Assert
        val matches = vm.uiState.value.receiverMatches
        assertEquals(1, matches.size)
        assertEquals(CARD_ID_COUNTERSPELL, matches.first().card.scryfallId)
    }

    @Test
    fun `given friend offers card not in my wishlist then receiverMatches is empty`() = runTest {
        // Arrange — my wishlist has COUNTERSPELL; friend offers DARK_RITUAL
        wishlistFlow.value = listOf(buildWishlistEntry(CARD_ID_COUNTERSPELL))
        val friend = buildFriend()
        stubFriendWishlist(FRIEND_USER_ID, emptyList())
        stubFriendOffers(FRIEND_USER_ID, listOf(buildFriendCard(CARD_ID_DARK_RITUAL, sourceList = "trade")))

        val vm = createViewModel()
        advanceUntilIdle()
        vm.onFriendSelected(friend)
        advanceUntilIdle()

        // Assert
        assertTrue(vm.uiState.value.receiverMatches.isEmpty())
    }

    @Test
    fun `given my empty wishlist then receiverMatches is always empty regardless of friend offers`() = runTest {
        // Arrange — my wishlist is empty; friend offers several cards
        wishlistFlow.value = emptyList()
        val friend = buildFriend()
        stubFriendWishlist(FRIEND_USER_ID, emptyList())
        stubFriendOffers(
            FRIEND_USER_ID,
            listOf(
                buildFriendCard(CARD_ID_LIGHTNING_BOLT, sourceList = "trade"),
                buildFriendCard(CARD_ID_COUNTERSPELL, sourceList = "trade"),
            )
        )

        val vm = createViewModel()
        advanceUntilIdle()
        vm.onFriendSelected(friend)
        advanceUntilIdle()

        assertTrue(
            "receiverMatches must be empty when my wishlist is empty",
            vm.uiState.value.receiverMatches.isEmpty()
        )
    }

    @Test
    fun `given friend empty offers then receiverMatches is always empty regardless of my wishlist`() = runTest {
        // Arrange — my wishlist has many cards; friend offers nothing
        wishlistFlow.value = listOf(
            buildWishlistEntry(CARD_ID_COUNTERSPELL),
            buildWishlistEntry(CARD_ID_DARK_RITUAL),
        )
        val friend = buildFriend()
        stubFriendWishlist(FRIEND_USER_ID, emptyList())
        stubFriendOffers(FRIEND_USER_ID, emptyList())

        val vm = createViewModel()
        advanceUntilIdle()
        vm.onFriendSelected(friend)
        advanceUntilIdle()

        assertTrue(
            "receiverMatches must be empty when friend has no offers",
            vm.uiState.value.receiverMatches.isEmpty()
        )
    }

    @Test
    fun `receiverMatch row carries friend offer metadata (offerEntry is set)`() = runTest {
        // Arrange
        wishlistFlow.value = listOf(buildWishlistEntry(CARD_ID_COUNTERSPELL))
        val friend = buildFriend()
        val friendOffer = buildFriendCard(
            CARD_ID_COUNTERSPELL,
            sourceList = "trade",
            isFoil = true,
            condition = "EX",
            language = "de",
            quantity = 2,
        )
        stubFriendWishlist(FRIEND_USER_ID, emptyList())
        stubFriendOffers(FRIEND_USER_ID, listOf(friendOffer))

        val vm = createViewModel()
        advanceUntilIdle()
        vm.onFriendSelected(friend)
        advanceUntilIdle()

        // Assert — offerEntry reflects the actual copy the friend has available
        val row = vm.uiState.value.receiverMatches.first()
        val offerEntry = row.offerEntry
        assertTrue("offerEntry must not be null on a receiver match", offerEntry != null)
        assertEquals(
            "offerEntry.isFoil should reflect friend's actual copy",
            true,
            offerEntry!!.isFoil
        )
        assertEquals("offerEntry.condition should reflect friend's actual copy", "EX", offerEntry.condition)
        assertEquals("offerEntry.language should reflect friend's actual copy", "de", offerEntry.language)
        assertEquals("availableQuantity should equal friend's offer quantity", 2, row.availableQuantity)
    }

    @Test
    fun `given both proposer and receiver matches exist then both lists are populated simultaneously`() = runTest {
        // Arrange — I offer LIGHTNING_BOLT (friend wants it) AND friend offers COUNTERSPELL (I want it)
        offerFlow.value = listOf(buildOfferEntry(CARD_ID_LIGHTNING_BOLT))
        wishlistFlow.value = listOf(buildWishlistEntry(CARD_ID_COUNTERSPELL))
        val friend = buildFriend()
        stubFriendWishlist(FRIEND_USER_ID, listOf(buildFriendCard(CARD_ID_LIGHTNING_BOLT, sourceList = "wishlist")))
        stubFriendOffers(FRIEND_USER_ID, listOf(buildFriendCard(CARD_ID_COUNTERSPELL, sourceList = "trade")))

        val vm = createViewModel()
        advanceUntilIdle()
        vm.onFriendSelected(friend)
        advanceUntilIdle()

        // Assert
        assertEquals(1, vm.uiState.value.proposerMatches.size)
        assertEquals(1, vm.uiState.value.receiverMatches.size)
        assertEquals(CARD_ID_LIGHTNING_BOLT, vm.uiState.value.proposerMatches.first().card.scryfallId)
        assertEquals(CARD_ID_COUNTERSPELL, vm.uiState.value.receiverMatches.first().card.scryfallId)
    }

    // =========================================================================
    // GROUP 3: updateSearchLists side-aware mapping — PROPOSER side
    // =========================================================================

    @Test
    fun `given searchingSide PROPOSER with friend then offerResults comes from MY offers`() = runTest {
        // Arrange — I offer LIGHTNING_BOLT
        offerFlow.value = listOf(buildOfferEntry(CARD_ID_LIGHTNING_BOLT))
        val friend = buildFriend()
        stubFriendWishlist(FRIEND_USER_ID, emptyList())
        stubFriendOffers(FRIEND_USER_ID, listOf(buildFriendCard(CARD_ID_DARK_RITUAL, sourceList = "trade")))

        val vm = createViewModel()
        advanceUntilIdle()
        vm.onFriendSelected(friend)
        advanceUntilIdle()

        // Act — open the PROPOSER search sheet
        vm.onOpenSearch(TradeSide.PROPOSER)
        advanceUntilIdle()

        // Assert — offerResults must be from MY offers (LIGHTNING_BOLT), NOT friend's offers (DARK_RITUAL)
        val offerIds = vm.uiState.value.offerResults.map { it.card.scryfallId }
        assertTrue("PROPOSER offerResults must include my LIGHTNING_BOLT", offerIds.contains(CARD_ID_LIGHTNING_BOLT))
        assertTrue("PROPOSER offerResults must NOT include friend's DARK_RITUAL", !offerIds.contains(CARD_ID_DARK_RITUAL))
    }

    @Test
    fun `given searchingSide PROPOSER with friend then wishlistResults comes from FRIEND's wishlist`() = runTest {
        // Arrange — friend's wishlist has COUNTERSPELL; my wishlist has DARK_RITUAL
        wishlistFlow.value = listOf(buildWishlistEntry(CARD_ID_DARK_RITUAL))
        val friend = buildFriend()
        stubFriendWishlist(FRIEND_USER_ID, listOf(buildFriendCard(CARD_ID_COUNTERSPELL, sourceList = "wishlist")))
        stubFriendOffers(FRIEND_USER_ID, emptyList())

        val vm = createViewModel()
        advanceUntilIdle()
        vm.onFriendSelected(friend)
        advanceUntilIdle()

        vm.onOpenSearch(TradeSide.PROPOSER)
        advanceUntilIdle()

        // Assert — wishlistResults must contain friend's COUNTERSPELL, NOT my DARK_RITUAL
        val wishIds = vm.uiState.value.wishlistResults.map { it.card.scryfallId }
        assertTrue("PROPOSER wishlistResults must contain friend's COUNTERSPELL", wishIds.contains(CARD_ID_COUNTERSPELL))
        assertTrue("PROPOSER wishlistResults must NOT contain my DARK_RITUAL", !wishIds.contains(CARD_ID_DARK_RITUAL))
    }

    @Test
    fun `given searchingSide PROPOSER with no friend then wishlistResults is empty`() = runTest {
        // Arrange — no friend selected; my wishlist has entries
        wishlistFlow.value = listOf(buildWishlistEntry(CARD_ID_DARK_RITUAL))

        val vm = createViewModel()
        advanceUntilIdle()

        vm.onOpenSearch(TradeSide.PROPOSER)
        advanceUntilIdle()

        // Assert — no friend selected → wishlistResults must be empty
        assertTrue(
            "PROPOSER wishlistResults must be empty when no friend is selected",
            vm.uiState.value.wishlistResults.isEmpty()
        )
    }

    @Test
    fun `given searchingSide PROPOSER then addCardsResults comes from MY collection`() = runTest {
        // Arrange — my collection has BIRDS_OF_PARADISE
        collectionFlow.value = listOf(buildUserCardWithCard(CARD_ID_BIRDS_OF_PARADISE, "Birds of Paradise"))
        val friend = buildFriend()
        stubFriendWishlist(FRIEND_USER_ID, emptyList())
        stubFriendOffers(FRIEND_USER_ID, emptyList())

        val vm = createViewModel()
        advanceUntilIdle()
        vm.onFriendSelected(friend)
        advanceUntilIdle()

        vm.onOpenSearch(TradeSide.PROPOSER)
        advanceUntilIdle()

        // Assert — addCardsResults is always from my collection regardless of side
        val collectionIds = vm.uiState.value.addCardsResults.map { it.card.scryfallId }
        assertTrue("addCardsResults must include my BIRDS_OF_PARADISE", collectionIds.contains(CARD_ID_BIRDS_OF_PARADISE))
    }

    // =========================================================================
    // GROUP 4: updateSearchLists side-aware mapping — RECEIVER side
    // =========================================================================

    @Test
    fun `given searchingSide RECEIVER with friend then offerResults comes from FRIEND's offers`() = runTest {
        // Arrange — friend offers DARK_RITUAL; I offer LIGHTNING_BOLT
        offerFlow.value = listOf(buildOfferEntry(CARD_ID_LIGHTNING_BOLT))
        val friend = buildFriend()
        stubFriendWishlist(FRIEND_USER_ID, emptyList())
        stubFriendOffers(FRIEND_USER_ID, listOf(buildFriendCard(CARD_ID_DARK_RITUAL, sourceList = "trade")))

        val vm = createViewModel()
        advanceUntilIdle()
        vm.onFriendSelected(friend)
        advanceUntilIdle()

        vm.onOpenSearch(TradeSide.RECEIVER)
        advanceUntilIdle()

        // Assert — offerResults must be from FRIEND's offers (DARK_RITUAL), NOT mine
        val offerIds = vm.uiState.value.offerResults.map { it.card.scryfallId }
        assertTrue("RECEIVER offerResults must contain friend's DARK_RITUAL", offerIds.contains(CARD_ID_DARK_RITUAL))
        assertTrue("RECEIVER offerResults must NOT contain my LIGHTNING_BOLT", !offerIds.contains(CARD_ID_LIGHTNING_BOLT))
    }

    @Test
    fun `given searchingSide RECEIVER with friend then wishlistResults comes from MY wishlist`() = runTest {
        // Arrange — my wishlist has DARK_RITUAL; friend's wishlist has COUNTERSPELL
        wishlistFlow.value = listOf(buildWishlistEntry(CARD_ID_DARK_RITUAL))
        val friend = buildFriend()
        stubFriendWishlist(FRIEND_USER_ID, listOf(buildFriendCard(CARD_ID_COUNTERSPELL, sourceList = "wishlist")))
        stubFriendOffers(FRIEND_USER_ID, emptyList())

        val vm = createViewModel()
        advanceUntilIdle()
        vm.onFriendSelected(friend)
        advanceUntilIdle()

        vm.onOpenSearch(TradeSide.RECEIVER)
        advanceUntilIdle()

        // Assert — wishlistResults must contain MY DARK_RITUAL, NOT friend's COUNTERSPELL
        val wishIds = vm.uiState.value.wishlistResults.map { it.card.scryfallId }
        assertTrue("RECEIVER wishlistResults must contain my DARK_RITUAL", wishIds.contains(CARD_ID_DARK_RITUAL))
        assertTrue("RECEIVER wishlistResults must NOT contain friend's COUNTERSPELL", !wishIds.contains(CARD_ID_COUNTERSPELL))
    }

    @Test
    fun `given searchingSide RECEIVER then addCardsResults is always my collection`() = runTest {
        // Arrange
        collectionFlow.value = listOf(buildUserCardWithCard(CARD_ID_BIRDS_OF_PARADISE, "Birds of Paradise"))
        val friend = buildFriend()
        stubFriendWishlist(FRIEND_USER_ID, emptyList())
        stubFriendOffers(FRIEND_USER_ID, listOf(buildFriendCard(CARD_ID_LIGHTNING_BOLT, sourceList = "trade")))

        val vm = createViewModel()
        advanceUntilIdle()
        vm.onFriendSelected(friend)
        advanceUntilIdle()

        vm.onOpenSearch(TradeSide.RECEIVER)
        advanceUntilIdle()

        // Assert — addCardsResults always from my collection
        val collectionIds = vm.uiState.value.addCardsResults.map { it.card.scryfallId }
        assertTrue(collectionIds.contains(CARD_ID_BIRDS_OF_PARADISE))
        assertTrue(!collectionIds.contains(CARD_ID_LIGHTNING_BOLT))
    }

    @Test
    fun `given query filter on RECEIVER side then offerResults are filtered by name`() = runTest {
        // Arrange — friend offers LIGHTNING_BOLT and COUNTERSPELL
        val friend = buildFriend()
        stubFriendWishlist(FRIEND_USER_ID, emptyList())
        stubFriendOffers(
            FRIEND_USER_ID,
            listOf(
                buildFriendCard(CARD_ID_LIGHTNING_BOLT, name = "Lightning Bolt", sourceList = "trade"),
                buildFriendCard(CARD_ID_COUNTERSPELL, name = "Counterspell", sourceList = "trade"),
            )
        )

        val vm = createViewModel()
        advanceUntilIdle()
        vm.onFriendSelected(friend)
        advanceUntilIdle()

        vm.onOpenSearch(TradeSide.RECEIVER)
        advanceUntilIdle()

        // Act — type a query that matches only "Lightning"
        vm.onAddCardsQueryChange("Lightning")
        advanceUntilIdle()

        // Assert — only LIGHTNING_BOLT in offerResults
        val offerIds = vm.uiState.value.offerResults.map { it.card.scryfallId }
        assertEquals(1, offerIds.size)
        assertEquals(CARD_ID_LIGHTNING_BOLT, offerIds.first())
    }

    // =========================================================================
    // GROUP 5: onFriendSelected — selection and data loading
    // =========================================================================

    @Test
    fun `given onFriendSelected called then selectedFriend is updated in state`() = runTest {
        // Arrange
        val friend = buildFriend()
        stubFriendWishlist(FRIEND_USER_ID, emptyList())
        stubFriendOffers(FRIEND_USER_ID, emptyList())

        val vm = createViewModel()
        advanceUntilIdle()

        // Act
        vm.onFriendSelected(friend)
        advanceUntilIdle()

        // Assert
        assertEquals(friend, vm.uiState.value.selectedFriend)
        assertEquals(FRIEND_USER_ID, vm.uiState.value.receiverId)
    }

    @Test
    fun `given onFriendSelected with friend then friend data is loaded and matches recomputed`() = runTest {
        // Arrange — I offer LIGHTNING_BOLT; friend wants it
        offerFlow.value = listOf(buildOfferEntry(CARD_ID_LIGHTNING_BOLT))
        val friend = buildFriend()
        stubFriendWishlist(FRIEND_USER_ID, listOf(buildFriendCard(CARD_ID_LIGHTNING_BOLT, sourceList = "wishlist")))
        stubFriendOffers(FRIEND_USER_ID, emptyList())

        val vm = createViewModel()
        advanceUntilIdle()

        // Act
        vm.onFriendSelected(friend)
        advanceUntilIdle()

        // Assert — proposerMatches populated after friend data loaded
        assertEquals(1, vm.uiState.value.proposerMatches.size)
    }

    @Test
    fun `given receiver is in friends list then friend is auto-selected on init`() = runTest {
        // Arrange — friend has same userId as receiverId passed via SavedStateHandle
        val friend = buildFriend(userId = FRIEND_USER_ID)
        stubFriendWishlist(FRIEND_USER_ID, emptyList())
        stubFriendOffers(FRIEND_USER_ID, emptyList())

        // Friends flow emits the friend AFTER ViewModel is created
        val vm = createViewModel(receiverId = FRIEND_USER_ID)
        advanceUntilIdle()

        // Emit friends list (this triggers auto-select in observeFriends)
        friendsFlow.value = listOf(friend)
        advanceUntilIdle()

        // Assert — friend auto-selected
        assertEquals(friend, vm.uiState.value.selectedFriend)
    }

    // =========================================================================
    // GROUP 6: onFriendSelected — stale job cancellation
    // =========================================================================

    @Test
    fun `given friend changed quickly then second friend's data is used for matches`() = runTest {
        // Arrange — two friends, each with different wishlist
        val friend1 = buildFriend(userId = "friend-001", nickname = "Alice")
        val friend2 = buildFriend(userId = "friend-002", nickname = "Bob")
        offerFlow.value = listOf(
            buildOfferEntry(CARD_ID_LIGHTNING_BOLT),
            buildOfferEntry(CARD_ID_COUNTERSPELL),
        )

        // Friend 1 wants LIGHTNING_BOLT; Friend 2 wants COUNTERSPELL
        coEvery { friendRepository.getFriendCollection("friend-001", "wishlist", "", any(), any()) } returns
            Result.success(listOf(buildFriendCard(CARD_ID_LIGHTNING_BOLT, sourceList = "wishlist")))
        coEvery { friendRepository.getFriendCollection("friend-001", "trade", "", any(), any()) } returns
            Result.success(emptyList())
        coEvery { friendRepository.getFriendCollection("friend-002", "wishlist", "", any(), any()) } returns
            Result.success(listOf(buildFriendCard(CARD_ID_COUNTERSPELL, sourceList = "wishlist")))
        coEvery { friendRepository.getFriendCollection("friend-002", "trade", "", any(), any()) } returns
            Result.success(emptyList())

        val vm = createViewModel()
        advanceUntilIdle()

        // Act — select friend1 then immediately friend2
        vm.onFriendSelected(friend1)
        vm.onFriendSelected(friend2)
        advanceUntilIdle()

        // Assert — final state reflects friend2 selection
        assertEquals(friend2, vm.uiState.value.selectedFriend)
        assertEquals("friend-002", vm.uiState.value.receiverId)
    }

    // =========================================================================
    // GROUP 7: onFriendSelected null — clears friend data and resets matches
    // =========================================================================

    @Test
    fun `given friend selected then deselected then selectedFriend is null`() = runTest {
        // Arrange
        offerFlow.value = listOf(buildOfferEntry(CARD_ID_LIGHTNING_BOLT))
        val friend = buildFriend()
        stubFriendWishlist(FRIEND_USER_ID, listOf(buildFriendCard(CARD_ID_LIGHTNING_BOLT, sourceList = "wishlist")))
        stubFriendOffers(FRIEND_USER_ID, emptyList())

        val vm = createViewModel()
        advanceUntilIdle()
        vm.onFriendSelected(friend)
        advanceUntilIdle()

        // Sanity check — matches exist before deselection
        assertEquals(1, vm.uiState.value.proposerMatches.size)

        // Act — deselect
        vm.onFriendSelected(null)
        advanceUntilIdle()

        // Assert
        assertTrue("selectedFriend must be null after deselection", vm.uiState.value.selectedFriend == null)
        assertEquals("receiverId must be cleared after deselection", "", vm.uiState.value.receiverId)
    }

    @Test
    fun `given friend deselected then proposerMatches and receiverMatches are cleared`() = runTest {
        // Arrange — set up both match types
        offerFlow.value = listOf(buildOfferEntry(CARD_ID_LIGHTNING_BOLT))
        wishlistFlow.value = listOf(buildWishlistEntry(CARD_ID_COUNTERSPELL))
        val friend = buildFriend()
        stubFriendWishlist(FRIEND_USER_ID, listOf(buildFriendCard(CARD_ID_LIGHTNING_BOLT, sourceList = "wishlist")))
        stubFriendOffers(FRIEND_USER_ID, listOf(buildFriendCard(CARD_ID_COUNTERSPELL, sourceList = "trade")))

        val vm = createViewModel()
        advanceUntilIdle()
        vm.onFriendSelected(friend)
        advanceUntilIdle()

        // Sanity check
        assertEquals(1, vm.uiState.value.proposerMatches.size)
        assertEquals(1, vm.uiState.value.receiverMatches.size)

        // Act
        vm.onFriendSelected(null)
        advanceUntilIdle()

        // Assert
        assertTrue(
            "proposerMatches must be cleared after friend deselection",
            vm.uiState.value.proposerMatches.isEmpty()
        )
        assertTrue(
            "receiverMatches must be cleared after friend deselection",
            vm.uiState.value.receiverMatches.isEmpty()
        )
    }

    @Test
    fun `given friend deselected when searchingSide is PROPOSER then wishlistResults is cleared`() = runTest {
        // Arrange
        val friend = buildFriend()
        stubFriendWishlist(FRIEND_USER_ID, listOf(buildFriendCard(CARD_ID_COUNTERSPELL, sourceList = "wishlist")))
        stubFriendOffers(FRIEND_USER_ID, emptyList())

        val vm = createViewModel()
        advanceUntilIdle()
        vm.onFriendSelected(friend)
        advanceUntilIdle()
        vm.onOpenSearch(TradeSide.PROPOSER)
        advanceUntilIdle()

        // Sanity check
        assertTrue(vm.uiState.value.wishlistResults.isNotEmpty())

        // Act — deselect friend
        vm.onFriendSelected(null)
        advanceUntilIdle()
        // Re-open search to trigger updateSearchLists with no friend
        vm.onOpenSearch(TradeSide.PROPOSER)
        advanceUntilIdle()

        // Assert — PROPOSER wishlistResults is empty when no friend
        assertTrue(
            "wishlistResults must be empty on PROPOSER side when no friend selected",
            vm.uiState.value.wishlistResults.isEmpty()
        )
    }

    // =========================================================================
    // GROUP 8: Edge cases
    // =========================================================================

    @Test
    fun `given getFriendCollection fails then matches remain empty and state does not crash`() = runTest {
        // Arrange
        offerFlow.value = listOf(buildOfferEntry(CARD_ID_LIGHTNING_BOLT))
        wishlistFlow.value = listOf(buildWishlistEntry(CARD_ID_COUNTERSPELL))
        val friend = buildFriend()
        stubFriendCollectionFailing(FRIEND_USER_ID)

        val vm = createViewModel()
        advanceUntilIdle()

        // Act — should not throw, should leave matches empty
        vm.onFriendSelected(friend)
        advanceUntilIdle()

        // Assert — friend selected but matches empty (load failed)
        assertEquals(friend, vm.uiState.value.selectedFriend)
        assertTrue(
            "proposerMatches must be empty when friend data fetch fails",
            vm.uiState.value.proposerMatches.isEmpty()
        )
        assertTrue(
            "receiverMatches must be empty when friend data fetch fails",
            vm.uiState.value.receiverMatches.isEmpty()
        )
    }

    @Test
    fun `given FriendCard with blank name then it is excluded from matches`() = runTest {
        // Arrange — FriendCard.toCard() returns null when name is blank
        offerFlow.value = listOf(buildOfferEntry(CARD_ID_DARK_RITUAL))
        wishlistFlow.value = listOf(buildWishlistEntry(CARD_ID_DARK_RITUAL))
        val friend = buildFriend()
        // Friend's offer for DARK_RITUAL has a blank name → toCard() returns null → excluded
        val blankNameCard = buildFriendCard(CARD_ID_DARK_RITUAL, name = "", sourceList = "trade")
        stubFriendWishlist(FRIEND_USER_ID, emptyList())
        stubFriendOffers(FRIEND_USER_ID, listOf(blankNameCard))

        val vm = createViewModel()
        advanceUntilIdle()
        vm.onFriendSelected(friend)
        advanceUntilIdle()

        // Assert — DARK_RITUAL excluded because toCard() returns null for blank name
        assertTrue(
            "receiverMatches must exclude FriendCard entries with blank names",
            vm.uiState.value.receiverMatches.isEmpty()
        )
    }

    @Test
    fun `given collectionIds updated after friend selected then match isOwned flags are correct`() = runTest {
        // Arrange — LIGHTNING_BOLT is in my collection AND my offers; friend wants it
        collectionFlow.value = listOf(buildUserCardWithCard(CARD_ID_LIGHTNING_BOLT))
        offerFlow.value = listOf(buildOfferEntry(CARD_ID_LIGHTNING_BOLT))
        val friend = buildFriend()
        stubFriendWishlist(FRIEND_USER_ID, listOf(buildFriendCard(CARD_ID_LIGHTNING_BOLT, sourceList = "wishlist")))
        stubFriendOffers(FRIEND_USER_ID, emptyList())

        val vm = createViewModel()
        advanceUntilIdle()
        vm.onFriendSelected(friend)
        advanceUntilIdle()

        // Assert — match row marks card as owned
        val match = vm.uiState.value.proposerMatches.first()
        assertTrue("proposerMatch row must have isOwned=true when card is in my collection", match.isOwned)
    }

    @Test
    fun `given searchingSide null then only matches are recomputed in state`() = runTest {
        // Arrange — open search then close it (searchingSide = null)
        offerFlow.value = listOf(buildOfferEntry(CARD_ID_LIGHTNING_BOLT))
        val friend = buildFriend()
        stubFriendWishlist(FRIEND_USER_ID, listOf(buildFriendCard(CARD_ID_LIGHTNING_BOLT, sourceList = "wishlist")))
        stubFriendOffers(FRIEND_USER_ID, emptyList())

        val vm = createViewModel()
        advanceUntilIdle()
        vm.onFriendSelected(friend)
        advanceUntilIdle()

        // Open then clear search state
        vm.onOpenSearch(TradeSide.PROPOSER)
        advanceUntilIdle()
        vm.clearAddCardsState()
        advanceUntilIdle()

        // Trigger updateSearchLists with no side open by changing offers flow
        offerFlow.value = listOf(
            buildOfferEntry(CARD_ID_LIGHTNING_BOLT),
            buildOfferEntry(CARD_ID_DARK_RITUAL),
        )
        advanceUntilIdle()

        // Assert — matches still computed even with no search sheet open
        // proposerMatches for DARK_RITUAL should NOT appear (friend doesn't want it)
        val matchIds = vm.uiState.value.proposerMatches.map { it.card.scryfallId }
        assertTrue(matchIds.contains(CARD_ID_LIGHTNING_BOLT))
        assertTrue(!matchIds.contains(CARD_ID_DARK_RITUAL))
    }

    @Test
    fun `given two offer variants of same card then proposerMatches contains one row per variant`() = runTest {
        // Arrange — I have two OpenForTradeEntry for LIGHTNING_BOLT: one foil, one non-foil.
        // Both scryfallIds are in the friend's wishlist → both must appear as separate rows.
        val card = buildCard(CARD_ID_LIGHTNING_BOLT, "Lightning Bolt")
        offerFlow.value = listOf(
            buildOfferEntry(CARD_ID_LIGHTNING_BOLT, card = card, isFoil = false, condition = "NM"),
            buildOfferEntry(CARD_ID_LIGHTNING_BOLT, card = card, isFoil = true, condition = "NM"),
        )
        val friend = buildFriend()
        stubFriendWishlist(FRIEND_USER_ID, listOf(buildFriendCard(CARD_ID_LIGHTNING_BOLT, sourceList = "wishlist")))
        stubFriendOffers(FRIEND_USER_ID, emptyList())

        val vm = createViewModel()
        advanceUntilIdle()
        vm.onFriendSelected(friend)
        advanceUntilIdle()

        // Assert — two rows, one per variant
        val matches = vm.uiState.value.proposerMatches
        assertEquals("Expected one row per offer variant", 2, matches.size)
        assertTrue("All rows must be for LIGHTNING_BOLT", matches.all { it.card.scryfallId == CARD_ID_LIGHTNING_BOLT })
        // Keys must be distinct (no uniqueKey collision)
        assertEquals("uniqueKey values must be distinct", 2, matches.map { it.uniqueKey }.toSet().size)
    }

    @Test
    fun `given two friend offer variants of same card then receiverMatches contains one row per variant`() = runTest {
        // Arrange — friend has two copies of COUNTERSPELL: foil EX in German, non-foil NM in English.
        // My wishlist has COUNTERSPELL → both variants must appear.
        wishlistFlow.value = listOf(buildWishlistEntry(CARD_ID_COUNTERSPELL))
        val friend = buildFriend()
        stubFriendWishlist(FRIEND_USER_ID, emptyList())
        stubFriendOffers(
            FRIEND_USER_ID,
            listOf(
                buildFriendCard(CARD_ID_COUNTERSPELL, sourceList = "trade", isFoil = false, condition = "NM", language = "en"),
                buildFriendCard(CARD_ID_COUNTERSPELL, sourceList = "trade", isFoil = true, condition = "EX", language = "de"),
            )
        )

        val vm = createViewModel()
        advanceUntilIdle()
        vm.onFriendSelected(friend)
        advanceUntilIdle()

        // Assert — two rows, one per variant
        val matches = vm.uiState.value.receiverMatches
        assertEquals("Expected one row per friend offer variant", 2, matches.size)
        assertTrue("All rows must be for COUNTERSPELL", matches.all { it.card.scryfallId == CARD_ID_COUNTERSPELL })
        // Keys must be distinct
        assertEquals("uniqueKey values must be distinct", 2, matches.map { it.uniqueKey }.toSet().size)
    }

    @Test
    fun `given exact-attribute match then isExactMatch is true`() = runTest {
        // Arrange — I offer a foil LP Japanese LIGHTNING_BOLT; friend wants exactly that variant.
        val card = buildCard(CARD_ID_LIGHTNING_BOLT)
        offerFlow.value = listOf(
            buildOfferEntry(CARD_ID_LIGHTNING_BOLT, card = card, isFoil = true, condition = "LP", language = "ja")
        )
        val friend = buildFriend()
        stubFriendWishlist(
            FRIEND_USER_ID,
            listOf(buildFriendCard(CARD_ID_LIGHTNING_BOLT, sourceList = "wishlist", isFoil = true, condition = "LP", language = "ja"))
        )
        stubFriendOffers(FRIEND_USER_ID, emptyList())

        val vm = createViewModel()
        advanceUntilIdle()
        vm.onFriendSelected(friend)
        advanceUntilIdle()

        val row = vm.uiState.value.proposerMatches.first()
        assertTrue("isExactMatch must be true when offer attributes equal wish attributes", row.isExactMatch)
    }

    @Test
    fun `given attribute mismatch then isExactMatch is false`() = runTest {
        // Arrange — I offer a NON-foil NM English LIGHTNING_BOLT; friend wants FOIL.
        val card = buildCard(CARD_ID_LIGHTNING_BOLT)
        offerFlow.value = listOf(
            buildOfferEntry(CARD_ID_LIGHTNING_BOLT, card = card, isFoil = false, condition = "NM", language = "en")
        )
        val friend = buildFriend()
        stubFriendWishlist(
            FRIEND_USER_ID,
            listOf(buildFriendCard(CARD_ID_LIGHTNING_BOLT, sourceList = "wishlist", isFoil = true, condition = "NM", language = "en"))
        )
        stubFriendOffers(FRIEND_USER_ID, emptyList())

        val vm = createViewModel()
        advanceUntilIdle()
        vm.onFriendSelected(friend)
        advanceUntilIdle()

        val row = vm.uiState.value.proposerMatches.first()
        assertTrue("isExactMatch must be false when foil flags differ", !row.isExactMatch)
    }
}
