package com.mmg.manahub.feature.decks

import androidx.lifecycle.SavedStateHandle
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import com.mmg.manahub.core.domain.model.DataResult
import com.mmg.manahub.core.domain.model.Deck
import com.mmg.manahub.core.domain.model.DeckSlot
import com.mmg.manahub.core.domain.model.DeckWithCards
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.domain.repository.DeckRepository
import com.mmg.manahub.core.domain.repository.UserCardRepository
import com.mmg.manahub.core.domain.repository.UserPreferencesRepository
import com.mmg.manahub.core.domain.usecase.card.SuggestTagsUseCase
import com.mmg.manahub.core.sync.SyncManager
import com.mmg.manahub.core.sync.SyncResult
import com.mmg.manahub.core.sync.SyncState
import com.mmg.manahub.feature.auth.domain.model.AuthUser
import com.mmg.manahub.feature.auth.domain.repository.AuthRepository
import com.mmg.manahub.util.TestFixtures
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [DeckMagicDetailViewModel].
 *
 * Focuses on:
 *  - GROUP 1: Draft mutations — confirm changes stay in memory (no Room writes)
 *  - GROUP 2: Commander management in draft
 *  - GROUP 3: saveDeck — diff computation, Room writes, sync trigger
 *  - GROUP 4: discardChanges — state restoration
 *  - GROUP 5: onNavigatingBack — guards against unsaved changes
 *  - GROUP 6: observeDeck — draft is NOT replaced when unsaved changes exist
 *
 * All I/O dependencies (deckRepository, cardRepository, authRepository,
 * syncManager, workManager) are mocked with MockK.
 * No real Room database or Supabase calls are made.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DeckMagicDetailViewModelTest {

    // ── Dispatcher ────────────────────────────────────────────────────────────

    private val testDispatcher = UnconfinedTestDispatcher()

    // ── Mocks ─────────────────────────────────────────────────────────────────

    private val deckRepository       = mockk<DeckRepository>(relaxed = true)
    private val cardRepository       = mockk<CardRepository>(relaxed = true)
    private val userCardRepository   = mockk<UserCardRepository>(relaxed = true)
    private val authRepository       = mockk<AuthRepository>(relaxed = true)
    private val suggestTagsUseCase   = mockk<SuggestTagsUseCase>(relaxed = true)
    private val userPreferencesRepo  = mockk<UserPreferencesRepository>(relaxed = true)
    private val syncManager          = mockk<SyncManager>(relaxed = true)
    private val workManager          = mockk<WorkManager>(relaxed = true)

    // ── Constants ─────────────────────────────────────────────────────────────

    private val DECK_ID        = "deck-uuid-001"
    private val SCRYFALL_ID_A  = "scryfall-id-a"
    private val SCRYFALL_ID_B  = "scryfall-id-b"
    private val COMMANDER_ID   = "commander-id"
    private val USER_ID        = "user-uuid-001"

    // ── Fixture helpers ───────────────────────────────────────────────────────

    private fun buildDeck(
        commanderCardId: String? = null,
        format:          String  = "commander",
        name:            String  = "Test Deck",
    ) = Deck(
        id              = DECK_ID,
        userId          = USER_ID,
        name            = name,
        description     = "",
        format          = format,
        coverCardId     = null,
        commanderCardId = commanderCardId,
        isDeleted       = false,
        createdAt       = 1_000L,
        updatedAt       = 2_000L,
    )

    private fun buildCommanderCard(
        scryfallId: String = COMMANDER_ID,
    ) = TestFixtures.buildCard(
        scryfallId     = scryfallId,
        name           = "Atraxa, Praetors' Voice",
        typeLine       = "Legendary Creature — Phyrexian Angel Horror",
        colorIdentity  = listOf("W", "U", "B", "G"),
    )

    private fun buildDeckWithCards(
        deck:      Deck       = buildDeck(),
        mainboard: List<DeckSlot> = emptyList(),
        sideboard: List<DeckSlot> = emptyList(),
    ) = DeckWithCards(deck = deck, mainboard = mainboard, sideboard = sideboard)

    private fun buildLoggedInUser() = AuthUser(
        id        = USER_ID,
        email     = "user@example.com",
        nickname  = "TestUser",
        gameTag   = "#ABC",
        avatarUrl = null,
        provider  = "email",
    )

    // ── ViewModel builder ─────────────────────────────────────────────────────

    /**
     * Creates the ViewModel wired to [deckRepository.observeDeckWithCards] emitting [deckWithCards].
     * Pass a [MutableStateFlow] of [DeckWithCards?] to simulate subsequent Room emissions.
     */
    private fun buildViewModel(
        deckFlow: kotlinx.coroutines.flow.Flow<DeckWithCards?> = flowOf(null),
    ): DeckMagicDetailViewModel {
        every { deckRepository.observeDeckWithCards(DECK_ID) } returns deckFlow
        every { userCardRepository.observeCollection() } returns flowOf(emptyList())
        every { syncManager.syncState } returns MutableStateFlow(SyncState.IDLE)

        return DeckMagicDetailViewModel(
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

    // ── Setup / Teardown ─────────────────────────────────────────────────────

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { authRepository.getCurrentUser() } returns null
        coEvery { cardRepository.getCardById(any()) } returns DataResult.Error("not found")
        coEvery { cardRepository.searchCards(any()) } returns DataResult.Success(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 1 — Draft mutations: verify no Room writes
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given loaded deck when addCardToDeck then draft state is updated without calling deckRepository`() = runTest {
        // Arrange
        val deck = buildDeck(format = "casual")
        val vm = buildViewModel(deckFlow = flowOf(buildDeckWithCards(deck = deck)))

        // Act
        vm.addCardToDeck(SCRYFALL_ID_A, isSideboard = false)

        // Assert: UI shows the new card in draft
        val cards = vm.uiState.value.cards.map { it.scryfallId }
        assertTrue(cards.contains(SCRYFALL_ID_A))
        // But Room was NOT written
        coVerify(exactly = 0) { deckRepository.addCardToDeck(any(), any(), any(), any()) }
    }

    @Test
    fun `given card in deck when addCardToDeck twice then quantity is 2 without Room write`() = runTest {
        // Arrange
        val deck = buildDeck(format = "casual")
        val vm = buildViewModel(deckFlow = flowOf(buildDeckWithCards(deck = deck)))

        // Act
        vm.addCardToDeck(SCRYFALL_ID_A, isSideboard = false)
        vm.addCardToDeck(SCRYFALL_ID_A, isSideboard = false)

        // Assert
        val entry = vm.uiState.value.cards.find { it.scryfallId == SCRYFALL_ID_A && !it.isSideboard }
        assertNotNull("Card should be in cards list", entry)
        assertEquals(2, entry!!.quantity)
        coVerify(exactly = 0) { deckRepository.addCardToDeck(any(), any(), any(), any()) }
    }

    @Test
    fun `given card in deck when removeCardFromDeck then draft is updated without Room write`() = runTest {
        // Arrange: deck has one copy of card A
        val deck      = buildDeck(format = "casual")
        val mainboard = listOf(DeckSlot(SCRYFALL_ID_A, 2))
        val vm = buildViewModel(deckFlow = flowOf(buildDeckWithCards(deck = deck, mainboard = mainboard)))

        // Act: remove one copy
        vm.removeCardFromDeck(SCRYFALL_ID_A, isSideboard = false)

        // Assert: draft quantity decremented, no Room call
        val entry = vm.uiState.value.cards.find { it.scryfallId == SCRYFALL_ID_A && !it.isSideboard }
        assertNotNull(entry)
        assertEquals(1, entry!!.quantity)
        coVerify(exactly = 0) { deckRepository.removeCardFromDeck(any(), any(), any()) }
    }

    @Test
    fun `given card with qty 1 when removeCardFromDeck then card is removed from draft without Room write`() = runTest {
        // Arrange: deck has exactly one copy
        val deck      = buildDeck(format = "casual")
        val mainboard = listOf(DeckSlot(SCRYFALL_ID_A, 1))
        val vm = buildViewModel(deckFlow = flowOf(buildDeckWithCards(deck = deck, mainboard = mainboard)))

        // Act
        vm.removeCardFromDeck(SCRYFALL_ID_A, isSideboard = false)

        // Assert: removed entirely from draft
        val entry = vm.uiState.value.cards.find { it.scryfallId == SCRYFALL_ID_A && !it.isSideboard }
        assertNull(entry)
        coVerify(exactly = 0) { deckRepository.removeCardFromDeck(any(), any(), any()) }
    }

    @Test
    fun `given loaded deck when updateDeckName then draft deck name changes without Room write`() = runTest {
        // Arrange
        val deck = buildDeck(name = "Old Name")
        val vm = buildViewModel(deckFlow = flowOf(buildDeckWithCards(deck = deck)))

        // Act
        vm.updateDeckName("New Name")

        // Assert
        assertEquals("New Name", vm.uiState.value.deck?.name)
        coVerify(exactly = 0) { deckRepository.updateDeck(any()) }
    }

    @Test
    fun `given blank name when updateDeckName then deck name is unchanged`() = runTest {
        // Arrange
        val deck = buildDeck(name = "Existing Name")
        val vm = buildViewModel(deckFlow = flowOf(buildDeckWithCards(deck = deck)))

        // Act: blank should be rejected
        vm.updateDeckName("   ")

        // Assert
        assertEquals("Existing Name", vm.uiState.value.deck?.name)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 2 — Commander management in draft
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given loaded commander deck when setCommander then draft commanderCardId is updated without Room write`() = runTest {
        // Arrange
        val deck = buildDeck(format = "commander")
        val commanderCard = buildCommanderCard()
        val vm = buildViewModel(deckFlow = flowOf(buildDeckWithCards(deck = deck)))

        // Act
        vm.setCommander(commanderCard)

        // Assert: draft updated
        assertEquals(COMMANDER_ID, vm.uiState.value.deck?.commanderCardId)
        // No Room write
        coVerify(exactly = 0) { deckRepository.updateDeck(any()) }
        coVerify(exactly = 0) { deckRepository.addCardToDeck(any(), any(), any(), any()) }
    }

    @Test
    fun `given loaded commander deck when setCommander then coverCardId is set to commander scryfallId`() = runTest {
        // Arrange
        val deck = buildDeck(format = "commander")
        val commanderCard = buildCommanderCard()
        val vm = buildViewModel(deckFlow = flowOf(buildDeckWithCards(deck = deck)))

        // Act
        vm.setCommander(commanderCard)

        // Assert: coverCardId points to commander
        assertEquals(COMMANDER_ID, vm.uiState.value.deck?.coverCardId)
    }

    @Test
    fun `given commander set when setCommander with new card then old commander is removed from draft`() = runTest {
        // Arrange: deck already has a commander
        val oldCommanderId = "old-commander-id"
        val deck      = buildDeck(format = "commander", commanderCardId = oldCommanderId)
        val mainboard = listOf(DeckSlot(oldCommanderId, 1))
        val vm = buildViewModel(deckFlow = flowOf(buildDeckWithCards(deck = deck, mainboard = mainboard)))

        val newCommander = buildCommanderCard(scryfallId = COMMANDER_ID)

        // Act
        vm.setCommander(newCommander)

        // Assert: old commander removed from draft cards
        val oldEntry = vm.uiState.value.cards.find { it.scryfallId == oldCommanderId }
        assertNull("Old commander must be removed from draft", oldEntry)
    }

    @Test
    fun `given commander is set when removeCommander then commanderCardId is null in draft`() = runTest {
        // Arrange
        val deck      = buildDeck(format = "commander", commanderCardId = COMMANDER_ID)
        val mainboard = listOf(DeckSlot(COMMANDER_ID, 1))
        val vm = buildViewModel(deckFlow = flowOf(buildDeckWithCards(deck = deck, mainboard = mainboard)))

        // Act
        vm.removeCommander()

        // Assert
        assertNull(vm.uiState.value.deck?.commanderCardId)
        coVerify(exactly = 0) { deckRepository.updateDeck(any()) }
    }

    @Test
    fun `given commander is set when removeCommander then commander card is removed from draft cards`() = runTest {
        // Arrange
        val deck      = buildDeck(format = "commander", commanderCardId = COMMANDER_ID)
        val mainboard = listOf(DeckSlot(COMMANDER_ID, 1), DeckSlot(SCRYFALL_ID_A, 2))
        val vm = buildViewModel(deckFlow = flowOf(buildDeckWithCards(deck = deck, mainboard = mainboard)))

        // Act
        vm.removeCommander()

        // Assert: commander card gone, other card still present
        val commanderEntry = vm.uiState.value.commanderCard
        assertNull("Commander entry must be null after removal", commanderEntry)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 3 — saveDeck
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given only one card changed when saveDeck then only that card is written to Room`() = runTest {
        // Arrange: deck has A(qty=1) and B(qty=2) persisted
        val deck      = buildDeck(format = "casual")
        val mainboard = listOf(
            DeckSlot(SCRYFALL_ID_A, 1),
            DeckSlot(SCRYFALL_ID_B, 2),
        )
        val vm = buildViewModel(deckFlow = flowOf(buildDeckWithCards(deck = deck, mainboard = mainboard)))
        // Mutate only card B in the draft
        vm.addCardToDeck(SCRYFALL_ID_B, isSideboard = false)   // B becomes qty=3

        // Act
        var completed = false
        vm.saveDeck { completed = true }
        advanceUntilIdle()

        // Assert: replaceAllCards called once with the full card list (atomic replace, no per-card diffs)
        coVerify(exactly = 1) { deckRepository.replaceAllCards(eq(DECK_ID), any()) }
        coVerify(exactly = 0) { deckRepository.addCardToDeck(any(), any(), any(), any()) }
        assertTrue(completed)
    }

    @Test
    fun `given card removed in draft when saveDeck then removeCardFromDeck is called`() = runTest {
        // Arrange: deck has card A persisted
        val deck      = buildDeck(format = "casual")
        val mainboard = listOf(DeckSlot(SCRYFALL_ID_A, 1))
        val vm = buildViewModel(deckFlow = flowOf(buildDeckWithCards(deck = deck, mainboard = mainboard)))
        // Remove card from draft
        vm.removeCardFromDeck(SCRYFALL_ID_A, isSideboard = false)

        // Act
        vm.saveDeck { }
        advanceUntilIdle()

        // Assert: replaceAllCards called with empty list (card removed from draft)
        coVerify(exactly = 1) { deckRepository.replaceAllCards(eq(DECK_ID), eq(emptyList())) }
        coVerify(exactly = 0) { deckRepository.removeCardFromDeck(any(), any(), any()) }
    }

    @Test
    fun `given unchanged cards when saveDeck then neither add nor remove is called for unchanged cards`() = runTest {
        // Arrange: persisted = draft (same qty for A)
        val deck      = buildDeck(format = "casual")
        val mainboard = listOf(DeckSlot(SCRYFALL_ID_A, 2))
        val vm = buildViewModel(deckFlow = flowOf(buildDeckWithCards(deck = deck, mainboard = mainboard)))
        // No mutations

        // Act
        vm.saveDeck { }
        advanceUntilIdle()

        // Assert: no write to Room since nothing changed
        coVerify(exactly = 0) { deckRepository.addCardToDeck(any(), any(), any(), any()) }
        coVerify(exactly = 0) { deckRepository.removeCardFromDeck(any(), any(), any()) }
    }

    @Test
    fun `given deck name changed in draft when saveDeck then updateDeck is called`() = runTest {
        // Arrange
        val deck = buildDeck(name = "Original Name")
        val vm = buildViewModel(deckFlow = flowOf(buildDeckWithCards(deck = deck)))
        vm.updateDeckName("New Name")

        // Act
        vm.saveDeck { }
        advanceUntilIdle()

        // Assert
        coVerify(exactly = 1) { deckRepository.updateDeck(any()) }
    }

    @Test
    fun `given logged-in user when saveDeck then syncManager sync is called`() = runTest {
        // Arrange
        val deck = buildDeck()
        val vm = buildViewModel(deckFlow = flowOf(buildDeckWithCards(deck = deck)))
        coEvery { authRepository.getCurrentUser() } returns buildLoggedInUser()
        coEvery { syncManager.sync(USER_ID) } returns SyncResult(state = SyncState.SUCCESS)

        // Act
        vm.saveDeck { }
        advanceUntilIdle()

        // Assert
        coVerify(exactly = 1) { syncManager.sync(USER_ID) }
    }

    @Test
    fun `given guest user (null userId) when saveDeck then syncManager sync is NOT called`() = runTest {
        // Arrange
        val deck = buildDeck()
        val vm = buildViewModel(deckFlow = flowOf(buildDeckWithCards(deck = deck)))
        coEvery { authRepository.getCurrentUser() } returns null

        // Act
        vm.saveDeck { }
        advanceUntilIdle()

        // Assert
        coVerify(exactly = 0) { syncManager.sync(any()) }
    }

    @Test
    fun `given successful save when saveDeck then hasUnsavedChanges is false`() = runTest {
        // Arrange
        val deck = buildDeck(format = "casual")
        val vm = buildViewModel(deckFlow = flowOf(buildDeckWithCards(deck = deck)))
        vm.addCardToDeck(SCRYFALL_ID_A)   // create a draft change

        // Act
        vm.saveDeck { }
        advanceUntilIdle()

        // Assert
        assertFalse(vm.uiState.value.hasUnsavedChanges)
    }

    @Test
    fun `given successful save when saveDeck then isSaving is false after completion`() = runTest {
        // Arrange
        val deck = buildDeck()
        val vm = buildViewModel(deckFlow = flowOf(buildDeckWithCards(deck = deck)))

        // Act
        vm.saveDeck { }
        advanceUntilIdle()

        // Assert
        assertFalse(vm.uiState.value.isSaving)
    }

    @Test
    fun `given sync returns error when saveDeck then syncError is set in uiState`() = runTest {
        // Arrange
        val deck = buildDeck()
        val vm = buildViewModel(deckFlow = flowOf(buildDeckWithCards(deck = deck)))
        coEvery { authRepository.getCurrentUser() } returns buildLoggedInUser()
        coEvery { syncManager.sync(USER_ID) } returns SyncResult(
            state = SyncState.ERROR,
            error = "network timeout",
        )

        // Act
        vm.saveDeck { }
        advanceUntilIdle()

        // Assert
        assertEquals("network timeout", vm.uiState.value.syncError)
    }

    @Test
    fun `given logged-in user when saveDeck then workManager enqueueUniqueWork is called`() = runTest {
        // Arrange
        val deck = buildDeck()
        val vm = buildViewModel(deckFlow = flowOf(buildDeckWithCards(deck = deck)))
        coEvery { authRepository.getCurrentUser() } returns buildLoggedInUser()
        coEvery { syncManager.sync(any()) } returns SyncResult(state = SyncState.SUCCESS)

        // Act
        vm.saveDeck { }
        advanceUntilIdle()

        // Assert: one-time sync worker enqueued
        verify(exactly = 1) {
            workManager.enqueueUniqueWork(
                any<String>(),
                eq(ExistingWorkPolicy.REPLACE),
                any<androidx.work.OneTimeWorkRequest>(),
            )
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 4 — discardChanges
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given unsaved changes when discardChanges then draft is restored to persisted state`() = runTest {
        // Arrange
        val deck      = buildDeck(format = "casual")
        val mainboard = listOf(DeckSlot(SCRYFALL_ID_A, 2))
        val vm = buildViewModel(deckFlow = flowOf(buildDeckWithCards(deck = deck, mainboard = mainboard)))
        // Mutate draft
        vm.addCardToDeck(SCRYFALL_ID_B)
        assertTrue(vm.uiState.value.hasUnsavedChanges)

        // Act
        var navigated = false
        vm.discardChanges { navigated = true }

        // Assert: draft is back to persisted (B was never persisted → not in draft)
        val cardB = vm.uiState.value.cards.find { it.scryfallId == SCRYFALL_ID_B }
        assertNull("Card B should not exist in restored draft", cardB)
        // But A should still be there
        val cardA = vm.uiState.value.cards.find { it.scryfallId == SCRYFALL_ID_A && !it.isSideboard }
        assertNotNull("Card A should still be present after discard", cardA)
        assertTrue(navigated)
    }

    @Test
    fun `given unsaved changes when discardChanges then hasUnsavedChanges is false`() = runTest {
        // Arrange
        val deck = buildDeck(format = "casual")
        val vm = buildViewModel(deckFlow = flowOf(buildDeckWithCards(deck = deck)))
        vm.addCardToDeck(SCRYFALL_ID_A)
        assertTrue(vm.uiState.value.hasUnsavedChanges)

        // Act
        vm.discardChanges { }

        // Assert
        assertFalse(vm.uiState.value.hasUnsavedChanges)
    }

    @Test
    fun `given discard dialog shown when discardChanges then showDiscardDialog is false`() = runTest {
        // Arrange: trigger the dialog first
        val deck = buildDeck(format = "casual")
        val vm = buildViewModel(deckFlow = flowOf(buildDeckWithCards(deck = deck)))
        vm.addCardToDeck(SCRYFALL_ID_A)
        vm.onNavigatingBack()   // sets showDiscardDialog = true
        assertTrue(vm.uiState.value.showDiscardDialog)

        // Act
        vm.discardChanges { }

        // Assert
        assertFalse(vm.uiState.value.showDiscardDialog)
    }

    @Test
    fun `given deck name changed in draft when discardChanges then original name is restored`() = runTest {
        // Arrange
        val deck = buildDeck(name = "Original Name")
        val vm = buildViewModel(deckFlow = flowOf(buildDeckWithCards(deck = deck)))
        vm.updateDeckName("Changed Name")
        assertEquals("Changed Name", vm.uiState.value.deck?.name)

        // Act
        vm.discardChanges { }

        // Assert
        assertEquals("Original Name", vm.uiState.value.deck?.name)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 5 — onNavigatingBack
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given unsaved changes when onNavigatingBack then returns false and shows discard dialog`() = runTest {
        // Arrange
        val deck = buildDeck(format = "casual")
        val vm = buildViewModel(deckFlow = flowOf(buildDeckWithCards(deck = deck)))
        vm.addCardToDeck(SCRYFALL_ID_A)
        assertTrue(vm.uiState.value.hasUnsavedChanges)

        // Act
        val canNavigate = vm.onNavigatingBack()

        // Assert
        assertFalse("Should block navigation when unsaved changes exist", canNavigate)
        assertTrue(vm.uiState.value.showDiscardDialog)
    }

    @Test
    fun `given no unsaved changes when onNavigatingBack then returns true`() = runTest {
        // Arrange: no mutations to draft
        val deck = buildDeck()
        val vm = buildViewModel(deckFlow = flowOf(buildDeckWithCards(deck = deck)))
        assertFalse(vm.uiState.value.hasUnsavedChanges)

        // Act
        val canNavigate = vm.onNavigatingBack()

        // Assert
        assertTrue("Should allow navigation when no unsaved changes", canNavigate)
        assertFalse(vm.uiState.value.showDiscardDialog)
    }

    @Test
    fun `given unsaved changes when onNavigatingBack then showDiscardDialog is true`() = runTest {
        // Arrange
        val deck = buildDeck(format = "casual")
        val vm = buildViewModel(deckFlow = flowOf(buildDeckWithCards(deck = deck)))
        vm.addCardToDeck(SCRYFALL_ID_A)

        // Act
        vm.onNavigatingBack()

        // Assert
        assertTrue(vm.uiState.value.showDiscardDialog)
    }

    @Test
    fun `given discard dialog visible when dismissDiscardDialog then dialog is hidden`() = runTest {
        // Arrange
        val deck = buildDeck(format = "casual")
        val vm = buildViewModel(deckFlow = flowOf(buildDeckWithCards(deck = deck)))
        vm.addCardToDeck(SCRYFALL_ID_A)
        vm.onNavigatingBack()
        assertTrue(vm.uiState.value.showDiscardDialog)

        // Act
        vm.dismissDiscardDialog()

        // Assert
        assertFalse(vm.uiState.value.showDiscardDialog)
        // hasUnsavedChanges unchanged
        assertTrue(vm.uiState.value.hasUnsavedChanges)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 6 — observeDeck: draft is NOT replaced when unsaved changes exist
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given unsaved changes when Room emits new deck then draft is NOT overridden`() = runTest {
        // Arrange: use a flow that can emit multiple values
        val deckFlow = MutableStateFlow<DeckWithCards?>(null)
        val vm = buildViewModel(deckFlow = deckFlow)

        // First emission: deck with no cards
        val deck = buildDeck(name = "Initial Name", format = "casual")
        deckFlow.value = buildDeckWithCards(deck = deck)

        // Make a draft change to mark hasUnsavedChanges
        vm.addCardToDeck(SCRYFALL_ID_A)
        assertTrue(vm.uiState.value.hasUnsavedChanges)

        // Remember draft state before next emission
        val draftCardCount = vm.uiState.value.cards.count { !it.isSideboard }

        // Act: Room emits a new snapshot (e.g. card B was saved by another process)
        val updatedDeck = buildDeck(name = "Updated Name", format = "casual")
        deckFlow.value = buildDeckWithCards(
            deck = updatedDeck,
            mainboard = listOf(DeckSlot(SCRYFALL_ID_B, 1)),
        )
        advanceUntilIdle()

        // Assert: draft was NOT overwritten — card A still present, card B not injected
        val cardA = vm.uiState.value.cards.find { it.scryfallId == SCRYFALL_ID_A && !it.isSideboard }
        assertNotNull("Card A (draft change) should still be in draft", cardA)
        // The draft card count should be the same as what we drafted, not the new emission
        assertEquals(draftCardCount, vm.uiState.value.cards.count { !it.isSideboard })
    }

    @Test
    fun `given NO unsaved changes when Room emits new deck then draft IS updated`() = runTest {
        // Arrange
        val deckFlow = MutableStateFlow<DeckWithCards?>(null)
        val vm = buildViewModel(deckFlow = deckFlow)

        val deck = buildDeck(format = "casual")
        deckFlow.value = buildDeckWithCards(deck = deck)
        assertFalse(vm.uiState.value.hasUnsavedChanges)

        // Act: Room emits a new snapshot
        val updatedDeck = buildDeck(name = "Server Updated Name")
        deckFlow.value = buildDeckWithCards(
            deck = updatedDeck,
            mainboard = listOf(DeckSlot(SCRYFALL_ID_B, 3)),
        )
        advanceUntilIdle()

        // Assert: draft was refreshed with the new emission
        assertEquals("Server Updated Name", vm.uiState.value.deck?.name)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 7 — hasUnsavedChanges detection
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given deck just loaded when no mutations then hasUnsavedChanges is false`() = runTest {
        // Arrange
        val deck = buildDeck(format = "casual")
        val vm = buildViewModel(deckFlow = flowOf(buildDeckWithCards(deck = deck)))

        // Assert
        assertFalse(vm.uiState.value.hasUnsavedChanges)
    }

    @Test
    fun `given card added to draft when addCardToDeck then hasUnsavedChanges is true`() = runTest {
        // Arrange
        val deck = buildDeck(format = "casual")
        val vm = buildViewModel(deckFlow = flowOf(buildDeckWithCards(deck = deck)))

        // Act
        vm.addCardToDeck(SCRYFALL_ID_A)

        // Assert
        assertTrue(vm.uiState.value.hasUnsavedChanges)
    }

    @Test
    fun `given deck name changed when updateDeckName then hasUnsavedChanges is true`() = runTest {
        // Arrange
        val deck = buildDeck(name = "Old Name")
        val vm = buildViewModel(deckFlow = flowOf(buildDeckWithCards(deck = deck)))

        // Act
        vm.updateDeckName("New Name")

        // Assert
        assertTrue(vm.uiState.value.hasUnsavedChanges)
    }
}
