package com.mmg.manahub.feature.home.presentation

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Result of one network one-shot: the value to cache and whether the fetch actually succeeded.
 * A failed fetch still caches its fallback value (so the widget resolves) but expires sooner.
 */
data class Fetched<T>(val value: T, val succeeded: Boolean)

/**
 * A ViewModel-owned, single-slot cache for a keyed network one-shot (Archidekt search, Worker
 * trending, trade-suggestion RPC, ...).
 *
 * It outlives `SharingStarted.WhileSubscribed` restarts of the flows that read it, so returning to
 * the screen re-reads the cached value instead of refetching; [ensureLoaded] only hits the network
 * when the slot holds a different key or the entry is older than its max age. Concurrent callers
 * are serialized, so a burst of resubscriptions issues at most one request.
 *
 * @param nowMs wall-clock source, injectable for deterministic tests.
 */
class OneShotCache<K : Any, T>(private val nowMs: () -> Long) {

    /** A cached value for [key], fetched at [fetchedAtMs]. */
    data class Entry<K, T>(
        val key: K,
        val value: T,
        val fetchedAtMs: Long,
        val succeeded: Boolean,
    )

    private val slot = MutableStateFlow<Entry<K, T>?>(null)
    private val mutex = Mutex()

    /** Emits the cached entry for [key], or null while nothing has been fetched for that key yet. */
    fun entryFor(key: K): Flow<Entry<K, T>?> =
        slot.map { entry -> entry?.takeIf { it.key == key } }.distinctUntilChanged()

    /**
     * Fetches [key] unless a fresh entry for it is already cached. A successful entry stays fresh
     * for [maxAgeMs]; a failed one for [failureMaxAgeMs]. [fetch] must rethrow cancellation, which
     * leaves the slot untouched.
     */
    suspend fun ensureLoaded(
        key: K,
        maxAgeMs: Long,
        failureMaxAgeMs: Long,
        fetch: suspend () -> Fetched<T>,
    ) {
        mutex.withLock {
            val current = slot.value
            if (current != null && current.key == key) {
                val limit = if (current.succeeded) maxAgeMs else failureMaxAgeMs
                if (nowMs() - current.fetchedAtMs < limit) return
            }
            val fetched = fetch()
            slot.value = Entry(key, fetched.value, nowMs(), fetched.succeeded)
        }
    }
}
