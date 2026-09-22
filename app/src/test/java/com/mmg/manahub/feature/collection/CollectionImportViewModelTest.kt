package com.mmg.manahub.feature.collection

import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.data.queue.InMemoryCardQueueStore
import com.mmg.manahub.core.data.queue.PersistentCardQueueRepository
import com.mmg.manahub.core.domain.collection.transfer.CollectionFileGateway
import com.mmg.manahub.core.domain.collection.transfer.CollectionImportResolution
import com.mmg.manahub.core.domain.collection.transfer.CollectionImportUnresolvedStore
import com.mmg.manahub.core.domain.collection.transfer.FileTooLargeException
import com.mmg.manahub.core.domain.collection.transfer.MAX_PERSISTED_UNRESOLVED_LINES
import com.mmg.manahub.core.domain.collection.transfer.ResolveCollectionImportUseCase
import com.mmg.manahub.core.domain.repository.CardQueueRepository
import com.mmg.manahub.core.domain.repository.UserCardRepository
import com.mmg.manahub.core.domain.repository.UserPreferencesRepository
import com.mmg.manahub.core.domain.usecase.collection.CommitImportedCardsUseCase
import com.mmg.manahub.core.domain.usecase.queue.CardQueueActions
import com.mmg.manahub.core.model.PreferredCurrency
import com.mmg.manahub.core.model.QueuedCard
import com.mmg.manahub.core.ui.components.MagicToastType
import com.mmg.manahub.feature.collection.presentation.importexport.CollectionImportError
import com.mmg.manahub.feature.collection.presentation.importexport.CollectionImportToast
import com.mmg.manahub.feature.collection.presentation.importexport.CollectionImportViewModel
import com.mmg.manahub.feature.collection.presentation.importexport.toastType
import com.mmg.manahub.util.TestFixtures
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.coroutines.ContinuationInterceptor
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
    private lateinit var unresolvedStore: FakeUnresolvedStore

    /** A distinct dispatcher over the same scheduler, so "ran off the caller's thread" is assertable. */
    private lateinit var parseDispatcher: TestDispatcher

    private class FakeUnresolvedStore(var lines: List<String> = emptyList()) : CollectionImportUnresolvedStore {
        override fun read(): List<String> = lines
        override fun write(lines: List<String>) {
            this.lines = lines.take(MAX_PERSISTED_UNRESOLVED_LINES)
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        parseDispatcher = StandardTestDispatcher(testDispatcher.scheduler)
        every { userPreferencesRepository.preferredCurrencyFlow } returns flowOf(PreferredCurrency.USD)
        every { userCardRepository.observeCollection() } returns flowOf(emptyList())
        queue = PersistentCardQueueRepository(InMemoryCardQueueStore())
        unresolvedStore = FakeUnresolvedStore()
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
        unresolvedStore = unresolvedStore,
        crashReporter = crashReporter,
        appScope = CoroutineScope(testDispatcher),
        parseDispatcher = parseDispatcher,
        ioDispatcher = testDispatcher,
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
        verify { crashReporter.log("collection_import_failed_rate_limited") }
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
        verify { crashReporter.setCustomKey("collection_import_input", "file:MOXFIELD_CSV") }
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
        verify { crashReporter.log("collection_import_committed_collection") }
    }

    // ── C1: the review queue is bounded ──────────────────────────────────────

    @Test
    fun `a list longer than the queue cap is rejected before any scryfall call`() = runTest(testDispatcher) {
        val vm = buildViewModel()
        val lines = (1..CollectionImportViewModel.MAX_IMPORT_QUEUE_ENTRIES + 1).joinToString("\n") { "1 Card $it" }

        vm.onImportText(lines)
        advanceUntilIdle()

        assertEquals(
            CollectionImportError.TooManyLines(CollectionImportViewModel.MAX_IMPORT_QUEUE_ENTRIES),
            vm.uiState.value.inputError,
        )
        coVerify(exactly = 0) { resolveImport(any(), any()) }
        assertTrue(queue.queue.value.isEmpty())
    }

    @Test
    fun `importing more is refused when it would push the pending review past the cap`() = runTest(testDispatcher) {
        queue.addAll((1..CollectionImportViewModel.MAX_IMPORT_QUEUE_ENTRIES).map { entry("pending-$it") })
        coEvery { resolveImport(any(), any()) } returns
            CollectionImportResolution.Resolved(listOf(entry("new")), emptyList(), resolvedLineCount = 1)
        val vm = buildViewModel()

        vm.onImportText("1 New Card")
        advanceUntilIdle()

        assertEquals(
            CollectionImportError.TooManyLines(CollectionImportViewModel.MAX_IMPORT_QUEUE_ENTRIES),
            vm.uiState.value.inputError,
        )
        assertEquals(CollectionImportViewModel.MAX_IMPORT_QUEUE_ENTRIES, queue.queue.value.size)
        assertFalse(queue.queue.value.any { it.card.scryfallId == "new" })
    }

    @Test
    fun `an oversized paste is rejected with the same ceiling as a picked file`() = runTest(testDispatcher) {
        val vm = buildViewModel()
        val oversized = "a".repeat((CollectionImportViewModel.MAX_FILE_BYTES / 2).toInt() + 1)

        vm.onImportText(oversized)
        advanceUntilIdle()

        assertEquals(CollectionImportError.FileTooLarge, vm.uiState.value.inputError)
        coVerify(exactly = 0) { resolveImport(any(), any()) }
        assertTrue(queue.queue.value.isEmpty())
    }

    // ── C2: resolution runs off the caller's thread ──────────────────────────

    @Test
    fun `resolution runs on the parse dispatcher, never on the caller's`() = runTest(testDispatcher) {
        var interceptor: ContinuationInterceptor? = null
        coEvery { resolveImport(any(), any()) } coAnswers {
            interceptor = currentCoroutineContext()[ContinuationInterceptor]
            CollectionImportResolution.Resolved(listOf(entry("a")), emptyList(), resolvedLineCount = 1)
        }
        val vm = buildViewModel()

        vm.onImportText("1 Opt")
        advanceUntilIdle()

        assertEquals(parseDispatcher, interceptor)
    }

    // ── H1: the 9,999 cap is never silent ────────────────────────────────────

    @Test
    fun `clamped copies reach the toast as a warning`() = runTest(testDispatcher) {
        coEvery { resolveImport(any(), any()) } returns CollectionImportResolution.Resolved(
            entries = listOf(entry("forest", quantity = 9_999)),
            unresolvedLines = emptyList(),
            resolvedLineCount = 1,
            clampedCopies = 2_001,
        )
        val vm = buildViewModel()

        vm.onImportText("6000 Forest (M21) 274\n6000 Forest (M21) 274")
        advanceUntilIdle()

        val toast = vm.uiState.value.queueToast
        assertEquals(CollectionImportToast.Resolved(entries = 1, unresolved = 0, clampedCopies = 2_001), toast)
        assertEquals(MagicToastType.WARNING, toast!!.toastType())
        verify { crashReporter.log("collection_import_quantity_clamped") }
    }

    @Test
    fun `copies clamped while merging into the pending review are reported too`() = runTest(testDispatcher) {
        queue.add(entry("forest", quantity = 9_000))
        coEvery { resolveImport(any(), any()) } returns
            CollectionImportResolution.Resolved(listOf(entry("forest", 5_000)), emptyList(), resolvedLineCount = 1)
        val vm = buildViewModel()

        vm.onImportText("5000 Forest")
        advanceUntilIdle()

        assertEquals(9_999, queue.queue.value.single().quantity)
        assertEquals(
            CollectionImportToast.Resolved(entries = 1, unresolved = 0, clampedCopies = 4_001),
            vm.uiState.value.queueToast,
        )
    }

    // ── H2: unresolved lines survive process death with the queue ────────────

    @Test
    fun `unresolved lines are persisted and restored alongside the queue`() = runTest(testDispatcher) {
        coEvery { resolveImport(any(), any()) } returns
            CollectionImportResolution.Resolved(listOf(entry("a")), listOf("1 Nope", "2 Also nope"), resolvedLineCount = 1)
        val vm = buildViewModel()
        vm.onImportText("1 A\n1 Nope\n2 Also nope")
        advanceUntilIdle()
        assertEquals(listOf("1 Nope", "2 Also nope"), unresolvedStore.lines)

        val restored = buildViewModel()
        advanceUntilIdle()

        assertEquals(listOf("1 Nope", "2 Also nope"), restored.uiState.value.unresolvedLines)
        restored.onImportRequested()
        assertTrue(restored.uiState.value.isResumePromptVisible)
        restored.onShowUnresolved()
        assertTrue(restored.uiState.value.isUnresolvedDialogVisible)
    }

    @Test
    fun `clearing the review clears the persisted unresolved lines`() = runTest(testDispatcher) {
        unresolvedStore.lines = listOf("1 Nope")
        queue.add(entry("a"))
        val vm = buildViewModel()
        advanceUntilIdle()

        vm.onClearQueue()
        advanceUntilIdle()

        assertTrue(unresolvedStore.lines.isEmpty())
        assertTrue(vm.uiState.value.unresolvedLines.isEmpty())
    }

    // ── H6: the owned-cards observer follows what the screen shows ───────────

    @Test
    fun `emptying the review one entry at a time closes the sheet`() = runTest(testDispatcher) {
        coEvery { userCardRepository.addOrIncrementBatch(any(), any()) } returns emptyList()
        queue.add(entry("a"))
        val vm = buildViewModel()
        vm.onOpenQueueSheet()
        vm.onToggleAutoDeleteOnAdd()

        vm.onAddEntryToCollection(queue.queue.value.single())
        advanceUntilIdle()

        assertTrue(queue.queue.value.isEmpty())
        assertFalse(vm.uiState.value.isQueueSheetVisible)
        assertFalse(vm.uiState.value.isQueueSheetOpen)
    }

    @Test
    fun `the queue sheet is not open while the queue is empty`() = runTest(testDispatcher) {
        val vm = buildViewModel()

        vm.onOpenQueueSheet()

        assertFalse(vm.uiState.value.isQueueSheetOpen)
    }
}
