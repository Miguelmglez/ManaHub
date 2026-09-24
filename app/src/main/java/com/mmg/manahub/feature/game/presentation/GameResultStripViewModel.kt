package com.mmg.manahub.feature.game.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.manahub.core.gamification.domain.GamificationAvailability
import com.mmg.manahub.core.gamification.domain.GamificationEngine
import com.mmg.manahub.core.gamification.domain.event.ProgressionEvent
import com.mmg.manahub.core.gamification.domain.model.ProgressionOutcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Supplies the [ProgressionOutcome] for the game the [GameResultScreen] is displaying (ADR-002 §8.3,
 * Phase 1, Chunk B).
 *
 * The `GameFinished` event is processed asynchronously by the engine AFTER `saveGameSession` returns
 * the session id, so the outcome arrives a moment after the screen mounts. This ViewModel subscribes
 * to [GamificationEngine.outcomes] and exposes the FIRST outcome whose source event is a
 * `GameFinished` for the shown session id — correlated by `sessionId`, never by name or by "the most
 * recent outcome" (multiple games could be in flight). The strip renders nothing until [outcome]
 * becomes non-null, so the result screen is never blocked.
 *
 * Suppressed by [GamificationAvailability]: when gamification is unavailable, [outcome] stays null
 * and the strip never appears.
 *
 * Usage: call [observe] once (e.g. from a `LaunchedEffect(sessionId)`); read [outcome].
 */
class GameResultStripViewModel(
    private val engine: GamificationEngine,
    private val gamificationAvailability: GamificationAvailability,
) : ViewModel() {

    private val _outcome = MutableStateFlow<ProgressionOutcome?>(null)

    /** The progression outcome for the observed session, or null until it arrives / when disabled. */
    val outcome: StateFlow<ProgressionOutcome?> = _outcome.asStateFlow()

    /** Session currently being observed; a different id restarts the collector. */
    private var observedSessionId: Long? = null
    private var observeJob: Job? = null

    /**
     * Begins listening for the outcome of [sessionId]. Idempotent per session, so it is safe to
     * invoke from a `LaunchedEffect`; a DIFFERENT id cancels the previous collector and clears the
     * stale outcome (this ViewModel survives a second game in the same back-stack entry). A
     * non-positive [sessionId] (game not yet saved) is ignored.
     */
    fun observe(sessionId: Long) {
        if (sessionId <= 0L || observedSessionId == sessionId) return
        observedSessionId = sessionId
        observeJob?.cancel()
        _outcome.value = null
        observeJob = viewModelScope.launch {
            // Master toggle: when disabled, never surface a strip.
            val enabled = runCatching { gamificationAvailability.availableFlow.first() }.getOrDefault(false)
            if (!enabled) return@launch

            // Suspend until the first outcome for this session arrives (replay buffer means an
            // outcome processed just before we subscribed is still delivered), then publish it. Using
            // first{} terminates the collection automatically after the match.
            val matched = runCatching {
                engine.outcomes.first { processed ->
                    val event = processed.sourceEvent
                    event is ProgressionEvent.GameFinished && event.sessionId == sessionId
                }
            }.getOrNull()
            if (matched != null) {
                _outcome.update { matched.outcome }
            }
        }
    }
}
