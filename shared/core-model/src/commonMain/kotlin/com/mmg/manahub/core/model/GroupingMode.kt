package com.mmg.manahub.core.model

/**
 * How cards in a deck/collection view are grouped.
 *
 * Moved to `:shared:core-model` (KMP migration, Phase 0 / Spike A) — pure Kotlin, no platform deps,
 * so it lives in `commonMain` and is shared by Android and Web.
 */
enum class GroupingMode { TYPE, COLOR, COST, TAG }
