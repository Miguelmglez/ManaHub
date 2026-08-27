package com.mmg.manahub.feature.collection

import androidx.lifecycle.SavedStateHandle
import androidx.work.WorkManager
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.core.model.AdvancedSearchQuery
import com.mmg.manahub.core.model.CollectionGroupingMode
import com.mmg.manahub.core.model.CollectionViewMode
import com.mmg.manahub.core.model.ComparisonOperator
import com.mmg.manahub.core.model.SearchCriterion
import com.mmg.manahub.core.model.UserCardWithCard
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.domain.repository.UserCardRepository
import com.mmg.manahub.core.domain.repository.UserPreferencesRepository
import com.mmg.manahub.core.domain.usecase.collection.GetCollectionUseCase
import com.mmg.manahub.core.sync.SyncManager
import com.mmg.manahub.core.sync.SyncState
import com.mmg.manahub.core.util.AnalyticsHelper
import com.mmg.manahub.core.domain.auth.SessionState
import com.mmg.manahub.core.domain.auth.AuthRepository
import com.mmg.manahub.feature.collection.presentation.CollectionViewModel
import com.mmg.manahub.feature.collection.presentation.SortOrder
import com.mmg.manahub.core.domain.repository.OpenForTradeRepository
import com.mmg.manahub.core.domain.repository.WishlistRepository
import com.mmg.manahub.feature.trades.domain.usecase.GetLocalWishlistUseCase
import com.mmg.manahub.feature.trades.domain.usecase.MigrateLocalTradeListsUseCase
import com.mmg.manahub.util.TestFixtures
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
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

    private val getCollection          = mockk<GetCollectionUseCase>()
    private val cardRepository         = mockk<CardRepository>(relaxed = true)
    private val userCardRepository     = mockk<UserCardRepository>(relaxed = true)
    private val authRepository         = mockk<AuthRepository>(relaxed = true)
    private val syncManager            = mockk<SyncManager>(relaxed = true)
    private val workManager            = mockk<WorkManager>(relaxed = true)
    private val migrateLocalTradeLists = mockk<MigrateLocalTradeListsUseCase>(relaxed = true)
    private val getLocalWishlist          = mockk<GetLocalWishlistUseCase>(relaxed = true)
    private val wishlistRepository        = mockk<WishlistRepository>(relaxed = true)
    private val openForTradeRepository    = mockk<OpenForTradeRepository>(relaxed = true)
    private val userPreferencesRepository = mockk<UserPreferencesRepository>(relaxed = true)
    private val analyticsHelper           = mockk<AnalyticsHelper>(relaxed = true)

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
        tags:       List<com.mmg.manahub.core.model.CardTag> = emptyList(),
    ): UserCardWithCard = TestFixtures.buildUserCardWithCard(
        userCard = TestFixtures.buildUserCard(
            id           = id,
            scryfallId   = scryfallId,
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
            tags              = tags,
        ),
    )

    /**
     * Builds a [CollectionViewModel] for testing.
     *
     * @param entries The collection entries to return from [getCollection]. Ignored if
     *   [getCollection] was already configured by the calling test.
     * @param overrideCollection When false (default) the helper configures [getCollection]
     *   to return [flowOf(entries)]. Set to true to skip this stub so a per-test
     *   stub already registered on [getCollection] is not overridden.
     */
    private fun buildViewModel(
        entries: List<UserCardWithCard> = emptyList(),
        overrideCollection: Boolean = true,
    ): CollectionViewModel {
        if (overrideCollection) every { getCollection() } returns flowOf(entries)
        coEvery { authRepository.getCurrentUser() } returns null
        every { authRepository.sessionState } returns MutableStateFlow(SessionState.Unauthenticated)
        every { syncManager.syncState } returns MutableStateFlow(SyncState.IDLE)
        every { getLocalWishlist() } returns flowOf(emptyList())
        coEvery { migrateLocalTradeLists(any()) } returns Result.success(0)

        return CollectionViewModel(
            savedStateHandle       = SavedStateHandle(),
            getCollection          = getCollection,
            cardRepository         = cardRepository,
            userCardRepository     = userCardRepository,
            authRepository         = authRepository,
            syncManager            = syncManager,
            workManager            = workManager,
            migrateLocalTradeLists = migrateLocalTradeLists,
            getLocalWishlist          = getLocalWishlist,
            wishlistRepository        = wishlistRepository,
            openForTradeRepository    = openForTradeRepository,
            userPreferencesRepository = userPreferencesRepository,
            analyticsHelper           = analyticsHelper,
        )
    }

    // ── Setup / Teardown ─────────────────────────────────────────────────────

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        // Prevent FirebaseCrashlytics.getInstance() from crashing in JVM tests
        mockkStatic(FirebaseCrashlytics::class)
        val crashlytics = mockk<FirebaseCrashlytics>(relaxed = true)
        every { FirebaseCrashlytics.getInstance() } returns crashlytics
        // Default collectionViewModeFlow stub (can be overridden per-test before buildViewModel())
        every { userPreferencesRepository.collectionViewModeFlow } returns flowOf(CollectionViewMode.GRID)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(FirebaseCrashlytics::class)
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

        viewModel = buildViewModel(emptyList())
        // Override after buildViewModel so init coroutine picks up the stale-card flow
        every { getCollection() } returns flowOf(listOf(entry))
        advanceUntilIdle()

        // Assert
        assertTrue(viewModel.uiState.value.hasStaleCards)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 2 — (RETIRED) CASCADE BUG REGRESSION: refreshCollectionPrices is called on init
    //  Backend & Performance Optimization plan, WS1+WS3 Part B item 7a (2026-07-28):
    //  CollectionViewModel no longer calls a price refresh on init at all — that call
    //  (`cardRepository.refreshCollectionPrices()`) was deleted end-to-end (interface member +
    //  CardRepositoryImpl override) since it duplicated PriceRefreshWorker's daily,
    //  watermark-guarded, stale-only refresh with no guard of its own. This group's two tests
    //  asserted the now-deleted call and were removed with it. WS6 (android-unit-test-writer) should
    //  add equivalent coverage against RefreshCollectionPricesUseCase/PriceRefreshWorker instead —
    //  see the WS1+WS3 task report for the exact behaviours to pin.
    // ══════════════════════════════════════════════════════════════════════════

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

    // ── Search debounce (Backend & Performance Optimization plan, WS5c item 4, 2026-07-28) ──

    @Test
    fun `given rapid onSearchQueryChange keystrokes when less than the debounce window elapses then the filtered cards are not recomputed yet`() =
        runTest {
            val entries = listOf(
                buildEntry(scryfallId = "id-001", name = "Lightning Bolt"),
                buildEntry(scryfallId = "id-002", name = "Counterspell"),
            )
            viewModel = buildViewModel(entries)
            advanceUntilIdle()
            assertEquals(2, viewModel.uiState.value.cards.size)

            viewModel.onSearchQueryChange("l")
            viewModel.onSearchQueryChange("li")
            viewModel.onSearchQueryChange("lig")
            // Well under the 300ms debounce window -- the expensive re-filter must not have run yet.
            advanceTimeBy(100L)

            assertEquals(
                "the re-filter pass must not fire before the debounce window elapses",
                2, viewModel.uiState.value.cards.size,
            )
        }

    @Test
    fun `given rapid onSearchQueryChange keystrokes when the debounce window elapses then exactly one recompute uses the final query`() =
        runTest {
            val entries = listOf(
                buildEntry(scryfallId = "id-001", name = "Lightning Bolt"),
                buildEntry(scryfallId = "id-002", name = "Counterspell"),
            )
            viewModel = buildViewModel(entries)
            advanceUntilIdle()

            // Rapid keystrokes spelling out "lightning" -- each call updates the visible TextField
            // state immediately (no input lag), but only the FINAL value after the debounce window
            // should ever drive a re-filter.
            "lightning".indices.forEach { i -> viewModel.onSearchQueryChange("lightning".substring(0, i + 1)) }
            advanceUntilIdle()

            assertEquals(1, viewModel.uiState.value.cards.size)
            assertEquals("Lightning Bolt", viewModel.uiState.value.cards.first().card.name)
        }

    @Test
    fun `given a search keystroke when onSearchQueryChange is called then the visible searchQuery updates immediately without waiting for the debounce`() =
        runTest {
            viewModel = buildViewModel(emptyList())
            advanceUntilIdle()

            viewModel.onSearchQueryChange("lightning")
            // Zero time advanced -- the debounce hasn't fired, but the TextField-visible state
            // must already reflect the keystroke (no input lag for the user).
            assertEquals("lightning", viewModel.uiState.value.searchQuery)
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
        assertEquals(CollectionViewMode.GRID, viewModel.uiState.value.viewMode)

        viewModel.onViewModeToggle()
        advanceUntilIdle()

        coVerify { userPreferencesRepository.saveCollectionViewMode(CollectionViewMode.LIST) }
    }

    @Test
    fun `given LIST mode when onViewModeToggle then mode switches back to GRID`() = runTest {
        val modeFlow = MutableStateFlow(CollectionViewMode.GRID)
        every { userPreferencesRepository.collectionViewModeFlow } returns modeFlow
        coEvery { userPreferencesRepository.saveCollectionViewMode(any()) } answers {
            modeFlow.value = firstArg()
        }

        viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.onViewModeToggle()   // GRID → LIST
        advanceUntilIdle()
        assertEquals(CollectionViewMode.LIST, viewModel.uiState.value.viewMode)

        viewModel.onViewModeToggle()   // LIST → GRID
        advanceUntilIdle()
        assertEquals(CollectionViewMode.GRID, viewModel.uiState.value.viewMode)
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

        // SearchCriterion.Rarity is now a plain membership list (multi-select rarity chips in the
        // advanced-search UI, see AdvancedSearchViewModel.kt / CollectionViewModel.compareRarity) —
        // it no longer carries a ComparisonOperator; there is no ordered "at least this rarity"
        // concept anymore, only "rarity is one of these values".
        val query = AdvancedSearchQuery(
            criteria = listOf(SearchCriterion.Rarity(listOf("mythic")))
        )
        viewModel.applyAdvancedFilters(query)
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.cards.size)
        assertEquals("Mythic", viewModel.uiState.value.cards.first().card.name)
    }

    @Test
    fun `given Rarity criterion listing rare and mythic when applyAdvancedFilters then only those rarities are included`() = runTest {
        val entries = listOf(
            buildEntry(scryfallId = "id-001", name = "Common",   rarity = "common"),
            buildEntry(scryfallId = "id-002", name = "Rare",     rarity = "rare"),
            buildEntry(scryfallId = "id-003", name = "Mythic",   rarity = "mythic"),
        )
        viewModel = buildViewModel(entries)
        advanceUntilIdle()

        // No ordered "GREATER_OR_EQUAL rare" operator exists anymore — the equivalent selection is
        // expressed as an explicit multi-value list (mirrors a user checking both the Rare and
        // Mythic chips in the advanced-search sheet).
        val query = AdvancedSearchQuery(
            criteria = listOf(SearchCriterion.Rarity(listOf("rare", "mythic")))
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
            criteria = listOf(SearchCriterion.Format(listOf("modern"), legal = true))
        )
        viewModel.applyAdvancedFilters(query)
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.cards.size)
        assertEquals("Modern Legal", viewModel.uiState.value.cards.first().card.name)
    }

    @Test
    fun `given CollectionStatus forTrade criterion when applyAdvancedFilters then only trade cards are shown`() = runTest {
        val entries = listOf(
            buildEntry(scryfallId = "id-001", name = "For Trade",  isForTrade = true),
            buildEntry(scryfallId = "id-002", name = "Not Trade",  isForTrade = false),
        )
        viewModel = buildViewModel(entries)
        advanceUntilIdle()

        // SearchCriterion.IsForTrade was folded into the combined local-only
        // SearchCriterion.CollectionStatus(wishlist, forTrade) criterion.
        val query = AdvancedSearchQuery(
            criteria = listOf(SearchCriterion.CollectionStatus(wishlist = false, forTrade = true))
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
                SearchCriterion.CardType(setOf("Instant")),
            )
        )
        viewModel.applyAdvancedFilters(query)
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.cards.size)
        assertEquals("Red Instant", viewModel.uiState.value.cards.first().card.name)
    }

    // ── CardFunction (Deck Analysis — Category Sections plan, W4) ───────────────
    // Regression guard for the `else -> true` trap noted in CLAUDE.md: matchesCriterion's `when`
    // ends in `else -> true`, so a missing/short-circuited SearchCriterion.CardFunction branch would
    // silently match every card with zero compile error. These tests fail if that branch is removed
    // or stops actually filtering.

    @Test
    fun `given CardFunction criterion when applyAdvancedFilters then only cards with a matching tag pass`() = runTest {
        val entries = listOf(
            buildEntry(
                scryfallId = "id-001", name = "Rampant Growth",
                tags = listOf(com.mmg.manahub.core.model.CardTag("ramp", com.mmg.manahub.core.model.TagCategory.ARCHETYPE)),
            ),
            buildEntry(
                scryfallId = "id-002", name = "Lightning Bolt",
                tags = emptyList(),
            ),
        )
        viewModel = buildViewModel(entries)
        advanceUntilIdle()

        val query = AdvancedSearchQuery(
            criteria = listOf(SearchCriterion.CardFunction(setOf("ramp"), matchAll = false))
        )
        viewModel.applyAdvancedFilters(query)
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.cards.size)
        assertEquals("Rampant Growth", viewModel.uiState.value.cards.first().card.name)
    }

    @Test
    fun `given CardFunction match-any criterion when applyAdvancedFilters then a card matching either function passes`() = runTest {
        val entries = listOf(
            buildEntry(
                scryfallId = "id-001", name = "Board Wipe Card",
                tags = listOf(com.mmg.manahub.core.model.CardTag("board_wipe", com.mmg.manahub.core.model.TagCategory.ROLE)),
            ),
            buildEntry(
                scryfallId = "id-002", name = "Tutor Card",
                tags = listOf(com.mmg.manahub.core.model.CardTag("tutor", com.mmg.manahub.core.model.TagCategory.ROLE)),
            ),
            buildEntry(
                scryfallId = "id-003", name = "Unrelated Card",
                tags = emptyList(),
            ),
        )
        viewModel = buildViewModel(entries)
        advanceUntilIdle()

        val query = AdvancedSearchQuery(
            criteria = listOf(
                SearchCriterion.CardFunction(setOf("board-wipe", "tutor"), matchAll = false)
            )
        )
        viewModel.applyAdvancedFilters(query)
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.cards.size)
        assertTrue(viewModel.uiState.value.cards.none { it.card.name == "Unrelated Card" })
    }

    @Test
    fun `given CardFunction match-all criterion when applyAdvancedFilters then only a card with both tags passes`() = runTest {
        val bothTags = listOf(
            com.mmg.manahub.core.model.CardTag("ramp", com.mmg.manahub.core.model.TagCategory.ARCHETYPE),
            com.mmg.manahub.core.model.CardTag("tutor", com.mmg.manahub.core.model.TagCategory.ROLE),
        )
        val entries = listOf(
            buildEntry(scryfallId = "id-001", name = "Ramp And Tutor", tags = bothTags),
            buildEntry(
                scryfallId = "id-002", name = "Ramp Only",
                tags = listOf(com.mmg.manahub.core.model.CardTag("ramp", com.mmg.manahub.core.model.TagCategory.ARCHETYPE)),
            ),
        )
        viewModel = buildViewModel(entries)
        advanceUntilIdle()

        val query = AdvancedSearchQuery(
            criteria = listOf(
                SearchCriterion.CardFunction(setOf("ramp", "tutor"), matchAll = true)
            )
        )
        viewModel.applyAdvancedFilters(query)
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.cards.size)
        assertEquals("Ramp And Tutor", viewModel.uiState.value.cards.first().card.name)
    }

    @Test
    fun `given CardFunction value with no local tag equivalent when applyAdvancedFilters then no card matches`() = runTest {
        // "cantrip" has an empty collectionTagKeys set (CardFunctionOption) — Scryfall-search-only,
        // no local tag captures the concept. This must NOT fall through to the `else -> true`
        // catch-all and match everything.
        val entries = listOf(
            buildEntry(
                scryfallId = "id-001", name = "Some Card",
                tags = listOf(com.mmg.manahub.core.model.CardTag("ramp", com.mmg.manahub.core.model.TagCategory.ARCHETYPE)),
            ),
        )
        viewModel = buildViewModel(entries)
        advanceUntilIdle()

        val query = AdvancedSearchQuery(
            criteria = listOf(SearchCriterion.CardFunction(setOf("cantrip"), matchAll = false))
        )
        viewModel.applyAdvancedFilters(query)
        advanceUntilIdle()

        assertEquals(0, viewModel.uiState.value.cards.size)
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
    //  GROUP 8 — groupByCard: multiple copies collapse into one group PER SET
    //  (Card Versions & Languages, Phase 1C)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given two copies of same printing when collection loads then they are grouped into one entry`() = runTest {
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

    @Test
    fun `given same identity and set but different printings when collection loads then they collapse into one set entry`() = runTest {
        // ps11 (es) does not exist here — this simulates 10e (en) + 10e (fr): same
        // identity (name fallback, oracleId blank), same set, different scryfallId/language.
        val entries = listOf(
            TestFixtures.buildUserCardWithCard(
                userCard = TestFixtures.buildUserCard(id = "uc-001", scryfallId = "10e-en", quantity = 1, language = "en", createdAt = 1_000L),
                card     = TestFixtures.buildCard("10e-en", name = "Venerable Monk", setCode = "10e"),
            ),
            TestFixtures.buildUserCardWithCard(
                userCard = TestFixtures.buildUserCard(id = "uc-002", scryfallId = "10e-fr", quantity = 1, language = "fr", createdAt = 2_000L),
                card     = TestFixtures.buildCard("10e-fr", name = "Venerable Monk", setCode = "10e"),
            ),
        )
        viewModel = buildViewModel(entries)
        advanceUntilIdle()

        // One item for the "10e" set, aggregating both language variants
        assertEquals(1, viewModel.uiState.value.cards.size)
        val group = viewModel.uiState.value.cards.first()
        assertEquals(2, group.totalQuantity)
        assertEquals(2, group.distinctCopies)
        // Representative print is the FIRST-added copy in that set (edge-case audit A6,
        // 2026-07-15) — a "most recent" representative churned the group's displayed
        // image/price/nav-target on every additional copy added.
        assertEquals("10e-en", group.card.scryfallId)
    }

    @Test
    fun `given same identity but different sets when collection loads then each set gets its own entry`() = runTest {
        val entries = listOf(
            TestFixtures.buildUserCardWithCard(
                userCard = TestFixtures.buildUserCard(id = "uc-001", scryfallId = "ps11-es", quantity = 1, language = "es"),
                card     = TestFixtures.buildCard("ps11-es", name = "Venerable Monk", setCode = "ps11"),
            ),
            TestFixtures.buildUserCardWithCard(
                userCard = TestFixtures.buildUserCard(id = "uc-002", scryfallId = "10e-en", quantity = 1, language = "en"),
                card     = TestFixtures.buildCard("10e-en", name = "Venerable Monk", setCode = "10e"),
            ),
        )
        viewModel = buildViewModel(entries)
        advanceUntilIdle()

        // Two entries — one per set — even though both share the same card identity
        assertEquals(2, viewModel.uiState.value.cards.size)
        val setCodes = viewModel.uiState.value.cards.map { it.card.setCode }.toSet()
        assertEquals(setOf("ps11", "10e"), setCodes)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 9 — Error handling
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given collection flow throws when observeCollection then error is set in state`() = runTest {
        // Arrange: configure the throwing flow before building so that buildViewModel
        // does NOT override it (overrideCollection = false).
        every { getCollection() } returns kotlinx.coroutines.flow.flow {
            throw RuntimeException("DB error")
        }

        viewModel = buildViewModel(overrideCollection = false)
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isLoading)
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

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP — Broken-image fix (2026-07-17): non-English groups show the cached
    //  English sibling's image; English groups and uncached foreign groups are untouched.
    // ══════════════════════════════════════════════════════════════════════════

    private fun buildEntryWithCard(
        card: com.mmg.manahub.core.model.Card,
        id: String = card.scryfallId,
        createdAt: Long = 1_000L,
    ) = TestFixtures.buildUserCardWithCard(
        userCard = TestFixtures.buildUserCard(id = id, scryfallId = card.scryfallId, createdAt = createdAt),
        card = card,
    )

    @Test
    fun `given non-English group when cached English sibling exists then group image is overridden but other fields keep the representative printing`() = runTest {
        val foreignCard = TestFixtures.buildCard(scryfallId = "es-001", name = "Rayo").copy(
            lang = "es",
            setCode = "lea",
            collectorNumber = "5",
            imageNormal = null,
            imageArtCrop = null,
        )
        val englishSibling = TestFixtures.buildCard(scryfallId = "en-001", name = "Lightning Bolt").copy(
            lang = "en",
            setCode = "lea",
            collectorNumber = "5",
            imageNormal = "https://example.com/english-normal.jpg",
            imageArtCrop = "https://example.com/english-art.jpg",
        )
        coEvery { cardRepository.getCachedEnglishSiblings(setOf("lea" to "5")) } returns
            mapOf(("lea" to "5") to englishSibling)

        viewModel = buildViewModel(listOf(buildEntryWithCard(foreignCard)))
        advanceUntilIdle()

        val group = viewModel.uiState.value.cards.single()
        // Image fields come from the English sibling.
        assertEquals("https://example.com/english-normal.jpg", group.card.imageNormal)
        assertEquals("https://example.com/english-art.jpg", group.card.imageArtCrop)
        // Every other field still comes from the actual (foreign) representative printing.
        assertEquals("Rayo", group.card.name)
        assertEquals("es", group.card.lang)
        assertEquals("lea", group.card.setCode)
    }

    @Test
    fun `given non-English group when no cached English sibling exists then group keeps its own image`() = runTest {
        val foreignCard = TestFixtures.buildCard(scryfallId = "es-002", name = "Contrahechizo").copy(
            lang = "es",
            setCode = "lea",
            collectorNumber = "9",
            imageNormal = "https://example.com/spanish-fallback.jpg",
            imageArtCrop = null,
        )
        coEvery { cardRepository.getCachedEnglishSiblings(any()) } returns emptyMap()

        viewModel = buildViewModel(listOf(buildEntryWithCard(foreignCard)))
        advanceUntilIdle()

        val group = viewModel.uiState.value.cards.single()
        assertEquals("https://example.com/spanish-fallback.jpg", group.card.imageNormal)
        assertNull(group.card.imageArtCrop)
    }

    @Test
    fun `given only English-language groups when loaded then the sibling repository is never queried`() = runTest {
        val englishCard = TestFixtures.buildCard(scryfallId = "en-005", name = "Lightning Bolt").copy(
            lang = "en",
            setCode = "lea",
            collectorNumber = "1",
            imageNormal = "https://example.com/original.jpg",
        )

        viewModel = buildViewModel(listOf(buildEntryWithCard(englishCard)))
        advanceUntilIdle()

        val group = viewModel.uiState.value.cards.single()
        assertEquals("https://example.com/original.jpg", group.card.imageNormal)
        // No non-English pairs in the raw collection → the batch lookup is skipped entirely,
        // not just called with an empty set (avoids a pointless Room round-trip).
        coVerify(exactly = 0) { cardRepository.getCachedEnglishSiblings(any()) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP — Dynamic grouping (onGroupingChange)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given default state when onGroupingChange to TYPE then groupingMode updates immediately`() = runTest {
        viewModel = buildViewModel()
        advanceUntilIdle()
        assertEquals(CollectionGroupingMode.NONE, viewModel.uiState.value.groupingMode)

        viewModel.onGroupingChange(CollectionGroupingMode.TYPE)
        advanceUntilIdle()

        assertEquals(CollectionGroupingMode.TYPE, viewModel.uiState.value.groupingMode)
    }

    @Test
    fun `given onGroupingChange then the selection is persisted and logged`() = runTest {
        viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.onGroupingChange(CollectionGroupingMode.COLOR)
        advanceUntilIdle()

        coVerify { userPreferencesRepository.saveCollectionGroupingMode(CollectionGroupingMode.COLOR) }
        verify {
            analyticsHelper.logEvent("collection_grouping_changed", mapOf("grouping_mode" to "COLOR"))
        }
    }

    @Test
    fun `given cards in collection when onGroupingChange to TYPE then sections are populated`() = runTest {
        val entries = listOf(
            buildEntry(scryfallId = "id-001", name = "Lightning Bolt", typeLine = "Instant"),
            buildEntry(scryfallId = "id-002", name = "Grizzly Bears", typeLine = "Creature — Bear"),
        )
        viewModel = buildViewModel(entries)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.sections.isEmpty())

        viewModel.onGroupingChange(CollectionGroupingMode.TYPE)
        advanceUntilIdle()

        val sections = viewModel.uiState.value.sections
        assertEquals(2, sections.size)
        val tokens = sections.map { it.labelToken }.toSet()
        assertTrue(tokens.contains("Instants"))
        assertTrue(tokens.contains("Creatures"))
    }

    @Test
    fun `given TYPE grouping when onGroupingChange back to NONE then sections are cleared`() = runTest {
        val entries = listOf(buildEntry(scryfallId = "id-001", name = "Lightning Bolt", typeLine = "Instant"))
        viewModel = buildViewModel(entries)
        advanceUntilIdle()

        viewModel.onGroupingChange(CollectionGroupingMode.TYPE)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.sections.isNotEmpty())

        viewModel.onGroupingChange(CollectionGroupingMode.NONE)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.sections.isEmpty())
    }
}

