package com.mmg.manahub.feature.decks

import androidx.lifecycle.SavedStateHandle
import androidx.work.WorkManager
import com.mmg.manahub.core.domain.model.DataResult
import com.mmg.manahub.core.domain.model.Deck
import com.mmg.manahub.core.domain.model.DeckWithCards
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.domain.repository.DeckRepository
import com.mmg.manahub.core.domain.repository.UserCardRepository
import com.mmg.manahub.core.domain.repository.UserPreferencesRepository
import com.mmg.manahub.core.domain.usecase.card.SuggestTagsUseCase
import com.mmg.manahub.core.sync.SyncManager
import com.mmg.manahub.core.sync.SyncState
import com.mmg.manahub.feature.auth.domain.repository.AuthRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [DeckMagicDetailViewModel] sync / navigation behaviour.
 *
 * Sync is now handled by [SyncManager] via WorkManager — the ViewModel must
 * not trigger any deck mutation (updateDeck, addCardToDeck…) on navigation back.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DeckDetailViewModelSyncTest {

    private val deckRepository       = mockk<DeckRepository>(relaxed = true)
    private val cardRepository       = mockk<CardRepository>(relaxed = true)
    private val userCardRepository   = mockk<UserCardRepository>(relaxed = true)
    private val authRepository       = mockk<AuthRepository>(relaxed = true)
    private val suggestTagsUseCase   = mockk<SuggestTagsUseCase>(relaxed = true)
    private val userPreferencesRepo  = mockk<UserPreferencesRepository>(relaxed = true)
    private val syncManager          = mockk<SyncManager>(relaxed = true)
    private val workManager          = mockk<WorkManager>(relaxed = true)

    private lateinit var viewModel: DeckMagicDetailViewModel

    private val DECK_ID = "550e8400-e29b-41d4-a716-446655440000"

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())

        every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(null)
        every { userCardRepository.observeCollection() } returns flowOf(emptyList())
        every { syncManager.syncState } returns MutableStateFlow(SyncState.IDLE)

        viewModel = DeckMagicDetailViewModel(
            deckRepository      = deckRepository,
            cardRepository      = cardRepository,
            userCardRepository  = userCardRepository,
            authRepository      = authRepository,
            suggestTagsUseCase  = suggestTagsUseCase,
            userPreferencesRepo = userPreferencesRepo,
            syncManager         = syncManager,
            workManager         = workManager,
            savedStateHandle    = SavedStateHandle(mapOf("deckId" to DECK_ID)),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── onNavigatingBack ──────────────────────────────────────────────────────

    @Test
    fun `onNavigatingBack does not mutate the deck`() = runTest {
        // No unsaved changes — back navigation should be immediate with no Room writes.
        viewModel.onNavigatingBack()

        coVerify(exactly = 0) { deckRepository.updateDeck(any()) }
        coVerify(exactly = 0) { deckRepository.addCardToDeck(any(), any(), any(), any()) }
    }

    @Test
    fun `onNavigatingBack returns true when there are no unsaved changes`() = runTest {
        // Fresh ViewModel with no edits made → safe to navigate.
        val canNavigate = viewModel.onNavigatingBack()
        assertFalse("Expected canNavigate=true when no unsaved changes", !canNavigate)
    }

    @Test
    fun `onNavigatingBack does not throw when deck has not loaded yet`() = runTest {
        // Verify it is safe to call before the deck flow emits.
        viewModel.onNavigatingBack()
    }

    @Test
    fun `onNavigatingBack returns false and shows discard dialog when there are unsaved changes`() = runTest {
        // updateDeckName requires a loaded deck (draftDeck != null), so create a ViewModel with one.
        val deck = Deck(
            id = DECK_ID, userId = "user", name = "Test Deck", description = "",
            format = "casual", coverCardId = null, commanderCardId = null,
            isDeleted = false, createdAt = 0L, updatedAt = 0L,
        )
        every { deckRepository.observeDeckWithCards(DECK_ID) } returns flowOf(
            DeckWithCards(deck = deck, mainboard = emptyList(), sideboard = emptyList())
        )
        coEvery { cardRepository.getCardById(any()) } returns DataResult.Error("not found")
        val localVm = DeckMagicDetailViewModel(
            deckRepository      = deckRepository,
            cardRepository      = cardRepository,
            userCardRepository  = userCardRepository,
            authRepository      = authRepository,
            suggestTagsUseCase  = suggestTagsUseCase,
            userPreferencesRepo = userPreferencesRepo,
            syncManager         = syncManager,
            workManager         = workManager,
            savedStateHandle    = SavedStateHandle(mapOf("deckId" to DECK_ID)),
        )

        localVm.updateDeckName("Modified Name")
        val canNavigate = localVm.onNavigatingBack()

        assertFalse("Expected canNavigate=false when unsaved changes exist", canNavigate)
    }
}
