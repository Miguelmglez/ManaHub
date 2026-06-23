package com.mmg.manahub.core.domain.engine

import com.mmg.manahub.core.model.BoosterPack
import com.mmg.manahub.core.model.DraftCard
import com.mmg.manahub.core.model.DraftSeat
import com.mmg.manahub.core.model.EngineConfig

interface BotDrafter {
    /**
     * Picks one card from [pack] for [seat].
     *
     * @param engine The set's archetype decision engine, or null when the set has none — in which
     *   case the implementation must fall back to the colour-commitment heuristic. Passing the same
     *   [engine] used by the bots into the human "suggested pick" keeps the suggestion consistent
     *   with how the bots actually draft.
     *
     * Pure and deterministic given the same inputs.
     */
    fun pick(
        seat: DraftSeat,
        pack: BoosterPack,
        round: Int,
        pickNumber: Int,
        engine: EngineConfig?,
    ): DraftCard
}
