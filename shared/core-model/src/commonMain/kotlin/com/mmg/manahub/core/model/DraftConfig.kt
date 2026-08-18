package com.mmg.manahub.core.model

data class DraftConfig(
    val setCode: String,
    val mode: DraftMode = DraftMode.DRAFT,
    val seatCount: Int = 8,
    /** Number of packs per seat. SEALED uses 6. */
    val packCount: Int = 3,
    /** Seconds per pick; null = no timer. */
    val pickTimerSeconds: Int? = null,
    /**
     * Number of cards the human (and, in lockstep, every bot seat) takes from their current pack
     * each turn before packs rotate. `1` = classic single-pick draft; `2` = "Pick 2" mode. The
     * last turn of a round may take fewer than [picksPerTurn] cards when the pack has an odd
     * number of cards remaining (see [DraftState.picksTakenInTurn]).
     */
    val picksPerTurn: Int = 1,
)
