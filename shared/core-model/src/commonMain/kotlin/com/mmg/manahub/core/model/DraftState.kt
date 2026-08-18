package com.mmg.manahub.core.model

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
    /** Packs for future rounds per seat (key = seat index, value = ordered list round 2..N). */
    val pendingPacks: Map<Int, List<BoosterPack>> = emptyMap(),
    /**
     * How many cards the human has taken from [packsInFlight]'s current pack so far this turn
     * (Pick 2+ mode), before the whole table's packs rotate. Always `0..config.picksPerTurn`;
     * reset to `0` every time packs rotate.
     */
    val picksTakenInTurn: Int = 0,
    /**
     * The Deck tab's in-progress mainboard/sideboard/basic-land curation (Draft Simulator Phase G,
     * G.4), persisted alongside the rest of the session so it survives a process death while
     * `status == BUILDING`. Null for a session that has never had its curation persisted yet
     * (a fresh draft, or one saved before this field existed) — the Deck tab then starts from its
     * defaults (everything active, no basics set), same as before this fix.
     */
    val curation: DraftCuration? = null,
)

/**
 * A snapshot of [com.mmg.manahub.feature.draft.presentation.viewmodel.DraftSimViewModel]'s Deck tab
 * curation state, persisted into [DraftState.curation] so it survives a process death mid-BUILDING
 * (Phase G, G.4). Written every time the user toggles a card or edits a basic-land count while
 * `status == BUILDING`; read back once, on the first BUILDING/COMPLETE emission observed by a given
 * ViewModel instance, to re-seed its local curation `StateFlow`s.
 */
data class DraftCuration(
    /** Positions into the human seat's pool the user toggled to the sideboard — see the matching
     * ViewModel property's KDoc for why this is keyed by pool position, not `scryfallId`. */
    val inactivePoolIndices: Set<Int> = emptySet(),
    /** Basic-land name ("Plains"/"Island"/…) → count. */
    val basicLandCounts: Map<String, Int> = emptyMap(),
    /** User-adjusted total basic-land target for the "Magic Land Suggestions" autofill. */
    val targetLandCount: Int = DeckFormat.DRAFT.targetLandCount,
)
