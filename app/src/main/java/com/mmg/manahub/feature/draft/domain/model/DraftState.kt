package com.mmg.manahub.feature.draft.domain.model

data class DraftState(
    val config: DraftConfig,
    /** Current pack number, 1-indexed (1..packCount). */
    val round: Int,
    /** Current pick number within the round, 1-indexed. */
    val pickNumber: Int,
    val seats: List<DraftSeat>,
    /** Pack currently in front of each seat, keyed by seat index. */
    val packsInFlight: Map<Int, BoosterPack>,
    val passDirection: PassDirection,
    val status: DraftStatus,
)
