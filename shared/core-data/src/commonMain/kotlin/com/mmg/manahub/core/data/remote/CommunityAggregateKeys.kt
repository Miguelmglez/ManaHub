package com.mmg.manahub.core.data.remote

/**
 * Client-side mirror of the Worker's `buildCanonicalKey` (Phase 3.2,
 * `cloudflare/manahub-community/src/lib/archidekt.ts`) — the client must derive the SAME
 * canonical key the Worker uses so both sides agree on one cache entry per signature-card
 * set (same-archetype users share a snapshot).
 *
 * Known minor divergence: the Worker sorts with JS's locale-aware `localeCompare`, this sorts
 * with Kotlin's ordinal `String` comparator. For ordinary (mostly-uppercase-starting, ASCII)
 * Magic card names this agrees in practice; a mismatch would at worst cause a cache-key miss
 * (fetch a fresh snapshot) — never invalid data — so it is not treated as a correctness bug.
 */
object CommunityAggregateKeys {

    private val NON_ALNUM = Regex("[^a-z0-9]+")

    fun buildCanonicalKey(signatureCards: List<String>): String =
        signatureCards
            .sorted()
            .joinToString("+") { name -> name.lowercase().replace(NON_ALNUM, "-") }

    fun slugifyCommanderName(name: String): String =
        name.lowercase().trim().replace(NON_ALNUM, "-").trim('-')

    fun commanderCacheKey(commanderName: String): String = "agg:commander:${slugifyCommanderName(commanderName)}"

    fun sixtyCacheKey(format: Int, signatureCards: List<String>): String =
        "agg:$format:${buildCanonicalKey(signatureCards)}"
}
