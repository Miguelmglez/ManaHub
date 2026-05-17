package com.mmg.manahub.feature.game.domain.model

// ─────────────────────────────────────────────────────────────────────────────
//  Grid slot position — determines default rotation
// ─────────────────────────────────────────────────────────────────────────────

enum class GridSlotPosition {
    LEFT,                // left column, text rotated 90°
    RIGHT,               // right column, text rotated 270°
    FULL_WIDTH_BOTTOM,   // full-width bottom, normal
    FULL_WIDTH_TOP,      // full-width top, rotated 180°
}
enum class ScreenedGridSlotPosition {
    TOP,
    MID,
    BOTTOM,
}

fun GridSlotPosition?.toDefaultDegrees(): Int = when (this) {
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
    val position: GridSlotPosition
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
    val gridRows:    Map<ScreenedGridSlotPosition, List<PlayerSlot>>
)

// ─────────────────────────────────────────────────────────────────────────────
//  LayoutTemplates — catalogue of built-in templates
// ─────────────────────────────────────────────────────────────────────────────

object LayoutTemplates {

    // ─── 2 players ───────────────────────────────────────────────────────────
    val slots = listOf(
        PlayerSlot(0, GridSlotPosition.FULL_WIDTH_TOP),
        PlayerSlot(1, GridSlotPosition.FULL_WIDTH_BOTTOM),
    )
    val TWO_TOP_BOTTOM = LayoutTemplate(
        id = "2_top_bottom", name = "Top / Bottom", playerCount = 2,
        slots = slots,
        gridRows = mapOf(ScreenedGridSlotPosition.TOP to listOf(slots[0]),ScreenedGridSlotPosition.BOTTOM to listOf(this.slots[1])),
    )

    val slots2 = listOf(
        PlayerSlot(0, GridSlotPosition.LEFT),
        PlayerSlot(1, GridSlotPosition.RIGHT),
    )
    val TWO_SIDE_BY_SIDE = LayoutTemplate(
        id = "2_side", name = "Side by Side", playerCount = 2,
        slots = slots2,
        gridRows = mapOf(ScreenedGridSlotPosition.MID to listOf(slots2[0],slots2[1]))
    )

    val layoutsFor2 = listOf(TWO_TOP_BOTTOM, TWO_SIDE_BY_SIDE )

    // ─── 3 players ───────────────────────────────────────────────────────────

    val slots3 = listOf(
        PlayerSlot(0, GridSlotPosition.LEFT),
        PlayerSlot(1, GridSlotPosition.RIGHT),
        PlayerSlot(2, GridSlotPosition.FULL_WIDTH_BOTTOM),
    )
    val THREE_U_SHAPE = LayoutTemplate(
        id = "3_triangle", name = "U", playerCount = 3,
        slots = slots3,
        gridRows = mapOf(ScreenedGridSlotPosition.MID to listOf(slots3[0],slots3[1]),ScreenedGridSlotPosition.BOTTOM to listOf(slots3[2]))
    )
    val slots5 = listOf(
        PlayerSlot(0, GridSlotPosition.LEFT),
        PlayerSlot(1, GridSlotPosition.LEFT),
        PlayerSlot(2, GridSlotPosition.RIGHT)
    )
    val THREE_TRIANGLE = LayoutTemplate(
        id = "3_two_one", name = "2-1", playerCount = 3,
        slots = slots5,
        gridRows = mapOf(ScreenedGridSlotPosition.MID to listOf(slots5[0],slots5[1],slots5[2])),
    )

    val layoutsFor3 = listOf(THREE_U_SHAPE, THREE_TRIANGLE)

    // ─── 4 players ───────────────────────────────────────────────────────────
    val slots7 = listOf(
        PlayerSlot(0, GridSlotPosition.LEFT),
        PlayerSlot(1, GridSlotPosition.LEFT),
        PlayerSlot(2, GridSlotPosition.RIGHT),
        PlayerSlot(3, GridSlotPosition.RIGHT),
    )
    val FOUR_COMPASS = LayoutTemplate(
        id = "4_compass", name = "2 each side", playerCount = 4,
        slots = slots7,
        gridRows = mapOf(ScreenedGridSlotPosition.MID to listOf(slots7[0],slots7[1],slots7[2],slots7[3])),
    )

    val slots8 = listOf(
        PlayerSlot(0, GridSlotPosition.FULL_WIDTH_TOP),
        PlayerSlot(1, GridSlotPosition.FULL_WIDTH_BOTTOM),
        PlayerSlot(2, GridSlotPosition.LEFT),
        PlayerSlot(3, GridSlotPosition.RIGHT),
    )
    val FOUR_CROSS = LayoutTemplate(
        id = "4_cross", name = "Cross", playerCount = 4,
        slots = slots8,
        gridRows = mapOf(ScreenedGridSlotPosition.TOP to listOf(slots8[0]),
            ScreenedGridSlotPosition.MID to listOf(slots8[2],slots8[3]), ScreenedGridSlotPosition.BOTTOM to listOf(slots8[1])),
    )

    val layoutsFor4 = listOf(FOUR_COMPASS,FOUR_CROSS)

    // ─── 5 players ───────────────────────────────────────────────────────────

    val slots9 = listOf(
        PlayerSlot(0, GridSlotPosition.LEFT),
        PlayerSlot(1, GridSlotPosition.LEFT),
        PlayerSlot(2, GridSlotPosition.LEFT),
        PlayerSlot(3, GridSlotPosition.RIGHT),
        PlayerSlot(4, GridSlotPosition.RIGHT),
    )
    val FIVE_THREE_TWO = LayoutTemplate(
        id = "5_three_two", name = "3 Top, 2 Bottom", playerCount = 5,
        slots = slots9,
        gridRows = mapOf(ScreenedGridSlotPosition.MID to listOf(slots9[0],slots9[1],slots9[2],slots9[3],slots9[4])))

    val slots10 = listOf(
        PlayerSlot(0, GridSlotPosition.LEFT),
        PlayerSlot(1, GridSlotPosition.LEFT),
        PlayerSlot(2, GridSlotPosition.RIGHT),
        PlayerSlot(3, GridSlotPosition.RIGHT),
        PlayerSlot(4, GridSlotPosition.RIGHT),
    )
    val FIVE_TWO_THREE = LayoutTemplate(
        id = "5_two_three", name = "2 Top, 3 Bottom", playerCount = 5,
        slots = slots10,
        gridRows = mapOf(ScreenedGridSlotPosition.MID to listOf(slots10[0],slots10[1],slots10[2],slots10[3],slots10[4]))
        )

    val slots11 = listOf(
        PlayerSlot(0, GridSlotPosition.LEFT),
        PlayerSlot(1, GridSlotPosition.LEFT),
        PlayerSlot(2, GridSlotPosition.RIGHT),
        PlayerSlot(3, GridSlotPosition.RIGHT),
        PlayerSlot(4, GridSlotPosition.FULL_WIDTH_BOTTOM),
    )
    val FIVE_SIDES_BOTTOM = LayoutTemplate(
        id = "5_sides_bottom", name = "2+2 Sides, 1 Bottom", playerCount = 5,
        slots = slots11,
        gridRows = mapOf(ScreenedGridSlotPosition.MID to listOf(slots11[0],slots11[1],slots11[2],slots11[3]),ScreenedGridSlotPosition.BOTTOM to listOf(slots11[4])
        )
    )


    val layoutsFor5 = listOf(FIVE_SIDES_BOTTOM,FIVE_THREE_TWO,FIVE_TWO_THREE)

    // ─── 6 players ───────────────────────────────────────────────────────────

    val slots13 = listOf(
        PlayerSlot(0, GridSlotPosition.LEFT),
        PlayerSlot(1, GridSlotPosition.LEFT),
        PlayerSlot(2, GridSlotPosition.LEFT),
        PlayerSlot(3, GridSlotPosition.RIGHT),
        PlayerSlot(4, GridSlotPosition.RIGHT),
        PlayerSlot(5, GridSlotPosition.RIGHT),
    )
    val SIX_TWO_ROWS = LayoutTemplate(
        id = "6_two_rows", name = "2 Rows of 3", playerCount = 6,
        slots = slots13,
        gridRows = mapOf(ScreenedGridSlotPosition.MID to listOf(slots13[0],slots13[1],slots13[2],slots13[3],slots13[4],slots13[5])
        )
    )

    val slots14 = listOf(
        PlayerSlot(0, GridSlotPosition.FULL_WIDTH_TOP),
        PlayerSlot(1, GridSlotPosition.LEFT),
        PlayerSlot(2, GridSlotPosition.LEFT),
        PlayerSlot(3, GridSlotPosition.RIGHT),
        PlayerSlot(4, GridSlotPosition.RIGHT),
        PlayerSlot(5, GridSlotPosition.FULL_WIDTH_BOTTOM),
    )
    val SIX_CROSSED = LayoutTemplate(
        id = "6_crossed", name = "6 - Cross", playerCount = 6,
        slots = slots14,
        gridRows = mapOf(ScreenedGridSlotPosition.TOP to listOf(slots14[0]), ScreenedGridSlotPosition.MID to listOf(slots14[1],slots14[2],slots14[3],slots14[4]),
            ScreenedGridSlotPosition.BOTTOM to listOf(slots14[5]))
    )

    val layoutsFor6 = listOf(SIX_TWO_ROWS, SIX_CROSSED)

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
