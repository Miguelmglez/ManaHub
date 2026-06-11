package com.mmg.manahub.core.gamification.engine

import com.mmg.manahub.core.di.DefaultDispatcher
import com.mmg.manahub.core.gamification.domain.GamificationEngine
import com.mmg.manahub.core.gamification.domain.ProgressionEventBus
import com.mmg.manahub.core.gamification.domain.event.ProgressionEvent
import com.mmg.manahub.core.gamification.domain.model.ProgressionOutcome
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Default [GamificationEngine].
 *
 * Collects the [ProgressionEventBus] on an application-scope coroutine and processes each event on
 * [@DefaultDispatcher][DefaultDispatcher] (CPU-bound; DAO calls inside are themselves IO-bound but
 * the orchestration/cap math is cheap and stays off the main thread). Each event is funnelled
 * through [process]: XP grant first (the idempotency gate), then the stubbed achievement / quest /
 * streak evaluators.
 *
 * Processing of one event never crashes the collector: failures are isolated per event so one bad
 * event cannot tear down progression for the whole session.
 */
@Singleton
class GamificationEngineImpl @Inject constructor(
    private val bus: ProgressionEventBus,
    private val xpGranter: XpGranter,
    private val achievementEvaluator: AchievementEvaluator,
    private val questEvaluator: QuestEvaluator,
    private val streakTracker: StreakTracker,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : GamificationEngine {

    /** Guards against starting the collector more than once. */
    private val started = AtomicBoolean(false)

    override suspend fun process(event: ProgressionEvent): ProgressionOutcome =
        withContext(defaultDispatcher) {
            // XP grant is the idempotency gate (ledger UNIQUE key). Always run it first.
            val outcome = runCatching { xpGranter.grant(event) }
                .getOrDefault(ProgressionOutcome.none)

            // Stubbed in Phase 0 — wiring is complete so Phase 1/2 only fills the bodies.
            runCatching { achievementEvaluator.process(event) }
            runCatching { questEvaluator.process(event) }
            runCatching { streakTracker.process(event) }

            outcome
        }

    override fun start(scope: CoroutineScope) {
        if (!started.compareAndSet(false, true)) return
        bus.events
            .onEach { event -> process(event) }
            .flowOn(defaultDispatcher)
            .launchIn(scope)
    }
}
