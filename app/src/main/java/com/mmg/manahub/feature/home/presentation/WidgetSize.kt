package com.mmg.manahub.feature.home.presentation

/**
 * The display size a Home widget occupies in the dashboard grid.
 *
 * SMALL widgets take a single grid column (glanceable). MEDIUM and LARGE widgets
 * span the full two-column width; LARGE simply gives the widget more vertical
 * room to render an expanded layout.
 */
enum class WidgetSize {
    /** 1 column, glanceable single fact. */
    SMALL,

    /** 2 columns, one rich row. */
    MEDIUM,

    /** 2 columns, expanded multi-row layout. */
    LARGE,
}
