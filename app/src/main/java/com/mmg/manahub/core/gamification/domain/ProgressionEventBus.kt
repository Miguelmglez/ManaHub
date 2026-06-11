package com.mmg.manahub.core.gamification.domain

import com.mmg.manahub.core.gamification.domain.event.ProgressionEvent
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Application-wide bus carrying [ProgressionEvent]s from feature write-paths to the
 * gamification engine (ADR-002 §1).
 *
 * Features emit; the engine collects on an application-scope coroutine. There is **no
 * replay** — an event missed because the engine has not started yet is acceptable
 * (the ledger + Family-A backfill reconcile derivable progress on next launch). The
 * buffer absorbs short bursts; on overflow the OLDEST event is dropped rather than
 * suspending the emitter, so a slow consumer can never back-pressure a repository's
 * commit path.
 */
@Singleton
class ProgressionEventBus @Inject constructor() {

    private val _events = MutableSharedFlow<ProgressionEvent>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** Hot stream of progression events. The engine collects this. */
    val events: SharedFlow<ProgressionEvent> = _events.asSharedFlow()

    /**
     * Suspending emit. Preferred from the canonical write path: with [BufferOverflow.DROP_OLDEST]
     * this never actually suspends, but keeps the call site `suspend`-aware so future tuning
     * (e.g. switching overflow strategy) does not silently change semantics.
     */
    suspend fun emit(event: ProgressionEvent) {
        _events.emit(event)
    }

    /**
     * Non-suspending convenience emit for call sites that are not in a coroutine. Returns
     * `true` if the event was accepted into the buffer (always `true` here given the
     * DROP_OLDEST policy, but the result is surfaced for completeness).
     */
    fun tryEmit(event: ProgressionEvent): Boolean = _events.tryEmit(event)
}
