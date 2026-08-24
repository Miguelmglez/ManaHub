package com.mmg.manahub.feature.scanner.data

import android.media.Image
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.mmg.manahub.feature.scanner.data.CardOcrAnalyzer.Companion.NAME_ZONE_BOTTOM_FRACTION
import com.mmg.manahub.feature.scanner.data.CardOcrAnalyzer.Companion.NAME_ZONE_TOP_FRACTION
import kotlinx.coroutines.CancellationException
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
 * ### Lifecycle contract (WS1, 2026-08-24)
 * This class is a Hilt `@Singleton` — one instance for the whole process (see
 * `ScannerModule.provideCardOcrAnalyzer`). **The UI must never call [close]**: a screen is
 * disposed far more often than the process is torn down (leaving the scanner, rotation, any
 * Activity recreation), and a closed ML Kit [TextRecognizer] never recovers on its own — every
 * subsequent [TextRecognizer.process] call throws forever. [close] exists only for tests and
 * explicit process teardown.
 *
 * To make the class resilient even if a client is ever closed out from under it (or ML Kit
 * itself invalidates the client, e.g. after a Play Services module update), the recognizer is
 * held in a nullable, `@Volatile` field instead of `by lazy` and is **recreated on demand**:
 * - [getOrCreateRecognizer] lazily (re)builds the client the first time it is needed.
 * - [recreateIfNeeded] proactively ensures a live client exists; call this on scanner
 *   (re)entry from the screen so the very first frame of a new session never pays the
 *   recreate-on-failure cost.
 * - [extractCardName] detects the "client already closed / unavailable" failure signature
 *   ([IllegalStateException], or [MlKitException] with [MlKitException.NOT_FOUND] /
 *   [MlKitException.UNAVAILABLE]) and recreates the client **once**, retrying that same frame
 *   once, before giving up. Any other exception is treated as a soft per-frame failure (`null`).
 *
 * Supported script:
 * - Latin (EN/ES/DE/FR/IT/PT): [TextRecognizerOptions.DEFAULT_OPTIONS]
 *
 * Japanese/Korean OCR support was removed (2026-07-22, Android 16 / API 36 migration): the
 * `-japanese`/`-korean` ML Kit artefacts ship native libraries that are not 16KB-page-size
 * aligned, with no fixed release available upstream. Scryfall search by language (`AddCard`,
 * unrelated to on-device OCR) still supports ja/ko — only recognition of physical cards does not.
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
    }

    /**
     * The live ML Kit client, or `null` when none has been built yet (first use) or the
     * previous one was explicitly [close]d. `@Volatile` + [recognizerLock] so concurrent
     * frames can never race two clients into existence.
     */
    @Volatile private var recognizer: TextRecognizer? = null
    private val recognizerLock = Any()

    private fun getOrCreateRecognizer(): TextRecognizer {
        recognizer?.let { return it }
        synchronized(recognizerLock) {
            recognizer?.let { return it }
            val fresh = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            recognizer = fresh
            return fresh
        }
    }

    /**
     * Ensures a live client exists without waiting for the first frame to discover it is
     * missing. Call this when the scanner screen (re)enters composition.
     */
    fun recreateIfNeeded() {
        getOrCreateRecognizer()
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
     */
    suspend fun extractCardName(
        mediaImage: Image,
        rotationDegrees: Int,
    ): String? {
        return try {
            runRecognition(mediaImage, rotationDegrees)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (isRecognizerUnavailable(e)) {
                logRecognitionFailure(e, willRetry = true)
                recreateRecognizer()
                return try {
                    runRecognition(mediaImage, rotationDegrees)
                } catch (retryException: CancellationException) {
                    throw retryException
                } catch (retryException: Exception) {
                    logRecognitionFailure(retryException, willRetry = false)
                    null
                }
            }
            logRecognitionFailure(e, willRetry = false)
            null
        }
    }

    private suspend fun runRecognition(mediaImage: Image, rotationDegrees: Int): String? {
        val image = InputImage.fromMediaImage(mediaImage, rotationDegrees)
        val result = getOrCreateRecognizer().process(image).await()
        return extractFromResult(result)
    }

    /**
     * True when [e] is the failure signature of a closed/unavailable ML Kit client rather
     * than an ordinary per-frame recognition failure.
     */
    private fun isRecognizerUnavailable(e: Exception): Boolean {
        if (e is IllegalStateException) return true
        if (e is MlKitException) {
            return e.errorCode == MlKitException.NOT_FOUND || e.errorCode == MlKitException.UNAVAILABLE
        }
        return false
    }

    private fun recreateRecognizer() {
        synchronized(recognizerLock) {
            recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        }
    }

    private fun logRecognitionFailure(e: Exception, willRetry: Boolean) {
        if (com.mmg.manahub.BuildConfig.DEBUG) {
            android.util.Log.w("CardOcrAnalyzer", "OCR failed (willRetry=$willRetry)", e)
        } else {
            android.util.Log.w("CardOcrAnalyzer", "OCR failed: ${e.javaClass.simpleName} (willRetry=$willRetry)")
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
            // English card types and common sub-types
            "Creature", "Instant", "Sorcery", "Enchantment",
            "Artifact", "Planeswalker", "Land", "Battle",
            "Legendary", "Basic", "Snow", "Token",
            "Human", "Wizard", "Warrior", "Dragon",
            "Elf", "Goblin", "Zombie", "Angel", "Demon",

            // Spanish card types (ES localised cards)
            "Criatura",        // Creature
            "Instantáneo",     // Instant
            "Conjuro",         // Sorcery
            "Encantamiento",   // Enchantment
            "Artefacto",       // Artifact
            "Planeswalker",    // same in ES
            "Tierra",          // Land
            "Batalla",         // Battle
            "Legendario",      // Legendary
            "Básica",          // Basic (land)
            "Ficha",           // Token
            "Humano",          // Human
            "Mago",            // Wizard (ES)
            "Guerrero",        // Warrior (ES)
            "Dragón",          // Dragon (ES)
            "Elfo",            // Elf (ES)
            "Trasgo",          // Goblin (ES)
            "Zombi",           // Zombie (ES)
            "Ángel",           // Angel (ES)
            "Demonio",         // Demon (ES)

            // German card types (DE localised cards)
            "Kreatur",         // Creature
            "Spontanzauber",   // Instant
            "Hexerei",         // Sorcery
            "Verzauberung",    // Enchantment
            "Artefakt",        // Artifact
            "Planeswalker",    // same in DE
            "Land",            // same in DE
            "Schlacht",        // Battle
            "Legendär",        // Legendary
            "Normales",        // Basic (land prefix)
            "Spielstein",      // Token
            "Mensch",          // Human (DE)
            "Magier",          // Wizard (DE)
            "Krieger",         // Warrior (DE)
            "Drache",          // Dragon (DE)
            "Elf",             // Elf (DE — same as EN)
            "Goblin",          // Goblin (DE — same as EN)
            "Zombie",          // Zombie (DE — same as EN)
            "Engel",           // Angel (DE)
            "Dämon",           // Demon (DE)
        )
        return types.any { text.contains(it, ignoreCase = true) }
    }

    private fun isRulesText(text: String): Boolean {
        val phraseKeywords = setOf(
            // English rules-text phrases
            "when ", "whenever ", "at the beginning",
            "draw a card", "you may", "each player", "all creatures",
            "at the end", "at the start",

            // Spanish rules-text phrases (ES localised cards)
            "cuando ",         // when / whenever
            "al comienzo",     // at the beginning
            "roba una carta",  // draw a card
            "puedes ",         // you may
            "cada jugador",    // each player
            "todas las criaturas", // all creatures
            "al final",        // at the end

            // German rules-text phrases (DE localised cards)
            "wenn ",           // when / whenever
            "zu beginn",       // at the beginning
            "ziehe eine karte",// draw a card
            "du kannst",       // you may
            "jeder spieler",   // each player
            "alle kreaturen",  // all creatures
            "am ende",         // at the end
        )
        val singleWordKeywords = setOf(
            // English keywords
            "flying", "haste", "trample", "lifelink",
            "deathtouch", "vigilance", "reach", "flash",
            "target", "damage", "destroy", "exile",
            "tap", "untap", "counter",

            // Spanish keywords (ES localised cards)
            "volar",           // flying (ES)
            "prisa",           // haste (ES)
            "arrollar",        // trample (ES)
            "vinculo vital",   // lifelink (ES)
            "toque mortal",    // deathtouch (ES)
            "vigilancia",      // vigilance (ES)
            "alcance",         // reach (ES)
            "destello",        // flash (ES)
            "objetivo",        // target (ES)
            "daño",            // damage (ES)
            "destruir",        // destroy (ES)
            "exiliar",         // exile (ES)
            "girar",           // tap (ES)
            "enderezar",       // untap (ES)

            // German keywords (DE localised cards)
            "flugfähigkeit",   // flying (DE)
            "eile",            // haste (DE)
            "trampeln",        // trample (DE)
            "lebensband",      // lifelink (DE)
            "todesberührung",  // deathtouch (DE)
            "wachsamkeit",     // vigilance (DE)
            "reichweite",      // reach (DE)
            "blitz",           // flash (DE)
            "ziel",            // target (DE)
            "schaden",         // damage (DE)
            "zerstören",       // destroy (DE)
            "verbannen",       // exile (DE)
            "tap",             // tap (DE — same as EN)
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

    /**
     * Closes the current client and clears the field so the next call rebuilds a fresh one.
     * **Never call this from the Compose tree** — see the class KDoc lifecycle contract.
     * Intended for tests and explicit process teardown only.
     */
    fun close() {
        synchronized(recognizerLock) {
            recognizer?.close()
            recognizer = null
        }
    }
}
