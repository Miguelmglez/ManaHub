package com.mmg.manahub.core.model

/**
 * Display mode for the collection list.
 *
 * Moved to `:shared:core-model` (KMP migration, Phase 0 / Spike A) — pure Kotlin, no platform deps,
 * so it lives in `commonMain` and is shared by Android and Web.
 */
enum class CollectionViewMode {
    GRID,
    LIST,
    ;

    companion object {
        /** Resolves a persisted name back to the enum, defaulting to [GRID] for null/unknown values. */
        fun fromName(name: String?) = entries.find { it.name == name } ?: GRID
    }
}
