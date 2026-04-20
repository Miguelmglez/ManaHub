package com.mmg.manahub.feature.decks

import androidx.lifecycle.SavedStateHandle
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.domain.repository.DeckRepository
import com.mmg.manahub.core.domain.repository.UserCardRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for the sync-related behaviour of [DeckDetailViewModel].
 *
 * Covers:
 *  - onNavigatingBack: triggers syncDeckNow with the deckId extracted from SavedStateHandle
 *
 * The ViewModel init block collects observeDeckWithCards() and observeCollection();
 * both are stubbed with flowOf(null) / flowOf(emptyList()) to prevent hanging.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DeckDetailViewModelSyncTest {

    // ── Mocks ─────────────────────────────────────────────────────────────────

    private val deckRepository     = mockk<DeckRepository>(relaxed = true)
    private val cardRepository     = mockk<CardRepository>(relaxed = true)
    private val userCardRepository = mockk<UserCardRepository>(relaxed = true)

    private lateinit var viewModel: DeckDetailViewModel

    private val DECK_ID = 1L

    // ── Setup / Teardown ──────────────────────────────────────────────────────

    @Before
    fun setUp() {
        // Replace Main dispatcher so viewModelScope.launch {} runs deterministically.
        Dispatchers.setMain(UnconfinedTestDispatcher())

        // Stub the flow observed in the init block. flowOf(null) means "deck not found",
        // which causes a safe early-return inside the collector — no crash.
        coEvery { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(null)

        // Stub the collection load in loadCollection() to prevent a hanging first()
        coEvery { userCardRepository.observeCollection() } returns flowOf(emptyList())

        val savedStateHandle = SavedStateHandle(mapOf("deckId" to DECK_ID))

        viewModel = DeckDetailViewModel(
            deckRepository     = deckRepository,
            cardRepository     = cardRepository,
            userCardRepository = userCardRepository,
            savedStateHandle   = savedStateHandle,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 1 — onNavigatingBack
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given deckId when onNavigatingBack then syncDeckNow is called with deckId`() = runTest {
        // Act: simulates the user pressing back after editing the deck
        viewModel.onNavigatingBack()

        // Assert: the repository must receive a sync request for this exact deck
        coVerify(exactly = 1) { deckRepository.syncDeckNow(DECK_ID) }
    }
}
