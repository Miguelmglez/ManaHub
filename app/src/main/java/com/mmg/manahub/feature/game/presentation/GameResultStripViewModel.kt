package com.mmg.manahub.feature.game.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.gamification.domain.GamificationEngine
import com.mmg.manahub.core.gamification.domain.event.ProgressionEvent
import com.mmg.manahub.core.gamification.domain.model.ProgressionOutcome
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

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
 * Suppressed by the master toggle: if gamification is disabled, [outcome] stays null and the strip
 * never appears.
 *
 * Usage: call [observe] once (e.g. from a `LaunchedEffect(sessionId)`); read [outcome].
 */
@HiltViewModel
class GameResultStripViewModel @Inject constructor(
    private val engine: GamificationEngine,
    private val userPreferencesDataStore: UserPreferencesDataStore,
) : ViewModel() {

    private val _outcome = MutableStateFlow<ProgressionOutcome?>(null)

    /** The progression outcome for the observed session, or null until it arrives / when disabled. */
    val outcome: StateFlow<ProgressionOutcome?> = _outcome.asStateFlow()

    private var started = false

    /**
     * Begins listening for the outcome of [sessionId]. Idempotent: the first call wins, so it is safe
     * to invoke from a `LaunchedEffect`. A non-positive [sessionId] (game not yet saved) is ignored.
     */
    fun observe(sessionId: Long) {
        if (started || sessionId <= 0L) return
        started = true
        viewModelScope.launch {
            // Master toggle: when disabled, never surface a strip.
            val enabled = runCatching {
                userPreferencesDataStore.gamificationEnabledFlow.catch { emit(false) }.first()
            }.getOrDefault(false)
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
