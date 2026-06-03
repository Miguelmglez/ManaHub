package com.mmg.manahub.feature.draft.domain.engine

import com.mmg.manahub.feature.draft.domain.model.BoosterPack
import com.mmg.manahub.feature.draft.domain.model.DraftCard
import com.mmg.manahub.feature.draft.domain.model.DraftSeat

interface BotDrafter {
    /**
     * Picks one card from [pack] for [seat], updating [DraftSeat.colorCommitment] and
     * [DraftSeat.seenSignal] in the returned [DraftCard]'s context.
     * Pure and deterministic given the same inputs.
     */
    fun pick(seat: DraftSeat, pack: BoosterPack, round: Int, pickNumber: Int): DraftCard
}
