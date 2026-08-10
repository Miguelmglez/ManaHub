package com.mmg.manahub.core.gamification.engine

import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.gamification.domain.GamificationEngine
import com.mmg.manahub.core.gamification.domain.ProgressionEventBus
import com.mmg.manahub.core.gamification.domain.event.ProgressionEvent
import com.mmg.manahub.core.gamification.domain.model.ProcessedOutcome
import com.mmg.manahub.core.gamification.domain.model.ProgressionOutcome
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Default [GamificationEngine].
 *
 * Collects the [ProgressionEventBus] on an application-scope coroutine and processes each event on
 * [@DefaultDispatcher][DefaultDispatcher]. Each event is funnelled through [process]: XP grant first
 * (the idempotency gate), THEN the achievement evaluator (its tier unlocks + tier XP are folded into
 * the same [ProgressionOutcome]); quest/streak evaluators are still Phase-2 stubs.
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

    /** Guards against starting the collector more than once. */
    private val started = AtomicBoolean(false)

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
            val xpOutcome = runCatching { xpGranter.grant(event) }
                .onFailure { e -> reportStageFailure("gamification_xp_grant_failed", event, e) }
                .getOrDefault(ProgressionOutcome.none)

            // Achievement evaluation may unlock tiers (and grant per-tier XP via the ledger).
            val unlocks = runCatching { achievementEvaluator.process(event) }
                .onFailure { e -> reportStageFailure("gamification_achievement_eval_failed", event, e) }
                .getOrDefault(emptyList())

            // Quest evaluation advances active quest instances; its deltas are folded into the outcome
            // so the UI can surface "+1 toward <quest>" / "Quest complete!".
            val questDeltas = runCatching { questEvaluator.process(event) }
                .onFailure { e -> reportStageFailure("gamification_quest_eval_failed", event, e) }
                .getOrDefault(emptyList())

            // Streak tracking is a side effect (no UI payload in this chunk) — fire-and-forget.
            runCatching { streakTracker.process(event) }
                .onFailure { e -> reportStageFailure("gamification_streak_failed", event, e) }

            val combined = xpOutcome
                .withAchievementUnlocks(unlocks)
                .withQuestProgress(questDeltas)

            // Grant any newly-satisfied cosmetic entitlements (level-up / achievement unlock). A pure
            // side effect for now — the Rewards tab reads entitlements reactively and the level-up
            // celebration is DataStore-driven (lastCelebratedLevel). Per-event isolated so a failure
            // here never tears down progression for the event.
            runCatching { entitlementGranter.grant(combined) }
                .onFailure { e -> reportStageFailure("gamification_entitlement_grant_failed", event, e) }

            // Only publish outcomes that carry something the UI should surface.
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
        crashReporter.setCustomKey("gamification_event_type", event::class.simpleName ?: "Unknown")
        crashReporter.log(logEvent)
        crashReporter.recordException(e)
    }

    override fun start(scope: CoroutineScope) {
        if (!started.compareAndSet(false, true)) return
        bus.events
            .onEach { event -> process(event) }
            .flowOn(defaultDispatcher)
            .launchIn(scope)
    }
}
