package com.mmg.manahub.feature.decks

import com.mmg.manahub.core.domain.model.Deck
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.domain.repository.DeckRepository
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for the sync-related behaviour of [DeckViewModel].
 *
 * Covers:
 *  - createDeck: blank name guard, happy-path (syncDeckNow called with returned id),
 *    failure path (syncDeckNow NOT called when repo throws)
 *  - deleteDeck: delegates to deckRepo.deleteDeck
 *  - init: syncAllDirtyDecks and pullIfStale are both triggered on construction
 *  - init: failures in syncAllDirtyDecks/pullIfStale are swallowed (runCatching)
 *
 * ViewModels are instantiated manually — no Hilt.
 * The init block observes observeAllDeckSummaries(); we stub it with flowOf(emptyList())
 * to prevent the coroutine from hanging.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DeckViewModelSyncTest {

    // ── Mocks ─────────────────────────────────────────────────────────────────

    private val deckRepo = mockk<DeckRepository>(relaxed = true)
    private val cardRepo = mockk<CardRepository>(relaxed = true)

    private lateinit var viewModel: DeckViewModel

    // ── Setup / Teardown ──────────────────────────────────────────────────────

    @Before
    fun setUp() {
        // Replace Main dispatcher so viewModelScope.launch {} runs deterministically.
        Dispatchers.setMain(UnconfinedTestDispatcher())

        // Stub the observable that the init block collects; without this the coroutine
        // would suspend forever waiting for emissions.
        coEvery { deckRepo.observeAllDeckSummaries() } returns flowOf(emptyList())

        viewModel = DeckViewModel(deckRepo, cardRepo)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 1 — createDeck
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given blank name when createDeck then createDeck is not called on repo`() = runTest {
        // Act
        viewModel.createDeck("   ")   // whitespace-only name

        // Assert: the blank-name guard fires before touching the repo
        coVerify(exactly = 0) { deckRepo.createDeck(any()) }
    }

    @Test
    fun `given valid name when createDeck succeeds then showCreateDialog is false and syncDeckNow is called with returned deckId`() = runTest {
        // Arrange: repo returns a new deck id
        val newDeckId = 42L
        coEvery { deckRepo.createDeck(any()) } returns newDeckId

        // Act
        viewModel.createDeck("Izzet Phoenix")

        // Assert 1: dialog is dismissed after a successful create
        assertFalse(viewModel.uiState.value.showCreateDialog)

        // Assert 2: syncDeckNow is called with the exact id returned by createDeck
        coVerify(exactly = 1) { deckRepo.syncDeckNow(newDeckId) }
    }

    @Test
    fun `given createDeck throws when createDeck then error is set in uiState and syncDeckNow is NOT called`() = runTest {
        // Arrange: simulate a repository failure (e.g. DB constraint)
        coEvery { deckRepo.createDeck(any()) } throws RuntimeException("DB write failed")

        // Act
        viewModel.createDeck("Storm Combo")

        // Assert 1: error surface is populated
        assertNotNull(viewModel.uiState.value.error)

        // Assert 2: syncDeckNow must not be called when createDeck failed
        coVerify(exactly = 0) { deckRepo.syncDeckNow(any()) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 2 — deleteDeck
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given deckId when deleteDeck then deckRepo deleteDeck is called`() = runTest {
        // Act
        viewModel.deleteDeck(99L)

        // Assert: the delete is delegated to the repository (which also handles remote delete)
        coVerify(exactly = 1) { deckRepo.deleteDeck(99L) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 3 — init sync
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `on init syncAllDirtyDecks is called`() = runTest {
        // The ViewModel is already constructed in setUp() — just verify the call was made.
        coVerify(atLeast = 1) { deckRepo.syncAllDirtyDecks() }
    }

    @Test
    fun `on init pullIfStale is called`() = runTest {
        // Assert: both sync operations fire during construction
        coVerify(atLeast = 1) { deckRepo.pullIfStale() }
    }

    @Test
    fun `on init syncAllDirtyDecks failure does not crash ViewModel`() = runTest {
        // Arrange: create a fresh ViewModel where syncAllDirtyDecks throws
        coEvery { deckRepo.syncAllDirtyDecks() } throws RuntimeException("network unavailable")

        // Act: construction must not propagate the exception
        val vm = DeckViewModel(deckRepo, cardRepo)

        // Assert: ViewModel is usable after construction (uiState is accessible)
        assertFalse(vm.uiState.value.showCreateDialog)
    }

    @Test
    fun `on init pullIfStale failure does not crash ViewModel`() = runTest {
        // Arrange: pullIfStale throws — runCatching in init absorbs it
        coEvery { deckRepo.pullIfStale() } throws RuntimeException("timeout")

        // Act: must not throw
        val vm = DeckViewModel(deckRepo, cardRepo)

        // Assert: ViewModel state is still initialised correctly
        assertFalse(vm.uiState.value.isImporting)
    }
}
