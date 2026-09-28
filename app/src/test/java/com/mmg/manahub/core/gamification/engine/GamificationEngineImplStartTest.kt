package com.mmg.manahub.core.gamification.engine

import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.gamification.domain.ProgressionEventBus
import com.mmg.manahub.core.gamification.domain.event.ProgressionEvent
import com.mmg.manahub.core.gamification.domain.model.ProgressionOutcome
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class GamificationEngineImplStartTest {

    private val bus = ProgressionEventBus()
    private val xpGranter: XpGranter = mockk()
    private val crashReporter: CrashReporter = mockk(relaxed = true)

    private val event = ProgressionEvent.AppOpenedToday(
        localDate = "2026-09-24",
        occurredAt = Instant.parse("2026-09-24T10:00:00Z"),
    )

    private fun engine(dispatcher: CoroutineDispatcher) = GamificationEngineImpl(
        bus = bus,
        xpGranter = xpGranter,
        achievementEvaluator = mockk(relaxed = true),
        questEvaluator = mockk(relaxed = true),
        streakTracker = mockk(relaxed = true),
        entitlementGranter = mockk(relaxed = true),
        defaultDispatcher = dispatcher,
        crashReporter = crashReporter,
    )

    @Test
    fun `an event emitted right after start is not dropped`() = runTest {
        coEvery { xpGranter.grant(any()) } returns XpGrantResult.NoXp
        val engine = engine(StandardTestDispatcher(testScheduler))

        engine.start(backgroundScope)
        // No runCurrent between start and emit: the subscription must already exist.
        bus.emit(event)
        runCurrent()

        coVerify(exactly = 1) { xpGranter.grant(event) }
    }

    @Test
    fun `start is idempotent while running and restartable after cancel`() = runTest {
        coEvery { xpGranter.grant(any()) } returns XpGrantResult.NoXp
        val engine = engine(StandardTestDispatcher(testScheduler))

        val first = engine.start(backgroundScope)
        assertSame(first, engine.start(backgroundScope))

        first.cancel()
        runCurrent()
        bus.emit(event)
        runCurrent()
        coVerify(exactly = 0) { xpGranter.grant(any()) }

        val second = engine.start(backgroundScope)
        assertNotSame(first, second)
        assertTrue(second.isActive)
        bus.emit(event)
        runCurrent()
        coVerify(exactly = 1) { xpGranter.grant(event) }
    }

    @Test
    fun `stopping mid-event aborts it without reporting a failure`() = runTest {
        coEvery { xpGranter.grant(any()) } coAnswers { awaitCancellation() }
        val engine = engine(StandardTestDispatcher(testScheduler))

        val job = engine.start(backgroundScope)
        bus.emit(event)
        runCurrent()
        job.cancel(CancellationException("gate closed"))
        runCurrent()

        assertFalse(job.isActive)
        verify(exactly = 0) { crashReporter.recordException(any()) }
    }
}
