package com.mmg.manahub.feature.scanner.data

import android.media.Image
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [CardOcrAnalyzer]'s WS1 self-healing client lifecycle (2026-08-24):
 * - The ML Kit client is (re)created on demand rather than held in a `by lazy` that never
 *   recovers once closed.
 * - A "client already closed / unavailable" failure signature ([IllegalStateException],
 *   or [MlKitException] with [MlKitException.NOT_FOUND] / [MlKitException.UNAVAILABLE])
 *   triggers exactly one recreate-and-retry of the same frame.
 * - Any other exception is treated as a soft per-frame failure — no retry, no extra client
 *   creation.
 *
 * Name-extraction accuracy (zone maths, keyword scoring, FR/IT/PT support) is WS2.A scope
 * per `docs/plans/scanner-reliability-plan.md`'s test plan and is not covered here.
 */
class CardOcrAnalyzerTest {

    private val mediaImage: Image = mockk(relaxed = true)
    private val inputImage: InputImage = mockk(relaxed = true)

    /** A [Text] result with no blocks — cheap to build, valid input for the retry paths
     *  below since we only assert on client lifecycle/retry behaviour, not on extraction
     *  (WS2.A scope). [CardOcrAnalyzer.extractFromResult] returns null immediately for it. */
    private val emptyText: Text = mockk(relaxed = true)

    @Before
    fun setUp() {
        mockkStatic(TextRecognition::class)
        mockkStatic(InputImage::class)
        every { InputImage.fromMediaImage(any(), any()) } returns inputImage
        every { emptyText.textBlocks } returns emptyList()
    }

    @After
    fun tearDown() {
        unmockkStatic(TextRecognition::class)
        unmockkStatic(InputImage::class)
    }

    @Test
    fun `client is built once and reused, recreateIfNeeded is a no-op while it is alive`() = runTest {
        val client = mockk<TextRecognizer>(relaxed = true)
        every { TextRecognition.getClient(any()) } returns client
        every { client.process(inputImage) } returns Tasks.forResult(emptyText)

        val analyzer = CardOcrAnalyzer()
        analyzer.extractCardName(mediaImage, 0)
        analyzer.recreateIfNeeded()
        analyzer.extractCardName(mediaImage, 0)

        verify(exactly = 1) { TextRecognition.getClient(any()) }
    }

    @Test
    fun `IllegalStateException from a closed client recreates the client once and retries successfully`() = runTest {
        val staleClient = mockk<TextRecognizer>(relaxed = true)
        val freshClient = mockk<TextRecognizer>(relaxed = true)
        every { TextRecognition.getClient(any()) } returnsMany listOf(staleClient, freshClient)
        every { staleClient.process(inputImage) } throws IllegalStateException("This client has been closed")
        every { freshClient.process(inputImage) } returns Tasks.forResult(emptyText)

        val analyzer = CardOcrAnalyzer()
        val result = analyzer.extractCardName(mediaImage, 0)

        assertNull(result)
        verify(exactly = 2) { TextRecognition.getClient(any()) }
        verify(exactly = 1) { staleClient.process(inputImage) }
        verify(exactly = 1) { freshClient.process(inputImage) }
    }

    @Test
    fun `MlKitException UNAVAILABLE recreates the client once and retries successfully`() = runTest {
        val staleClient = mockk<TextRecognizer>(relaxed = true)
        val freshClient = mockk<TextRecognizer>(relaxed = true)
        every { TextRecognition.getClient(any()) } returnsMany listOf(staleClient, freshClient)
        every { staleClient.process(inputImage) } throws MlKitException("unavailable", MlKitException.UNAVAILABLE)
        every { freshClient.process(inputImage) } returns Tasks.forResult(emptyText)

        val analyzer = CardOcrAnalyzer()
        val result = analyzer.extractCardName(mediaImage, 0)

        assertNull(result)
        verify(exactly = 2) { TextRecognition.getClient(any()) }
    }

    @Test
    fun `MlKitException NOT_FOUND recreates the client once and retries successfully`() = runTest {
        val staleClient = mockk<TextRecognizer>(relaxed = true)
        val freshClient = mockk<TextRecognizer>(relaxed = true)
        every { TextRecognition.getClient(any()) } returnsMany listOf(staleClient, freshClient)
        every { staleClient.process(inputImage) } throws MlKitException("not found", MlKitException.NOT_FOUND)
        every { freshClient.process(inputImage) } returns Tasks.forResult(emptyText)

        val analyzer = CardOcrAnalyzer()
        val result = analyzer.extractCardName(mediaImage, 0)

        assertNull(result)
        verify(exactly = 2) { TextRecognition.getClient(any()) }
    }

    @Test
    fun `unrelated exception is a soft failure with no retry and no extra client creation`() = runTest {
        val client = mockk<TextRecognizer>(relaxed = true)
        every { TextRecognition.getClient(any()) } returns client
        every { client.process(inputImage) } throws RuntimeException("transient OCR glitch")

        val analyzer = CardOcrAnalyzer()
        val result = analyzer.extractCardName(mediaImage, 0)

        assertNull(result)
        verify(exactly = 1) { TextRecognition.getClient(any()) }
        verify(exactly = 1) { client.process(inputImage) }
    }

    @Test
    fun `close nulls the client so the next extractCardName rebuilds a fresh one`() = runTest {
        val firstClient = mockk<TextRecognizer>(relaxed = true)
        val secondClient = mockk<TextRecognizer>(relaxed = true)
        every { TextRecognition.getClient(any()) } returnsMany listOf(firstClient, secondClient)
        every { firstClient.process(inputImage) } returns Tasks.forResult(emptyText)
        every { secondClient.process(inputImage) } returns Tasks.forResult(emptyText)

        val analyzer = CardOcrAnalyzer()
        analyzer.extractCardName(mediaImage, 0)
        analyzer.close()
        analyzer.extractCardName(mediaImage, 0)

        verify(exactly = 2) { TextRecognition.getClient(any()) }
        verify(exactly = 1) { firstClient.close() }
    }
}
