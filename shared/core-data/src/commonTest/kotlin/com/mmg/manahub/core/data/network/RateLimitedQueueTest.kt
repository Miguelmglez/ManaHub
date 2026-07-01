package com.mmg.manahub.core.data.network

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Multiplatform (commonTest) coverage for [RateLimitedQueue] — KMP migration remediation P1.4.
 *
 * [RateLimitedQueue] is the concurrency primitive most likely to diverge between the JVM host and
 * the wasmJs `Default` dispatcher (pure coroutines + [kotlinx.coroutines.sync.Mutex], no platform
 * dependency), so it is the first target for `commonTest` coverage in `:shared:core-data`.
 *
 * Timing note: [RateLimitedQueue] measures spacing against the REAL wall clock
 * (`kotlin.time.Clock.System`), which [kotlinx.coroutines.test.runTest]'s virtual-time scheduler
 * does not advance. Because these tests execute back-to-back with negligible real elapsed time
 * between statements, the queue's internal `elapsed` computation is always ~0ms — meaning every
 * call after the first deterministically triggers a `delay(minDelayMs)` (or the requested backoff),
 * and that delay duration IS tracked by [kotlinx.coroutines.test.currentTime] (the scheduler
 * advances its virtual clock to match every requested delay, real time or not). That is what the
 * timing assertions below rely on — not the real-time wall clock itself.
 */
@OptIn(ExperimentalCoroutinesApi::class)
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
    }

    @Test
    fun retriesUpToMaxRetriesThenThrowsTheLastException() = runTest {
        var blockCalls = 0
        var shouldRetryCalls = 0
        val queue = RateLimitedQueue(
            config = RateLimitConfig(minDelayMs = 0L, maxRetries = 2, initialBackoffMs = 1L, maxBackoffMs = 1L),
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

    @Test
    fun serverRetryAfterHintIsHonouredAndCappedAtMaxBackoff() = runTest {
        var blockCalls = 0
        val queue = RateLimitedQueue(
            config = RateLimitConfig(minDelayMs = 0L, maxRetries = 1, maxBackoffMs = 500L),
            shouldRetry = { _, _ -> RetryDecision.Retry(serverRetryAfterMs = 5_000L) },
        )

        assertFailsWith<RuntimeException> {
            queue.execute { blockCalls++; throw RuntimeException("boom") }
        }

        assertEquals(2, blockCalls)
        // The server hinted 5000ms, but the config caps backoff at 500ms; the server-hint path
        // applies no jitter, so the tracked delay must be exactly the capped value.
        assertEquals(500L, currentTime)
    }
}
