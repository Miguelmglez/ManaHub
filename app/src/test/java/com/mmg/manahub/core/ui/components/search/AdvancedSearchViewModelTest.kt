package com.mmg.manahub.core.ui.components.search

import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.data.remote.ScryfallRemoteDataSource
import com.mmg.manahub.core.domain.usecase.search.BuildScryfallQueryUseCase
import com.mmg.manahub.core.model.AdvancedSearchQuery
import com.mmg.manahub.core.model.ColorMatchMode
import com.mmg.manahub.core.model.MagicSet
import com.mmg.manahub.core.model.PreferredCurrency
import com.mmg.manahub.core.model.SearchCriterion
import com.mmg.manahub.core.model.SetType
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Regression suite for the "Advanced Search returns ZERO cards" defect (2026-09-07).
 *
 * This ViewModel is resolved with `koinViewModel()` from `AdvancedSearchSheet`, a fixed-position
 * overlay — so a single instance is shared by every open of the sheet within one navigation
 * destination. The tests below pin the contract that makes that sharing safe: the sheet's form
 * state is seeded from the caller's CURRENTLY APPLIED query on every open, so a criterion the user
 * cannot see can never AND itself onto the next search.
 */
@kotlinx.coroutines.ExperimentalCoroutinesApi
class AdvancedSearchViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val scryfallDataSource = mockk<ScryfallRemoteDataSource>(relaxed = true)
    private val userPreferences = mockk<UserPreferencesDataStore>()

    /** The Analysis tab's "Browse for &lt;Category&gt;" preset shape (SectionSearchQuery output). */
    private val sectionPreset = AdvancedSearchQuery(
        criteria = listOf(
            SearchCriterion.ColorIdentity(setOf("G", "W")),
            SearchCriterion.Format(listOf("commander")),
            SearchCriterion.OracleTerms(allOf = listOf("destroy target")),
            SearchCriterion.ManaProduction(colors = setOf("G"), requireLand = true),
        )
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { userPreferences.preferredCurrencyFlow } returns flowOf(PreferredCurrency.USD)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createVm() = AdvancedSearchViewModel(
        scryfallDataSource = scryfallDataSource,
        buildQuery = BuildScryfallQueryUseCase(),
        userPreferencesDataStore = userPreferences,
    )

    // ── The reported defect ───────────────────────────────────────────────────

    @Test
    fun `re-opening with no applied query drops every criterion from the previous seed`() =
        runTest(dispatcher) {
            val vm = createVm()
            advanceUntilIdle()
            // The user browses a Deck Analysis category: the sheet seeds from the section preset.
            vm.seedFrom(sectionPreset)
            assertEquals(4, vm.uiState.value.currentQuery.criteria.size)

            // The sheet closes and is later re-opened from the Build tab's FAB, where NOTHING is
            // applied any more. Pre-fix, this open ran no seed at all and the four criteria above
            // stayed live but invisible.
            vm.seedFrom(AdvancedSearchQuery())

            val state = vm.uiState.value
            assertTrue("no criterion may survive an unfiltered open", state.currentQuery.criteria.isEmpty())
            assertTrue(state.selectedColors.isEmpty())
            assertTrue(state.selectedFormat.isEmpty())
            assertNull(state.oracleTerms)
            assertNull(state.manaProduction)
        }

    @Test
    fun `picking Card Advantage after an unfiltered open yields exactly one CardFunction criterion`() =
        runTest(dispatcher) {
            val vm = createVm()
            advanceUntilIdle()
            vm.seedFrom(sectionPreset)

            // Re-open with nothing applied, then pick the single "Card Advantage" function.
            vm.seedFrom(AdvancedSearchQuery())
            vm.toggleCardFunction("card-advantage")

            val criteria = vm.uiState.value.currentQuery.criteria
            assertEquals(
                "the search must carry ONLY what the user picked",
                listOf(SearchCriterion.CardFunction(setOf("card-advantage"))),
                criteria,
            )
        }

    @Test
    fun `seeding a section preset still pre-selects its filters`() = runTest(dispatcher) {
        val vm = createVm()
        advanceUntilIdle()

        vm.seedFrom(sectionPreset)

        val state = vm.uiState.value
        assertEquals(setOf("G", "W"), state.selectedColors)
        assertTrue("identity mode must survive the round-trip", state.useColorIdentity)
        assertEquals(listOf("commander"), state.selectedFormat)
        assertEquals(listOf("destroy target"), state.oracleTerms?.allOf)
        assertEquals(setOf("G"), state.manaProduction?.colors)
    }

    @Test
    fun `re-seeding from an applied query restores the selected sets`() = runTest(dispatcher) {
        // A MagicSet cannot be rebuilt from a set code, so re-opening the sheet on an applied
        // CardSet criterion must carry over the sets the form already holds — otherwise the
        // filter stays applied while its chips silently vanish from the form.
        val vm = createVm()
        advanceUntilIdle()
        val dominaria = MagicSet(
            code = "dom",
            name = "Dominaria",
            setType = SetType.EXPANSION,
            releasedAt = "2018-04-27",
            cardCount = 269,
            iconSvgUri = "",
        )
        vm.toggleSet(dominaria)
        val applied = vm.uiState.value.currentQuery

        vm.seedFrom(applied)

        assertEquals(setOf(dominaria), vm.uiState.value.selectedSets)
        assertEquals(applied.criteria, vm.uiState.value.currentQuery.criteria)
    }

    // ── Color match mode defaults ─────────────────────────────────────────────
    //  `id>=w or id>=u` is the semantic inversion SearchCriterion.ColorIdentity's KDoc warns
    //  about: it returns Jund, Grixis and 5-color cards to a user asking "what fits in Azorius".

    @Test
    fun `switching to Identity picks AT_MOST while the user has not chosen a mode`() =
        runTest(dispatcher) {
            val vm = createVm()
            advanceUntilIdle()
            vm.toggleColor("W")
            vm.toggleColor("U")
            assertEquals(ColorMatchMode.ANY_OF, vm.uiState.value.colorMode)

            vm.setUseColorIdentity(true)

            assertEquals(ColorMatchMode.AT_MOST, vm.uiState.value.colorMode)
            val criterion = vm.uiState.value.currentQuery.criteria
                .filterIsInstance<SearchCriterion.ColorIdentity>().single()
            assertEquals(ColorMatchMode.AT_MOST, criterion.mode)

            // Back to printed colors: `c>=w or c>=u` is the right reading of "any of these".
            vm.setUseColorIdentity(false)
            assertEquals(ColorMatchMode.ANY_OF, vm.uiState.value.colorMode)
        }

    @Test
    fun `an explicitly picked mode is never overwritten by the Color Identity chip`() =
        runTest(dispatcher) {
            val vm = createVm()
            advanceUntilIdle()
            vm.setColorMode(ColorMatchMode.EXACTLY)

            vm.setUseColorIdentity(true)
            assertEquals(ColorMatchMode.EXACTLY, vm.uiState.value.colorMode)
            vm.setUseColorIdentity(false)
            assertEquals(ColorMatchMode.EXACTLY, vm.uiState.value.colorMode)
        }

    @Test
    fun `a seeded mode survives verbatim and drops the previous explicit pick`() =
        runTest(dispatcher) {
            val vm = createVm()
            advanceUntilIdle()
            vm.setColorMode(ColorMatchMode.AT_LEAST)

            // Deck Analysis seeds an AT_MOST identity clause; it must not be re-derived.
            vm.seedFrom(sectionPreset)
            assertEquals(ColorMatchMode.AT_MOST, vm.uiState.value.colorMode)

            // The seed also resets "the user chose this", so the facet default applies again.
            vm.setUseColorIdentity(false)
            assertEquals(ColorMatchMode.ANY_OF, vm.uiState.value.colorMode)
        }

    @Test
    fun `clearAll drops an explicit mode pick along with every criterion`() = runTest(dispatcher) {
        val vm = createVm()
        advanceUntilIdle()
        vm.setColorMode(ColorMatchMode.EXACTLY)

        vm.clearAll()

        assertEquals(ColorMatchMode.ANY_OF, vm.uiState.value.colorMode)
        vm.setUseColorIdentity(true)
        assertEquals(ColorMatchMode.AT_MOST, vm.uiState.value.colorMode)
    }

    @Test
    fun `clearAll keeps the user's preferred currency`() = runTest(dispatcher) {
        val vm = createVm()
        advanceUntilIdle()
        assertEquals("usd", vm.uiState.value.priceCurrency)
        vm.setName("Bolt")

        vm.clearAll()

        assertEquals("usd", vm.uiState.value.priceCurrency)
        assertTrue(vm.uiState.value.currentQuery.criteria.isEmpty())
    }
}
