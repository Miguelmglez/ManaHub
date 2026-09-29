package com.mmg.manahub.core.gamification.domain

import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.domain.config.DefaultsOnlyRemoteConfigRepository
import com.mmg.manahub.core.gamification.domain.event.ProgressionEvent
import com.mmg.manahub.core.gamification.domain.model.ProcessedOutcome
import com.mmg.manahub.core.gamification.domain.model.ProgressionOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GamificationBackendGateTest {

    private val optIn = MutableStateFlow(true)
    private val userId = MutableStateFlow<String?>(null)
    private val foreground = MutableSharedFlow<Unit>()
    private val bus = ProgressionEventBus()
    private val engine = FakeEngine(bus)
    private val scheduler = FakeScheduler()
    private val crashReporter = FakeCrashReporter()
    private val clock = MutableClock(Instant.parse("2026-09-24T10:00:00Z"))
    private var catchUpRuns = 0
    private var accountResult = GamificationAccountScopeResult.SYNCED
    private var accountFailure: Throwable? = null
    private val scopedUsers = mutableListOf<String>()

    private fun gate(compileEnabled: Boolean = true) = GamificationBackendGate(
        availability = DefaultGamificationAvailability(
            remoteConfigRepository = DefaultsOnlyRemoteConfigRepository(),
            userOptInFlow = optIn,
            compileEnabled = compileEnabled,
        ),
        engine = engine,
        bus = bus,
        workScheduler = scheduler,
        catchUp = { catchUpRuns++ },
        accountScope = { id ->
            scopedUsers += id
            accountFailure?.let { throw it }
            accountResult
        },
        signedInUserId = userId,
        appForegroundEvents = foreground,
        clock = clock,
        timeZoneProvider = { TimeZone.UTC },
        crashReporter = crashReporter,
    )

    private fun TestScope.startGate(compileEnabled: Boolean = true): Job =
        gate(compileEnabled).start(backgroundScope).also { runCurrent() }

    private val appOpens get() = engine.received.filterIsInstance<ProgressionEvent.AppOpenedToday>()

    @Test
    fun compileDisabledNeverStartsTheEngineAndCancelsStaleWork() = runTest {
        startGate(compileEnabled = false)

        assertEquals(0, engine.starts)
        assertEquals(1, scheduler.cancelAll)
        assertEquals(0, scheduler.scheduleLocal)
        assertEquals(0, catchUpRuns)
        assertEquals("compile_disabled", crashReporter.keys["gamification_gate_state"])
    }

    @Test
    fun offOnOffOnStopsAndRestartsEverythingAndRepeatsTheCatchUp() = runTest {
        optIn.value = false
        startGate()
        assertEquals(0, engine.starts)
        assertEquals(1, scheduler.cancelAll)

        optIn.value = true
        runCurrent()
        assertEquals(1, engine.starts)
        assertTrue(engine.lastJob!!.isActive)
        assertEquals(1, scheduler.scheduleLocal)
        assertEquals(1, catchUpRuns)
        assertEquals(1, appOpens.size)

        optIn.value = false
        runCurrent()
        assertFalse(engine.lastJob!!.isActive)
        assertEquals(2, scheduler.cancelAll)

        optIn.value = true
        runCurrent()
        assertEquals(2, engine.starts)
        assertTrue(engine.lastJob!!.isActive)
        assertEquals(2, scheduler.scheduleLocal)
        assertEquals(2, catchUpRuns)
        // Same local day, but a new enabled period re-emits; the ledger key dedupes it.
        assertEquals(2, appOpens.size)
        assertEquals("available", crashReporter.keys["gamification_gate_state"])
    }

    @Test
    fun appOpenedTodayEmittedAtStartReachesTheEngine() = runTest {
        startGate()

        assertEquals(listOf("2026-09-24"), appOpens.map { it.localDate })
    }

    @Test
    fun foregroundEmitsAgainOnlyAfterTheLocalDateChanges() = runTest {
        startGate()

        foreground.emit(Unit)
        runCurrent()
        assertEquals(1, appOpens.size)

        clock.now = Instant.parse("2026-09-25T00:05:00Z")
        foreground.emit(Unit)
        runCurrent()
        assertEquals(listOf("2026-09-24", "2026-09-25"), appOpens.map { it.localDate })
    }

    @Test
    fun signedInUserIsScopedAndSyncScheduledSignOutCancelsSync() = runTest {
        startGate()

        userId.value = "user-a"
        runCurrent()
        assertEquals(listOf("user-a"), scopedUsers)
        assertEquals(1, scheduler.scheduleSync)

        userId.value = null
        runCurrent()
        assertEquals(2, scheduler.cancelSync)
    }

    @Test
    fun noAccountScopingWhileUnavailable() = runTest {
        optIn.value = false
        userId.value = "user-a"
        startGate()

        assertTrue(scopedUsers.isEmpty())
        assertEquals(0, scheduler.scheduleSync)
    }

    @Test
    fun accountSwitchReRunsTheCatchUpAndTheCheckIn() = runTest {
        accountResult = GamificationAccountScopeResult.SWITCHED
        startGate()
        assertEquals(1, catchUpRuns)

        userId.value = "user-b"
        runCurrent()
        assertEquals(2, catchUpRuns)
        assertEquals(2, appOpens.size)
    }

    @Test
    fun accountScopeFailureIsReportedAndDoesNotStopTheGate() = runTest {
        accountFailure = IllegalStateException("disk full")
        startGate()

        userId.value = "user-a"
        runCurrent()

        assertTrue("gamification_account_scope_failed" in crashReporter.logs)
        assertEquals(1, crashReporter.exceptions.size)
        assertTrue(engine.lastJob!!.isActive)
    }

    private class FakeEngine(private val bus: ProgressionEventBus) : GamificationEngine {
        var starts = 0
        var lastJob: Job? = null
        val received = mutableListOf<ProgressionEvent>()
        override val outcomes: SharedFlow<ProcessedOutcome> = MutableSharedFlow()

        override suspend fun process(event: ProgressionEvent): ProgressionOutcome = ProgressionOutcome.none

        override fun start(scope: CoroutineScope): Job {
            starts++
            return scope.launch(start = CoroutineStart.UNDISPATCHED) {
                bus.events.collect { received += it }
            }.also { lastJob = it }
        }
    }

    private class FakeScheduler : GamificationWorkScheduler {
        var scheduleLocal = 0
        var scheduleSync = 0
        var cancelSync = 0
        var cancelAll = 0
        override fun scheduleLocalWork() { scheduleLocal++ }
        override fun scheduleSync() { scheduleSync++ }
        override fun cancelSync() { cancelSync++ }
        override fun cancelAll() { cancelAll++ }
    }

    private class FakeCrashReporter : CrashReporter {
        val logs = mutableListOf<String>()
        val keys = mutableMapOf<String, String>()
        val exceptions = mutableListOf<Throwable>()
        override fun recordException(throwable: Throwable) { exceptions += throwable }
        override fun log(message: String) { logs += message }
        override fun setCustomKey(key: String, value: String) { keys[key] = value }
    }

    private class MutableClock(var now: Instant) : Clock {
        override fun now(): Instant = now
    }
}
