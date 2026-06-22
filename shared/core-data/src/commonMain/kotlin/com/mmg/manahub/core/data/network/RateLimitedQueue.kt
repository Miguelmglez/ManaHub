package com.mmg.manahub.core.data.network

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.min
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Configuration for a [RateLimitedQueue].
 *
 * @property minDelayMs   Minimum gap between consecutive requests (ms).
 * @property maxRetries   Maximum number of retry attempts on retryable failures.
 * @property initialBackoffMs Starting back-off duration for the first retry (ms).
 * @property maxBackoffMs Hard cap for any computed back-off (ms).
 * @property jitterFactor Jitter applied as +/-[jitterFactor] of the capped back-off.
 */
data class RateLimitConfig(
    val minDelayMs: Long = 100L,
    val maxRetries: Int = 3,
    val initialBackoffMs: Long = 200L,
    val maxBackoffMs: Long = 8_000L,
    val jitterFactor: Double = 0.25
)

/**
 * Result of examining a thrown exception to decide whether to retry.
 *
 * Platform-specific callers inspect the exception (e.g. Retrofit [HttpException] on Android,
 * Ktor exceptions on web) and return either [Retry] (with an optional server-provided
 * back-off hint) or [DoNotRetry].
 */
sealed class RetryDecision {
    /** Indicates the request should be retried, optionally after [serverRetryAfterMs]. */
    data class Retry(val serverRetryAfterMs: Long? = null) : RetryDecision()

    /** Indicates the request should NOT be retried; the exception will be re-thrown. */
    data object DoNotRetry : RetryDecision()
}

/**
 * A KMP-safe rate-limited request queue with configurable retry and exponential back-off.
 *
 * Serialises concurrent callers via a [Mutex], enforcing a minimum inter-request delay of
 * [RateLimitConfig.minDelayMs]. On retryable failures (determined by the [shouldRetry] lambda),
 * applies truncated binary-exponential back-off with jitter, honouring server-provided
 * `Retry-After` hints when available.
 *
 * This class lives in `commonMain` (no platform deps). The platform-specific retry strategy
 * is injected via [shouldRetry] — e.g. on Android it inspects `retrofit2.HttpException`,
 * on web it inspects Ktor exceptions.
 *
 * Usage:
 * ```kotlin
 * val queue = RateLimitedQueue(
 *     config = RateLimitConfig(minDelayMs = 100L, maxRetries = 3),
 *     shouldRetry = { attempt, e -> /* platform retry logic */ }
 * )
 * val result = queue.execute { apiService.fetchData() }
 * ```
 *
 * @param config       Tuning constants for throttling and retry behaviour.
 * @param shouldRetry  Lambda inspecting the attempt index and thrown exception to decide
 *                     whether to retry. Defaults to never retrying.
 */
@OptIn(ExperimentalTime::class)
class RateLimitedQueue(
    private val config: RateLimitConfig = RateLimitConfig(),
    private val shouldRetry: (attempt: Int, exception: Throwable) -> RetryDecision =
        { _, _ -> RetryDecision.DoNotRetry }
) {
    private val mutex = Mutex()

    /**
     * Timestamp (epoch ms) of the last request dispatched. Accessed only inside [mutex],
     * so a plain `var` is safe (no atomic needed).
     */
    private var lastRequestTime: Long = 0L

    /**
     * Executes [block] while honouring the configured rate-limit contract.
     *
     * Retries automatically on retryable failures (as determined by [shouldRetry]) up to
     * [RateLimitConfig.maxRetries] times with exponential back-off.
     *
     * @throws Throwable the last exception if all retries are exhausted or a non-retryable
     *   exception occurs.
     */
    suspend fun <T> execute(block: suspend () -> T): T {
        var attempt = 0
        while (true) {
            // Honour the minimum inter-request spacing inside the mutex so that
            // concurrent callers are serialised at the network level.
            mutex.withLock {
                val now = Clock.System.now().toEpochMilliseconds()
                val elapsed = now - lastRequestTime
                if (elapsed < config.minDelayMs) {
                    delay(config.minDelayMs - elapsed)
                }
                lastRequestTime = Clock.System.now().toEpochMilliseconds()
            }

            try {
                return block()
            } catch (e: Throwable) {
                if (attempt < config.maxRetries) {
                    when (val decision = shouldRetry(attempt, e)) {
                        is RetryDecision.Retry -> {
                            val backoffMs = computeBackoffMs(attempt, decision.serverRetryAfterMs)
                            attempt++
                            delay(backoffMs)
                            // continue → next iteration retries the call
                        }
                        RetryDecision.DoNotRetry -> throw e
                    }
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
     * 1. Server-provided retry-after hint (if present and positive), clamped to [RateLimitConfig.maxBackoffMs].
     * 2. Truncated binary-exponential back-off with +/-[RateLimitConfig.jitterFactor] jitter,
     *    capped at [RateLimitConfig.maxBackoffMs].
     */
    private fun computeBackoffMs(attempt: Int, serverRetryAfterMs: Long?): Long {
        if (serverRetryAfterMs != null && serverRetryAfterMs > 0) {
            return min(serverRetryAfterMs, config.maxBackoffMs)
        }

        // Binary-exponential: initialBackoffMs * 2^attempt, capped at maxBackoffMs.
        val base = config.initialBackoffMs shl attempt
        val capped = min(base, config.maxBackoffMs)
        val jitter = (capped * config.jitterFactor * (Random.nextDouble() * 2.0 - 1.0)).toLong()
        return (capped + jitter).coerceAtLeast(config.initialBackoffMs)
    }
}
