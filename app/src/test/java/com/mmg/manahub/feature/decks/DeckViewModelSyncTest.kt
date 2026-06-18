package com.mmg.manahub.feature.decks

import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.domain.repository.DeckRepository
import com.mmg.manahub.feature.decks.presentation.DeckViewModel
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
