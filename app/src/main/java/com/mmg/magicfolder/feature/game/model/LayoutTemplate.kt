package com.mmg.magicfolder.feature.game.model

// ─────────────────────────────────────────────────────────────────────────────
//  Grid slot position — determines default rotation
// ─────────────────────────────────────────────────────────────────────────────

enum class GridSlotPosition {
    BOTTOM,              // bottom row, text normal (0°)
    TOP,                 // top row, text rotated 180°
    LEFT,                // left column, text rotated 90°
    RIGHT,               // right column, text rotated 270°
    FULL_WIDTH_BOTTOM,   // full-width bottom, normal
    FULL_WIDTH_TOP,      // full-width top, rotated 180°
}

fun GridSlotPosition?.toDefaultDegrees(): Int = when (this) {
    GridSlotPosition.TOP,
    GridSlotPosition.FULL_WIDTH_TOP -> 180
    GridSlotPosition.LEFT           -> 90
    GridSlotPosition.RIGHT          -> 270
    else                            -> 0
}

// ─────────────────────────────────────────────────────────────────────────────
//  PlayerSlot — one seat in the layout grid
// ─────────────────────────────────────────────────────────────────────────────

data class PlayerSlot(
    val playerId: Int,
    val position: GridSlotPosition,
    val rowSpan:  Int = 1,
    val colSpan:  Int = 1,
)

// ─────────────────────────────────────────────────────────────────────────────
//  LayoutTemplate — named arrangement of seats for N players
// ─────────────────────────────────────────────────────────────────────────────

data class LayoutTemplate(
    val id:          String,
    val name:        String,
    val playerCount: Int,
    val slots:       List<PlayerSlot>,
    /** Rows of slot indices (into slots list), top to bottom. null = empty cell. */
    val gridRows:    List<List<Int?>>,
)

// ─────────────────────────────────────────────────────────────────────────────
//  LayoutTemplates — catalogue of built-in templates
// ─────────────────────────────────────────────────────────────────────────────

object LayoutTemplates {

    // ─── 2 players ───────────────────────────────────────────────────────────

    val TWO_TOP_BOTTOM = LayoutTemplate(
        id = "2_top_bottom", name = "Top / Bottom", playerCount = 2,
        slots = listOf(
            PlayerSlot(0, GridSlotPosition.TOP),
            PlayerSlot(1, GridSlotPosition.BOTTOM),
        ),
        gridRows = listOf(listOf(0), listOf(1)),
    )

    val TWO_SIDE_BY_SIDE = LayoutTemplate(
        id = "2_side", name = "Side by Side", playerCount = 2,
        slots = listOf(
            PlayerSlot(0, GridSlotPosition.LEFT),
            PlayerSlot(1, GridSlotPosition.RIGHT),
        ),
        gridRows = listOf(listOf(0, 1)),
    )

    val layoutsFor2 = listOf(TWO_TOP_BOTTOM, TWO_SIDE_BY_SIDE)

    // ─── 3 players ───────────────────────────────────────────────────────────

    val THREE_U_SHAPE = LayoutTemplate(
        id = "3_u", name = "U Shape", playerCount = 3,
        slots = listOf(
            PlayerSlot(0, GridSlotPosition.TOP),
            PlayerSlot(1, GridSlotPosition.TOP),
            PlayerSlot(2, GridSlotPosition.FULL_WIDTH_BOTTOM),
        ),
        gridRows = listOf(listOf(0, 1), listOf(2)),
    )

    val THREE_ONE_TOP = LayoutTemplate(
        id = "3_one_top", name = "1 Top, 2 Bottom", playerCount = 3,
        slots = listOf(
            PlayerSlot(0, GridSlotPosition.FULL_WIDTH_TOP),
            PlayerSlot(1, GridSlotPosition.BOTTOM),
            PlayerSlot(2, GridSlotPosition.BOTTOM),
        ),
        gridRows = listOf(listOf(0), listOf(1, 2)),
    )

    val THREE_TRIANGLE = LayoutTemplate(
        id = "3_triangle", name = "Triangle", playerCount = 3,
        slots = listOf(
            PlayerSlot(0, GridSlotPosition.LEFT),
            PlayerSlot(1, GridSlotPosition.RIGHT),
            PlayerSlot(2, GridSlotPosition.FULL_WIDTH_BOTTOM),
        ),
        gridRows = listOf(listOf(0, 1), listOf(2)),
    )

    val layoutsFor3 = listOf(THREE_U_SHAPE, THREE_ONE_TOP, THREE_TRIANGLE)

    // ─── 4 players ───────────────────────────────────────────────────────────

    val FOUR_GRID = LayoutTemplate(
        id = "4_grid", name = "2×2 Grid", playerCount = 4,
        slots = listOf(
            PlayerSlot(0, GridSlotPosition.TOP),
            PlayerSlot(1, GridSlotPosition.TOP),
            PlayerSlot(2, GridSlotPosition.BOTTOM),
            PlayerSlot(3, GridSlotPosition.BOTTOM),
        ),
        gridRows = listOf(listOf(0, 1), listOf(2, 3)),
    )

    val FOUR_COMPASS = LayoutTemplate(
        id = "4_compass", name = "4 Sides", playerCount = 4,
        slots = listOf(
            PlayerSlot(0, GridSlotPosition.FULL_WIDTH_TOP),
            PlayerSlot(1, GridSlotPosition.LEFT),
            PlayerSlot(2, GridSlotPosition.RIGHT),
            PlayerSlot(3, GridSlotPosition.FULL_WIDTH_BOTTOM),
        ),
        gridRows = listOf(listOf(0), listOf(1, 2), listOf(3)),
    )

    val FOUR_ONE_THREE = LayoutTemplate(
        id = "4_one_three", name = "1 Top, 3 Bottom", playerCount = 4,
        slots = listOf(
            PlayerSlot(0, GridSlotPosition.FULL_WIDTH_TOP),
            PlayerSlot(1, GridSlotPosition.BOTTOM),
            PlayerSlot(2, GridSlotPosition.BOTTOM),
            PlayerSlot(3, GridSlotPosition.BOTTOM),
        ),
        gridRows = listOf(listOf(0), listOf(1, 2, 3)),
    )

    val layoutsFor4 = listOf(FOUR_GRID, FOUR_COMPASS, FOUR_ONE_THREE)

    // ─── 5 players ───────────────────────────────────────────────────────────

    val FIVE_THREE_TWO = LayoutTemplate(
        id = "5_three_two", name = "3 Top, 2 Bottom", playerCount = 5,
        slots = listOf(
            PlayerSlot(0, GridSlotPosition.TOP),
            PlayerSlot(1, GridSlotPosition.TOP),
            PlayerSlot(2, GridSlotPosition.TOP),
            PlayerSlot(3, GridSlotPosition.BOTTOM),
            PlayerSlot(4, GridSlotPosition.BOTTOM),
        ),
        gridRows = listOf(listOf(0, 1, 2), listOf(3, 4)),
    )

    val FIVE_TWO_THREE = LayoutTemplate(
        id = "5_two_three", name = "2 Top, 3 Bottom", playerCount = 5,
        slots = listOf(
            PlayerSlot(0, GridSlotPosition.TOP),
            PlayerSlot(1, GridSlotPosition.TOP),
            PlayerSlot(2, GridSlotPosition.BOTTOM),
            PlayerSlot(3, GridSlotPosition.BOTTOM),
            PlayerSlot(4, GridSlotPosition.BOTTOM),
        ),
        gridRows = listOf(listOf(0, 1), listOf(2, 3, 4)),
    )

    val FIVE_SIDES_BOTTOM = LayoutTemplate(
        id = "5_sides_bottom", name = "2+2 Sides, 1 Bottom", playerCount = 5,
        slots = listOf(
            PlayerSlot(0, GridSlotPosition.LEFT),
            PlayerSlot(1, GridSlotPosition.RIGHT),
            PlayerSlot(2, GridSlotPosition.LEFT),
            PlayerSlot(3, GridSlotPosition.RIGHT),
            PlayerSlot(4, GridSlotPosition.FULL_WIDTH_BOTTOM),
        ),
        gridRows = listOf(listOf(0, 1), listOf(2, 3), listOf(4)),
    )

    val FIVE_CROSS = LayoutTemplate(
        id = "5_cross", name = "Cross", playerCount = 5,
        slots = listOf(
            PlayerSlot(0, GridSlotPosition.FULL_WIDTH_TOP),
            PlayerSlot(1, GridSlotPosition.LEFT),
            PlayerSlot(2, GridSlotPosition.RIGHT),
            PlayerSlot(3, GridSlotPosition.BOTTOM),
            PlayerSlot(4, GridSlotPosition.BOTTOM),
        ),
        gridRows = listOf(listOf(0), listOf(1, 2), listOf(3, 4)),
    )

    val layoutsFor5 = listOf(FIVE_THREE_TWO, FIVE_TWO_THREE, FIVE_SIDES_BOTTOM, FIVE_CROSS)

    // ─── 6 players ───────────────────────────────────────────────────────────

    val SIX_THREE_THREE = LayoutTemplate(
        id = "6_three_three", name = "3×2 Grid", playerCount = 6,
        slots = listOf(
            PlayerSlot(0, GridSlotPosition.TOP),
            PlayerSlot(1, GridSlotPosition.TOP),
            PlayerSlot(2, GridSlotPosition.TOP),
            PlayerSlot(3, GridSlotPosition.BOTTOM),
            PlayerSlot(4, GridSlotPosition.BOTTOM),
            PlayerSlot(5, GridSlotPosition.BOTTOM),
        ),
        gridRows = listOf(listOf(0, 1, 2), listOf(3, 4, 5)),
    )

    val SIX_FULL_TABLE = LayoutTemplate(
        id = "6_full_table", name = "Full Table", playerCount = 6,
        slots = listOf(
            PlayerSlot(0, GridSlotPosition.TOP),
            PlayerSlot(1, GridSlotPosition.TOP),
            PlayerSlot(2, GridSlotPosition.LEFT),
            PlayerSlot(3, GridSlotPosition.RIGHT),
            PlayerSlot(4, GridSlotPosition.BOTTOM),
            PlayerSlot(5, GridSlotPosition.BOTTOM),
        ),
        gridRows = listOf(listOf(0, 1), listOf(2, 3), listOf(4, 5)),
    )

    val SIX_LONG_TABLE = LayoutTemplate(
        id = "6_long_table", name = "Long Table", playerCount = 6,
        slots = listOf(
            PlayerSlot(0, GridSlotPosition.FULL_WIDTH_TOP),
            PlayerSlot(1, GridSlotPosition.LEFT),
            PlayerSlot(2, GridSlotPosition.RIGHT),
            PlayerSlot(3, GridSlotPosition.LEFT),
            PlayerSlot(4, GridSlotPosition.RIGHT),
            PlayerSlot(5, GridSlotPosition.FULL_WIDTH_BOTTOM),
        ),
        gridRows = listOf(listOf(0), listOf(1, 2), listOf(3, 4), listOf(5)),
    )

    val SIX_TWO_ROWS = LayoutTemplate(
        id = "6_two_rows", name = "2 Rows of 3", playerCount = 6,
        slots = listOf(
            PlayerSlot(0, GridSlotPosition.TOP),
            PlayerSlot(1, GridSlotPosition.TOP),
            PlayerSlot(2, GridSlotPosition.TOP),
            PlayerSlot(3, GridSlotPosition.BOTTOM),
            PlayerSlot(4, GridSlotPosition.BOTTOM),
            PlayerSlot(5, GridSlotPosition.BOTTOM),
        ),
        gridRows = listOf(listOf(0, 1, 2), listOf(3, 4, 5)),
    )

    val layoutsFor6 = listOf(SIX_THREE_THREE, SIX_FULL_TABLE, SIX_LONG_TABLE, SIX_TWO_ROWS)

    // ─── Lookups ──────────────────────────────────────────────────────────────

    fun getLayoutsForCount(count: Int): List<LayoutTemplate> = when (count) {
        2    -> layoutsFor2
        3    -> layoutsFor3
        4    -> layoutsFor4
        5    -> layoutsFor5
        6    -> layoutsFor6
        else -> layoutsFor4
    }

    fun getDefaultLayout(count: Int): LayoutTemplate = getLayoutsForCount(count).first()
}
