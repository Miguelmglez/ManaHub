package com.mmg.manahub.feature.scanner

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.atomic.AtomicBoolean

class CardNameAnalyzer(
    private val onCardNameDetected: (String) -> Unit,
) : ImageAnalysis.Analyzer {

    private val recognizer = TextRecognition.getClient(
        TextRecognizerOptions.DEFAULT_OPTIONS
    )

    // AtomicBoolean ensures compareAndSet is used for the busy-flag check,
    // avoiding a TOCTOU race between multiple camera background threads.
    private val isProcessing = AtomicBoolean(false)
    @Volatile private var lastDetectionTime = 0L
    private val DEBOUNCE_MS = 800L

    override fun analyze(imageProxy: ImageProxy) {
        if (!isProcessing.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }

        val now = System.currentTimeMillis()
        if (now - lastDetectionTime < DEBOUNCE_MS) {
            isProcessing.set(false)
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            isProcessing.set(false)
            imageProxy.close()
            return
        }

        val image = InputImage.fromMediaImage(
            mediaImage,
            imageProxy.imageInfo.rotationDegrees,
        )

        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val cardName = extractCardName(visionText)
                if (cardName != null) {
                    lastDetectionTime = System.currentTimeMillis()
                    onCardNameDetected(cardName)
                }
            }
            .addOnFailureListener {
                // Ignore individual frame errors
            }
            .addOnCompleteListener {
                isProcessing.set(false)
                imageProxy.close()
            }
    }

    private fun extractCardName(visionText: Text): String? {
        if (visionText.textBlocks.isEmpty()) return null

        val imageBottom = visionText.textBlocks
            .maxOfOrNull { it.boundingBox?.bottom ?: 0 } ?: return null

        val candidates = visionText.textBlocks
            .filter { block ->
                val blockTop = block.boundingBox?.top ?: 0
                blockTop < imageBottom / 3
            }
            .flatMap { it.lines }
            .filter { line ->
                val text = line.text.trim()
                text.length in 3..40 &&
                    !text.all { it.isDigit() || it == '/' } &&
                    !text.contains('{') &&
                    !text.contains('}') &&
                    !isCardTypeLine(text) &&
                    !isRulesText(text)
            }

        val bestCandidate = candidates
            .sortedBy { it.boundingBox?.top ?: Int.MAX_VALUE }
            .firstOrNull()
            ?.text
            ?.trim()

        return bestCandidate?.let { cleanOcrText(it) }
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
        // Multi-word phrases are unambiguous — match as substrings.
        val phraseKeywords = setOf(
            "when ", "whenever ", "at the beginning",
            "draw a card", "you may", "each player", "all creatures",
            "at the end", "at the start",
        )
        // Single-word keywords that could also be card names (e.g. "Flash",
        // "Counter", "Reach") — only treat as rules text when they are NOT the
        // entire trimmed string (i.e. the line has additional words around them).
        val singleWordKeywords = setOf(
            "flying", "haste", "trample", "lifelink",
            "deathtouch", "vigilance", "reach", "flash",
            "target", "damage", "destroy", "exile",
            "tap", "untap", "counter",
        )
        val lower = text.lowercase().trim()

        // Phrase match — these are never standalone card names.
        if (phraseKeywords.any { lower.contains(it) }) return true

        // Single-word match — only flag as rules text when the line is NOT
        // just that one word (pure "Flash" is a card name; "Flash: do X" is not).
        return singleWordKeywords.any { kw ->
            lower.contains(kw) && lower != kw
        }
    }

    private fun cleanOcrText(text: String): String? {
        val cleaned = text
            .replace(Regex("[|!]"), "I")
            .replace(Regex("[0O](?=[a-z])"), "O")
            .replace("\u2019", "'")
            .trim()
        return if (cleaned.length >= 3) cleaned else null
    }
}
