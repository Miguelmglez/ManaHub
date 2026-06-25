package com.mmg.manahub.core.gamification.domain.catalog

import com.mmg.manahub.core.gamification.domain.QuestPeriod
import com.mmg.manahub.core.gamification.domain.QuestWeightClass
import com.mmg.manahub.core.gamification.domain.event.ProgressionEvent
import kotlin.reflect.KClass

/**
 * Static definition of one quest template (ADR-002, Phase 2).
 *
 * The catalog ([com.mmg.manahub.core.gamification.domain.catalog.QuestCatalog]) is a code-side list
 * of these. A concrete quest instance entity is generated per period from a template
 * (id = `{templateId}:{periodKey}`).
 *
 * ### Pure int counters (deliberate simplification)
 * Every quest is a monotonic integer counter: [advance] returns the progress increment for an event
 * (0 when the event does not apply). There are intentionally NO derived/distinct-mode quests in v1 —
 * keeping the int-progress model clean. Conditional quests (win/multiplayer/featureKey) return 1 or 0;
 * count-based quests return the event's count.
 *
 * @param id STABLE template id (part of the persisted instance PK — NEVER rename).
 * @param period whether this quest rotates daily or weekly.
 * @param weightClass difficulty band, used by the generator's balance rules.
 * @param target value to complete the quest.
 * @param xpReward XP granted on claim.
 * @param title English title text (inline, no Android string resources).
 * @param description English description text (inline, no Android string resources).
 * @param emoji glyph for the UI.
 * @param reactsTo event classes that can advance this quest.
 * @param advance pure mapping from a matching event to its progress increment (>= 0).
 */
data class QuestTemplate(
    val id: String,
    val period: QuestPeriod,
    val weightClass: QuestWeightClass,
    val target: Int,
    val xpReward: Int,
    val title: String,
    val description: String,
    val emoji: String,
    val reactsTo: Set<KClass<out ProgressionEvent>>,
    val advance: (ProgressionEvent) -> Int,
) {
    init {
        require(target > 0) { "Quest '$id' target must be positive" }
        require(reactsTo.isNotEmpty()) { "Quest '$id' must react to at least one event" }
    }
}
