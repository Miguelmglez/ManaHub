package com.mmg.manahub.feature.scanner.data

import android.media.Image
import androidx.camera.core.ImageInfo
import androidx.camera.core.ImageProxy
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.feature.scanner.domain.model.RecognitionResult
import com.mmg.manahub.util.TestFixtures
import io.mockk.CapturingSlot
import io.mockk.coEvery
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
 * Unit tests for [CardRecognizer]'s WS1 guard/resource-ownership contract:
 * - The in-flight guard ([isProcessing]) and the per-frame [ImageProxy] are owned by the
 *   launched coroutine and released on EVERY path via `finally`.
 * - A stuck OCR call is bounded by [CardRecognizer.OCR_TIMEOUT_MS].
 * - A latched guard is recovered by the stall watchdog after
 *   [CardRecognizer.STALL_THRESHOLD_MS].
 * - Cancellation is rethrown, never reported as a soft [RecognitionResult.NoCard].
 *
 * Name-extraction accuracy (zone maths, keyword scoring) is WS2.A scope, not covered here.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CardRecognizerTest {

    private val cardOcrAnalyzer: CardOcrAnalyzer = mockk()
    private val cardRepository: CardRepository = mockk()
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

    // ── Success / null-result paths ───────────────────────────────────────────

    @Test
    fun `successful frame closes imageProxy exactly once, releases guard, emits Identified`() = runTest {
        val results = mutableListOf<RecognitionResult>()
        val recognizer = CardRecognizer(
            cardRepository = cardRepository,
            cardOcrAnalyzer = cardOcrAnalyzer,
            scope = this,
            ioDispatcher = StandardTestDispatcher(testScheduler),
            onResult = { results += it },
        )
        val imageProxy = buildImageProxyMock()
        coEvery { cardOcrAnalyzer.extractCardName(any(), any()) } returns "Lightning Bolt"
        coEvery { cardRepository.getCardByExactName("Lightning Bolt") } returns Result.success(defaultCard)

        recognizer.analyze(imageProxy)
        advanceUntilIdle()

        verify(exactly = 1) { imageProxy.close() }
        assertEquals(1, results.size)
        assertEquals(
            RecognitionResult.Identified(
                card = defaultCard,
                similarity = 1.0f,
                ambiguous = false,
                corners = emptyList(),
            ),
            results.single(),
        )
        assertFalse(recognizer.isProcessingFlag())
    }

    @Test
    fun `null OCR result closes imageProxy exactly once, releases guard, emits NoCard`() = runTest {
        val results = mutableListOf<RecognitionResult>()
        val recognizer = CardRecognizer(
            cardRepository = cardRepository,
            cardOcrAnalyzer = cardOcrAnalyzer,
            scope = this,
            ioDispatcher = StandardTestDispatcher(testScheduler),
            onResult = { results += it },
        )
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
        val recognizer = CardRecognizer(
            cardRepository = cardRepository,
            cardOcrAnalyzer = cardOcrAnalyzer,
            scope = this,
            ioDispatcher = StandardTestDispatcher(testScheduler),
            onResult = { results += it },
        )
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
        val recognizer = CardRecognizer(
            cardRepository = cardRepository,
            cardOcrAnalyzer = cardOcrAnalyzer,
            scope = this,
            ioDispatcher = StandardTestDispatcher(testScheduler),
            onResult = { results += it },
        )
        val imageProxy = buildImageProxyMock()
        coEvery { cardOcrAnalyzer.extractCardName(any(), any()) } returns "Lightning Bolt"
        coEvery { cardRepository.getCardByExactName("Lightning Bolt") } throws RuntimeException("boom")

        recognizer.analyze(imageProxy)
        advanceUntilIdle()

        verify(exactly = 1) { imageProxy.close() }
        assertEquals(listOf(RecognitionResult.NoCard), results)
        assertFalse(recognizer.isProcessingFlag())
    }

    // ── Cancellation ───────────────────────────────────────────────────────────

    @Test
    fun `cancellation closes imageProxy exactly once, releases guard, and is not reported as NoCard`() = runTest {
        val results = mutableListOf<RecognitionResult>()
        val childJob = Job()
        val childScope = CoroutineScope(coroutineContext + childJob)
        val recognizer = CardRecognizer(
            cardRepository = cardRepository,
            cardOcrAnalyzer = cardOcrAnalyzer,
            scope = childScope,
            ioDispatcher = StandardTestDispatcher(testScheduler),
            onResult = { results += it },
        )
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
        val recognizer = CardRecognizer(
            cardRepository = cardRepository,
            cardOcrAnalyzer = cardOcrAnalyzer,
            scope = this,
            ioDispatcher = StandardTestDispatcher(testScheduler),
            onResult = { results += it },
        )

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
        coEvery { cardOcrAnalyzer.extractCardName(any(), any()) } returns "Serra Angel"
        coEvery { cardRepository.getCardByExactName("Serra Angel") } returns Result.success(secondCard)

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
            ),
            results.single(),
        )
        assertFalse(recognizer.isProcessingFlag())
    }

    @Test
    fun `guard still held within the stall threshold drops the frame without recovering`() = runTest {
        val results = mutableListOf<RecognitionResult>()
        val recognizer = CardRecognizer(
            cardRepository = cardRepository,
            cardOcrAnalyzer = cardOcrAnalyzer,
            scope = this,
            ioDispatcher = StandardTestDispatcher(testScheduler),
            onResult = { results += it },
        )
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
}
