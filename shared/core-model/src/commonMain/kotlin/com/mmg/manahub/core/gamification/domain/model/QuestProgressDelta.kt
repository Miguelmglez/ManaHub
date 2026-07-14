package com.mmg.manahub.core.gamification.domain.model

/**
 * The change applied to ONE quest instance while processing a progression event (ADR-002, Phase 2).
 *
 * Returned by `QuestEvaluator.process` and folded into the [ProgressionOutcome] so Chunk B's UI can
 * surface "+1 toward <quest>" / "Quest complete!" feedback. Title is inline English text (no Android
 * string resources) to keep the domain layer platform-agnostic.
 *
 * @param instanceId the persisted quest instance id (`{templateId}:{periodKey}`).
 * @param templateId the catalog template id.
 * @param title English title text.
 * @param emoji glyph for the UI.
 * @param newProgress the progress AFTER this event (clamped to [target]).
 * @param target the completion target.
 * @param justCompleted true if THIS event is the one that pushed the quest to COMPLETED.
 */
data class QuestProgressDelta(
    val instanceId: String,
    val templateId: String,
    val title: String,
    val emoji: String,
    val newProgress: Int,
    val target: Int,
    val justCompleted: Boolean,
)
