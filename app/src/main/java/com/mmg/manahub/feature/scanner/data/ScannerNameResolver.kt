package com.mmg.manahub.feature.scanner.data

/**
 * Scanner-local negative cache (scanner-reliability-plan.md, W2.6, 2026-08-24): remembers
 * normalized OCR name keys that have already failed a Scryfall lookup this session, so a
 * repeated garbage OCR string (the same misread held in frame for tens of seconds) short-circuits
 * with ZERO network calls instead of re-hitting Scryfall every time [CardRecognizer]'s
 * pre-resolution stability gate is satisfied again.
 *
 * Deliberately scanner-local and NOT folded into the shared
 * [com.mmg.manahub.core.data.network.ScryfallCache] (used by every feature in the app) -- see
 * finding F7 in the plan: a shared negative cache would change failure-caching behaviour for
 * every other caller (Add Card, Deck Doctor, Community Decks, …), which nobody asked for. This
 * cache's blast radius is a single [CardRecognizer] instance (one scanner screen session).
 *
 * Bounded by [maxSize] (simple LRU eviction via [LinkedHashMap]'s access-order mode) and
 * time-limited by [ttlMs] so a name that genuinely gets fixed later (Scryfall's catalog changes,
 * or the user re-frames the card and OCR reads it correctly) is retried after the TTL rather than
 * being negative-cached forever.
 *
 * Not KMP-shared: `feature/scanner` is explicitly EXCLUDED from the KMP migration
 * (Hilt + Android Compose only) — see the "Kotlin Multiplatform migration" section of the root
 * CLAUDE.md and `docs/plans/scanner-reliability-plan.md`.
 */
class ScannerNameResolver(
    private val maxSize: Int = MAX_SIZE,
    private val ttlMs: Long = TTL_MS,
) {
    /** `accessOrder = true` gives LRU iteration order for the manual eviction in [recordFailure]. */
    private val failures = LinkedHashMap<String, Long>(16, 0.75f, true)

    private fun now(): Long = System.currentTimeMillis()

    /**
     * True when [normalizedKey] failed a lookup within the last [ttlMs]. Lazily evicts an expired
     * entry on read so [size] never over-reports live entries for long.
     */
    @Synchronized
    fun isNegative(normalizedKey: String): Boolean {
        val recordedAt = failures[normalizedKey] ?: return false
        if (now() - recordedAt > ttlMs) {
            failures.remove(normalizedKey)
            return false
        }
        return true
    }

    /** Records [normalizedKey] as a failed lookup, evicting the least-recently-touched entry if over [maxSize]. */
    @Synchronized
    fun recordFailure(normalizedKey: String) {
        failures[normalizedKey] = now()
        if (failures.size > maxSize) {
            val eldest = failures.keys.iterator()
            if (eldest.hasNext()) {
                eldest.next()
                eldest.remove()
            }
        }
    }

    /** Clears every recorded failure. Call on scanner exit and on language change. */
    @Synchronized
    fun clear() {
        failures.clear()
    }

    /** Current number of (potentially expired) recorded failures — test/debug visibility only. */
    @Synchronized
    fun size(): Int = failures.size

    companion object {
        /** How long a negative result is trusted before the name is eligible for retry. */
        const val TTL_MS: Long = 10 * 60 * 1_000L

        /** Maximum distinct failed names remembered at once (LRU-evicted beyond this). */
        const val MAX_SIZE: Int = 200
    }
}
