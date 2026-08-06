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
     * Normalizes [name] for comparison/hashing. MUST stay byte-identical to the JS side's algorithm
     * (`normalize.mjs`'s `normalizeCardName`):
     *  1. Trims, then for a double-faced/split/adventure card name using the `" // "` separator,
     *     keeps only the front-face name (e.g. "Fire // Ice" -> "Fire").
     *  2. Lowercases.
     *  3. Expands the `æ`/`œ` ligatures to `"ae"`/`"oe"` — Unicode NFD does NOT decompose these into
     *     base+combining-mark pairs (unlike accented letters), so without this step a real name like
     *     "Ærathi Berserker" would normalize to a value the generator's hash never matches.
     *  4. Strips diacritics (e.g. "Séance" -> "seance").
     *  5. Strips quote-like punctuation (apostrophes, commas, periods) with NO replacement space
     *     (e.g. "Urza's Tower" -> "urzas tower").
     *  6. Replaces every remaining run of non-alphanumeric characters (hyphens, whitespace, any other
     *     punctuation) with a SINGLE space, then trims. This is a REPLACE, not a delete: a hyphenated
     *     name like "Ranger-Captain of Eos" must split into separate words ("ranger captain of eos"),
     *     never collapse into "rangercaptain of eos".
     */
    fun normalize(name: String): String {
        if (name.isEmpty()) return ""
        val trimmed = name.trim()
        val frontFace = trimmed.substringBefore(" // ")
        val lowercased = frontFace.lowercase()
        val withLigaturesExpanded = expandLigatures(lowercased)
        val withoutDiacritics = stripDiacritics(withLigaturesExpanded)
        val withoutQuoteLikeChars = withoutDiacritics.filterNot { it in QUOTE_LIKE_CHARS }
        return withoutQuoteLikeChars.replace(NON_ALPHANUMERIC_RUN, " ").trim()
    }

    /**
     * Expands the two Latin ligatures that appear in Magic card names but have no Unicode NFD
     * decomposition (mirrors the JS side's `LIGATURES` map). Must run AFTER lowercasing — only the
     * lowercase forms need an entry since [normalize] lowercases before calling this.
     */
    private fun expandLigatures(input: String): String = buildString {
        for (char in input) {
            append(LIGATURE_MAP[char] ?: char.toString())
        }
    }

    /** Lowercase-only ligature expansions (mirrors JS `LIGATURES`). */
    private val LIGATURE_MAP: Map<Char, String> = mapOf('æ' to "ae", 'œ' to "oe")

    /**
     * Apostrophes (straight and curly), commas, and periods — stripped with no replacement space
     * (mirrors JS `QUOTE_LIKE_PATTERN`), so "Urza's Tower" becomes "urzas tower", not "urza s tower".
     */
    private val QUOTE_LIKE_CHARS: Set<Char> = setOf('\'', '’', ',', '.')

    /** Any run of non-`[a-z0-9]` characters — collapsed to a single space (mirrors JS `/[^a-z0-9]+/g`). */
    private val NON_ALPHANUMERIC_RUN: Regex = Regex("[^a-z0-9]+")

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
