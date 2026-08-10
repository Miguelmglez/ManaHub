package com.mmg.manahub.core.model.puzzle

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Covers the normalization rules documented on [PuzzleNameNormalizer.normalize]. These rules are
 * ALSO hand-reproduced in `tools/puzzle-generator/stages/normalize.mjs` — if a test here starts
 * failing after a rule change, that Node.js script must be updated to match.
 */
class PuzzleNameNormalizerTest {

    @Test
    fun `simple name is lowercased and trimmed`() {
        assertEquals("lightning bolt", PuzzleNameNormalizer.normalize("Lightning Bolt"))
    }

    @Test
    fun `double-faced card name keeps only the front face`() {
        assertEquals("fire", PuzzleNameNormalizer.normalize("Fire // Ice"))
    }

    @Test
    fun `accented name strips diacritics`() {
        assertEquals("seance", PuzzleNameNormalizer.normalize("Séance"))
    }

    @Test
    fun `apostrophe is stripped without leaving a stray space`() {
        assertEquals("urzas tower", PuzzleNameNormalizer.normalize("Urza's Tower"))
    }

    @Test
    fun `extra internal and surrounding whitespace collapses to single spaces`() {
        assertEquals("llanowar elves", PuzzleNameNormalizer.normalize("  Llanowar   Elves  "))
    }

    @Test
    fun `already-normalized input is idempotent`() {
        val once = PuzzleNameNormalizer.normalize("Jace, the Mind Sculptor")
        val twice = PuzzleNameNormalizer.normalize(once)
        assertEquals(once, twice)
    }

    @Test
    fun `comma and period punctuation is stripped`() {
        assertEquals("jace the mind sculptor", PuzzleNameNormalizer.normalize("Jace, the Mind Sculptor."))
    }

    // ── Regression fixtures added by the 2026-08-06 edge-case audit ──────────────────────────────
    // An earlier Kotlin implementation DELETED non-alphanumeric characters instead of replacing them
    // with a space, and had no æ/œ ligature handling — both would have made any real hyphenated or
    // ligature-containing card name permanently unguessable if ever selected as an answer. This
    // fixture list mirrors `tools/puzzle-generator/stages/normalize.test.mjs` on the JS side (both
    // sides MUST be exercised against the SAME cases — see that file's own KDoc/comment header).

    @Test
    fun `hyphenated name splits into separate words instead of collapsing`() {
        assertEquals(
            "ranger captain of eos",
            PuzzleNameNormalizer.normalize("Ranger-Captain of Eos"),
        )
    }

    @Test
    fun `ae ligature expands before diacritic stripping`() {
        assertEquals("aerathi berserker", PuzzleNameNormalizer.normalize("Ærathi Berserker"))
        assertEquals("aether vial", PuzzleNameNormalizer.normalize("Æther Vial"))
    }

    @Test
    fun `additional hyphenated names split into separate words`() {
        assertEquals("ghost lit redeemer", PuzzleNameNormalizer.normalize("Ghost-Lit Redeemer"))
        assertEquals("blood chin rager", PuzzleNameNormalizer.normalize("Blood-Chin Rager"))
    }

    @Test
    fun `surrounding whitespace is trimmed after front-face extraction`() {
        assertEquals(
            "sword of fire and ice",
            PuzzleNameNormalizer.normalize("  Sword of Fire and Ice  "),
        )
    }
}
