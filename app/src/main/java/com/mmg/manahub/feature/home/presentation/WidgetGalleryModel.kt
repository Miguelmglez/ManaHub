package com.mmg.manahub.feature.home.presentation

/** Direction of a one-step reorder in the widget gallery. */
enum class GalleryMoveDirection(val step: Int) { UP(-1), DOWN(1) }

/**
 * The gallery's category order after [layout] changed: absent categories keep their slots and the
 * present categories' slots are refilled in layout order, so placed categories always read in board
 * order while nothing else moves.
 */
internal fun reconcileGalleryCategoryOrder(
    previous: List<WidgetCategory>,
    layout: List<WidgetInstance>,
): List<WidgetCategory> {
    val present = layout.map { it.type.category }.distinct()
    val presentSet = present.toSet()
    val refill = present.iterator()
    return (previous + WidgetCategory.entries).distinct().map { if (it in presentSet) refill.next() else it }
}

/**
 * The rows the gallery lists for [category]: placed widgets in layout order, then the unplaced ones
 * in declaration order. Hidden types ([isVisible] false) are never listed.
 */
internal fun galleryRowsFor(
    category: WidgetCategory,
    layout: List<WidgetInstance>,
    isVisible: (HomeWidgetType) -> Boolean,
): List<HomeWidgetType> {
    val placed = layout.map { it.type }.filter { it.category == category && isVisible(it) }.distinct()
    val placedSet = placed.toSet()
    val unplaced = HomeWidgetType.entries.filter { it.category == category && isVisible(it) && it !in placedSet }
    return placed + unplaced
}

/** The visible placed widget one step [direction] from [type] within its category, or null at that end. */
internal fun List<WidgetInstance>.galleryStepNeighbor(
    type: HomeWidgetType,
    direction: GalleryMoveDirection,
    isVisible: (HomeWidgetType) -> Boolean,
): HomeWidgetType? {
    val siblings = map { it.type }.filter { it.category == type.category && isVisible(it) }
    val position = siblings.indexOf(type)
    if (position < 0) return null
    return siblings.getOrNull(position + direction.step)
}

/**
 * Moves placed widget [type] past its visible neighbour in [direction], skipping hidden widgets in
 * between, so the category run stays contiguous. Null when there is no neighbour that way.
 */
internal fun List<WidgetInstance>.withWidgetStepped(
    type: HomeWidgetType,
    direction: GalleryMoveDirection,
    isVisible: (HomeWidgetType) -> Boolean,
): List<WidgetInstance>? {
    val neighbor = galleryStepNeighbor(type, direction, isVisible) ?: return null
    val from = indexOfFirst { it.type == type }
    val to = indexOfFirst { it.type == neighbor }
    return toMutableList().apply { add(to, removeAt(from)) }
}

/**
 * The visible category one step [direction] from [category], or null when there is none or it would
 * cross the pinned [WidgetCategory.ACTIVITY] block.
 */
internal fun galleryCategoryNeighbor(
    category: WidgetCategory,
    direction: GalleryMoveDirection,
    visibleCategories: List<WidgetCategory>,
): WidgetCategory? {
    if (category == WidgetCategory.ACTIVITY) return null
    val position = visibleCategories.indexOf(category)
    if (position < 0) return null
    return visibleCategories.getOrNull(position + direction.step)?.takeIf { it != WidgetCategory.ACTIVITY }
}

/** [category] moved past its visible neighbour in [direction]; null when the move is not allowed. */
internal fun List<WidgetCategory>.withCategoryStepped(
    category: WidgetCategory,
    direction: GalleryMoveDirection,
    visibleCategories: List<WidgetCategory>,
): List<WidgetCategory>? {
    val neighbor = galleryCategoryNeighbor(category, direction, visibleCategories) ?: return null
    val from = indexOf(category)
    val to = indexOf(neighbor)
    if (from < 0 || to < 0) return null
    return toMutableList().apply { add(to, removeAt(from)) }
}

/** The layout regrouped by [order], keeping each category's internal order (stable sort). */
internal fun List<WidgetInstance>.sortedByCategoryOrder(order: List<WidgetCategory>): List<WidgetInstance> =
    sortedBy { order.indexOf(it.type.category) }
