package com.mmg.manahub.feature.home.presentation

import com.mmg.manahub.core.model.WidgetSize
import com.mmg.manahub.feature.home.presentation.GalleryMoveDirection.DOWN
import com.mmg.manahub.feature.home.presentation.GalleryMoveDirection.UP
import com.mmg.manahub.feature.home.presentation.HomeWidgetType.CARD_OF_THE_DAY
import com.mmg.manahub.feature.home.presentation.HomeWidgetType.CONTEXT_HERO
import com.mmg.manahub.feature.home.presentation.HomeWidgetType.DAILY_PUZZLE
import com.mmg.manahub.feature.home.presentation.HomeWidgetType.DISCOVER_CARDS
import com.mmg.manahub.feature.home.presentation.HomeWidgetType.FRIENDS
import com.mmg.manahub.feature.home.presentation.HomeWidgetType.GAME_STATS_HUB
import com.mmg.manahub.feature.home.presentation.HomeWidgetType.LATEST_SETS
import com.mmg.manahub.feature.home.presentation.HomeWidgetType.MTG_NEWS
import com.mmg.manahub.feature.home.presentation.HomeWidgetType.RECENTLY_ADDED
import com.mmg.manahub.feature.home.presentation.HomeWidgetType.YOUR_DECKS_SHELF
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Pure reorder / grouping rules behind [WidgetGallerySheet]. */
class WidgetGalleryModelTest {

    private val all: (HomeWidgetType) -> Boolean = { true }

    private fun layoutOf(vararg types: HomeWidgetType) = types.map { WidgetInstance(it, WidgetSize.MEDIUM) }

    @Test
    fun `rows list placed widgets in layout order then unplaced in declaration order`() {
        val layout = layoutOf(MTG_NEWS, DISCOVER_CARDS)

        val rows = galleryRowsFor(WidgetCategory.DISCOVER, layout, all)

        assertEquals(listOf(MTG_NEWS, DISCOVER_CARDS, CARD_OF_THE_DAY, LATEST_SETS), rows.take(4))
        assertEquals(HomeWidgetType.entries.count { it.category == WidgetCategory.DISCOVER }, rows.size)
    }

    @Test
    fun `hidden types are never listed`() {
        val rows = galleryRowsFor(WidgetCategory.DISCOVER, layoutOf(DAILY_PUZZLE), isVisible = { it != DAILY_PUZZLE })

        assertEquals(false, DAILY_PUZZLE in rows)
    }

    @Test
    fun `stepping down skips hidden widgets and keeps the category contiguous`() {
        val layout = layoutOf(CONTEXT_HERO, MTG_NEWS, DAILY_PUZZLE, DISCOVER_CARDS, FRIENDS)

        val moved = layout.withWidgetStepped(MTG_NEWS, DOWN, isVisible = { it != DAILY_PUZZLE })

        assertEquals(layoutOf(CONTEXT_HERO, DAILY_PUZZLE, DISCOVER_CARDS, MTG_NEWS, FRIENDS), moved)
    }

    @Test
    fun `stepping up moves before the visible neighbour`() {
        val layout = layoutOf(MTG_NEWS, DISCOVER_CARDS, LATEST_SETS)

        assertEquals(layoutOf(MTG_NEWS, LATEST_SETS, DISCOVER_CARDS), layout.withWidgetStepped(LATEST_SETS, UP, all))
    }

    @Test
    fun `a widget never steps out of its category`() {
        val layout = layoutOf(YOUR_DECKS_SHELF, MTG_NEWS)

        assertNull(layout.withWidgetStepped(YOUR_DECKS_SHELF, DOWN, all))
        assertNull(layout.withWidgetStepped(MTG_NEWS, UP, all))
    }

    @Test
    fun `adding a widget of an absent category moves only that category after the placed ones`() {
        val previous = listOf(
            WidgetCategory.ACTIVITY, WidgetCategory.SOCIAL, WidgetCategory.STATS,
            WidgetCategory.COLLECTION, WidgetCategory.DISCOVER, WidgetCategory.TOURNAMENT, WidgetCategory.COMMUNITY,
        )
        val layout = layoutOf(CONTEXT_HERO, GAME_STATS_HUB, RECENTLY_ADDED).withWidgetAdded(FRIENDS)

        val order = reconcileGalleryCategoryOrder(previous, layout)

        assertEquals(
            listOf(
                WidgetCategory.ACTIVITY, WidgetCategory.STATS, WidgetCategory.COLLECTION,
                WidgetCategory.SOCIAL, WidgetCategory.DISCOVER, WidgetCategory.TOURNAMENT, WidgetCategory.COMMUNITY,
            ),
            order,
        )
    }

    @Test
    fun `removing the last widget of a category leaves every category in place`() {
        val previous = listOf(
            WidgetCategory.ACTIVITY, WidgetCategory.COLLECTION, WidgetCategory.STATS,
            WidgetCategory.DISCOVER, WidgetCategory.SOCIAL, WidgetCategory.TOURNAMENT, WidgetCategory.COMMUNITY,
        )

        val order = reconcileGalleryCategoryOrder(previous, layoutOf(CONTEXT_HERO, GAME_STATS_HUB))

        assertEquals(previous, order)
    }

    @Test
    fun `categories step among visible ones and never cross the pinned activity block`() {
        val visible = listOf(WidgetCategory.ACTIVITY, WidgetCategory.STATS, WidgetCategory.COLLECTION)
        val order = visible + listOf(WidgetCategory.DISCOVER)

        assertNull(order.withCategoryStepped(WidgetCategory.STATS, UP, visible))
        assertNull(order.withCategoryStepped(WidgetCategory.ACTIVITY, DOWN, visible))
        assertEquals(
            listOf(WidgetCategory.ACTIVITY, WidgetCategory.COLLECTION, WidgetCategory.STATS, WidgetCategory.DISCOVER),
            order.withCategoryStepped(WidgetCategory.STATS, DOWN, visible),
        )
    }

    @Test
    fun `regrouping by category order keeps each category's internal order`() {
        val layout = layoutOf(CONTEXT_HERO, RECENTLY_ADDED, YOUR_DECKS_SHELF, GAME_STATS_HUB)
        val order = listOf(WidgetCategory.ACTIVITY, WidgetCategory.STATS, WidgetCategory.COLLECTION)

        assertEquals(
            layoutOf(CONTEXT_HERO, GAME_STATS_HUB, RECENTLY_ADDED, YOUR_DECKS_SHELF),
            layout.sortedByCategoryOrder(order),
        )
    }
}
