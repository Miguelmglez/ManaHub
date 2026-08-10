package com.mmg.manahub.core.data.network

import com.mmg.manahub.core.common.CrashReporter
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
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
 * @property maxBackoffMs Hard cap for any PER-CALL computed back-off (ms) — bounds how long a
 *   single caller sleeps between its own retry attempts. Independent of [maxCooldownMs], which
 *   bounds the SHARED cooldown every caller (including this one, on its next loop iteration)
 *   honours -- see [RateLimitedQueue] class docs.
 * @property jitterFactor Jitter applied as +/-[jitterFactor] of the capped back-off.
 * @property maxConcurrent Hard cap on concurrent in-flight [RateLimitedQueue.execute] [block]
 *   executions (a [kotlinx.coroutines.sync.Semaphore] permit count). Spacing ([minDelayMs]) alone
 *   only paces *dispatch*; without this, a slow response lets unboundedly many requests pile up
 *   in flight at once. Backend-performance-optimization-plan WS2 default: 2 (headroom for a
 *   couple of concurrent screens without ever approaching the documented per-second guideline).
 * @property cooldownEscalationStepsMs Ladder of shared-cooldown durations (ms) applied on
 *   successive retryable (429/503) failures while no clean-success decay window has elapsed —
 *   e.g. `[1_000, 2_000, 5_000, 15_000]` means the 1st hit in a "storm" opens a 1s cooldown, the
 *   2nd (before decay) widens it to 2s, etc., capped at the ladder's last step. This is what stops
 *   every caller from re-triggering the limit the instant the first cooldown expires.
 * @property cooldownDecayWindowMs After this many clean (non-retryable-failure) milliseconds have
 *   passed since the last retryable failure, the escalation ladder resets to its first step.
 * @property maxCooldownMs Hard cap on the shared cooldown duration, whichever is larger of the
 *   escalation-ladder step or a server `Retry-After` hint. Guards against a pathological/huge
 *   server-provided hint stalling the whole queue indefinitely.
 */
data class RateLimitConfig(
    val minDelayMs: Long = 100L,
    val maxRetries: Int = 3,
    val initialBackoffMs: Long = 200L,
    val maxBackoffMs: Long = 8_000L,
    val jitterFactor: Double = 0.25,
    val maxConcurrent: Int = 2,
    val cooldownEscalationStepsMs: List<Long> = listOf(1_000L, 2_000L, 5_000L, 15_000L),
    val cooldownDecayWindowMs: Long = 30_000L,
    val maxCooldownMs: Long = 30_000L,
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
 * A KMP-safe rate-limited request queue with configurable retry, exponential back-off, bounded
 * concurrency, and a SHARED cooldown gate.
 *
 * Serialises the *dispatch* of concurrent callers via a [Mutex], enforcing a minimum inter-request
 * delay of [RateLimitConfig.minDelayMs], and bounds concurrent in-flight [block] executions via a
 * [Semaphore] ([RateLimitConfig.maxConcurrent]). On retryable failures (determined by [shouldRetry],
 * e.g. HTTP 429/503), applies truncated binary-exponential back-off with jitter for the failing
 * caller's OWN retries, honouring server-provided `Retry-After` hints when available.
 *
 * ## Shared cooldown (Backend & Performance Optimization plan, WS2)
 * A single per-call back-off used to be the ENTIRE story: every *other* concurrent caller kept
 * dispatching at full speed into a server that was already rate-limiting, so the app could never
 * recover from a 429. Every retryable failure now also widens a SHARED `cooldownUntilMs` deadline
 * (escalating on consecutive hits per [RateLimitConfig.cooldownEscalationStepsMs], decaying back to
 * the first step after [RateLimitConfig.cooldownDecayWindowMs] of clean successes). **Every** caller
 * — including ones that never personally hit a 429 — waits out this cooldown at the TOP of [execute]
 * (and again on each of its own retry-loop iterations) before dispatching, via [awaitCooldown]. This
 * is what makes the queue recoverable instead of digging itself deeper.
 *
 * When a caller's own retries are exhausted on a retryable failure, [execute] throws
 * [RateLimitExhaustedException] (wrapping the original cause) instead of the raw exception, so UI
 * callers can distinguish "the service is rate-limiting us, back off" from a generic network error
 * and disable/countdown a retry CTA instead of letting the user re-trigger the storm.
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
 * @param config       Tuning constants for throttling, concurrency, and retry/cooldown behaviour.
 * @param shouldRetry  Lambda inspecting the attempt index and thrown exception to decide
 *                     whether to retry. Defaults to never retrying.
 * @param queueName    Stable, PII-free identifier used to namespace telemetry breadcrumbs/keys
 *                     (e.g. `"scryfall"`, `"archidekt"`) so two queues sharing this class don't
 *                     collide on the same Crashlytics custom key.
 * @param crashReporter Optional platform-neutral crash/log reporter (see [CrashReporter]). `null`
 *                     (the default) makes every telemetry hook a silent no-op — safe for tests and
 *                     any caller that doesn't want/need cooldown telemetry.
 */
@OptIn(ExperimentalTime::class)
class RateLimitedQueue(
    private val config: RateLimitConfig = RateLimitConfig(),
    private val shouldRetry: (attempt: Int, exception: Throwable) -> RetryDecision =
        { _, _ -> RetryDecision.DoNotRetry },
    private val queueName: String = "rate_limited_queue",
    private val crashReporter: CrashReporter? = null,
) {
    /** Guards every mutable field below — spacing bookkeeping AND cooldown/escalation state. */
    private val mutex = Mutex()

    /** Bounds concurrent in-flight [block] executions. See [RateLimitConfig.maxConcurrent]. */
    private val semaphore = Semaphore(config.maxConcurrent)

    /**
     * Timestamp (epoch ms) of the last request dispatched. Accessed only inside [mutex].
     */
    private var lastRequestTime: Long = 0L

    /** Epoch ms at which the shared cooldown clears. `0L` (the default) means "no active cooldown". */
    private var cooldownUntilMs: Long = 0L

    /**
     * `true` from the moment a cooldown is opened until the first waiter observes it has expired.
     * Used to log the "entered"/"exited" telemetry breadcrumbs exactly ONCE per cooldown episode,
     * even though many concurrent callers race through [awaitCooldown].
     */
    private var cooldownActive: Boolean = false

    /** Index into [RateLimitConfig.cooldownEscalationStepsMs] used by the NEXT retryable failure. */
    private var escalationLevel: Int = 0

    /** Epoch ms of the last retryable failure, used to detect a clean [RateLimitConfig.cooldownDecayWindowMs]. */
    private var lastFailureAtMs: Long = 0L

    /**
     * Running count of real dispatches (including retries) for this queue instance, for the whole
     * process lifetime. WS7 telemetry (backend-performance-optimization-plan.md): a cheap, approximate
     * (non-atomic outside the [mutex] it happens to sit inside) empirical signal for "how many calls
     * did this queue actually make this session" -- the thing that makes the campaign's call-volume
     * reduction verifiable in the field rather than just argued in an ADR. Deliberately NOT a separate
     * counter class with a lifecycle-boundary flush (see the WS7 audit's "session call counters"
     * design note) -- it just overwrites a Crashlytics custom key on every increment.
     */
    private var callCount: Long = 0L

    /**
     * Executes [block] while honouring the configured rate-limit contract: shared cooldown gate,
     * bounded concurrency, spacing, and retry/back-off.
     *
     * Retries automatically on retryable failures (as determined by [shouldRetry]) up to
     * [RateLimitConfig.maxRetries] times with exponential back-off.
     *
     * @throws RateLimitExhaustedException if a retryable failure (per [shouldRetry]) exhausts all
     *   retries.
     * @throws Throwable the last exception if a non-retryable exception occurs.
     */
    suspend fun <T> execute(block: suspend () -> T): T {
        var attempt = 0
        while (true) {
            // Shared gate: EVERY caller — not just the one that got rate-limited — waits out any
            // active cooldown before dispatching. Re-checked on every retry-loop iteration too, since
            // another concurrent caller's failure can widen the cooldown while we were sleeping.
            awaitCooldown()

            // Honour the minimum inter-request spacing inside the mutex so that concurrent callers
            // are serialised at the dispatch level.
            mutex.withLock {
                val now = nowMs()
                val elapsed = now - lastRequestTime
                if (elapsed < config.minDelayMs) {
                    delay(config.minDelayMs - elapsed)
                }
                lastRequestTime = nowMs()
                // WS7 session call counter: sits inside the already-locked block (free correctness,
                // per the WS7 audit's design note), counts every real dispatch attempt incl. retries.
                callCount++
                crashReporter?.setCustomKey("${queueName}_calls_session", callCount.toString())
            }

            try {
                // Bounded concurrency: caps how many `block()` calls can be in flight at once,
                // independently of dispatch spacing above (a slow response no longer lets in-flight
                // requests pile up unboundedly).
                return semaphore.withPermit { block() }
            } catch (e: Throwable) {
                if (attempt < config.maxRetries) {
                    when (val decision = shouldRetry(attempt, e)) {
                        is RetryDecision.Retry -> {
                            enterCooldown(decision.serverRetryAfterMs)
                            val backoffMs = computeBackoffMs(attempt, decision.serverRetryAfterMs)
                            attempt++
                            delay(backoffMs)
                            // continue -> next iteration re-awaits the (possibly widened) cooldown
                            // before retrying this same call.
                        }
                        RetryDecision.DoNotRetry -> throw e
                    }
                } else {
                    // Retries exhausted. `attempt > 0` is proof at least one prior iteration in THIS
                    // call took the Retry branch above (attempt only ever increments there), so this
                    // exhaustion was rate-limit-driven -- surface the typed error instead of the raw
                    // exception so callers/UI can distinguish "service is rate-limiting us" from a
                    // generic failure and gate a retry CTA on the remaining cooldown.
                    if (attempt > 0) {
                        val remaining = remainingCooldownMs()
                        // WS7 (2026-07-29): promoted from a log-only breadcrumb to a recordException --
                        // a log() alone is not independently aggregable in the Crashlytics console (it
                        // only surfaces as context on a LATER report), and "how often are we fully
                        // exhausting retries" is exactly the kind of frequency question that needs one.
                        val exhausted = RateLimitExhaustedException(retryAfterMs = remaining, cause = e)
                        crashReporter?.log("${queueName}_rate_limit_exhausted")
                        crashReporter?.setCustomKey("${queueName}_exhausted_retry_after_ms", remaining.toString())
                        crashReporter?.recordException(exhausted)
                        throw exhausted
                    } else {
                        // maxRetries == 0: shouldRetry was never consulted, so we cannot classify
                        // this failure -- preserve the original (pre-WS2) behaviour.
                        throw e
                    }
                }
            }
        }
    }

    // ── Shared cooldown ──────────────────────────────────────────────────────

    /**
     * Suspends until the shared cooldown (if any) has cleared. Logs the "cooldown exited"
     * breadcrumb exactly once, on the transition, via whichever waiter observes it first.
     */
    private suspend fun awaitCooldown() {
        while (true) {
            var waitMs = 0L
            var justExited = false
            mutex.withLock {
                waitMs = cooldownUntilMs - nowMs()
                if (waitMs <= 0 && cooldownActive) {
                    cooldownActive = false
                    justExited = true
                }
            }
            if (justExited) {
                crashReporter?.log("${queueName}_cooldown_exited")
            }
            if (waitMs <= 0) return
            delay(waitMs)
            // Loop: re-check under the mutex, since another caller's failure may have widened
            // cooldownUntilMs while we were sleeping.
        }
    }

    /**
     * Records a retryable (429/503) failure: widens the shared cooldown per the escalation ladder
     * (or the server's `Retry-After` hint, whichever is larger), capped at [RateLimitConfig.maxCooldownMs].
     * Resets the escalation ladder first if enough clean time has passed
     * ([RateLimitConfig.cooldownDecayWindowMs]) since the previous failure.
     */
    private suspend fun enterCooldown(serverRetryAfterMs: Long?) {
        var cooldownMs = 0L
        var wasInactive = false
        mutex.withLock {
            val now = nowMs()
            if (now - lastFailureAtMs > config.cooldownDecayWindowMs) {
                escalationLevel = 0
            }
            val steps = config.cooldownEscalationStepsMs
            val stepMs = if (steps.isEmpty()) 0L else steps[escalationLevel.coerceIn(0, steps.size - 1)]
            cooldownMs = maxOf(serverRetryAfterMs ?: 0L, stepMs).coerceAtMost(config.maxCooldownMs)
            if (steps.isNotEmpty() && escalationLevel < steps.size - 1) escalationLevel++
            lastFailureAtMs = now

            val candidateUntil = now + cooldownMs
            if (candidateUntil > cooldownUntilMs) cooldownUntilMs = candidateUntil
            wasInactive = !cooldownActive
            cooldownActive = true
        }
        if (wasInactive) {
            crashReporter?.log("${queueName}_cooldown_entered")
        }
        crashReporter?.setCustomKey("${queueName}_cooldown_ms", cooldownMs.toString())
    }

    /** Milliseconds remaining until [cooldownUntilMs], never negative. */
    private suspend fun remainingCooldownMs(): Long =
        mutex.withLock { (cooldownUntilMs - nowMs()).coerceAtLeast(0L) }

    private fun nowMs(): Long = Clock.System.now().toEpochMilliseconds()

    // ── Per-call back-off ────────────────────────────────────────────────────

    /**
     * Computes the delay in milliseconds before the CALLING coroutine's own next retry attempt.
     * This is independent of (and typically smaller than) the shared cooldown above -- the shared
     * cooldown is what actually protects every OTHER caller; this is just this attempt's own pacing.
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
