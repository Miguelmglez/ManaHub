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
import com.mmg.manahub.core.model.TagCategory
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
import io.mockk.verify
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

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP — load failure + retry
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given getCardById fails on the first load then error is set and no card is shown`() = runTest {
        viewModel = buildViewModel()
        coEvery { cardRepo.getCardById(initialScryfallId) } returns DataResult.Error("SCRYFALL_404")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(null, state.card)
        assertEquals(false, state.isLoading)
        assertEquals("SCRYFALL_404", state.error)
    }

    @Test
    fun `given the first load failed when onRetryLoad and the fetch now succeeds then the card loads and error clears`() = runTest {
        viewModel = buildViewModel()
        coEvery { cardRepo.getCardById(initialScryfallId) } returns DataResult.Error("offline")
        advanceUntilIdle()
        assertEquals("offline", viewModel.uiState.value.error)

        val card = TestFixtures.buildCard(scryfallId = initialScryfallId, setCode = "lea")
        coEvery { cardRepo.getCardById(initialScryfallId) } returns DataResult.Success(card)
        viewModel.onRetryLoad()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(initialScryfallId, state.card?.scryfallId)
        assertEquals(null, state.error)
        assertEquals(false, state.isLoading)
        coVerify(exactly = 2) { cardRepo.getCardById(initialScryfallId) }
    }

    @Test
    fun `given a loaded card when a later print switch fails then the loaded print is kept, writes target it and an error toast is emitted`() = runTest {
        viewModel = buildViewModel()
        advanceUntilIdle()
        val missingPrint = TestFixtures.buildCard(scryfallId = "print-missing", setCode = "lea")
        coEvery { cardRepo.getCardById("print-missing") } returns DataResult.Error("offline")

        viewModel.events.test {
            viewModel.onSelectLanguagePrint(missingPrint)
            advanceUntilIdle()
            val toast = awaitItem() as CardDetailEvent.ShowToast
            assertEquals(ToastSeverity.ERROR, toast.severity)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(initialScryfallId, viewModel.uiState.value.card?.scryfallId)
        assertEquals(null, viewModel.uiState.value.error)

        viewModel.onAddUserTag(CardTag.REMOVAL)
        advanceUntilIdle()
        coVerify(exactly = 1) { cardRepo.updateUserTags(initialScryfallId, any()) }
        coVerify(exactly = 0) { cardRepo.updateUserTags("print-missing", any()) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP — user tags
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given a custom tag created under a built-in category then the card gets it under that category`() = runTest {
        viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.onSaveAndAddCustomTag("My Ramp", TagCategory.STRATEGY.name)
        advanceUntilIdle()

        assertEquals(listOf(CardTag("my_ramp", TagCategory.STRATEGY)), viewModel.uiState.value.card?.userTags)
    }

    @Test
    fun `given a user tag already on the card when the same key is added under another category then it is not duplicated`() = runTest {
        val card = TestFixtures.buildCard(
            scryfallId = initialScryfallId,
            userTags = listOf(CardTag("my_ramp", TagCategory.CUSTOM)),
        )
        viewModel = buildViewModel(card = card)
        advanceUntilIdle()

        viewModel.onAddUserTag(CardTag("my_ramp", TagCategory.STRATEGY))
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.card?.userTags?.size)
        coVerify(exactly = 0) { cardRepo.updateUserTags(any(), any()) }
    }

    @Test
    fun `given a custom tag is saved then analytics receive only the label length, never the free text`() = runTest {
        viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.onSaveAndAddCustomTag("Secret Tech", "My Private Category")
        advanceUntilIdle()

        verify { helper.logEvent("save_custom_tag", mapOf("label_length" to 11, "category" to "custom")) }
    }

    @Test
    fun `given updateUserTags throws when onAddUserTag then the optimistic tag is rolled back and an error toast is emitted`() = runTest {
        viewModel = buildViewModel()
        advanceUntilIdle()
        coEvery { cardRepo.updateUserTags(any(), any()) } throws RuntimeException("disk full")

        viewModel.events.test {
            viewModel.onAddUserTag(CardTag.REMOVAL)
            advanceUntilIdle()
            val toast = awaitItem() as CardDetailEvent.ShowToast
            assertEquals(ToastSeverity.ERROR, toast.severity)
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(viewModel.uiState.value.card?.userTags.orEmpty().isEmpty())
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP — silent-failure paths now surface a toast
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given deleteCard throws when onDeleteCard then the confirm dialog closes and an error toast is emitted`() = runTest {
        viewModel = buildViewModel()
        advanceUntilIdle()
        viewModel.onRequestDelete(TestFixtures.buildUserCard(id = "uc-1", scryfallId = initialScryfallId))
        coEvery { userCardRepo.deleteCard("uc-1") } throws RuntimeException("db locked")

        viewModel.events.test {
            viewModel.onDeleteCard("uc-1")
            advanceUntilIdle()
            val toast = awaitItem() as CardDetailEvent.ShowToast
            assertEquals(ToastSeverity.ERROR, toast.severity)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(null, viewModel.uiState.value.cardToDelete)
    }

    @Test
    fun `given a trade update fails when onConfirmTradeSelection then an error toast is emitted and the sheet closes`() = runTest {
        val entry = TestFixtures.buildUserCardWithCard(
            userCard = TestFixtures.buildUserCard(id = "uc-1", scryfallId = initialScryfallId),
        )
        viewModel = buildViewModel()
        every { userCardRepo.observeVersionsByOracle(any(), any(), any()) } returns flowOf(listOf(entry))
        coEvery {
            openForTradeRepo.addLocal(any(), any(), any(), any(), any(), any())
        } returns Result.failure(RuntimeException("offline"))
        advanceUntilIdle()
        viewModel.onShowTradeSheet()

        viewModel.events.test {
            viewModel.onConfirmTradeSelection(mapOf("uc-1" to 1))
            advanceUntilIdle()
            val toast = awaitItem() as CardDetailEvent.ShowToast
            assertEquals(ToastSeverity.ERROR, toast.severity)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(false, viewModel.uiState.value.showTradeSheet)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP — variant selection
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given a variant in the displayed language when onSelectVariant from the screen then no language lookup is made`() = runTest {
        viewModel = buildViewModel()
        advanceUntilIdle()
        val variant = TestFixtures.buildCard(scryfallId = "variant-en", setCode = "m10")

        viewModel.events.test {
            viewModel.onSelectVariant(variant)
            advanceUntilIdle()
            assertEquals(CardDetailEvent.NavigateToCard("variant-en"), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 0) { cardRepo.getLanguagePrints("m10", any()) }
    }
}
