package com.mmg.manahub.feature.carddetail.presentation

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.core.domain.auth.AuthRepository
import com.mmg.manahub.core.domain.auth.SessionState
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.domain.repository.DeckRepository
import com.mmg.manahub.core.domain.repository.OpenForTradeRepository
import com.mmg.manahub.core.domain.repository.UpdateEntryOutcome
import com.mmg.manahub.core.domain.repository.UserCardRepository
import com.mmg.manahub.core.domain.repository.UserPreferencesRepository
import com.mmg.manahub.core.domain.repository.WishlistRepository
import com.mmg.manahub.core.domain.usecase.card.RefreshCardStrategyTagsUseCase
import com.mmg.manahub.core.domain.usecase.collection.AddCardToCollectionUseCase
import com.mmg.manahub.core.domain.usecase.collection.UpdateCollectionEntryUseCase
import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.util.AnalyticsHelper
import com.mmg.manahub.feature.trades.domain.usecase.AddToWishlistUseCase
import com.mmg.manahub.feature.trades.domain.usecase.UpdateWishlistEntryUseCase
import com.mmg.manahub.util.TestFixtures
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [CardDetailViewModel] — Card Versions & Languages, Phase 1B.
 *
 * Focused on the language selector + entry-update-outcome paths (the new surfaces added by this
 * feature); the pre-existing add/wishlist/trade flows are not re-covered here. VM has a large
 * constructor graph (14 deps) — everything not directly relevant to a given test is a relaxed
 * mock with a safe no-op/empty-flow default set up in [buildViewModel].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CardDetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    // ── Mocks ─────────────────────────────────────────────────────────────────

    private val cardRepo                  = mockk<CardRepository>(relaxed = true)
    private val userCardRepo              = mockk<UserCardRepository>(relaxed = true)
    private val deckRepo                  = mockk<DeckRepository>(relaxed = true)
    private val addToCollection           = mockk<AddCardToCollectionUseCase>(relaxed = true)
    private val addToWishlistUseCase      = mockk<AddToWishlistUseCase>(relaxed = true)
    private val wishlistRepo              = mockk<WishlistRepository>(relaxed = true)
    private val openForTradeRepo          = mockk<OpenForTradeRepository>(relaxed = true)
    private val userPrefs                 = mockk<UserPreferencesRepository>(relaxed = true)
    private val authRepository            = mockk<AuthRepository>(relaxed = true)
    private val helper                    = mockk<AnalyticsHelper>(relaxed = true)
    private val updateCollectionEntry     = mockk<UpdateCollectionEntryUseCase>()
    private val updateWishlistEntry       = mockk<UpdateWishlistEntryUseCase>()
    private val refreshCardStrategyTags   = mockk<RefreshCardStrategyTagsUseCase>(relaxed = true)

    private lateinit var viewModel: CardDetailViewModel

    private val initialScryfallId = "card-initial"

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildViewModel(card: com.mmg.manahub.core.model.Card = TestFixtures.buildCard(scryfallId = initialScryfallId, setCode = "lea")): CardDetailViewModel {
        coEvery { cardRepo.getCardById(initialScryfallId) } returns DataResult.Success(card)
        every { cardRepo.observeCard(any()) } returns flowOf(null)
        every { deckRepo.observeDecksContainingCard(any()) } returns flowOf(emptyList())
        every { userCardRepo.observeVersionsByOracle(any(), any(), any()) } returns flowOf(emptyList())
        every { wishlistRepo.observeVersionsByOracle(any(), any()) } returns flowOf(emptyList())
        every { openForTradeRepo.observeVersionsByOracle(any(), any()) } returns flowOf(emptyList())
        every { userPrefs.userDefinedTagsFlow } returns flowOf(emptyList())
        every { authRepository.sessionState } returns MutableStateFlow(SessionState.Unauthenticated)

        return CardDetailViewModel(
            savedStateHandle          = SavedStateHandle(mapOf("scryfallId" to initialScryfallId)),
            cardRepo                  = cardRepo,
            userCardRepo              = userCardRepo,
            deckRepo                  = deckRepo,
            addToCollection           = addToCollection,
            addToWishlistUseCase      = addToWishlistUseCase,
            wishlistRepo              = wishlistRepo,
            openForTradeRepo          = openForTradeRepo,
            userPrefs                 = userPrefs,
            authRepository            = authRepository,
            helper                    = helper,
            updateCollectionEntry     = updateCollectionEntry,
            updateWishlistEntry       = updateWishlistEntry,
            refreshCardStrategyTags   = refreshCardStrategyTags,
        )
    }

    // ── Setup / Teardown ─────────────────────────────────────────────────────

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic(FirebaseCrashlytics::class)
        val crashlytics = mockk<FirebaseCrashlytics>(relaxed = true)
        every { FirebaseCrashlytics.getInstance() } returns crashlytics
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(FirebaseCrashlytics::class)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 1 — Language selector: onOpenLanguageSelector
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given getLanguagePrints succeeds when onOpenLanguageSelector then languagePrints is populated`() = runTest {
        viewModel = buildViewModel()
        advanceUntilIdle()

        val prints = listOf(
            TestFixtures.buildCard(scryfallId = "print-en", setCode = "lea"),
            TestFixtures.buildCard(scryfallId = "print-es", setCode = "lea"),
        )
        coEvery { cardRepo.getLanguagePrints("lea", "161") } returns DataResult.Success(prints)

        viewModel.onOpenLanguageSelector()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.showLanguageSelector)
        assertEquals(prints, state.languagePrints)
        assertEquals(false, state.isLoadingLanguages)
    }

    @Test
    fun `given getLanguagePrints fails when onOpenLanguageSelector then languagePrints is empty and loading is cleared`() = runTest {
        viewModel = buildViewModel()
        advanceUntilIdle()

        coEvery { cardRepo.getLanguagePrints("lea", "161") } returns DataResult.Error("SCRYFALL_404")

        viewModel.onOpenLanguageSelector()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        // Empty/error both fall back to the full language list in the UI layer — the VM state
        // just needs to be a clean "nothing loaded, not loading" so the UI can render the fallback.
        assertTrue(state.showLanguageSelector)
        assertTrue(state.languagePrints.isEmpty())
        assertEquals(false, state.isLoadingLanguages)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 2 — onSelectLanguagePrint
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given the same printing is re-selected when onSelectLanguagePrint then it only dismisses without reloading the card`() = runTest {
        viewModel = buildViewModel()
        advanceUntilIdle()
        // Initial load already called getCardById once.
        val currentCard = viewModel.uiState.value.card!!
        assertEquals(initialScryfallId, currentCard.scryfallId)

        viewModel.onSelectLanguagePrint(currentCard)
        advanceUntilIdle()

        assertEquals(false, viewModel.uiState.value.showLanguageSelector)
        // No re-key => no second getCardById call for the same printing.
        coVerify(exactly = 1) { cardRepo.getCardById(initialScryfallId) }
    }

    @Test
    fun `given a different printing when onSelectLanguagePrint then the displayed printing changes`() = runTest {
        viewModel = buildViewModel()
        advanceUntilIdle()

        val otherCard = TestFixtures.buildCard(scryfallId = "print-other", setCode = "lea")
        coEvery { cardRepo.getCardById("print-other") } returns DataResult.Success(otherCard)

        viewModel.onSelectLanguagePrint(otherCard)
        advanceUntilIdle()

        assertEquals(false, viewModel.uiState.value.showLanguageSelector)
        assertEquals("print-other", viewModel.uiState.value.card?.scryfallId)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 3 — onSelectFallbackLanguage
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given the current language is re-selected when onSelectFallbackLanguage then it only dismisses without a toast`() = runTest {
        viewModel = buildViewModel()
        advanceUntilIdle()
        // Fixture card's lang defaults to "en".
        assertEquals("en", viewModel.uiState.value.card?.lang)

        viewModel.events.test {
            viewModel.onSelectFallbackLanguage("en")
            advanceUntilIdle()
            expectNoEvents()
        }
        assertEquals(false, viewModel.uiState.value.showLanguageSelector)
    }

    @Test
    fun `given a different language when onSelectFallbackLanguage then an info toast is emitted`() = runTest {
        viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.events.test {
            viewModel.onSelectFallbackLanguage("de")
            advanceUntilIdle()
            val event = awaitItem()
            assertTrue(event is CardDetailEvent.ShowToast)
            assertEquals(ToastSeverity.INFO, (event as CardDetailEvent.ShowToast).severity)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 4 — onUpdateCollectionEntry outcome handling
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given updateCollectionEntry reports ENTRY_NOT_FOUND when onUpdateCollectionEntry then an error toast is emitted, not a success toast`() = runTest {
        viewModel = buildViewModel()
        advanceUntilIdle()

        val entry = TestFixtures.buildUserCardWithCard(
            userCard = TestFixtures.buildUserCard(id = "entry-1", scryfallId = initialScryfallId),
            card = viewModel.uiState.value.card!!,
        )
        viewModel.onEditCollectionEntry(entry)
        coEvery {
            updateCollectionEntry(
                entryId = "entry-1",
                newScryfallId = any(),
                isFoil = any(),
                condition = any(),
                language = any(),
                quantity = any(),
                userId = any(),
            )
        } returns DataResult.Success(UpdateEntryOutcome.ENTRY_NOT_FOUND)

        viewModel.events.test {
            viewModel.onUpdateCollectionEntry(isFoil = false, condition = "NM", language = "en", quantity = 1)
            advanceUntilIdle()
            val event = awaitItem()
            assertTrue(event is CardDetailEvent.ShowToast)
            val toast = event as CardDetailEvent.ShowToast
            assertEquals(ToastSeverity.ERROR, toast.severity)
            assertEquals("This entry no longer exists", toast.message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given updateCollectionEntry succeeds when onUpdateCollectionEntry then a success toast is emitted`() = runTest {
        viewModel = buildViewModel()
        advanceUntilIdle()

        val entry = TestFixtures.buildUserCardWithCard(
            userCard = TestFixtures.buildUserCard(id = "entry-1", scryfallId = initialScryfallId),
            card = viewModel.uiState.value.card!!,
        )
        viewModel.onEditCollectionEntry(entry)
        coEvery {
            updateCollectionEntry(
                entryId = "entry-1",
                newScryfallId = any(),
                isFoil = any(),
                condition = any(),
                language = any(),
                quantity = any(),
                userId = any(),
            )
        } returns DataResult.Success(UpdateEntryOutcome.UPDATED)

        viewModel.events.test {
            viewModel.onUpdateCollectionEntry(isFoil = false, condition = "NM", language = "en", quantity = 1)
            advanceUntilIdle()
            val event = awaitItem()
            assertTrue(event is CardDetailEvent.ShowToast)
            val toast = event as CardDetailEvent.ShowToast
            assertEquals(ToastSeverity.SUCCESS, toast.severity)
            assertEquals("Entry updated", toast.message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP — Deck Engine Unification plan, D8, §5 Phase 5c: precomputed strategy tags
    //  (auto-generated tags must surface for ANY viewed card, owned or not)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given a card with a non-blank oracleId when loadCard then refreshCardStrategyTags is invoked`() = runTest {
        val card = TestFixtures.buildCard(scryfallId = initialScryfallId, setCode = "lea")
            .copy(oracleId = "oracle-123")
        viewModel = buildViewModel(card = card)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            refreshCardStrategyTags(initialScryfallId, "oracle-123", card.tags)
        }
    }

    @Test
    fun `given an unowned card with precomputed tags available when loadCard then observeCard reflects the merged tags`() = runTest {
        // Unowned in the sense that the card was never previously cached with confirmed tags —
        // loadCard() still calls getCardById/observeCard for ANY viewed card regardless of
        // collection ownership, so the merged tags surface here exactly the same way they would
        // for an owned card.
        val card = TestFixtures.buildCard(scryfallId = initialScryfallId, setCode = "lea")
            .copy(oracleId = "oracle-123", tags = emptyList())
        val enriched = card.copy(tags = listOf(CardTag.REMOVAL))

        viewModel = buildViewModel(card = card)
        // Override AFTER buildViewModel() so this specific-id stub isn't shadowed by its blanket
        // `observeCard(any()) -> flowOf(null)` default — nothing collects yet under
        // StandardTestDispatcher until advanceUntilIdle() below.
        every { cardRepo.observeCard(initialScryfallId) } returns flowOf(enriched)
        advanceUntilIdle()

        assertEquals(listOf(CardTag.REMOVAL), viewModel.uiState.value.card?.tags)
    }

    @Test
    fun `given refreshCardStrategyTags throws when loadCard then the card still loads successfully`() = runTest {
        val card = TestFixtures.buildCard(scryfallId = initialScryfallId, setCode = "lea")
            .copy(oracleId = "oracle-123")
        coEvery { refreshCardStrategyTags(any(), any(), any()) } throws RuntimeException("offline")

        viewModel = buildViewModel(card = card)
        advanceUntilIdle()

        assertEquals(card.scryfallId, viewModel.uiState.value.card?.scryfallId)
        assertTrue(viewModel.uiState.value.error == null)
    }

    @Test
    fun `given a card with a blank oracleId when loadCard then refreshCardStrategyTags is never invoked`() = runTest {
        // Blank-oracleId cards route through the pre-existing refreshCardById backfill instead
        // (edge-case audit A3) — the two background refreshes are mutually exclusive.
        val card = TestFixtures.buildCard(scryfallId = initialScryfallId, setCode = "lea")
        viewModel = buildViewModel(card = card)
        advanceUntilIdle()

        coVerify(exactly = 0) { refreshCardStrategyTags(any(), any(), any()) }
        coVerify(exactly = 1) { cardRepo.refreshCardById(initialScryfallId) }
    }
}
