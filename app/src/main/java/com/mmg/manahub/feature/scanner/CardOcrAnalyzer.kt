package com.mmg.manahub.feature.scanner

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

/**
 * Extracts the card name from a warped card bitmap using ML Kit Text Recognition.
 *
 * MTG card name occupies the top title bar, approximately the top 14% of card height.
 * The recognizer crops to this region before running OCR to reduce noise from the
 * card's type line, rules text, and flavor text.
 *
 * Expected input: a flat perspective-corrected bitmap of the full card face,
 * [OCR_WARP_WIDTH] × [OCR_WARP_HEIGHT] pixels, portrait orientation.
 */
class CardOcrAnalyzer {

    companion object {
        /** Width of the perspective-warped card image fed to OCR. */
        const val OCR_WARP_WIDTH = 420
        /** Height of the perspective-warped card image fed to OCR. */
        const val OCR_WARP_HEIGHT = 588
        /** Fraction of card height that contains the title bar. */
        private const val TITLE_HEIGHT_FRACTION = 0.14f
    }

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /**
     * Runs OCR on the title bar region of [cardBitmap] and returns the most likely
     * card name, or null if no text was found or the result looks invalid.
     *
     * Must be called from a coroutine — suspends until ML Kit completes.
     *
     * @param cardBitmap Full-card bitmap, [OCR_WARP_WIDTH]×[OCR_WARP_HEIGHT].
     */
    suspend fun extractCardName(cardBitmap: Bitmap): String? {
        // Crop to just the title bar area
        val titleHeight = (cardBitmap.height * TITLE_HEIGHT_FRACTION).toInt().coerceAtLeast(1)
        val titleBitmap = Bitmap.createBitmap(cardBitmap, 0, 0, cardBitmap.width, titleHeight)

        return try {
            val image = InputImage.fromBitmap(titleBitmap, 0)
            val result = recognizer.process(image).await()

            // Take the first text block — that's the card name on MTG cards
            val rawText = result.textBlocks.firstOrNull()?.text
                ?.replace("\n", " ")
                ?.trim()
                ?: return null

            // Basic sanity: discard if too short, too long, or obviously a mana cost
            if (rawText.length < 2 || rawText.length > 40) return null
            if (rawText.all { it.isDigit() || it == '{' || it == '}' }) return null

            rawText
        } catch (e: Exception) {
            android.util.Log.w("CardOcrAnalyzer", "OCR failed", e)
            null
        } finally {
            if (!titleBitmap.isRecycled) titleBitmap.recycle()
        }
    }

    /** Releases the underlying ML Kit recognizer client. */
    fun close() {
        recognizer.close()
    }
}
