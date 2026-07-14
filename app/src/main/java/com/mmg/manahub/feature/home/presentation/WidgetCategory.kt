package com.mmg.manahub.feature.home.presentation

/**
 * Grouping used to organise widgets in the gallery. Purely a presentation
 * concern — it has no effect on which data a widget reads.
 *
 * [TOURNAMENT] is currently UNUSED — no [HomeWidgetType] is assigned this category (tournament
 * summary data now lives inside `SOCIAL_HUB`'s `ActiveTournament` slide). Kept (not removed)
 * because [WidgetGallerySheet]'s exhaustive `when` branches (display name/icon) still reference it
 * defensively; a future dedicated tournament widget can claim it without a new enum entry.
 */
enum class WidgetCategory { ACTIVITY, STATS, COLLECTION, DISCOVER, SOCIAL, TOURNAMENT, COMMUNITY }
