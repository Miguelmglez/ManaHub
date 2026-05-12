package com.mmg.manahub.feature.scanner

import android.media.Image
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.mmg.manahub.feature.scanner.CardOcrAnalyzer.Companion.NAME_ZONE_BOTTOM_FRACTION
import com.mmg.manahub.feature.scanner.CardOcrAnalyzer.Companion.NAME_ZONE_TOP_FRACTION
import kotlinx.coroutines.tasks.await

/**
 * Extracts the MTG card name from a camera frame using ML Kit Text Recognition.
 *
 * Receives the raw [android.media.Image] (YUV_420_888) directly from CameraX —
 * ML Kit handles rotation internally via [rotationDegrees], so no manual YUV→Bitmap
 * conversion or coordinate transform is needed.
 *
 * Only text blocks whose top edge falls within [NAME_ZONE_TOP_FRACTION]…[NAME_ZONE_BOTTOM_FRACTION]
 * of the detected image height are considered. This matches the visual name-zone strip
 * rendered by [ScannerScreen], ensuring only the card name area is read.
 *
 * Smart filters (card-type-line keywords, rules-text phrases, OCR artefact cleanup)
 * further reduce false positives from artwork text or bottom card body.
 *
 * Recognizer clients are cached by script group and released on [close].
 *
 * Supported scripts:
 * - Latin (EN/ES/DE/FR/IT/PT): [TextRecognizerOptions.DEFAULT_OPTIONS]
 * - Japanese (JA): `JapaneseTextRecognizerOptions`
 * - Korean  (KO): `KoreanTextRecognizerOptions`
 */
class CardOcrAnalyzer {

    companion object {
        /**
         * Vertical band of the rotated image (fraction of max detected height) where
         * card names appear when the card is centred under the name-zone indicator.
         * Intentionally generous to survive slight tilt or card size variation.
         */
        const val NAME_ZONE_TOP_FRACTION    = 0.30f
        const val NAME_ZONE_BOTTOM_FRACTION = 0.60f

        private val LATIN_LANGUAGES = setOf("en", "es", "de", "fr", "it", "pt")
    }

    private val recognizerCache = mutableMapOf<String, TextRecognizer>()

    private fun getRecognizer(language: String): TextRecognizer {
        val key = when {
            language in LATIN_LANGUAGES -> "latin"
            language == "ja"            -> "ja"
            language == "ko"            -> "ko"
            else                        -> "latin"
        }
        return recognizerCache.getOrPut(key) {
            when (key) {
                "ja" -> TextRecognition.getClient(
                    com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions.Builder().build()
                )
                "ko" -> TextRecognition.getClient(
                    com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions.Builder().build()
                )
                else -> TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            }
        }
    }

    /**
     * Runs OCR on [mediaImage] and returns the most likely card name within the name zone,
     * or null if nothing valid is detected.
     *
     * [mediaImage] must NOT be closed until this function returns; [ImageProxy.close] must
     * be called by the caller AFTER awaiting this result.
     *
     * @param mediaImage      Raw YUV_420_888 image from [ImageProxy.image].
     * @param rotationDegrees Degrees to rotate for upright display (from [ImageProxy.imageInfo]).
     * @param language        Language code used to select the OCR script.
     */
    suspend fun extractCardName(
        mediaImage: Image,
        rotationDegrees: Int,
        language: String = "en",
    ): String? {
        return try {
            val image = InputImage.fromMediaImage(mediaImage, rotationDegrees)
            val result = getRecognizer(language).process(image).await()
            extractFromResult(result)
        } catch (e: Exception) {
            android.util.Log.w("CardOcrAnalyzer", "OCR failed", e)
            null
        }
    }

    private fun extractFromResult(visionText: Text): String? {
        if (visionText.textBlocks.isEmpty()) return null

        val imageHeight = visionText.textBlocks
            .mapNotNull { it.boundingBox?.bottom }
            .maxOrNull()
            ?.takeIf { it > 0 } ?: return null

        val zoneTop    = imageHeight * NAME_ZONE_TOP_FRACTION
        val zoneBottom = imageHeight * NAME_ZONE_BOTTOM_FRACTION

        val candidates = visionText.textBlocks
            .filter { block ->
                val top = block.boundingBox?.top ?: return@filter false
                top >= zoneTop && top <= zoneBottom
            }
            .flatMap { it.lines }
            .filter { line ->
                val text = line.text.trim()
                text.length in 2..50 &&
                    !text.all { it.isDigit() || it == '/' } &&
                    !text.contains('{') &&
                    !text.contains('}') &&
                    !isCardTypeLine(text) &&
                    !isRulesText(text)
            }
            .sortedBy { it.boundingBox?.top ?: Int.MAX_VALUE }

        return candidates.firstOrNull()?.text?.trim()?.let { cleanOcrText(it) }
    }

    private fun isCardTypeLine(text: String): Boolean {
        val types = setOf(
            "Creature", "Instant", "Sorcery", "Enchantment",
            "Artifact", "Planeswalker", "Land", "Battle",
            "Legendary", "Basic", "Snow", "Token",
            "Human", "Wizard", "Warrior", "Dragon",
            "Elf", "Goblin", "Zombie", "Angel", "Demon",
        )
        return types.any { text.contains(it, ignoreCase = true) }
    }

    private fun isRulesText(text: String): Boolean {
        val phraseKeywords = setOf(
            "when ", "whenever ", "at the beginning",
            "draw a card", "you may", "each player", "all creatures",
            "at the end", "at the start",
        )
        val singleWordKeywords = setOf(
            "flying", "haste", "trample", "lifelink",
            "deathtouch", "vigilance", "reach", "flash",
            "target", "damage", "destroy", "exile",
            "tap", "untap", "counter",
        )
        val lower = text.lowercase().trim()
        if (phraseKeywords.any { lower.contains(it) }) return true
        return singleWordKeywords.any { kw -> lower.contains(kw) && lower != kw }
    }

    private fun cleanOcrText(text: String): String? {
        val cleaned = text
            .replace(Regex("[|!]"), "I")
            .replace(Regex("[0O](?=[a-z])"), "O")
            .replace("’", "'")
            .trim()
        return if (cleaned.length >= 2) cleaned else null
    }

    fun close() {
        recognizerCache.values.forEach { it.close() }
        recognizerCache.clear()
    }
}
