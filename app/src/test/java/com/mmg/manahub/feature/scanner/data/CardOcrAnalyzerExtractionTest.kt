package com.mmg.manahub.feature.scanner.data

import com.mmg.manahub.feature.scanner.data.CardOcrAnalyzer.RawLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [CardOcrAnalyzer]'s WS2.A name-extraction accuracy (2026-08-24):
 * - Name-zone geometry uses the real, rotation-aware frame dimensions (W2.1) instead of a
 *   floating pseudo-height derived from detected text (finding F3).
 * - The zone applies both a vertical AND a horizontal constraint (W2.2) — the horizontal band
 *   is entirely new.
 * - Candidate scoring (W2.3) replaces hard keyword-substring rejection, so real card names that
 *   happen to contain a type/rules keyword as a whole word (Shivan Dragon, Serra Angel, Goblin
 *   Guide, Dragonlord Ojutai) or as a false substring (Counterspell, Preacher) are no longer
 *   thrown away (finding F4).
 * - [CardOcrAnalyzer.extractCardName] now returns a scored `OcrCandidate` (W2.4).
 *
 * ### Why this exercises [CardOcrAnalyzer.scoreLines]/[RawLine] instead of mocking ML Kit's
 * `Text`/`Text.TextBlock`/`Text.Line`
 * Those nested ML Kit types are unmockable in this project's JVM unit test environment — MockK's
 * inline agent fails every single case with `UnsupportedOperationException: class redefinition
 * failed: attempted to add a method` (not a flakiness/repetition issue: reproducible for every
 * mocked instance). [CardOcrAnalyzer.extractFromResult] is therefore kept as a thin ML-Kit →
 * [RawLine] mapping layer, and all the actual zone-filtering + scoring logic lives in the
 * `internal` [CardOcrAnalyzer.scoreLines], which this file exercises directly with plain
 * [RawLine] data — no mocking needed, and the assertions are exactly as meaningful (same
 * production code path, only the ML-Kit-shaped input is swapped for its already-mapped form).
 * The W2.1 rotated-dimension swap is covered separately via the pure
 * [CardOcrAnalyzer.rotatedDimensions] helper.
 *
 * Client lifecycle / recreate-retry behaviour is [CardOcrAnalyzerTest]'s scope, not this file's.
 */
class CardOcrAnalyzerExtractionTest {

    private val analyzer = CardOcrAnalyzer()

    /** Frame dimensions used across most cases: a portrait 1080×1920 upright frame. */
    private val frameWidth = 1080
    private val frameHeight = 1920

    /** One OCR line: [text] with a bounding box centred at ([centerX], [centerY]) sized [w]x[h]. */
    private fun line(text: String, centerX: Int, centerY: Int, w: Int, h: Int): RawLine =
        RawLine(
            text = text,
            top = centerY - h / 2,
            bottom = centerY + h / 2,
            left = centerX - w / 2,
            right = centerX + w / 2,
        )

    // ── W2.1 — rotation-aware dimension swap ────────────────────────────────────────────────

    @Test
    fun `rotation 0 keeps width and height as-is`() {
        assertEquals(1080 to 1920, analyzer.rotatedDimensions(1080, 1920, 0))
    }

    @Test
    fun `rotation 90 swaps width and height`() {
        assertEquals(1080 to 1920, analyzer.rotatedDimensions(1920, 1080, 90))
    }

    @Test
    fun `rotation 180 keeps width and height as-is`() {
        assertEquals(1080 to 1920, analyzer.rotatedDimensions(1080, 1920, 180))
    }

    @Test
    fun `rotation 270 swaps width and height`() {
        assertEquals(1080 to 1920, analyzer.rotatedDimensions(1920, 1080, 270))
    }

    // ── W2.1 — zone maths uses the real frame height, not a pseudo-height from detected text ──

    @Test
    fun `zone maths uses the real frame height, not a pseudo-height derived from the line itself`() {
        // Centred at y=850 (~0.4427 of the real 1920 frame height) — inside the 0.40..0.52 zone
        // against the REAL frame height. A naive "max(bottom)"-based pseudo-height (this line's
        // own bottom, ~870) would put 0.30..0.60 of ITSELF far outside where this line sits,
        // rejecting it — the exact bug finding F3 describes.
        val rawLines = listOf(line("Shivan Dragon", centerX = frameWidth / 2, centerY = 850, w = 300, h = 40))

        val result = analyzer.scoreLines(rawLines, frameWidth, frameHeight)

        assertEquals("Shivan Dragon", result?.text)
    }

    // ── W2.2 — horizontal band ───────────────────────────────────────────────────────────────

    @Test
    fun `a line whose centre-x is outside the width band is rejected`() {
        // Vertically inside the zone, but centred far to the left edge — well outside
        // ScannerZone.WIDTH (0.72) + tolerance of the frame width.
        val rawLines = listOf(line("Offscreen Text", centerX = 20, centerY = 850, w = 200, h = 40))

        val result = analyzer.scoreLines(rawLines, frameWidth, frameHeight)

        assertNull(result)
    }

    @Test
    fun `a line inside the vertical zone but outside the horizontal band loses to a centred line`() {
        val rawLines = listOf(
            line("Offscreen Text", centerX = 20, centerY = 850, w = 400, h = 60),
            line("Serra Angel", centerX = frameWidth / 2, centerY = 850, w = 260, h = 40),
        )

        val result = analyzer.scoreLines(rawLines, frameWidth, frameHeight)

        assertEquals("Serra Angel", result?.text)
    }

    // ── W2.3 — name wins over type line for keyword-containing names ───────────────────────

    private fun assertNameBeatsTypeLine(name: String, typeLine: String) {
        val rawLines = listOf(
            line(name, centerX = frameWidth / 2, centerY = 850, w = 300, h = 44),
            line(typeLine, centerX = frameWidth / 2, centerY = 900, w = 280, h = 26),
        )

        val result = analyzer.scoreLines(rawLines, frameWidth, frameHeight)

        assertEquals("$name should beat '$typeLine'", name, result?.text)
    }

    @Test
    fun `Shivan Dragon beats its type line`() = assertNameBeatsTypeLine("Shivan Dragon", "Creature — Dragon")

    @Test
    fun `Serra Angel beats its type line`() = assertNameBeatsTypeLine("Serra Angel", "Creature — Angel")

    @Test
    fun `Goblin Guide beats its type line`() = assertNameBeatsTypeLine("Goblin Guide", "Creature — Goblin")

    @Test
    fun `Counterspell beats a nearby rules line mentioning counter`() =
        assertNameBeatsTypeLine("Counterspell", "Counter target spell.")

    @Test
    fun `Preacher beats a nearby rules line mentioning reach`() =
        assertNameBeatsTypeLine("Preacher", "This creature has reach.")

    @Test
    fun `Dragonlord Ojutai beats its type line`() =
        assertNameBeatsTypeLine("Dragonlord Ojutai", "Legendary Creature — Elder Dragon")

    // ── em/en dash penalty demotes type lines in every language ────────────────────────────

    private fun assertDashLineLosesToName(typeLine: String) {
        val rawLines = listOf(
            line("Test Card Name", centerX = frameWidth / 2, centerY = 850, w = 300, h = 44),
            line(typeLine, centerX = frameWidth / 2, centerY = 900, w = 280, h = 26),
        )

        val result = analyzer.scoreLines(rawLines, frameWidth, frameHeight)

        assertEquals("Test Card Name", result?.text)
    }

    @Test
    fun `English em-dash type line is demoted`() = assertDashLineLosesToName("Creature — Human Wizard")

    @Test
    fun `Spanish em-dash type line is demoted`() = assertDashLineLosesToName("Criatura — Humano Mago")

    @Test
    fun `German em-dash type line is demoted`() = assertDashLineLosesToName("Kreatur — Mensch Magier")

    @Test
    fun `French em-dash type line is demoted`() = assertDashLineLosesToName("Créature — Humain Sorcier")

    // ── P/T lines and mana-cost artefacts are hard-rejected ─────────────────────────────────

    @Test
    fun `a pure power slash toughness line is rejected outright`() {
        val rawLines = listOf(line("3/4", centerX = frameWidth / 2, centerY = 850, w = 60, h = 30))

        assertNull(analyzer.scoreLines(rawLines, frameWidth, frameHeight))
    }

    @Test
    fun `a spaced power slash toughness line is rejected outright`() {
        val rawLines = listOf(line("3 / 4", centerX = frameWidth / 2, centerY = 850, w = 60, h = 30))

        assertNull(analyzer.scoreLines(rawLines, frameWidth, frameHeight))
    }

    @Test
    fun `a mana cost artefact line is rejected outright`() {
        val rawLines = listOf(line("{2}{R}{R}", centerX = frameWidth / 2, centerY = 850, w = 100, h = 30))

        assertNull(analyzer.scoreLines(rawLines, frameWidth, frameHeight))
    }

    @Test
    fun `a P-T line and mana cost artefact both lose to the real name`() {
        val rawLines = listOf(
            line("2/2", centerX = frameWidth / 2, centerY = 900, w = 50, h = 20),
            line("{1}{G}", centerX = frameWidth / 2, centerY = 820, w = 60, h = 18),
            line("Grizzly Bears", centerX = frameWidth / 2, centerY = 850, w = 260, h = 40),
        )

        val result = analyzer.scoreLines(rawLines, frameWidth, frameHeight)

        assertEquals("Grizzly Bears", result?.text)
    }

    // ── FR/IT/PT type words are penalised ───────────────────────────────────────────────────

    @Test
    fun `French type word Creature is demoted relative to the real name`() =
        assertNameBeatsTypeLine("Serra Angel", "Créature — Ange")

    @Test
    fun `Italian type word Creatura is demoted relative to the real name`() =
        assertNameBeatsTypeLine("Serra Angel", "Creatura — Angelo")

    @Test
    fun `Portuguese type word Criatura is demoted relative to the real name`() =
        assertNameBeatsTypeLine("Serra Angel", "Criatura — Anjo")

    // ── rules text (ends in '.' / long) loses to a shorter, taller name line ───────────────

    @Test
    fun `a long rules-text line ending in a period loses to a shorter taller name line`() {
        val rawLines = listOf(
            line("Counterspell", centerX = frameWidth / 2, centerY = 850, w = 260, h = 42),
            line(
                "Counter target spell unless its controller pays an additional cost.",
                centerX = frameWidth / 2,
                centerY = 900,
                w = 300,
                h = 22,
            ),
        )

        val result = analyzer.scoreLines(rawLines, frameWidth, frameHeight)

        assertEquals("Counterspell", result?.text)
    }

    // ── OcrCandidate shape (W2.4) ────────────────────────────────────────────────────────────

    @Test
    fun `the winning candidate is the tallest line and reports lineHeightRatio 1_0`() {
        val rawLines = listOf(
            line("Shivan Dragon", centerX = frameWidth / 2, centerY = 850, w = 300, h = 44),
            line("Creature — Dragon", centerX = frameWidth / 2, centerY = 900, w = 280, h = 22),
        )

        val result = analyzer.scoreLines(rawLines, frameWidth, frameHeight)

        assertEquals("Shivan Dragon", result?.text)
        assertEquals(1.0f, result?.lineHeightRatio)
        assertTrue("winning candidate should have a positive score", (result?.score ?: -1f) > 0f)
    }

    // ── cleanOcrText behaviour preserved ────────────────────────────────────────────────────

    @Test
    fun `cleanOcrText artefact substitutions are still applied to the winning candidate`() {
        // '|' / '!' become 'I'; a curly apostrophe becomes a straight one.
        val rawLines = listOf(line("Se|| of Micah", centerX = frameWidth / 2, centerY = 850, w = 260, h = 40))

        val result = analyzer.scoreLines(rawLines, frameWidth, frameHeight)

        assertEquals("SeII of Micah", result?.text)
    }

    @Test
    fun `nothing in the zone returns null`() {
        assertNull(analyzer.scoreLines(emptyList(), frameWidth, frameHeight))
    }
}
