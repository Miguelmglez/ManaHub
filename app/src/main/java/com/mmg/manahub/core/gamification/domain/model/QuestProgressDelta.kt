package com.mmg.manahub.core.gamification.domain.model

import androidx.annotation.StringRes

/**
 * The change applied to ONE quest instance while processing a progression event (ADR-002, Phase 2).
 *
 * Returned by `QuestEvaluator.process` and folded into the [ProgressionOutcome] so Chunk B's UI can
 * surface "+1 toward <quest>" / "Quest complete!" feedback. Title is a string resource (resolved in the
 * Composable) to keep the domain layer Context-free.
 *
 * @param instanceId the persisted quest instance id (`{templateId}:{periodKey}`).
 * @param templateId the catalog template id.
 * @param titleRes English title string resource.
 * @param emoji glyph for the UI.
 * @param newProgress the progress AFTER this event (clamped to [target]).
 * @param target the completion target.
 * @param justCompleted true if THIS event is the one that pushed the quest to COMPLETED.
 */
data class QuestProgressDelta(
    val instanceId: String,
    val templateId: String,
    @StringRes val titleRes: Int,
    val emoji: String,
    val newProgress: Int,
    val target: Int,
    val justCompleted: Boolean,
)
