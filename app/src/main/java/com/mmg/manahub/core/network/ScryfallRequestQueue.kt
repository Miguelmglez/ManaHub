package com.mmg.manahub.core.network

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enforces Scryfall's rate-limit guideline of ≤10 requests/second.
 *
 * All Scryfall API calls must be wrapped with [execute] so that at least
 * 100 ms elapses between consecutive requests. The Mutex serialises
 * concurrent callers; the delay is applied inside the lock so only one
 * coroutine proceeds at a time.
 */
@Singleton
class ScryfallRequestQueue @Inject constructor() {

    private val lastRequestTime = AtomicLong(0L)
    private val minDelayMs = 100L
    private val mutex = Mutex()

    suspend fun <T> execute(block: suspend () -> T): T {
        mutex.withLock {
            val now = System.currentTimeMillis()
            val elapsed = now - lastRequestTime.get()
            if (elapsed < minDelayMs) {
                delay(minDelayMs - elapsed)
            }
            lastRequestTime.set(System.currentTimeMillis())
        }
        return block()
    }
}
