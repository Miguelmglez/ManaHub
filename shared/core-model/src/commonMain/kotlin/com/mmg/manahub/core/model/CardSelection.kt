package com.mmg.manahub.core.model

/**
 * A single card entry inside a selection session (e.g. scanner queue, bulk add),
 * capturing all collection parameters chosen by the user.
 */
data class CardSelectionEntry(
    val card: Card,
    val quantity: Int,
    val isFoil: Boolean,
    val language: String,
    val condition: String,
    val setCode: String,
    val timestamp: Long,
    val id: String,
)

/**
 * Accumulates cards in a selection session.
 */
data class CardSelectionSession(
    val entries: List<CardSelectionEntry> = emptyList(),
)
