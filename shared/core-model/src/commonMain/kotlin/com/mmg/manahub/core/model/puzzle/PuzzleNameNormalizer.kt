package com.mmg.manahub.core.model.puzzle

/**
 * Normalizes a card name for puzzle answer matching (name-based, not oracle-id-based — see the
 * Daily Puzzle plan: the answer card may never have been cached/searched by this device before, so
 * matching by name via a fresh exact-name fetch is simpler and doesn't depend on cache state).
 *
 * IMPORTANT — mirrored implementation: this normalization is ALSO hand-reproduced in
 * `tools/puzzle-generator/stages/normalize.mjs` (a separate, non-Kotlin part of this project that
 * generates the daily puzzle payload, including [GuessCardPayload.answerNameHash]). Any change to
 * the rules below MUST be mirrored there, or the client and the generator will silently disagree on
 * what counts as a matching guess.
 */
object PuzzleNameNormalizer {

    /**
     * Normalizes [name] for comparison/hashing:
     *  1. For a double-faced/split/adventure card name using the `" // "` separator, keeps only the
     *     front-face name (e.g. "Fire // Ice" -> "Fire").
     *  2. Lowercases.
     *  3. Strips diacritics (e.g. "Séance" -> "seance").
     *  4. Strips punctuation (apostrophes, commas, periods, etc. — e.g. "Urza's Tower" -> "urzas tower").
     *  5. Collapses runs of whitespace to a single space and trims leading/trailing whitespace.
     */
    fun normalize(name: String): String {
        val frontFace = name.substringBefore(" // ")
        val lowercased = frontFace.lowercase()
        val withoutDiacritics = stripDiacritics(lowercased)
        val withoutPunctuation = withoutDiacritics.filter { it.isLetterOrDigit() || it.isWhitespace() }
        return withoutPunctuation.trim().replace(Regex("\\s+"), " ")
    }

    /**
     * Decomposes accented characters to their base letter + combining marks (Unicode NFD-style),
     * then drops the combining marks — pure Kotlin, no `java.text.Normalizer` dependency so this
     * stays usable from `commonMain` (including wasmJs).
     */
    private fun stripDiacritics(input: String): String =
        input.map { char -> DIACRITIC_MAP[char] ?: char }.joinToString("")

    /**
     * Explicit base-letter mapping for the Latin-1 Supplement + Latin Extended-A accented letters
     * that appear in Magic card names (French/German/Spanish/Portuguese accents). Kept as a flat
     * map rather than a Unicode-normalization library call to avoid a platform-specific dependency.
     */
    private val DIACRITIC_MAP: Map<Char, Char> = buildMap {
        val groups = listOf(
            "àáâãäåāă" to 'a',
            "çćč" to 'c',
            "èéêëēĕėęě" to 'e',
            "ìíîïĩīĭ" to 'i',
            "ñń" to 'n',
            "òóôõöøōŏő" to 'o',
            "ùúûüũūŭůűų" to 'u',
            "ýÿ" to 'y',
            "žźż" to 'z',
            "šś" to 's',
        )
        for ((chars, base) in groups) {
            for (c in chars) {
                put(c, base)
                put(c.uppercaseChar(), base)
            }
        }
    }
}
