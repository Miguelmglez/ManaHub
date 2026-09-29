package com.mmg.manahub.core.gamification.domain

import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.gamification.domain.event.ProgressionEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Platform scheduling of the gamification background jobs (quest rotation + remote sync).
 */
interface GamificationWorkScheduler {

    /** Schedules the local, account-independent periodic work (quest rotation). Idempotent. */
    fun scheduleLocalWork()

    /** Schedules the periodic remote sync for the signed-in account. Idempotent. */
    fun scheduleSync()

    /** Cancels every remote sync work name. */
    fun cancelSync()

    /** Cancels every gamification work name, local and sync. */
    fun cancelAll()
}

/**
 * The idempotent catch-up run on every OFF→ON transition: DERIVED achievement backfill (with its tier
 * XP), full entitlement reconcile and quest reconcile. Implementations must never throw except on
 * cancellation.
 */
fun interface GamificationCatchUp {

    /** Runs the catch-up passes in order. */
    suspend fun run()
}

/** What [GamificationAccountScope.onSignedIn] did with the local store. */
enum class GamificationAccountScopeResult {
    /** Guest-owned store claimed by the account and merged into it. */
    CLAIMED,

    /** Store already owned by the account; a normal sync ran. */
    SYNCED,

    /** Store belonged to another account: wiped, re-owned and pulled from the server. */
    SWITCHED,
}

/**
 * Binds the local gamification store to exactly one account (D3 / G-02).
 */
fun interface GamificationAccountScope {

    /** Claims, syncs or wipes-and-pulls the local store for [userId]. May throw on local storage failures. */
    suspend fun onSignedIn(userId: String): GamificationAccountScopeResult

    /** Quarantines unowned legacy rows before a signed-out session begins earning guest progress. */
    suspend fun onGuestActive(): Boolean = false
}

/**
 * Owns the whole gamification backend lifecycle (ADR-005 D1).
 *
 * While [GamificationAvailability.gateState] is AVAILABLE: the engine collects the bus, local work is
 * scheduled, the catch-up runs, `AppOpenedToday` is emitted once per local day (at enable and on every
 * foreground), and the signed-in account is scoped + synced. Any other state cancels the engine and
 * every work name. Each OFF→ON transition repeats the catch-up because it is idempotent.
 *
 * @param signedInUserId the authenticated user id, or null when signed out / unknown.
 * @param appForegroundEvents one emission each time the app comes to the foreground.
 */
class GamificationBackendGate(
    private val availability: GamificationAvailability,
    private val engine: GamificationEngine,
    private val bus: ProgressionEventBus,
    private val workScheduler: GamificationWorkScheduler,
    private val catchUp: GamificationCatchUp,
    private val accountScope: GamificationAccountScope,
    private val scopeExternallyManaged: Boolean = false,
    private val signedInUserId: Flow<String?>,
    private val appForegroundEvents: Flow<Unit>,
    private val clock: Clock,
    private val timeZoneProvider: () -> TimeZone,
    private val crashReporter: CrashReporter,
) {

    /** Starts observing the gate on [scope]; cancel the returned job to stop everything. */
    fun start(scope: CoroutineScope): Job = scope.launch {
        availability.gateState.collectLatest { state ->
            crashReporter.setCustomKey(GATE_STATE_KEY, state.telemetryKey)
            crashReporter.log("gamification_gate_${state.telemetryKey}")
            if (state == GamificationGateState.AVAILABLE) runEnabled() else workScheduler.cancelAll()
        }
    }

    // Everything launched here is a child of this scope, so collectLatest cancelling it stops the engine.
    private suspend fun runEnabled() = coroutineScope {
        // Catch-up and account scoping both rewrite local tables; they must never interleave.
        val localStoreMutex = Mutex()
        val appOpen = AppOpenEmitter()

        engine.start(this)
        workScheduler.scheduleLocalWork()
        appOpen.emitIfNewDay()

        launch { localStoreMutex.withLock { guarded("gamification_catch_up_failed") { catchUp.run() } } }
        launch { appForegroundEvents.collect { appOpen.emitIfNewDay() } }
        if (!scopeExternallyManaged) launch {
            signedInUserId.distinctUntilChanged().collectLatest { userId ->
                if (userId == null) {
                    workScheduler.cancelSync()
                    var legacyRowsWiped = false
                    localStoreMutex.withLock {
                        guarded("gamification_guest_scope_failed") {
                            legacyRowsWiped = accountScope.onGuestActive()
                        }
                    }
                    if (legacyRowsWiped) appOpen.emitIfNewDay(force = true)
                    return@collectLatest
                }
                workScheduler.scheduleSync()
                var result: GamificationAccountScopeResult? = null
                localStoreMutex.withLock {
                    guarded("gamification_account_scope_failed") { result = accountScope.onSignedIn(userId) }
                    if (result == GamificationAccountScopeResult.SWITCHED) {
                        guarded("gamification_catch_up_failed") { catchUp.run() }
                    }
                }
                // The wipe dropped today's check-in row, so the new account gets its own.
                if (result == GamificationAccountScopeResult.SWITCHED) appOpen.emitIfNewDay(force = true)
            }
        }
    }

    private suspend inline fun guarded(logEvent: String, block: () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            crashReporter.log(logEvent)
            crashReporter.recordException(IllegalStateException("[$logEvent] ${e::class.simpleName}"))
        }
    }

    /** Emits `AppOpenedToday` at most once per local date for one enabled period. */
    private inner class AppOpenEmitter {
        private val mutex = Mutex()
        private var lastDate: LocalDate? = null

        suspend fun emitIfNewDay(force: Boolean = false) {
            val now = clock.now()
            val today = now.toLocalDateTime(timeZoneProvider()).date
            val shouldEmit = mutex.withLock {
                if (!force && lastDate == today) {
                    false
                } else {
                    lastDate = today
                    true
                }
            }
            if (shouldEmit) {
                bus.emit(ProgressionEvent.AppOpenedToday(localDate = today.toString(), occurredAt = now))
            }
        }
    }

    private companion object {
        const val GATE_STATE_KEY = "gamification_gate_state"
    }
}
