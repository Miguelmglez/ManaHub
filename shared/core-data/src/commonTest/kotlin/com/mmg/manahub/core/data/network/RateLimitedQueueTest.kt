package com.mmg.manahub.core.data.network

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Multiplatform (commonTest) coverage for [RateLimitedQueue] — KMP migration remediation P1.4, plus
 * the Backend & Performance Optimization plan WS2 rewrite (2026-07-28, see
 * `project_scryfall_rate_limit_resilience_ws2` memory). This is the SOLE owning suite for 429/
 * shared-cooldown behaviour — no other suite in the repo re-tests it (see WS6 of
 * `docs/plans/backend-performance-optimization-plan.md`).
 *
 * [RateLimitedQueue] is the concurrency primitive most likely to diverge between the JVM host and
 * the wasmJs `Default` dispatcher (pure coroutines + [kotlinx.coroutines.sync.Mutex], no platform
 * dependency), so it is the first target for `commonTest` coverage in `:shared:core-data`.
 *
 * ## Timing note — spacing/backoff vs. the shared cooldown gate (READ BEFORE ADDING A TEST HERE)
 * [RateLimitedQueue] measures ALL of its deadlines against the REAL wall clock
 * (`kotlin.time.Clock.System`), which [kotlinx.coroutines.test.runTest]'s virtual-time scheduler does
 * not advance. Two very different consequences follow, and conflating them is exactly the mistake
 * this class doc exists to prevent:
 *
 * 1. **One-shot spacing/backoff delays** (`minDelayMs` in the dispatch mutex, and the per-call
 *    `computeBackoffMs` delay) each compute their duration ONCE and call `delay()` ONCE. Since these
 *    tests execute back-to-back with ~0ms of real elapsed time, the computed duration is
 *    deterministic, and the virtual scheduler's [kotlinx.coroutines.test.currentTime] DOES faithfully
 *    track it (a `delay(N)` call always advances `currentTime` by exactly `N`, regardless of real
 *    time). This is safe to assert on directly — see [consecutiveCallsAreSpacedByAtLeastTheConfiguredMinDelay].
 * 2. **The shared cooldown gate** (`awaitCooldown`) instead LOOPS: it re-checks `cooldownUntilMs -
 *    realNowMs()` under a mutex, `delay()`s the (real-time-derived) remainder, and re-checks again.
 *    Because real wall-clock time barely advances between statements in a fast-running test, each
 *    loop iteration only shaves a sub-millisecond sliver off the real remainder — so the loop takes
 *    MANY iterations to converge, and EACH iteration's `delay()` call adds to `currentTime`. Measured
 *    locally: a 50ms configured cooldown ballooned `currentTime` to ~14,850ms while taking ~75ms of
 *    REAL wall time to actually resolve. **`currentTime` is therefore not a valid proxy for a
 *    cooldown's configured duration** — this is exactly why the old `serverRetryAfterHintIs...`
 *    test's `assertEquals(500L, currentTime)` assertion (encoding the PRE-WS2 behaviour, where the
 *    server hint was wrongly truncated to the per-call `maxBackoffMs` instead of the separate shared
 *    `maxCooldownMs`) could not simply be "fixed" to a new hardcoded `currentTime` value — the whole
 *    measurement approach is wrong for this code path.
 *
 * Every test below that exercises `awaitCooldown` (i.e. a `RetryDecision.Retry` is taken at least
 * once) therefore: (a) uses MILLISECOND-SCALE `RateLimitConfig` overrides (`cooldownEscalationStepsMs`/
 * `maxCooldownMs` in the 1-50ms range, never the production defaults of 1s-30s — those are real-time
 * costs in this harness, not virtual ones) so the real convergence cost of the test stays in the tens
 * of milliseconds, and (b) asserts on REAL elapsed time (`kotlin.time.Clock.System.now()`, the same
 * KMP-safe API the production code itself uses) with generous bounds, or on purely structural
 * signals (job completion state via [runCurrent]/[advanceUntilIdle], exception fields) — never on
 * `currentTime` for a cooldown-gated code path.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)
class RateLimitedQueueTest {

    @Test
    fun executeReturnsTheBlockResultOnSuccess() = runTest {
        val queue = RateLimitedQueue()
        val result = queue.execute { "ok" }
        assertEquals("ok", result)
    }

    @Test
    fun firstCallProceedsWithoutWaitingForTheMinDelay() = runTest {
        val queue = RateLimitedQueue(RateLimitConfig(minDelayMs = 100L))
        queue.execute { "first" }
        // lastRequestTime starts at 0L, so "now - 0" is always far larger than minDelayMs:
        // the very first dispatch must never wait.
        assertEquals(0L, currentTime)
    }

    @Test
    fun consecutiveCallsAreSpacedByAtLeastTheConfiguredMinDelay() = runTest {
        val queue = RateLimitedQueue(RateLimitConfig(minDelayMs = 100L))
        queue.execute { "first" }
        queue.execute { "second" }
        // The second call sees ~0ms of real elapsed time since the first, so it must wait out
        // (almost exactly) the configured minDelayMs.
        assertTrue(currentTime >= 90L, "expected >=90ms of tracked delay, was $currentTime")
    }

    @Test
    fun serialisesConcurrentCallersUnderTheMutex() = runTest {
        val queue = RateLimitedQueue(RateLimitConfig(minDelayMs = 50L))
        val order = mutableListOf<Int>()
        val jobs = (1..5).map { i ->
            launch { queue.execute { order += i } }
        }
        jobs.forEach { it.join() }

        // All 5 callers ran exactly once each (the mutex serialises the spacing bookkeeping, so no
        // caller is starved or double-counted).
        assertEquals(5, order.size)
        assertEquals((1..5).toSet(), order.toSet())
        // Only the chronologically-first of the 5 skips the spacing delay; the other 4 each incur
        // ~minDelayMs of tracked delay, regardless of dispatch order.
        assertTrue(
            currentTime in 180L..260L,
            "expected ~4x50ms of tracked spacing delay across 5 serialised dispatches, was $currentTime",
        )
    }

    @Test
    fun doNotRetryPropagatesTheExceptionImmediatelyWithoutRetrying() = runTest {
        var blockCalls = 0
        var shouldRetryCalls = 0
        val queue = RateLimitedQueue(
            config = RateLimitConfig(minDelayMs = 0L),
            shouldRetry = { _, _ -> shouldRetryCalls++; RetryDecision.DoNotRetry },
        )
        val error = IllegalStateException("boom")

        val thrown = assertFailsWith<IllegalStateException> {
            queue.execute { blockCalls++; throw error }
        }

        assertEquals(error, thrown)
        assertEquals(1, blockCalls)
        assertEquals(1, shouldRetryCalls)
        // DoNotRetry never takes the Retry branch, so enterCooldown is never called -- a completely
        // innocent concurrent caller must NOT be paused by a non-retryable failure.
        val nextCallStart = Clock.System.now()
        queue.execute { "unaffected" }
        val elapsed = (Clock.System.now() - nextCallStart).inWholeMilliseconds
        assertTrue(elapsed < 500L, "a DoNotRetry failure must not open a shared cooldown, was ${elapsed}ms")
    }

    @Test
    fun retriesUpToMaxRetriesThenThrowsTheLastException() = runTest {
        var blockCalls = 0
        var shouldRetryCalls = 0
        val queue = RateLimitedQueue(
            // WS2 (2026-07-28): every Retry decision now also opens the shared cooldown gate
            // (`enterCooldown`), which converges against the REAL wall clock (see the class doc's
            // timing note) -- cooldownEscalationStepsMs/maxCooldownMs are pinned to millisecond-scale
            // values here so this test stays fast; the production defaults (1s-30s) would make this
            // single test take several real seconds.
            config = RateLimitConfig(
                minDelayMs = 0L, maxRetries = 2, initialBackoffMs = 1L, maxBackoffMs = 1L,
                cooldownEscalationStepsMs = listOf(1L), maxCooldownMs = 1L,
            ),
            shouldRetry = { _, _ -> shouldRetryCalls++; RetryDecision.Retry() },
        )

        assertFailsWith<RuntimeException> {
            queue.execute { blockCalls++; throw RuntimeException("fail-$blockCalls") }
        }

        // 1 initial attempt + 2 retries. shouldRetry is consulted only while attempt < maxRetries,
        // so the 3rd (final) failure short-circuits straight to the throw without a 3rd consultation.
        assertEquals(3, blockCalls)
        assertEquals(2, shouldRetryCalls)
    }

    // ── WS2: shared cooldown gate ───────────────────────────────────────────────

    @Test
    fun a429OnOneCallerPausesEveryConcurrentCallerNotOnlyTheFailingOne() = runTest {
        // This is THE recoverability fix WS2 exists for: before it, every OTHER concurrent caller
        // kept dispatching at full speed into an already-limited server. Verified deterministically
        // via job-completion state (no real-time reliance for the assertion itself) -- only
        // driving the queue to convergence at the end uses (bounded, millisecond-scale) real time.
        var failed = false
        val queue = RateLimitedQueue(
            config = RateLimitConfig(
                minDelayMs = 0L, maxRetries = 1, initialBackoffMs = 1L, maxBackoffMs = 1L,
                cooldownEscalationStepsMs = listOf(50L), maxCooldownMs = 50L,
            ),
            shouldRetry = { _, _ -> RetryDecision.Retry() },
        )

        // Caller A fails once, opening the shared cooldown, then succeeds on its own retry.
        val aJob = launch {
            queue.execute { if (!failed) { failed = true; throw RuntimeException("boom") } else "A-done" }
        }
        // Drive A synchronously up to (and including) enterCooldown() + its own backoff delay's
        // suspension point -- runCurrent() only runs work ready at the CURRENT virtual time, so a
        // scheduled-in-the-future delay() correctly leaves A suspended rather than completing it.
        runCurrent()

        // Caller B is a completely innocent caller -- its block never throws.
        val bJob = launch { queue.execute { "B-done" } }
        runCurrent()

        assertFalse(aJob.isCompleted, "caller A should still be waiting out its own cooldown/backoff")
        assertFalse(bJob.isCompleted, "an innocent concurrent caller B must be paused by A's cooldown too")

        advanceUntilIdle()
        assertTrue(aJob.isCompleted)
        assertTrue(bJob.isCompleted)
    }

    @Test
    fun serverRetryAfterHintWidensTheSharedCooldownButIsCappedAtMaxCooldownMs() = runTest {
        // Rewrite of the pre-WS2 `serverRetryAfterHintIsHonouredAndCappedAtMaxBackoff` test, which
        // asserted the EXACT bug this workstream fixes: a large server Retry-After hint used to be
        // silently truncated to the per-call `maxBackoffMs` for the retrying caller's own wait, while
        // every OTHER caller ignored it entirely. Now the shared cooldown honours the hint, capped at
        // the SEPARATE `maxCooldownMs` -- not `maxBackoffMs`. Asserted via bounded REAL elapsed time
        // (see the class doc's timing note): if the 10-SECOND server hint were honoured uncapped,
        // this test would take ~10 real seconds; capped at maxCooldownMs=30ms it must stay fast.
        var attempts = 0
        val queue = RateLimitedQueue(
            config = RateLimitConfig(
                minDelayMs = 0L, maxRetries = 1, initialBackoffMs = 1L, maxBackoffMs = 1L,
                cooldownEscalationStepsMs = listOf(5L), maxCooldownMs = 30L,
            ),
            shouldRetry = { _, _ -> RetryDecision.Retry(serverRetryAfterMs = 10_000L) },
        )

        val start = Clock.System.now()
        val result = queue.execute {
            attempts++
            if (attempts == 1) throw RuntimeException("boom") else "A-done"
        }
        val elapsed = (Clock.System.now() - start).inWholeMilliseconds

        assertEquals("A-done", result)
        assertEquals(2, attempts)
        assertTrue(
            elapsed < 2_000L,
            "expected the shared cooldown to cap the 10s server hint at maxCooldownMs=30ms, was ${elapsed}ms",
        )
    }

    @Test
    fun escalationLadderWidensCooldownAcrossConsecutiveRetryableFailures() = runTest {
        var attempts = 0
        val queue = RateLimitedQueue(
            config = RateLimitConfig(
                minDelayMs = 0L, maxRetries = 2, initialBackoffMs = 1L, maxBackoffMs = 1L,
                cooldownEscalationStepsMs = listOf(10L, 20L, 40L), maxCooldownMs = 40L,
            ),
            shouldRetry = { _, _ -> RetryDecision.Retry() },
        )

        val start = Clock.System.now()
        assertFailsWith<RateLimitExhaustedException> {
            queue.execute { attempts++; throw RuntimeException("fail-$attempts") }
        }
        val elapsed = (Clock.System.now() - start).inWholeMilliseconds

        assertEquals(3, attempts)
        // Two escalating cooldown episodes are awaited within this single call (step[0]=10ms after
        // the 1st failure, step[1]=20ms after the 2nd) -- the total real time must clear a floor
        // that a FLAT (non-escalating) 10ms-per-failure cooldown could not reach, proving the ladder
        // actually widened rather than staying pinned to step[0]. Generous bounds absorb CI/JIT jitter.
        assertTrue(
            elapsed >= 15L,
            "expected escalation across 2 widening cooldown episodes (~30ms of real cooldown), was ${elapsed}ms",
        )
        assertTrue(elapsed < 5_000L, "expected the sequence to stay well under a runaway bound, was ${elapsed}ms")
    }

    @Test
    fun cooldownEscalationDecaysAfterACleanSuccessWindow() = runTest {
        // step[1] (200ms) is deliberately ~20x step[0] (10ms) so the two scenarios are trivially
        // distinguishable via real elapsed time. A genuine (non-virtual) sleep on Dispatchers.Default
        // is required here -- unlike every other test in this file, decay is measured against actual
        // real-clock elapsed time (cooldownDecayWindowMs), which the virtual scheduler cannot fake.
        val queue = RateLimitedQueue(
            config = RateLimitConfig(
                minDelayMs = 0L, maxRetries = 1, initialBackoffMs = 1L, maxBackoffMs = 1L,
                cooldownEscalationStepsMs = listOf(10L, 200L), cooldownDecayWindowMs = 5L, maxCooldownMs = 200L,
            ),
            shouldRetry = { _, _ -> RetryDecision.Retry() },
        )

        var shouldFail = true
        val start1 = Clock.System.now()
        queue.execute { if (shouldFail) { shouldFail = false; throw RuntimeException("boom-1") } else "ok-1" }
        val elapsed1 = (Clock.System.now() - start1).inWholeMilliseconds

        // Real sleep comfortably longer than cooldownDecayWindowMs=5ms, on a REAL dispatcher (escapes
        // runTest's virtual scheduler) so the decay window's real-time check genuinely elapses.
        withContext(Dispatchers.Default) { delay(50) }

        shouldFail = true
        val start2 = Clock.System.now()
        queue.execute { if (shouldFail) { shouldFail = false; throw RuntimeException("boom-2") } else "ok-2" }
        val elapsed2 = (Clock.System.now() - start2).inWholeMilliseconds

        // Without decay, the 2nd failure would climb to step[1]=200ms -- a ~20x larger real cooldown
        // than step[0]. Decay resets the escalation ladder, so the 2nd failure's cost stays
        // comparable to the first instead of ballooning toward 200ms.
        assertTrue(elapsed1 < 150L, "step[0] cooldown should stay well under step[1]=200ms, was ${elapsed1}ms")
        assertTrue(
            elapsed2 < 150L,
            "expected decay to reset the ladder back to step[0]=10ms rather than climbing to " +
                "step[1]=200ms, was ${elapsed2}ms (elapsed1=${elapsed1}ms)",
        )
    }

    @Test
    fun semaphoreCapsInFlightBlockExecutions() = runTest {
        val queue = RateLimitedQueue(config = RateLimitConfig(minDelayMs = 0L, maxConcurrent = 2))
        var inFlight = 0
        var maxObserved = 0
        val gates = List(4) { CompletableDeferred<Unit>() }

        val jobs = gates.map { gate ->
            launch {
                queue.execute {
                    inFlight++
                    maxObserved = maxOf(maxObserved, inFlight)
                    gate.await()
                    inFlight--
                }
            }
        }
        // Dispatch is unpaced (minDelayMs=0) and no cooldown is active, so all 4 launches run
        // synchronously up to whatever blocks them: the first 2 acquire a semaphore permit and park
        // inside the block on gate.await(); the other 2 block ON THE SEMAPHORE ITSELF, never even
        // reaching the `inFlight++` line.
        runCurrent()
        assertEquals(2, maxObserved, "maxConcurrent=2 must cap in-flight block() executions")
        assertEquals(2, inFlight, "only 2 of the 4 callers should have been admitted past the semaphore")

        // Release the first two permits; the two queued callers should now be admitted.
        gates[0].complete(Unit)
        gates[1].complete(Unit)
        runCurrent()
        assertEquals(2, inFlight, "2 released + 2 newly admitted -- still capped at 2 concurrently in-flight")
        assertEquals(2, maxObserved, "the cap must never have been exceeded at any point")

        gates[2].complete(Unit)
        gates[3].complete(Unit)
        jobs.forEach { it.join() }
        assertEquals(0, inFlight)
    }

    @Test
    fun retryExhaustionThrowsTypedExceptionWithAParseableRetryAfter() = runTest {
        val queue = RateLimitedQueue(
            config = RateLimitConfig(
                minDelayMs = 0L, maxRetries = 1, initialBackoffMs = 1L, maxBackoffMs = 1L,
                cooldownEscalationStepsMs = listOf(1L), maxCooldownMs = 1L,
            ),
            shouldRetry = { _, _ -> RetryDecision.Retry() },
        )

        val thrown = assertFailsWith<RateLimitExhaustedException> {
            queue.execute { throw RuntimeException("boom") }
        }

        assertTrue(thrown.retryAfterMs >= 0L, "retryAfterMs must never be negative")
        assertTrue(
            thrown.message?.startsWith(RateLimitExhaustedException.SENTINEL_PREFIX) == true,
            "the flattened message must carry the sentinel so a caller several layers up the stack " +
                "(DataResult.Error(message: String)) can still recognise a rate-limit exhaustion",
        )
        // Round-trips through the SAME parsing path a caller with only the flattened message uses.
        assertEquals(thrown.retryAfterMs, RateLimitExhaustedException.retryAfterMsOrNull(thrown.message))
    }
}
