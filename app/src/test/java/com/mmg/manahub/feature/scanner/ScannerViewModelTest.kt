package com.mmg.manahub.feature.scanner

import android.content.Context
import android.graphics.PointF
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.domain.repository.UserCardRepository
import com.mmg.manahub.core.domain.usecase.collection.CommitScannedCardsUseCase
import com.mmg.manahub.core.util.AnalyticsHelper
import com.mmg.manahub.feature.scanner.domain.model.RecognitionResult
import com.mmg.manahub.feature.scanner.presentation.ScannerViewModel
import com.mmg.manahub.feature.scanner.presentation.SoundManager
import com.mmg.manahub.feature.trades.domain.usecase.AddToWishlistUseCase
import com.mmg.manahub.util.TestFixtures
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
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
import org.junit.Rule
import org.junit.Test

/**
 * Unit tests for [ScannerViewModel] — ML embedding scanner architecture.
 *
 * Covers:
 * - Initial default state
 * - NoCard result: state unchanged
 * - Stability buffer — normal path: a single frame with similarity ≥ 1.0 (exact name OCR)
 *   confirms the card ([HIGH_CONFIDENCE_FRAMES]=1)
 * - Anti-duplicate guard: same card within 800 ms is blocked
 * - Set lock filter: mismatched setCode is rejected before stability
 * - Language fallback (W2.11, 2026-08-24): informational only — a fallback add sets
 *   languageMismatch as a badge but the card IS added; a genuine localized-printing hit never
 *   sets it
 * - Rate-limit cooldown (W2.10): RecognitionResult.RateLimited sets rateLimitedUntilMs
 * - onLanguageSelected resets: clears rateLimitedUntilMs/languageMismatch (the CardRecognizer-side
 *   reset — negative cache/generation/stability buffer — is Composable-scoped, covered by
 *   CardRecognizerTest instead, see that class's KDoc)
 * - Ambiguity selector: ambiguous → showAmbiguitySelector=true
 * - UI toggle actions: flash, queue sheet, sound
 *
 * NOTE: [SoundManager] and [AnalyticsHelper] are relaxed mocks — their side-effects
 * (audio playback, Firebase calls) are suppressed in unit tests.
 * [CommitScannedCardsUseCase] is a relaxed mock — the recognition-result tests here only
 * queue cards into the scan session; collection commits (which invoke it) are exercised
 * elsewhere.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScannerViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    // ── Dispatcher ─────────────────────────────────────────────────────────────

    private val testDispatcher = StandardTestDispatcher()

    // ── Mocks ──────────────────────────────────────────────────────────────────

    private val cardRepository: CardRepository = mockk(relaxed = true)
    private val userCardRepository: UserCardRepository = mockk(relaxed = true)
    private val commitScannedCards: CommitScannedCardsUseCase = mockk(relaxed = true)
    private val addToWishlist: AddToWishlistUseCase = mockk()
    private val analyticsHelper: AnalyticsHelper = mockk(relaxed = true)
    private val soundManager: SoundManager = mockk(relaxed = true)
    private val context: Context = mockk(relaxed = true)

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
     * Builds a [RecognitionResult.Identified] for [defaultCard] with default settings.
     *
     * Default [similarity] is 0.85f — above the acceptance threshold (0.80) but below the
     * high-confidence threshold (0.90) — so tests that call this without overriding similarity
     * exercise the 3-frame stability path.  Pass similarity ≥ 0.90f to test the 1-frame path.
     *
     * [languageFallback] defaults to false — W2.11 (scanner-reliability-plan.md, 2026-08-24):
     * true simulates `CardRecognizer` resolving an English-fallback printing because no printing
     * exists in the selected language (informational badge, card is still added).
     */
    private fun identified(
        ambiguous: Boolean = false,
        similarity: Float = 0.85f,
        card: com.mmg.manahub.core.model.Card = defaultCard,
        languageFallback: Boolean = false,
    ) = RecognitionResult.Identified(
        card = card,
        similarity = similarity,
        ambiguous = ambiguous,
        corners = fakeCorners,
        languageFallback = languageFallback,
    )

    // ── Setup / Teardown ───────────────────────────────────────────────────────

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        // Empty collection by default: the "already in collection" badge collector (init block)
        // needs a real Flow — a relaxed mock alone would return Unit for `collect` without ever
        // touching FlowCollector, which happens to be harmless here but this stub keeps intent explicit.
        every { userCardRepository.observeCollection() } returns emptyFlow()
        viewModel = ScannerViewModel(
            cardRepository = cardRepository,
            userCardRepository = userCardRepository,
            commitScannedCards = commitScannedCards,
            addToWishlist = addToWishlist,
            analyticsHelper = analyticsHelper,
            soundManager = soundManager,
            context = context,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Helper: feed N identical Identified results ────────────────────────────

    /**
     * Sends [count] identical [RecognitionResult.Identified] results to the ViewModel.
     *
     * Defaults to [similarity]=0.85f so that tests exercise the normal stability path
     * ([STABILITY_FRAMES]=3). Use [similarity]≥0.90f to exercise the high-confidence
     * path ([HIGH_CONFIDENCE_FRAMES]=1).
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
        assertFalse(state.showQueueSheet)
        assertNull(state.toastMessage)
        assertTrue(state.isSoundEnabled)
        assertTrue(state.hasFlash)             // defaults to true until hardware confirms
        assertFalse(state.isFlashOn)
        assertNull(state.rateLimitedUntilMs)
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
        // Session and flags must not change
        assertEquals(stateBefore.scanSession, stateAfter.scanSession)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 3 — Stability buffer
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun onRecognitionResult_stabilityBuffer_confirmsImmediately_forHighConfidenceMatch() = runTest {
        // Arrange — similarity=1.0 (exact OCR match) → needs 1 frame
        // Act — single frame with high-confidence similarity
        viewModel.onRecognitionResult(identified(similarity = 1.0f))
        advanceUntilIdle()

        // Assert — card confirmed and added after just 1 frame
        assertFalse(
            "Session should contain the card after a single high-confidence frame",
            viewModel.uiState.value.scanSession.cards.isEmpty(),
        )
        assertEquals(defaultCard.scryfallId, viewModel.uiState.value.scanSession.cards.first().card.scryfallId)
    }

    @Test
    fun onRecognitionResult_identified_addsToSession() = runTest {
        // Arrange
        // Act — satisfy the stability buffer (1 frame for exact name OCR)
        viewModel.onRecognitionResult(identified(similarity = 1.0f))
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
        // Act — first successful add
        viewModel.onRecognitionResult(identified(similarity = 1.0f))
        advanceUntilIdle()

        val countAfterFirst = viewModel.uiState.value.scanSession.cards.sumOf { it.quantity }

        // Act — immediately try to add the same card again (within 800 ms window)
        viewModel.onRecognitionResult(identified(similarity = 1.0f))
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

        // Act — card from set "lea" but lock is "khm"
        viewModel.onRecognitionResult(identified(similarity = 1.0f))
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

        // Act
        viewModel.onRecognitionResult(identified(similarity = 1.0f))
        advanceUntilIdle()

        // Assert — card passes the lock filter and is added
        assertFalse(
            "Set lock match: card should be added",
            viewModel.uiState.value.scanSession.cards.isEmpty(),
        )
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 6 — Language fallback (W2.11, 2026-08-24 — informational, never blocks the add)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun onRecognitionResult_languageFallback_addsCardAndSetsInformationalBadge() = runTest {
        // Arrange — user selected "es", but CardRecognizer found no Spanish printing and fell
        // back to the English one (languageFallback = true on the incoming result).
        viewModel.onLanguageSelected("es")

        // Act
        viewModel.onRecognitionResult(identified(similarity = 1.0f, languageFallback = true))
        advanceUntilIdle()

        // Assert — languageMismatch surfaces the informational badge, but the card IS added.
        val state = viewModel.uiState.value
        assertTrue(
            "languageMismatch must be set as an informational badge for a fallback add",
            state.languageMismatch,
        )
        assertNotNull(state.lastDetectedCard)
        assertFalse(
            "A language-fallback result must still be added to the session, never silently refused",
            state.scanSession.cards.isEmpty(),
        )
        assertEquals(defaultCard.scryfallId, state.scanSession.cards.first().card.scryfallId)
    }

    @Test
    fun onRecognitionResult_localizedPrinting_neverSetsLanguageMismatch() = runTest {
        // Arrange — the normal (non-fallback) case: CardRecognizer resolved the ACTUAL localized
        // printing, so languageFallback = false even though selectedLanguage != "en".
        viewModel.onLanguageSelected("es")

        // Act
        viewModel.onRecognitionResult(identified(similarity = 1.0f, languageFallback = false))
        advanceUntilIdle()

        // Assert — no badge, card added normally.
        val state = viewModel.uiState.value
        assertFalse(
            "A genuine localized-printing hit must never show the fallback badge",
            state.languageMismatch,
        )
        assertFalse(state.scanSession.cards.isEmpty())
    }

    @Test
    fun onRecognitionResult_englishSelected_languageFallbackNeverFires() = runTest {
        // Arrange — default selectedLanguage = "en"; CardRecognizer never sets languageFallback
        // = true on the English ladder, but this asserts the ViewModel trusts the flag either way.
        // Act
        viewModel.onRecognitionResult(identified(similarity = 1.0f, languageFallback = false))
        advanceUntilIdle()

        // Assert
        assertFalse(viewModel.uiState.value.languageMismatch)
        assertFalse(viewModel.uiState.value.scanSession.cards.isEmpty())
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 6b — RecognitionResult.RateLimited (W2.10, 2026-08-24)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun onRecognitionResult_rateLimited_setsRateLimitedUntilMsAndStopsSearching() = runTest {
        // Arrange
        val beforeMs = System.currentTimeMillis()

        // Act
        viewModel.onRecognitionResult(RecognitionResult.RateLimited(retryAfterMs = 5_000L))
        advanceUntilIdle()

        // Assert
        val state = viewModel.uiState.value
        assertFalse(state.isSearching)
        assertNotNull(state.rateLimitedUntilMs)
        assertTrue(
            "rateLimitedUntilMs must be roughly now + retryAfterMs",
            state.rateLimitedUntilMs!! >= beforeMs + 5_000L,
        )
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 6c — onLanguageSelected resets (W2.11, 2026-08-24)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun onLanguageSelected_clearsRateLimitedUntilMsAndLanguageMismatch() = runTest {
        // Arrange — reach a state with both flags set.
        viewModel.onLanguageSelected("es")
        viewModel.onRecognitionResult(RecognitionResult.RateLimited(retryAfterMs = 30_000L))
        viewModel.onRecognitionResult(identified(similarity = 1.0f, languageFallback = true))
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.rateLimitedUntilMs)
        assertTrue(viewModel.uiState.value.languageMismatch)

        // Act — switching language again is a fresh scanning intent.
        viewModel.onLanguageSelected("de")

        // Assert
        val state = viewModel.uiState.value
        assertEquals("de", state.selectedLanguage)
        assertNull(
            "onLanguageSelected must clear a stale rate-limit cooldown badge from the OLD language",
            state.rateLimitedUntilMs,
        )
        assertFalse(state.languageMismatch)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 7 — Ambiguity selector
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun onRecognitionResult_ambiguous_showsSelector() = runTest {
        // Arrange
        // Act — ambiguous=true
        viewModel.onRecognitionResult(identified(similarity = 1.0f, ambiguous = true))
        advanceUntilIdle()

        // Assert — inline ambiguity selector is triggered
        assertTrue(
            "Ambiguous card should set showAmbiguitySelector=true",
            viewModel.uiState.value.showAmbiguitySelector,
        )
        // Card is set in bottom bar but session remains empty (user must confirm)
        assertNotNull(viewModel.uiState.value.lastDetectedCard)
        assertTrue(viewModel.uiState.value.scanSession.cards.isEmpty())
    }

    @Test
    fun onDismissAmbiguitySelector_clearsSelectorAndCard() = runTest {
        // Arrange — reach ambiguity state
        viewModel.onRecognitionResult(identified(similarity = 1.0f, ambiguous = true))
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
