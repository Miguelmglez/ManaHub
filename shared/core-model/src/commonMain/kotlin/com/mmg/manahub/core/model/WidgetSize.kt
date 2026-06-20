package com.mmg.manahub.core.model

/**
 * The display size a Home widget occupies in the dashboard grid.
 *
 * SMALL widgets take a single grid column (glanceable). MEDIUM and LARGE widgets
 * span the full two-column width; LARGE simply gives the widget more vertical
 * room to render an expanded layout.
 *
 * Lives in the domain layer (resource-free, presentation-free) because it is a
 * persisted user-preference value (KMP modularization, Phase 0.5 Blocker 2):
 * the size name is serialised into the saved dashboard layout, so the
 * persistence layer must reference it without importing presentation code.
 */
enum class WidgetSize {
    /** 1 column, glanceable single fact. */
    SMALL,

    /** 2 columns, one rich row. */
    MEDIUM,

    /** 2 columns, expanded multi-row layout. */
    LARGE,
}
