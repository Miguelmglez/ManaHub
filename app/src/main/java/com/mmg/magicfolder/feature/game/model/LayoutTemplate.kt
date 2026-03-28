package com.mmg.magicfolder.feature.game.model

// ─────────────────────────────────────────────────────────────────────────────
//  Grid slot identifiers
// ─────────────────────────────────────────────────────────────────────────────

enum class GridSlotPosition {
    TOP_LEFT, TOP_CENTER, TOP_RIGHT,
    MIDDLE_LEFT, MIDDLE_CENTER, MIDDLE_RIGHT,
    BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT,
}

// ─────────────────────────────────────────────────────────────────────────────
//  PlayerSlot — one seat in the layout grid
// ─────────────────────────────────────────────────────────────────────────────

data class PlayerSlot(
    val position:       GridSlotPosition,
    val defaultRotation: Int = 0,    // degrees: 0, 90, 180, 270
)

// ─────────────────────────────────────────────────────────────────────────────
//  LayoutTemplate — named arrangement of seats for N players
// ─────────────────────────────────────────────────────────────────────────────

data class LayoutTemplate(
    val name:        String,
    val playerCount: Int,
    /** Rows of slots, top to bottom. Each row is rendered as a horizontal Row. */
    val gridRows:    List<List<PlayerSlot>>,
)

// ─────────────────────────────────────────────────────────────────────────────
//  LayoutTemplates — catalogue of built-in templates
// ─────────────────────────────────────────────────────────────────────────────

object LayoutTemplates {

    val TWO_PLAYER = LayoutTemplate(
        name        = "Face to Face",
        playerCount = 2,
        gridRows    = listOf(
            listOf(PlayerSlot(GridSlotPosition.TOP_CENTER,    defaultRotation = 180)),
            listOf(PlayerSlot(GridSlotPosition.BOTTOM_CENTER, defaultRotation = 0)),
        ),
    )

    val THREE_PLAYER = LayoutTemplate(
        name        = "Triangle",
        playerCount = 3,
        gridRows    = listOf(
            listOf(PlayerSlot(GridSlotPosition.TOP_LEFT,   defaultRotation = 180),
                   PlayerSlot(GridSlotPosition.TOP_RIGHT,  defaultRotation = 180)),
            listOf(PlayerSlot(GridSlotPosition.BOTTOM_CENTER, defaultRotation = 0)),
        ),
    )

    val FOUR_PLAYER = LayoutTemplate(
        name        = "Four Corners",
        playerCount = 4,
        gridRows    = listOf(
            listOf(PlayerSlot(GridSlotPosition.TOP_LEFT,    defaultRotation = 180),
                   PlayerSlot(GridSlotPosition.TOP_RIGHT,   defaultRotation = 180)),
            listOf(PlayerSlot(GridSlotPosition.BOTTOM_LEFT, defaultRotation = 0),
                   PlayerSlot(GridSlotPosition.BOTTOM_RIGHT,defaultRotation = 0)),
        ),
    )

    val FIVE_PLAYER = LayoutTemplate(
        name        = "Star",
        playerCount = 5,
        gridRows    = listOf(
            listOf(PlayerSlot(GridSlotPosition.TOP_LEFT,      defaultRotation = 180),
                   PlayerSlot(GridSlotPosition.TOP_RIGHT,     defaultRotation = 180)),
            listOf(PlayerSlot(GridSlotPosition.MIDDLE_CENTER, defaultRotation = 0)),
            listOf(PlayerSlot(GridSlotPosition.BOTTOM_LEFT,   defaultRotation = 0),
                   PlayerSlot(GridSlotPosition.BOTTOM_RIGHT,  defaultRotation = 0)),
        ),
    )

    val SIX_PLAYER = LayoutTemplate(
        name        = "Six Pack",
        playerCount = 6,
        gridRows    = listOf(
            listOf(PlayerSlot(GridSlotPosition.TOP_LEFT,    defaultRotation = 180),
                   PlayerSlot(GridSlotPosition.TOP_CENTER,  defaultRotation = 180),
                   PlayerSlot(GridSlotPosition.TOP_RIGHT,   defaultRotation = 180)),
            listOf(PlayerSlot(GridSlotPosition.BOTTOM_LEFT, defaultRotation = 0),
                   PlayerSlot(GridSlotPosition.BOTTOM_CENTER,defaultRotation = 0),
                   PlayerSlot(GridSlotPosition.BOTTOM_RIGHT, defaultRotation = 0)),
        ),
    )

    fun forPlayerCount(count: Int): LayoutTemplate = when (count) {
        2    -> TWO_PLAYER
        3    -> THREE_PLAYER
        4    -> FOUR_PLAYER
        5    -> FIVE_PLAYER
        else -> SIX_PLAYER
    }
}
