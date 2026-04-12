package com.mmg.manahub.feature.carddetail

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.domain.model.DataResult
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.domain.repository.DeckRepository
import com.mmg.manahub.core.domain.repository.UserCardRepository
import com.mmg.manahub.core.domain.usecase.collection.AddCardToCollectionUseCase
import com.mmg.manahub.util.TestFixtures
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
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
 * Unit tests for [CardDetailViewModel].
 *
 * The ViewModel is initialized with a SavedStateHandle that contains "scryfallId".
 * All tests use the same base scryfallId ("id-001") unless noted otherwise.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CardDetailViewModelTest {

    // ── Test dispatcher ───────────────────────────────────────────────────────

    private val testDispatcher = StandardTestDispatcher()

    // ── Mocks ─────────────────────────────────────────────────────────────────

    private val savedStateHandle = SavedStateHandle(mapOf("scryfallId" to "id-001"))
    private val cardRepo         = mockk<CardRepository>()
    private val userCardRepo     = mockk<UserCardRepository>(relaxed = true)
    private val deckRepo         = mockk<DeckRepository>()
    private val addToCollection  = mockk<AddCardToCollectionUseCase>()
    private val userPrefs        = mockk<UserPreferencesDataStore>()

    private lateinit var viewModel: CardDetailViewModel

    // ── Setup / Teardown ─────────────────────────────────────────────────────

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        val card = TestFixtures.buildCard("id-001")

        // Default happy-path stubs
        coEvery { cardRepo.getCardById("id-001") } returns DataResult.Success(card)
        every { cardRepo.observeCard("id-001") } returns flowOf(card)
        every { userCardRepo.observeByScryfallId("id-001") } returns flowOf(emptyList())
        every { deckRepo.observeDecksContainingCard("id-001") } returns flowOf(emptyList())
        every { userPrefs.userDefinedTagsFlow } returns flowOf(emptyList())

        viewModel = CardDetailViewModel(
            savedStateHandle = savedStateHandle,
            cardRepo         = cardRepo,
            userCardRepo     = userCardRepo,
            deckRepo         = deckRepo,
            addToCollection  = addToCollection,
            userPrefs        = userPrefs,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 1 — Initial load
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given card exists when ViewModel initializes then card is loaded into state`() = runTest {
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull(state.card)
        assertEquals("id-001", state.card?.scryfallId)
        assertFalse(state.isLoading)
        assertNull(state.error)
    }

    @Test
    fun `given getCardById returns stale success when ViewModel initializes then isStale is true`() = runTest {
        // Arrange
        val staleCard = TestFixtures.buildCard("id-001", isStale = true)
        coEvery { cardRepo.getCardById("id-001") } returns DataResult.Success(staleCard, isStale = true)
        every { cardRepo.observeCard("id-001") } returns flowOf(staleCard)

        // Re-create ViewModel with updated stubs
        viewModel = CardDetailViewModel(
            savedStateHandle = savedStateHandle,
            cardRepo         = cardRepo,
            userCardRepo     = userCardRepo,
            deckRepo         = deckRepo,
            addToCollection  = addToCollection,
            userPrefs        = userPrefs,
        )
        advanceUntilIdle()

        // Assert
        assertTrue(viewModel.uiState.value.isStale)
    }

    @Test
    fun `given getCardById returns error when ViewModel initializes then error is set in state`() = runTest {
        // Arrange
        coEvery { cardRepo.getCardById("id-001") } returns DataResult.Error("HTTP 404")

        // Re-create ViewModel
        viewModel = CardDetailViewModel(
            savedStateHandle = savedStateHandle,
            cardRepo         = cardRepo,
            userCardRepo     = userCardRepo,
            deckRepo         = deckRepo,
            addToCollection  = addToCollection,
            userPrefs        = userPrefs,
        )
        advanceUntilIdle()

        // Assert
        val state = viewModel.uiState.value
        assertEquals("HTTP 404", state.error)
        assertFalse(state.isLoading)
        assertNull(state.card)
    }

    @Test
    fun `given observeCard emits updated card when observing then state is updated`() = runTest {
        // Arrange — card observer emits twice
        val initial = TestFixtures.buildCard("id-001", priceUsd = 1.00)
        val updated = TestFixtures.buildCard("id-001", priceUsd = 1.50)

        coEvery { cardRepo.getCardById("id-001") } returns DataResult.Success(initial)
        every { cardRepo.observeCard("id-001") } returns flowOf(initial, updated)

        viewModel = CardDetailViewModel(
            savedStateHandle = savedStateHandle,
            cardRepo         = cardRepo,
            userCardRepo     = userCardRepo,
            deckRepo         = deckRepo,
            addToCollection  = addToCollection,
            userPrefs        = userPrefs,
        )
        advanceUntilIdle()

        // Assert: state reflects the latest emission
        assertEquals(1.50, viewModel.uiState.value.card?.priceUsd)
    }

    @Test
    fun `given observeUserCards emits non-wishlist cards then userCards state is populated`() = runTest {
        // Arrange
        val collectionEntry = TestFixtures.buildUserCard(id = 10L, scryfallId = "id-001", isInWishlist = false)
        val wishlistEntry   = TestFixtures.buildUserCard(id = 11L, scryfallId = "id-001", isInWishlist = true)

        every { userCardRepo.observeByScryfallId("id-001") } returns
                flowOf(listOf(collectionEntry, wishlistEntry))

        viewModel = CardDetailViewModel(
            savedStateHandle = savedStateHandle,
            cardRepo         = cardRepo,
            userCardRepo     = userCardRepo,
            deckRepo         = deckRepo,
            addToCollection  = addToCollection,
            userPrefs        = userPrefs,
        )
        advanceUntilIdle()

        // Assert: wishlist entry is filtered out from userCards
        val userCards = viewModel.uiState.value.userCards
        assertEquals(1, userCards.size)
        assertEquals(10L, userCards.first().id)
        assertFalse(userCards.any { it.isInWishlist })
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 2 — Sheet visibility toggles
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given closed sheet when onShowAddSheet then showAddSheet becomes true`() = runTest {
        viewModel.onShowAddSheet()
        assertTrue(viewModel.uiState.value.showAddSheet)
    }

    @Test
    fun `given open sheet when onDismissAddSheet then showAddSheet becomes false`() = runTest {
        viewModel.onShowAddSheet()
        viewModel.onDismissAddSheet()
        assertFalse(viewModel.uiState.value.showAddSheet)
    }

    @Test
    fun `given closed wishlist sheet when onShowWishlistSheet then showWishlistSheet becomes true`() = runTest {
        viewModel.onShowWishlistSheet()
        assertTrue(viewModel.uiState.value.showWishlistSheet)
    }

    @Test
    fun `given open wishlist sheet when onDismissWishlistSheet then showWishlistSheet becomes false`() = runTest {
        viewModel.onShowWishlistSheet()
        viewModel.onDismissWishlistSheet()
        assertFalse(viewModel.uiState.value.showWishlistSheet)
    }

    @Test
    fun `given UserCard when onRequestDelete then cardToDelete is set`() = runTest {
        val userCard = TestFixtures.buildUserCard()
        viewModel.onRequestDelete(userCard)
        assertEquals(userCard, viewModel.uiState.value.cardToDelete)
    }

    @Test
    fun `given cardToDelete set when onDismissDeleteConfirm then cardToDelete is null`() = runTest {
        viewModel.onRequestDelete(TestFixtures.buildUserCard())
        viewModel.onDismissDeleteConfirm()
        assertNull(viewModel.uiState.value.cardToDelete)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 3 — onAddToCollection
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given successful add when onAddToCollection then success toast event is emitted`() = runTest {
        // Arrange
        coEvery { addToCollection(any(), any(), any(), any(), any(), any()) } returns
                DataResult.Success(Unit)

        // Act & Assert
        viewModel.events.test {
            viewModel.onAddToCollection(
                isFoil           = false,
                isAlternativeArt = false,
                condition        = "NM",
                language         = "en",
                quantity         = 1,
            )
            advanceUntilIdle()

            val event = awaitItem()
            assertTrue(event is CardDetailEvent.ShowToast)
            assertEquals(
                ToastSeverity.SUCCESS,
                (event as CardDetailEvent.ShowToast).severity,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given failed add when onAddToCollection then error toast event is emitted and error state set`() = runTest {
        // Arrange
        coEvery { addToCollection(any(), any(), any(), any(), any(), any()) } returns
                DataResult.Error("Card not found")

        // Act & Assert
        viewModel.events.test {
            viewModel.onAddToCollection(
                isFoil           = false,
                isAlternativeArt = false,
                condition        = "NM",
                language         = "en",
                quantity         = 1,
            )
            advanceUntilIdle()

            val event = awaitItem()
            assertTrue(event is CardDetailEvent.ShowToast)
            assertEquals(
                ToastSeverity.ERROR,
                (event as CardDetailEvent.ShowToast).severity,
            )
            assertEquals("Card not found", viewModel.uiState.value.error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given any result when onAddToCollection then showAddSheet becomes false`() = runTest {
        // Arrange
        coEvery { addToCollection(any(), any(), any(), any(), any(), any()) } returns
                DataResult.Success(Unit)

        viewModel.onShowAddSheet()
        assertTrue(viewModel.uiState.value.showAddSheet)

        // Act
        viewModel.onAddToCollection(false, false, "NM", "en", 1)
        advanceUntilIdle()

        // Assert
        assertFalse(viewModel.uiState.value.showAddSheet)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 4 — onAddToWishlist
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given successful wishlist add when onAddToWishlist then success toast is emitted`() = runTest {
        // Arrange
        coEvery { userCardRepo.addOrIncrement(any()) } returns Unit

        // Act & Assert
        viewModel.events.test {
            viewModel.onAddToWishlist(false, false, "NM", "en", 1)
            advanceUntilIdle()

            val event = awaitItem() as CardDetailEvent.ShowToast
            assertEquals("Added to wishlist", event.message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given failed wishlist add when onAddToWishlist then error toast is emitted`() = runTest {
        // Arrange
        coEvery { userCardRepo.addOrIncrement(any()) } throws RuntimeException("DB error")

        // Act & Assert
        viewModel.events.test {
            viewModel.onAddToWishlist(false, false, "NM", "en", 1)
            advanceUntilIdle()

            val event = awaitItem() as CardDetailEvent.ShowToast
            assertEquals(ToastSeverity.ERROR, event.severity)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 5 — onDeleteCard
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given valid card id when onDeleteCard then deleteCard is called and cardToDelete is cleared`() = runTest {
        // Arrange
        val userCard = TestFixtures.buildUserCard(id = 5L)
        viewModel.onRequestDelete(userCard)
        coEvery { userCardRepo.deleteCard(5L) } returns Unit

        // Act
        viewModel.onDeleteCard(5L)
        advanceUntilIdle()

        // Assert
        coVerify(exactly = 1) { userCardRepo.deleteCard(5L) }
        assertNull(viewModel.uiState.value.cardToDelete)
    }

    @Test
    fun `given delete fails when onDeleteCard then error is set in state`() = runTest {
        // Arrange
        coEvery { userCardRepo.deleteCard(any()) } throws RuntimeException("FK constraint")

        // Act
        viewModel.onDeleteCard(5L)
        advanceUntilIdle()

        // Assert
        assertNotNull(viewModel.uiState.value.error)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 6 — onUpdateQuantity
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given valid quantity when onUpdateQuantity then updateQuantity is called on repo`() = runTest {
        // Arrange
        coEvery { userCardRepo.updateQuantity(any(), any()) } returns Unit

        // Act
        viewModel.onUpdateQuantity(userCardId = 3L, quantity = 4)
        advanceUntilIdle()

        // Assert
        coVerify(exactly = 1) { userCardRepo.updateQuantity(3L, 4) }
    }

    @Test
    fun `given quantity update fails when onUpdateQuantity then error is set in state`() = runTest {
        // Arrange
        coEvery { userCardRepo.updateQuantity(any(), any()) } throws
                IllegalArgumentException("Quantity cannot be negative")

        // Act
        viewModel.onUpdateQuantity(userCardId = 3L, quantity = -1)
        advanceUntilIdle()

        // Assert
        assertNotNull(viewModel.uiState.value.error)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 7 — Tag management
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given card in state when onAddTag then tag is added optimistically`() = runTest {
        // Arrange
        advanceUntilIdle()
        coEvery { cardRepo.updateCardTags(any(), any()) } returns Unit
        val newTag = TestFixtures.TAG_REMOVAL

        // Act
        viewModel.onAddTag(newTag)
        advanceUntilIdle()

        // Assert: optimistic update
        assertTrue(viewModel.uiState.value.card?.tags?.contains(newTag) == true)
    }

    @Test
    fun `given card already has tag when onAddTag then duplicate is not added`() = runTest {
        // Arrange — card already has the REMOVAL tag
        val tag = TestFixtures.TAG_REMOVAL
        val card = TestFixtures.buildCard("id-001", tags = listOf(tag))
        coEvery { cardRepo.getCardById("id-001") } returns DataResult.Success(card)
        every { cardRepo.observeCard("id-001") } returns flowOf(card)
        viewModel = CardDetailViewModel(
            savedStateHandle = savedStateHandle,
            cardRepo         = cardRepo,
            userCardRepo     = userCardRepo,
            deckRepo         = deckRepo,
            addToCollection  = addToCollection,
            userPrefs        = userPrefs,
        )
        advanceUntilIdle()
        coEvery { cardRepo.updateCardTags(any(), any()) } returns Unit

        // Act — try to add the same tag again
        viewModel.onAddTag(tag)
        advanceUntilIdle()

        // Assert: updateCardTags was NOT called (guard in onAddTag: "if (tag in current) return")
        coVerify(exactly = 0) { cardRepo.updateCardTags(any(), any()) }
    }

    @Test
    fun `given card in state when onRemoveTag then tag is removed from state`() = runTest {
        // Arrange
        val tag  = TestFixtures.TAG_REMOVAL
        val card = TestFixtures.buildCard("id-001", tags = listOf(tag))
        coEvery { cardRepo.getCardById("id-001") } returns DataResult.Success(card)
        every { cardRepo.observeCard("id-001") } returns flowOf(card)
        viewModel = CardDetailViewModel(
            savedStateHandle = savedStateHandle,
            cardRepo         = cardRepo,
            userCardRepo     = userCardRepo,
            deckRepo         = deckRepo,
            addToCollection  = addToCollection,
            userPrefs        = userPrefs,
        )
        advanceUntilIdle()
        coEvery { cardRepo.updateCardTags(any(), any()) } returns Unit

        // Act
        viewModel.onRemoveTag(tag)
        advanceUntilIdle()

        // Assert
        assertFalse(viewModel.uiState.value.card?.tags?.contains(tag) == true)
    }

    @Test
    fun `given successful user tag add when onAddUserTag then success toast is emitted`() = runTest {
        // Arrange
        advanceUntilIdle()
        val userTag = TestFixtures.TAG_CUSTOM
        coEvery { cardRepo.updateUserTags(any(), any()) } returns Unit

        // Act & Assert
        viewModel.events.test {
            viewModel.onAddUserTag(userTag)
            advanceUntilIdle()

            val event = awaitItem() as CardDetailEvent.ShowToast
            assertTrue(event.message.contains(userTag.label))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given user tag update fails when onAddUserTag then state is rolled back`() = runTest {
        // Arrange — card starts with empty userTags
        advanceUntilIdle()
        val userTag = TestFixtures.TAG_CUSTOM
        coEvery { cardRepo.updateUserTags(any(), any()) } throws RuntimeException("DB error")

        val stateBefore = viewModel.uiState.value.card?.userTags?.toList() ?: emptyList()

        // Act
        viewModel.onAddUserTag(userTag)
        advanceUntilIdle()

        // Assert: rollback — userTags is restored to pre-optimistic state
        assertEquals(stateBefore, viewModel.uiState.value.card?.userTags)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 8 — onConfirmTradeSelection
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given trade selection with one marked card when onConfirmTradeSelection then toast mentions one copy`() = runTest {
        // Arrange — pre-populate userCards with a non-trade card
        val userCard = TestFixtures.buildUserCard(id = 1L, isForTrade = false)
        every { userCardRepo.observeByScryfallId("id-001") } returns flowOf(listOf(userCard))
        viewModel = CardDetailViewModel(
            savedStateHandle = savedStateHandle,
            cardRepo         = cardRepo,
            userCardRepo     = userCardRepo,
            deckRepo         = deckRepo,
            addToCollection  = addToCollection,
            userPrefs        = userPrefs,
        )
        advanceUntilIdle()
        coEvery { userCardRepo.updateCard(any()) } returns Unit

        // Act & Assert
        viewModel.events.test {
            viewModel.onConfirmTradeSelection(mapOf(1L to true))
            advanceUntilIdle()

            val event = awaitItem() as CardDetailEvent.ShowToast
            assertTrue(event.message.contains("1"))
            assertTrue(event.message.contains("copy"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given trade selection with zero marked cards when onConfirmTradeSelection then cleared toast is shown`() = runTest {
        // Arrange
        val userCard = TestFixtures.buildUserCard(id = 1L, isForTrade = true)
        every { userCardRepo.observeByScryfallId("id-001") } returns flowOf(listOf(userCard))
        viewModel = CardDetailViewModel(
            savedStateHandle = savedStateHandle,
            cardRepo         = cardRepo,
            userCardRepo     = userCardRepo,
            deckRepo         = deckRepo,
            addToCollection  = addToCollection,
            userPrefs        = userPrefs,
        )
        advanceUntilIdle()
        coEvery { userCardRepo.updateCard(any()) } returns Unit

        // Act & Assert
        viewModel.events.test {
            viewModel.onConfirmTradeSelection(mapOf(1L to false))
            advanceUntilIdle()

            val event = awaitItem() as CardDetailEvent.ShowToast
            assertTrue(event.message.contains("cleared"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 9 — onErrorDismissed
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given error in state when onErrorDismissed then error is cleared`() = runTest {
        // Arrange — inject an error
        coEvery { userCardRepo.deleteCard(any()) } throws RuntimeException("error")
        viewModel.onDeleteCard(1L)
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.error)

        // Act
        viewModel.onErrorDismissed()

        // Assert
        assertNull(viewModel.uiState.value.error)
    }
}
