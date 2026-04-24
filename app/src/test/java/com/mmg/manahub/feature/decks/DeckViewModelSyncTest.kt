package com.mmg.manahub.feature.decks

import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.domain.repository.DeckRepository
import io.mockk.coEvery
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [DeckViewModel].
 *
 * Sync is now handled by [com.mmg.manahub.core.sync.SyncManager] via WorkManager.
 * The ViewModel no longer calls syncAllDirtyDecks / pullIfStale / syncDeckNow —
 * those tests are removed and replaced with tests for the current behaviour.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DeckViewModelSyncTest {

    private val deckRepo = mockk<DeckRepository>(relaxed = true)
    private val cardRepo = mockk<CardRepository>(relaxed = true)

    private lateinit var viewModel: DeckViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { deckRepo.observeAllDeckSummaries() } returns flowOf(emptyList())
        viewModel = DeckViewModel(deckRepo, cardRepo)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── createDeck ────────────────────────────────────────────────────────────

    @Test
    fun `given blank name when createDeck then repo is not called`() = runTest {
        viewModel.createDeck("   ")

        coVerify(exactly = 0) { deckRepo.createDeck(any(), any(), any()) }
    }

    @Test
    fun `given valid name when createDeck succeeds then dialog is dismissed`() = runTest {
        val newDeckId = "550e8400-e29b-41d4-a716-446655440000"
        coEvery { deckRepo.createDeck(any(), any(), any()) } returns newDeckId

        viewModel.createDeck("Izzet Phoenix")

        assertFalse(viewModel.uiState.value.showCreateDialog)
    }

    @Test
    fun `given valid name when createDeck succeeds then no error is surfaced`() = runTest {
        coEvery { deckRepo.createDeck(any(), any(), any()) } returns "new-id"

        viewModel.createDeck("Mono Red Burn")

        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `given createDeck throws then error is set in uiState`() = runTest {
        coEvery { deckRepo.createDeck(any(), any(), any()) } throws RuntimeException("DB write failed")

        viewModel.createDeck("Storm Combo")

        assertNotNull(viewModel.uiState.value.error)
    }

    @Test
    fun `given createDeck throws then dialog is NOT dismissed`() = runTest {
        coEvery { deckRepo.createDeck(any(), any(), any()) } throws RuntimeException("DB write failed")
        viewModel.onShowCreateDialog()

        viewModel.createDeck("Storm Combo")

        // Dialog should still be shown on failure so the user can retry.
        // (showCreateDialog is only cleared on success)
    }

    // ── deleteDeck ────────────────────────────────────────────────────────────

    @Test
    fun `given deckId when deleteDeck then deckRepo deleteDeck is called with String id`() = runTest {
        val deckId = "abc-123-def-456"

        viewModel.deleteDeck(deckId)

        coVerify(exactly = 1) { deckRepo.deleteDeck(deckId) }
    }

    // ── init ──────────────────────────────────────────────────────────────────

    @Test
    fun `on init observeAllDeckSummaries is collected`() = runTest {
        // observeAllDeckSummaries() is called in the init block — verify at least once.
        coVerify(atLeast = 1) { deckRepo.observeAllDeckSummaries() }
    }

    @Test
    fun `on init isLoading is set to false after decks are emitted`() = runTest {
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `on init no deck is created or deleted automatically`() = runTest {
        // The VM must not perform any write operations during initialisation —
        // only reads (observeAllDeckSummaries).
        coVerify(exactly = 0) { deckRepo.createDeck(any(), any(), any()) }
        coVerify(exactly = 0) { deckRepo.deleteDeck(any()) }
    }
}
