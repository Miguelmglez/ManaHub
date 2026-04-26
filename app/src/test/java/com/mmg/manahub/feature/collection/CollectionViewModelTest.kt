package com.mmg.manahub.feature.collection

import androidx.work.WorkManager
import com.mmg.manahub.core.domain.model.AdvancedSearchQuery
import com.mmg.manahub.core.domain.model.ComparisonOperator
import com.mmg.manahub.core.domain.model.SearchCriterion
import com.mmg.manahub.core.domain.model.UserCardWithCard
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.domain.repository.UserCardRepository
import com.mmg.manahub.core.domain.usecase.collection.GetCollectionUseCase
import com.mmg.manahub.core.domain.usecase.collection.RemoveCardUseCase
import com.mmg.manahub.core.sync.SyncManager
import com.mmg.manahub.core.sync.SyncState
import com.mmg.manahub.feature.auth.domain.model.SessionState
import com.mmg.manahub.feature.auth.domain.repository.AuthRepository
import com.mmg.manahub.util.TestFixtures
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [CollectionViewModel].
 *
 * Covers:
 * - Collection observation and state propagation
 * - Text search filtering
 * - Sort orders (NAME, PRICE_DESC, PRICE_ASC, RARITY, DATE_ADDED)
 * - Advanced filter criteria (Name, Colors, Rarity, ManaCost, Price, etc.)
 * - View mode toggle
 * - Delete card
 * - refreshPrices() is called on init (regression guard for the CASCADE bug fix)
 * - Error handling
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CollectionViewModelTest {

    // ── Test dispatcher ───────────────────────────────────────────────────────

    private val testDispatcher = StandardTestDispatcher()

    // ── Mocks ─────────────────────────────────────────────────────────────────

    private val getCollection     = mockk<GetCollectionUseCase>()
    private val removeCard        = mockk<RemoveCardUseCase>(relaxed = true)
    private val cardRepository    = mockk<CardRepository>(relaxed = true)
    private val userCardRepository = mockk<UserCardRepository>(relaxed = true)
    private val authRepository    = mockk<AuthRepository>(relaxed = true)
    private val syncManager       = mockk<SyncManager>(relaxed = true)
    private val workManager       = mockk<WorkManager>(relaxed = true)

    private lateinit var viewModel: CollectionViewModel

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildEntry(
        id:         String  = "id-001",
        scryfallId: String  = "id-001",
        name:       String  = "Lightning Bolt",
        rarity:     String  = "common",
        priceUsd:   Double? = 1.00,
        colors:     List<String> = listOf("R"),
        cmc:        Double  = 1.0,
        createdAt:  Long    = 1_000L,
        isFoil:     Boolean = false,
        isInWishlist: Boolean = false,
        isForTrade:   Boolean = false,
        legalityStandard:  String = "not_legal",
        legalityPioneer:   String = "legal",
        legalityModern:    String = "legal",
        legalityCommander: String = "legal",
        oracleText: String? = null,
        typeLine:   String  = "Instant",
        keywords:   List<String> = emptyList(),
        power:      String? = null,
        toughness:  String? = null,
    ): UserCardWithCard = TestFixtures.buildUserCardWithCard(
        userCard = TestFixtures.buildUserCard(
            id           = id,
            scryfallId   = scryfallId,
            isInWishlist = isInWishlist,
            isForTrade   = isForTrade,
            createdAt    = createdAt,
            isFoil       = isFoil,
        ),
        card = TestFixtures.buildCard(
            scryfallId        = scryfallId,
            name              = name,
            rarity            = rarity,
            priceUsd          = priceUsd,
            colors            = colors,
            cmc               = cmc,
            oracleText        = oracleText,
            typeLine          = typeLine,
            keywords          = keywords,
            power             = power,
            toughness         = toughness,
            legalityStandard  = legalityStandard,
            legalityPioneer   = legalityPioneer,
            legalityModern    = legalityModern,
            legalityCommander = legalityCommander,
        ),
    )

    private fun buildViewModel(entries: List<UserCardWithCard> = emptyList()): CollectionViewModel {
        every { getCollection() } returns flowOf(entries)
        coEvery { cardRepository.refreshCollectionPrices() } returns Unit
        coEvery { authRepository.getCurrentUser() } returns null
        every { authRepository.sessionState } returns MutableStateFlow(SessionState.Unauthenticated)
        every { syncManager.syncState } returns MutableStateFlow(SyncState.IDLE)
        return CollectionViewModel(
            getCollection      = getCollection,
            removeCard         = removeCard,
            cardRepository     = cardRepository,
            userCardRepository = userCardRepository,
            authRepository     = authRepository,
            syncManager        = syncManager,
            workManager        = workManager,
        )
    }

    // ── Setup / Teardown ─────────────────────────────────────────────────────

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 1 — Initial state and collection loading
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given collection has entries when ViewModel initializes then cards are loaded into state`() = runTest {
        // Arrange
        val entries = listOf(
            buildEntry(id = "id-001", scryfallId = "id-001", name = "Lightning Bolt"),
            buildEntry(id = "id-002", scryfallId = "id-002", name = "Counterspell"),
        )
        viewModel = buildViewModel(entries)
        advanceUntilIdle()

        // Assert
        assertEquals(2, viewModel.uiState.value.cards.size)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `given empty collection when ViewModel initializes then cards list is empty`() = runTest {
        viewModel = buildViewModel(emptyList())
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.cards.isEmpty())
    }

    @Test
    fun `given collection with stale card when ViewModel initializes then hasStaleCards is true`() = runTest {
        // Arrange
        val staleCard = TestFixtures.buildStaleCard("id-001")
        val entry = TestFixtures.buildUserCardWithCard(card = staleCard)
        coEvery { cardRepository.refreshCollectionPrices() } returns Unit

        viewModel = buildViewModel(emptyList())
        // Override after buildViewModel so init coroutine picks up the stale-card flow
        every { getCollection() } returns flowOf(listOf(entry))
        advanceUntilIdle()

        // Assert
        assertTrue(viewModel.uiState.value.hasStaleCards)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 2 — CASCADE BUG REGRESSION: refreshCollectionPrices is called on init
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given ViewModel initialized then refreshCollectionPrices is called once`() = runTest {
        viewModel = buildViewModel()
        advanceUntilIdle()

        // This verifies that price refresh is triggered — the underlying repo
        // call must use safe upsert (INSERT-IGNORE+UPDATE), not REPLACE
        coVerify(exactly = 1) { cardRepository.refreshCollectionPrices() }
    }

    @Test
    fun `given refreshCollectionPrices throws when ViewModel initializes then state remains stable`() = runTest {
        // Arrange — even if the refresh crashes, the collection should still load
        val entries = listOf(buildEntry())
        every { getCollection() } returns flowOf(entries)
        coEvery { cardRepository.refreshCollectionPrices() } throws RuntimeException("Network error")

        viewModel = buildViewModel(entries)
        advanceUntilIdle()

        // Assert: cards are still present — refresh error is swallowed by runCatching
        assertEquals(1, viewModel.uiState.value.cards.size)
        assertNull(viewModel.uiState.value.error)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 3 — Text search
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given cards in collection when onSearchQueryChange with matching name then filtered results are returned`() = runTest {
        val entries = listOf(
            buildEntry(scryfallId = "id-001", name = "Lightning Bolt"),
            buildEntry(scryfallId = "id-002", name = "Counterspell"),
        )
        viewModel = buildViewModel(entries)
        advanceUntilIdle()

        viewModel.onSearchQueryChange("lightning")
        advanceUntilIdle()

        val cards = viewModel.uiState.value.cards
        assertEquals(1, cards.size)
        assertEquals("Lightning Bolt", cards.first().card.name)
    }

    @Test
    fun `given cards in collection when onSearchQueryChange with non-matching query then empty list is returned`() = runTest {
        val entries = listOf(buildEntry(name = "Lightning Bolt"))
        viewModel = buildViewModel(entries)
        advanceUntilIdle()

        viewModel.onSearchQueryChange("zzz")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.cards.isEmpty())
    }

    @Test
    fun `given filtered results when onSearchQueryChange cleared then all cards are shown again`() = runTest {
        val entries = listOf(
            buildEntry(scryfallId = "id-001", name = "Lightning Bolt"),
            buildEntry(scryfallId = "id-002", name = "Counterspell"),
        )
        viewModel = buildViewModel(entries)
        advanceUntilIdle()

        viewModel.onSearchQueryChange("bolt")
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.cards.size)

        viewModel.onSearchQueryChange("")
        advanceUntilIdle()
        assertEquals(2, viewModel.uiState.value.cards.size)
    }

    @Test
    fun `given search when onSearchQueryChange then query is case-insensitive`() = runTest {
        val entries = listOf(buildEntry(name = "Lightning Bolt"))
        viewModel = buildViewModel(entries)
        advanceUntilIdle()

        viewModel.onSearchQueryChange("LIGHTNING")
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.cards.size)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 4 — Sort orders
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given multiple cards when sorted by NAME then alphabetical order is applied`() = runTest {
        val entries = listOf(
            buildEntry(scryfallId = "id-001", name = "Zap"),
            buildEntry(scryfallId = "id-002", name = "Aura"),
            buildEntry(scryfallId = "id-003", name = "Millstone"),
        )
        viewModel = buildViewModel(entries)
        advanceUntilIdle()

        viewModel.onSortChange(SortOrder.NAME)
        advanceUntilIdle()

        val names = viewModel.uiState.value.cards.map { it.card.name }
        assertEquals(listOf("Aura", "Millstone", "Zap"), names)
    }

    @Test
    fun `given multiple cards when sorted by PRICE_DESC then most expensive is first`() = runTest {
        val entries = listOf(
            buildEntry(scryfallId = "id-001", name = "Cheap",    priceUsd = 0.50),
            buildEntry(scryfallId = "id-002", name = "Expensive", priceUsd = 99.99),
            buildEntry(scryfallId = "id-003", name = "Mid",      priceUsd = 5.00),
        )
        viewModel = buildViewModel(entries)
        advanceUntilIdle()

        viewModel.onSortChange(SortOrder.PRICE_DESC)
        advanceUntilIdle()

        val names = viewModel.uiState.value.cards.map { it.card.name }
        assertEquals("Expensive", names.first())
        assertEquals("Cheap",     names.last())
    }

    @Test
    fun `given multiple cards when sorted by PRICE_ASC then cheapest is first`() = runTest {
        val entries = listOf(
            buildEntry(scryfallId = "id-001", name = "Cheap",    priceUsd = 0.50),
            buildEntry(scryfallId = "id-002", name = "Expensive", priceUsd = 99.99),
        )
        viewModel = buildViewModel(entries)
        advanceUntilIdle()

        viewModel.onSortChange(SortOrder.PRICE_ASC)
        advanceUntilIdle()

        assertEquals("Cheap", viewModel.uiState.value.cards.first().card.name)
    }

    @Test
    fun `given mixed rarities when sorted by RARITY then mythic comes before common`() = runTest {
        val entries = listOf(
            buildEntry(scryfallId = "id-001", name = "Common Card",  rarity = "common"),
            buildEntry(scryfallId = "id-002", name = "Mythic Card",  rarity = "mythic"),
            buildEntry(scryfallId = "id-003", name = "Rare Card",    rarity = "rare"),
            buildEntry(scryfallId = "id-004", name = "Uncommon Card", rarity = "uncommon"),
        )
        viewModel = buildViewModel(entries)
        advanceUntilIdle()

        viewModel.onSortChange(SortOrder.RARITY)
        advanceUntilIdle()

        val names = viewModel.uiState.value.cards.map { it.card.name }
        assertEquals("Mythic Card", names.first())
        assertEquals("Common Card", names.last())
    }

    @Test
    fun `given different addedAt timestamps when sorted by DATE_ADDED then newest is first`() = runTest {
        val entries = listOf(
            buildEntry(scryfallId = "id-001", name = "Old",    createdAt = 1_000L),
            buildEntry(scryfallId = "id-002", name = "Newest", createdAt = 9_000L),
            buildEntry(scryfallId = "id-003", name = "Mid",    createdAt = 5_000L),
        )
        viewModel = buildViewModel(entries)
        advanceUntilIdle()

        viewModel.onSortChange(SortOrder.DATE_ADDED)
        advanceUntilIdle()

        assertEquals("Newest", viewModel.uiState.value.cards.first().card.name)
        assertEquals("Old",    viewModel.uiState.value.cards.last().card.name)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 5 — ViewMode toggle
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given GRID mode when onViewModeToggle then mode switches to LIST`() = runTest {
        viewModel = buildViewModel()
        advanceUntilIdle()
        assertEquals(ViewMode.GRID, viewModel.uiState.value.viewMode)

        viewModel.onViewModeToggle()

        assertEquals(ViewMode.LIST, viewModel.uiState.value.viewMode)
    }

    @Test
    fun `given LIST mode when onViewModeToggle then mode switches back to GRID`() = runTest {
        viewModel = buildViewModel()
        advanceUntilIdle()
        viewModel.onViewModeToggle()   // GRID → LIST
        viewModel.onViewModeToggle()   // LIST → GRID

        assertEquals(ViewMode.GRID, viewModel.uiState.value.viewMode)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 6 — Delete card
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given valid id when onDeleteCard then removeCard use case is called`() = runTest {
        viewModel = buildViewModel()
        advanceUntilIdle()
        coEvery { removeCard(any()) } returns Unit

        viewModel.onDeleteCard("id-007")
        advanceUntilIdle()

        coVerify(exactly = 1) { removeCard("id-007") }
    }

    @Test
    fun `given delete fails when onDeleteCard then error is set in state`() = runTest {
        viewModel = buildViewModel()
        advanceUntilIdle()
        coEvery { removeCard(any()) } throws RuntimeException("FK violation")

        viewModel.onDeleteCard("id-007")
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.error)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 7 — Advanced filters: applyAdvancedFilters
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given Name criterion when applyAdvancedFilters then only matching cards are shown`() = runTest {
        val entries = listOf(
            buildEntry(scryfallId = "id-001", name = "Lightning Bolt"),
            buildEntry(scryfallId = "id-002", name = "Counterspell"),
        )
        viewModel = buildViewModel(entries)
        advanceUntilIdle()

        val query = AdvancedSearchQuery(criteria = listOf(SearchCriterion.Name("bolt")))
        viewModel.applyAdvancedFilters(query)
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.cards.size)
        assertEquals("Lightning Bolt", viewModel.uiState.value.cards.first().card.name)
    }

    @Test
    fun `given exact Name criterion when applyAdvancedFilters then partial match is excluded`() = runTest {
        val entries = listOf(
            buildEntry(scryfallId = "id-001", name = "Lightning Bolt"),
            buildEntry(scryfallId = "id-002", name = "Bolt of Lightning"),
        )
        viewModel = buildViewModel(entries)
        advanceUntilIdle()

        val query = AdvancedSearchQuery(
            criteria = listOf(SearchCriterion.Name("Lightning Bolt", exact = true))
        )
        viewModel.applyAdvancedFilters(query)
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.cards.size)
        assertEquals("Lightning Bolt", viewModel.uiState.value.cards.first().card.name)
    }

    @Test
    fun `given Colors criterion non-exactly when applyAdvancedFilters then mono-red card passes`() = runTest {
        val entries = listOf(
            buildEntry(scryfallId = "id-001", name = "Red Card",   colors = listOf("R")),
            buildEntry(scryfallId = "id-002", name = "Blue Card",  colors = listOf("U")),
        )
        viewModel = buildViewModel(entries)
        advanceUntilIdle()

        val query = AdvancedSearchQuery(
            criteria = listOf(SearchCriterion.Colors(setOf("R"), exactly = false))
        )
        viewModel.applyAdvancedFilters(query)
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.cards.size)
        assertEquals("Red Card", viewModel.uiState.value.cards.first().card.name)
    }

    @Test
    fun `given Rarity EQUAL criterion when applyAdvancedFilters then only matching rarity cards are shown`() = runTest {
        val entries = listOf(
            buildEntry(scryfallId = "id-001", name = "Common",  rarity = "common"),
            buildEntry(scryfallId = "id-002", name = "Mythic",  rarity = "mythic"),
        )
        viewModel = buildViewModel(entries)
        advanceUntilIdle()

        val query = AdvancedSearchQuery(
            criteria = listOf(SearchCriterion.Rarity("mythic", ComparisonOperator.EQUAL))
        )
        viewModel.applyAdvancedFilters(query)
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.cards.size)
        assertEquals("Mythic", viewModel.uiState.value.cards.first().card.name)
    }

    @Test
    fun `given Rarity GREATER_OR_EQUAL rare when applyAdvancedFilters then rare and mythic are included`() = runTest {
        val entries = listOf(
            buildEntry(scryfallId = "id-001", name = "Common",   rarity = "common"),
            buildEntry(scryfallId = "id-002", name = "Rare",     rarity = "rare"),
            buildEntry(scryfallId = "id-003", name = "Mythic",   rarity = "mythic"),
        )
        viewModel = buildViewModel(entries)
        advanceUntilIdle()

        val query = AdvancedSearchQuery(
            criteria = listOf(SearchCriterion.Rarity("rare", ComparisonOperator.GREATER_OR_EQUAL))
        )
        viewModel.applyAdvancedFilters(query)
        advanceUntilIdle()

        val names = viewModel.uiState.value.cards.map { it.card.name }
        assertTrue(names.contains("Rare"))
        assertTrue(names.contains("Mythic"))
        assertFalse(names.contains("Common"))
    }

    @Test
    fun `given ManaCost criterion when applyAdvancedFilters then only cards with matching cmc are shown`() = runTest {
        val entries = listOf(
            buildEntry(scryfallId = "id-001", name = "1-drop", cmc = 1.0),
            buildEntry(scryfallId = "id-002", name = "3-drop", cmc = 3.0),
        )
        viewModel = buildViewModel(entries)
        advanceUntilIdle()

        val query = AdvancedSearchQuery(
            criteria = listOf(SearchCriterion.ManaCost(3, ComparisonOperator.EQUAL))
        )
        viewModel.applyAdvancedFilters(query)
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.cards.size)
        assertEquals("3-drop", viewModel.uiState.value.cards.first().card.name)
    }

    @Test
    fun `given Price criterion when applyAdvancedFilters then cards over the threshold are excluded`() = runTest {
        val entries = listOf(
            buildEntry(scryfallId = "id-001", name = "Cheap",    priceUsd = 0.50),
            buildEntry(scryfallId = "id-002", name = "Expensive", priceUsd = 200.0),
        )
        viewModel = buildViewModel(entries)
        advanceUntilIdle()

        val query = AdvancedSearchQuery(
            criteria = listOf(
                SearchCriterion.Price(value = 10.0, currency = "usd", operator = ComparisonOperator.LESS_OR_EQUAL)
            )
        )
        viewModel.applyAdvancedFilters(query)
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.cards.size)
        assertEquals("Cheap", viewModel.uiState.value.cards.first().card.name)
    }

    @Test
    fun `given Format legal criterion when applyAdvancedFilters then legal cards are included`() = runTest {
        val entries = listOf(
            buildEntry(scryfallId = "id-001", name = "Modern Legal",   legalityModern = "legal"),
            buildEntry(scryfallId = "id-002", name = "Modern Banned",  legalityModern = "banned"),
        )
        viewModel = buildViewModel(entries)
        advanceUntilIdle()

        val query = AdvancedSearchQuery(
            criteria = listOf(SearchCriterion.Format("modern", legal = true))
        )
        viewModel.applyAdvancedFilters(query)
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.cards.size)
        assertEquals("Modern Legal", viewModel.uiState.value.cards.first().card.name)
    }

    @Test
    fun `given IsForTrade criterion when applyAdvancedFilters then only trade cards are shown`() = runTest {
        val entries = listOf(
            buildEntry(scryfallId = "id-001", name = "For Trade",  isForTrade = true),
            buildEntry(scryfallId = "id-002", name = "Not Trade",  isForTrade = false),
        )
        viewModel = buildViewModel(entries)
        advanceUntilIdle()

        val query = AdvancedSearchQuery(
            criteria = listOf(SearchCriterion.IsForTrade(true))
        )
        viewModel.applyAdvancedFilters(query)
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.cards.size)
        assertEquals("For Trade", viewModel.uiState.value.cards.first().card.name)
    }

    @Test
    fun `given OracleText criterion when applyAdvancedFilters then only matching oracle text cards are shown`() = runTest {
        val entries = listOf(
            buildEntry(scryfallId = "id-001", name = "Deals Damage", oracleText = "deals 3 damage to any target"),
            buildEntry(scryfallId = "id-002", name = "Draws Card",   oracleText = "draw a card"),
        )
        viewModel = buildViewModel(entries)
        advanceUntilIdle()

        val query = AdvancedSearchQuery(
            criteria = listOf(SearchCriterion.OracleText("damage"))
        )
        viewModel.applyAdvancedFilters(query)
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.cards.size)
        assertEquals("Deals Damage", viewModel.uiState.value.cards.first().card.name)
    }

    @Test
    fun `given multiple criteria when applyAdvancedFilters then all criteria must match (AND logic)`() = runTest {
        val entries = listOf(
            buildEntry(scryfallId = "id-001", name = "Red Instant", colors = listOf("R"), typeLine = "Instant"),
            buildEntry(scryfallId = "id-002", name = "Red Sorcery", colors = listOf("R"), typeLine = "Sorcery"),
            buildEntry(scryfallId = "id-003", name = "Blue Instant", colors = listOf("U"), typeLine = "Instant"),
        )
        viewModel = buildViewModel(entries)
        advanceUntilIdle()

        val query = AdvancedSearchQuery(
            criteria = listOf(
                SearchCriterion.Colors(setOf("R")),
                SearchCriterion.CardType("Instant"),
            )
        )
        viewModel.applyAdvancedFilters(query)
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.cards.size)
        assertEquals("Red Instant", viewModel.uiState.value.cards.first().card.name)
    }

    @Test
    fun `given active filters when clearAdvancedFilters then all cards are shown again`() = runTest {
        val entries = listOf(
            buildEntry(scryfallId = "id-001", name = "Lightning Bolt"),
            buildEntry(scryfallId = "id-002", name = "Counterspell"),
        )
        viewModel = buildViewModel(entries)
        advanceUntilIdle()

        // Apply filter
        viewModel.applyAdvancedFilters(
            AdvancedSearchQuery(criteria = listOf(SearchCriterion.Name("bolt")))
        )
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.cards.size)

        // Clear
        viewModel.clearAdvancedFilters()
        advanceUntilIdle()
        assertEquals(2, viewModel.uiState.value.cards.size)
        assertNull(viewModel.uiState.value.activeQuery)
    }

    @Test
    fun `given empty AdvancedSearchQuery when applyAdvancedFilters then activeQuery is null`() = runTest {
        viewModel = buildViewModel(listOf(buildEntry()))
        advanceUntilIdle()

        viewModel.applyAdvancedFilters(AdvancedSearchQuery(criteria = emptyList()))
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.activeQuery)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 8 — groupByCard: multiple copies collapse into one group
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given two copies of same card when collection loads then they are grouped into one entry`() = runTest {
        val entries = listOf(
            TestFixtures.buildUserCardWithCard(
                userCard = TestFixtures.buildUserCard(id = "uc-001", scryfallId = "id-001", quantity = 1, isFoil = false),
                card     = TestFixtures.buildCard("id-001", name = "Lightning Bolt"),
            ),
            TestFixtures.buildUserCardWithCard(
                userCard = TestFixtures.buildUserCard(id = "uc-002", scryfallId = "id-001", quantity = 2, isFoil = true),
                card     = TestFixtures.buildCard("id-001", name = "Lightning Bolt"),
            ),
        )
        viewModel = buildViewModel(entries)
        advanceUntilIdle()

        // Assert: both UserCard rows are collapsed into one CollectionCardGroup
        assertEquals(1, viewModel.uiState.value.cards.size)
        val group = viewModel.uiState.value.cards.first()
        assertEquals(3,    group.totalQuantity)   // 1 + 2
        assertTrue(group.hasFoil)
        assertEquals(2,    group.distinctCopies)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 9 — Error handling
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given collection flow throws when observeCollection then error is set in state`() = runTest {
        // Arrange
        coEvery { cardRepository.refreshCollectionPrices() } returns Unit

        viewModel = buildViewModel(emptyList())
        // Override after buildViewModel so init coroutine picks up the throwing flow
        every { getCollection() } returns kotlinx.coroutines.flow.flow {
            throw RuntimeException("DB error")
        }
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `given error in state when onErrorDismissed then error is cleared`() = runTest {
        viewModel = buildViewModel()
        advanceUntilIdle()
        coEvery { removeCard(any()) } throws RuntimeException("error")
        viewModel.onDeleteCard("id-001")
        advanceUntilIdle()

        viewModel.onErrorDismissed()

        assertNull(viewModel.uiState.value.error)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 10 — Power / Toughness criteria
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given Power criterion when applyAdvancedFilters then only creatures with matching power are shown`() = runTest {
        val entries = listOf(
            buildEntry(scryfallId = "id-001", name = "2-2 Creature", power = "2", toughness = "2"),
            buildEntry(scryfallId = "id-002", name = "4-4 Creature", power = "4", toughness = "4"),
        )
        viewModel = buildViewModel(entries)
        advanceUntilIdle()

        val query = AdvancedSearchQuery(
            criteria = listOf(SearchCriterion.Power(4, ComparisonOperator.EQUAL))
        )
        viewModel.applyAdvancedFilters(query)
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.cards.size)
        assertEquals("4-4 Creature", viewModel.uiState.value.cards.first().card.name)
    }

    @Test
    fun `given Power criterion on non-creature card when applyAdvancedFilters then card is excluded`() = runTest {
        // Non-creature cards have null power
        val entries = listOf(
            buildEntry(scryfallId = "id-001", name = "Instant Spell", power = null)
        )
        viewModel = buildViewModel(entries)
        advanceUntilIdle()

        val query = AdvancedSearchQuery(
            criteria = listOf(SearchCriterion.Power(2, ComparisonOperator.EQUAL))
        )
        viewModel.applyAdvancedFilters(query)
        advanceUntilIdle()

        // null power → toIntOrNull returns null → criterion returns false
        assertTrue(viewModel.uiState.value.cards.isEmpty())
    }
}

