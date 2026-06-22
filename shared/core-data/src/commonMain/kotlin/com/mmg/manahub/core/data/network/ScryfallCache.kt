package com.mmg.manahub.core.data.network

import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.MagicSet
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Thread-safe in-memory LRU cache with per-entry TTL expiration and
 * in-flight request deduplication.
 *
 * When multiple coroutines request the same key concurrently via [getOrFetch]
 * and it's not cached, only one executes the loader -- the others suspend and
 * receive the same result, avoiding redundant network calls.
 *
 * The LRU eviction is implemented with a [HashMap] + [MutableList] tracking
 * access order (KMP-safe; replaces the JVM-only `LinkedHashMap(accessOrder=true)`
 * with `removeEldestEntry` override).
 *
 * @param maxSize Maximum entries before the least-recently-used entry is evicted.
 * @param ttlMs   Time-to-live in milliseconds. Expired entries are evicted on read.
 */
@OptIn(ExperimentalTime::class)
class TimedLruCache<K : Any, V : Any>(
    private val maxSize: Int,
    private val ttlMs: Long,
) {
    private data class Entry<V>(val value: V, val timestamp: Long)

    private val map = HashMap<K, Entry<V>>()
    private val accessOrder = mutableListOf<K>()
    private val mutex = Mutex()
    private val inFlight = mutableMapOf<K, CompletableDeferred<V>>()

    /** Returns the current wall-clock epoch millis (KMP-safe). */
    private fun now(): Long = Clock.System.now().toEpochMilliseconds()

    /**
     * Moves [key] to the end of [accessOrder] (most-recently-used position).
     * If the key is not in the list, it is appended.
     */
    private fun touchKey(key: K) {
        accessOrder.remove(key)
        accessOrder.add(key)
    }

    /** Evicts the least-recently-used entry if size exceeds [maxSize]. */
    private fun evictIfNeeded() {
        while (accessOrder.size > maxSize) {
            val eldest = accessOrder.removeFirst()
            map.remove(eldest)
        }
    }

    /** Returns the cached value if present and not expired, or null. */
    suspend fun get(key: K): V? = mutex.withLock {
        val entry = map[key] ?: return@withLock null
        if (now() - entry.timestamp > ttlMs) {
            map.remove(key)
            accessOrder.remove(key)
            null
        } else {
            touchKey(key)
            entry.value
        }
    }

    /** Stores a value in the cache. */
    suspend fun put(key: K, value: V): Unit = mutex.withLock {
        map[key] = Entry(value, now())
        touchKey(key)
        evictIfNeeded()
    }

    /**
     * Returns the cached value if available and fresh. Otherwise, ensures only
     * one coroutine executes [loader] for the given key (in-flight dedup),
     * caches the result, and returns it.
     */
    suspend fun getOrFetch(key: K, loader: suspend () -> V): V {
        // Fast path: cache hit
        get(key)?.let { return it }

        // Slow path: coordinate concurrent callers
        val (deferred, isOwner) = mutex.withLock {
            // Double-check inside lock
            val entry = map[key]
            if (entry != null && now() - entry.timestamp <= ttlMs) {
                touchKey(key)
                return entry.value
            }
            val existing = inFlight[key]
            if (existing != null) {
                existing to false
            } else {
                val new = CompletableDeferred<V>()
                inFlight[key] = new
                new to true
            }
        }

        if (!isOwner) return deferred.await()

        return try {
            val value = loader()
            put(key, value)
            deferred.complete(value)
            value
        } catch (e: Throwable) {
            deferred.completeExceptionally(e)
            throw e
        } finally {
            mutex.withLock { inFlight.remove(key) }
        }
    }

    /** Removes a specific entry. */
    suspend fun invalidate(key: K): Unit = mutex.withLock {
        map.remove(key)
        accessOrder.remove(key)
    }

    /** Clears all entries. */
    suspend fun clear(): Unit = mutex.withLock {
        map.clear()
        accessOrder.clear()
    }

    /** Returns the current number of (potentially expired) entries. */
    suspend fun size(): Int = mutex.withLock { map.size }
}

/**
 * Application-level in-memory cache for Scryfall API responses.
 *
 * Sits between [ScryfallRemoteDataSource] and the network to eliminate
 * redundant API calls. Each resource type has its own TTL and capacity.
 *
 * | Resource      | TTL   | Max entries | Rationale                          |
 * |---------------|-------|-------------|------------------------------------|
 * | Cards (by ID) | 24 h  | 500         | Prices update daily                |
 * | Card names    | 24 h  | 200         | Same card, same day                |
 * | Searches      | 5 min | 50          | Users expect near-fresh results    |
 * | Sets          | 24 h  | 1           | New sets are released infrequently |
 * | Art variants  | 24 h  | 50          | Art doesn't change                 |
 */
class ScryfallCache {

    /** Individual card lookups keyed by Scryfall ID. */
    val cards = TimedLruCache<String, Card>(MAX_CARDS, TTL_CARDS_MS)

    /** Card-by-name lookups keyed by "fuzzy:$name:$set" or "exact:$name". */
    val cardNames = TimedLruCache<String, Card>(MAX_CARD_NAMES, TTL_CARDS_MS)

    /** Search result lists keyed by "$query:$page". */
    val searches = TimedLruCache<String, List<Card>>(MAX_SEARCHES, TTL_SEARCHES_MS)

    /** The full set list (single entry, key = "all"). */
    val sets = TimedLruCache<String, List<MagicSet>>(1, TTL_SETS_MS)

    /** Art variant lists keyed by card name. */
    val artVariants = TimedLruCache<String, List<Card>>(MAX_ART_VARIANTS, TTL_CARDS_MS)

    /** Clears every cache. Useful for manual refresh or testing. */
    suspend fun clearAll() {
        cards.clear()
        cardNames.clear()
        searches.clear()
        sets.clear()
        artVariants.clear()
    }

    companion object {
        const val TTL_CARDS_MS    = 24L * 60 * 60 * 1_000   // 24 hours
        const val TTL_SEARCHES_MS = 5L * 60 * 1_000          // 5 minutes
        const val TTL_SETS_MS     = 24L * 60 * 60 * 1_000   // 24 hours

        private const val MAX_CARDS        = 500
        private const val MAX_CARD_NAMES   = 200
        private const val MAX_SEARCHES     = 50
        private const val MAX_ART_VARIANTS = 50
    }
}
