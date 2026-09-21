package com.mmg.manahub.core.model

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * One entry of the shared card queue (Scanner + AddCard "Select multiple"), carrying the
 * collection attributes the card will be committed with.
 *
 * @property id Stable identity for edit/remove/duplicate/partial-retry; [timestamp] alone collides
 *   when two entries share a millisecond.
 */
data class QueuedCard(
    val card: Card,
    val quantity: Int,
    val isFoil: Boolean,
    val language: String,
    val condition: String,
    val setCode: String,
    val timestamp: Long,
    val id: String = newQueuedCardId(),
)

/** Generates a fresh random [QueuedCard.id]. */
@OptIn(ExperimentalUuidApi::class)
fun newQueuedCardId(): String = Uuid.random().toString()

/** True when [other] would be merged into this entry instead of queued as a separate row. */
fun QueuedCard.hasSameAttributesAs(other: QueuedCard): Boolean =
    card.scryfallId == other.card.scryfallId &&
        isFoil == other.isFoil &&
        language == other.language &&
        condition == other.condition
