package com.mmg.manahub.feature.scanner.data

import android.media.Image
import androidx.camera.core.ImageInfo
import androidx.camera.core.ImageProxy
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.core.data.local.dao.CardDao
import com.mmg.manahub.core.data.network.RateLimitExhaustedException
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.feature.scanner.domain.model.OcrCandidate
import com.mmg.manahub.feature.scanner.domain.model.RecognitionResult
import com.mmg.manahub.util.TestFixtures
import io.mockk.CapturingSlot
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [CardRecognizer]'s two responsibilities:
 * - WS1's guard/resource-ownership contract (in-flight guard, timeout, stall watchdog, cancellation).
 * - WS2.B's call-budget pipeline (pre-resolution stability, negative cache, local-first Room
 *   lookup, rolling lookup budget, the ≤2-call resolution ladder, staleness rejection, rate-limit
 *   propagation) — see `docs/plans/scanner-reliability-plan.md`.
 *
 * Name-extraction accuracy (zone maths, keyword scoring) is WS2.A scope, not covered here.
 *
 * ### Two-frame stability in tests
 * [CardRecognizer.analyze] now requires the SAME normalized OCR text on
 * [CardRecognizer.STABILITY_FRAMES_PRE_RESOLUTION] (= 2) consecutive PROCESSED frames before any
 * repository/DAO call. [stabilize] sends one throwaway priming frame (via a separate,
 * untracked [ImageProxy] mock) with the same candidate text queued for the test's real
 * [imageProxy], bypassing the real 800 ms wall-clock throttle via the same reflection technique
 * [setLongField] already used for the stall-watchdog tests — so every pre-existing single-frame
 * test now sends 2 frames and asserts against the SECOND (real, tracked) [ImageProxy].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CardRecognizerTest {

    private val cardOcrAnalyzer: CardOcrAnalyzer = mockk()
    private val cardRepository: CardRepository = mockk()
    private val cardDao: CardDao = mockk(relaxed = true) // relaxed: null (no local-cache hit) by default
    private val crashlytics: FirebaseCrashlytics = mockk(relaxed = true)

    private val defaultCard = TestFixtures.buildCard(
        scryfallId = "card-abc-001",
        name = "Lightning Bolt",
    )
    private val secondCard = TestFixtures.buildCard(
        scryfallId = "card-def-002",
        name = "Serra Angel",
    )

    @Before
    fun setUp() {
        mockkStatic(FirebaseCrashlytics::class)
        every { FirebaseCrashlytics.getInstance() } returns crashlytics
        // MockK's relaxed mode returns an auto-generated (non-null, all-default-field) CardEntity
        // for an UNSTUBBED nullable-returning call rather than null -- an explicit default here
        // keeps "no local Room hit" the actual default for every test; the local-first-lookup
        // test overrides this per-name.
        coEvery { cardDao.findByExactNameForLanguage(any(), any()) } returns null
    }

    @After
    fun tearDown() {
        unmockkStatic(FirebaseCrashlytics::class)
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun buildImageProxyMock(): ImageProxy {
        val imageProxy = mockk<ImageProxy>(relaxed = true)
        val mediaImage = mockk<Image>(relaxed = true)
        val imageInfo = mockk<ImageInfo>(relaxed = true)
        every { imageProxy.image } returns mediaImage
        every { imageProxy.imageInfo } returns imageInfo
        every { imageInfo.rotationDegrees } returns 0
        return imageProxy
    }

    private fun CardRecognizer.isProcessingFlag(): Boolean {
        val field = CardRecognizer::class.java.getDeclaredField("isProcessing")
        field.isAccessible = true
        return (field.get(this) as AtomicBoolean).get()
    }

    private fun CardRecognizer.setProcessingFlag(value: Boolean) {
        val field = CardRecognizer::class.java.getDeclaredField("isProcessing")
        field.isAccessible = true
        (field.get(this) as AtomicBoolean).set(value)
    }

    private fun CardRecognizer.setLongField(name: String, value: Long) {
        val field = CardRecognizer::class.java.getDeclaredField(name)
        field.isAccessible = true
        field.setLong(this, value)
    }

    /** Resets the real wall-clock throttle so the next [analyze] call is not dropped. */
    private fun CardRecognizer.bypassThrottle() {
        setLongField("lastProcessedMs", 0L)
    }

    private fun defaultRecognizer(
        scope: CoroutineScope,
        dispatcher: kotlinx.coroutines.CoroutineDispatcher,
        onResult: (RecognitionResult) -> Unit,
    ) = CardRecognizer(
        cardRepository = cardRepository,
        cardOcrAnalyzer = cardOcrAnalyzer,
        scope = scope,
        cardDao = cardDao,
        ioDispatcher = dispatcher,
        onResult = onResult,
    )

    /**
     * Sends a throwaway PRIMING frame carrying the same OCR text the caller is about to send for
     * real, satisfying [CardRecognizer.STABILITY_FRAMES_PRE_RESOLUTION] so the NEXT [analyze]
     * call reaches the actual resolution pipeline. Uses a separate, untracked [ImageProxy] mock
     * so `verify(exactly = 1) { imageProxy.close() }` assertions on the caller's own mock stay
     * valid. Bypasses the real 800 ms throttle before returning so the caller's own [analyze]
     * call is not itself dropped.
     */
    private fun CardRecognizer.stabilize() {
        // Bypass BEFORE too: on a 2nd+ call within the same fast-running test, real wall-clock
        // time since the previous processed frame is almost always < minIntervalMs (800 ms), so
        // without this the priming frame itself would be silently throttle-dropped and never
        // reach the stability buffer at all.
        bypassThrottle()
        analyze(buildImageProxyMock())
        bypassThrottle()
    }

    // ── Success / null-result paths ───────────────────────────────────────────

    @Test
    fun `successful frame closes imageProxy exactly once, releases guard, emits Identified`() = runTest {
        val results = mutableListOf<RecognitionResult>()
        val recognizer = defaultRecognizer(this, StandardTestDispatcher(testScheduler)) { results += it }
        val imageProxy = buildImageProxyMock()
        coEvery { cardOcrAnalyzer.extractCardName(any(), any()) } returns OcrCandidate(text = "Lightning Bolt", score = 100f, lineHeightRatio = 1f)
        coEvery { cardRepository.getCardByExactName("Lightning Bolt") } returns Result.success(defaultCard)

        recognizer.stabilize()
        advanceUntilIdle()
        recognizer.analyze(imageProxy)
        advanceUntilIdle()

        verify(exactly = 1) { imageProxy.close() }
        // 2 results total: the priming frame's NoCard (stability not yet satisfied) + the real
        // frame's Identified.
        assertEquals(2, results.size)
        assertEquals(
            RecognitionResult.Identified(
                card = defaultCard,
                similarity = 1.0f,
                ambiguous = false,
                corners = emptyList(),
                languageFallback = false,
            ),
            results.last(),
        )
        assertFalse(recognizer.isProcessingFlag())
        // WS4: the ocr-score gauge is set once per non-null candidate — priming frame + real frame.
        verify(exactly = 2) { crashlytics.setCustomKey("scanner_ocr_score", 100f) }
    }

    @Test
    fun `null OCR result closes imageProxy exactly once, releases guard, emits NoCard`() = runTest {
        val results = mutableListOf<RecognitionResult>()
        val recognizer = defaultRecognizer(this, StandardTestDispatcher(testScheduler)) { results += it }
        val imageProxy = buildImageProxyMock()
        coEvery { cardOcrAnalyzer.extractCardName(any(), any()) } returns null

        recognizer.analyze(imageProxy)
        advanceUntilIdle()

        verify(exactly = 1) { imageProxy.close() }
        assertEquals(listOf(RecognitionResult.NoCard), results)
        assertFalse(recognizer.isProcessingFlag())
    }

    // ── Timeout ────────────────────────────────────────────────────────────────

    @Test
    fun `stuck OCR call times out, closes imageProxy once, records non-fatal, emits NoCard`() = runTest {
        val results = mutableListOf<RecognitionResult>()
        val recognizer = defaultRecognizer(this, StandardTestDispatcher(testScheduler)) { results += it }
        val imageProxy = buildImageProxyMock()
        coEvery { cardOcrAnalyzer.extractCardName(any(), any()) } coAnswers { awaitCancellation() }

        recognizer.analyze(imageProxy)
        advanceUntilIdle()

        verify(exactly = 1) { imageProxy.close() }
        assertEquals(listOf(RecognitionResult.NoCard), results)
        assertFalse(recognizer.isProcessingFlag())

        val exceptionSlot: CapturingSlot<Throwable> = slot()
        verify(exactly = 1) { crashlytics.recordException(capture(exceptionSlot)) }
        assertTrue(exceptionSlot.captured.message.orEmpty().contains("scanner_ocr_timeout"))
    }

    // ── Pipeline exception (non-cancellation) ─────────────────────────────────

    @Test
    fun `resolution exception closes imageProxy exactly once, releases guard, emits NoCard`() = runTest {
        val results = mutableListOf<RecognitionResult>()
        val recognizer = defaultRecognizer(this, StandardTestDispatcher(testScheduler)) { results += it }
        val imageProxy = buildImageProxyMock()
        coEvery { cardOcrAnalyzer.extractCardName(any(), any()) } returns OcrCandidate(text = "Lightning Bolt", score = 100f, lineHeightRatio = 1f)
        coEvery { cardRepository.getCardByExactName("Lightning Bolt") } throws RuntimeException("boom")

        recognizer.stabilize()
        advanceUntilIdle()
        recognizer.analyze(imageProxy)
        advanceUntilIdle()

        verify(exactly = 1) { imageProxy.close() }
        // Priming frame's NoCard (stability not yet satisfied) + the real frame's NoCard
        // (repository threw).
        assertEquals(listOf(RecognitionResult.NoCard, RecognitionResult.NoCard), results)
        assertFalse(recognizer.isProcessingFlag())
    }

    // ── Cancellation ───────────────────────────────────────────────────────────

    @Test
    fun `cancellation closes imageProxy exactly once, releases guard, and is not reported as NoCard`() = runTest {
        val results = mutableListOf<RecognitionResult>()
        val childJob = Job()
        val childScope = CoroutineScope(coroutineContext + childJob)
        val recognizer = defaultRecognizer(childScope, StandardTestDispatcher(testScheduler)) { results += it }
        val imageProxy = buildImageProxyMock()
        // Hangs inside the OCR call (not the timeout-bounded path failing on its own) so we
        // can trigger an EXTERNAL cancellation instead of a timeout.
        coEvery { cardOcrAnalyzer.extractCardName(any(), any()) } coAnswers { awaitCancellation() }

        recognizer.analyze(imageProxy)
        runCurrent() // let the coroutine start and suspend inside the OCR call
        childJob.cancel()
        advanceUntilIdle()

        verify(exactly = 1) { imageProxy.close() }
        assertTrue("cancellation must never be reported as a recognition result", results.isEmpty())
        assertFalse(recognizer.isProcessingFlag())
    }

    // ── Stall watchdog ─────────────────────────────────────────────────────────

    @Test
    fun `stall watchdog force-resets a latched guard and processes the new frame`() = runTest {
        val results = mutableListOf<RecognitionResult>()
        val recognizer = defaultRecognizer(this, StandardTestDispatcher(testScheduler)) { results += it }
        coEvery { cardOcrAnalyzer.extractCardName(any(), any()) } returns OcrCandidate(text = "Serra Angel", score = 100f, lineHeightRatio = 1f)
        coEvery { cardRepository.getCardByExactName("Serra Angel") } returns Result.success(secondCard)

        recognizer.stabilize()
        advanceUntilIdle()

        // Simulate a previous frame's coroutine that latched the guard well past the stall
        // threshold — deterministic via reflection rather than a real hanging coroutine,
        // since the guard timestamps are wall-clock (System.currentTimeMillis()), not
        // virtual test time.
        recognizer.setProcessingFlag(true)
        recognizer.setLongField(
            "processingStartedAtMs",
            System.currentTimeMillis() - (CardRecognizer.STALL_THRESHOLD_MS + 1_000),
        )
        recognizer.setLongField(
            "lastProcessedMs",
            System.currentTimeMillis() - 10_000L,
        )

        val imageProxy = buildImageProxyMock()

        recognizer.analyze(imageProxy)
        advanceUntilIdle()

        verify(exactly = 1) { crashlytics.log("scanner_ocr_stall_recovered") }
        verify(exactly = 1) { imageProxy.close() }
        assertEquals(
            RecognitionResult.Identified(
                card = secondCard,
                similarity = 1.0f,
                ambiguous = false,
                corners = emptyList(),
                languageFallback = false,
            ),
            results.last(),
        )
        assertFalse(recognizer.isProcessingFlag())
    }

    @Test
    fun `guard still held within the stall threshold drops the frame without recovering`() = runTest {
        val results = mutableListOf<RecognitionResult>()
        val recognizer = defaultRecognizer(this, StandardTestDispatcher(testScheduler)) { results += it }
        recognizer.setProcessingFlag(true)
        recognizer.setLongField("processingStartedAtMs", System.currentTimeMillis())
        recognizer.setLongField("lastProcessedMs", System.currentTimeMillis() - 10_000L)

        val imageProxy = buildImageProxyMock()

        recognizer.analyze(imageProxy)
        advanceUntilIdle()

        verify(exactly = 1) { imageProxy.close() }
        assertTrue(results.isEmpty())
        verify(exactly = 0) { crashlytics.log("scanner_ocr_stall_recovered") }
        assertTrue(recognizer.isProcessingFlag())
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  W2.5 — pre-resolution stability
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `a single frame never reaches the repository — stability requires 2 consecutive frames`() = runTest {
        val results = mutableListOf<RecognitionResult>()
        val recognizer = defaultRecognizer(this, StandardTestDispatcher(testScheduler)) { results += it }
        coEvery { cardOcrAnalyzer.extractCardName(any(), any()) } returns OcrCandidate(text = "Lightning Bolt", score = 100f, lineHeightRatio = 1f)

        recognizer.analyze(buildImageProxyMock())
        advanceUntilIdle()

        coVerify(exactly = 0) { cardRepository.getCardByExactName(any()) }
        assertEquals(listOf(RecognitionResult.NoCard), results)
    }

    @Test
    fun `two consecutive frames with a DIFFERENT text each never reach stability`() = runTest {
        val results = mutableListOf<RecognitionResult>()
        val recognizer = defaultRecognizer(this, StandardTestDispatcher(testScheduler)) { results += it }
        coEvery { cardOcrAnalyzer.extractCardName(any(), any()) } returnsMany listOf(
            OcrCandidate(text = "Lightning Bolt", score = 100f, lineHeightRatio = 1f),
            OcrCandidate(text = "Serra Angel", score = 100f, lineHeightRatio = 1f),
        )

        recognizer.analyze(buildImageProxyMock())
        advanceUntilIdle()
        recognizer.bypassThrottle()
        recognizer.analyze(buildImageProxyMock())
        advanceUntilIdle()

        coVerify(exactly = 0) { cardRepository.getCardByExactName(any()) }
        assertTrue(results.all { it == RecognitionResult.NoCard })
    }

    @Test
    fun `a candidate below the confidence threshold never reaches the repository, even on a single frame`() = runTest {
        val results = mutableListOf<RecognitionResult>()
        val recognizer = defaultRecognizer(this, StandardTestDispatcher(testScheduler)) { results += it }
        coEvery { cardOcrAnalyzer.extractCardName(any(), any()) } returns
            OcrCandidate(text = "Creature — Human Wizard", score = CardRecognizer.MIN_CONFIDENCE_SCORE - 1f, lineHeightRatio = 0.6f)

        // A single frame suffices: a below-threshold candidate is rejected BEFORE the stability
        // buffer is even consulted, so it never gets a chance to earn 2-frame stability anyway.
        recognizer.analyze(buildImageProxyMock())
        advanceUntilIdle()

        coVerify(exactly = 0) { cardRepository.getCardByExactName(any()) }
        assertEquals(listOf(RecognitionResult.NoCard), results)
        // WS4: the low-confidence skip gate must flush its session counter via setCustomKey.
        verify(exactly = 1) { crashlytics.setCustomKey("scanner_low_confidence_skips_session", 1) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  W2.6 — negative cache
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `a name that already failed this session short-circuits with zero repository calls`() = runTest {
        val results = mutableListOf<RecognitionResult>()
        val recognizer = defaultRecognizer(this, StandardTestDispatcher(testScheduler)) { results += it }
        coEvery { cardOcrAnalyzer.extractCardName(any(), any()) } returns OcrCandidate(text = "Garbage Text", score = 100f, lineHeightRatio = 1f)
        coEvery { cardRepository.getCardByExactName("Garbage Text") } returns Result.failure(RuntimeException("404"))
        coEvery { cardRepository.searchCardByName("Garbage Text") } returns DataResult.Error("SCRYFALL_404")

        // First resolution attempt: reaches the network, fails, gets negative-cached.
        recognizer.stabilize()
        advanceUntilIdle()
        recognizer.analyze(buildImageProxyMock())
        advanceUntilIdle()
        coVerify(exactly = 1) { cardRepository.getCardByExactName("Garbage Text") }
        coVerify(exactly = 1) { cardRepository.searchCardByName("Garbage Text") }

        // Same text again (stability already satisfied — key unchanged) — must be a pure
        // negative-cache hit with ZERO further repository calls.
        recognizer.bypassThrottle()
        recognizer.analyze(buildImageProxyMock())
        advanceUntilIdle()

        coVerify(exactly = 1) { cardRepository.getCardByExactName("Garbage Text") } // still exactly 1
        coVerify(exactly = 1) { cardRepository.searchCardByName("Garbage Text") }   // still exactly 1
        assertTrue(results.all { it == RecognitionResult.NoCard })
        // WS4: exactly 1 negative-cache HIT (the first attempt was a genuine network miss, not a
        // cache hit — the session counter only increments on the second, short-circuited call).
        verify(exactly = 1) { crashlytics.setCustomKey("scanner_negcache_hits_session", 1) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  W2.7 — local-first Room lookup
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `a card already cached locally resolves with zero network calls`() = runTest {
        val results = mutableListOf<RecognitionResult>()
        val recognizer = defaultRecognizer(this, StandardTestDispatcher(testScheduler)) { results += it }
        coEvery { cardOcrAnalyzer.extractCardName(any(), any()) } returns OcrCandidate(text = "Lightning Bolt", score = 100f, lineHeightRatio = 1f)
        val cachedEntity = TestFixtures.buildCardEntity(
            scryfallId = defaultCard.scryfallId,
            name = defaultCard.name,
        )
        coEvery { cardDao.findByExactNameForLanguage("Lightning Bolt", "en") } returns cachedEntity

        recognizer.stabilize()
        advanceUntilIdle()
        recognizer.analyze(buildImageProxyMock())
        advanceUntilIdle()

        coVerify(exactly = 0) { cardRepository.getCardByExactName(any()) }
        coVerify(exactly = 0) { cardRepository.searchCardByName(any()) }
        assertTrue(results.last() is RecognitionResult.Identified)
        assertEquals(defaultCard.scryfallId, (results.last() as RecognitionResult.Identified).card.scryfallId)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  W2.10 — scanner-local lookup budget
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `the 7th distinct lookup attempt within the rolling window is skipped without a network call`() = runTest {
        val results = mutableListOf<RecognitionResult>()
        val recognizer = defaultRecognizer(this, StandardTestDispatcher(testScheduler)) { results += it }
        coEvery { cardRepository.getCardByExactName(any()) } returns Result.failure(RuntimeException("404"))
        coEvery { cardRepository.searchCardByName(any()) } returns DataResult.Error("SCRYFALL_404")

        // 6 DISTINCT names, each satisfying 2-frame stability, each a genuine (failing) network
        // attempt — consumes the whole CardRecognizer.LOOKUP_BUDGET_MAX budget.
        repeat(CardRecognizer.LOOKUP_BUDGET_MAX) { i ->
            val text = "Distinct Name $i"
            coEvery { cardOcrAnalyzer.extractCardName(any(), any()) } returns OcrCandidate(text = text, score = 100f, lineHeightRatio = 1f)
            recognizer.stabilize()
            advanceUntilIdle()
            recognizer.analyze(buildImageProxyMock())
            advanceUntilIdle()
        }
        coVerify(exactly = CardRecognizer.LOOKUP_BUDGET_MAX) { cardRepository.getCardByExactName(any()) }

        // A 7th DISTINCT name, past the budget — must be skipped with zero network calls.
        val overBudgetText = "Distinct Name over-budget"
        coEvery { cardOcrAnalyzer.extractCardName(any(), any()) } returns OcrCandidate(text = overBudgetText, score = 100f, lineHeightRatio = 1f)
        recognizer.stabilize()
        advanceUntilIdle()
        recognizer.analyze(buildImageProxyMock())
        advanceUntilIdle()

        coVerify(exactly = 0) { cardRepository.getCardByExactName(overBudgetText) }
        // WS4: the budget-skip session counter increments exactly once, for the 7th attempt.
        verify(exactly = 1) { crashlytics.setCustomKey("scanner_budget_skips_session", 1) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  W2.8 — resolution ladder (≤2 network calls per attempt)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `english path — exact-name miss falls back to fuzzy search, at most 2 calls`() = runTest {
        val results = mutableListOf<RecognitionResult>()
        val recognizer = defaultRecognizer(this, StandardTestDispatcher(testScheduler)) { results += it }
        coEvery { cardOcrAnalyzer.extractCardName(any(), any()) } returns OcrCandidate(text = "Lightning Bolt", score = 100f, lineHeightRatio = 1f)
        coEvery { cardRepository.getCardByExactName("Lightning Bolt") } returns Result.failure(RuntimeException("404"))
        coEvery { cardRepository.searchCardByName("Lightning Bolt") } returns DataResult.Success(defaultCard)

        recognizer.stabilize()
        advanceUntilIdle()
        recognizer.analyze(buildImageProxyMock())
        advanceUntilIdle()

        coVerify(exactly = 1) { cardRepository.getCardByExactName("Lightning Bolt") }
        coVerify(exactly = 1) { cardRepository.searchCardByName("Lightning Bolt") }
        assertEquals(defaultCard, (results.last() as RecognitionResult.Identified).card)
    }

    @Test
    fun `non-english path — printed-name search success issues exactly 1 call and never falls back`() = runTest {
        val results = mutableListOf<RecognitionResult>()
        val recognizer = defaultRecognizer(this, StandardTestDispatcher(testScheduler)) { results += it }
        recognizer.selectedLanguage = "es"
        val spanishCard = defaultCard.copy(lang = "es", printedName = "Rayo")
        coEvery { cardOcrAnalyzer.extractCardName(any(), any()) } returns OcrCandidate(text = "Rayo", score = 100f, lineHeightRatio = 1f)
        coEvery { cardRepository.searchCardPrintedName("Rayo", "es") } returns DataResult.Success(spanishCard)

        recognizer.stabilize()
        advanceUntilIdle()
        recognizer.analyze(buildImageProxyMock())
        advanceUntilIdle()

        coVerify(exactly = 1) { cardRepository.searchCardPrintedName("Rayo", "es") }
        coVerify(exactly = 0) { cardRepository.getCardByExactName(any()) }
        coVerify(exactly = 0) { cardRepository.searchCardByName(any()) }
        val identified = results.last() as RecognitionResult.Identified
        assertEquals(spanishCard, identified.card)
        assertFalse("a genuine localized hit must not be flagged as a fallback", identified.languageFallback)
    }

    @Test
    fun `non-english path — printed-name search miss falls back to a SINGLE English exact-name call, at most 2 calls total`() = runTest {
        val results = mutableListOf<RecognitionResult>()
        val recognizer = defaultRecognizer(this, StandardTestDispatcher(testScheduler)) { results += it }
        recognizer.selectedLanguage = "es"
        coEvery { cardOcrAnalyzer.extractCardName(any(), any()) } returns OcrCandidate(text = "Lightning Bolt", score = 100f, lineHeightRatio = 1f)
        coEvery { cardRepository.searchCardPrintedName("Lightning Bolt", "es") } returns DataResult.Error("SCRYFALL_404")
        coEvery { cardRepository.getCardByExactName("Lightning Bolt") } returns Result.success(defaultCard) // lang="en"

        recognizer.stabilize()
        advanceUntilIdle()
        recognizer.analyze(buildImageProxyMock())
        advanceUntilIdle()

        coVerify(exactly = 1) { cardRepository.searchCardPrintedName("Lightning Bolt", "es") }
        coVerify(exactly = 1) { cardRepository.getCardByExactName("Lightning Bolt") }
        coVerify(exactly = 0) { cardRepository.searchCardByName(any()) } // never a 3rd call
        val identified = results.last() as RecognitionResult.Identified
        assertEquals(defaultCard, identified.card)
        assertTrue("an English-fallback resolution must be flagged", identified.languageFallback)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  W2.9 — staleness / generation rejection
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `a result superseded by bumpGeneration mid-flight is dropped, never reaching onResult`() = runTest {
        val results = mutableListOf<RecognitionResult>()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val recognizer = defaultRecognizer(this, dispatcher) { results += it }
        coEvery { cardOcrAnalyzer.extractCardName(any(), any()) } returns OcrCandidate(text = "Lightning Bolt", score = 100f, lineHeightRatio = 1f)
        coEvery { cardRepository.getCardByExactName("Lightning Bolt") } coAnswers {
            // Bump generation WHILE the network call is notionally in flight (simulates a pause
            // arriving between the frame being processed and the network round-trip completing).
            recognizer.bumpGeneration()
            Result.success(defaultCard)
        }

        recognizer.stabilize()
        advanceUntilIdle()
        recognizer.analyze(buildImageProxyMock())
        advanceUntilIdle()

        assertTrue("a superseded result must never reach onResult", results.none { it is RecognitionResult.Identified })
        // WS4: the generation-drop counter (kept separate from the age-drop counter — see the
        // production KDoc for why) must flush exactly once.
        verify(exactly = 1) { crashlytics.setCustomKey("scanner_stale_drop_generation_count", 1) }
    }

    @Test
    fun `a result older than RESULT_MAX_AGE_MS is dropped, never reaching onResult`() = runTest {
        val results = mutableListOf<RecognitionResult>()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val recognizer = defaultRecognizer(this, dispatcher) { results += it }
        coEvery { cardOcrAnalyzer.extractCardName(any(), any()) } returns OcrCandidate(text = "Lightning Bolt", score = 100f, lineHeightRatio = 1f)
        coEvery { cardRepository.getCardByExactName("Lightning Bolt") } coAnswers {
            // Rewind processingStartedAtMs so "now - processingStartedAtMs" already exceeds
            // RESULT_MAX_AGE_MS by the time the (mocked, instant) call returns.
            recognizer.setLongField(
                "processingStartedAtMs",
                System.currentTimeMillis() - (CardRecognizer.RESULT_MAX_AGE_MS + 1_000),
            )
            Result.success(defaultCard)
        }

        recognizer.stabilize()
        advanceUntilIdle()
        recognizer.analyze(buildImageProxyMock())
        advanceUntilIdle()

        assertTrue("a stale result must never reach onResult", results.none { it is RecognitionResult.Identified })
        // WS4: the age-drop counter (kept separate from the generation-drop counter) must flush.
        verify(exactly = 1) { crashlytics.setCustomKey("scanner_stale_drop_age_count", 1) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  W3.5 — resetForCameraStop (full camera stop, scanner-reliability-plan.md, 2026-08-24)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `resetForCameraStop bumps generation, so a result already in flight when the camera stops is dropped`() = runTest {
        val results = mutableListOf<RecognitionResult>()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val recognizer = defaultRecognizer(this, dispatcher) { results += it }
        coEvery { cardOcrAnalyzer.extractCardName(any(), any()) } returns OcrCandidate(text = "Lightning Bolt", score = 100f, lineHeightRatio = 1f)
        coEvery { cardRepository.getCardByExactName("Lightning Bolt") } coAnswers {
            // Simulate ScannerScreen's CameraPreview calling resetForCameraStop() the instant a
            // covering sheet/overlay opens, WHILE this network call is notionally in flight.
            recognizer.resetForCameraStop()
            Result.success(defaultCard)
        }

        recognizer.stabilize()
        advanceUntilIdle()
        recognizer.analyze(buildImageProxyMock())
        advanceUntilIdle()

        assertTrue(
            "a result superseded by resetForCameraStop's generation bump must never reach onResult",
            results.none { it is RecognitionResult.Identified },
        )
    }

    @Test
    fun `resetForCameraStop clears the pre-resolution stability buffer`() = runTest {
        val results = mutableListOf<RecognitionResult>()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val recognizer = defaultRecognizer(this, dispatcher) { results += it }
        coEvery { cardOcrAnalyzer.extractCardName(any(), any()) } returns OcrCandidate(text = "Lightning Bolt", score = 100f, lineHeightRatio = 1f)
        coEvery { cardRepository.getCardByExactName("Lightning Bolt") } returns Result.success(defaultCard)

        // First frame: stableFrameCount goes 0 -> 1 (STABILITY_FRAMES_PRE_RESOLUTION = 2), not yet
        // enough to reach the network.
        recognizer.analyze(buildImageProxyMock())
        advanceUntilIdle()
        coVerify(exactly = 0) { cardRepository.getCardByExactName(any()) }

        // Camera stop arrives here — must wipe the buffer back to 0, not leave it at 1.
        recognizer.resetForCameraStop()

        // A SECOND consecutive frame with the same text: if the buffer had NOT been reset, this
        // would be the 2nd consecutive hit (0->1 already happened above) and would trigger the
        // network call. Since it was reset, this is only the 1st hit again post-reset.
        recognizer.bypassThrottle()
        recognizer.analyze(buildImageProxyMock())
        advanceUntilIdle()

        coVerify(exactly = 0) { cardRepository.getCardByExactName(any()) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  W2.10 — RateLimited propagation
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `a RateLimitExhaustedException from the exact-name call surfaces as RecognitionResult RateLimited`() = runTest {
        val results = mutableListOf<RecognitionResult>()
        val recognizer = defaultRecognizer(this, StandardTestDispatcher(testScheduler)) { results += it }
        coEvery { cardOcrAnalyzer.extractCardName(any(), any()) } returns OcrCandidate(text = "Lightning Bolt", score = 100f, lineHeightRatio = 1f)
        coEvery { cardRepository.getCardByExactName("Lightning Bolt") } returns
            Result.failure(RateLimitExhaustedException(retryAfterMs = 5_000L))

        recognizer.stabilize()
        advanceUntilIdle()
        recognizer.analyze(buildImageProxyMock())
        advanceUntilIdle()

        coVerify(exactly = 0) { cardRepository.searchCardByName(any()) } // never falls through to fuzzy on rate-limit
        assertEquals(RecognitionResult.RateLimited(5_000L), results.last())
    }

    @Test
    fun `while an active rate-limit cooldown is running, further lookups are suspended with zero network calls`() = runTest {
        val results = mutableListOf<RecognitionResult>()
        val recognizer = defaultRecognizer(this, StandardTestDispatcher(testScheduler)) { results += it }
        coEvery { cardOcrAnalyzer.extractCardName(any(), any()) } returns OcrCandidate(text = "Lightning Bolt", score = 100f, lineHeightRatio = 1f)
        coEvery { cardRepository.getCardByExactName("Lightning Bolt") } returns
            Result.failure(RateLimitExhaustedException(retryAfterMs = 60_000L))

        recognizer.stabilize()
        advanceUntilIdle()
        recognizer.analyze(buildImageProxyMock())
        advanceUntilIdle()
        coVerify(exactly = 1) { cardRepository.getCardByExactName("Lightning Bolt") }

        // Same text again, well within the 60s cooldown — must be suspended with zero network.
        recognizer.bypassThrottle()
        recognizer.analyze(buildImageProxyMock())
        advanceUntilIdle()

        coVerify(exactly = 1) { cardRepository.getCardByExactName("Lightning Bolt") } // still exactly 1
        assertEquals(RecognitionResult.NoCard, results.last())
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  W2.11 — language change resets negative cache / generation / stability
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `changing selectedLanguage resets the negative cache so a previously-failed name is retried`() = runTest {
        val results = mutableListOf<RecognitionResult>()
        val recognizer = defaultRecognizer(this, StandardTestDispatcher(testScheduler)) { results += it }
        coEvery { cardOcrAnalyzer.extractCardName(any(), any()) } returns OcrCandidate(text = "Garbage Text", score = 100f, lineHeightRatio = 1f)
        coEvery { cardRepository.getCardByExactName("Garbage Text") } returns Result.failure(RuntimeException("404"))
        coEvery { cardRepository.searchCardByName("Garbage Text") } returns DataResult.Error("SCRYFALL_404")

        recognizer.stabilize()
        advanceUntilIdle()
        recognizer.analyze(buildImageProxyMock())
        advanceUntilIdle()
        coVerify(exactly = 1) { cardRepository.getCardByExactName("Garbage Text") }

        // Language change (still "en" -> "en" would be a no-op; go to "es" then back to "en" to
        // exercise a REAL transition) resets the negative cache — the identical failing name must
        // be eligible for a fresh attempt (2-frame stability applies again after the reset).
        recognizer.selectedLanguage = "es"
        recognizer.selectedLanguage = "en"
        coEvery { cardOcrAnalyzer.extractCardName(any(), any()) } returns OcrCandidate(text = "Garbage Text", score = 100f, lineHeightRatio = 1f)
        recognizer.stabilize()
        advanceUntilIdle()
        recognizer.analyze(buildImageProxyMock())
        advanceUntilIdle()

        coVerify(exactly = 2) { cardRepository.getCardByExactName("Garbage Text") }
        // WS4: a REAL transition must tag the session context for both hops.
        verify(exactly = 1) { crashlytics.setCustomKey("scanner_selected_lang", "es") }
        verify(exactly = 1) { crashlytics.setCustomKey("scanner_selected_lang", "en") }
    }

    @Test
    fun `changing selectedLanguage does not reset when the value is unchanged`() = runTest {
        val results = mutableListOf<RecognitionResult>()
        val recognizer = defaultRecognizer(this, StandardTestDispatcher(testScheduler)) { results += it }
        coEvery { cardOcrAnalyzer.extractCardName(any(), any()) } returns OcrCandidate(text = "Garbage Text", score = 100f, lineHeightRatio = 1f)
        coEvery { cardRepository.getCardByExactName("Garbage Text") } returns Result.failure(RuntimeException("404"))
        coEvery { cardRepository.searchCardByName("Garbage Text") } returns DataResult.Error("SCRYFALL_404")

        recognizer.stabilize()
        advanceUntilIdle()
        recognizer.analyze(buildImageProxyMock())
        advanceUntilIdle()
        coVerify(exactly = 1) { cardRepository.getCardByExactName("Garbage Text") }

        recognizer.selectedLanguage = "en" // same value — must be a no-op, negative cache stays intact
        recognizer.bypassThrottle()
        recognizer.analyze(buildImageProxyMock())
        advanceUntilIdle()

        coVerify(exactly = 1) { cardRepository.getCardByExactName("Garbage Text") } // still exactly 1
        // WS4: an unchanged-value assignment must never reach the custom-setter's telemetry line.
        verify(exactly = 0) { crashlytics.setCustomKey("scanner_selected_lang", any<String>()) }
    }
}
