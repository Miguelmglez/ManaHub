package com.mmg.manahub.feature.communitydecks.presentation

import androidx.lifecycle.SavedStateHandle
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.domain.repository.CommunityAggregateRepository
import com.mmg.manahub.core.domain.usecase.card.SearchCardsUseCase
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.CommunityDeckOwner
import com.mmg.manahub.core.model.CommunityDeckSearchResult
import com.mmg.manahub.core.model.CommunityDeckSummary
import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.model.PaginatedCards
import com.mmg.manahub.feature.communitydecks.domain.usecase.SearchCommunityDecksUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
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
 * Regression tests for [CommunityDecksSearchViewModel]'s job-cancellation fixes (post-review
 * hardening): before this fix, [CommunityDecksSearchViewModel.loadMore] and
 * [CommunityDecksSearchViewModel.loadDiscover] never tracked/cancelled their own [kotlinx.coroutines.Job],
 * so a slower in-flight fetch for a query/filter/format the user had already moved on from could
 * resolve AFTER the newer one and silently overwrite/append onto its results. Also covers the
 * [CommunityDecksSearchViewModel.onClearAdvancedFilters] fix for the card-picker debounce flows.
 *
 * Kept as its OWN focused file (rather than added to `CommunityDecksSearchViewModelTest.kt`)
 * because that file currently has extensive PRE-EXISTING compile errors unrelated to this task
 * (stale references to renamed/removed ViewModel and model APIs — see the fix notes for this task)
 * and does not compile; this file is intentionally self-contained so it is not blocked by that.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CommunityDecksSearchViewModelConcurrencyTest {

    private val testDispatcher = StandardTestDispatcher()

    private val searchUseCase: SearchCommunityDecksUseCase = mockk()
    private val userPreferences: UserPreferencesDataStore = mockk()
    private val communityAggregateRepository: CommunityAggregateRepository = mockk(relaxed = true)
    private val communityEngineEnabledFlow = MutableStateFlow(false)
    private val cardRepository: CardRepository = mockk()
    private val searchCards: SearchCardsUseCase = mockk()

    private fun fakeCard(name: String, id: String = name) = Card(
        scryfallId = id, name = name, printedName = null,
        manaCost = "{2}{U}", cmc = 3.0, colors = listOf("U"), colorIdentity = listOf("U"),
        typeLine = "Creature", printedTypeLine = null, oracleText = null, printedText = null,
        keywords = emptyList(), power = "1", toughness = "1", loyalty = null,
        setCode = "TST", setName = "Test Set", collectorNumber = "1", rarity = "common",
        releasedAt = "2025-01-01", frameEffects = emptyList(), promoTypes = emptyList(),
        lang = "en", imageNormal = null, imageArtCrop = null, imageBackNormal = null,
        priceUsd = null, priceUsdFoil = null, priceEur = null, priceEurFoil = null,
        legalityStandard = "legal", legalityPioneer = "legal", legalityModern = "legal",
        legalityCommander = "legal", flavorText = null, artist = null,
        scryfallUri = "https://scryfall.com/$id",
    )

    private fun fakeSummary(id: Int, name: String = "Deck $id") = CommunityDeckSummary(
        archidektId = id,
        name = name,
        size = 100,
        format = "commander",
        deckFormatId = 3,
        owner = CommunityDeckOwner(id = 1, username = "user", avatarUrl = ""),
        viewCount = 10,
        createdAt = "2024-01-01",
        updatedAt = "2024-01-01",
        colorIdentity = emptyList(),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic(FirebaseCrashlytics::class)
        every { FirebaseCrashlytics.getInstance() } returns mockk(relaxed = true)
        every { userPreferences.communityEngineEnabledFlow } returns communityEngineEnabledFlow
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(FirebaseCrashlytics::class)
    }

    private fun createViewModel(): CommunityDecksSearchViewModel {
        val handle = SavedStateHandle()
        return CommunityDecksSearchViewModel(
            handle, searchUseCase, userPreferences, communityAggregateRepository, cardRepository, searchCards,
        )
    }

    @Test
    fun `given loadMore in flight when a new search resolves first then the stale page is never appended`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        // First search (page 1) succeeds immediately and reports a further page available.
        coEvery { searchUseCase(match { it.page == 1 && it.deckName == "Sol Ring" }) } returns DataResult.Success(
            CommunityDeckSearchResult(totalCount = 2, hasMore = true, decks = listOf(fakeSummary(1))),
        )
        vm.onQueryChange("Sol Ring")
        vm.search()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.hasMore)

        // loadMore() (page 2) is SLOW — it will not resolve until virtual time advances past 1000ms.
        coEvery { searchUseCase(match { it.page == 2 }) } coAnswers {
            delay(1_000)
            DataResult.Success(
                CommunityDeckSearchResult(totalCount = 2, hasMore = false, decks = listOf(fakeSummary(999, "Stale page 2"))),
            )
        }
        vm.loadMore()

        // Before the stale page 2 fetch resolves, the user changes the query and searches again —
        // this new search resolves fast.
        coEvery { searchUseCase(match { it.page == 1 && it.deckName == "Lightning Bolt" }) } returns DataResult.Success(
            CommunityDeckSearchResult(totalCount = 1, hasMore = false, decks = listOf(fakeSummary(555, "Fresh result"))),
        )
        vm.onQueryChange("Lightning Bolt")
        vm.search()

        // Advances virtual time past the stale loadMore's delay(1_000) too — if loadMore's job
        // were NOT cancelled, its coroutine would resume here and append its stale deck.
        advanceUntilIdle()

        assertEquals(listOf(555), vm.uiState.value.results.map { it.archidektId })
        // A stuck spinner (from the cancelled loadMore never reaching its own completion update)
        // must not survive the new search either.
        assertEquals(false, vm.uiState.value.isLoadingMore)
    }

    @Test
    fun `given rapid discover format switches when the older format resolves later then the newer format wins`() = runTest {
        communityEngineEnabledFlow.value = true
        val vm = createViewModel()
        advanceUntilIdle() // initial auto-load (default COMMANDER format) settles first.

        // STANDARD's response is SLOW.
        coEvery {
            searchUseCase(match { it.deckFormatId == CommunityDeckFormatFilter.STANDARD.apiId })
        } coAnswers {
            delay(1_000)
            DataResult.Success(
                CommunityDeckSearchResult(totalCount = 1, hasMore = false, decks = listOf(fakeSummary(1, "Standard deck"))),
            )
        }
        vm.onSelectDiscoveryFormat(CommunityDeckFormatFilter.STANDARD)

        // Before STANDARD resolves, the user switches to MODERN, which resolves fast.
        coEvery {
            searchUseCase(match { it.deckFormatId == CommunityDeckFormatFilter.MODERN.apiId })
        } returns DataResult.Success(
            CommunityDeckSearchResult(totalCount = 1, hasMore = false, decks = listOf(fakeSummary(2, "Modern deck"))),
        )
        vm.onSelectDiscoveryFormat(CommunityDeckFormatFilter.MODERN)

        // Advances virtual time past STANDARD's delay(1_000) too — if the earlier loadDiscover job
        // were NOT cancelled, its stale response would land after and overwrite MODERN's results.
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(CommunityDeckFormatFilter.MODERN, state.selectedDiscoveryFormat)
        assertEquals(listOf(2), state.popularDecks.map { it.archidektId })
        assertEquals(listOf(2), state.recentDecks.map { it.archidektId })
    }

    @Test
    fun `given onClearAdvancedFilters when the same commander query is re-entered then it searches again`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        val krenko = fakeCard("Krenko, Mob Boss", id = "krenko")
        coEvery { searchCards("Krenko", 1) } returns DataResult.Success(
            PaginatedCards(cards = listOf(krenko), hasMore = false, totalCards = 1),
        )

        vm.onCommanderQueryChange("Krenko")
        advanceUntilIdle()
        assertEquals(listOf(krenko), vm.uiState.value.commanderResults)

        vm.onCommanderSelected(krenko)
        vm.onClearAdvancedFilters()
        // Let the blank reset's debounce window settle (mirrors a real user pausing after the
        // field visually clears) BEFORE re-entering the same text — without this gap, `debounce`
        // itself would coalesce the blank reset and the re-entry into a single emission, which
        // would mask whether distinctUntilChanged's baseline was actually reset.
        advanceUntilIdle()

        // Re-enter the EXACT same query text after clearing. Before the fix, the backing
        // MutableStateFlow still held "Krenko" from before the clear, so this identical value
        // never re-emitted (StateFlow conflation + distinctUntilChanged) and commanderResults
        // stayed empty.
        vm.onCommanderQueryChange("Krenko")
        advanceUntilIdle()

        assertEquals(listOf(krenko), vm.uiState.value.commanderResults)
    }
}
