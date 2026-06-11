package com.mmg.manahub.feature.home.presentation

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
