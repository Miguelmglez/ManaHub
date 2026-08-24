package com.mmg.manahub.feature.scanner.data

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.util.recordSafeNonFatal
import com.mmg.manahub.feature.scanner.domain.model.RecognitionResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicBoolean

/**
 * [ImageAnalysis.Analyzer] that identifies MTG cards by running ML Kit OCR on a
 * restricted name-zone strip of each camera frame.
 *
 * Pipeline per frame:
 * 1. Throttle (800 ms between processed frames) and re-entrance guard.
 * 2. Pass [ImageProxy.image] (YUV_420_888 [android.media.Image]) to [CardOcrAnalyzer].
 *    ML Kit receives the raw media image with [ImageProxy.imageInfo.rotationDegrees] so it
 *    handles rotation internally — no manual YUV→BGR conversion is needed.
 * 3. [CardOcrAnalyzer.extractCardName] filters OCR results to the on-screen name zone and
 *    applies smart keyword filters to suppress false positives from the card body.
 * 4. Name→card resolution via [CardRepository.getCardByExactName] /
 *    [CardRepository.searchCardByName] on [ioDispatcher] ([Dispatchers.IO] by default).
 *    An in-memory cache ([OCR_CACHE_TTL_MS]) skips the network round-trip when the same
 *    card name is held in frame across consecutive pipeline ticks.
 * 5. Emit [RecognitionResult] via [onResult].
 *
 * ### Guard + resource-ownership contract (WS1, 2026-08-24)
 * [isProcessing] and the per-frame [ImageProxy] are owned **entirely by the launched
 * coroutine**, never by the analyzer thread that calls [analyze]:
 * - [analyze] (the analyzer thread) only ever *reads* [isProcessing]; it never sets it to
 *   `true`. This is deliberate — if it set the guard before launching, a scope that never
 *   runs the coroutine body (already cancelled) or that dies mid-`launch` would latch the
 *   guard forever with no code path left to clear it.
 * - The coroutine sets [isProcessing] to `true` as its first statement, and a `finally` block
 *   both closes the [ImageProxy] and resets the guard on every path: success, null OCR
 *   result, timeout, any other exception, or cancellation.
 * - The OCR call is wrapped in [withTimeout] ([OCR_TIMEOUT_MS]); a stuck ML Kit `Task` can
 *   therefore never latch the guard past the timeout window.
 * - A **stall watchdog** is kept as a backstop: if a later frame observes [isProcessing]
 *   still `true` after [STALL_THRESHOLD_MS], it force-resets the guard and continues
 *   processing the new frame instead of dropping it, logging a `scanner_ocr_stall_recovered`
 *   breadcrumb.
 * - [CancellationException] is rethrown after cleanup, never swallowed as a generic pipeline
 *   failure — a cancelled coroutine is not an OCR/resolution error.
 *
 * @param cardRepository   Domain repository used to resolve card names via Scryfall.
 * @param cardOcrAnalyzer  ML Kit OCR wrapper that extracts the card name from the name zone.
 * @param scope            [CoroutineScope] for suspending Scryfall calls.
 * @param selectedLanguage Current user-selected language code. Mutable in-place via
 *                         [@Volatile var] — no recognizer recreation needed on language change.
 * @param ioDispatcher     Dispatcher used for [CardRepository] calls in [resolveCard]. Defaults
 *                         to [Dispatchers.IO] in production; injectable so tests can substitute
 *                         a virtual-time `TestDispatcher` sharing the same scheduler as [scope]
 *                         and get deterministic `advanceUntilIdle()` behaviour instead of racing
 *                         a real dispatcher hop.
 * @param onResult         Callback invoked on each processed frame with the recognition outcome.
 */
class CardRecognizer(
    private val cardRepository: CardRepository,
    private val cardOcrAnalyzer: CardOcrAnalyzer,
    private val scope: CoroutineScope,
    @Volatile var selectedLanguage: String = "en",
    private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO,
    private val onResult: (RecognitionResult) -> Unit,
) : ImageAnalysis.Analyzer {

    companion object {
        /** Max time budget for a single OCR call before it is treated as a timed-out frame. */
        const val OCR_TIMEOUT_MS = 2_500L

        /** If [isProcessing] has been held this long, assume it latched and force-reset it. */
        const val STALL_THRESHOLD_MS = 5_000L
    }

    private val minIntervalMs = 800L
    @Volatile private var lastProcessedMs = 0L
    private val isProcessing = AtomicBoolean(false)

    /** Wall-clock time [isProcessing] was last set to `true`; backs the stall watchdog. */
    @Volatile private var processingStartedAtMs = 0L

    private var lastOcrName: String?  = null
    private var lastOcrCard: com.mmg.manahub.core.model.Card? = null
    private var lastOcrTimeMs: Long   = 0L
    private val OCR_CACHE_TTL_MS      = 3_000L

    override fun analyze(imageProxy: ImageProxy) {
        val now = System.currentTimeMillis()

        if (now - lastProcessedMs < minIntervalMs) {
            imageProxy.close()
            return
        }

        if (isProcessing.get()) {
            val heldForMs = now - processingStartedAtMs
            if (heldForMs < STALL_THRESHOLD_MS) {
                // A previous frame's coroutine is still legitimately in flight — drop this
                // one, same as today.
                imageProxy.close()
                return
            }
            // Stall watchdog: the guard has been held far longer than any real OCR call
            // should take. Force-reset and process this frame instead of dropping it too.
            FirebaseCrashlytics.getInstance().log("scanner_ocr_stall_recovered")
            isProcessing.set(false)
        }

        lastProcessedMs = now

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            onResult(RecognitionResult.NoCard)
            return
        }

        val rotationDegrees = imageProxy.imageInfo.rotationDegrees

        // Launches on [scope]'s own dispatcher (Dispatchers.Default in production — see
        // ScannerScreen's recognizerScope) rather than forcing Dispatchers.Default here.
        // This keeps prod behaviour identical while letting tests inject a virtual-time
        // TestScope/StandardTestDispatcher to deterministically exercise withTimeout.
        scope.launch {
            // First statement inside the coroutine: the guard is only ever claimed once the
            // coroutine body is actually running (see class KDoc for why).
            isProcessing.set(true)
            processingStartedAtMs = System.currentTimeMillis()
            try {
                // imageProxy must remain open until ML Kit finishes consuming mediaImage —
                // it is closed in the finally block below, on every path.
                val cardName = extractCardNameWithTimeout(mediaImage, rotationDegrees)

                if (cardName == null) {
                    if (com.mmg.manahub.BuildConfig.DEBUG) {
                        android.util.Log.d("CardRecognizer", "OCR: nothing in name zone")
                    }
                    onResult(RecognitionResult.NoCard)
                    return@launch
                }

                if (com.mmg.manahub.BuildConfig.DEBUG) {
                    android.util.Log.d("CardRecognizer", "OCR [$selectedLanguage]: '$cardName'")
                }

                val resolvedAtMs = System.currentTimeMillis()
                val card = if (cardName.equals(lastOcrName, ignoreCase = true) &&
                    lastOcrCard != null &&
                    (resolvedAtMs - lastOcrTimeMs) < OCR_CACHE_TTL_MS
                ) {
                    lastOcrCard!!
                } else {
                    resolveCard(cardName)
                }

                if (card == null) {
                    onResult(RecognitionResult.NoCard)
                    return@launch
                }

                lastOcrName   = cardName
                lastOcrCard   = card
                lastOcrTimeMs = resolvedAtMs

                onResult(
                    RecognitionResult.Identified(
                        card       = card,
                        similarity = 1.0f,
                        ambiguous  = false,
                        corners    = emptyList(),
                    )
                )
            } catch (e: CancellationException) {
                // Not a pipeline failure — the scope was torn down or the frame superseded.
                // Cleanup still runs via `finally`; rethrow afterwards.
                throw e
            } catch (e: Exception) {
                if (com.mmg.manahub.BuildConfig.DEBUG) {
                    android.util.Log.w("CardRecognizer", "Pipeline exception", e)
                } else {
                    android.util.Log.w("CardRecognizer", "Pipeline exception: ${e.javaClass.simpleName}")
                }
                onResult(RecognitionResult.NoCard)
            } finally {
                imageProxy.close()
                isProcessing.set(false)
            }
        }
    }

    /**
     * Runs [CardOcrAnalyzer.extractCardName] under a [OCR_TIMEOUT_MS] budget. On timeout,
     * records a `scanner_ocr_timeout` non-fatal (no raw OCR text — never PII) and treats the
     * frame as having produced no candidate, same as a genuine "nothing detected" result.
     */
    private suspend fun extractCardNameWithTimeout(
        mediaImage: android.media.Image,
        rotationDegrees: Int,
    ): String? {
        return try {
            withTimeout(OCR_TIMEOUT_MS) {
                cardOcrAnalyzer.extractCardName(mediaImage, rotationDegrees)
            }
        } catch (e: TimeoutCancellationException) {
            recordSafeNonFatal("scanner_ocr_timeout", e)
            null
        }
    }

    private suspend fun resolveCard(cardName: String): com.mmg.manahub.core.model.Card? {
        val exactResult = withContext(ioDispatcher) {
            cardRepository.getCardByExactName(cardName)
        }
        if (exactResult.isSuccess) return exactResult.getOrNull()

        if (selectedLanguage != "en") {
            val langQuery = "!\"${cardName}\" lang:${selectedLanguage}"
            val langResult = withContext(ioDispatcher) {
                cardRepository.searchCardByName(langQuery)
            }
            if (langResult is DataResult.Success) return langResult.data
        }

        val fuzzyResult = withContext(ioDispatcher) {
            cardRepository.searchCardByName(cardName)
        }
        return when (fuzzyResult) {
            is DataResult.Success -> fuzzyResult.data
            is DataResult.Error   -> {
                if (com.mmg.manahub.BuildConfig.DEBUG) {
                    android.util.Log.d("CardRecognizer", "Scryfall lookup failed for '$cardName'")
                }
                null
            }
        }
    }
}
