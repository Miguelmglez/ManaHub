package com.mmg.manahub.feature.draft.domain.model

data class DraftConfig(
    val setCode: String,
    val mode: DraftMode = DraftMode.DRAFT,
    val seatCount: Int = 8,
    /** Number of packs per seat. SEALED uses 6. */
    val packCount: Int = 3,
    /** Seconds per pick; null = no timer. */
    val pickTimerSeconds: Int? = null,
)
