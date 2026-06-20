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
