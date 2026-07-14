package com.mmg.manahub.core.data.network

import io.ktor.client.plugins.ResponseException

/**
 * Serialises and throttles requests to the Archidekt API.
 *
 * Archidekt does not publish a documented rate limit, so this queue is deliberately
 * conservative: at least 200 ms elapses between consecutive requests (approx 5 req/s).
 * Delegates to the KMP-shared [RateLimitedQueue] for throttling, serialisation, and retry logic.
 *
 * ## Retry / back-off
 * When the server responds with HTTP 429 (Too Many Requests) or 503 (Service Unavailable),
 * [execute] retries the call up to 2 times using truncated binary-exponential back-off
 * with +/-25% random jitter and a hard cap of 8 000 ms. If the response includes a
 * `Retry-After` header (integer seconds), that value overrides the computed back-off for
 * that attempt.
 *
 * After exhausting all retries the last [ResponseException] is re-thrown so callers can
 * handle it via their existing `Result`/`DataResult` wrappers.
 */
class ArchidektRequestQueue {

    private val delegate = RateLimitedQueue(
        config = RateLimitConfig(
            minDelayMs = 200L,
            maxRetries = 2,
            initialBackoffMs = 500L,
            maxBackoffMs = 8_000L,
            jitterFactor = 0.25
        ),
        shouldRetry = { _, e ->
            if (e is ResponseException) {
                val code = e.response.status.value
                if (code == 429 || code == 503) {
                    val retryAfter = e.response.headers["Retry-After"]
                        ?.trim()?.toLongOrNull()?.let { it * 1_000L }
                    RetryDecision.Retry(serverRetryAfterMs = retryAfter)
                } else {
                    RetryDecision.DoNotRetry
                }
            } else {
                RetryDecision.DoNotRetry
            }
        }
    )

    /**
     * Executes [block] while honouring Archidekt's conservative rate-limit contract.
     * Retries automatically on 429/503 up to 2 times.
     *
     * @throws ResponseException if all retries are exhausted or a non-retryable HTTP error occurs.
     * @throws Throwable for any other exception thrown by [block].
     */
    suspend fun <T> execute(block: suspend () -> T): T = delegate.execute(block)
}
