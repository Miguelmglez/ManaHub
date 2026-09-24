package com.mmg.manahub.core.gamification.engine

import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.gamification.domain.GamificationEngine
import com.mmg.manahub.core.gamification.domain.ProgressionEventBus
import com.mmg.manahub.core.gamification.domain.event.ProgressionEvent
import com.mmg.manahub.core.gamification.domain.model.ProcessedOutcome
import com.mmg.manahub.core.gamification.domain.model.ProgressionOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext

/**
 * Default [GamificationEngine].
 *
 * Collects the [ProgressionEventBus] while started (see [start]) and processes each event on
 * [defaultDispatcher]. Each event is funnelled through [process] in a fixed stage order: XP grant (the
 * idempotency gate) → streak → achievements → quests → entitlements, all folded into one
 * [ProgressionOutcome]. A ledger duplicate skips the COUNTER achievements and the quest stage.
 *
 * The combined outcome is published on [outcomes] (paired with its source event) so Chunk B's
 * GameResult strip can correlate it. Processing of one event never crashes the collector: failures are
 * isolated per event so one bad event cannot tear down progression for the whole session.
 */
class GamificationEngineImpl(
    private val bus: ProgressionEventBus,
    private val xpGranter: XpGranter,
    private val achievementEvaluator: AchievementEvaluator,
    private val questEvaluator: QuestEvaluator,
    private val streakTracker: StreakTracker,
    private val entitlementGranter: EntitlementGranter,
    private val defaultDispatcher: CoroutineDispatcher,
    private val crashReporter: CrashReporter,
) : GamificationEngine {

    private val startLock = Any()

    /** The live bus collector; replaced only once the previous one is no longer active. */
    private var collectorJob: Job? = null

    // Small replay so a screen subscribing right after its event was processed still sees it.
    private val _outcomes = MutableSharedFlow<ProcessedOutcome>(
        replay = 8,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val outcomes: SharedFlow<ProcessedOutcome> = _outcomes.asSharedFlow()

    override suspend fun process(event: ProgressionEvent): ProgressionOutcome =
        withContext(defaultDispatcher) {
            // XP grant is the idempotency gate (ledger UNIQUE key). Always run it first.
            val xp = runCatching { xpGranter.grant(event) }
                .onFailure { e -> reportStageFailure("gamification_xp_grant_failed", event, e) }
                .getOrDefault(XpGrantResult.NoXp)
            // A replayed event must not advance counters or quests a second time (D10).
            val isReplay = xp is XpGrantResult.Duplicate

            // Before achievements, so STREAK_* defs read today's streak (G-12). Idempotent per day.
            runCatching { streakTracker.process(event) }
                .onFailure { e -> reportStageFailure("gamification_streak_failed", event, e) }

            val unlocks = runCatching { achievementEvaluator.process(event, includeCounters = !isReplay) }
                .onFailure { e -> reportStageFailure("gamification_achievement_eval_failed", event, e) }
                .getOrDefault(emptyList())

            val questDeltas = if (isReplay) {
                emptyList()
            } else {
                runCatching { questEvaluator.process(event) }
                    .onFailure { e -> reportStageFailure("gamification_quest_eval_failed", event, e) }
                    .getOrDefault(emptyList())
            }

            val combined = xp.outcome
                .withAchievementUnlocks(unlocks)
                .withQuestProgress(questDeltas)

            // Last: needs the level-up and the unlocks produced above.
            runCatching { entitlementGranter.grant(combined) }
                .onFailure { e -> reportStageFailure("gamification_entitlement_grant_failed", event, e) }

            if (combined.hasAnything) {
                _outcomes.emit(ProcessedOutcome(sourceEvent = event, outcome = combined))
            }
            combined
        }

    /**
     * Records a per-stage processing failure via the injected [CrashReporter] — this engine had ZERO
     * telemetry anywhere before the 2026-08-06 audit (pre-existing gap, first exposed by Daily
     * Puzzle's new [ProgressionEvent.PuzzleSolved] event flowing through it). Each `runCatching` above
     * already isolates the failure so one bad stage never tears down the rest of [process]; this only
     * adds visibility on top, it changes no control flow.
     */
    private fun reportStageFailure(logEvent: String, event: ProgressionEvent, e: Throwable) {
        // Stopping the engine cancels an in-flight event; that must abort it, not be reported.
        if (e is CancellationException) throw e
        crashReporter.setCustomKey("gamification_event_type", event::class.simpleName ?: "Unknown")
        crashReporter.log(logEvent)
        crashReporter.recordException(e)
    }

    override fun start(scope: CoroutineScope): Job = synchronized(startLock) {
        collectorJob?.takeIf { it.isActive }?.let { return it }
        // UNDISPATCHED runs up to the bus subscription before returning, so the next emit is never dropped.
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            bus.events.collect { event -> process(event) }
        }.also { collectorJob = it }
    }
}
