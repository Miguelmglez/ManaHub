package com.mmg.manahub.feature.collection

import androidx.lifecycle.SavedStateHandle
import androidx.work.WorkManager
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.domain.auth.AuthRepository
import com.mmg.manahub.core.domain.auth.SessionState
import com.mmg.manahub.core.domain.collection.transfer.CollectionFileFormat
import com.mmg.manahub.core.domain.collection.transfer.CollectionFileGateway
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.domain.repository.OpenForTradeRepository
import com.mmg.manahub.core.domain.repository.UserCardRepository
import com.mmg.manahub.core.domain.repository.UserPreferencesRepository
import com.mmg.manahub.core.domain.repository.WishlistRepository
import com.mmg.manahub.core.domain.usecase.collection.GetCollectionUseCase
import com.mmg.manahub.core.model.CollectionViewMode
import com.mmg.manahub.core.model.UserCardWithCard
import com.mmg.manahub.core.sync.SyncManager
import com.mmg.manahub.core.sync.SyncState
import com.mmg.manahub.core.util.AnalyticsHelper
import com.mmg.manahub.feature.collection.presentation.CollectionViewModel
import com.mmg.manahub.feature.collection.presentation.canExport
import com.mmg.manahub.feature.collection.presentation.importexport.CollectionExportAction
import com.mmg.manahub.feature.collection.presentation.importexport.CollectionExportMessage
import com.mmg.manahub.feature.trades.domain.usecase.GetLocalWishlistUseCase
import com.mmg.manahub.feature.trades.domain.usecase.MigrateLocalTradeListsUseCase
import com.mmg.manahub.util.TestFixtures
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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

/** Export state of [CollectionViewModel]: visible-row selection, hydration, and share/save outcomes. */
@OptIn(ExperimentalCoroutinesApi::class)
class CollectionViewModelExportTest {

    private val testDispatcher = StandardTestDispatcher()
    private val getCollection = mockk<GetCollectionUseCase>()
    private val cardRepository = mockk<CardRepository>(relaxed = true)
    private val authRepository = mockk<AuthRepository>(relaxed = true)
    private val syncManager = mockk<SyncManager>(relaxed = true)
    private val getLocalWishlist = mockk<GetLocalWishlistUseCase>(relaxed = true)
    private val openForTradeRepository = mockk<OpenForTradeRepository>(relaxed = true)
    private val userPreferencesRepository = mockk<UserPreferencesRepository>(relaxed = true)
    private val fileGateway = mockk<CollectionFileGateway>(relaxed = true)
    private val crashReporter = mockk<CrashReporter>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic(FirebaseCrashlytics::class)
        every { FirebaseCrashlytics.getInstance() } returns mockk(relaxed = true)
        every { userPreferencesRepository.collectionViewModeFlow } returns flowOf(CollectionViewMode.GRID)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(FirebaseCrashlytics::class)
    }

    private fun buildViewModel(entries: List<UserCardWithCard>): CollectionViewModel {
        every { getCollection() } returns flowOf(entries)
        coEvery { authRepository.getCurrentUser() } returns null
        every { authRepository.sessionState } returns MutableStateFlow(SessionState.Unauthenticated)
        every { syncManager.syncState } returns MutableStateFlow(SyncState.IDLE)
        every { getLocalWishlist() } returns flowOf(emptyList())
        every { openForTradeRepository.observeLocal() } returns flowOf(emptyList())
        return CollectionViewModel(
            savedStateHandle = SavedStateHandle(),
            getCollection = getCollection,
            cardRepository = cardRepository,
            userCardRepository = mockk<UserCardRepository>(relaxed = true),
            authRepository = authRepository,
            syncManager = syncManager,
            workManager = mockk<WorkManager>(relaxed = true),
            migrateLocalTradeLists = mockk<MigrateLocalTradeListsUseCase>(relaxed = true),
            getLocalWishlist = getLocalWishlist,
            wishlistRepository = mockk<WishlistRepository>(relaxed = true),
            openForTradeRepository = openForTradeRepository,
            userPreferencesRepository = userPreferencesRepository,
            analyticsHelper = mockk<AnalyticsHelper>(relaxed = true),
            collectionMergeConflictResolver = mockk(relaxed = true),
            fileGateway = fileGateway,
            crashReporter = crashReporter,
            exportDispatcher = testDispatcher,
            nowMillis = { 1_758_499_200_000L },
        )
    }

    private fun row(id: String, name: String, quantity: Int = 1, isFoil: Boolean = false) =
        TestFixtures.buildUserCardWithCard(
            userCard = TestFixtures.buildUserCard(id = id, scryfallId = id, quantity = quantity, isFoil = isFoil),
            card = TestFixtures.buildCard(scryfallId = id, name = name),
        )

    @Test
    fun `export is unavailable while nothing is visible`() = runTest(testDispatcher) {
        val vm = buildViewModel(emptyList())
        advanceUntilIdle()

        vm.onExportRequested()

        assertFalse(vm.uiState.value.canExport)
        assertFalse(vm.uiState.value.export.isSheetVisible)
    }

    @Test
    fun `share writes only the searched rows and exposes a share location`() = runTest(testDispatcher) {
        val content = slot<String>()
        coEvery { fileGateway.writeShareableFile(any(), capture(content)) } returns "content://export"
        val vm = buildViewModel(listOf(row("a", "Lightning Bolt", 4, isFoil = true), row("b", "Counterspell")))
        advanceUntilIdle()
        vm.onSearchQueryChange("bolt")
        advanceUntilIdle()

        vm.onExportRequested()
        vm.onExportShare()
        advanceUntilIdle()

        val export = vm.uiState.value.export
        assertEquals("content://export", export.pendingShare?.location)
        assertEquals("text/plain", export.pendingShare?.mimeType)
        assertEquals(CollectionExportMessage.Completed(CollectionExportAction.SHARE, rows = 1, skippedRows = 0), export.message)
        assertTrue(content.captured.contains("4 Lightning Bolt (LEA) "))
        assertTrue(content.captured.contains("*F*"))
        assertFalse(content.captured.contains("Counterspell"))
    }

    @Test
    fun `save uses the picked location and the selected csv format`() = runTest(testDispatcher) {
        val content = slot<String>()
        coEvery { fileGateway.writeText("content://picked", capture(content)) } returns Unit
        val vm = buildViewModel(listOf(row("a", "Lightning Bolt")))
        advanceUntilIdle()

        vm.onExportFormatSelected(CollectionFileFormat.MANABOX_CSV)
        assertEquals("manahub-collection-2025-09-22.csv", vm.exportFileName().replace(Regex("\\d{4}-\\d{2}-\\d{2}"), "2025-09-22"))
        vm.onExportSave("content://picked")
        advanceUntilIdle()

        assertTrue(content.captured.startsWith("Name,Set code,Set name"))
        assertTrue(content.captured.contains(",a,"))
    }

    @Test
    fun `placeholder rows are hydrated first and still-unresolved ones are reported as skipped`() = runTest(testDispatcher) {
        val placeholderA = row("a", "Unresolved card (a)").let {
            it.copy(card = it.card.copy(isStale = true, staleReason = "pending_hydration"))
        }
        val placeholderB = row("b", "Unresolved card (b)").let {
            it.copy(card = it.card.copy(isStale = true, staleReason = "pending_hydration"))
        }
        coEvery { cardRepository.getCardsByIds(listOf("a", "b")) } returns
            listOf(TestFixtures.buildCard(scryfallId = "a", name = "Sol Ring"))
        coEvery { fileGateway.writeShareableFile(any(), any()) } returns "content://export"
        val vm = buildViewModel(listOf(placeholderA, placeholderB))
        advanceUntilIdle()

        vm.onExportShare()
        advanceUntilIdle()

        coVerify(exactly = 1) { cardRepository.warmCacheForIds(listOf("a", "b")) }
        assertEquals(
            CollectionExportMessage.Completed(CollectionExportAction.SHARE, rows = 1, skippedRows = 1),
            vm.uiState.value.export.message,
        )
    }

    @Test
    fun `a write failure surfaces a failed message and keeps the sheet open`() = runTest(testDispatcher) {
        coEvery { fileGateway.writeShareableFile(any(), any()) } throws java.io.IOException("disk full")
        val vm = buildViewModel(listOf(row("a", "Lightning Bolt")))
        advanceUntilIdle()

        vm.onExportRequested()
        vm.onExportShare()
        advanceUntilIdle()

        assertEquals(CollectionExportMessage.Failed, vm.uiState.value.export.message)
        assertTrue(vm.uiState.value.export.isSheetVisible)
        assertFalse(vm.uiState.value.export.isExporting)
    }
}
