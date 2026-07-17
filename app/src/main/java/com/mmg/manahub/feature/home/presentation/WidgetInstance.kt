package com.mmg.manahub.feature.home.presentation

import com.mmg.manahub.core.model.PersistedWidget
import com.mmg.manahub.core.model.WidgetSize

/**
 * A single placed widget on the dashboard: its [type] plus the chosen [size].
 *
 * The layout is an ordered list of these instances. A given [HomeWidgetType] can
 * appear at most once in a layout (the gallery enforces uniqueness), so
 * [HomeWidgetType.persistedId] is a stable list key.
 */
data class WidgetInstance(
    val type: HomeWidgetType,
    val size: WidgetSize,
)

/**
 * Maps this UI widget instance to its presentation-agnostic [PersistedWidget] for storage.
 */
fun WidgetInstance.toPersisted(): PersistedWidget =
    PersistedWidget(persistedId = type.persistedId, size = size)

/**
 * Resolves a stored [PersistedWidget] back to a [WidgetInstance], or null when its
 * persistedId no longer maps to a known [HomeWidgetType] (e.g. a widget removed in a newer
 * app version). Callers drop nulls so an unknown stored widget is silently skipped.
 */
fun PersistedWidget.toInstanceOrNull(): WidgetInstance? {
    val type = HomeWidgetType.fromPersistedId(persistedId) ?: return null
    return WidgetInstance(type = type, size = size)
}

/**
 * Maps a persisted layout to UI [WidgetInstance]s, migrating any legacy `social_hub` token
 * (Home widget board overhaul, TASK 5c — SOCIAL_HUB was split into FRIENDS + COMMUNITY_DECKS) into
 * both of its replacement widgets in place, then drops any other still-unrecognised id and
 * de-duplicates by [HomeWidgetType.persistedId].
 *
 * The expansion is idempotent and cheap to re-run on every decode: it only rewrites the
 * DataStore-persisted token once the user next mutates the layout (add/remove/move), since every
 * mutation path re-persists from the already-expanded in-memory list.
 */
fun List<PersistedWidget>.toInstancesWithMigration(): List<WidgetInstance> =
    flatMap { persisted ->
        if (persisted.persistedId == HomeWidgetType.LEGACY_SOCIAL_HUB_PERSISTED_ID) {
            listOf(
                WidgetInstance(HomeWidgetType.FRIENDS, persisted.size),
                WidgetInstance(HomeWidgetType.COMMUNITY_DECKS, persisted.size),
            )
        } else {
            listOfNotNull(persisted.toInstanceOrNull())
        }
    }.distinctBy { it.type.persistedId }
