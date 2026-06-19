package com.mmg.manahub.core.network

import com.mmg.manahub.core.network.ScryfallRequestQueue.Companion.JITTER_FACTOR
import com.mmg.manahub.core.network.ScryfallRequestQueue.Companion.MAX_BACKOFF_MS
import com.mmg.manahub.core.network.ScryfallRequestQueue.Companion.MAX_RETRIES
import com.mmg.manahub.core.network.ScryfallRequestQueue.Companion.MIN_DELAY_MS
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlin.random.Random

/**
 * Enforces Scryfall's rate-limit guideline of ≤10 requests/second.
 *
 * All Scryfall API calls must be wrapped with [execute] so that at least
 * [MIN_DELAY_MS] elapses between consecutive requests. The [Mutex] serialises
 * concurrent callers; the inter-request delay is applied inside the lock so
 * only one coroutine proceeds at a time.
 *
 * ## Retry / back-off
 * When the server responds with HTTP 429 (Too Many Requests) or 503
 * (Service Unavailable), [execute] retries the call up to [MAX_RETRIES] times
 * using truncated binary-exponential back-off with ±25 % random jitter and a
 * hard cap of [MAX_BACKOFF_MS]. If the response includes a `Retry-After`
 * header (integer seconds), that value overrides the computed back-off for
 * that attempt.
 *
 * After exhausting all retries the last [HttpException] is re-thrown so
 * callers can handle it via their existing `Result`/`DataResult` wrappers.
 */
@Singleton
class ScryfallRequestQueue @Inject constructor() {

    private val lastRequestTime = AtomicLong(0L)
    private val mutex = Mutex()

    /**
     * Executes [block] while honouring Scryfall's rate-limit contract.
     * Retries automatically on 429/503 up to [MAX_RETRIES] times.
     *
     * @throws HttpException if all retries are exhausted or a non-retryable
     *   HTTP error occurs.
     * @throws Throwable for any other exception thrown by [block].
     */
    suspend fun <T> execute(block: suspend () -> T): T {
        var attempt = 0
        while (true) {
            // Honour the minimum inter-request spacing inside the mutex so that
            // concurrent callers are serialised at the network level.
            mutex.withLock {
                val now = System.currentTimeMillis()
                val elapsed = now - lastRequestTime.get()
                if (elapsed < MIN_DELAY_MS) {
                    delay(MIN_DELAY_MS - elapsed)
                }
                lastRequestTime.set(System.currentTimeMillis())
            }

            try {
                return block()
            } catch (e: HttpException) {
                val code = e.code()
                if ((code == 429 || code == 503) && attempt < MAX_RETRIES) {
                    val backoffMs = computeBackoffMs(attempt, e)
                    attempt++
                    delay(backoffMs)
                    // continue → next iteration retries the call
                } else {
                    throw e
                }
            }
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Computes the delay in milliseconds before the next retry attempt.
     *
     * Priority:
     * 1. `Retry-After` header value (integer seconds), if present and positive.
     * 2. Truncated binary-exponential back-off with ±25 % jitter, capped at
     *    [MAX_BACKOFF_MS].
     */
    private fun computeBackoffMs(attempt: Int, exception: HttpException): Long {
        val retryAfterHeader = exception.response()
            ?.headers()
            ?.get("Retry-After")
            ?.trim()
            ?.toLongOrNull()

        if (retryAfterHeader != null && retryAfterHeader > 0) {
            // Retry-After is in seconds; convert and clamp to our maximum.
            return min(retryAfterHeader * 1_000L, MAX_BACKOFF_MS)
        }

        // Binary-exponential: 200 ms, 400 ms, 800 ms, … capped at MAX_BACKOFF_MS.
        val base = INITIAL_BACKOFF_MS shl attempt          // 200 * 2^attempt
        val capped = min(base, MAX_BACKOFF_MS)
        val jitter = (capped * JITTER_FACTOR * (Random.nextDouble() * 2.0 - 1.0)).toLong()
        return (capped + jitter).coerceAtLeast(INITIAL_BACKOFF_MS)
    }

    companion object {
        /** Minimum gap between consecutive Scryfall requests (≤10 req/s). */
        private const val MIN_DELAY_MS = 100L

        /** Maximum number of retry attempts on 429/503. */
        private const val MAX_RETRIES = 3

        /** Starting back-off duration for the first retry (ms). */
        private const val INITIAL_BACKOFF_MS = 200L

        /** Hard cap for any computed back-off (ms). */
        private const val MAX_BACKOFF_MS = 8_000L

        /** Jitter factor applied as ±[JITTER_FACTOR] of the capped back-off. */
        private const val JITTER_FACTOR = 0.25
    }
}
