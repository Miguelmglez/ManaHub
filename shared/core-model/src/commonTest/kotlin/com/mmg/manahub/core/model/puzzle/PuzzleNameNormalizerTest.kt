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
}
