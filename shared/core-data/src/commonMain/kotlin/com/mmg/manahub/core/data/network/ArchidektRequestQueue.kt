package com.mmg.manahub.core.data.network

import com.mmg.manahub.core.common.CrashReporter
import io.ktor.client.plugins.ResponseException

/**
 * Serialises and throttles requests to the Archidekt API.
 *
 * Archidekt does not publish a documented rate limit, so this queue is deliberately
 * conservative: at least 200 ms elapses between consecutive requests (approx 5 req/s).
 * Delegates to the KMP-shared [RateLimitedQueue] for throttling, serialisation, bounded
 * concurrency, shared cooldown, and retry logic.
 *
 * ## Concurrency
 * At most [RateLimitConfig.maxConcurrent] (2) requests are in flight at once, same default and same
 * rationale as [ScryfallRequestQueue] -- Archidekt's undocumented limit is reason to stay at least as
 * conservative, not looser.
 *
 * ## Retry / back-off / shared cooldown
 * When the server responds with HTTP 429 (Too Many Requests) or 503 (Service Unavailable),
 * [execute] retries the call up to 2 times using truncated binary-exponential back-off
 * with +/-25% random jitter and a hard cap of 8 000 ms for this caller's OWN retry pacing. Every
 * 429/503 ALSO widens a cooldown shared by every caller of this queue (same escalation ladder and
 * caps as [ScryfallRequestQueue] -- see [RateLimitedQueue] class docs). If the response includes a
 * `Retry-After` header (integer seconds), that value feeds both the per-call back-off (capped at
 * 8 000 ms) and the shared cooldown (capped at 30 000 ms).
 *
 * After exhausting all retries, [RateLimitExhaustedException] is thrown (wrapping the last
 * [ResponseException]) instead of the raw exception, so callers can handle it via their existing
 * `Result`/`DataResult` wrappers.
 *
 * @param crashReporter Optional telemetry sink for cooldown-entered/exited breadcrumbs and an
 *   `archidekt_cooldown_ms` custom key (WS7). `null` (e.g. in tests) makes telemetry a silent no-op.
 */
class ArchidektRequestQueue(
    crashReporter: CrashReporter? = null,
) {

    private val delegate = RateLimitedQueue(
        config = RateLimitConfig(
            minDelayMs = 200L,
            maxRetries = 2,
            initialBackoffMs = 500L,
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
        queueName = "archidekt",
        crashReporter = crashReporter,
    )

    /**
     * Executes [block] while honouring Archidekt's conservative rate-limit contract.
     * Retries automatically on 429/503 up to 2 times; every caller waits out the shared cooldown.
     *
     * @throws RateLimitExhaustedException if retries are exhausted on a 429/503.
     * @throws ResponseException if a non-retryable HTTP error occurs.
     * @throws Throwable for any other exception thrown by [block].
     */
    suspend fun <T> execute(block: suspend () -> T): T = delegate.execute(block)
}
