package com.mmg.manahub.feature.scanner.data

import android.media.Image
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.mmg.manahub.feature.scanner.domain.ScannerZone
import com.mmg.manahub.feature.scanner.domain.model.OcrCandidate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import kotlin.math.abs

/**
 * Extracts the MTG card name from a camera frame using ML Kit Text Recognition.
 *
 * Receives the raw [android.media.Image] (YUV_420_888) directly from CameraX — ML Kit handles
 * rotation internally via `rotationDegrees`, so no manual YUV→Bitmap conversion is needed for OCR
 * itself. The **rotated** frame dimensions are still computed in [runRecognition] (WS2.1,
 * 2026-08-24: `if (rotationDegrees % 180 == 0) mediaImage.width to mediaImage.height else
 * mediaImage.height to mediaImage.width`) because ML Kit's [Text] bounding boxes come back in the
 * upright/rotated coordinate space, and the name-zone geometry must be evaluated in that same
 * space. Previously [extractFromResult] derived a pseudo-height from `max(boundingBox.bottom)` of
 * whatever text happened to be detected — it floated frame to frame and never matched the real
 * frame size (finding F3 in `docs/plans/scanner-reliability-plan.md`).
 *
 * Only text lines whose centre falls inside [ScannerZone.TOP]…[ScannerZone.BOTTOM] of frame
 * height, and within [ScannerZone.WIDTH] of frame width (centred), both widened by
 * [ScannerZone.TOLERANCE], are considered (WS2.2). This is the same zone `NameZoneIndicator` in
 * `ScannerScreen` draws on screen, so the visual guide matches what is actually scanned.
 *
 * ### Candidate scoring (WS2.3/W2.4, 2026-08-24)
 * Earlier versions hard-rejected any line containing a card-type or rules-text keyword as a
 * *substring* — `"Shivan Dragon"` was thrown out for containing `"Dragon"`, `"Counterspell"` for
 * containing `"counter"`, `"Preacher"` for containing `"reach"`, etc. (finding F4). A rejected
 * name meant the pipeline fell through to garbage text (artist line, flavour text) and produced a
 * doomed Scryfall query — this is what caused the request storm this workstream exists to fix.
 *
 * [extractFromResult] instead builds every line surviving the *structural* hard filters (see
 * [isStructurallyValid]) into an [OcrCandidate], scores it, and returns the best-scoring one:
 * - `+` [WEIGHT_HEIGHT] × relative line height — the **dominant** term. Card names are printed in
 *   the largest font in the name zone, so height alone should usually decide the winner.
 * - `+` [WEIGHT_HORIZONTAL_CENTER] × horizontal centredness (card names are centred on the card).
 * - `+` [WEIGHT_VERTICAL_CENTER] × closeness to the zone's vertical centre.
 * - `-` [PENALTY_DASH] when the line contains an em/en dash (`—`/`–`) — type lines use one in
 *   every supported language ("Creature — Human Wizard", "Criatura — Humano Mago", …).
 * - `-` [PENALTY_RULES_TEXT] when the line ends in `.` or is longer than
 *   [RULES_TEXT_LENGTH_THRESHOLD] characters — rules text, not a name.
 * - `-` [PENALTY_KEYWORD] when a type-line or rules-text keyword matches **as a whole word**
 *   (`\b…\b` — never a substring, which is what caused F4) **and** the line is not the tallest
 *   candidate. A name that happens to contain a real keyword as a whole word (`"Shivan Dragon"`
 *   contains the word "Dragon") is still expected to be the tallest text in the zone and is
 *   therefore exempt from this penalty — the height term alone should already separate it from
 *   the (shorter) type line that also mentions "Dragon".
 *
 * These weights are tuned against the WS2.A test table in the plan (Shivan Dragon / Serra Angel /
 * Goblin Guide / Counterspell / Preacher / Dragonlord Ojutai must all beat their type line; a
 * rules-text line must lose to a shorter, taller name line) — see
 * `CardOcrAnalyzerExtractionTest`.
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
        // ── Scoring weights (WS2.3) ─────────────────────────────────────────────────────────
        // Height is deliberately the dominant term: its max contribution (100, at ratio 1.0)
        // exceeds the combined max of the two positional terms (25 + 15 = 40), so a genuinely
        // tall line only loses to a shorter one when the shorter one also avoids every penalty
        // AND sits closer to dead centre than the tall line — a combination that never happens
        // for a real type/rules line versus a real name line in practice.
        private const val WEIGHT_HEIGHT = 100f
        private const val WEIGHT_HORIZONTAL_CENTER = 25f
        private const val WEIGHT_VERTICAL_CENTER = 15f

        private const val PENALTY_DASH = 80f
        private const val PENALTY_RULES_TEXT = 60f
        private const val PENALTY_KEYWORD = 50f

        /** Lines longer than this (even without a trailing `.`) also take [PENALTY_RULES_TEXT]. */
        private const val RULES_TEXT_LENGTH_THRESHOLD = 40

        // ── Hard structural bounds (true filters, never scored) ────────────────────────────
        private const val MIN_TEXT_LENGTH = 2
        private const val MAX_TEXT_LENGTH = 50

        /** Matches a power/toughness line such as `"3/4"` or `"3 / 4"`. */
        private val PT_LINE_REGEX = Regex("^\\d+\\s*/\\s*\\d+$")

        // ── Keyword sets (WS2.3 — penalties, not filters) ───────────────────────────────────
        // Card-type / creature-type / supertype keywords across the 6 Latin-script languages the
        // language selector offers (EN/ES/DE/FR/IT/PT). Matched as WHOLE WORDS only via
        // [TYPE_LINE_KEYWORD_REGEXES] — a plain substring match here is what used to reject real
        // card names like "Shivan Dragon" or "Serra Angel" (finding F4).
        private val TYPE_LINE_KEYWORDS = setOf(
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
            "Schlacht",        // Battle
            "Legendär",        // Legendary
            "Normales",        // Basic (land prefix)
            "Spielstein",      // Token
            "Mensch",          // Human (DE)
            "Magier",          // Wizard (DE)
            "Krieger",         // Warrior (DE)
            "Drache",          // Dragon (DE)
            "Engel",           // Angel (DE)
            "Dämon",           // Demon (DE)

            // French card types (FR localised cards)
            "Créature",        // Creature
            "Éphémère",        // Instant
            "Rituel",          // Sorcery
            "Enchantement",    // Enchantment (FR — same as EN spelling)
            "Artefact",        // Artifact (FR — same as EN spelling)
            "Terrain",         // Land

            // Italian card types (IT localised cards)
            "Creatura",        // Creature
            "Istantaneo",      // Instant
            "Stregoneria",     // Sorcery
            "Incantesimo",     // Enchantment
            "Artefatto",       // Artifact
            "Terra",           // Land

            // Portuguese card types (PT localised cards)
            "Instantâneo",     // Instant
            "Feitiço",         // Sorcery
            "Encantamento",    // Enchantment
            "Artefato",        // Artifact
            "Terreno",         // Land

            // Shared across EN/DE/Goblin, and Elf/Zombie (EN — DE spellings match)
            "Elf", "Goblin", "Zombie",
        )
        private val TYPE_LINE_KEYWORD_REGEXES = TYPE_LINE_KEYWORDS.map {
            Regex("\\b" + Regex.escape(it) + "\\b", RegexOption.IGNORE_CASE)
        }

        // Rules-text phrases and single-word keywords. Phrases are checked with a plain
        // lowercase `contains` — a multi-word phrase has effectively no substring-false-positive
        // risk. Single words are checked as WHOLE WORDS only via
        // [RULES_TEXT_SINGLE_WORD_REGEXES] — this is what fixes "Preacher" (contains "reach"),
        // "Counterspell" (contains "counter") and "Tapestry Warden" (contains "tap").
        private val RULES_TEXT_PHRASES = setOf(
            // English rules-text phrases
            "when ", "whenever ", "at the beginning",
            "draw a card", "you may", "each player", "all creatures",
            "at the end", "at the start",

            // Spanish rules-text phrases (ES localised cards)
            "cuando ",              // when / whenever
            "al comienzo",          // at the beginning
            "roba una carta",       // draw a card
            "puedes ",              // you may
            "cada jugador",         // each player
            "todas las criaturas",  // all creatures
            "al final",             // at the end

            // German rules-text phrases (DE localised cards)
            "wenn ",                // when / whenever
            "zu beginn",            // at the beginning
            "ziehe eine karte",     // draw a card
            "du kannst",            // you may
            "jeder spieler",        // each player
            "alle kreaturen",       // all creatures
            "am ende",              // at the end
        )
        private val RULES_TEXT_SINGLE_WORDS = setOf(
            // English keywords
            "flying", "haste", "trample", "lifelink",
            "deathtouch", "vigilance", "reach", "flash",
            "target", "damage", "destroy", "exile",
            "tap", "untap", "counter",

            // Spanish keywords (ES localised cards)
            "volar",            // flying (ES)
            "prisa",            // haste (ES)
            "arrollar",         // trample (ES)
            "vinculo vital",    // lifelink (ES)
            "toque mortal",     // deathtouch (ES)
            "vigilancia",       // vigilance (ES)
            "alcance",          // reach (ES)
            "destello",         // flash (ES)
            "objetivo",         // target (ES)
            "daño",             // damage (ES)
            "destruir",         // destroy (ES)
            "exiliar",          // exile (ES)
            "girar",            // tap (ES)
            "enderezar",        // untap (ES)

            // German keywords (DE localised cards)
            "flugfähigkeit",    // flying (DE)
            "eile",             // haste (DE)
            "trampeln",         // trample (DE)
            "lebensband",       // lifelink (DE)
            "todesberührung",   // deathtouch (DE)
            "wachsamkeit",      // vigilance (DE)
            "reichweite",       // reach (DE)
            "blitz",            // flash (DE)
            "ziel",             // target (DE)
            "schaden",          // damage (DE)
            "zerstören",        // destroy (DE)
            "verbannen",        // exile (DE)
        )
        private val RULES_TEXT_SINGLE_WORD_REGEXES = RULES_TEXT_SINGLE_WORDS.map {
            Regex("\\b" + Regex.escape(it) + "\\b", RegexOption.IGNORE_CASE)
        }
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
     * Runs OCR on [mediaImage] and returns the best-scoring [OcrCandidate] for the card name
     * within the name zone, or null if nothing valid is detected.
     *
     * [mediaImage] must NOT be closed until this function returns; `ImageProxy.close` must
     * be called by the caller AFTER awaiting this result.
     *
     * @param mediaImage      Raw YUV_420_888 image from `ImageProxy.image`.
     * @param rotationDegrees Degrees to rotate for upright display (from `ImageProxy.imageInfo`).
     */
    suspend fun extractCardName(
        mediaImage: Image,
        rotationDegrees: Int,
    ): OcrCandidate? {
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

    private suspend fun runRecognition(mediaImage: Image, rotationDegrees: Int): OcrCandidate? {
        val image = InputImage.fromMediaImage(mediaImage, rotationDegrees)
        val result = getOrCreateRecognizer().process(image).await()

        // W2.1: ML Kit's Text bounding boxes come back in the rotated/upright coordinate space,
        // so the zone maths must use the ROTATED frame dimensions, not the raw sensor ones.
        val (rotatedWidth, rotatedHeight) = rotatedDimensions(mediaImage.width, mediaImage.height, rotationDegrees)
        return extractFromResult(result, rotatedWidth, rotatedHeight)
    }

    /**
     * Swaps [width]/[height] when [rotationDegrees] is an odd multiple of 90° (90 or 270) —
     * ML Kit's [Text] bounding boxes come back in the rotated/upright coordinate space, so the
     * zone maths in [scoreLines] must be evaluated against the ROTATED frame dimensions, not the
     * raw sensor ones (finding F3 / W2.1). `internal` so it is directly unit-testable without
     * mocking [android.media.Image] or ML Kit types.
     */
    internal fun rotatedDimensions(width: Int, height: Int, rotationDegrees: Int): Pair<Int, Int> =
        if (rotationDegrees % 180 == 0) width to height else height to width

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

    /**
     * Maps [visionText]'s lines to [RawLine]s and delegates to [scoreLines] for the actual
     * zone-filtering + scoring (against the real, rotated [imageWidth]/[imageHeight] — see
     * [runRecognition]). Returns null when [visionText] has no text blocks or a line has no
     * bounding box.
     *
     * Kept as a thin mapping layer on purpose: ML Kit's [Text.TextBlock]/[Text.Line] are
     * unmockable in this project's JVM unit test environment (MockK's inline agent fails to
     * instrument them — "class redefinition failed: attempted to add a method" — reproducible
     * for every case, not a flakiness/repetition issue). [scoreLines] operates on the
     * ML-Kit-independent [RawLine] so the actual zone/scoring logic stays fully unit-testable;
     * see `CardOcrAnalyzerExtractionTest`.
     */
    private fun extractFromResult(
        visionText: Text,
        imageWidth: Int,
        imageHeight: Int,
    ): OcrCandidate? {
        if (visionText.textBlocks.isEmpty()) return null
        val rawLines = visionText.textBlocks
            .flatMap { it.lines }
            .mapNotNull { line ->
                val box = line.boundingBox ?: return@mapNotNull null
                RawLine(line.text.trim(), box.top, box.bottom, box.left, box.right)
            }
        return scoreLines(rawLines, imageWidth, imageHeight)
    }

    /**
     * A single OCR line's text + bounding-box geometry, decoupled from ML Kit's [Text.Line] —
     * see [extractFromResult] KDoc for why.
     */
    internal data class RawLine(
        val text: String,
        val top: Int,
        val bottom: Int,
        val left: Int,
        val right: Int,
    )

    /**
     * Pure geometry + scoring core of name extraction (WS2.2/W2.3), decoupled from ML Kit types
     * so it is directly unit-testable — see [extractFromResult] KDoc. Filters [rawLines] to the
     * name zone (vertical AND horizontal band — see [ScannerZone]), applies the hard structural
     * filters (see [isStructurallyValid]), scores every survivor (see class KDoc "Candidate
     * scoring"), and returns the best-scoring [OcrCandidate]. Returns null when [rawLines] is
     * empty, [imageWidth]/[imageHeight] are degenerate, or nothing survives the structural
     * filters.
     */
    internal fun scoreLines(
        rawLines: List<RawLine>,
        imageWidth: Int,
        imageHeight: Int,
    ): OcrCandidate? {
        if (rawLines.isEmpty() || imageWidth <= 0 || imageHeight <= 0) return null

        val zoneTop = imageHeight * (ScannerZone.TOP - ScannerZone.TOLERANCE)
        val zoneBottom = imageHeight * (ScannerZone.BOTTOM + ScannerZone.TOLERANCE)
        val zoneCenterY = imageHeight * ((ScannerZone.TOP + ScannerZone.BOTTOM) / 2f)
        val zoneHalfHeight = ((zoneBottom - zoneTop) / 2f).coerceAtLeast(1f)

        val frameCenterX = imageWidth / 2f
        val halfBandWidth =
            (imageWidth * (ScannerZone.WIDTH / 2f + ScannerZone.TOLERANCE)).coerceAtLeast(1f)

        val zoneLines = rawLines
            .mapNotNull { line -> toZoneLine(line, zoneTop, zoneBottom, frameCenterX, halfBandWidth) }
            .filter { isStructurallyValid(it.text) }

        if (zoneLines.isEmpty()) return null

        val tallestHeight = zoneLines.maxOf { it.height }

        val best = zoneLines
            .map { scoreLine(it, tallestHeight, frameCenterX, halfBandWidth, zoneCenterY, zoneHalfHeight) }
            .maxByOrNull { it.score }
            ?: return null

        val cleaned = cleanOcrText(best.text) ?: return null
        return best.copy(text = cleaned)
    }

    /** A single OCR line already confirmed to sit inside the name zone (vertically + horizontally). */
    private data class ZoneLine(
        val text: String,
        val top: Int,
        val bottom: Int,
        val left: Int,
        val right: Int,
    ) {
        val height: Int get() = (bottom - top).coerceAtLeast(1)
        val centerX: Float get() = (left + right) / 2f
        val centerY: Float get() = (top + bottom) / 2f
    }

    /**
     * Converts [line] to a [ZoneLine] when its bounding-box centre sits within the vertical band
     * ([zoneTop]…[zoneBottom]) AND the horizontal band ([frameCenterX] ± [halfBandWidth]) — the
     * horizontal constraint is new in WS2.2 (previously entirely absent). Returns null when the
     * line falls outside either band.
     */
    private fun toZoneLine(
        line: RawLine,
        zoneTop: Float,
        zoneBottom: Float,
        frameCenterX: Float,
        halfBandWidth: Float,
    ): ZoneLine? {
        val centerY = (line.top + line.bottom) / 2f
        if (centerY < zoneTop || centerY > zoneBottom) return null
        val centerX = (line.left + line.right) / 2f
        if (centerX < frameCenterX - halfBandWidth || centerX > frameCenterX + halfBandWidth) return null
        return ZoneLine(line.text, line.top, line.bottom, line.left, line.right)
    }

    /**
     * True structural hard filters — length bounds, pure P/T lines, mana-cost artefacts. These
     * are language-independent and unambiguous, so they stay true rejects rather than scoring
     * penalties (unlike the semantic keyword sets — see class KDoc).
     */
    private fun isStructurallyValid(text: String): Boolean {
        if (text.length !in MIN_TEXT_LENGTH..MAX_TEXT_LENGTH) return false
        if (text.all { it.isDigit() || it == '/' || it.isWhitespace() }) return false
        if (PT_LINE_REGEX.matches(text)) return false
        if (text.contains('{') || text.contains('}')) return false
        return true
    }

    private fun scoreLine(
        zoneLine: ZoneLine,
        tallestHeight: Int,
        frameCenterX: Float,
        halfBandWidth: Float,
        zoneCenterY: Float,
        zoneHalfHeight: Float,
    ): OcrCandidate {
        val heightRatio = zoneLine.height.toFloat() / tallestHeight.toFloat()
        val isTallest = zoneLine.height == tallestHeight

        val horizontalCloseness =
            1f - (abs(zoneLine.centerX - frameCenterX) / halfBandWidth).coerceIn(0f, 1f)
        val verticalCloseness =
            1f - (abs(zoneLine.centerY - zoneCenterY) / zoneHalfHeight).coerceIn(0f, 1f)

        var score = heightRatio * WEIGHT_HEIGHT +
            horizontalCloseness * WEIGHT_HORIZONTAL_CENTER +
            verticalCloseness * WEIGHT_VERTICAL_CENTER

        if (containsDash(zoneLine.text)) {
            score -= PENALTY_DASH
        }
        if (zoneLine.text.endsWith('.') || zoneLine.text.length > RULES_TEXT_LENGTH_THRESHOLD) {
            score -= PENALTY_RULES_TEXT
        }
        if (!isTallest && (matchesTypeLineKeyword(zoneLine.text) || matchesRulesTextKeyword(zoneLine.text))) {
            score -= PENALTY_KEYWORD
        }

        return OcrCandidate(text = zoneLine.text, score = score, lineHeightRatio = heightRatio)
    }

    private fun containsDash(text: String): Boolean = text.contains('—') || text.contains('–')

    private fun matchesTypeLineKeyword(text: String): Boolean =
        TYPE_LINE_KEYWORD_REGEXES.any { it.containsMatchIn(text) }

    private fun matchesRulesTextKeyword(text: String): Boolean {
        val lower = text.lowercase()
        if (RULES_TEXT_PHRASES.any { lower.contains(it) }) return true
        return RULES_TEXT_SINGLE_WORD_REGEXES.any { it.containsMatchIn(text) }
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
