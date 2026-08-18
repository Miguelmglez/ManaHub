package com.mmg.manahub.core.data.network

import com.mmg.manahub.core.common.CrashReporter
import io.ktor.client.plugins.ResponseException

/**
 * Serialises and throttles requests to the `manahub-competitive` Cloudflare Worker.
 *
 * This Worker is our own infrastructure (no documented third-party rate limit to respect), but
 * is still fronted by the same [RateLimitedQueue] every other external client in this codebase
 * uses — a conservative default (mirroring [ArchidektRequestQueue]'s numbers, since Community's
 * `manahub-community` Worker is the closest precedent for "our own Cloudflare Worker, not a raw
 * third-party API") protects the Worker's KV/D1 read budget and gives this codebase one
 * consistent retry/back-off/shared-cooldown story for every network client, rather than a
 * bespoke one-off for this feature.
 *
 * ## Concurrency
 * At most [RateLimitConfig.maxConcurrent] (2) requests are in flight at once.
 *
 * ## Retry / back-off / shared cooldown
 * On HTTP 429/503, [execute] retries up to 2 times with truncated binary-exponential back-off
 * (+/-25% jitter, capped at 8 000 ms), and widens a cooldown shared by every caller of this
 * queue (escalation ladder + decay, see [RateLimitedQueue] class docs). A `Retry-After` header
 * (integer seconds) feeds both the per-call back-off and the shared cooldown.
 *
 * After exhausting all retries, [RateLimitExhaustedException] is thrown (wrapping the last
 * [ResponseException]) instead of the raw exception, so callers can handle it via their existing
 * `DataResult` wrappers.
 *
 * @param crashReporter Optional telemetry sink for cooldown-entered/exited breadcrumbs and a
 *   `competitive_cooldown_ms` custom key. `null` (e.g. in tests) makes telemetry a silent no-op.
 */
class CompetitiveRequestQueue(
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
        queueName = "competitive",
        crashReporter = crashReporter,
    )

    /**
     * Executes [block] while honouring this queue's rate-limit contract. Retries automatically
     * on 429/503 up to 2 times; every caller waits out the shared cooldown.
     *
     * @throws RateLimitExhaustedException if retries are exhausted on a 429/503.
     * @throws ResponseException if a non-retryable HTTP error occurs.
     * @throws Throwable for any other exception thrown by [block].
     */
    suspend fun <T> execute(block: suspend () -> T): T = delegate.execute(block)
}
