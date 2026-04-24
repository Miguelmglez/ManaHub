package com.mmg.manahub.feature.decks

import androidx.lifecycle.SavedStateHandle
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.domain.repository.DeckRepository
import com.mmg.manahub.core.domain.repository.UserCardRepository
import io.mockk.coVerify
import io.mockk.every
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
 * Unit tests for [DeckDetailViewModel].
 *
 * Sync is now handled by [com.mmg.manahub.core.sync.SyncManager] via WorkManager —
 * the ViewModel no longer calls any sync method on navigation back.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DeckDetailViewModelSyncTest {

    private val deckRepository     = mockk<DeckRepository>(relaxed = true)
    private val cardRepository     = mockk<CardRepository>(relaxed = true)
    private val userCardRepository = mockk<UserCardRepository>(relaxed = true)

    private lateinit var viewModel: DeckDetailViewModel

    private val DECK_ID = "550e8400-e29b-41d4-a716-446655440000"

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())

        every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(null)
        every { userCardRepository.observeCollection() } returns flowOf(emptyList())

        viewModel = DeckDetailViewModel(
            deckRepository     = deckRepository,
            cardRepository     = cardRepository,
            userCardRepository = userCardRepository,
            savedStateHandle   = SavedStateHandle(mapOf("deckId" to DECK_ID)),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── onNavigatingBack ──────────────────────────────────────────────────────

    @Test
    fun `onNavigatingBack does not mutate the deck`() = runTest {
        viewModel.onNavigatingBack()

        // Sync is now SyncManager's responsibility — the VM must not trigger
        // any deck mutation (updateDeck, addCardToDeck…) on navigation back.
        coVerify(exactly = 0) { deckRepository.updateDeck(any()) }
        coVerify(exactly = 0) { deckRepository.addCardToDeck(any(), any(), any(), any()) }
    }

    @Test
    fun `onNavigatingBack does not throw`() = runTest {
        // Verify it is safe to call when the deck hasn't loaded yet.
        viewModel.onNavigatingBack()
    }
}
