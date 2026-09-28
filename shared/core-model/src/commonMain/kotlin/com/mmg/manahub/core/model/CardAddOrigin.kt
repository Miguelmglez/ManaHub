package com.mmg.manahub.core.model

/** Where a queued collection add came from, so the commit rewards it as a scan or a manual add. */
enum class CardAddOrigin {
    /** Recognised by the camera scanner. */
    SCANNED,

    /** Picked by hand (AddCard search, "Select multiple"). */
    MANUAL,
}
