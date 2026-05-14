package com.mmg.manahub.feature.scanner.data

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.mmg.manahub.core.domain.model.DataResult
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.feature.scanner.domain.model.RecognitionResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
 *    [CardRepository.searchCardByName] on [Dispatchers.IO].
 *    An in-memory cache ([OCR_CACHE_TTL_MS]) skips the network round-trip when the same
 *    card name is held in frame across consecutive pipeline ticks.
 * 5. Emit [RecognitionResult] via [onResult].
 *
 * @param cardRepository   Domain repository used to resolve card names via Scryfall.
 * @param cardOcrAnalyzer  ML Kit OCR wrapper that extracts the card name from the name zone.
 * @param scope            [CoroutineScope] for suspending Scryfall calls.
 * @param selectedLanguage Current user-selected language code. Mutable in-place via
 *                         [@Volatile var] — no recognizer recreation needed on language change.
 * @param onResult         Callback invoked on each processed frame with the recognition outcome.
 */
class CardRecognizer(
    private val cardRepository: CardRepository,
    private val cardOcrAnalyzer: CardOcrAnalyzer,
    private val scope: CoroutineScope,
    @Volatile var selectedLanguage: String = "en",
    private val onResult: (RecognitionResult) -> Unit,
) : ImageAnalysis.Analyzer {

    private val minIntervalMs = 800L
    @Volatile private var lastProcessedMs = 0L
    private val isProcessing = AtomicBoolean(false)

    private var lastOcrName: String?  = null
    private var lastOcrCard: com.mmg.manahub.core.domain.model.Card? = null
    private var lastOcrTimeMs: Long   = 0L
    private val OCR_CACHE_TTL_MS      = 3_000L

    override fun analyze(imageProxy: ImageProxy) {
        val now = System.currentTimeMillis()

        if (now - lastProcessedMs < minIntervalMs) {
            imageProxy.close()
            return
        }
        if (!isProcessing.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }
        lastProcessedMs = now

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            isProcessing.set(false)
            onResult(RecognitionResult.NoCard)
            return
        }

        val rotationDegrees = imageProxy.imageInfo.rotationDegrees

        scope.launch(Dispatchers.Default) {
            try {
                // imageProxy must remain open until ML Kit finishes consuming mediaImage.
                val cardName = cardOcrAnalyzer.extractCardName(
                    mediaImage     = mediaImage,
                    rotationDegrees = rotationDegrees,
                    language       = selectedLanguage,
                )
                imageProxy.close()
                isProcessing.set(false)

                if (cardName == null) {
                    android.util.Log.d("CardRecognizer", "OCR: nothing in name zone")
                    onResult(RecognitionResult.NoCard)
                    return@launch
                }

                android.util.Log.d("CardRecognizer", "OCR [$selectedLanguage]: '$cardName'")

                val now2 = System.currentTimeMillis()
                val card = if (cardName.equals(lastOcrName, ignoreCase = true) &&
                    lastOcrCard != null &&
                    (now2 - lastOcrTimeMs) < OCR_CACHE_TTL_MS
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
                lastOcrTimeMs = now2

                onResult(
                    RecognitionResult.Identified(
                        card       = card,
                        similarity = 1.0f,
                        ambiguous  = false,
                        corners    = emptyList(),
                    )
                )
            } catch (e: Exception) {
                android.util.Log.w("CardRecognizer", "Pipeline exception", e)
                imageProxy.close()
                isProcessing.set(false)
                onResult(RecognitionResult.NoCard)
            }
        }
    }

    private suspend fun resolveCard(cardName: String): com.mmg.manahub.core.domain.model.Card? {
        val exactResult = withContext(Dispatchers.IO) {
            cardRepository.getCardByExactName(cardName)
        }
        if (exactResult.isSuccess) return exactResult.getOrNull()

        if (selectedLanguage != "en") {
            val langQuery = "!\"${cardName}\" lang:${selectedLanguage}"
            val langResult = withContext(Dispatchers.IO) {
                cardRepository.searchCardByName(langQuery)
            }
            if (langResult is DataResult.Success) return langResult.data
        }

        val fuzzyResult = withContext(Dispatchers.IO) {
            cardRepository.searchCardByName(cardName)
        }
        return when (fuzzyResult) {
            is DataResult.Success -> fuzzyResult.data
            is DataResult.Error   -> {
                android.util.Log.d("CardRecognizer", "Scryfall lookup failed for '$cardName'")
                null
            }
        }
    }

    fun release() {
        cardOcrAnalyzer.close()
    }
}
