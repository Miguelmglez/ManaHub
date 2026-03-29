package com.mmg.magicfolder.feature.scanner

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class CardNameAnalyzer(
    private val onCardNameDetected: (String) -> Unit,
) : ImageAnalysis.Analyzer {

    private val recognizer = TextRecognition.getClient(
        TextRecognizerOptions.DEFAULT_OPTIONS
    )

    private var isProcessing = false
    private var lastDetectionTime = 0L
    private val DEBOUNCE_MS = 800L

    override fun analyze(imageProxy: ImageProxy) {
        if (isProcessing) {
            imageProxy.close()
            return
        }

        val now = System.currentTimeMillis()
        if (now - lastDetectionTime < DEBOUNCE_MS) {
            imageProxy.close()
            return
        }

        isProcessing = true

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            isProcessing = false
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
                isProcessing = false
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
        val rulesKeywords = setOf(
            "flying", "haste", "trample", "lifelink",
            "deathtouch", "vigilance", "reach", "flash",
            "when", "whenever", "at the beginning",
            "target", "damage", "destroy", "exile",
            "draw a card", "tap", "untap", "counter",
            "you may", "each player", "all creatures",
        )
        val lower = text.lowercase()
        return rulesKeywords.any { lower.contains(it) }
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
