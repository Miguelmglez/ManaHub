package com.mmg.manahub.core.model

/**
 * Persistence-layer representation of a single placed Home dashboard widget.
 *
 * Decouples the [com.mmg.manahub.core.data.local.UserPreferencesDataStore] from the
 * presentation-layer `HomeWidgetType` / `WidgetInstance` (which carry Compose icons and
 * Android string resources). The DataStore reads/writes the stable [persistedId] string
 * and the [size]; the presentation layer maps [PersistedWidget] to/from its UI widget
 * instance (KMP modularization, Phase 0.5 Blocker 2).
 */
data class PersistedWidget(
    val persistedId: String,
    val size: WidgetSize,
)
