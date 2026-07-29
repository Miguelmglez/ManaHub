package com.mmg.manahub.core.data.network

import com.mmg.manahub.core.common.CrashReporter
import io.ktor.client.plugins.ResponseException

/**
 * Enforces Scryfall's rate-limit guideline of <=10 requests/second.
 *
 * All Scryfall API calls must be wrapped with [execute] so that at least 100 ms elapses
 * between consecutive requests. Delegates to the KMP-shared [RateLimitedQueue] for
 * throttling, serialisation, bounded concurrency, shared cooldown, and retry logic.
 *
 * ## Concurrency
 * At most [RateLimitConfig.maxConcurrent] (2) requests are in flight at once. Two was chosen as a
 * conservative default: it leaves real headroom under the 10 req/s guideline (so normal usage never
 * feels serialised end-to-end) while keeping the worst case low if several screens fire concurrent
 * Scryfall calls at once (e.g. a card search plus a spotlight-feed page load).
 *
 * ## Retry / back-off / shared cooldown
 * When the server responds with HTTP 429 (Too Many Requests) or 503 (Service Unavailable),
 * [execute] retries the call up to 3 times using truncated binary-exponential back-off
 * with +/-25% random jitter and a hard cap of 8 000 ms for this caller's OWN retry pacing. Every
 * 429/503 ALSO widens a cooldown shared by every caller of this queue (escalating 1s -> 2s -> 5s ->
 * 15s on consecutive hits, decaying back after 30s of clean successes, capped at 30s) -- see
 * [RateLimitedQueue] class docs for why this is the fix that makes the app recoverable from a 429
 * instead of digging itself deeper. If the response includes a `Retry-After` header (integer
 * seconds), that value feeds both the per-call back-off (capped at 8 000 ms) and the shared cooldown
 * (capped at 30 000 ms).
 *
 * After exhausting all retries, [RateLimitExhaustedException] is thrown (wrapping the last
 * [ResponseException]) instead of the raw exception, so callers can distinguish rate-limit
 * exhaustion from a generic network failure via their existing `Result`/`DataResult` wrappers.
 *
 * @param crashReporter Optional telemetry sink for cooldown-entered/exited breadcrumbs and a
 *   `scryfall_cooldown_ms` custom key (WS7). `null` (e.g. in tests) makes telemetry a silent no-op.
 */
class ScryfallRequestQueue(
    crashReporter: CrashReporter? = null,
) {

    private val delegate = RateLimitedQueue(
        config = RateLimitConfig(
            minDelayMs = 100L,
            maxRetries = 3,
            initialBackoffMs = 200L,
            maxBackoffMs = 8_000L,
            jitterFactor = 0.25,
            maxConcurrent = 2,
            cooldownEscalationStepsMs = listOf(1_000L, 2_000L, 5_000L, 15_000L),
            cooldownDecayWindowMs = 30_000L,
            maxCooldownMs = 30_000L,
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
        },
        queueName = "scryfall",
        crashReporter = crashReporter,
    )

    /**
     * Executes [block] while honouring Scryfall's rate-limit contract.
     * Retries automatically on 429/503 up to 3 times; every caller waits out the shared cooldown.
     *
     * @throws RateLimitExhaustedException if retries are exhausted on a 429/503.
     * @throws ResponseException if a non-retryable HTTP error occurs.
     * @throws Throwable for any other exception thrown by [block].
     */
    suspend fun <T> execute(block: suspend () -> T): T = delegate.execute(block)
}
