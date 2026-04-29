package com.mmg.manahub.feature.scanner

import android.content.Context
import android.graphics.PointF
import androidx.lifecycle.MutableLiveData
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.domain.model.DataResult
import com.mmg.manahub.core.domain.usecase.collection.AddCardToCollectionUseCase
import com.mmg.manahub.core.util.AnalyticsHelper
import com.mmg.manahub.util.TestFixtures
import io.mockk.coEvery
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
 * Unit tests for [ScannerViewModel] — pHash-based scanner architecture.
 *
 * Covers:
 * - Initial default state
 * - NoCard result: state unchanged
 * - Stability buffer: requires [STABILITY_FRAMES]=3 consecutive identical matches
 * - Anti-duplicate guard: same card within 800 ms is blocked
 * - Set lock filter: mismatched setCode is rejected before stability
 * - Language mismatch: Quick Mode + language != "en" sets languageMismatch flag
 * - Lookup Only mode: card shown in bar, never added to session
 * - Ambiguity selector: ambiguous + normal mode → showAmbiguitySelector=true
 * - UI toggle actions: flash, queue sheet, sound
 *
 * NOTE: [SoundManager] and [AnalyticsHelper] are relaxed mocks — their side-effects
 * (audio playback, Firebase calls) are suppressed in unit tests.
 * [AddCardToCollectionUseCase] is a suspend operator fun — stubbed with coEvery.
 * [HashDatabase] is used as a relaxed mock (the ViewModel exposes it as a val;
 * the scanner does not invoke it during recognition result processing).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScannerViewModelTest {

    // ── Dispatcher ─────────────────────────────────────────────────────────────

    private val testDispatcher = StandardTestDispatcher()

    // ── Mocks ──────────────────────────────────────────────────────────────────

    private val addToCollection: AddCardToCollectionUseCase = mockk()
    private val analyticsHelper: AnalyticsHelper = mockk(relaxed = true)
    private val soundManager: SoundManager = mockk(relaxed = true)
    private val hashDatabase: HashDatabase = HashDatabase(mockk<Context>(relaxed = true))
    private val userPreferencesDataStore: UserPreferencesDataStore = mockk(relaxed = true) {
        every { hashDbVersionFlow } returns flowOf(0)
    }
    private val workManager: WorkManager = mockk(relaxed = true) {
        every { getWorkInfosForUniqueWorkLiveData(any()) } returns
            MutableLiveData(emptyList<WorkInfo>())
    }

    // ── ViewModel under test ───────────────────────────────────────────────────

    private lateinit var viewModel: ScannerViewModel

    // ── Sample data ────────────────────────────────────────────────────────────

    /** Default card — setCode "lea", lang "en". */
    private val defaultCard = TestFixtures.buildCard(
        scryfallId = "card-abc-001",
        name = "Lightning Bolt",
        setCode = "lea",
    )

    /** Fake corner points — content is irrelevant for ViewModel logic. */
    private val fakeCorners: List<PointF> = listOf(
        PointF(0f, 0f), PointF(100f, 0f), PointF(100f, 140f), PointF(0f, 140f),
    )

    /**
     * Builds an [RecognitionResult.Identified] for [defaultCard] with default settings.
     */
    private fun identified(
        ambiguous: Boolean = false,
        confidence: Float = 0.95f,
    ) = RecognitionResult.Identified(
        card = defaultCard,
        confidence = confidence,
        ambiguous = ambiguous,
        corners = fakeCorners,
    )

    // ── Setup / Teardown ───────────────────────────────────────────────────────

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = ScannerViewModel(
            addToCollection = addToCollection,
            analyticsHelper = analyticsHelper,
            soundManager = soundManager,
            hashDatabase = hashDatabase,
            userPreferencesDataStore = userPreferencesDataStore,
            workManager = workManager,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Helper: feed N identical Identified results ────────────────────────────

    /**
     * Sends [count] identical [RecognitionResult.Identified] results to the ViewModel.
     * The stability buffer requires exactly [STABILITY_FRAMES]=3 to confirm a card.
     */
    private fun repeatIdentified(count: Int, result: RecognitionResult.Identified = identified()) {
        repeat(count) { viewModel.onRecognitionResult(result) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 1 — Initial state
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun initialState_isCorrect() {
        val state = viewModel.uiState.value

        assertNull(state.lastDetectedCard)
        assertTrue(state.scanSession.cards.isEmpty())
        assertFalse(state.isSearching)
        assertFalse(state.showAmbiguitySelector)
        assertFalse(state.languageMismatch)
        assertNull(state.lockedSetCode)
        assertTrue(state.isQuickMode)          // Quick Mode ON by default
        assertFalse(state.isLookupOnly)
        assertFalse(state.showQueueSheet)
        assertFalse(state.showSettingsSheet)
        assertNull(state.toastMessage)
        assertTrue(state.isSoundEnabled)
        assertTrue(state.hasFlash)             // defaults to true until hardware confirms
        assertFalse(state.isFlashOn)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 2 — RecognitionResult.NoCard
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun onRecognitionResult_noCard_doesNotChangeState() {
        // Arrange — default initial state
        val stateBefore = viewModel.uiState.value

        // Act
        viewModel.onRecognitionResult(RecognitionResult.NoCard)

        // Assert — only transient overlay fields cleared; session/modes unchanged
        val stateAfter = viewModel.uiState.value
        assertNull(stateAfter.detectedCorners)
        assertFalse(stateAfter.isSearching)
        assertFalse(stateAfter.languageMismatch)
        // Session and mode flags must not change
        assertEquals(stateBefore.scanSession, stateAfter.scanSession)
        assertEquals(stateBefore.isQuickMode, stateAfter.isQuickMode)
        assertEquals(stateBefore.isLookupOnly, stateAfter.isLookupOnly)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 3 — Stability buffer (Quick Mode)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun onRecognitionResult_stabilityBuffer_requiresThreeConsecutive() = runTest {
        // Arrange — Quick Mode ON, addToCollection succeeds
        coEvery {
            addToCollection(
                scryfallId = any(),
                isFoil = any(),
                condition = any(),
                language = any(),
            )
        } returns DataResult.Success(Unit)

        // Act — only 2 frames: should NOT confirm
        repeatIdentified(2)
        advanceUntilIdle()

        // Assert — card not yet added after 2 frames
        assertTrue(
            "Session should still be empty after only 2 stability frames",
            viewModel.uiState.value.scanSession.cards.isEmpty(),
        )

        // Act — 3rd frame: should confirm and add
        viewModel.onRecognitionResult(identified())
        advanceUntilIdle()

        // Assert — card added after 3rd consecutive frame
        assertFalse(
            "Session should contain the card after 3 consecutive frames",
            viewModel.uiState.value.scanSession.cards.isEmpty(),
        )
    }

    @Test
    fun onRecognitionResult_identified_quickMode_addsToSession() = runTest {
        // Arrange
        coEvery {
            addToCollection(
                scryfallId = any(),
                isFoil = any(),
                condition = any(),
                language = any(),
            )
        } returns DataResult.Success(Unit)

        // Act — 3 consecutive frames to satisfy stability buffer
        repeatIdentified(3)
        advanceUntilIdle()

        // Assert — card appears in session
        val session = viewModel.uiState.value.scanSession
        assertFalse(session.cards.isEmpty())
        assertEquals(defaultCard.scryfallId, session.cards.first().card.scryfallId)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 4 — Anti-duplicate guard
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun onRecognitionResult_antiDuplicate_blocksWithin800ms() = runTest {
        // Arrange
        coEvery {
            addToCollection(
                scryfallId = any(),
                isFoil = any(),
                condition = any(),
                language = any(),
            )
        } returns DataResult.Success(Unit)

        // Act — first successful add (3 frames)
        repeatIdentified(3)
        advanceUntilIdle()

        val countAfterFirst = viewModel.uiState.value.scanSession.cards.sumOf { it.quantity }

        // Act — immediately try to add the same card again (within 800 ms window)
        // The anti-duplicate guard clears recentMatches and returns early.
        // We need 3 new frames but the guard fires on the first one.
        viewModel.onRecognitionResult(identified())
        advanceUntilIdle()

        val countAfterSecond = viewModel.uiState.value.scanSession.cards.sumOf { it.quantity }

        // Assert — count unchanged; second scan within 800 ms was blocked
        assertEquals(
            "Anti-duplicate guard should block same card within 800 ms",
            countAfterFirst,
            countAfterSecond,
        )
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 5 — Set lock filter
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun onRecognitionResult_setLock_mismatch_doesNotAdd() = runTest {
        // Arrange — lock to a different set than the card's setCode ("lea")
        viewModel.onSetLockSelected("khm")

        coEvery {
            addToCollection(
                scryfallId = any(),
                isFoil = any(),
                condition = any(),
                language = any(),
            )
        } returns DataResult.Success(Unit)

        // Act — 3 frames with a card from set "lea" but lock is "khm"
        repeatIdentified(3)
        advanceUntilIdle()

        // Assert — card rejected by set lock; session empty
        assertTrue(
            "Set lock mismatch: card should not be added",
            viewModel.uiState.value.scanSession.cards.isEmpty(),
        )
    }

    @Test
    fun onRecognitionResult_setLock_match_addsCard() = runTest {
        // Arrange — lock matches the card's setCode
        viewModel.onSetLockSelected("lea")

        coEvery {
            addToCollection(
                scryfallId = any(),
                isFoil = any(),
                condition = any(),
                language = any(),
            )
        } returns DataResult.Success(Unit)

        // Act
        repeatIdentified(3)
        advanceUntilIdle()

        // Assert — card passes the lock filter and is added
        assertFalse(
            "Set lock match: card should be added",
            viewModel.uiState.value.scanSession.cards.isEmpty(),
        )
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 6 — Language mismatch (Quick Mode)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun onRecognitionResult_languageMismatch_quickMode_setsFlag() = runTest {
        // Arrange — Quick Mode ON, selectedLanguage = "ja", card.lang = "en"
        // The language filter only triggers when selectedLanguage != "en"
        viewModel.onLanguageSelected("ja")

        coEvery {
            addToCollection(
                scryfallId = any(),
                isFoil = any(),
                condition = any(),
                language = any(),
            )
        } returns DataResult.Success(Unit)

        // Act — 3 frames to satisfy stability buffer
        repeatIdentified(3)
        advanceUntilIdle()

        // Assert — languageMismatch is true; card was NOT auto-added
        val state = viewModel.uiState.value
        assertTrue(
            "Language mismatch flag should be set when card.lang != selectedLanguage in Quick Mode",
            state.languageMismatch,
        )
        // Card is shown in bottom bar (lastDetectedCard set) but session is empty
        assertNotNull(state.lastDetectedCard)
        assertTrue(
            "Session should be empty when language mismatch in Quick Mode",
            state.scanSession.cards.isEmpty(),
        )
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 7 — Lookup Only mode
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun onRecognitionResult_lookupOnly_setsLastDetectedCard() = runTest {
        // Arrange — enable Lookup Only (disables both Quick Mode auto-add and manual add)
        viewModel.onToggleLookupOnly()  // isLookupOnly = true
        // Also turn off Quick Mode to isolate Lookup Only behaviour
        viewModel.onToggleQuickMode()   // isQuickMode = false

        // Act — 3 frames to confirm card
        repeatIdentified(3)
        advanceUntilIdle()

        // Assert — card shown in bottom bar but NOT added to session
        val state = viewModel.uiState.value
        assertNotNull(
            "Lookup Only: lastDetectedCard should be set",
            state.lastDetectedCard,
        )
        assertTrue(
            "Lookup Only: session must remain empty — card is never added",
            state.scanSession.cards.isEmpty(),
        )
        assertFalse(
            "Lookup Only: languageMismatch should be cleared in this mode",
            state.languageMismatch,
        )
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 8 — Ambiguity selector (normal mode)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun onRecognitionResult_ambiguous_normalMode_showsSelector() = runTest {
        // Arrange — disable Quick Mode and Lookup Only to enter normal mode
        viewModel.onToggleQuickMode()   // isQuickMode = false; isLookupOnly already false

        // Act — 3 frames with ambiguous=true
        repeatIdentified(3, result = identified(ambiguous = true))
        advanceUntilIdle()

        // Assert — inline ambiguity selector is triggered
        assertTrue(
            "Ambiguous card in normal mode should set showAmbiguitySelector=true",
            viewModel.uiState.value.showAmbiguitySelector,
        )
        // Card is set in bottom bar but session remains empty (user must confirm)
        assertNotNull(viewModel.uiState.value.lastDetectedCard)
        assertTrue(viewModel.uiState.value.scanSession.cards.isEmpty())
    }

    @Test
    fun onDismissAmbiguitySelector_clearsSelectorAndCard() = runTest {
        // Arrange — reach ambiguity state
        viewModel.onToggleQuickMode()
        repeatIdentified(3, result = identified(ambiguous = true))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.showAmbiguitySelector)

        // Act
        viewModel.onDismissAmbiguitySelector()

        // Assert
        assertFalse(viewModel.uiState.value.showAmbiguitySelector)
        assertNull(viewModel.uiState.value.lastDetectedCard)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 9 — UI toggle actions
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun onToggleFlash_updatesState() {
        // Arrange
        assertFalse(viewModel.uiState.value.isFlashOn)

        // Act
        viewModel.onToggleFlash()

        // Assert
        assertTrue(viewModel.uiState.value.isFlashOn)

        // Act — toggle back
        viewModel.onToggleFlash()
        assertFalse(viewModel.uiState.value.isFlashOn)
    }

    @Test
    fun onOpenQueue_updatesShowQueueSheet() {
        // Arrange
        assertFalse(viewModel.uiState.value.showQueueSheet)

        // Act
        viewModel.onOpenQueue()

        // Assert
        assertTrue(viewModel.uiState.value.showQueueSheet)
    }

    @Test
    fun onCloseQueue_hidesQueueSheet() {
        // Arrange
        viewModel.onOpenQueue()
        assertTrue(viewModel.uiState.value.showQueueSheet)

        // Act
        viewModel.onCloseQueue()

        // Assert
        assertFalse(viewModel.uiState.value.showQueueSheet)
    }

    @Test
    fun onToggleSound_updatesState() {
        // Arrange — sound is enabled by default
        assertTrue(viewModel.uiState.value.isSoundEnabled)

        // Act
        viewModel.onToggleSound()

        // Assert
        assertFalse(viewModel.uiState.value.isSoundEnabled)

        // Act — toggle back
        viewModel.onToggleSound()
        assertTrue(viewModel.uiState.value.isSoundEnabled)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 10 — Miscellaneous state transitions
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun onToastDismissed_clearsToastMessage() = runTest {
        // Arrange — trigger a successful add to produce a toast
        coEvery {
            addToCollection(
                scryfallId = any(),
                isFoil = any(),
                condition = any(),
                language = any(),
            )
        } returns DataResult.Success(Unit)

        repeatIdentified(3)
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.toastMessage)

        // Act
        viewModel.onToastDismissed()

        // Assert
        assertNull(viewModel.uiState.value.toastMessage)
    }

    @Test
    fun onClearSession_emptiesSessionAndResetsGuard() = runTest {
        // Arrange — add a card first
        coEvery {
            addToCollection(
                scryfallId = any(),
                isFoil = any(),
                condition = any(),
                language = any(),
            )
        } returns DataResult.Success(Unit)

        repeatIdentified(3)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.scanSession.cards.isEmpty())

        // Act
        viewModel.onClearSession()

        // Assert
        assertTrue(viewModel.uiState.value.scanSession.cards.isEmpty())
        assertFalse(viewModel.uiState.value.showQueueSheet)
    }
}
