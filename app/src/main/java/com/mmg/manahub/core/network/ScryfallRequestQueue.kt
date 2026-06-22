package com.mmg.manahub.core.network

import com.mmg.manahub.core.data.network.RateLimitConfig
import com.mmg.manahub.core.data.network.RateLimitedQueue
import com.mmg.manahub.core.data.network.RetryDecision
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enforces Scryfall's rate-limit guideline of <=10 requests/second.
 *
 * All Scryfall API calls must be wrapped with [execute] so that at least 100 ms elapses
 * between consecutive requests. Delegates to the KMP-shared [RateLimitedQueue] for
 * throttling, serialisation, and retry logic.
 *
 * ## Retry / back-off
 * When the server responds with HTTP 429 (Too Many Requests) or 503 (Service Unavailable),
 * [execute] retries the call up to 3 times using truncated binary-exponential back-off
 * with +/-25% random jitter and a hard cap of 8 000 ms. If the response includes a
 * `Retry-After` header (integer seconds), that value overrides the computed back-off for
 * that attempt.
 *
 * After exhausting all retries the last [HttpException] is re-thrown so callers can handle
 * it via their existing `Result`/`DataResult` wrappers.
 */
@Singleton
class ScryfallRequestQueue @Inject constructor() {

    private val delegate = RateLimitedQueue(
        config = RateLimitConfig(
            minDelayMs = 100L,
            maxRetries = 3,
            initialBackoffMs = 200L,
            maxBackoffMs = 8_000L,
            jitterFactor = 0.25
        ),
        shouldRetry = { _, e ->
            if (e is HttpException && (e.code() == 429 || e.code() == 503)) {
                val retryAfter = e.response()?.headers()?.get("Retry-After")
                    ?.trim()?.toLongOrNull()?.let { it * 1_000L }
                RetryDecision.Retry(serverRetryAfterMs = retryAfter)
            } else {
                RetryDecision.DoNotRetry
            }
        }
    )

    /**
     * Executes [block] while honouring Scryfall's rate-limit contract.
     * Retries automatically on 429/503 up to 3 times.
     *
     * @throws HttpException if all retries are exhausted or a non-retryable
     *   HTTP error occurs.
     * @throws Throwable for any other exception thrown by [block].
     */
    suspend fun <T> execute(block: suspend () -> T): T = delegate.execute(block)
}
