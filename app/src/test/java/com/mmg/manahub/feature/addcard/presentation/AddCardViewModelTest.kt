package com.mmg.manahub.feature.addcard.presentation

import app.cash.turbine.test
import com.mmg.manahub.core.domain.repository.UserPreferencesRepository
import com.mmg.manahub.core.domain.usecase.card.GetSpotlightFeedUseCase
import com.mmg.manahub.core.domain.usecase.card.SearchCardsUseCase
import com.mmg.manahub.core.domain.usecase.card.SpotlightFeedResult
import com.mmg.manahub.core.domain.usecase.search.BuildScryfallQueryUseCase
import com.mmg.manahub.core.model.AppLanguage
import com.mmg.manahub.core.model.CardLanguage
import com.mmg.manahub.core.model.CollectionViewMode
import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.model.MagicSet
import com.mmg.manahub.core.model.NewsLanguage
import com.mmg.manahub.core.model.PreferredCurrency
import com.mmg.manahub.core.model.SetType
import com.mmg.manahub.core.model.UserPreferences
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AddCardViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private val searchCards: SearchCardsUseCase = mockk()
    private val userPreferences: UserPreferencesRepository = mockk()
    private val buildScryfallQuery: BuildScryfallQueryUseCase = mockk()
    private val getSpotlightFeed: GetSpotlightFeedUseCase = mockk()

    private val testSet = MagicSet(
        code = "tst",
        name = "Test Set",
        setType = SetType.EXPANSION,
        releasedAt = "2024-01-01",
        cardCount = 100,
        iconSvgUri = "https://example.com/icon.svg"
    )

    private val testPreferences = UserPreferences(
        appLanguage = AppLanguage.ENGLISH,
        cardLanguage = CardLanguage.ENGLISH,
        newsLanguages = setOf(NewsLanguage.ENGLISH),
        preferredCurrency = PreferredCurrency.EUR,
        collectionViewMode = CollectionViewMode.GRID,
    )

    private lateinit var viewModel: AddCardViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)

        coEvery { userPreferences.preferencesFlow } returns flowOf(testPreferences)
        coEvery { getSpotlightFeed(any()) } returns DataResult.Success(
            SpotlightFeedResult(
                cards = emptyList(),
                sourceSet = testSet,
                nextSetIndex = 1
            )
        )

        viewModel = AddCardViewModel(
            searchCards = searchCards,
            userPreferences = userPreferences,
            buildScryfallQuery = buildScryfallQuery,
            getSpotlightFeed = getSpotlightFeed
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state has empty query and no results`() = runTest(dispatcher) {
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals("", state.query)
            assertEquals(emptyList<Any>(), state.results)
            assertEquals(false, state.isSearching)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onClearAll resets all search state`() = runTest(dispatcher) {
        viewModel.uiState.test {
            awaitItem() // Initial

            viewModel.onQueryChange("lotus")
            awaitItem() // Query change

            viewModel.onClearAll()
            val finalState = awaitItem()

            assertEquals("", finalState.query)
            assertEquals(null, finalState.activeQuery)
            assertEquals(emptyList<Any>(), finalState.results)
            assertEquals(false, finalState.isSearching)
            assertEquals(null, finalState.error)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
