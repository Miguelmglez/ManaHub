package com.mmg.manahub.core.data.remote

/**
 * Cache-key derivation for Commander Spellbook combo lookups (Deck Engine Unification plan D7).
 *
 * Unlike [CommunityAggregateKeys.buildCanonicalKey] (which keys off 2-3 short "signature cards"
 * and can afford a plain sorted-joined string as the Room primary key), a combo lookup can carry
 * up to 600 card names -- a literal sorted-joined string would make an unreasonably long PK. This
 * hashes the canonical (lowercased, sorted, `|`-joined) name set with FNV-1a 64-bit, the same
 * deterministic-hash technique already established in this codebase for
 * [com.mmg.manahub.core.gamification.engine] quest selection (never `String.hashCode()` -- not
 * guaranteed stable/collision-resistant enough, and this must hash identically on every KMP
 * target including wasmJs).
 */
object ComboCacheKeys {

    fun cacheKey(cardNames: List<String>, commanderNames: List<String>): String {
        val canonical = (commanderNames.map { "C:${it.lowercase().trim()}" } + cardNames.map { it.lowercase().trim() })
            .filter { it.isNotBlank() }
            .sorted()
            .joinToString("|")
        return "combo:${fnv1a64Hex(canonical)}"
    }

    /** FNV-1a 64-bit, rendered as a fixed-width lowercase hex string. */
    private fun fnv1a64Hex(input: String): String {
        var hash = -3750763034362895579L // FNV offset basis (0xcbf29ce484222325)
        val prime = 1099511628211L // FNV prime
        for (ch in input) {
            hash = hash xor ch.code.toLong()
            hash *= prime
        }
        return hash.toULong().toString(16).padStart(16, '0')
    }
}
