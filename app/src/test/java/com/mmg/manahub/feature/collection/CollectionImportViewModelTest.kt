package com.mmg.manahub.feature.collection

import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.data.queue.InMemoryCardQueueStore
import com.mmg.manahub.core.data.queue.PersistentCardQueueRepository
import com.mmg.manahub.core.domain.collection.transfer.CollectionFileGateway
import com.mmg.manahub.core.domain.collection.transfer.CollectionImportResolution
import com.mmg.manahub.core.domain.collection.transfer.FileTooLargeException
import com.mmg.manahub.core.domain.collection.transfer.ResolveCollectionImportUseCase
import com.mmg.manahub.core.domain.repository.CardQueueRepository
import com.mmg.manahub.core.domain.repository.UserCardRepository
import com.mmg.manahub.core.domain.repository.UserPreferencesRepository
import com.mmg.manahub.core.domain.usecase.collection.CommitImportedCardsUseCase
import com.mmg.manahub.core.domain.usecase.queue.CardQueueActions
import com.mmg.manahub.core.model.PreferredCurrency
import com.mmg.manahub.core.model.QueuedCard
import com.mmg.manahub.feature.collection.presentation.importexport.CollectionImportError
import com.mmg.manahub.feature.collection.presentation.importexport.CollectionImportToast
import com.mmg.manahub.feature.collection.presentation.importexport.CollectionImportViewModel
import com.mmg.manahub.util.TestFixtures
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Parse → resolve → private review queue flow of [CollectionImportViewModel]. */
@OptIn(ExperimentalCoroutinesApi::class)
class CollectionImportViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val resolveImport = mockk<ResolveCollectionImportUseCase>()
    private val fileGateway = mockk<CollectionFileGateway>()
    private val userCardRepository = mockk<UserCardRepository>(relaxed = true)
    private val userPreferencesRepository = mockk<UserPreferencesRepository>(relaxed = true)
    private val crashReporter = mockk<CrashReporter>(relaxed = true)
    private lateinit var queue: CardQueueRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { userPreferencesRepository.preferredCurrencyFlow } returns flowOf(PreferredCurrency.USD)
        every { userCardRepository.observeCollection() } returns flowOf(emptyList())
        queue = PersistentCardQueueRepository(InMemoryCardQueueStore())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel() = CollectionImportViewModel(
        resolveImport = resolveImport,
        fileGateway = fileGateway,
        queueRepository = queue,
        queueActions = CardQueueActions(
            queueRepository = queue,
            committer = CommitImportedCardsUseCase(userCardRepository),
            addToWishlist = mockk(relaxed = true),
        ),
        cardRepository = mockk(relaxed = true),
        userCardRepository = userCardRepository,
        userPreferencesRepository = userPreferencesRepository,
        crashReporter = crashReporter,
        appScope = CoroutineScope(testDispatcher),
        parseDispatcher = testDispatcher,
        mainDispatcher = testDispatcher,
    )

    private fun entry(id: String, quantity: Int = 1) =
        QueuedCard(TestFixtures.buildCard(scryfallId = id, name = "Card $id"), quantity, false, "en", "NM", "lea", 0L)

    @Test
    fun `a resolved paste fills the private queue and opens the review with unresolved lines`() = runTest(testDispatcher) {
        coEvery { resolveImport(any(), any()) } returns
            CollectionImportResolution.Resolved(listOf(entry("a", 4)), listOf("1 Nope"), resolvedLineCount = 1)
        val vm = buildViewModel()

        vm.onImportRequested()
        vm.onImportText("4 Lightning Bolt\n1 Nope")
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(listOf("a"), queue.queue.value.map { it.card.scryfallId })
        assertTrue(state.isQueueSheetVisible)
        assertFalse(state.isInputSheetVisible)
        assertTrue(state.isUnresolvedDialogVisible)
        assertEquals(listOf("1 Nope"), state.unresolvedLines)
        assertEquals(CollectionImportToast.Resolved(entries = 1, unresolved = 1), state.queueToast)
        verify { crashReporter.log("collection_import_resolved") }
    }

    @Test
    fun `reopening import with a pending queue offers to resume instead of discarding`() = runTest(testDispatcher) {
        queue.add(entry("a"))
        val vm = buildViewModel()

        vm.onImportRequested()

        assertTrue(vm.uiState.value.isResumePromptVisible)
        assertFalse(vm.uiState.value.isInputSheetVisible)
        vm.onResumeReview()
        assertTrue(vm.uiState.value.isQueueSheetVisible)
        assertEquals(1, queue.queue.value.size)
    }

    @Test
    fun `importing more merges into the pending queue`() = runTest(testDispatcher) {
        queue.add(entry("a", 2))
        coEvery { resolveImport(any(), any()) } returns
            CollectionImportResolution.Resolved(listOf(entry("a", 3), entry("b")), emptyList(), resolvedLineCount = 2)
        val vm = buildViewModel()

        vm.onImportRequested()
        vm.onImportMore()
        vm.onImportText("3 A\n1 B")
        advanceUntilIdle()

        assertEquals(listOf("a" to 5, "b" to 1), queue.queue.value.map { it.card.scryfallId to it.quantity })
        assertFalse(vm.uiState.value.isUnresolvedDialogVisible)
    }

    @Test
    fun `garbage text is reported without calling scryfall`() = runTest(testDispatcher) {
        val vm = buildViewModel()

        vm.onImportText("just some words")
        advanceUntilIdle()

        assertEquals(CollectionImportError.NothingRecognized, vm.uiState.value.inputError)
        coVerify(exactly = 0) { resolveImport(any(), any()) }
    }

    @Test
    fun `rate limiting is surfaced with the cooldown in seconds`() = runTest(testDispatcher) {
        coEvery { resolveImport(any(), any()) } returns CollectionImportResolution.RateLimited(4_200)
        val vm = buildViewModel()

        vm.onImportText("1 Opt")
        advanceUntilIdle()

        assertEquals(CollectionImportError.RateLimited(5), vm.uiState.value.inputError)
        assertFalse(vm.uiState.value.isResolving)
        assertTrue(queue.queue.value.isEmpty())
        verify { crashReporter.setCustomKey("collection_import_fail_reason", "rate_limited") }
    }

    @Test
    fun `an oversized file is rejected before parsing`() = runTest(testDispatcher) {
        coEvery { fileGateway.readText("content://big", any()) } throws FileTooLargeException(1)
        val vm = buildViewModel()

        vm.onImportFile("content://big")
        advanceUntilIdle()

        assertEquals(CollectionImportError.FileTooLarge, vm.uiState.value.inputError)
    }

    @Test
    fun `a file is parsed directly and never exposed to the text field`() = runTest(testDispatcher) {
        coEvery { fileGateway.readText("content://list", any()) } returns "Count,Name,Edition\n2,Opt,xln"
        coEvery { resolveImport(any(), any()) } returns
            CollectionImportResolution.Resolved(listOf(entry("opt", 2)), emptyList(), resolvedLineCount = 1)
        val vm = buildViewModel()

        vm.onImportFile("content://list")
        advanceUntilIdle()

        coVerify { resolveImport(match { it.lines.single().name == "Opt" && it.lines.single().quantity == 2 }, any()) }
        verify { crashReporter.setCustomKey("collection_import_source", "file") }
        verify { crashReporter.setCustomKey("collection_import_format", "MOXFIELD_CSV") }
    }

    @Test
    fun `add all commits without the scan path and clears the review`() = runTest(testDispatcher) {
        coEvery { userCardRepository.addOrIncrementBatch(any(), any()) } returns emptyList()
        queue.addAll(listOf(entry("a"), entry("b")))
        val vm = buildViewModel()
        vm.onOpenQueueSheet()

        vm.onAddAllToCollection()
        advanceUntilIdle()

        assertTrue(queue.queue.value.isEmpty())
        assertFalse(vm.uiState.value.isQueueSheetVisible)
        assertEquals(CollectionImportToast.AddedAllToCollection(2), vm.uiState.value.queueToast)
        verify { crashReporter.log("collection_import_committed") }
    }
}
